import csv
from datetime import date, datetime as dt
from django.db import IntegrityError, transaction
from django.db.models import Exists, F, OuterRef, Q
from django.db.models.functions import Least
from django.http import HttpResponse, JsonResponse
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone
from rest_framework import generics, permissions, status, viewsets
from rest_framework.exceptions import ValidationError
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.throttling import AnonRateThrottle, ScopedRateThrottle, UserRateThrottle
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenObtainPairView

from .forms import EventAdminForm, EventMediaAdminForm
from .staff_auth import staff_required
from .models import Bookmark, ClassSchedule, Event, FavoriteTeam, Fixture, RSVP, School, Team, User, Notification
from .serializers import (
    ClassScheduleSerializer,
    EventSerializer,
    FixtureSerializer,
    TeamSerializer,
    RSVPRequestSerializer,
    RegisterSerializer,
    SchoolSerializer,
    UserSerializer,
    NotificationSerializer,
)
from .sports import build_standings, fixture_queryset_for_team


class IsAdminOrReadOnly(permissions.BasePermission):
    def has_permission(self, request, view):
        if request.method in permissions.SAFE_METHODS:
            return True
        return bool(request.user and request.user.is_authenticated and request.user.is_staff)


class ThrottledTokenObtainPairView(TokenObtainPairView):
    authentication_classes = []
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = 'auth'


@staff_required
def admin_dashboard_view(request):
    events = Event.objects.all().order_by('-start_time')

    fixtures_count = Fixture.objects.count()
    users_count = User.objects.count()
    schools_count = School.objects.count()

    upcoming_events_count = Event.objects.filter(
        start_time__gte=timezone.now()
    ).count()

    confirmed_rsvps_count = RSVP.objects.filter(
        status='CONFIRMED'
    ).count()

    context = {
        'events': events,
        'events_count': events.count(),
        'upcoming_events_count': upcoming_events_count,
        'confirmed_rsvps_count': confirmed_rsvps_count,
        'fixtures_count': fixtures_count,
        'users_count': users_count,
        'schools_count': schools_count,
        'active_page': 'overview',
    }

    return render(request, 'api/admin_dashboard.html', context)
@staff_required
def admin_events_management_view(request):
    events = Event.objects.all().order_by('-start_time')
    missing_media_events = [event for event in events if not (event.image or event.image_url) or not event.video]
    missing_image_count = sum(1 for event in events if not (event.image or event.image_url))
    missing_video_count = sum(1 for event in events if not event.video)
    return render(request, 'api/admin_events_management.html', {
        'events': events,
        'missing_media_events': missing_media_events,
        'missing_image_count': missing_image_count,
        'missing_video_count': missing_video_count,
        'missing_media_count': len(missing_media_events),
    })


@staff_required
def admin_event_media_update_view(request, pk):
    if request.method != 'POST':
        return JsonResponse({'ok': False, 'error': 'Use POST to update event media.'}, status=405)
    event = get_object_or_404(Event, pk=pk)
    form = EventMediaAdminForm(request.POST, request.FILES, instance=event)
    if not form.is_valid():
        errors = {key: [str(error) for error in values] for key, values in form.errors.items()}
        return JsonResponse({'ok': False, 'errors': errors}, status=400)

    has_new_image = bool(request.FILES.get('image'))
    new_image_url = form.cleaned_data.get('image_url')
    clear_image = form.cleaned_data.get('clear_image')
    has_new_video = bool(request.FILES.get('video'))
    clear_video = form.cleaned_data.get('clear_video')
    event = form.save(commit=False)

    if has_new_image:
        event.image_url = None
    elif clear_image:
        if event.image:
            event.image.delete(save=False)
            event.image = None
        event.image_url = None
    elif new_image_url:
        if event.image:
            event.image.delete(save=False)
            event.image = None

    if clear_video and not has_new_video:
        if event.video:
            event.video.delete(save=False)
            event.video = None

    event.save(update_fields=['image', 'image_url', 'video'])
    all_events = Event.objects.all()
    missing_image_count = sum(1 for item in all_events if not (item.image or item.image_url))
    missing_video_count = sum(1 for item in all_events if not item.video)
    missing_media_count = sum(1 for item in all_events if not (item.image or item.image_url) or not item.video)
    return JsonResponse({
        'ok': True,
        'event_id': event.pk,
        'image_complete': bool(event.image or event.image_url),
        'video_complete': bool(event.video),
        'complete': bool(event.image or event.image_url) and bool(event.video),
        'image_url': event.image.url if event.image else (event.image_url or ''),
        'video_url': event.video.url if event.video else '',
        'missing_image_count': missing_image_count,
        'missing_video_count': missing_video_count,
        'missing_media_count': missing_media_count,
    })


