from datetime import timedelta

from django.core.cache import cache
from django.test import Client, TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from .models import ClassSchedule, Event, RSVP, School, User

WEEKDAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']


def make_event(**overrides):
    start = timezone.now() + timedelta(days=2)
    data = {
        'title': 'Test Event',
        'description': 'A test event.',
        'event_type': 'Social',
        'start_time': start,
        'end_time': start + timedelta(hours=2),
        'venue': 'Student Center',
        'total_seats': 10,
        'seats_left': 10,
        'organizer': 'Tests',
    }
    data.update(overrides)
    return Event.objects.create(**data)


def make_user(name, **extra):
    kwargs = {
        'username': name,
        'email': f'{name}@usiu.ac.ke',
        'password': 'Str0ng-pass-123',
    }
    kwargs.update(extra)
    return User.objects.create_user(**kwargs)


def client_for(user=None):
    client = APIClient()
    if user:
        client.force_authenticate(user=user)
    return client


def admin_client_for(user):
    client = Client()
    client.force_login(user)
    return client


class BaseTest(TestCase):
    def setUp(self):
        # Throttle counters live in the cache; reset between tests.
        cache.clear()


class PermissionTests(BaseTest):
    def test_anonymous_cannot_create_events(self):
        resp = client_for().post('/api/events/', {}, format='json')
        self.assertEqual(resp.status_code, 401)

    def test_regular_user_cannot_create_or_delete_events(self):
        user = make_user('student1')
        event = make_event()
        client = client_for(user)
        self.assertEqual(
            client.post('/api/events/', {}, format='json').status_code, 403
        )
        self.assertEqual(
            client.delete(f'/api/events/{event.pk}/').status_code, 403
        )
        self.assertTrue(Event.objects.filter(pk=event.pk).exists())

    def test_anyone_can_read_events(self):
        make_event()
        self.assertEqual(client_for().get('/api/events/').status_code, 200)


class RSVPTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.user = make_user('student1')
        self.event = make_event()
        self.url = f'/api/events/{self.event.pk}/rsvp/'

    def test_rsvp_takes_one_seat(self):
        resp = client_for(self.user).post(self.url, {}, format='json')
        self.assertEqual(resp.status_code, 201)
        self.assertEqual(resp.data['seats_left'], 9)

    def test_rsvp_is_idempotent(self):
        client = client_for(self.user)
        client.post(self.url, {}, format='json')
        resp = client.post(self.url, {}, format='json')
        self.assertEqual(resp.status_code, 200)
        self.event.refresh_from_db()
        self.assertEqual(self.event.seats_left, 9)
        self.assertEqual(RSVP.objects.filter(event=self.event).count(), 1)

    def test_cancel_returns_seat_and_is_idempotent(self):
        client = client_for(self.user)
        client.post(self.url, {}, format='json')
        client.delete(self.url)
        client.delete(self.url)
        self.event.refresh_from_db()
        self.assertEqual(self.event.seats_left, 10)

    def test_can_rsvp_again_after_cancelling(self):
        client = client_for(self.user)
        client.post(self.url, {}, format='json')
        client.delete(self.url)
        resp = client_for(self.user).post(self.url, {}, format='json')
        self.assertEqual(resp.status_code, 201)
        self.event.refresh_from_db()
        self.assertEqual(self.event.seats_left, 9)
        self.assertEqual(RSVP.objects.filter(event=self.event).count(), 1)

    def test_full_event_rejects_rsvp(self):
        event = make_event(total_seats=1, seats_left=1)
        url = f'/api/events/{event.pk}/rsvp/'
        first = client_for(self.user).post(url, {}, format='json')
        second = client_for(make_user('student2')).post(
            url, {}, format='json'
        )
        self.assertEqual(first.status_code, 201)
        self.assertEqual(second.status_code, 400)
        event.refresh_from_db()
        self.assertEqual(event.seats_left, 0)
        self.assertEqual(
            RSVP.objects.filter(
                event=event, status=RSVP.Status.CONFIRMED
            ).count(),
            1,
        )

    def test_ended_event_rejects_rsvp(self):
        past = timezone.now() - timedelta(hours=3)
        event = make_event(start_time=past, end_time=past + timedelta(hours=1))
        resp = client_for(self.user).post(
            f'/api/events/{event.pk}/rsvp/', {}, format='json'
        )
        self.assertEqual(resp.status_code, 400)

    def test_overlapping_events_conflict(self):
        client = client_for(self.user)
        client.post(self.url, {}, format='json')
        overlap = make_event(
            title='Overlap',
            start_time=self.event.start_time + timedelta(hours=1),
            end_time=self.event.end_time + timedelta(hours=1),
        )
        resp = client.post(
            f'/api/events/{overlap.pk}/rsvp/', {}, format='json'
        )
        self.assertEqual(resp.status_code, 400)
        self.assertIn('conflict', resp.data['error'].lower())
        overlap.refresh_from_db()
        self.assertEqual(overlap.seats_left, 10)

    def test_class_schedule_conflict(self):
        local = timezone.localtime(timezone.now()) + timedelta(days=3)
        start = local.replace(hour=10, minute=0, second=0, microsecond=0)
        event = make_event(
            start_time=start, end_time=start + timedelta(hours=2)
        )
        ClassSchedule.objects.create(
            user=self.user,
            day_of_week=WEEKDAYS[start.weekday()],
            start_time=start.replace(hour=9).time(),
            end_time=start.replace(hour=11).time(),
            course_name='Database Systems',
        )
        resp = client_for(self.user).post(
            f'/api/events/{event.pk}/rsvp/', {}, format='json'
        )
        self.assertEqual(resp.status_code, 400)
        self.assertIn('class', resp.data['error'].lower())
        event.refresh_from_db()
        self.assertEqual(event.seats_left, 10)

    def test_guest_rsvp_with_email(self):
        resp = client_for().post(
            self.url, {'email': 'Guest@Example.com'}, format='json'
        )
        self.assertEqual(resp.status_code, 201)
        rsvp = RSVP.objects.get(event=self.event)
        self.assertIsNone(rsvp.user)
        self.assertEqual(rsvp.guest_email, 'guest@example.com')

    def test_guest_without_email_is_rejected(self):
        resp = client_for().post(self.url, {}, format='json')
        self.assertEqual(resp.status_code, 401)


class AdminEventTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.admin = make_user('boss', is_staff=True)
        self.event = make_event(total_seats=5, seats_left=3)
        for name in ('a', 'b'):
            RSVP.objects.create(
                user=make_user(name),
                event=self.event,
                status=RSVP.Status.CONFIRMED,
            )

    def test_raising_total_seats_opens_seats(self):
        client = client_for(self.admin)
        resp = client.patch(
            f'/api/events/{self.event.pk}/', {'total_seats': 10},
            format='json',
        )
        self.assertEqual(resp.status_code, 200)
        self.event.refresh_from_db()
        self.assertEqual(self.event.seats_left, 8)

    def test_cannot_drop_total_below_confirmed(self):
        client = client_for(self.admin)
        resp = client.patch(
            f'/api/events/{self.event.pk}/', {'total_seats': 1},
            format='json',
        )
        self.assertEqual(resp.status_code, 400)
        self.event.refresh_from_db()
        self.assertEqual(self.event.total_seats, 5)


class SchoolTests(BaseTest):
    def test_majors_with_commas_inside_parentheses_stay_whole(self):
        school = School(
            code='SCCCA',
            name='Creative Arts',
            majors='Animation, Journalism (Broadcast, Print, Public Relations)',
        )
        self.assertEqual(
            school.get_majors_list(),
            [
                'Animation',
                'Journalism (Broadcast, Print, Public Relations)',
            ],
        )


