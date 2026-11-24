import uuid
from datetime import datetime
from datetime import timezone as dt_timezone
from django.contrib.auth.models import AbstractUser
from django.core.exceptions import ValidationError
from django.db import models
from django.db.models import F, Q


def generate_event_id():
    return f"evt_{uuid.uuid4().hex[:10]}"


def generate_fixture_id():
    return f"fix_{uuid.uuid4().hex[:10]}"


class User(AbstractUser):
    email = models.EmailField(unique=True, blank=True, null=True)
    school_code = models.CharField(max_length=20, blank=True, null=True, db_index=True)
    major = models.CharField(max_length=100, blank=True, null=True)
    student_id = models.CharField(max_length=50, unique=True, blank=True, null=True)

    class Meta:
        permissions = [
            ('view_dashboard', 'Can view the administration dashboard'),
            ('change_user_status', 'Can activate or deactivate user accounts'),
            ('assign_staff_roles', 'Can assign non-super-admin staff roles'),
            ('grant_super_admin', 'Can grant or revoke Super Admin role'),
            ('view_sensitive_user_data', 'Can view sensitive user data'),
            ('reset_user_password', 'Can initiate user password resets'),
        ]

    def clean(self):
        super().clean()
        if self.email == '':
            self.email = None
        if self.student_id == '':
            self.student_id = None
        if self.school_code == '':
            self.school_code = None
        if self.major == '':
            self.major = None

    def save(self, *args, **kwargs):
        self.clean()
        super().save(*args, **kwargs)


class ClassSchedule(models.Model):
    class DayOfWeek(models.TextChoices):
        MON = 'MON', 'Monday'
        TUE = 'TUE', 'Tuesday'
        WED = 'WED', 'Wednesday'
        THU = 'THU', 'Thursday'
        FRI = 'FRI', 'Friday'
        SAT = 'SAT', 'Saturday'
        SUN = 'SUN', 'Sunday'

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='schedules')
    day_of_week = models.CharField(max_length=3, choices=DayOfWeek.choices, db_index=True)
    start_time = models.TimeField()
    end_time = models.TimeField()
    course_name = models.CharField(max_length=100)

    class Meta:
        ordering = ['day_of_week', 'start_time']
        permissions = [
            ('view_timetable_conflicts', 'Can view timetable conflicts'),
            ('manage_class_schedules', 'Can manage class schedules'),
        ]
        constraints = [
            models.CheckConstraint(
                condition=Q(end_time__gt=F('start_time')),
                name='class_schedule_end_after_start',
            ),
            models.UniqueConstraint(
                fields=['user', 'day_of_week', 'start_time', 'course_name'],
                name='unique_user_class_schedule_slot',
            ),
        ]

    @property
    def start_hour(self):
        return self.start_time.hour if self.start_time else 0

    @property
    def start_minute(self):
        return self.start_time.minute if self.start_time else 0

    @property
    def end_hour(self):
        return self.end_time.hour if self.end_time else 0

    @property
    def end_minute(self):
        return self.end_time.minute if self.end_time else 0

    def clean(self):
        super().clean()
        if self.start_time and self.end_time and self.end_time <= self.start_time:
            raise ValidationError({'end_time': 'Class end time must be after start time.'})

    def __str__(self):
        return f"{self.user.username} - {self.course_name} ({self.day_of_week} {self.start_time:%H:%M}-{self.end_time:%H:%M})"


class School(models.Model):
    code = models.CharField(max_length=20, unique=True, db_index=True)
    name = models.CharField(max_length=200)
    majors = models.TextField(help_text="Comma-separated list of majors")

    def get_majors_list(self):
        if not self.majors:
            return []
        import re
        raw = re.split(r',\s*(?![^()]*\))', self.majors)
        return [m.strip() for m in raw if m.strip()]

    def __str__(self):
        return f"{self.code} - {self.name}"


