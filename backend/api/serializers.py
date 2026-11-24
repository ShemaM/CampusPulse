from datetime import time as dt_time
from django.contrib.auth.password_validation import validate_password
from django.utils import timezone
from rest_framework import serializers
from .models import User, School, Event, Fixture, FixtureScorer, RSVP, Bookmark, ClassSchedule, Team, Notification


class ClassScheduleSerializer(serializers.ModelSerializer):
    credits = serializers.SerializerMethodField()
    has_collision = serializers.SerializerMethodField()
    collision_event_id = serializers.SerializerMethodField()
    collision_event_title = serializers.SerializerMethodField()
    collision_time = serializers.SerializerMethodField()
    collision_venue = serializers.SerializerMethodField()
    start_time = serializers.TimeField(required=False)
    end_time = serializers.TimeField(required=False)
    start_hour = serializers.IntegerField(min_value=0, max_value=23, required=False)
    start_minute = serializers.IntegerField(min_value=0, max_value=59, required=False)
    end_hour = serializers.IntegerField(min_value=0, max_value=23, required=False)
    end_minute = serializers.IntegerField(min_value=0, max_value=59, required=False)

    class Meta:
        model = ClassSchedule
        fields = (
            'id', 'day_of_week', 'start_time', 'end_time',
            'start_hour', 'start_minute', 'end_hour', 'end_minute', 'course_name',
            'credits', 'has_collision', 'collision_event_id', 'collision_event_title', 'collision_time', 'collision_venue'
        )
        read_only_fields = ('id', 'credits', 'has_collision', 'collision_event_id', 'collision_event_title', 'collision_time', 'collision_venue')

    def get_credits(self, obj): return 3

    def _collision(self, obj):
        from django.utils import timezone
        from .models import RSVP
        user = None
        req = self.context.get('request')
        if req and hasattr(req, 'user') and req.user.is_authenticated:
            user = req.user
        elif getattr(obj, 'user', None):
            user = obj.user
        if not user: return None
        week = {'MON':0,'TUE':1,'WED':2,'THU':3,'FRI':4,'SAT':5,'SUN':6}
        for rsvp in RSVP.objects.filter(user=user, status='CONFIRMED', event__start_time__isnull=False,
                                        event__end_time__gte=timezone.now()).select_related('event'):
            event = rsvp.event
            start, end = timezone.localtime(event.start_time), timezone.localtime(event.end_time)
            if start.weekday() == week.get(obj.day_of_week) and obj.start_time < end.time() and obj.end_time > start.time():
                return event
        return None

    def get_has_collision(self, obj): return self._collision(obj) is not None
    def get_collision_event_id(self, obj):
        e = self._collision(obj); return e.event_id if e else None
    def get_collision_event_title(self, obj):
        e = self._collision(obj); return e.title if e else None
    def get_collision_time(self, obj):
        from django.utils import timezone
        e = self._collision(obj)
        return f"{timezone.localtime(e.start_time):%I:%M %p} - {timezone.localtime(e.end_time):%I:%M %p}" if e else None
    def get_collision_venue(self, obj):
        e = self._collision(obj); return e.venue if e else None

    def validate(self, attrs):
        start_time = attrs.get('start_time')
        end_time = attrs.get('end_time')

        if start_time is None and 'start_hour' in attrs:
            start_time = dt_time(attrs.pop('start_hour'), attrs.pop('start_minute', 0))
            attrs['start_time'] = start_time
        else:
            attrs.pop('start_hour', None)
            attrs.pop('start_minute', None)

        if end_time is None and 'end_hour' in attrs:
            end_time = dt_time(attrs.pop('end_hour'), attrs.pop('end_minute', 0))
            attrs['end_time'] = end_time
        else:
            attrs.pop('end_hour', None)
            attrs.pop('end_minute', None)

        effective_start = start_time or (self.instance.start_time if self.instance else None)
        effective_end = end_time or (self.instance.end_time if self.instance else None)

        if not effective_start or not effective_end:
            raise serializers.ValidationError(
                'Provide either start_time/end_time or start_hour/start_minute and end_hour/end_minute.'
            )
        if effective_end <= effective_start:
            raise serializers.ValidationError({'end_time': 'Class end time must be after start time.'})

        day = attrs.get('day_of_week', getattr(self.instance, 'day_of_week', None))
        weekday = {
            'MON': [('07:00','08:40'),('09:00','10:40'),('11:00','12:40'),('13:20','15:00'),('15:30','17:10'),('17:30','19:10'),('19:20','21:00')],
            'TUE': [('07:00','08:40'),('09:00','10:40'),('11:00','12:40'),('13:20','15:00'),('15:30','17:10'),('17:30','19:10'),('19:20','21:00')],
            'WED': [('07:00','08:40'),('09:00','10:40'),('11:00','12:40'),('13:20','15:00'),('15:30','17:10'),('17:30','19:10'),('19:20','21:00')],
            'THU': [('07:00','08:40'),('09:00','10:40'),('11:00','12:40'),('13:20','15:00'),('15:30','17:10'),('17:30','19:10'),('19:20','21:00')],
            'FRI': [('08:00','11:00'),('13:30','16:50'),('17:40','21:00')],
            'SAT': [('09:00','12:00'),('13:30','16:50')],
        }
        if day not in weekday or (effective_start.strftime('%H:%M'), effective_end.strftime('%H:%M')) not in weekday[day]:
            raise serializers.ValidationError({'start_time': 'Select an official USIU-Africa class time for this day.'})

        return attrs


