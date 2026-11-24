# CampusPulse staff authorization foundation

## Roles

Staff roles are Django Groups named `SUPER_ADMIN`, `ADMIN`, `CONTENT_MANAGER`,
and `READ_ONLY_STAFF`. The policy and role-assignment service are in
`api/authorization.py`.

| Role | Granted capabilities |
|---|---|
| `SUPER_ADMIN` | All enumerated administrative capabilities. Requires active status, `is_staff=True`, and `is_superuser=True`. |
| `ADMIN` | User/account operations, event and RSVP operations, schools, fixtures, schedules, bookmarks, and conflict management. Cannot grant/revoke Super Admin. |
| `CONTENT_MANAGER` | Create/edit events and media; view aggregate RSVP information only. No user management, attendee PII, RSVP cancellation/export, or event archive/delete. |
| `READ_ONLY_STAFF` | Dashboard and authorized event, school, fixture, aggregate RSVP, and conflict reads. No administrative writes or user PII. Normal personal self-service remains separate. |

The `ROLE_PERMISSIONS` mapping is the application capability allowlist. Django
Group permissions are required for non-superuser roles. The `SUPER_ADMIN`
Group is a role label and intentionally has no Group permissions; validated
superuser flags provide full authority. Unknown capabilities and invalid role
states fail closed.

## Role identification

An active account with `is_staff=True` and `is_superuser=True` is identified as
`SUPER_ADMIN`, including during rollout before its Group label is present. An
active non-superuser staff account must belong to exactly one recognized
non-Super-Admin role Group. Missing/conflicting role Groups, inactive accounts,
and role Groups on ordinary students do not grant staff access. Do not infer
`ADMIN` from `is_staff=True`.

## Safe assignment and protections

Application role changes must use `assign_role(actor, target, role)` in
`api/authorization.py`. It checks actor authority, validates the target role,
updates flags and role Group membership atomically, and returns a structured
`RoleAssignmentError` on rejection. Admins cannot change their own role or
grant/revoke Super Admin. Only Super Admins can grant/revoke that role.

`assign_role`, `deactivate_account`, `reactivate_account`, and
`delete_account` are the supported application services for staff privilege and
account lifecycle changes. They re-read and lock actor/target state inside an
atomic transaction. Deactivation changes only `is_active`, retaining user
records and role history. Reactivation rejects inconsistent role state.

Permanent deletion is restricted to Super Admins and requires the exact
`DELETE USER <id>` confirmation plus a non-empty retention reference. The
reference is an operator-provided record, not an automated determination that
the retention policy permits deletion. User-related rows may cascade according
to their model foreign keys. No deletion route is exposed in Django admin.

The Django User admin exposes only ordinary profile fields. Account creation,
status changes, role/groups, direct permissions, and permanent deletion are
not editable there. Server-side save and delete checks reject protected fields
even if submitted outside the rendered form. Other model admin registrations
remain unchanged.

The last-active-Super-Admin guard is called by role demotion, deactivation,
and deletion inside the same atomic block. It locks the active Super Admin set
in stable primary-key order on databases that implement `SELECT FOR UPDATE`.
Supported ORM services preserve this invariant; direct `QuerySet.update()`,
model `save()` calls that modify privilege fields, raw SQL, database-console
changes, or other unreviewed code can bypass them and must be treated as
privileged operations.

Initial provisioning through Django's `createsuperuser` and the existing
`seed_campus_data` management command are out-of-band privileged paths, not
role-service operations. The seed command can set superuser flags directly and
must remain limited to disposable development data; do not run it in
production. Restrict bootstrap command execution to authorized operators,
review resulting account state, and record the operation. No public bootstrap
or recovery endpoint is provided.

## Permission checks

Use `admin_permission_required(codename)` for dashboard views and
`AdminCapabilityPermission` with an explicit `admin_permission_map` on DRF
views/viewsets. Missing DRF action mappings are denied. Object ownership must
continue to be enforced by the view's queryset and object-permission checks.
Anonymous dashboard requests redirect to login; authenticated users lacking
the capability receive 403.

This phase provides reusable checks only. Existing dashboard decorators and
API viewsets are not yet wired to this policy; route-by-route integration is a
separate phase.

## Bootstrap and recovery

The migration creates the four role Groups and permissions, assigns only
already-active staff superusers to `SUPER_ADMIN`, and leaves other staff
unassigned for explicit review. It does not change privilege flags or create
accounts. Review staff assignments before granting roles through the central
service.

Maintain at least two independently controlled Super Admin accounts. If none
can sign in, use a controlled host-level break-glass procedure: verify two
authorized operators, take a database backup, use Django's `createsuperuser`
only on the authorized host to create an active bootstrap account, confirm its
effective role and access, record the incident without recording credentials,
and rotate affected credentials. This bootstrap path is an explicit operator
exception to the central role service. Do not expose a public recovery
endpoint or share a permanent password.

## Migration notes

`api/migrations/0005_authorization_foundation.py` uses historical models and
creates permissions/groups idempotently. It preflights all existing reserved
role groups before changing them. Unexpected memberships or permissions cause
the migration to abort for operator review; the migration never clears them.
Unrelated groups and permissions are preserved. No user is promoted by
`is_staff` alone; other existing staff require manual review. The migration
does not change account privilege flags or application records.

The migration is deliberately forward-only. Django will refuse to reverse it;
the data migration does not attempt to infer or delete later role assignments.
Before rollout, take a database backup and resolve any preflight conflicts.
To recover from a bad deployment, roll back application code while leaving this
migration applied, or restore the verified backup during a controlled
maintenance window. Do not fake-reverse or manually clear role groups.
If an environment already recorded migration 0005 before this remediation,
editing the migration source will not rerun its forward code there. Inspect
that environment's migration history and role-group state during deployment
planning; this work did not access those databases.

## Database concurrency

The configured development database is SQLite. SQLite does not implement
row-level `SELECT FOR UPDATE`; `transaction.atomic()` provides transaction
boundaries, but the active-Super-Admin read/check/write sequence is not a
proven concurrent serialization guarantee there. Do not claim that concurrent
last-admin operations are safe on SQLite.

Production must use a database with effective row locks (for example,
PostgreSQL) and keep all supported Super Admin grant, demotion, deactivation,
reactivation, and deletion operations on the shared locking path. The service
locks the current active Super Admin rows in stable order before the actor and
target rows; the actor must itself be an active Super Admin to change another
Super Admin. Verify this behavior with concurrent integration tests against
the actual production engine and isolation level before deployment. This
remediation does not switch the configured database or add a singleton lock
table.
