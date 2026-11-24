from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from api.models import Event, School


WEEK_START = date(2026, 11, 23)  # Monday, Week 12
LOCAL_TZ = ZoneInfo("Africa/Nairobi")

# Illustrative sample campus activities for the Week 12 app dataset.
# They are not confirmed or official university announcements.
# Fields: day, time, duration_hours, category, title, description,
#         school_code, major, venue, capacity, organizer
EVENTS = [
    # MONDAY — 9 events
    (0, "08:30", 2, "Academic", "Research Skills: From Question to Proposal",
     "Learn how to turn an area of interest into a focused research question, build a practical proposal outline, and identify credible academic sources.",
     "SGSRE", "Graduate Studies (Postgraduate diplomas, Masters, PhD)", "Library Training Room", 60, "Research Students Network"),
    (0, "10:30", 2, "Career", "CV Clinic and LinkedIn Profile Lab",
     "Get practical guidance on structuring a clear CV, presenting projects and experience, and improving your professional online profile.",
     "CSOB", "International Business Administration (IBA)", "Business School Room 2", 45, "Career Development Centre"),
    (0, "12:30", 1.5, "Wellness", "Mindful Monday: Reset and Refocus",
     "Take a guided break with simple breathing, grounding, and stress-management exercises designed for a busy study week.",
     None, None, "Student Centre", 40, "Student Wellness Team"),
    (0, "14:00", 2, "Social", "Campus Board Games Meetup",
     "Meet other students over friendly rounds of chess, Scrabble, cards, and quick party games. Beginners are welcome.",
     None, None, "Student Centre Lounge", 50, "Campus Activities Club"),
    (0, "16:00", 2, "Sports", "Five-a-Side Football: Open Play",
     "Join mixed teams for relaxed five-a-side matches. Bring sports shoes, water, and a willingness to play.",
     None, None, "Main Sports Grounds", 40, "Sports and Recreation"),
    (0, "09:00", 2, "Academic", "Data Storytelling with Spreadsheets",
     "Explore how to clean a small dataset, choose suitable charts, and communicate findings clearly using spreadsheet tools.",
     "SST", "Data Science and Analytics (DSA)", "SST Computer Lab", 35, "Data Science Society"),
    (0, "11:00", 2, "Career", "Careers in Digital Marketing and Communications",
     "Hear practical perspectives on entry-level roles, portfolios, analytics, and the skills employers look for in digital communications.",
     "SCCCA", "Journalism (Broadcast, Print, Public Relations)", "SCCCA Seminar Room", 70, "Media and Communications Society"),
    (0, "13:00", 1.5, "Wellness", "Healthy Study Habits Workshop",
     "Build a realistic study routine with tips on breaks, sleep consistency, focus, and balancing coursework with other commitments.",
     None, None, "Classroom Block Seminar Room", 55, "Peer Support Network"),
    (0, "15:00", 2, "Social", "International Student Connections Hour",
     "Share stories, discover student communities, and make new connections through informal conversation and icebreaker activities.",
     None, None, "Student Centre", 80, "International Students Association"),

    # TUESDAY — 9 events
    (1, "08:30", 2, "Academic", "Introduction to Cybersecurity Careers",
     "Understand core cybersecurity domains, common security responsibilities, and the learning pathways that can help students enter the field.",
     "SST", "Software Engineering (SWE)", "SST Lecture Theatre", 80, "Cybersecurity Club"),
    (1, "10:30", 2, "Sports", "Campus Esports Strategy Tournament",
     "Compete in friendly team-based gaming rounds that reward coordination, quick thinking, and sportsmanship. Open to beginners and experienced players.",
     None, None, "Student Centre Gaming Area", 60, "Esports and Gaming Club"),
    (1, "12:30", 1.5, "Wellness", "Move More: Campus Walking Group",
     "Take a social guided walk around campus at a comfortable pace and learn simple ways to add movement to a study day.",
     None, None, "Main Campus Meeting Point", 35, "Student Wellness Team"),
    (1, "14:00", 2, "Sports", "Basketball Skills and Shooting Challenge",
     "Practise passing, footwork, and shooting drills before a friendly skills challenge open to different experience levels.",
     None, None, "Basketball Court", 50, "Sports and Recreation"),
    (1, "16:00", 2, "Social", "Creative Writing and Spoken Word Circle",
     "Bring a poem, short story, or unfinished idea to share in a supportive creative space. Listening is welcome too.",
     "SCCCA", "Journalism (Broadcast, Print, Public Relations)", "Arts Studio", 35, "Literary and Arts Society"),
    (1, "09:00", 2, "Academic", "Understanding Research Ethics",
     "Discuss informed consent, privacy, responsible data handling, and ethical decision-making when conducting student research.",
     "SGSRE", "Graduate Studies (Postgraduate diplomas, Masters, PhD)", "Library Training Room", 50, "Research Students Network"),
    (1, "11:00", 2, "Career", "Entrepreneurship: Validate Your Business Idea",
     "Use simple customer-discovery techniques to test assumptions, understand a target audience, and refine an early business idea.",
     "CSOB", "International Business Administration (IBA)", "Business School Room 2", 65, "Entrepreneurship Club"),
    (1, "13:00", 1.5, "Wellness", "Nutrition on a Student Budget",
     "Explore practical meal planning, hydration, and affordable food choices that can support energy during a demanding semester.",
     "SPHS", "BSc in Nursing (BScN)", "Health Sciences Seminar Room", 45, "Health Sciences Students Association"),
    (1, "15:00", 2, "Sports", "Table Tennis Ladder Tournament",
     "Compete in short table-tennis matches, meet fellow players, and take part in a friendly campus ladder tournament.",
     None, None, "Indoor Recreation Area", 32, "Sports and Recreation"),

    # WEDNESDAY — 10 events
    (2, "08:30", 2, "Academic", "Building Accessible Mobile Interfaces",
     "Explore mobile accessibility principles, readable layouts, touch targets, and practical checks for more inclusive app experiences.",
     "SST", "Applied Computer Technology (APT)", "SST Computer Lab", 40, "Mobile Developers Society"),
    (2, "10:30", 2, "Career", "Mock Interviews: Practice and Feedback",
     "Practise common interview questions in short sessions and receive constructive feedback on examples, clarity, and confidence.",
     None, None, "Career Centre", 40, "Career Development Centre"),
    (2, "12:30", 1.5, "Social", "Culture Exchange: Music and Stories",
     "Discover music, traditions, and personal stories from different communities in a welcoming student-led cultural exchange.",
     None, None, "Student Centre", 100, "International Students Association"),
    (2, "14:00", 2, "Sports", "Volleyball Mixed-Team Challenge",
     "Form mixed teams for friendly volleyball sets focused on participation, teamwork, and good sportsmanship.",
     None, None, "Outdoor Volleyball Court", 48, "Sports and Recreation"),
    (2, "16:00", 1.5, "Wellness", "Digital Wellbeing and Screen Breaks",
     "Reflect on screen habits and learn practical ways to manage notifications, reduce digital fatigue, and take intentional breaks.",
     None, None, "Student Centre Seminar Room", 50, "Student Wellness Team"),
    (2, "09:00", 2, "Academic", "Introduction to Financial Modelling",
     "Work through the foundations of assumptions, basic forecasts, and spreadsheet models used to explore business decisions.",
     "CSOB", "Finance", "Business School Room 1", 45, "Finance Students Association"),
    (2, "11:00", 2, "Career", "Portfolio Reviews for Creative Students",
     "Get feedback on how to organise creative work, explain your process, and present selected projects to potential employers.",
     "SCCCA", "Animation", "Creative Arts Studio", 35, "Creative Careers Network"),
    (2, "13:00", 1.5, "Sports", "Badminton Doubles Social",
     "Enjoy rotating doubles games in a relaxed setting. Equipment can be shared while available.",
     None, None, "Indoor Sports Hall", 36, "Sports and Recreation"),
    (2, "15:00", 1.5, "Wellness", "Guided Stretch and Mobility Session",
     "Follow gentle mobility and stretching exercises suitable for students who spend long hours sitting in lectures or studying.",
     None, None, "Multipurpose Hall", 40, "Student Wellness Team"),
    (2, "17:00", 2, "Social", "Student Talent Showcase",
     "Celebrate student creativity through short performances, music, dance, comedy, and other talents in an informal showcase.",
     "SCCCA", "Film Production and Directing", "Auditorium", 150, "Campus Activities Club"),

    # THURSDAY — 9 events
    (3, "08:30", 2, "Academic", "Cloud Computing Fundamentals",
     "Get an overview of cloud service models, deployment approaches, and practical use cases for modern software systems.",
     "SST", "Information Systems & Technology (IST)", "SST Lecture Theatre", 70, "Cloud Computing Society"),
    (3, "10:30", 2, "Career", "Meet the Professionals: Supply Chain and Logistics",
     "Explore career paths in procurement, operations, logistics, and supply chain analytics through a practical industry discussion.",
     "CSOB", "International Business Administration (IBA)", "Business School Auditorium", 90, "Business Students Association"),
    (3, "12:30", 1.5, "Wellness", "Stress Management Peer Circle",
     "Join a facilitated peer discussion about academic pressure, coping strategies, and where to find student support resources.",
     None, None, "Student Support Room", 25, "Peer Support Network"),
    (3, "14:00", 2, "Sports", "Chess Blitz and Strategy Tournament",
     "Play timed chess rounds, test your strategy, and meet other players in a friendly open tournament.",
     None, None, "Student Centre Lounge", 40, "Chess Club"),
    (3, "16:00", 2, "Social", "Film Appreciation and Discussion",
     "Watch a selected short film and join a moderated conversation about storytelling, representation, and visual language.",
     "SCCCA", "Film Production and Directing", "Screening Room", 60, "Film and Media Society"),
    (3, "09:00", 2, "Academic", "Biostatistics: Reading Data with Confidence",
     "Review introductory concepts for interpreting data, understanding variation, and asking sound questions about health-related evidence.",
     "SPHS", "Epidemiology and Biostatistics", "Health Sciences Seminar Room", 45, "Health Sciences Students Association"),
    (3, "11:00", 2, "Career", "Personal Branding for Early-Career Professionals",
     "Identify your strengths, communicate your value, and create a consistent professional introduction for networking opportunities.",
     None, None, "Career Centre", 55, "Career Development Centre"),
    (3, "13:00", 1.5, "Wellness", "First Aid Awareness and Everyday Safety",
     "Learn basic emergency awareness, how to seek appropriate help, and simple steps for responding safely while waiting for trained assistance.",
     "SPHS", "BSc in Nursing (BScN)", "Health Sciences Demonstration Room", 40, "Nursing Students Association"),
    (3, "15:00", 2, "Sports", "Track and Field Fun Meet",
     "Take part in short running, relay, and field activities designed for participation, movement, and team spirit.",
     None, None, "Athletics Track", 60, "Sports and Recreation"),

    # FRIDAY — 9 events
    (4, "08:30", 2, "Social", "Campus Conversation: AI and Society",
     "Join an open student conversation about how artificial intelligence is changing everyday life, communities, and the way we relate to technology.",
     "SHSS", "Sociology", "Humanities Seminar Room", 75, "Technology and Society Forum"),
    (4, "10:30", 2, "Career", "Career Fair: Graduate Opportunities",
     "Explore sample graduate pathways across business, technology, communications, and health-related fields, and practise introducing yourself to recruiters.",
     None, None, "Multipurpose Hall", 180, "Career Development Centre"),
    (4, "12:30", 1.5, "Social", "Friday Campus Picnic and Connect",
     "Wind down the week with lawn games, conversation, and a bring-your-own-snack campus picnic.",
     None, None, "Campus Green", 100, "Campus Activities Club"),
    (4, "14:00", 2, "Sports", "Futsal Friendly: Students' Open Match",
     "Join an open futsal session with rotating teams and short matches. All skill levels are welcome.",
     None, None, "Indoor Sports Hall", 50, "Sports and Recreation"),
    (4, "16:00", 1.5, "Wellness", "Friday Wind-Down: Relaxation Session",
     "Close the week with gentle breathing, guided relaxation, and practical ideas for restoring energy before the weekend.",
     None, None, "Multipurpose Hall", 45, "Student Wellness Team"),
    (4, "09:00", 2, "Social", "Campus Public Speaking Circle",
     "Build confidence in a friendly student-led speaking circle with short talks, fun prompts, and supportive peer feedback.",
     "SHSS", "International Relations (IR)", "Humanities Lecture Room", 60, "Communication Skills Society"),
    (4, "11:00", 2, "Career", "Freelancing and Building a Client Portfolio",
     "Learn how to define a service, present sample work, communicate with clients, and manage expectations as a beginner freelancer.",
     "SCCCA", "Journalism (Broadcast, Print, Public Relations)", "Creative Arts Studio", 50, "Young Professionals Network"),
    (4, "13:00", 1.5, "Sports", "Yoga and Recreational Fitness",
     "Take part in a beginner-friendly movement session combining simple yoga-inspired stretches and low-impact fitness activities.",
     None, None, "Outdoor Recreation Area", 40, "Sports and Recreation"),
    (4, "15:00", 1.5, "Wellness", "Healthy Relationships and Peer Support",
     "Explore respectful communication, boundaries, supportive friendships, and ways to connect with campus support services.",
     "SHSS", "Psychology", "Humanities Seminar Room", 40, "Student Wellness Team"),
]


