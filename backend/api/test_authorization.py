from types import SimpleNamespace
from importlib import import_module

from django.apps import apps
from django.contrib import admin as django_admin
from django.contrib.auth.models import Group, Permission
from django.core.exceptions import PermissionDenied
from django.db import connection, transaction
from django.http import HttpResponse
from django.test import Client, RequestFactory, TestCase, TransactionTestCase, override_settings
from django.urls import path
from unittest.mock import patch
from rest_framework.test import APIRequestFactory

from .authorization import (
    ADMIN,
    CONTENT_MANAGER,
    READ_ONLY_STAFF,
    ROLE_PERMISSIONS,
    SUPER_ADMIN,
    AdminCapabilityPermission,
    RoleAssignmentError,
    admin_permission_required,
    assign_role,
    deactivate_account,
    delete_account,
    get_effective_role,
    guard_last_active_super_admin,
    has_admin_permission,
    reactivate_account,
)
from .models import ClassSchedule, User


# The production root URLconf does not mount Django admin. Expose the actual
# AdminSite only in these request tests; this adds no application route.
urlpatterns = [path('admin/', django_admin.site.urls)]


def make_user(username, **kwargs):
    return User.objects.create_user(username=username, password='Test-password-123!', **kwargs)


def configure_role(user, role):
    user.is_staff = True
    user.is_superuser = role == SUPER_ADMIN
    user.save(update_fields=['is_staff', 'is_superuser'])
    group, _ = Group.objects.get_or_create(name=role)
    migration_roles = import_module('api.migrations.0005_authorization_foundation').ROLE_PERMISSIONS
    permissions = []
    for permission_key in migration_roles[role]:
        model_name, codename = permission_key.split('.', 1)
        permissions.append(Permission.objects.get(
            content_type__app_label='api',
            content_type__model=model_name,
            codename=codename,
        ))
    if role != SUPER_ADMIN:
        group.permissions.set(permissions)
    user.groups.add(group)
    return user


def persisted_account_state(user):
    user.refresh_from_db()
    return {
        'is_active': user.is_active,
        'is_staff': user.is_staff,
        'is_superuser': user.is_superuser,
        'groups': set(user.groups.values_list('name', flat=True)),
        'permissions': set(user.user_permissions.values_list(
            'content_type__app_label', 'codename'
        )),
    }


def admin_user_change_payload(user):
    return {
        'username': user.username,
        'first_name': user.first_name,
        'last_name': user.last_name,
        'email': user.email or '',
        'school_code': user.school_code or '',
        'major': user.major or '',
        'student_id': user.student_id or '',
    }


class EffectiveRoleTests(TestCase):
    def test_valid_staff_groups_resolve_to_their_role(self):
        for role in (ADMIN, CONTENT_MANAGER, READ_ONLY_STAFF):
            user = configure_role(make_user(f'user_{role.lower()}'), role)
            self.assertEqual(get_effective_role(user), role)

    def test_active_superuser_is_super_admin_even_before_group_label(self):
        user = make_user('superuser', is_staff=True, is_superuser=True)
        self.assertEqual(get_effective_role(user), SUPER_ADMIN)

    def test_active_superuser_precedence_is_explicit_with_conflicting_groups(self):
        user = make_user('superuser_conflict', is_staff=True, is_superuser=True)
        user.groups.add(Group.objects.get(name=ADMIN), Group.objects.get(name=CONTENT_MANAGER))
        self.assertEqual(get_effective_role(user), SUPER_ADMIN)

    def test_inactive_superuser_does_not_resolve_even_with_super_admin_group(self):
        user = make_user(
            'inactive_superuser_group', is_active=False,
            is_staff=True, is_superuser=True,
        )
        user.groups.add(Group.objects.get(name=SUPER_ADMIN))
        self.assertIsNone(get_effective_role(user))

    def test_inactive_staff_and_unstaffed_superuser_fail_closed(self):
        inactive = configure_role(make_user('inactive', is_active=False), ADMIN)
        unstaffed_superuser = make_user('unlogged_superuser', is_superuser=True)
        self.assertIsNone(get_effective_role(inactive))
        self.assertIsNone(get_effective_role(unstaffed_superuser))

    def test_staff_without_role_or_with_conflicting_roles_fails_closed(self):
        missing = make_user('missing_role', is_staff=True)
        conflicting = make_user('conflicting', is_staff=True)
        conflicting.groups.add(Group.objects.get(name=ADMIN), Group.objects.get(name=CONTENT_MANAGER))
        self.assertIsNone(get_effective_role(missing))
        self.assertIsNone(get_effective_role(conflicting))

    def test_student_with_accidental_staff_group_fails_closed(self):
        student = make_user('student')
        student.groups.add(Group.objects.get(name=ADMIN))
        self.assertIsNone(get_effective_role(student))

    def test_super_admin_group_without_superuser_flags_fails_closed(self):
        user = make_user('false_super_admin', is_staff=True)
        user.groups.add(Group.objects.get(name=SUPER_ADMIN))
        self.assertIsNone(get_effective_role(user))