def validate_school_and_major(school_code, major):
    if school_code:
        school = School.objects.filter(code=school_code).first()
        if school and major:
            valid_majors = school.get_majors_list()
            if valid_majors and major not in valid_majors:
                raise serializers.ValidationError(
                    {'major': f"'{major}' is not a valid major for school '{school_code}'."}
                )


class UserSerializer(serializers.ModelSerializer):
    schedules = ClassScheduleSerializer(many=True, read_only=True)
    total_courses = serializers.SerializerMethodField()
    total_credits = serializers.SerializerMethodField()
    total_collisions = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = (
            'id', 'username', 'email', 'first_name', 'last_name',
            'school_code', 'major', 'student_id', 'schedules', 'total_courses', 'total_credits', 'total_collisions'
        )
        read_only_fields = ('id', 'username')

    def validate_email(self, value):
        value = (value or '').strip() or None
        if value and User.objects.exclude(pk=self.instance.pk if self.instance else None).filter(email__iexact=value).exists():
            raise serializers.ValidationError('A user with this email already exists.')
        return value

    def validate_student_id(self, value):
        value = (value or '').strip() or None
        if value and User.objects.exclude(pk=self.instance.pk if self.instance else None).filter(student_id=value).exists():
            raise serializers.ValidationError('A user with this student ID already exists.')
        return value

    def validate(self, attrs):
        if self.instance and self.instance.school_code and 'school_code' in attrs and attrs['school_code'] != self.instance.school_code:
            raise serializers.ValidationError({'school_code': 'Academic School is permanent and confirmed by the Registrar.'})
        if self.instance and self.instance.major and 'major' in attrs and attrs['major'] != self.instance.major:
            raise serializers.ValidationError({'major': 'Degree Program (major) is constant and confirmed by the Registrar.'})
        school_code = attrs.get('school_code', self.instance.school_code if self.instance else None)
        major = attrs.get('major', self.instance.major if self.instance else None)
        validate_school_and_major(school_code, major)
        return attrs

    def get_total_courses(self, obj):
        return obj.schedules.values('course_name').distinct().count()

    def get_total_credits(self, obj): return self.get_total_courses(obj) * 3

    def get_total_collisions(self, obj):
        request = self.context.get('request')
        if not request: return 0
        schedules = ClassScheduleSerializer(obj.schedules.all(), many=True, context=self.context).data
        return sum(1 for slot in schedules if slot['has_collision'])


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, validators=[validate_password])

    class Meta:
        model = User
        fields = ('username', 'password', 'email', 'first_name', 'last_name', 'school_code', 'major', 'student_id')

    def validate_email(self, value):
        value = (value or '').strip() or None
        if value and User.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError('A user with this email already exists.')
        return value

    def validate_student_id(self, value):
        value = (value or '').strip() or None
        if value and User.objects.filter(student_id=value).exists():
            raise serializers.ValidationError('A user with this student ID already exists.')
        return value

    def validate(self, attrs):
        validate_school_and_major(attrs.get('school_code'), attrs.get('major'))
        return attrs

    def create(self, validated_data):
        return User.objects.create_user(
            username=validated_data['username'],
            email=validated_data.get('email') or None,
            first_name=validated_data.get('first_name', ''),
            last_name=validated_data.get('last_name', ''),
            school_code=validated_data.get('school_code') or None,
            major=validated_data.get('major') or None,
            student_id=validated_data.get('student_id') or None,
            password=validated_data['password'],
        )