class Event(models.Model):
    class EventType(models.TextChoices):
        ACADEMIC = 'Academic', 'Academic'
        CAREER = 'Career', 'Career'
        SOCIAL = 'Social', 'Social'
        SPORTS = 'Sports', 'Sports'
        WELLNESS = 'Wellness', 'Wellness'

    event_id = models.CharField(max_length=50, unique=True, default=generate_event_id, db_index=True)
    title = models.CharField(max_length=255)
    description = models.TextField()
    school_code = models.CharField(max_length=20, blank=True, null=True, db_index=True)
    major = models.CharField(max_length=100, blank=True, null=True, db_index=True)
    event_type = models.CharField(max_length=50, choices=EventType.choices, default=EventType.ACADEMIC, db_index=True)
    start_time = models.DateTimeField(db_index=True)
    end_time = models.DateTimeField(db_index=True)
    venue = models.CharField(max_length=100)
    total_seats = models.PositiveIntegerField(blank=True, null=True, default=None)
    seats_left = models.PositiveIntegerField(blank=True, null=True, default=None)
    organizer = models.CharField(max_length=100)
    image = models.ImageField(upload_to='event_flyers/', blank=True, null=True)
    image_url = models.URLField(max_length=500, blank=True, null=True)
    video = models.FileField(upload_to='event_videos/', blank=True, null=True)

    class Meta:
        ordering = ['start_time']
        permissions = [
            ('archive_event', 'Can archive events'),
            ('manage_event_media', 'Can manage event media'),
        ]
        constraints = [
            models.CheckConstraint(
                condition=Q(end_time__gt=F('start_time')),
                name='event_end_after_start',
            ),
            models.CheckConstraint(
                condition=Q(total_seats__isnull=True) | Q(total_seats__gte=1),
                name='event_total_seats_positive',
            ),
            models.CheckConstraint(
                condition=Q(total_seats__isnull=True, seats_left__isnull=True) | Q(seats_left__lte=F('total_seats')),
                name='event_seats_left_lte_total',
            ),
        ]

    @property
    def start_millis(self):
        return int(self.start_time.timestamp() * 1000) if self.start_time else 0

    @start_millis.setter
    def start_millis(self, value):
        if value is not None:
            self.start_time = datetime.fromtimestamp(int(value) / 1000.0, tz=dt_timezone.utc)

    @property
    def end_millis(self):
        return int(self.end_time.timestamp() * 1000) if self.end_time else 0

    @end_millis.setter
    def end_millis(self, value):
        if value is not None:
            self.end_time = datetime.fromtimestamp(int(value) / 1000.0, tz=dt_timezone.utc)

    def clean(self):
        super().clean()
        if self.school_code == '':
            self.school_code = None
        if self.major == '':
            self.major = None
        if self.start_time and self.end_time and self.end_time <= self.start_time:
            raise ValidationError({'end_time': 'Event end time must be after start time.'})
        if self.seats_left is not None and self.total_seats is not None and self.seats_left > self.total_seats:
            raise ValidationError({'seats_left': 'Seats left cannot exceed total seats.'})

    def __str__(self):
        return self.title


class Fixture(models.Model):
    fixture_id = models.CharField(max_length=50, unique=True, default=generate_fixture_id, db_index=True)
    team_id = models.CharField(max_length=50, db_index=True)
    team_name = models.CharField(max_length=100)
    sport = models.CharField(max_length=50, db_index=True)
    opponent = models.CharField(max_length=100)
    start_time = models.DateTimeField(db_index=True)
    end_time = models.DateTimeField()
    venue = models.CharField(max_length=100)
    league = models.CharField(max_length=100, default='KUSA League')
    fan_count = models.PositiveIntegerField(default=0)
    score_usiu = models.PositiveIntegerField(blank=True, null=True)
    score_opponent = models.PositiveIntegerField(blank=True, null=True)
    is_home = models.BooleanField(default=True)

    class Meta:
        ordering = ['start_time']
        constraints = [
            models.CheckConstraint(
                condition=Q(end_time__gt=F('start_time')),
                name='fixture_end_after_start',
            ),
        ]

    @property
    def time_millis(self):
        return self.start_millis

    @time_millis.setter
    def time_millis(self, value):
        self.start_millis = value
        if value is not None and not self.end_time:
            self.end_millis = int(value) + 2 * 3600 * 1000

    @property
    def start_millis(self):
        return int(self.start_time.timestamp() * 1000) if self.start_time else 0

    @start_millis.setter
    def start_millis(self, value):
        if value is not None:
            self.start_time = datetime.fromtimestamp(int(value) / 1000.0, tz=dt_timezone.utc)

    @property
    def end_millis(self):
        return int(self.end_time.timestamp() * 1000) if self.end_time else 0

    @end_millis.setter
    def end_millis(self, value):
        if value is not None:
            self.end_time = datetime.fromtimestamp(int(value) / 1000.0, tz=dt_timezone.utc)

    def __str__(self):
        return f"{self.team_name} vs {self.opponent}"


