"""Shared staff-role policy and safe role-assignment service.

Dashboard/API handlers should use ``admin_permission_required`` or
``AdminCapabilityPermission`` rather than checking ``is_staff`` directly.
This foundation intentionally does not wire every existing route yet.
"""

from functools import wraps

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.contrib.auth.views import redirect_to_login
from django.core.exceptions import PermissionDenied
from django.db import connection, transaction
from rest_framework.permissions import BasePermission


SUPER_ADMIN = 'SUPER_ADMIN'
ADMIN = 'ADMIN'
CONTENT_MANAGER = 'CONTENT_MANAGER'
READ_ONLY_STAFF = 'READ_ONLY_STAFF'
STAFF_ROLES = (SUPER_ADMIN, ADMIN, CONTENT_MANAGER, READ_ONLY_STAFF)
NON_SUPER_ADMIN_ROLES = (ADMIN, CONTENT_MANAGER, READ_ONLY_STAFF)


def _perms(*codenames):
    return frozenset(codenames)


_ADMIN_PERMISSIONS = _perms(
    'view_dashboard', 'view_user', 'add_user', 'change_user',
    'change_user_status', 'assign_staff_roles', 'view_sensitive_user_data',
    'reset_user_password', 'view_event', 'add_event', 'change_event',
    'archive_event', 'manage_event_media', 'view_rsvp',
    'view_rsvp_aggregate', 'view_rsvp_pii', 'export_rsvps', 'cancel_rsvp',
    'view_school', 'add_school', 'change_school', 'delete_school',
    'view_fixture', 'add_fixture', 'change_fixture', 'delete_fixture',
    'view_classschedule', 'add_classschedule', 'change_classschedule',
    'delete_classschedule', 'view_timetable_conflicts',
    'manage_class_schedules', 'view_bookmark', 'add_bookmark',
    'change_bookmark', 'delete_bookmark',
)
_CONTENT_MANAGER_PERMISSIONS = _perms(
    'view_dashboard', 'view_event', 'add_event', 'change_event',
    'manage_event_media', 'view_rsvp_aggregate',
)
_READ_ONLY_PERMISSIONS = _perms(
    'view_dashboard', 'view_event', 'view_rsvp_aggregate', 'view_school',
    'view_fixture', 'view_timetable_conflicts',
)
_SUPER_ADMIN_PERMISSIONS = frozenset(
    _ADMIN_PERMISSIONS | _CONTENT_MANAGER_PERMISSIONS | _READ_ONLY_PERMISSIONS
    | _perms(
        'grant_super_admin', 'delete_user', 'delete_event',
        'add_rsvp', 'change_rsvp', 'delete_rsvp',
    )
)

# This is the authoritative application capability allowlist. Super Admin
# authority comes from the validated account flags, not permissions on its
# marker Group; the group itself therefore receives no permissions.
ROLE_PERMISSIONS = {
    SUPER_ADMIN: _SUPER_ADMIN_PERMISSIONS,
    ADMIN: _ADMIN_PERMISSIONS,
    CONTENT_MANAGER: _CONTENT_MANAGER_PERMISSIONS,
    READ_ONLY_STAFF: _READ_ONLY_PERMISSIONS,
}
ALL_ADMIN_PERMISSION_CODENAMES = frozenset().union(*ROLE_PERMISSIONS.values())


class RoleAssignmentError(Exception):
    """Structured, safe-to-display error raised by role management."""

    def __init__(self, code, message):
        self.code = code
        self.message = message
        super().__init__(message)

    def as_dict(self):
        return {'code': self.code, 'message': self.message}


