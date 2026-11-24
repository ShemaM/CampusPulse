from django.contrib import admin
from django.core.exceptions import PermissionDenied
from django.db import transaction

from .authorization import (
    SUPER_ADMIN,
    _is_super_admin_state,
    get_effective_role,
    has_admin_permission,
)
from .models import User, Event, Fixture, FixtureScorer, School, RSVP, Bookmark, ClassSchedule, Team, FavoriteTeam


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    list_display = ('username', 'email', 'school_code', 'major', 'is_staff')
    list_filter = ('is_staff', 'school_code')
    search_fields = ('username', 'email', 'student_id')
    # Account creation, status, role, password, and permission operations are
    # deliberately not exposed through Django admin. Only profile fields are
    # editable here; privilege changes belong to authorization services.
    fields = ('username', 'first_name', 'last_name', 'email', 'school_code', 'major', 'student_id')

    def has_view_permission(self, request, obj=None):
        return has_admin_permission(request.user, 'view_user')

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        if not has_admin_permission(request.user, 'change_user'):
            return False
        if obj is not None and _is_super_admin_state(obj):
            return get_effective_role(request.user) == SUPER_ADMIN
        return True

    def has_delete_permission(self, request, obj=None):
        # Permanent deletion is available only through delete_account(), which
        # applies role, retention-reference, confirmation, and last-admin checks.
        return False

    def save_model(self, request, obj, form, change):
        if not change or not obj.pk:
            raise PermissionDenied('User creation is not available in Django admin.')
        protected_post_fields = {
            'is_active', 'is_staff', 'is_superuser', 'groups', 'user_permissions',
        }
        if protected_post_fields.intersection(request.POST):
            raise PermissionDenied('Account status and privileges require authorization services.')
        if {'groups', 'user_permissions'} & set(form.cleaned_data):
            raise PermissionDenied('Groups and permissions require authorization services.')
        with transaction.atomic():
            original = User.objects.select_for_update().get(pk=obj.pk)
            if not self.has_change_permission(request, original):
                raise PermissionDenied
            protected_fields = ('is_active', 'is_staff', 'is_superuser')
            if any(getattr(original, field) != getattr(obj, field) for field in protected_fields):
                raise PermissionDenied('Account status and staff privileges require authorization services.')
            # Persist only explicit profile changes so stale model fields can
            # never overwrite concurrently changed privilege state.
            changed_fields = list(form.changed_data)
            if changed_fields:
                obj.save(update_fields=changed_fields)

    def save_related(self, request, form, formsets, change):
        if {'groups', 'user_permissions'} & set(form.cleaned_data):
            raise PermissionDenied('Groups and permissions require authorization services.')
        super().save_related(request, form, formsets, change)


@admin.register(School)
class SchoolAdmin(admin.ModelAdmin):
    list_display = ('code', 'name')
    search_fields = ('code', 'name')


@admin.register(Event)
class EventAdmin(admin.ModelAdmin):
    list_display = ('title', 'event_type', 'school_code', 'venue', 'start_time', 'seats_left', 'total_seats', 'has_image', 'has_video')
    list_filter = ('event_type', 'school_code')
    search_fields = ('title', 'venue', 'organizer')
    fields = (
        'title', 'description', 'school_code', 'major', 'event_type',
        'start_time', 'end_time', 'venue', 'total_seats', 'seats_left',
        'organizer', 'image', 'image_url', 'video'
    )

    def has_image(self, obj):
        return bool(obj.image or obj.image_url)
    has_image.boolean = True
    has_image.short_description = 'Image'

    def has_video(self, obj):
        return bool(obj.video)
    has_video.boolean = True
    has_video.short_description = 'Video'


class FixtureScorerInline(admin.TabularInline):
    model = FixtureScorer
    extra = 0
    fields = ('player_name', 'minute', 'added_minute')


@admin.register(Fixture)
class FixtureAdmin(admin.ModelAdmin):
    list_display = ('team_name', 'sport', 'opponent', 'start_time', 'venue', 'is_home')
    list_filter = ('sport', 'is_home')
    search_fields = ('team_name', 'opponent', 'venue')
    inlines = (FixtureScorerInline,)


@admin.register(Team)
class TeamAdmin(admin.ModelAdmin):
    list_display = ('team_id', 'name', 'sport', 'competition')
    list_filter = ('sport', 'competition')
    search_fields = ('team_id', 'name')


@admin.register(FavoriteTeam)
class FavoriteTeamAdmin(admin.ModelAdmin):
    list_display = ('user', 'team', 'created_at')
    list_filter = ('team__sport', 'created_at')
    search_fields = ('user__username', 'team__team_id', 'team__name')


@admin.register(RSVP)
class RSVPAdmin(admin.ModelAdmin):
    list_display = ('event', 'user', 'guest_email', 'status', 'timestamp')
    list_filter = ('status', 'timestamp', 'event')
    search_fields = ('user__username', 'guest_email', 'event__title')
    readonly_fields = ('user', 'event', 'status', 'guest_email', 'guest_student_id', 'notes', 'timestamp')


@admin.register(Bookmark)
class BookmarkAdmin(admin.ModelAdmin):
    list_display = ('user', 'event', 'timestamp')
    list_filter = ('timestamp',)
    search_fields = ('user__username', 'event__title')


@admin.register(ClassSchedule)
class ClassScheduleAdmin(admin.ModelAdmin):
    list_display = ('user', 'course_name', 'day_of_week', 'start_time', 'end_time')
    list_filter = ('day_of_week',)
    search_fields = ('user__username', 'course_name')