@staff_required
def admin_rsvps_ledger_view(request):
    rsvps = RSVP.objects.select_related('user', 'event').all().order_by('-timestamp')
    events = Event.objects.all().order_by('title')
    confirmed_count = rsvps.filter(status='CONFIRMED').count()
    cancelled_count = rsvps.filter(status='CANCELLED').count()
    waitlisted_count = rsvps.filter(status='WAITLISTED').count()

    context = {
        'rsvps': rsvps,
        'total_rsvps': rsvps.count(),
        'confirmed_count': confirmed_count,
        'cancelled_count': cancelled_count,
        'waitlisted_count': waitlisted_count,
        'events': events,
        'events_count': events.count(),
        'active_page': 'rsvps',
    }
    return render(request, 'api/admin_attendees_ledger.html', context)


@staff_required
def admin_all_rsvps_csv_view(request):
    rsvps = RSVP.objects.select_related('user', 'event').order_by('-timestamp')
    response = HttpResponse(content_type='text/csv')
    filename = f"all_rsvps_ledger_{timezone.now():%Y%m%d_%H%M}.csv"
    response['Content-Disposition'] = f'attachment; filename="{filename}"'

    writer = csv.writer(response)
    writer.writerow(['Pass Code', 'Attendee Name', 'Student ID', 'Email', 'School', 'Major', 'Event Title', 'Venue', 'Status', 'Notes', 'Timestamp'])

    def sanitize(val):
        s = str(val or '')
        if s and s[0] in ('=', '+', '-', '@'):
            return "'" + s
        return s

    for r in rsvps:
        pass_code = f"#USIU-{r.pk:04d}"
        name = r.user.get_full_name() or r.user.username if r.user else (r.guest_email or 'Guest')
        email = r.user.email if r.user else (r.guest_email or '')
        sid = r.user.student_id if (r.user and r.user.student_id) else (r.guest_student_id or '')
        school = r.user.school_code if (r.user and r.user.school_code) else ''
        major = r.user.major if (r.user and r.user.major) else ''
        event_title = r.event.title if r.event else ''
        venue = r.event.venue if r.event else ''

        writer.writerow([
            sanitize(pass_code),
            sanitize(name),
            sanitize(sid),
            sanitize(email),
            sanitize(school),
            sanitize(major),
            sanitize(event_title),
            sanitize(venue),
            sanitize(r.status),
            sanitize(r.notes),
            r.timestamp.strftime('%Y-%m-%d %H:%M')
        ])

    return response


@staff_required
def admin_timetable_conflicts_view(request):
    return render(request, 'api/admin_timetable_conflicts.html', {})


@staff_required
def admin_users_list_view(request):
    users = User.objects.all().order_by('-date_joined')
    
    q = request.GET.get('q', '').strip()
    if q:
        users = users.filter(
            Q(username__icontains=q) |
            Q(first_name__icontains=q) |
            Q(last_name__icontains=q) |
            Q(email__icontains=q) |
            Q(student_id__icontains=q)
        )
    
    role = request.GET.get('role', '').strip()
    if role == 'admin':
        users = users.filter(is_staff=True)
    elif role == 'student':
        users = users.filter(is_staff=False)
        
    status_filter = request.GET.get('status', '').strip()
    if status_filter == 'active':
        users = users.filter(is_active=True)
    elif status_filter == 'inactive':
        users = users.filter(is_active=False)

    total_count = User.objects.count()
    active_count = User.objects.filter(is_active=True).count()
    admin_count = User.objects.filter(is_staff=True).count()
    student_count = User.objects.filter(is_staff=False).count()

    context = {
        'users': users,
        'total_count': total_count,
        'active_count': active_count,
        'admin_count': admin_count,
        'student_count': student_count,
        'search_query': q,
        'current_role': role,
        'current_status': status_filter,
    }
    return render(request, 'api/admin_users_list.html', context)