def get_effective_role(user):
    """Return a valid staff role, or None when identity/role state is invalid."""
    if not getattr(user, 'is_authenticated', False):
        return None
    if not getattr(user, 'is_active', False) or not getattr(user, 'is_staff', False):
        return None

    # Django's active superuser permission bypass is intentional and maps to
    # Super Admin. The migration adds the explicit Group label; the flags keep
    # an already-valid Super Admin recognizable during rollout.
    if getattr(user, 'is_superuser', False):
        return SUPER_ADMIN

    if not getattr(user, 'pk', None):
        return None
    memberships = set(
        user.groups.filter(name__in=STAFF_ROLES).values_list('name', flat=True)
    )
    if SUPER_ADMIN in memberships or len(memberships) != 1:
        return None
    role = next(iter(memberships))
    return role if role in NON_SUPER_ADMIN_ROLES else None


def has_admin_permission(user, permission_codename):
    """Check a known capability after validating the account's effective role."""
    role = get_effective_role(user)
    if role is None or permission_codename not in ALL_ADMIN_PERMISSION_CODENAMES:
        return False
    if permission_codename not in ROLE_PERMISSIONS[role]:
        return False
    if role == SUPER_ADMIN:
        return True
    return user.has_perm(f'api.{permission_codename}')


def admin_permission_required(permission_codename, login_url='/api/dashboard/login/'):
    """Protect a Django dashboard view; anonymous users log in, others get 403."""
    def decorator(view_func):
        @wraps(view_func)
        def wrapped(request, *args, **kwargs):
            user = getattr(request, 'user', None)
            if not getattr(user, 'is_authenticated', False):
                return redirect_to_login(request.get_full_path(), login_url)
            if not has_admin_permission(user, permission_codename):
                raise PermissionDenied
            return view_func(request, *args, **kwargs)
        return wrapped
    return decorator


class AdminCapabilityPermission(BasePermission):
    """DRF permission using ``view.admin_permission_map`` by action/method.

    Missing action mappings fail closed. Object ownership remains the
    responsibility of the view's queryset and object-permission checks.
    """

    message = 'You do not have permission to perform this administrative action.'

    def _required_capability(self, request, view):
        action = getattr(view, 'action', None) or request.method.lower()
        permission_map = getattr(view, 'admin_permission_map', {})
        return permission_map.get(action)

    def has_permission(self, request, view):
        capability = self._required_capability(request, view)
        return bool(capability and has_admin_permission(request.user, capability))

    def has_object_permission(self, request, view, obj):
        return self.has_permission(request, view)


def _role_assignment_error(code, message):
    return RoleAssignmentError(code, message)


def _is_super_admin_state(user):
    """Recognize accounts that must never be managed by an ordinary Admin."""
    return bool(
        getattr(user, 'is_superuser', False)
        or (
            getattr(user, 'pk', None)
            and user.groups.filter(name=SUPER_ADMIN).exists()
        )
    )


def _lock_active_super_admins(User):
    """Lock the current active Super Admin set in stable order.

    This serializes supported lifecycle operations on databases that honor
    SELECT FOR UPDATE. SQLite does not honor row locks; see authorization.md.
    """
    return list(
        User.objects.select_for_update().filter(
            is_active=True, is_staff=True, is_superuser=True
        ).order_by('pk')
    )


def _lock_accounts(actor_id, target_id):
    """Lock Super Admin state first, then any other actor/target rows."""
    User = get_user_model()
    active_super_admins = _lock_active_super_admins(User)
    users = {user.pk: user for user in active_super_admins}
    remaining_ids = {actor_id, target_id} - set(users)
    if remaining_ids:
        for user in User.objects.select_for_update().filter(
            pk__in=remaining_ids
        ).order_by('pk'):
            users[user.pk] = user
    return users


def _actor_can_manage_account(actor, target, *, status_change=False,
                              super_admin_only=False):
    actor_role = get_effective_role(actor)
    target_is_super_admin = _is_super_admin_state(target)
    if actor_role == SUPER_ADMIN:
        return
    if (
        not super_admin_only
        and actor_role == ADMIN
        and not target_is_super_admin
        and has_admin_permission(
            actor,
            'change_user_status' if status_change else 'change_user',
        )
    ):
        return
    raise _role_assignment_error(
        'actor_not_authorized',
        'The acting account cannot perform this account operation.',
    )