class SignupTests(BaseTest):
    def test_signup_success(self):
        url = '/api/auth/register/'
        data = {
            'username': 'newstudent',
            'email': 'NewStudent@USIU.ac.ke',
            'password': 'StrongPassword123!',
            'student_id': 'SID12345',
        }
        resp = client_for().post(url, data, format='json')
        self.assertEqual(resp.status_code, 201)
        self.assertIn('access', resp.data)
        self.assertIn('refresh', resp.data)

        client = client_for()
        client.credentials(HTTP_AUTHORIZATION=f"Bearer {resp.data['access']}")
        profile_resp = client.get('/api/auth/profile/')
        self.assertEqual(profile_resp.status_code, 200)
        self.assertEqual(profile_resp.data['username'], 'newstudent')

    def test_duplicate_username_rejected(self):
        make_user('existing')
        resp = client_for().post('/api/auth/register/', {
            'username': 'existing', 'password': 'StrongPassword123!'
        }, format='json')
        self.assertEqual(resp.status_code, 400)

    def test_duplicate_email_case_insensitive_rejected(self):
        make_user('user1', email='test@usiu.ac.ke')
        resp = client_for().post('/api/auth/register/', {
            'username': 'user2', 'email': 'TEST@usiu.ac.ke', 'password': 'StrongPassword123!'
        }, format='json')
        self.assertEqual(resp.status_code, 400)

    def test_duplicate_student_id_rejected(self):
        make_user('user1', student_id='12345')
        resp = client_for().post('/api/auth/register/', {
            'username': 'user2', 'student_id': '12345', 'password': 'StrongPassword123!'
        }, format='json')
        self.assertEqual(resp.status_code, 400)

    def test_weak_password_rejected(self):
        resp = client_for().post('/api/auth/register/', {
            'username': 'user3', 'password': '123'
        }, format='json')
        self.assertEqual(resp.status_code, 400)

    def test_unknown_major_rejected(self):
        School.objects.create(code='SST', name='Tech', majors='Software Engineering')
        resp = client_for().post('/api/auth/register/', {
            'username': 'user4', 'school_code': 'SST', 'major': 'Medicine', 'password': 'StrongPassword123!'
        }, format='json')
        self.assertEqual(resp.status_code, 400)

    def test_blank_email_allowed(self):
        resp = client_for().post('/api/auth/register/', {
            'username': 'user5', 'email': '', 'password': 'StrongPassword123!'
        }, format='json')
        self.assertEqual(resp.status_code, 201)


class AdminDashboardAndCreationTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.admin = make_user('adminboss', is_staff=True)
        self.student = make_user('regularstudent')

    def test_staff_can_create_event_with_explicit_times(self):
        client = admin_client_for(self.admin)
        start = timezone.now() + timedelta(days=1)
        end = start + timedelta(hours=2)
        resp = client.post('/api/dashboard/events/add/', {
            'title': 'Admin Created Event',
            'description': 'Description here',
            'event_type': 'Academic',
            'start_time': start.strftime('%Y-%m-%dT%H:%M'),
            'end_time': end.strftime('%Y-%m-%dT%H:%M'),
            'venue': 'Auditorium',
            'venue_select': 'Auditorium',
            'total_seats': 50,
            'organizer': 'Dean',
        })
        self.assertIn(resp.status_code, (200, 302))
        event = Event.objects.filter(title='Admin Created Event').first()
        self.assertIsNotNone(event)
        self.assertEqual(event.total_seats, 50)
        self.assertEqual(event.seats_left, 50)

    def test_non_staff_denied_dashboard_add(self):
        client = admin_client_for(self.student)
        resp = client.get('/api/dashboard/events/add/')
        self.assertIn(resp.status_code, (302, 403))

    def test_invalid_times_end_before_start(self):
        client = admin_client_for(self.admin)
        start = timezone.now() + timedelta(days=2)
        end = start - timedelta(hours=1)
        resp = client.post('/api/dashboard/events/add/', {
            'title': 'Bad Time Event',
            'description': 'Desc',
            'event_type': 'Social',
            'start_time': start.strftime('%Y-%m-%dT%H:%M'),
            'end_time': end.strftime('%Y-%m-%dT%H:%M'),
            'venue': 'Student Center',
            'venue_select': 'Student Center',
            'total_seats': 10,
            'organizer': 'Tests',
        })
        self.assertEqual(resp.status_code, 200)
        self.assertFalse(Event.objects.filter(title='Bad Time Event').exists())

    def test_delete_event_via_post_only(self):
        event = make_event()
        client = admin_client_for(self.admin)
        resp_get = client.get(f'/api/dashboard/events/{event.pk}/delete/')
        self.assertEqual(resp_get.status_code, 200)
        self.assertTrue(Event.objects.filter(pk=event.pk).exists())

        resp_post = client.post(f'/api/dashboard/events/{event.pk}/delete/')
        self.assertIn(resp_post.status_code, (200, 302))
        self.assertFalse(Event.objects.filter(pk=event.pk).exists())


class AdminRsvpManagementTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.admin = make_user('adminboss', is_staff=True)
        self.student = make_user('regularstudent')
        self.event = make_event(total_seats=5, seats_left=4)
        self.rsvp = RSVP.objects.create(
            user=self.student,
            event=self.event,
            status=RSVP.Status.CONFIRMED,
        )

    def test_staff_can_view_rsvps_list(self):
        client = admin_client_for(self.admin)
        resp = client.get(f'/api/dashboard/events/{self.event.pk}/rsvps/')
        self.assertEqual(resp.status_code, 200)
        self.assertContains(resp, self.student.username)

    def test_non_staff_cannot_view_rsvps(self):
        client = admin_client_for(self.student)
        resp = client.get(f'/api/dashboard/events/{self.event.pk}/rsvps/')
        self.assertIn(resp.status_code, (302, 403))

    def test_admin_cancel_rsvp_restores_seat_idempotent(self):
        client = admin_client_for(self.admin)
        url = f'/api/dashboard/events/{self.event.pk}/rsvps/{self.rsvp.pk}/cancel/'
        resp = client.post(url)
        self.assertIn(resp.status_code, (200, 302))
        self.event.refresh_from_db()
        self.assertEqual(self.event.seats_left, 5)

        client.post(url)
        self.event.refresh_from_db()
        self.assertEqual(self.event.seats_left, 5)

    def test_csv_export_neutralizes_formula_injection(self):
        self.rsvp.notes = "=SUM(A1:A10)"
        self.rsvp.save()
        client = admin_client_for(self.admin)
        resp = client.get(f'/api/dashboard/events/{self.event.pk}/rsvps/csv/')
        self.assertEqual(resp.status_code, 200)
        content = resp.content.decode('utf-8')
        self.assertIn("'=SUM", content)


class PublicAuthIgnoredTokenTests(BaseTest):
    def test_register_with_garbage_token_succeeds(self):
        client = APIClient()
        client.credentials(HTTP_AUTHORIZATION='Bearer not-a-real-token')
        resp = client.post('/api/auth/register/', {
            'username': 'garbagetokenuser',
            'password': 'StrongPassword123!',
        }, format='json')
        self.assertEqual(resp.status_code, 201)
        self.assertTrue(User.objects.filter(username='garbagetokenuser').exists())

    def test_login_with_garbage_token_succeeds(self):
        make_user('loginuser', password='StrongPassword123!')
        client = APIClient()
        client.credentials(HTTP_AUTHORIZATION='Bearer not-a-real-token')
        resp = client.post('/api/auth/login/', {
            'username': 'loginuser',
            'password': 'StrongPassword123!',
        }, format='json')
        self.assertEqual(resp.status_code, 200)
        self.assertIn('access', resp.data)

    def test_register_with_no_header_succeeds(self):
        resp = client_for().post('/api/auth/register/', {
            'username': 'noheaderuser',
            'password': 'StrongPassword123!',
        }, format='json')
        self.assertEqual(resp.status_code, 201)

    def test_protected_endpoint_with_garbage_token_fails(self):
        client = APIClient()
        client.credentials(HTTP_AUTHORIZATION='Bearer not-a-real-token')
        resp = client.get('/api/auth/profile/')
        self.assertEqual(resp.status_code, 401)


class UnlimitedCapacityFeatureTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.admin = make_user('adminboss', is_staff=True)
        self.user1 = make_user('user1')
        self.user2 = make_user('user2')

    def test_create_unlimited_event_explicit_null(self):
        client = client_for(self.admin)
        start = timezone.now() + timedelta(days=1)
        end = start + timedelta(hours=2)
        resp = client.post('/api/events/', {
            'title': 'Unlimited Event',
            'description': 'Desc',
            'event_type': 'Social',
            'start_time': start.isoformat(),
            'end_time': end.isoformat(),
            'venue': 'Quad',
            'total_seats': None,
            'organizer': 'SAC',
        }, format='json')
        self.assertEqual(resp.status_code, 201)
        event = Event.objects.get(pk=resp.data['id'])
        self.assertIsNone(event.total_seats)
        self.assertIsNone(event.seats_left)

    def test_create_fixed_capacity_event(self):
        client = client_for(self.admin)
        start = timezone.now() + timedelta(days=1)
        end = start + timedelta(hours=2)
        resp = client.post('/api/events/', {
            'title': 'Fixed Event',
            'description': 'Desc',
            'event_type': 'Academic',
            'start_time': start.isoformat(),
            'end_time': end.isoformat(),
            'venue': 'Auditorium',
            'total_seats': 50,
            'organizer': 'Dean',
        }, format='json')
        self.assertEqual(resp.status_code, 201)
        event = Event.objects.get(pk=resp.data['id'])
        self.assertEqual(event.total_seats, 50)
        self.assertEqual(event.seats_left, 50)

    def test_rsvp_to_unlimited_event(self):
        event = Event.objects.create(
            title='Unlimited RSVP',
            description='Desc',
            start_time=timezone.now() + timedelta(days=1),
            end_time=timezone.now() + timedelta(days=1, hours=2),
            venue='Quad',
            total_seats=None,
            seats_left=None,
            organizer='SAC'
        )
        url = f'/api/events/{event.pk}/rsvp/'
        resp = client_for(self.user1).post(url, {}, format='json')
        self.assertEqual(resp.status_code, 201)
        self.assertIsNone(resp.data['seats_left'])

    def test_multiple_users_rsvp_unlimited_event(self):
        event = Event.objects.create(
            title='Mass Unlimited',
            description='Desc',
            start_time=timezone.now() + timedelta(days=1),
            end_time=timezone.now() + timedelta(days=1, hours=2),
            venue='Quad',
            total_seats=None,
            seats_left=None,
            organizer='SAC'
        )
        url = f'/api/events/{event.pk}/rsvp/'
        resp1 = client_for(self.user1).post(url, {}, format='json')
        resp2 = client_for(self.user2).post(url, {}, format='json')
        self.assertEqual(resp1.status_code, 201)
        self.assertEqual(resp2.status_code, 201)
        event.refresh_from_db()
        self.assertIsNone(event.seats_left)
        self.assertEqual(RSVP.objects.filter(event=event, status=RSVP.Status.CONFIRMED).count(), 2)

    def test_cancel_rsvp_to_unlimited_event(self):
        event = Event.objects.create(
            title='Cancel Unlimited',
            description='Desc',
            start_time=timezone.now() + timedelta(days=1),
            end_time=timezone.now() + timedelta(days=1, hours=2),
            venue='Quad',
            total_seats=None,
            seats_left=None,
            organizer='SAC'
        )
        url = f'/api/events/{event.pk}/rsvp/'
        client = client_for(self.user1)
        client.post(url, {}, format='json')
        resp = client.delete(url)
        self.assertEqual(resp.status_code, 200)
        event.refresh_from_db()
        self.assertIsNone(event.seats_left)
        self.assertEqual(RSVP.objects.get(user=self.user1, event=event).status, RSVP.Status.CANCELLED)

    def test_reregister_after_cancellation_unlimited_event(self):
        event = Event.objects.create(
            title='Re-register Unlimited',
            description='Desc',
            start_time=timezone.now() + timedelta(days=1),
            end_time=timezone.now() + timedelta(days=1, hours=2),
            venue='Quad',
            total_seats=None,
            seats_left=None,
            organizer='SAC'
        )
        url = f'/api/events/{event.pk}/rsvp/'
        client = client_for(self.user1)
        client.post(url, {}, format='json')
        client.delete(url)
        resp = client.post(url, {}, format='json')
        self.assertEqual(resp.status_code, 201)
        self.assertEqual(RSVP.objects.get(user=self.user1, event=event).status, RSVP.Status.CONFIRMED)

    def test_prevent_overbooking_fixed_capacity_events(self):
        event = make_event(total_seats=1, seats_left=1)
        url = f'/api/events/{event.pk}/rsvp/'
        r1 = client_for(self.user1).post(url, {}, format='json')
        r2 = client_for(self.user2).post(url, {}, format='json')
        self.assertEqual(r1.status_code, 201)
        self.assertEqual(r2.status_code, 400)
        event.refresh_from_db()
        self.assertEqual(event.seats_left, 0)

    def test_change_fixed_capacity_to_unlimited(self):
        event = make_event(total_seats=10, seats_left=10)
        client = client_for(self.admin)
        resp = client.patch(f'/api/events/{event.pk}/', {'total_seats': None}, format='json')
        self.assertEqual(resp.status_code, 200)
        event.refresh_from_db()
        self.assertIsNone(event.total_seats)
        self.assertIsNone(event.seats_left)

    def test_change_unlimited_to_fixed_capacity(self):
        event = Event.objects.create(
            title='Change to Fixed',
            description='Desc',
            start_time=timezone.now() + timedelta(days=1),
            end_time=timezone.now() + timedelta(days=1, hours=2),
            venue='Quad',
            total_seats=None,
            seats_left=None,
            organizer='SAC'
        )
        RSVP.objects.create(user=self.user1, event=event, status=RSVP.Status.CONFIRMED)
        RSVP.objects.create(user=self.user2, event=event, status=RSVP.Status.CONFIRMED)

        client = client_for(self.admin)
        resp = client.patch(f'/api/events/{event.pk}/', {'total_seats': 10}, format='json')
        self.assertEqual(resp.status_code, 200)
        event.refresh_from_db()
        self.assertEqual(event.total_seats, 10)
        self.assertEqual(event.seats_left, 8)

    def test_api_capacity_omitted_vs_explicit_null(self):
        client = client_for(self.admin)
        start = timezone.now() + timedelta(days=1)
        end = start + timedelta(hours=2)
        resp1 = client.post('/api/events/', {
            'title': 'Omitted Capacity',
            'description': 'Desc',
            'event_type': 'Social',
            'start_time': start.isoformat(),
            'end_time': end.isoformat(),
            'venue': 'Quad',
            'organizer': 'SAC',
        }, format='json')
        self.assertEqual(resp1.status_code, 201)
        self.assertEqual(Event.objects.get(pk=resp1.data['id']).total_seats, 100)

        resp2 = client.post('/api/events/', {
            'title': 'Explicit Null Capacity',
            'description': 'Desc',
            'event_type': 'Social',
            'start_time': start.isoformat(),
            'end_time': end.isoformat(),
            'venue': 'Quad',
            'total_seats': None,
            'organizer': 'SAC',
        }, format='json')
        self.assertEqual(resp2.status_code, 201)
        self.assertIsNone(Event.objects.get(pk=resp2.data['id']).total_seats)

    def test_existing_event_retains_original_capacity(self):
        event = make_event(total_seats=25, seats_left=25)
        event.refresh_from_db()
        self.assertEqual(event.total_seats, 25)
        self.assertEqual(event.seats_left, 25)


class AdminUserDirectoryTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.admin = make_user('adminboss', is_staff=True)
        self.student = make_user('regularstudent', email='student@usiu.ac.ke', student_id='SID999')
        self.other_user = make_user('otheruser', email='other@usiu.ac.ke')

    def test_admin_can_view_users_list(self):
        client = admin_client_for(self.admin)
        resp = client.get('/api/dashboard/users/')
        self.assertEqual(resp.status_code, 200)
        self.assertContains(resp, self.student.username)
        self.assertContains(resp, self.other_user.username)

    def test_non_staff_cannot_view_users_list(self):
        client = admin_client_for(self.student)
        resp = client.get('/api/dashboard/users/')
        self.assertIn(resp.status_code, (302, 403))

    def test_users_list_search_and_filter(self):
        client = admin_client_for(self.admin)
        resp = client.get('/api/dashboard/users/?q=SID999')
        self.assertEqual(resp.status_code, 200)
        self.assertContains(resp, self.student.username)
        self.assertNotContains(resp, self.other_user.username)

        resp_admin = client.get('/api/dashboard/users/?role=admin')
        self.assertEqual(resp_admin.status_code, 200)
        self.assertContains(resp_admin, self.admin.username)

    def test_admin_can_view_user_detail(self):
        client = admin_client_for(self.admin)
        resp = client.get(f'/api/dashboard/users/{self.student.pk}/')
        self.assertEqual(resp.status_code, 200)
        self.assertContains(resp, self.student.username)
        self.assertContains(resp, self.student.email)
        self.assertContains(resp, self.student.student_id)

    def test_non_staff_cannot_view_user_detail(self):
        client = admin_client_for(self.other_user)
        resp = client.get(f'/api/dashboard/users/{self.student.pk}/')
        self.assertIn(resp.status_code, (302, 403))


class ProductionReadinessRSVPTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.user1 = make_user('user1')
        self.user2 = make_user('user2')

    def test_1_successful_registration(self):
        event = make_event(total_seats=10, seats_left=10)
        client = client_for(self.user1)
        resp = client.post(f'/api/events/{event.pk}/rsvp/', {}, format='json')
        self.assertEqual(resp.status_code, 201)
        event.refresh_from_db()
        self.assertEqual(event.seats_left, 9)
        self.assertTrue(RSVP.objects.filter(user=self.user1, event=event, status=RSVP.Status.CONFIRMED).exists())

    def test_2_successful_cancellation(self):
        event = make_event(total_seats=10, seats_left=9)
        RSVP.objects.create(user=self.user1, event=event, status=RSVP.Status.CONFIRMED)
        client = client_for(self.user1)
        resp = client.delete(f'/api/events/{event.pk}/rsvp/')
        self.assertEqual(resp.status_code, 200)
        event.refresh_from_db()
        self.assertEqual(event.seats_left, 10)

    def test_3_cancellation_preserves_rsvp_record(self):
        event = make_event(total_seats=10, seats_left=9)
        RSVP.objects.create(user=self.user1, event=event, status=RSVP.Status.CONFIRMED)
        client = client_for(self.user1)
        client.delete(f'/api/events/{event.pk}/rsvp/')
        rsvp = RSVP.objects.get(user=self.user1, event=event)
        self.assertEqual(rsvp.status, RSVP.Status.CANCELLED)

    def test_4_cancellation_restores_one_seat(self):
        event = make_event(total_seats=5, seats_left=3)
        RSVP.objects.create(user=self.user1, event=event, status=RSVP.Status.CONFIRMED)
        client = client_for(self.user1)
        client.delete(f'/api/events/{event.pk}/rsvp/')
        event.refresh_from_db()
        self.assertEqual(event.seats_left, 4)

    def test_5_cancellation_does_not_exceed_total_capacity(self):
        event = make_event(total_seats=5, seats_left=5)
        RSVP.objects.create(user=self.user1, event=event, status=RSVP.Status.CANCELLED)
        client = client_for(self.user1)
        client.delete(f'/api/events/{event.pk}/rsvp/')
        event.refresh_from_db()
        self.assertLessEqual(event.seats_left, event.total_seats)

    def test_6_repeated_cancellation_does_not_restore_extra_seats(self):
        event = make_event(total_seats=10, seats_left=9)
        RSVP.objects.create(user=self.user1, event=event, status=RSVP.Status.CONFIRMED)
        client = client_for(self.user1)
        client.delete(f'/api/events/{event.pk}/rsvp/')
        client.delete(f'/api/events/{event.pk}/rsvp/')
        event.refresh_from_db()
        self.assertEqual(event.seats_left, 10)

    def test_7_duplicate_registration_does_not_consume_extra_seats(self):
        event = make_event(total_seats=10, seats_left=10)
        client = client_for(self.user1)
        client.post(f'/api/events/{event.pk}/rsvp/', {}, format='json')
        resp = client.post(f'/api/events/{event.pk}/rsvp/', {}, format='json')
        self.assertEqual(resp.status_code, 200)
        event.refresh_from_db()
        self.assertEqual(event.seats_left, 9)
        self.assertEqual(RSVP.objects.filter(event=event, user=self.user1, status=RSVP.Status.CONFIRMED).count(), 1)

    def test_8_full_fixed_capacity_event_rejects_new_registrations(self):
        event = make_event(total_seats=1, seats_left=0)
        client = client_for(self.user1)
        resp = client.post(f'/api/events/{event.pk}/rsvp/', {}, format='json')
        self.assertEqual(resp.status_code, 400)

    def test_9_unlimited_capacity_event_accepts_registration(self):
        event = Event.objects.create(
            title='Unlimited',
            description='Desc',
            start_time=timezone.now() + timedelta(days=1),
            end_time=timezone.now() + timedelta(days=1, hours=2),
            venue='Quad',
            total_seats=None,
            seats_left=None,
            organizer='SAC'
        )
        client = client_for(self.user1)
        resp = client.post(f'/api/events/{event.pk}/rsvp/', {}, format='json')
        self.assertEqual(resp.status_code, 201)
        event.refresh_from_db()
        self.assertIsNone(event.seats_left)

    def test_10_unlimited_capacity_cancellation_does_not_modify_capacity(self):
        event = Event.objects.create(
            title='Unlimited',
            description='Desc',
            start_time=timezone.now() + timedelta(days=1),
            end_time=timezone.now() + timedelta(days=1, hours=2),
            venue='Quad',
            total_seats=None,
            seats_left=None,
            organizer='SAC'
        )
        RSVP.objects.create(user=self.user1, event=event, status=RSVP.Status.CONFIRMED)
        client = client_for(self.user1)
        resp = client.delete(f'/api/events/{event.pk}/rsvp/')
        self.assertEqual(resp.status_code, 200)
        event.refresh_from_db()
        self.assertIsNone(event.seats_left)

    def test_11_is_rsvped_correct_for_authenticated_users(self):
        event = make_event(total_seats=10, seats_left=10)
        RSVP.objects.create(user=self.user1, event=event, status=RSVP.Status.CONFIRMED)
        client = client_for(self.user1)
        resp = client.get(f'/api/events/{event.pk}/')
        self.assertEqual(resp.status_code, 200)
        self.assertTrue(resp.data['is_rsvped'])