class SchoolSerializer(serializers.ModelSerializer):
    majors_list = serializers.SerializerMethodField()

    class Meta:
        model = School
        fields = ('id', 'code', 'name', 'majors', 'majors_list')

    def get_majors_list(self, obj):
        return obj.get_majors_list()


class EventSerializer(serializers.ModelSerializer):
    start_millis = serializers.IntegerField(required=False)
    end_millis = serializers.IntegerField(required=False)
    start_time = serializers.DateTimeField(required=False)
    end_time = serializers.DateTimeField(required=False)
    is_saved = serializers.SerializerMethodField()
    is_rsvped = serializers.SerializerMethodField()

    class Meta:
        model = Event
        fields = (
            'id', 'event_id', 'title', 'description', 'school_code', 'major', 'event_type',
            'start_time', 'end_time', 'start_millis', 'end_millis', 'venue',
            'total_seats', 'seats_left', 'organizer', 'image', 'image_url', 'video', 'is_saved', 'is_rsvped'
        )
        read_only_fields = ('id', 'event_id', 'seats_left', 'is_saved', 'is_rsvped')

    def validate(self, attrs):
        video = attrs.get('video', self.instance.video if self.instance else None)
        if video and hasattr(video, 'size'):
            if video.size > 50 * 1024 * 1024:
                raise serializers.ValidationError({'video': 'Video file size cannot exceed 50MB.'})
            name_lower = video.name.lower()
            if not any(name_lower.endswith(ext) for ext in ['.mp4', '.mov', '.webm', '.m4v']):
                raise serializers.ValidationError({'video': 'Unsupported video format. Permitted formats: MP4, MOV, WEBM, M4V.'})
        if 'start_millis' in attrs and 'start_time' not in attrs:
            temp = Event()
            temp.start_millis = attrs.pop('start_millis')
            attrs['start_time'] = temp.start_time
        else:
            attrs.pop('start_millis', None)

        if 'end_millis' in attrs and 'end_time' not in attrs:
            temp = Event()
            temp.end_millis = attrs.pop('end_millis')
            attrs['end_time'] = temp.end_time
        else:
            attrs.pop('end_millis', None)

        start_time = attrs.get('start_time', self.instance.start_time if self.instance else None)
        end_time = attrs.get('end_time', self.instance.end_time if self.instance else None)
        if not start_time or not end_time:
            raise serializers.ValidationError('Both start_time (or start_millis) and end_time (or end_millis) are required.')
        if end_time <= start_time:
            raise serializers.ValidationError({'end_time': 'Event end time must be after start time.'})

        total_seats = attrs.get('total_seats', self.instance.total_seats if self.instance else 100)
        if self.instance:
            confirmed_count = RSVP.objects.filter(
                event=self.instance, status=RSVP.Status.CONFIRMED
            ).count()
            if total_seats is not None and total_seats < confirmed_count:
                raise serializers.ValidationError({
                    'total_seats': f'Total seats cannot be less than confirmed RSVPs ({confirmed_count}).'
                })

        validate_school_and_major(
            attrs.get('school_code', self.instance.school_code if self.instance else None),
            attrs.get('major', self.instance.major if self.instance else None),
        )
        return attrs

    def create(self, validated_data):
        if self.initial_data is None or 'total_seats' not in self.initial_data:
            validated_data['total_seats'] = 100
        total_seats = validated_data.get('total_seats')
        validated_data['seats_left'] = total_seats
        return super().create(validated_data)

    def update(self, instance, validated_data):
        capacity_changed = 'total_seats' in validated_data
        instance = super().update(instance, validated_data)
        if capacity_changed:
            if instance.total_seats is None:
                instance.seats_left = None
            else:
                confirmed_count = RSVP.objects.filter(
                    event=instance, status=RSVP.Status.CONFIRMED
                ).count()
                instance.seats_left = max(0, instance.total_seats - confirmed_count)
            instance.save(update_fields=['seats_left'])
        return instance

    def get_is_saved(self, obj):
        if hasattr(obj, '_is_saved'):
            return bool(obj._is_saved)
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            return Bookmark.objects.filter(user=request.user, event=obj).exists()
        return False

    def get_is_rsvped(self, obj):
        if hasattr(obj, '_is_rsvped'):
            return bool(obj._is_rsvped)
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            return RSVP.objects.filter(
                user=request.user, event=obj, status=RSVP.Status.CONFIRMED
            ).exists()
        return False