def _assert_reactivatable_role_state(user):
    """Reject inconsistent privilege state rather than activating it."""
    memberships = set(
        user.groups.filter(name__in=STAFF_ROLES).values_list('name', flat=True)
    )
    if user.is_superuser:
        valid = user.is_staff and memberships.issubset({SUPER_ADMIN})
    elif user.is_staff:
        valid = len(memberships) == 1 and memberships.issubset(
            set(NON_SUPER_ADMIN_ROLES)
        )
    else:
        valid = not memberships
    if not valid:
        raise _role_assignment_error(
            'inconsistent_role_state',
            'The account has inconsistent role state; repair it through role assignment first.',
        )


def _write_role_assignment(user, role, group):
    existing_role_groups = list(user.groups.filter(name__in=STAFF_ROLES))
    if existing_role_groups:
        user.groups.remove(*existing_role_groups)
    user.is_staff = role is not None
    user.is_superuser = role == SUPER_ADMIN
    user.save(update_fields=['is_staff', 'is_superuser'])
    if group is not None:
        user.groups.add(group)
    for cache_name in ('_perm_cache', '_user_perm_cache', '_group_perm_cache'):
        if hasattr(user, cache_name):
            delattr(user, cache_name)
    return user


def _persist_account_status(user, is_active):
    user.is_active = is_active
    user.save(update_fields=['is_active'])
    return user


def guard_last_active_super_admin(target, operation='change'):
    """Guard removal of the last active Super Admin inside an atomic block.

    Supported lifecycle services call this in the same transaction as the
    state change. It does not itself alter an account.
    """
    if not connection.in_atomic_block:
        raise RuntimeError('Super Admin protection must run inside transaction.atomic().')
    active_super_admins = _lock_active_super_admins(get_user_model())
    active_ids = {user.pk for user in active_super_admins}
    if target.pk in active_ids and len(active_ids) <= 1:
        raise _role_assignment_error(
            'last_active_super_admin',
            f'The last active Super Admin cannot be {operation}.',
        )


def assign_role(actor, target, role):
    """Atomically assign/revoke one staff role through the central service.

    ``role=None`` revokes staff access. Admins may assign non-Super-Admin
    roles to other accounts but cannot change their own role or grant/revoke
    Super Admin. Existing unrelated Group memberships are preserved.
    """
    if role is not None and role not in STAFF_ROLES:
        raise _role_assignment_error('invalid_role', 'The requested staff role is not recognized.')
    if not getattr(actor, 'pk', None) or not getattr(target, 'pk', None):
        raise _role_assignment_error('saved_accounts_required', 'Actor and target accounts must be saved.')

    actor_id, target_id = actor.pk, target.pk
    with transaction.atomic():
        users_by_id = _lock_accounts(actor_id, target_id)
        locked_actor = users_by_id.get(actor_id)
        locked_target = users_by_id.get(target_id)
        if locked_actor is None or locked_target is None:
            raise _role_assignment_error('account_not_found', 'Actor or target account no longer exists.')

        actor_role = get_effective_role(locked_actor)
        target_has_super_admin_state = _is_super_admin_state(locked_target)
        if actor_role == SUPER_ADMIN:
            pass
        elif actor_role == ADMIN and has_admin_permission(locked_actor, 'assign_staff_roles'):
            if role == SUPER_ADMIN:
                raise _role_assignment_error(
                    'super_admin_only', 'Only a Super Admin may grant or revoke Super Admin.'
                )
            if target_has_super_admin_state:
                raise _role_assignment_error(
                    'super_admin_only', 'Only a Super Admin may grant or revoke Super Admin.'
                )
            if actor_id == target_id:
                raise _role_assignment_error(
                    'self_role_change_forbidden', 'Admins cannot change their own staff role.'
                )
        else:
            raise _role_assignment_error(
                'actor_not_authorized', 'The acting account cannot assign staff roles.'
            )

        if role is not None and not locked_target.is_active:
            raise _role_assignment_error('target_inactive', 'An inactive account cannot receive a staff role.')

        target_is_active_super_admin = (
            locked_target.is_active and locked_target.is_staff and locked_target.is_superuser
        )
        if target_is_active_super_admin and role != SUPER_ADMIN:
            guard_last_active_super_admin(locked_target, operation='demoted or deactivated')

        group = None
        if role is not None:
            try:
                group = Group.objects.get(name=role)
            except Group.DoesNotExist:
                raise _role_assignment_error(
                    'role_not_configured', 'The requested staff role is not configured.'
                )

        return _write_role_assignment(locked_target, role, group)