class CapabilityCheckTests(TestCase):
    def test_group_migration_permission_sets_match_runtime_role_policy(self):
        migrated = import_module('api.migrations.0005_authorization_foundation')
        for role in (ADMIN, CONTENT_MANAGER, READ_ONLY_STAFF):
            migration_codenames = {
                permission.split('.', 1)[1]
                for permission in migrated.ROLE_PERMISSIONS[role]
            }
            self.assertEqual(migration_codenames, ROLE_PERMISSIONS[role])
        self.assertEqual(migrated.ROLE_PERMISSIONS[SUPER_ADMIN], ())

    def test_role_migration_is_idempotent_and_does_not_promote_staff(self):
        migration = import_module('api.migrations.0005_authorization_foundation')
        active_superuser = make_user('migration_super', is_active=True,
                                     is_staff=True, is_superuser=True)
        existing_staff = make_user('migration_staff', is_staff=True)
        inactive_superuser = make_user('migration_inactive_super', is_active=False,
                                       is_staff=True, is_superuser=True)

        migration.create_roles_and_permissions(apps, connection.schema_editor())
        migration.create_roles_and_permissions(apps, connection.schema_editor())

        active_superuser.refresh_from_db()
        existing_staff.refresh_from_db()
        inactive_superuser.refresh_from_db()
        self.assertTrue(active_superuser.is_active and active_superuser.is_staff
                        and active_superuser.is_superuser)
        self.assertTrue(active_superuser.groups.filter(name=SUPER_ADMIN).exists())
        self.assertTrue(existing_staff.is_staff)
        self.assertFalse(existing_staff.groups.filter(name__in=(
            SUPER_ADMIN, ADMIN, CONTENT_MANAGER, READ_ONLY_STAFF,
        )).exists())
        self.assertTrue(inactive_superuser.is_staff and inactive_superuser.is_superuser)
        self.assertFalse(inactive_superuser.groups.filter(name=SUPER_ADMIN).exists())

        for role in (ADMIN, CONTENT_MANAGER, READ_ONLY_STAFF):
            group = Group.objects.get(name=role)
            self.assertEqual(
                set(group.permissions.values_list('codename', flat=True)),
                set(ROLE_PERMISSIONS[role]),
            )
        self.assertFalse(Group.objects.get(name=SUPER_ADMIN).permissions.exists())

    def test_role_migration_rejects_preexisting_memberships_without_erasing_them(self):
        migration = import_module('api.migrations.0005_authorization_foundation')
        preexisting_user = make_user('legacy_role_member')
        admin_group = Group.objects.get(name=ADMIN)
        admin_group.user_set.add(preexisting_user)
        before_members = set(admin_group.user_set.values_list('pk', flat=True))
        before_permissions = set(admin_group.permissions.values_list('pk', flat=True))
        with self.assertRaisesRegex(RuntimeError, 'existing user memberships'):
            migration.create_roles_and_permissions(apps, connection.schema_editor())
        self.assertEqual(set(admin_group.user_set.values_list('pk', flat=True)), before_members)
        self.assertEqual(set(admin_group.permissions.values_list('pk', flat=True)), before_permissions)

    def test_role_migration_rejects_unexpected_permissions_and_preserves_unrelated_group(self):
        migration = import_module('api.migrations.0005_authorization_foundation')
        other_user = make_user('legacy_unrelated_member')
        unrelated, _ = Group.objects.get_or_create(name='unrelated-existing-group')
        unrelated_permission = Permission.objects.filter(
            content_type__app_label='api', codename='view_dashboard'
        ).first()
        unrelated.permissions.add(unrelated_permission)
        unrelated.user_set.add(other_user)
        before_members = set(unrelated.user_set.values_list('pk', flat=True))
        before_permissions = set(unrelated.permissions.values_list('pk', flat=True))

        manager = Group.objects.get(name=CONTENT_MANAGER)
        extra_permission = Permission.objects.create(
            name='Unexpected permission',
            codename='unexpected_authorization_permission',
            content_type=unrelated_permission.content_type,
        )
        manager.permissions.add(extra_permission)
        before_manager_permissions = set(manager.permissions.values_list('pk', flat=True))
        with self.assertRaisesRegex(RuntimeError, 'outside the approved role mapping'):
            migration.create_roles_and_permissions(apps, connection.schema_editor())
        self.assertEqual(set(manager.permissions.values_list('pk', flat=True)), before_manager_permissions)
        self.assertEqual(set(unrelated.user_set.values_list('pk', flat=True)), before_members)
        self.assertEqual(set(unrelated.permissions.values_list('pk', flat=True)), before_permissions)

    def test_role_migration_is_explicitly_forward_only(self):
        migration_module = import_module('api.migrations.0005_authorization_foundation')
        run_python = next(
            operation for operation in migration_module.Migration.operations
            if operation.__class__.__name__ == 'RunPython'
        )
        self.assertIsNone(run_python.reverse_code)
        with self.assertRaises(NotImplementedError):
            run_python.database_backwards(
                'api', connection.schema_editor(), None, None
            )

    def test_role_mapping_and_group_permissions_both_apply(self):
        content_manager = configure_role(make_user('content'), CONTENT_MANAGER)
        self.assertTrue(has_admin_permission(content_manager, 'add_event'))
        self.assertFalse(has_admin_permission(content_manager, 'export_rsvps'))
        self.assertFalse(has_admin_permission(content_manager, 'delete_user'))

    def test_unmapped_or_unknown_permissions_fail_closed(self):
        read_only = configure_role(make_user('readonly'), READ_ONLY_STAFF)
        self.assertFalse(has_admin_permission(read_only, 'change_event'))
        self.assertFalse(has_admin_permission(read_only, 'invented_permission'))

    def test_super_admin_has_only_known_administrative_capabilities(self):
        super_admin = configure_role(make_user('sa'), SUPER_ADMIN)
        self.assertTrue(has_admin_permission(super_admin, 'grant_super_admin'))
        self.assertFalse(has_admin_permission(super_admin, 'invented_permission'))

    def test_admin_permission_is_required_even_with_role_group(self):
        admin = make_user('admin_without_permissions', is_staff=True)
        group, _ = Group.objects.get_or_create(name=ADMIN)
        group.permissions.clear()
        admin.groups.add(group)
        self.assertFalse(has_admin_permission(admin, 'assign_staff_roles'))


