from django.db import migrations


# Frozen migration mapping. Do not import current models or authorization
# constants here: historical migrations must remain stable over time.
ROLE_PERMISSIONS = {
    'SUPER_ADMIN': (),  # Full authority is supplied by validated superuser flags.
    'ADMIN': (
        'user.view_dashboard', 'user.view_user', 'user.add_user',
        'user.change_user', 'user.change_user_status', 'user.assign_staff_roles',
        'user.view_sensitive_user_data', 'user.reset_user_password',
        'event.view_event', 'event.add_event', 'event.change_event',
        'event.archive_event', 'event.manage_event_media', 'rsvp.view_rsvp',
        'rsvp.view_rsvp_aggregate', 'rsvp.view_rsvp_pii', 'rsvp.export_rsvps',
        'rsvp.cancel_rsvp', 'school.view_school', 'school.add_school',
        'school.change_school', 'school.delete_school', 'fixture.view_fixture',
        'fixture.add_fixture', 'fixture.change_fixture', 'fixture.delete_fixture',
        'classschedule.view_classschedule', 'classschedule.add_classschedule',
        'classschedule.change_classschedule', 'classschedule.delete_classschedule',
        'classschedule.view_timetable_conflicts', 'classschedule.manage_class_schedules',
        'bookmark.view_bookmark', 'bookmark.add_bookmark',
        'bookmark.change_bookmark', 'bookmark.delete_bookmark',
    ),
    'CONTENT_MANAGER': (
        'user.view_dashboard', 'event.view_event', 'event.add_event',
        'event.change_event', 'event.manage_event_media', 'rsvp.view_rsvp_aggregate',
    ),
    'READ_ONLY_STAFF': (
        'user.view_dashboard', 'event.view_event', 'rsvp.view_rsvp_aggregate',
        'school.view_school', 'fixture.view_fixture',
        'classschedule.view_timetable_conflicts',
    ),
}

ALL_PERMISSION_KEYS = frozenset(
    permission for role_permissions in ROLE_PERMISSIONS.values()
    for permission in role_permissions
) | frozenset({
    'user.grant_super_admin', 'user.delete_user', 'event.delete_event',
    'rsvp.add_rsvp', 'rsvp.change_rsvp', 'rsvp.delete_rsvp',
})


def create_roles_and_permissions(apps, schema_editor):
    db_alias = schema_editor.connection.alias
    Group = apps.get_model('auth', 'Group')
    Permission = apps.get_model('auth', 'Permission')
    ContentType = apps.get_model('contenttypes', 'ContentType')
    User = apps.get_model('api', 'User')

    existing_group_names = set(
        Group.objects.using(db_alias).values_list('name', flat=True)
    )

    active_superuser_ids = set(User.objects.using(db_alias).filter(
        is_active=True, is_staff=True, is_superuser=True
    ).values_list('pk', flat=True))

    # Preflight every existing reserved group before creating or changing any
    # role data. Conflicts abort with no partial role-group modifications.
    for role_name, permission_keys in ROLE_PERMISSIONS.items():
        if role_name not in existing_group_names:
            continue
        group = Group.objects.using(db_alias).get(name=role_name)
        expected_members = active_superuser_ids if role_name == 'SUPER_ADMIN' else set()
        actual_members = set(group.user_set.using(db_alias).values_list('pk', flat=True))
        if actual_members - expected_members:
            raise RuntimeError(
                f'Reserved role Group {role_name!r} has existing user memberships; '
                'review and reconcile them before applying this migration.'
            )
        expected_permissions = {
            tuple(permission_key.split('.', 1))
            for permission_key in permission_keys
        }
        actual_permissions = set(group.permissions.using(db_alias).values_list(
            'content_type__model', 'codename'
        ))
        if actual_permissions - expected_permissions:
            raise RuntimeError(
                f'Reserved role Group {role_name!r} has existing permissions outside '
                f'the approved role mapping: {sorted(actual_permissions - expected_permissions)!r}; '
                'review and reconcile them before applying this migration.'
            )

    groups = {}
    for role_name in ROLE_PERMISSIONS:
        groups[role_name], _ = Group.objects.using(db_alias).get_or_create(name=role_name)

    permissions = {}
    for key in ALL_PERMISSION_KEYS:
        model_name, codename = key.split('.', 1)
        content_type, _ = ContentType.objects.using(db_alias).get_or_create(
            app_label='api', model=model_name
        )
        permission, _ = Permission.objects.using(db_alias).get_or_create(
            content_type=content_type,
            codename=codename,
            defaults={'name': f'Can {codename.replace("_", " ")}'},
        )
        permissions[key] = permission

    # Never erase data from a pre-existing role-named Group. Unexpected
    # memberships or permissions indicate a conflict that requires explicit
    # operator review; the enclosing migration transaction will roll back.
    for role_name, permission_keys in ROLE_PERMISSIONS.items():
        group = groups[role_name]
        group.permissions.add(*[permissions[key] for key in permission_keys])

    # Only already-active staff superusers receive the explicit role label.
    # Other existing staff remain unassigned for manual review.
    super_admin_group = groups['SUPER_ADMIN']
    active_superusers = User.objects.using(db_alias).filter(pk__in=active_superuser_ids)
    super_admin_group.user_set.add(*active_superusers)


class Migration(migrations.Migration):
    dependencies = [
        ('api', '0004_event_video'),
        ('auth', '0012_alter_user_first_name_max_length'),
    ]

    operations = [
        migrations.AlterModelOptions(
            name='user',
            options={
                'permissions': [
                    ('view_dashboard', 'Can view the administration dashboard'),
                    ('change_user_status', 'Can activate or deactivate user accounts'),
                    ('assign_staff_roles', 'Can assign non-super-admin staff roles'),
                    ('grant_super_admin', 'Can grant or revoke Super Admin role'),
                    ('view_sensitive_user_data', 'Can view sensitive user data'),
                    ('reset_user_password', 'Can initiate user password resets'),
                ],
            },
        ),
        migrations.AlterModelOptions(
            name='classschedule',
            options={
                'ordering': ['day_of_week', 'start_time'],
                'permissions': [
                    ('view_timetable_conflicts', 'Can view timetable conflicts'),
                    ('manage_class_schedules', 'Can manage class schedules'),
                ],
            },
        ),
        migrations.AlterModelOptions(
            name='event',
            options={
                'ordering': ['start_time'],
                'permissions': [
                    ('archive_event', 'Can archive events'),
                    ('manage_event_media', 'Can manage event media'),
                ],
            },
        ),
        migrations.AlterModelOptions(
            name='rsvp',
            options={
                'permissions': [
                    ('view_rsvp_aggregate', 'Can view aggregate RSVP information'),
                    ('view_rsvp_pii', 'Can view personally identifiable RSVP data'),
                    ('export_rsvps', 'Can export RSVP data'),
                    ('cancel_rsvp', 'Can cancel an RSVP administratively'),
                ],
            },
        ),
        # Forward-only by design: rolling back would otherwise leave role
        # memberships/permissions behind or risk deleting later assignments.
        # Recovery is documented; Django must refuse an unsafe reverse.
        migrations.RunPython(create_roles_and_permissions),
    ]