class FixtureScorer(models.Model):
    fixture = models.ForeignKey(Fixture, on_delete=models.CASCADE, related_name='scorers')
    player_name = models.CharField(max_length=100)
    minute = models.PositiveSmallIntegerField()
    added_minute = models.PositiveSmallIntegerField(blank=True, null=True)

    class Meta:
        ordering = ['minute', 'added_minute', 'id']
        constraints = [
            models.CheckConstraint(condition=models.Q(minute__lte=150), name='fixture_scorer_minute_max_150'),
            models.CheckConstraint(condition=models.Q(added_minute__isnull=True) | models.Q(added_minute__lte=30), name='fixture_scorer_added_minute_max_30'),
        ]

    def clean(self):
        super().clean()
        if self.fixture_id and self.fixture.sport.casefold() != 'football':
            raise ValidationError({'fixture': 'Scorer events are supported only for football fixtures.'})

    @property
    def display_minute(self):
        return f"{self.minute}+{self.added_minute}'" if self.added_minute else f"{self.minute}'"

    def __str__(self):
        return f"{self.player_name}, {self.display_minute}"


class Team(models.Model):
    """Stable team identity derived from fixture participants."""
    team_id = models.CharField(max_length=120, unique=True)
    name = models.CharField(max_length=120)
    sport = models.CharField(max_length=50, db_index=True)
    competition = models.CharField(max_length=100, blank=True)

    class Meta:
        ordering = ['sport', 'name']

    def __str__(self):
        return f"{self.name} ({self.sport})"


class FavoriteTeam(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='favorite_teams')
    team = models.ForeignKey(Team, on_delete=models.CASCADE, related_name='followers')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['team__sport', 'team__name']
        constraints = [
            models.UniqueConstraint(fields=['user', 'team'], name='unique_user_favorite_team'),
        ]
        indexes = [models.Index(fields=['user', 'created_at'], name='favorite_user_created_idx')]

    def __str__(self):
        return f"{self.user_id} follows {self.team_id}"


class RSVP(models.Model):
    class Status(models.TextChoices):
        CONFIRMED = 'CONFIRMED', 'Confirmed'
        CANCELLED = 'CANCELLED', 'Cancelled'
        WAITLISTED = 'WAITLISTED', 'Waitlisted'

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='rsvps', blank=True, null=True)
    event = models.ForeignKey(Event, on_delete=models.CASCADE, related_name='rsvps')
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.CONFIRMED, db_index=True)
    guest_email = models.EmailField(blank=True, null=True)
    guest_student_id = models.CharField(max_length=50, blank=True, null=True)
    notes = models.TextField(blank=True, null=True)
    timestamp = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        permissions = [
            ('view_rsvp_aggregate', 'Can view aggregate RSVP information'),
            ('view_rsvp_pii', 'Can view personally identifiable RSVP data'),
            ('export_rsvps', 'Can export RSVP data'),
            ('cancel_rsvp', 'Can cancel an RSVP administratively'),
        ]
        constraints = [
            models.UniqueConstraint(
                fields=['user', 'event'],
                condition=Q(user__isnull=False),
                name='unique_authenticated_user_event_rsvp',
            ),
            models.UniqueConstraint(
                fields=['guest_email', 'event'],
                condition=Q(user__isnull=True, guest_email__isnull=False),
                name='unique_guest_email_event_rsvp',
            ),
            models.CheckConstraint(
                condition=Q(user__isnull=False) | Q(guest_email__isnull=False),
                name='rsvp_requires_user_or_guest_email',
            ),
        ]

    def __str__(self):
        who = self.user.username if self.user else self.guest_email
        return f"{who} -> {self.event.title} ({self.status})"


class Bookmark(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='bookmarks')
    event = models.ForeignKey(Event, on_delete=models.CASCADE, related_name='bookmarks')
    timestamp = models.DateTimeField(auto_now_add=True)


class Notification(models.Model):
    class Category(models.TextChoices):
        ACADEMIC = 'Academic', 'Academic'
        CAMPUS_LIFE = 'Campus Life', 'Campus Life'
        RSVP = 'RSVP', 'RSVP'
        SAVED = 'Saved', 'Saved'

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='notifications', blank=True, null=True)
    title = models.CharField(max_length=200)
    message = models.TextField()
    category = models.CharField(max_length=50, choices=Category.choices, default=Category.CAMPUS_LIFE)
    source = models.CharField(max_length=100, default='USIU-Africa CampusPulse')
    time_label = models.CharField(max_length=50, default='Today')
    is_priority = models.BooleanField(default=False)
    is_read = models.BooleanField(default=False)
    is_dismissed = models.BooleanField(default=False)
    event_id = models.CharField(max_length=50, blank=True, null=True)
    action_label = models.CharField(max_length=50, blank=True, null=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-is_priority', '-created_at']

    def __str__(self):
        return f"{self.title} ({'Unread' if not self.is_read else 'Read'})"