class RoleAssignmentServiceTests(TestCase):
    def setUp(self):
        self.admin = configure_role(make_user('admin_actor'), ADMIN)
        self.target = make_user('target')

    def test_admin_can_assign_a_role_to_another_account(self):
        updated = assign_role(self.admin, self.target, CONTENT_MANAGER)
        self.assertEqual(get_effective_role(updated), CONTENT_MANAGER)
        self.assertTrue(updated.is_staff)
        self.assertFalse(updated.is_superuser)
        role_memberships = set(
            updated.groups.filter(name__in=(SUPER_ADMIN, ADMIN, CONTENT_MANAGER, READ_ONLY_STAFF))
            .values_list('name', flat=True)
        )
        self.assertEqual(role_memberships, {CONTENT_MANAGER})

    def test_admin_cannot_change_own_role(self):
        before = persisted_account_state(self.admin)
        with self.assertRaises(RoleAssignmentError) as caught:
            assign_role(self.admin, self.admin, CONTENT_MANAGER)
        self.assertEqual(caught.exception.code, 'self_role_change_forbidden')
        self.assertEqual(persisted_account_state(self.admin), before)

    def test_admin_cannot_grant_super_admin(self):
        before = persisted_account_state(self.target)
        with self.assertRaises(RoleAssignmentError) as caught:
            assign_role(self.admin, self.target, SUPER_ADMIN)
        self.assertEqual(caught.exception.code, 'super_admin_only')
        self.assertEqual(persisted_account_state(self.target), before)

    def test_admin_cannot_revoke_inactive_super_admin_state(self):
        inactive_super_admin = make_user(
            'inactive_sa', is_active=False, is_staff=True, is_superuser=True
        )
        with self.assertRaises(RoleAssignmentError) as caught:
            assign_role(self.admin, inactive_super_admin, None)
        self.assertEqual(caught.exception.code, 'super_admin_only')
        self.assertEqual(persisted_account_state(inactive_super_admin), {
            'is_active': False,
            'is_staff': True,
            'is_superuser': True,
            'groups': set(),
            'permissions': set(),
        })

    def test_read_only_staff_cannot_assign_roles(self):
        read_only = configure_role(make_user('ro_actor'), READ_ONLY_STAFF)
        before = persisted_account_state(self.target)
        with self.assertRaises(RoleAssignmentError) as caught:
            assign_role(read_only, self.target, ADMIN)
        self.assertEqual(caught.exception.code, 'actor_not_authorized')
        self.assertEqual(persisted_account_state(self.target), before)

    def test_content_manager_cannot_assign_roles(self):
        content_manager = configure_role(make_user('cm_actor'), CONTENT_MANAGER)
        before = persisted_account_state(self.target)
        with self.assertRaises(RoleAssignmentError) as caught:
            assign_role(content_manager, self.target, ADMIN)
        self.assertEqual(caught.exception.code, 'actor_not_authorized')
        self.assertEqual(persisted_account_state(self.target), before)

    def test_successful_role_change_preserves_unrelated_groups(self):
        unrelated, _ = Group.objects.get_or_create(name='unrelated-membership')
        self.target.groups.add(unrelated)
        updated = assign_role(self.admin, self.target, CONTENT_MANAGER)
        self.assertEqual(
            set(updated.groups.values_list('name', flat=True)),
            {'unrelated-membership', CONTENT_MANAGER},
        )

    def test_super_admin_can_grant_and_revoke_role_through_service(self):
        super_admin = configure_role(make_user('sa_actor'), SUPER_ADMIN)
        assign_role(super_admin, self.target, SUPER_ADMIN)
        self.target.refresh_from_db()
        self.assertTrue(self.target.is_staff)
        self.assertTrue(self.target.is_superuser)
        self.assertEqual(get_effective_role(self.target), SUPER_ADMIN)
        assign_role(super_admin, self.target, None)
        self.target.refresh_from_db()
        self.assertFalse(self.target.is_staff)
        self.assertFalse(self.target.is_superuser)
        self.assertIsNone(get_effective_role(self.target))

    def test_unstaffed_django_superuser_bypass_does_not_authorize_role_assignment(self):
        unstaffed_superuser = make_user('unstaffed_privileged', is_superuser=True)
        self.assertTrue(unstaffed_superuser.has_perm('api.assign_staff_roles'))
        before = persisted_account_state(self.target)
        with self.assertRaises(RoleAssignmentError) as caught:
            assign_role(unstaffed_superuser, self.target, ADMIN)
        self.assertEqual(caught.exception.code, 'actor_not_authorized')
        self.assertEqual(persisted_account_state(self.target), before)

    def test_last_active_super_admin_cannot_be_demoted(self):
        super_admin = configure_role(make_user('last_sa'), SUPER_ADMIN)
        before = persisted_account_state(super_admin)
        for role in (ADMIN, None):
            with self.subTest(role=role):
                with self.assertRaises(RoleAssignmentError) as caught:
                    assign_role(super_admin, super_admin, role)
                self.assertEqual(caught.exception.code, 'last_active_super_admin')
                self.assertEqual(persisted_account_state(super_admin), before)

    def test_two_super_admins_can_reduce_to_one_but_not_remove_the_final_one(self):
        first = configure_role(make_user('first_sa'), SUPER_ADMIN)
        second = configure_role(make_user('second_sa'), SUPER_ADMIN)
        assign_role(first, second, None)
        second.refresh_from_db()
        self.assertIsNone(get_effective_role(second))
        before = persisted_account_state(first)
        with self.assertRaises(RoleAssignmentError) as caught:
            assign_role(first, first, None)
        self.assertEqual(caught.exception.code, 'last_active_super_admin')
        self.assertEqual(persisted_account_state(first), before)

    def test_last_super_admin_guard_requires_same_atomic_transaction(self):
        super_admin = configure_role(make_user('guarded_sa'), SUPER_ADMIN)
        with self.assertRaises(RoleAssignmentError) as caught:
            with transaction.atomic():
                guard_last_active_super_admin(super_admin, 'deactivated')
        self.assertEqual(caught.exception.code, 'last_active_super_admin')

    def test_invalid_role_returns_structured_error_without_changes(self):
        before = persisted_account_state(self.target)
        with self.assertRaises(RoleAssignmentError) as caught:
            assign_role(self.admin, self.target, 'ROOT')
        self.assertEqual(caught.exception.as_dict()['code'], 'invalid_role')
        self.assertEqual(persisted_account_state(self.target), before)