@staff_required
def admin_user_detail_view(request, pk):
    target_user = get_object_or_404(User, pk=pk)
    rsvps = RSVP.objects.filter(user=target_user).select_related('event').order_by('-timestamp')
    bookmarks = Bookmark.objects.filter(user=target_user).select_related('event').order_by('-timestamp')
    schedules = ClassSchedule.objects.filter(user=target_user).order_by('day_of_week', 'start_time')

    context = {
        'target_user': target_user,
        'rsvps': rsvps,
        'bookmarks': bookmarks,
        'schedules': schedules,
    }
    return render(request, 'api/admin_user_detail.html', context)


@staff_required
def admin_event_add_view(request):
    schools = School.objects.all()
    if request.method == 'POST':
        form = EventAdminForm(request.POST, request.FILES)
        if form.is_valid():
            form.save()
            return redirect('/api/dashboard/')
    else:
        form = EventAdminForm()
    return render(request, 'api/event_form.html', {'form': form, 'schools': schools})


@staff_required
def admin_event_edit_view(request, pk):
    event = get_object_or_404(Event, pk=pk)
    schools = School.objects.all()
    if request.method == 'POST':
        form = EventAdminForm(request.POST, request.FILES, instance=event)
        if form.is_valid():
            form.save()
            return redirect('/api/dashboard/')
    else:
        form = EventAdminForm(instance=event)
    return render(
        request, 'api/event_form.html', {'form': form, 'event': event, 'schools': schools}
    )


@staff_required
def admin_event_delete_view(request, pk):
    event = get_object_or_404(Event, pk=pk)
    if request.method == 'POST':
        event.delete()
        return redirect('/api/dashboard/')
    return render(request, 'api/event_confirm_delete.html', {'event': event})


def cancel_rsvp_for_event(event, rsvp):
    if rsvp and rsvp.status == RSVP.Status.CONFIRMED:
        rsvp.status = RSVP.Status.CANCELLED
        rsvp.save(update_fields=['status', 'updated_at'])
        if event.total_seats is not None:
            Event.objects.filter(pk=event.pk).update(
                seats_left=Least(F('total_seats'), F('seats_left') + 1)
            )
            event.refresh_from_db(fields=['seats_left'])
        return True
    return False

@staff_required
def admin_event_rsvps_view(request, pk):
    event = get_object_or_404(Event, pk=pk)
    rsvps = RSVP.objects.filter(event=event).select_related('user').order_by('-timestamp')
    return render(request, 'api/event_rsvps.html', {'event': event, 'rsvps': rsvps})


@staff_required
def admin_event_rsvps_csv_view(request, pk):
    event = get_object_or_404(Event, pk=pk)
    rsvps = RSVP.objects.filter(event=event).select_related('user').order_by('-timestamp')

    response = HttpResponse(content_type='text/csv')
    filename = f"rsvps_event_{event.pk}_{timezone.now():%Y%m%d}.csv"
    response['Content-Disposition'] = f'attachment; filename="{filename}"'

    writer = csv.writer(response)
    writer.writerow(['Attendee Name/Email', 'Student ID', 'Status', 'Notes', 'Timestamp'])

    def sanitize(val):
        s = str(val or '')
        if s and s[0] in ('=', '+', '-', '@'):
            return "'" + s
        return s

    for r in rsvps:
        name = r.user.username if r.user else (r.guest_email or 'Guest')
        email = r.user.email if r.user else ''
        identifier = f"{name} ({email})" if email else name
        sid = r.user.student_id if (r.user and r.user.student_id) else (r.guest_student_id or '')

        writer.writerow([
            sanitize(identifier),
            sanitize(sid),
            sanitize(r.status),
            sanitize(r.notes),
            r.timestamp.strftime('%Y-%m-%d %H:%M')
        ])
    return response


@staff_required
def admin_rsvp_cancel_view(request, pk, rsvp_pk):
    if request.method != 'POST':
        return redirect(f'/api/dashboard/events/{pk}/rsvps/')

    event = get_object_or_404(Event, pk=pk)
    rsvp = get_object_or_404(RSVP, pk=rsvp_pk, event=event)

    with transaction.atomic():
        locked_event = Event.objects.select_for_update().get(pk=event.pk)
        locked_rsvp = RSVP.objects.select_for_update().get(pk=rsvp.pk)
        cancel_rsvp_for_event(locked_event, locked_rsvp)

    return redirect(f'/api/dashboard/events/{pk}/rsvps/')