def deactivate_account(actor, target):
    """Deactivate an account without deleting user-owned history."""
    if not getattr(actor, 'pk', None) or not getattr(target, 'pk', None):
        raise _role_assignment_error('saved_accounts_required', 'Actor and target accounts must be saved.')
    with transaction.atomic():
        users = _lock_accounts(actor.pk, target.pk)
        locked_actor, locked_target = users.get(actor.pk), users.get(target.pk)
        if locked_actor is None or locked_target is None:
            raise _role_assignment_error('account_not_found', 'Actor or target account no longer exists.')
        _actor_can_manage_account(locked_actor, locked_target, status_change=True)
        if locked_target.is_active and locked_target.is_staff and locked_target.is_superuser:
            guard_last_active_super_admin(locked_target, operation='deactivated')
        return _persist_account_status(locked_target, False)


def reactivate_account(actor, target):
    """Reactivate an account while preserving its existing role and history."""
    if not getattr(actor, 'pk', None) or not getattr(target, 'pk', None):
        raise _role_assignment_error('saved_accounts_required', 'Actor and target accounts must be saved.')
    with transaction.atomic():
        users = _lock_accounts(actor.pk, target.pk)
        locked_actor, locked_target = users.get(actor.pk), users.get(target.pk)
        if locked_actor is None or locked_target is None:
            raise _role_assignment_error('account_not_found', 'Actor or target account no longer exists.')
        _actor_can_manage_account(locked_actor, locked_target, status_change=True)
        _assert_reactivatable_role_state(locked_target)
        return _persist_account_status(locked_target, True)


def delete_account(actor, target, *, confirmation, retention_reference):
    """Permanently delete an account with explicit safeguards.

    This is an exceptional operation: callers must supply the exact target
    confirmation token and an approved retention/ticket reference. It is
    available only to Super Admins and cascades related records per model
    foreign-key policies.
    """
    if not getattr(actor, 'pk', None) or not getattr(target, 'pk', None):
        raise _role_assignment_error('saved_accounts_required', 'Actor and target accounts must be saved.')
    if not isinstance(confirmation, str) or confirmation != f'DELETE USER {target.pk}':
        raise _role_assignment_error('confirmation_required', 'The exact deletion confirmation is required.')
    if not isinstance(retention_reference, str) or not retention_reference.strip():
        raise _role_assignment_error('retention_reference_required', 'An approved retention reference is required.')
    with transaction.atomic():
        users = _lock_accounts(actor.pk, target.pk)
        locked_actor, locked_target = users.get(actor.pk), users.get(target.pk)
        if locked_actor is None or locked_target is None:
            raise _role_assignment_error('account_not_found', 'Actor or target account no longer exists.')
        _actor_can_manage_account(locked_actor, locked_target, super_admin_only=True)
        if locked_target.is_active and locked_target.is_staff and locked_target.is_superuser:
            guard_last_active_super_admin(locked_target, operation='deleted')
        if actor.pk == target.pk:
            raise _role_assignment_error('self_deletion_forbidden', 'An account cannot delete itself.')
        locked_target.delete()