class AccountLifecycleServiceTests(TestCase):
    def setUp(self):
        self.admin = configure_role(make_user('lifecycle_admin'), ADMIN)
        self.super_admin = configure_role(make_user('lifecycle_sa'), SUPER_ADMIN)
        self.target = make_user('lifecycle_target')

    def test_admin_can_deactivate_and_reactivate_regular_account_without_losing_history(self):
        schedule = ClassSchedule.objects.create(
            user=self.target,
            day_of_week=ClassSchedule.DayOfWeek.MON,
            start_time='09:00',
            end_time='10:00',
            course_name='History preservation',
        )
        before_groups = set(self.target.groups.values_list('name', flat=True))
        deactivate_account(self.admin, self.target)
        self.target.refresh_from_db()
        self.assertFalse(self.target.is_active)
        self.assertEqual(set(self.target.groups.values_list('name', flat=True)), before_groups)
        self.assertTrue(ClassSchedule.objects.filter(pk=schedule.pk, user=self.target).exists())
        reactivate_account(self.admin, self.target)
        self.target.refresh_from_db()
        self.assertTrue(self.target.is_active)

    def test_admin_cannot_deactivate_or_reactivate_super_admin(self):
        before = persisted_account_state(self.super_admin)
        for operation in (deactivate_account, reactivate_account):
            with self.subTest(operation=operation.__name__):
                with self.assertRaises(RoleAssignmentError) as caught:
                    operation(self.admin, self.super_admin)
                self.assertEqual(caught.exception.code, 'actor_not_authorized')
                self.assertEqual(persisted_account_state(self.super_admin), before)

    def test_last_super_admin_cannot_be_deactivated(self):
        lone = self.super_admin
        before = persisted_account_state(lone)
        with self.assertRaises(RoleAssignmentError) as caught:
            deactivate_account(lone, lone)
        self.assertEqual(caught.exception.code, 'last_active_super_admin')
        self.assertEqual(persisted_account_state(lone), before)

    def test_two_super_admins_can_deactivate_one_but_preserve_the_final_one(self):
        another = configure_role(make_user('another_lifecycle_sa'), SUPER_ADMIN)
        deactivate_account(self.super_admin, another)
        another.refresh_from_db()
        self.assertFalse(another.is_active)
        before = persisted_account_state(self.super_admin)
        with self.assertRaises(RoleAssignmentError) as caught:
            deactivate_account(self.super_admin, self.super_admin)
        self.assertEqual(caught.exception.code, 'last_active_super_admin')
        self.assertEqual(persisted_account_state(self.super_admin), before)

    def test_admin_cannot_permanently_delete_even_with_confirmation(self):
        before = persisted_account_state(self.target)
        with self.assertRaises(RoleAssignmentError) as caught:
            delete_account(
                self.admin,
                self.target,
                confirmation=f'DELETE USER {self.target.pk}',
                retention_reference='approved-ticket-1234',
            )
        self.assertEqual(caught.exception.code, 'actor_not_authorized')
        self.assertEqual(persisted_account_state(self.target), before)


