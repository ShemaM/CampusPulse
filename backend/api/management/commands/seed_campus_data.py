import os
import secrets
from datetime import time, timedelta

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from api.models import ClassSchedule, Event, Fixture, School, User

SCHOOLS = [
    ("CSOB", "Chandaria School of Business", "Accounting, Finance, International Business Administration (IBA), Hotel and Restaurant Management (HRM), Global Leadership and Governance"),
    ("SST", "School of Science and Technology", "Software Engineering (SWE), Applied Computer Technology (APT), Information Systems & Technology (IST), Data Science and Analytics (DSA)"),
    ("SHSS", "School of Humanities and Social Sciences", "International Relations (IR), Psychology, Criminal Justice Studies, Sociology"),
    ("SPHS", "School of Pharmacy and Health Sciences", "Bachelor of Pharmacy (BPharm), BSc in Nursing (BScN), Applied Biochemistry, Analytical Chemistry, Epidemiology and Biostatistics"),
    ("SCCCA", "School of Communication, Cinematic and Creative Arts", "Animation, Film Production and Directing, Journalism (Broadcast, Print, Public Relations)"),
    ("SGSRE", "School of Graduate Studies, Research and Extension", "Graduate Studies (Postgraduate diplomas, Masters, PhD)"),
]

H = timedelta(hours=1)
D = timedelta(days=1)

# (event_id, title, description, school, major, type, start_offset, duration, venue, total, left, organizer)
EVENTS = [
    ("evt_now", "Open Mic and Coffee Hour", "Come relax with live music, poetry, and free coffee between classes.", None, None, "Social", -30 * H / 60, 2 * H, "Student Center", 80, 32, "Student Council"),
    ("evt_hack", "USIU CodeSprint 2026", "24-hour hackathon building smart campus solutions with AI and cloud tech.", "SST", "Software Engineering (SWE)", "Academic", 2 * D, 8 * H, "SSHS LT8", 120, 14, "Tech Club"),
    ("evt_fin", "Fintech & Investment Panel", "Industry leaders discuss digital banking, crypto regulations, and career paths.", "CSOB", "Finance", "Career", 1 * D, 3 * H, "Auditoriam", 250, 45, "CSOB Student Association"),
    ("evt_arts", "Cinematic Arts Film Showcase", "Screening award-winning short films produced by USIU student directors.", "SCCCA", "Film Production and Directing", "Social", 3 * D, 4 * H, "Student Center", 200, 88, "Film & Media Club"),
    ("evt_basket", "KBF League: Tigers vs Strathmore", "Cheer on the men's basketball team as they battle for top spot in the league.", None, None, "Sports", 4 * D, 2 * H, "Basketball Court", 300, 12, "Sports Department"),
]

# (fixture_id, team_id, team_name, sport, opponent, start_offset, venue, usiu_score, opp_score)
FIXTURES = [
    ("fix_1", "team_tigers", "Tigers", "Basketball", "Strathmore Blades", -2 * D, "Basketball Court", 78, 65),
    ("fix_2", "team_flames", "Flames", "Basketball", "Equity Hawks", -1 * D, "Basketball Court", 65, 58),
    ("fix_3", "team_mashujaa", "Mashujaa", "Hockey", "Wazalendo", 3 * D, "School Pitch", None, None),
    ("fix_4", "team_spartans", "Spartans", "Hockey", "Telkom", 4 * D, "School Pitch", None, None),
]

CLASSES = [
    ("MON", time(9, 0), time(10, 40), "Software Engineering (SWE)"),
    ("MON", time(15, 30), time(17, 10), "Database Systems"),
    ("WED", time(9, 0), time(10, 40), "Software Engineering (SWE)"),
    ("WED", time(15, 30), time(17, 10), "Database Systems"),
    ("TUE", time(15, 30), time(17, 10), "Distributed Systems"),
    ("TUE", time(17, 30), time(19, 10), "Cloud Computing"),
    ("THU", time(15, 30), time(17, 10), "Distributed Systems"),
    ("THU", time(17, 30), time(19, 10), "Cloud Computing"),
]


class Command(BaseCommand):
    help = "Seeds development data: schools, events, fixtures, and a demo student. Development only."

    def add_arguments(self, parser):
        parser.add_argument("--reset", action="store_true",
                            help="Delete existing events, fixtures and schools first.")

    @transaction.atomic
    def handle(self, *args, **options):
        if not settings.DEBUG:
            raise CommandError("seed_campus_data is development-only (DEBUG must be True).")

        if options["reset"]:
            Event.objects.all().delete()
            Fixture.objects.all().delete()
            School.objects.all().delete()

        now = timezone.now()

        for code, name, majors in SCHOOLS:
            School.objects.update_or_create(code=code, defaults={"name": name, "majors": majors})

        for (eid, title, desc, school, major, etype, offset, duration,
             venue, total, left, organizer) in EVENTS:
            start = now + offset
            Event.objects.update_or_create(event_id=eid, defaults={
                "title": title, "description": desc, "school_code": school, "major": major,
                "event_type": etype, "start_time": start, "end_time": start + duration,
                "venue": venue, "total_seats": total, "seats_left": left, "organizer": organizer,
            })

        for fid, team_id, team, sport, opp, offset, venue, s_usiu, s_opp in FIXTURES:
            start = now + offset
            Fixture.objects.update_or_create(fixture_id=fid, defaults={
                "team_id": team_id, "team_name": team, "sport": sport, "opponent": opp,
                "start_time": start, "end_time": start + 2 * H, "venue": venue,
                "score_usiu": s_usiu, "score_opponent": s_opp, "is_home": True,
            })

        admin, created = User.objects.get_or_create(
            username="admin",
            defaults={"email": "admin@campuspulse.ac.ke", "is_staff": True, "is_superuser": True},
        )
        if created:
            password = os.environ.get("CAMPUSPULSE_ADMIN_PASSWORD") or secrets.token_urlsafe(12)
            admin.set_password(password)
            admin.save()
            self.stdout.write(self.style.WARNING(f"Admin created: admin / {password} (shown once)"))

        student, created = User.objects.get_or_create(
            username="student",
            defaults={"email": "student@usiu.ac.ke", "school_code": "SST",
                      "major": "Software Engineering (SWE)"},
        )
        if created:
            student.set_password("student123")
            student.save()
            self.stdout.write("Demo student created: student / student123 (dev only)")

        student.schedules.all().delete()
        ClassSchedule.objects.bulk_create(
            ClassSchedule(user=student, day_of_week=d, start_time=s, end_time=e, course_name=c)
            for d, s, e, c in CLASSES
        )

        self.stdout.write(self.style.SUCCESS("Seeded CampusPulse development data."))