from datetime import time

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from api.models import ClassSchedule, Event, RSVP, User


class Command(BaseCommand):
    help = "Replace sampleUser's timetable with the official USIU demonstration courses and RSVP collision."

    @transaction.atomic
    def handle(self, *args, **options):
        user = User.objects.filter(pk=7, username="sampleUser").first()
        if not user:
            raise CommandError("Expected sampleUser with id=7 was not found.")

        ClassSchedule.objects.filter(user=user).delete()
        courses = [
            ("SWE 3010: Software Architecture", ("TUE", "THU"), time(11), time(12, 40)),
            ("APT 2040: Operating Systems", ("MON", "WED"), time(9), time(10, 40)),
            ("IST 3020: Web Development", ("TUE", "THU"), time(15, 30), time(17, 10)),
        ]
        for name, days, starts, ends in courses:
            for day in days:
                ClassSchedule.objects.create(user=user, course_name=name, day_of_week=day,
                                             start_time=starts, end_time=ends)

        event = Event.objects.filter(event_id="w12_016").first()
        if not event:
            raise CommandError("Timetable seeded, but event w12_016 is missing; RSVP was not created.")
        local_tz = timezone.get_current_timezone()
        expected_start = timezone.make_aware(timezone.datetime(2026, 11, 24, 11), local_tz)
        if timezone.localtime(event.start_time) != expected_start:
            self.stderr.write("Warning: w12_016 does not start at the specified Tuesday 11:00 local time.")
        RSVP.objects.update_or_create(user=user, event=event, defaults={"status": RSVP.Status.CONFIRMED})
        self.stdout.write(self.style.SUCCESS("Seeded 6 USIU timetable slots and confirmed the w12_016 RSVP."))