@override_settings(ROOT_URLCONF='api.test_authorization')
class UserAdminProtectionTests(TestCase):
    def setUp(self):
        self.admin = configure_role(make_user('admin_site_actor'), ADMIN)
        self.target = make_user('admin_site_target')
        self.super_admin = configure_role(make_user('admin_site_sa'), SUPER_ADMIN)
        self.client = Client()
        self.client.force_login(self.admin)

    def _change_url(self, user):
        return f'/admin/api/user/{user.pk}/change/'

    def test_admin_cannot_promote_self_through_admin_form(self):
        before = persisted_account_state(self.admin)
        payload = admin_user_change_payload(self.admin)
        payload.update({'is_staff': '1', 'is_superuser': '1'})
        response = self.client.post(self._change_url(self.admin), payload)
        self.assertEqual(response.status_code, 403)
        self.assertEqual(persisted_account_state(self.admin), before)

    def test_admin_cannot_grant_super_admin_or_edit_role_groups(self):
        before = persisted_account_state(self.target)
        response = self.client.post(self._change_url(self.target), {
            **admin_user_change_payload(self.target),
            'groups': [Group.objects.get(name=SUPER_ADMIN).pk],
            'is_staff': '1',
            'is_superuser': '1',
        })
        self.assertEqual(response.status_code, 403)
        self.assertEqual(persisted_account_state(self.target), before)

    def test_admin_cannot_assign_or_revoke_role_groups_directly(self):
        self.target.groups.add(Group.objects.get(name=CONTENT_MANAGER))
        self.target.is_staff = True
        self.target.save(update_fields=['is_staff'])
        before = persisted_account_state(self.target)
        for submitted_groups in ([Group.objects.get(name=ADMIN).pk], ''):
            with self.subTest(submitted_groups=submitted_groups):
                response = self.client.post(self._change_url(self.target), {
                    **admin_user_change_payload(self.target),
                    'groups': submitted_groups,
                })
                self.assertEqual(response.status_code, 403)
                self.assertEqual(persisted_account_state(self.target), before)

    def test_admin_cannot_clear_staff_or_superuser_flags(self):
        before = persisted_account_state(self.admin)
        payload = admin_user_change_payload(self.admin)
        payload.update({'is_active': '0', 'is_staff': '0', 'is_superuser': '0'})
        response = self.client.post(self._change_url(self.admin), payload)
        self.assertEqual(response.status_code, 403)
        self.assertEqual(persisted_account_state(self.admin), before)

    def test_admin_cannot_deactivate_or_delete_last_super_admin(self):
        before = persisted_account_state(self.super_admin)
        payload = admin_user_change_payload(self.super_admin)
        payload['is_active'] = '0'
        response = self.client.post(self._change_url(self.super_admin), payload)
        self.assertEqual(response.status_code, 403)
        delete_response = self.client.post(
            f'/admin/api/user/{self.super_admin.pk}/delete/', {'post': 'yes'}
        )
        self.assertEqual(delete_response.status_code, 403)
        self.assertEqual(persisted_account_state(self.super_admin), before)

    def test_admin_cannot_add_users_or_delete_ordinary_users_from_admin(self):
        before = persisted_account_state(self.target)
        add_response = self.client.get('/admin/api/user/add/')
        delete_response = self.client.post(
            f'/admin/api/user/{self.target.pk}/delete/', {'post': 'yes'}
        )
        self.assertEqual(add_response.status_code, 403)
        self.assertEqual(delete_response.status_code, 403)
        self.assertEqual(persisted_account_state(self.target), before)

    def test_admin_can_edit_only_ordinary_profile_fields(self):
        payload = admin_user_change_payload(self.target)
        payload['major'] = 'Public Administration'
        response = self.client.post(self._change_url(self.target), payload)
        self.assertEqual(response.status_code, 302)
        self.target.refresh_from_db()
        self.assertEqual(self.target.major, 'Public Administration')
        self.assertFalse(self.target.is_staff)
        self.assertFalse(self.target.is_superuser)