class Command(BaseCommand):
    help = (
        "Preview or import 46 illustrative Week 12 campus events. "
        "Preview is the default; use --apply to write to the database."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "--apply",
            action="store_true",
            help="Create missing events in the database. Without this flag, only preview.",
        )

    def handle(self, *args, **options):
        if len(EVENTS) != 46:
            raise CommandError(f"Expected 46 event definitions, found {len(EVENTS)}.")

        expected = {
            "Academic": 8,
            "Career": 9,
            "Social": 9,
            "Sports": 10,
            "Wellness": 10,
        }
        counts = {}
        for row in EVENTS:
            counts[row[3]] = counts.get(row[3], 0) + 1
        if counts != expected:
            raise CommandError(f"Category distribution is incorrect: {counts}")

        missing_schools = sorted({
            row[6] for row in EVENTS if row[6] and not School.objects.filter(code=row[6]).exists()
        })
        if missing_schools:
            raise CommandError(f"These school codes do not exist: {', '.join(missing_schools)}")

        self.stdout.write(self.style.MIGRATE_HEADING(
            "CampusPulse Week 12 event import — 23 to 27 November 2026"
        ))
        self.stdout.write("All times are Africa/Nairobi. Event images will be left blank.")
        self.stdout.write("These are illustrative sample activities, not confirmed university announcements.\n")

        existing_ids = set(Event.objects.filter(
            event_id__startswith="w12_"
        ).values_list("event_id", flat=True))

        prepared = []
        for index, row in enumerate(EVENTS, start=1):
            day, clock, duration, category, title, description, school, major, venue, capacity, organizer = row
            event_id = f"w12_{index:03d}"
            local_date = WEEK_START + timedelta(days=day)
            hour, minute = map(int, clock.split(":"))
            start_naive = datetime.combine(local_date, time(hour, minute))
            start = timezone.make_aware(start_naive, LOCAL_TZ)
            end = start + timedelta(minutes=int(duration * 60))
            prepared.append({
                "event_id": event_id,
                "title": title,
                "description": description,
                "school_code": school,
                "major": major,
                "event_type": category,
                "start_time": start,
                "end_time": end,
                "venue": venue,
                "total_seats": capacity,
                "seats_left": capacity,
                "organizer": organizer,
            })

        missing = [event for event in prepared if event["event_id"] not in existing_ids]
        already_present = len(prepared) - len(missing)

        for event in prepared:
            status = "EXISTS — skip" if event["event_id"] in existing_ids else "NEW"
            self.stdout.write(
                f'{status:12} {event["event_id"]} | '
                f'{event["start_time"].strftime("%a %d %b %H:%M")} | '
                f'{event["event_type"]:8} | {event["title"]}'
            )

        self.stdout.write("")
        self.stdout.write(f"Definitions: {len(prepared)} | Already imported: {already_present} | To create: {len(missing)}")
        if not options["apply"]:
            self.stdout.write(self.style.WARNING(
                "PREVIEW ONLY — no database changes made. Review the list, then rerun with --apply to import."
            ))
            return

        if not missing:
            self.stdout.write(self.style.SUCCESS("Nothing to import; all Week 12 event IDs already exist."))
            return

        with transaction.atomic():
            for event in missing:
                # A second existence check protects against an already-present stable ID.
                if Event.objects.filter(event_id=event["event_id"]).exists():
                    continue
                Event.objects.create(**event)

        self.stdout.write(self.style.SUCCESS(
            f"Import complete: created {len(missing)} events. Existing records were not modified."
        ))
        self.stdout.write("All imported event image fields are blank for later admin-panel uploads.")