class FixtureSerializer(serializers.ModelSerializer):
    time_millis = serializers.IntegerField(required=False)
    start_millis = serializers.IntegerField(required=False)
    end_millis = serializers.IntegerField(required=False)
    start_time = serializers.DateTimeField(required=False)
    end_time = serializers.DateTimeField(required=False)
    status = serializers.SerializerMethodField()
    is_demo = serializers.SerializerMethodField()
    scorers = serializers.SerializerMethodField()

    class Meta:
        model = Fixture
        fields = (
            'id', 'fixture_id', 'team_id', 'team_name', 'sport', 'opponent',
            'start_time', 'end_time', 'time_millis', 'start_millis', 'end_millis',
            'venue', 'league', 'fan_count', 'score_usiu', 'score_opponent', 'is_home',
            'status', 'is_demo', 'scorers'
        )
        read_only_fields = ('id', 'fixture_id')

    def validate(self, attrs):
        if 'start_millis' not in attrs and 'time_millis' in attrs:
            attrs['start_millis'] = attrs.pop('time_millis')
        else:
            attrs.pop('time_millis', None)

        if 'start_millis' in attrs and 'start_time' not in attrs:
            temp = Fixture()
            temp.start_millis = attrs.pop('start_millis')
            attrs['start_time'] = temp.start_time
        else:
            attrs.pop('start_millis', None)

        if 'end_millis' in attrs and 'end_time' not in attrs:
            temp = Fixture()
            temp.end_millis = attrs.pop('end_millis')
            attrs['end_time'] = temp.end_time
        else:
            attrs.pop('end_millis', None)

        start_time = attrs.get('start_time', self.instance.start_time if self.instance else None)
        end_time = attrs.get('end_time', self.instance.end_time if self.instance else None)
        if start_time and not end_time:
            from datetime import timedelta
            end_time = start_time + timedelta(hours=2)
            attrs['end_time'] = end_time

        if not start_time or not end_time:
            raise serializers.ValidationError('Fixture start_time (or start_millis/time_millis) is required.')
        if end_time <= start_time:
            raise serializers.ValidationError({'end_time': 'Fixture end time must be after start time.'})
        return attrs

    def get_status(self, obj):
        from django.utils import timezone
        if (obj.score_usiu is not None and obj.score_opponent is not None
                and obj.end_time <= timezone.now()):
            return 'completed'
        return 'upcoming'

    def get_is_demo(self, obj):
        return 'demo' in (obj.league or '').lower()

    def get_scorers(self, obj):
        if (obj.sport.casefold() != 'football' or obj.end_time > timezone.now()
                or obj.score_usiu is None or obj.score_opponent is None):
            return []
        return FixtureScorerSerializer(obj.scorers.all(), many=True).data

    def to_representation(self, instance):
        data = super().to_representation(instance)
        from django.utils import timezone
        if instance.end_time > timezone.now():
            # A prematurely entered score must never make a future fixture
            # look completed or appear as a result in clients.
            data['score_usiu'] = None
            data['score_opponent'] = None
        return data


class FixtureScorerSerializer(serializers.ModelSerializer):
    display_minute = serializers.CharField(read_only=True)

    class Meta:
        model = FixtureScorer
        fields = ('id', 'player_name', 'minute', 'added_minute', 'display_minute')


class TeamSerializer(serializers.ModelSerializer):
    class Meta:
        model = Team
        fields = ('id', 'team_id', 'name', 'sport', 'competition')


class RSVPRequestSerializer(serializers.Serializer):
    email = serializers.EmailField(required=False, allow_blank=True, allow_null=True)
    student_id = serializers.CharField(max_length=50, required=False, allow_blank=True, allow_null=True)
    notes = serializers.CharField(max_length=500, required=False, allow_blank=True, allow_null=True)


class NotificationSerializer(serializers.ModelSerializer):
    class Meta:
        model = Notification
        fields = (
            'id', 'title', 'message', 'category', 'source', 'time_label',
            'is_priority', 'is_read', 'is_dismissed', 'event_id',
            'action_label', 'created_at', 'updated_at'
        )