class AccountLifecycleExtendedTests(TestCase):
    def setUp(self):
        self.admin = configure_role(make_user('lifecycle_extended_admin'), ADMIN)
        self.super_admin = configure_role(make_user('lifecycle_extended_sa'), SUPER_ADMIN)
        self.target = make_user('lifecycle_extended_target')

    def test_super_admin_delete_requires_confirmation_and_retention_reference(self):
        before = persisted_account_state(self.target)
        with self.assertRaises(RoleAssignmentError):
            delete_account(self.super_admin, self.target, confirmation='yes', retention_reference='ticket')
        with self.assertRaises(RoleAssignmentError):
            delete_account(
                self.super_admin,
                self.target,
                confirmation=f'DELETE USER {self.target.pk}',
                retention_reference=' ',
            )
        self.assertEqual(persisted_account_state(self.target), before)

    def test_super_admin_delete_requires_explicit_confirmation_and_reference_then_deletes(self):
        target_id = self.target.pk
        delete_account(
            self.super_admin,
            self.target,
            confirmation=f'DELETE USER {target_id}',
            retention_reference='approved-ticket-5678',
        )
        self.assertFalse(User.objects.filter(pk=target_id).exists())

    def test_super_admin_can_delete_one_of_two_and_the_remaining_one_is_protected(self):
        second = configure_role(make_user('delete_second_sa'), SUPER_ADMIN)
        target_id = self.super_admin.pk
        delete_account(
            second,
            self.super_admin,
            confirmation=f'DELETE USER {target_id}',
            retention_reference='approved-ticket-2345',
        )
        self.assertFalse(User.objects.filter(pk=target_id).exists())
        before = persisted_account_state(second)
        with self.assertRaises(RoleAssignmentError) as caught:
            deactivate_account(second, second)
        self.assertEqual(caught.exception.code, 'last_active_super_admin')
        self.assertEqual(persisted_account_state(second), before)

    def test_last_super_admin_cannot_be_deleted_by_another_admin_path(self):
        before = persisted_account_state(self.super_admin)
        with self.assertRaises(RoleAssignmentError) as caught:
            delete_account(
                self.super_admin,
                self.super_admin,
                confirmation=f'DELETE USER {self.super_admin.pk}',
                retention_reference='approved-ticket-9012',
            )
        self.assertEqual(caught.exception.code, 'last_active_super_admin')
        self.assertEqual(persisted_account_state(self.super_admin), before)

    def test_reactivation_rejects_inconsistent_staff_role_state(self):
        inconsistent = make_user('inconsistent_inactive', is_active=False, is_staff=True)
        before = persisted_account_state(inconsistent)
        with self.assertRaises(RoleAssignmentError) as caught:
            reactivate_account(self.super_admin, inconsistent)
        self.assertEqual(caught.exception.code, 'inconsistent_role_state')
        self.assertEqual(persisted_account_state(inconsistent), before)

    def test_lifecycle_failure_after_status_write_rolls_back(self):
        before = persisted_account_state(self.target)
        from . import authorization
        original_save = authorization._persist_account_status

        def save_then_fail(*args, **kwargs):
            original_save(*args, **kwargs)
            raise RuntimeError('simulated lifecycle failure')

        with patch('api.authorization._persist_account_status', side_effect=save_then_fail):
            with self.assertRaises(RuntimeError):
                deactivate_account(self.admin, self.target)
        self.assertEqual(persisted_account_state(self.target), before)

    def test_role_assignment_failure_after_writes_rolls_back_everything(self):
        unrelated, _ = Group.objects.get_or_create(name='rollback-membership')
        self.target.groups.add(unrelated)
        before = persisted_account_state(self.target)
        from . import authorization
        original_write = authorization._write_role_assignment

        def write_then_fail(*args, **kwargs):
            original_write(*args, **kwargs)
            raise RuntimeError('simulated failure after role writes')

        with patch('api.authorization._write_role_assignment', side_effect=write_then_fail):
            with self.assertRaises(RuntimeError):
                assign_role(self.admin, self.target, CONTENT_MANAGER)
        self.assertEqual(persisted_account_state(self.target), before)