class RegisterView(generics.CreateAPIView):
    queryset = User.objects.all()
    permission_classes = (permissions.AllowAny,)
    authentication_classes = []
    serializer_class = RegisterSerializer
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = 'auth'

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        refresh = RefreshToken.for_user(user)
        return Response(
            {
                'user': UserSerializer(user).data,
                'refresh': str(refresh),
                'access': str(refresh.access_token),
            },
            status=status.HTTP_201_CREATED,
        )


class UserProfileView(generics.RetrieveUpdateAPIView):
    permission_classes = (permissions.IsAuthenticated,)
    serializer_class = UserSerializer

    def get_object(self):
        return User.objects.prefetch_related('schedules').get(pk=self.request.user.pk)


class ClassScheduleViewSet(viewsets.ModelViewSet):
    serializer_class = ClassScheduleSerializer
    permission_classes = (permissions.IsAuthenticated,)

    def get_queryset(self):
        return ClassSchedule.objects.filter(user=self.request.user).order_by('day_of_week', 'start_time')

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)

    def create(self, request, *args, **kwargs):
        many = isinstance(request.data, list)
        serializer = self.get_serializer(data=request.data, many=many)
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            serializer.save(user=request.user)
        return Response(serializer.data, status=status.HTTP_201_CREATED)

    def perform_destroy(self, instance):
        # A paired MW/TR course is one registration; deleting either side removes its partner.
        partner_days = {'MON':'WED','WED':'MON','TUE':'THU','THU':'TUE'}
        partner = partner_days.get(instance.day_of_week)
        if partner:
            ClassSchedule.objects.filter(user=instance.user, course_name=instance.course_name,
                day_of_week=partner, start_time=instance.start_time, end_time=instance.end_time).delete()
        instance.delete()

    @action(detail=False, methods=['post'])
    def bulk_sync(self, request):
        if not isinstance(request.data, list):
            return Response(
                {'error': 'Expected a JSON list of class schedule entries.'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        serializer = self.get_serializer(data=request.data, many=True)
        serializer.is_valid(raise_exception=True)
        with transaction.atomic():
            ClassSchedule.objects.filter(user=self.request.user).delete()
            serializer.save(user=self.request.user)
        return Response(self.get_serializer(self.get_queryset(), many=True).data, status=status.HTTP_200_OK)


class SchoolViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = School.objects.all()
    serializer_class = SchoolSerializer
    permission_classes = (permissions.AllowAny,)
    pagination_class = None


def _check_class_schedule_conflict(user, event):
    local_start = timezone.localtime(event.start_time)
    local_end = timezone.localtime(event.end_time)
    weekday_map = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
    event_day = weekday_map[local_start.weekday()]
    ev_start_time = local_start.time()
    ev_end_time = local_end.time()

    conflicting_class = ClassSchedule.objects.filter(
        user=user,
        day_of_week=event_day,
        start_time__lt=ev_end_time,
        end_time__gt=ev_start_time,
    ).first()
    return conflicting_class


class EventViewSet(viewsets.ModelViewSet):
    serializer_class = EventSerializer
    permission_classes = (IsAdminOrReadOnly,)

    def get_object(self):
        lookup = self.kwargs.get(self.lookup_field)
        qs = self.filter_queryset(self.get_queryset())
        if lookup is not None and not str(lookup).isdigit():
            obj = get_object_or_404(qs, event_id=lookup)
            self.check_object_permissions(self.request, obj)
            return obj
        return super().get_object()

    def get_queryset(self):
        qs = Event.objects.all().order_by('start_time')
        user = self.request.user
        if user and user.is_authenticated:
            qs = qs.annotate(
                _is_saved=Exists(Bookmark.objects.filter(user=user, event=OuterRef('pk'))),
                _is_rsvped=Exists(
                    RSVP.objects.filter(user=user, event=OuterRef('pk'), status=RSVP.Status.CONFIRMED)
                ),
            )

        params = self.request.query_params
        school_code = params.get('school_code')
        major = params.get('major')
        event_type = params.get('event_type')
        upcoming_only = params.get('upcoming')

        if school_code:
            qs = qs.filter(Q(school_code__isnull=True) | Q(school_code=school_code))
        if major:
            qs = qs.filter(Q(major__isnull=True) | Q(major=major))
        if event_type:
            qs = qs.filter(event_type__iexact=event_type)
        if upcoming_only and upcoming_only.lower() in ('1', 'true', 'yes'):
            qs = qs.filter(end_time__gte=timezone.now())

        return qs

    @action(
        detail=True,
        methods=['post', 'delete'],
        permission_classes=[permissions.AllowAny],
        throttle_classes=[ScopedRateThrottle],
    )
    def rsvp(self, request, pk=None):
        target = self.get_object()
        if target.end_time < timezone.now():
            return Response(
                {'error': 'Event has already ended.'},
                status=status.HTTP_400_BAD_REQUEST,
            )

        req_serializer = RSVPRequestSerializer(data=request.data if isinstance(request.data, dict) else {})
        req_serializer.is_valid(raise_exception=True)
        payload = req_serializer.validated_data

        user = request.user if request.user.is_authenticated else None
        guest_email = (payload.get('email') or '').strip().lower() or None
        guest_student_id = (payload.get('student_id') or '').strip() or None
        notes = (payload.get('notes') or '').strip() or None

        if not user and not guest_email:
            return Response(
                {'error': 'Authentication or a valid guest email is required to RSVP.'},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        with transaction.atomic():
            event = Event.objects.select_for_update().get(pk=target.pk)

            if user:
                existing_rsvp = RSVP.objects.select_for_update().filter(user=user, event=event).first()
            else:
                existing_rsvp = RSVP.objects.select_for_update().filter(
                    user__isnull=True, guest_email__iexact=guest_email, event=event
                ).first()

            if request.method == 'DELETE':
                cancel_rsvp_for_event(event, existing_rsvp)
                return Response(
                    {'status': 'unrsvped', 'seats_left': event.seats_left},
                    status=status.HTTP_200_OK,
                )

            # POST: idempotent confirm
            if existing_rsvp and existing_rsvp.status == RSVP.Status.CONFIRMED:
                return Response(
                    {'status': 'rsvped', 'seats_left': event.seats_left},
                    status=status.HTTP_200_OK,
                )

            if user:
                conflicting_rsvp = (
                    RSVP.objects.filter(user=user, status=RSVP.Status.CONFIRMED)
                    .exclude(event=event)
                    .select_related('event')
                    .filter(event__start_time__lt=event.end_time, event__end_time__gt=event.start_time)
                    .first()
                )
                if conflicting_rsvp:
                    return Response(
                        {
                            'error': f"Time conflict with another event: '{conflicting_rsvp.event.title}'."
                        },
                        status=status.HTTP_400_BAD_REQUEST,
                    )

                conflicting_class = _check_class_schedule_conflict(user, event)
                if conflicting_class:
                    return Response(
                        {
                            'error': f"Time conflict with your class: '{conflicting_class.course_name}' ({conflicting_class.day_of_week} {conflicting_class.start_time:%H:%M}-{conflicting_class.end_time:%H:%M})."
                        },
                        status=status.HTTP_400_BAD_REQUEST,
                    )

            if event.total_seats is not None:
                updated = Event.objects.filter(pk=event.pk, seats_left__gt=0).update(
                    seats_left=F('seats_left') - 1
                )
                if not updated:
                    return Response({'error': 'Event is full!'}, status=status.HTTP_400_BAD_REQUEST)

            try:
                if existing_rsvp:
                    existing_rsvp.status = RSVP.Status.CONFIRMED
                    if notes is not None:
                        existing_rsvp.notes = notes
                    existing_rsvp.save(update_fields=['status', 'notes', 'updated_at'])
                else:
                    RSVP.objects.create(
                        user=user,
                        event=event,
                        status=RSVP.Status.CONFIRMED,
                        guest_email=None if user else guest_email,
                        guest_student_id=None if user else guest_student_id,
                        notes=notes,
                    )
            except IntegrityError:
                if event.total_seats is not None:
                    Event.objects.filter(pk=event.pk).update(
                        seats_left=Least(F('total_seats'), F('seats_left') + 1)
                    )
                    event.refresh_from_db(fields=['seats_left'])
                return Response(
                    {'status': 'rsvped', 'seats_left': event.seats_left},
                    status=status.HTTP_200_OK,
                )

            event.refresh_from_db(fields=['seats_left'])
            return Response(
                {'status': 'rsvped', 'seats_left': event.seats_left},
                status=status.HTTP_201_CREATED,
            )

    @action(detail=True, methods=['post', 'delete'], permission_classes=[permissions.IsAuthenticated])
    def bookmark(self, request, pk=None):
        event = self.get_object()
        user = request.user
        if request.method == 'DELETE':
            Bookmark.objects.filter(user=user, event=event).delete()
            return Response({'status': 'unbookmarked'}, status=status.HTTP_200_OK)

        bm_obj, created = Bookmark.objects.get_or_create(user=user, event=event)
        if created:
            return Response({'status': 'bookmarked'}, status=status.HTTP_201_CREATED)
        bm_obj.delete()
        return Response({'status': 'unbookmarked'}, status=status.HTTP_200_OK)


class FixtureViewSet(viewsets.ModelViewSet):
    queryset = Fixture.objects.all().order_by('start_time')
    serializer_class = FixtureSerializer
    permission_classes = (IsAdminOrReadOnly,)

    def get_object(self):
        lookup = self.kwargs.get(self.lookup_field)
        qs = self.filter_queryset(self.get_queryset())
        if lookup is not None and not str(lookup).isdigit():
            obj = get_object_or_404(qs, fixture_id=lookup)
            self.check_object_permissions(self.request, obj)
            return obj
        return super().get_object()

    FILTER_PARAMS = {
        'sport', 'team', 'competition', 'league', 'status', 'date_from',
        'date_to', 'home', 'venue', 'search', 'q',
    }

    def get_queryset(self):
        params = self.request.query_params
        unknown = set(params) - self.FILTER_PARAMS
        if unknown:
            raise ValidationError({'query': f"Unsupported parameter(s): {', '.join(sorted(unknown))}"})

        queryset = Fixture.objects.all().prefetch_related('scorers').order_by('start_time', 'id')
        if params.get('sport'):
            queryset = queryset.filter(sport__iexact=params['sport'].strip())
        if params.get('team'):
            team = params['team'].strip()
            queryset = queryset.filter(
                Q(team_id__iexact=team) | Q(team_name__iexact=team) | Q(opponent__iexact=team)
            )
        competition = params.get('competition') or params.get('league')
        if competition:
            queryset = queryset.filter(league__icontains=competition.strip())
        if params.get('venue'):
            queryset = queryset.filter(venue__icontains=params['venue'].strip())
        search = params.get('search') or params.get('q')
        if search:
            queryset = queryset.filter(
                Q(team_name__icontains=search.strip()) | Q(opponent__icontains=search.strip())
            )
        if params.get('home'):
            home = params['home'].strip().lower()
            if home not in {'home', 'away', 'true', 'false'}:
                raise ValidationError({'home': 'Use home, away, true, or false.'})
            queryset = queryset.filter(is_home=home in {'home', 'true'})

        parsed_dates = {}
        for key in ('date_from', 'date_to'):
            value = params.get(key)
            if value:
                try:
                    parsed_dates[key] = date.fromisoformat(value)
                except ValueError:
                    raise ValidationError({key: 'Use ISO date format YYYY-MM-DD.'})
        if parsed_dates.get('date_from') and parsed_dates.get('date_to'):
            if parsed_dates['date_from'] > parsed_dates['date_to']:
                raise ValidationError({'date_to': 'Must be on or after date_from.'})
        if 'date_from' in parsed_dates:
            queryset = queryset.filter(start_time__date__gte=parsed_dates['date_from'])
        if 'date_to' in parsed_dates:
            queryset = queryset.filter(start_time__date__lte=parsed_dates['date_to'])

        match_status = params.get('status', '').strip().lower()
        if match_status not in {'', 'upcoming', 'completed'}:
            raise ValidationError({'status': 'Use upcoming or completed.'})
        if match_status == 'completed':
            queryset = queryset.filter(
                end_time__lte=timezone.now(),
                score_usiu__isnull=False,
                score_opponent__isnull=False,
            )
        elif match_status == 'upcoming':
            queryset = queryset.exclude(
                end_time__lte=timezone.now(),
                score_usiu__isnull=False,
                score_opponent__isnull=False,
            )
        return queryset


class TeamViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = Team.objects.all()
    serializer_class = TeamSerializer
    permission_classes = (permissions.AllowAny,)
    lookup_field = 'team_id'
    lookup_value_regex = '[^/]+'

    @action(detail=True, methods=['get'])
    def fixtures(self, request, team_id=None):
        team = self.get_object()
        rows = fixture_queryset_for_team(team).prefetch_related('scorers').order_by('start_time', 'id')
        view = request.query_params.get('status', '').strip().lower()
        if view not in {'', 'all', 'upcoming', 'completed'}:
            raise ValidationError({'status': 'Use all, upcoming, or completed.'})
        if view == 'completed':
            rows = rows.filter(
                end_time__lte=timezone.now(),
                score_usiu__isnull=False,
                score_opponent__isnull=False,
            )
        elif view == 'upcoming':
            rows = rows.exclude(
                end_time__lte=timezone.now(),
                score_usiu__isnull=False,
                score_opponent__isnull=False,
            )
        return Response(FixtureSerializer(rows, many=True).data)

    @action(detail=True, methods=['get'])
    def standings(self, request, team_id=None):
        team = self.get_object()
        rows = build_standings(team)
        rules = (
            'Football points: win 3, draw 1, loss 0; sorted by points, goal difference, goals for, then team name.'
            if team.sport.casefold() == 'football'
            else 'DEMONSTRATION ONLY: illustrative 3/1/0 points; competition rules are unavailable. '
                 'Sorted by points, score difference, points for, then team name.'
        )
        return Response({
            'sport': team.sport,
            'competition': team.competition,
            'is_demo': 'demo' in team.competition.lower() or team.sport.casefold() != 'football',
            'rules_label': rules,
            'standings': rows,
        })


class FavoriteTeamListCreateView(generics.GenericAPIView):
    permission_classes = [permissions.IsAuthenticated]
    serializer_class = TeamSerializer

    def get(self, request):
        teams = Team.objects.filter(followers__user=request.user).order_by('sport', 'name')
        return Response(TeamSerializer(teams, many=True).data)

    def post(self, request):
        team_id = request.data.get('team_id')
        if not isinstance(team_id, str) or not team_id.strip():
            raise ValidationError({'team_id': 'A team_id is required.'})
        team = get_object_or_404(Team, team_id=team_id.strip())
        FavoriteTeam.objects.get_or_create(user=request.user, team=team)
        return Response(TeamSerializer(team).data, status=status.HTTP_200_OK)


class FavoriteTeamDetailView(generics.GenericAPIView):
    permission_classes = [permissions.IsAuthenticated]

    def delete(self, request, team_id):
        FavoriteTeam.objects.filter(user=request.user, team__team_id=team_id).delete()
        return Response(status=status.HTTP_204_NO_CONTENT)


class NotificationViewSet(viewsets.ModelViewSet):
    serializer_class = NotificationSerializer
    permission_classes = [permissions.AllowAny]

    def get_queryset(self):
        user = self.request.user if self.request.user.is_authenticated else None
        if not user:
            return Notification.objects.filter(user__isnull=True, is_dismissed=False)

        self._sync_user_notifications(user)
        return Notification.objects.filter(
            Q(user=user) | Q(user__isnull=True),
            is_dismissed=False
        ).order_by('-is_priority', '-created_at')

    def _sync_user_notifications(self, user):
        from datetime import timedelta
        now = timezone.now()

        # 1. Active RSVP notifications
        rsvps = RSVP.objects.filter(user=user, status=RSVP.Status.CONFIRMED).select_related('event')
        for rsvp in rsvps:
            event = rsvp.event
            title = f"Seat Confirmed: {event.title}"
            if not Notification.objects.filter(user=user, event_id=event.event_id, title=title).exists():
                Notification.objects.create(
                    user=user,
                    title=title,
                    message=f"Your seat reservation is confirmed for {event.venue}. Scheduled for {timezone.localtime(event.start_time):%a, %b %d at %I:%M %p}.",
                    category='Campus Life',
                    source=event.organizer or 'Campus Life & Arts',
                    time_label='Confirmed RSVP',
                    is_priority=False,
                    event_id=event.event_id,
                    action_label='View event'
                )

        # 2. Saved (Bookmarked) events
        bookmarks = Bookmark.objects.filter(user=user).select_related('event')
        for bm in bookmarks:
            event = bm.event
            title = f"Saved Event: {event.title}"
            if not Notification.objects.filter(user=user, event_id=event.event_id, title=title).exists():
                Notification.objects.create(
                    user=user,
                    title=title,
                    message=f"Event saved in My Week: scheduled for {timezone.localtime(event.start_time):%a, %b %d at %I:%M %p} at {event.venue}.",
                    category='Campus Life',
                    source=event.organizer or 'USIU-Africa',
                    time_label='Saved Event',
                    is_priority=False,
                    event_id=event.event_id,
                    action_label='View event'
                )

        # 3. Almost happening (events within next 48h that the user RSVPed or saved)
        upcoming_window = now + timedelta(hours=48)
        almost_events = Event.objects.filter(
            Q(rsvps__user=user, rsvps__status=RSVP.Status.CONFIRMED) | Q(bookmarks__user=user),
            start_time__gte=now - timedelta(hours=2),
            start_time__lte=upcoming_window
        ).distinct()
        for ev in almost_events:
            title = f"Starting Soon: {ev.title}"
            if not Notification.objects.filter(user=user, event_id=ev.event_id, title=title).exists():
                diff = ev.start_time - now
                hours = max(0, int(diff.total_seconds() // 3600))
                time_str = f"in {hours} hours" if hours > 1 else ("in 1 hour" if hours == 1 else "momentarily")
                Notification.objects.create(
                    user=user,
                    title=title,
                    message=f"{ev.title} starts {time_str} at {ev.venue}. Arrive 10 minutes early for check-in.",
                    category='Campus Life',
                    source=ev.organizer or 'USIU-Africa',
                    time_label='Starting Soon',
                    is_priority=True,
                    event_id=ev.event_id,
                    action_label='View event'
                )

        # 4. Academic lecture schedule / collision defense notice
        if user.schedules.exists():
            sched_title = "Timetable Collision Defense Active"
            if not Notification.objects.filter(user=user, title=sched_title).exists():
                slots_count = user.schedules.count()
                Notification.objects.create(
                    user=user,
                    title=sched_title,
                    message=f"{slots_count} weekly lecture slots synchronized with Academic Registry. Automatic collision defense protects your schedule.",
                    category='Academic',
                    source='Academic Registry · USIU ICT',
                    time_label='Active',
                    is_priority=True,
                    action_label='View timetable'
                )

        # 5. Default high-priority academic announcement if none exists
        acad_title = "Chandaria Lab Session Relocation"
        if not Notification.objects.filter(user=user, title=acad_title).exists():
            Notification.objects.create(
                user=user,
                title=acad_title,
                message="SWE 3010 lab at 2:00 PM has moved from Chandaria Innovation Lab to Science Complex B-104 due to network maintenance.",
                category='Academic',
                source='USIU ICT Operations · Academic Registry',
                time_label='Priority Notice',
                is_priority=True,
                action_label='View room'
            )

    @action(detail=True, methods=['post'])
    def read(self, request, pk=None):
        notif = self.get_object()
        notif.is_read = True
        notif.save(update_fields=['is_read', 'updated_at'])
        return Response({'status': 'marked_read'}, status=status.HTTP_200_OK)

    @action(detail=True, methods=['post'])
    def dismiss(self, request, pk=None):
        notif = self.get_object()
        notif.is_dismissed = True
        notif.save(update_fields=['is_dismissed', 'updated_at'])
        return Response({'status': 'dismissed'}, status=status.HTTP_200_OK)

    @action(detail=False, methods=['post'], url_path='mark-all-read')
    def mark_all_read(self, request):
        user = request.user if request.user.is_authenticated else None
        if user:
            Notification.objects.filter(Q(user=user) | Q(user__isnull=True), is_read=False).update(is_read=True)
        else:
            Notification.objects.filter(user__isnull=True, is_read=False).update(is_read=True)
        return Response({'status': 'all_marked_read'}, status=status.HTTP_200_OK)

