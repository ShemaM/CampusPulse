from django.urls import path, include
from rest_framework.routers import DefaultRouter
from rest_framework_simplejwt.views import TokenRefreshView
from .staff_auth import staff_login_view, staff_logout_view
from .views import (
    RegisterView,
    ThrottledTokenObtainPairView,
    UserProfileView,
    ClassScheduleViewSet,
    SchoolViewSet,
    EventViewSet,
    FixtureViewSet,
    TeamViewSet,
    FavoriteTeamListCreateView,
    FavoriteTeamDetailView,
    NotificationViewSet,
    admin_dashboard_view,
    admin_events_management_view,
    admin_event_media_update_view,
    admin_rsvps_ledger_view,
    admin_all_rsvps_csv_view,
    admin_timetable_conflicts_view,
    admin_event_add_view,
    admin_event_edit_view,
    admin_event_delete_view,
    admin_event_rsvps_view,
    admin_event_rsvps_csv_view,
    admin_rsvp_cancel_view,
    admin_users_list_view,
    admin_user_detail_view,
)

router = DefaultRouter()
router.register(r'schools', SchoolViewSet, basename='school')
router.register(r'events', EventViewSet, basename='event')
router.register(r'fixtures', FixtureViewSet, basename='fixture')
router.register(r'teams', TeamViewSet, basename='team')
router.register(r'schedules', ClassScheduleViewSet, basename='schedule')
router.register(r'notifications', NotificationViewSet, basename='notification')

urlpatterns = [
    path('dashboard/login/', staff_login_view, name='staff_login'),
    path('dashboard/logout/', staff_logout_view, name='staff_logout'),
    path('dashboard/', admin_dashboard_view, name='admin_dashboard'),
    path('dashboard/events/', admin_events_management_view, name='admin_events_management'),
    path('dashboard/events/<int:pk>/media/', admin_event_media_update_view, name='admin_event_media_update'),
    path('dashboard/rsvps/', admin_rsvps_ledger_view, name='admin_rsvps_ledger'),
    path('dashboard/rsvps/csv/', admin_all_rsvps_csv_view, name='admin_rsvps_csv'),
    path('dashboard/users/', admin_users_list_view, name='admin_users_list'),
    path('dashboard/users/<int:pk>/', admin_user_detail_view, name='admin_user_detail'),
    path('dashboard/conflicts/', admin_timetable_conflicts_view, name='admin_timetable_conflicts'),
    path('dashboard/events/add/', admin_event_add_view, name='admin_event_add'),
    path('dashboard/events/<int:pk>/edit/', admin_event_edit_view, name='admin_event_edit'),
    path('dashboard/events/<int:pk>/delete/', admin_event_delete_view, name='admin_event_delete'),
    path('dashboard/events/<int:pk>/rsvps/', admin_event_rsvps_view, name='admin_event_rsvps'),
    path('dashboard/events/<int:pk>/rsvps/csv/', admin_event_rsvps_csv_view, name='admin_event_rsvps_csv'),
    path('dashboard/events/<int:pk>/rsvps/<int:rsvp_pk>/cancel/', admin_rsvp_cancel_view, name='admin_rsvp_cancel'),
    path('auth/register/', RegisterView.as_view(), name='register'),
    path('auth/login/', ThrottledTokenObtainPairView.as_view(), name='login'),
    path('auth/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
    path('auth/profile/', UserProfileView.as_view(), name='profile'),
    path('favorites/teams/', FavoriteTeamListCreateView.as_view(), name='favorite-teams'),
    path('favorites/teams/<str:team_id>/', FavoriteTeamDetailView.as_view(), name='favorite-team-detail'),
    path('', include(router.urls)),
]