class AuthorizationAdapterTests(TestCase):
    def test_dashboard_decorator_redirects_anonymous_and_forbids_student(self):
        factory = RequestFactory()
        protected = admin_permission_required('view_dashboard')(lambda request: HttpResponse('ok'))
        anonymous_request = factory.get('/api/dashboard/')
        anonymous_request.user = type('Anonymous', (), {'is_authenticated': False})()
        self.assertEqual(protected(anonymous_request).status_code, 302)
        student_request = factory.get('/api/dashboard/')
        student_request.user = make_user('dashboard_student')
        with self.assertRaises(PermissionDenied):
            protected(student_request)

    def test_drf_permission_uses_action_mapping_and_fails_closed_if_missing(self):
        user = configure_role(make_user('api_content'), CONTENT_MANAGER)
        request = APIRequestFactory().post('/api/events/')
        request.user = user
        permission = AdminCapabilityPermission()
        view = SimpleNamespace(action='create', admin_permission_map={'create': 'add_event'})
        self.assertTrue(permission.has_permission(request, view))
        view.admin_permission_map = {}
        self.assertFalse(permission.has_permission(request, view))


class AtomicRoleGuardTests(TransactionTestCase):
    def test_last_super_admin_guard_requires_an_atomic_transaction(self):
        super_admin = make_user(
            'guard_requires_atomic', is_staff=True, is_superuser=True
        )
        with self.assertRaises(RuntimeError):
            guard_last_active_super_admin(super_admin, 'deactivated')
