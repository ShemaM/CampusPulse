from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

from django.core.management.base import BaseCommand
from django.db import transaction
from django.utils import timezone

from api.models import Fixture


LOCAL_TZ = ZoneInfo("Africa/Nairobi")
START_DATE = date(2026, 8, 1)
END_DATE = date(2026, 12, 31)

OPPONENTS = [
    "Strathmore University",
    "University of Nairobi",
    "Kenyatta University",
    "JKUAT",
    "Daystar University",
    "KCA University",
    "Mount Kenya University",
    "Zetech University",
]

VENUES = [
    "USIU-Africa Main Sports Grounds",
    "USIU-Africa Basketball Court",
    "USIU-Africa Indoor Sports Hall",
    "USIU-Africa Rugby Grounds",
    "USIU-Africa Outdoor Courts",
    "University of Nairobi Sports Grounds",
    "Kenyatta University Sports Complex",
    "Strathmore University Sports Grounds",
]

SPORTS = ["Football", "Basketball", "Volleyball", "Rugby", "Netball"]


def simulated_score(sport, week, slot):
    """Deterministic illustrative scores, not official match results."""
    a = (week * 3 + slot * 2) % 7
    b = (week * 2 + slot * 3 + 1) % 7

    if sport == "Football":
        return a % 5, b % 5
    if sport == "Basketball":
        return 55 + (a * 5), 48 + (b * 6)
    if sport == "Volleyball":
        usiu = 3
        opponent = (week + slot) % 3
        return usiu, opponent
    if sport == "Rugby":
        return 12 + (a * 4), 8 + (b * 3)
    if sport == "Netball":
        return 30 + (a * 4), 28 + (b * 3)

    return None, None


class Command(BaseCommand):
    help = "Import simulated Fall 2026 weekend sports fixtures."

    def add_arguments(self, parser):
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Preview fixtures without saving them.",
        )

    def handle(self, *args, **options):
        dry_run = options["dry_run"]
        now = timezone.now().astimezone(LOCAL_TZ)
        fixtures = []
        current = START_DATE
        weekend_number = 0

        while current <= END_DATE:
            if current.weekday() in (5, 6):
                for slot in range(2):
                    sport = SPORTS[(weekend_number * 2 + slot) % len(SPORTS)]
                    opponent = OPPONENTS[
                        (weekend_number + slot * 2) % len(OPPONENTS)
                    ]

                    hour = 10 if slot == 0 else 14
                    start = timezone.make_aware(
                        datetime.combine(current, time(hour, 0)),
                        LOCAL_TZ,
                    )
                    end = start + timedelta(hours=2)

                    is_home = (weekend_number + slot) % 2 == 0
                    venue = (
                        VENUES[(weekend_number + slot) % 5]
                        if is_home
                        else VENUES[5 + ((weekend_number + slot) % 3)]
                    )

                    if end <= now:
                        score_usiu, score_opponent = simulated_score(
                            sport, weekend_number, slot
                        )
                    else:
                        score_usiu, score_opponent = None, None

                    slug = sport.lower()
                    fixture_id = (
                        f"F26-{slug}-{current.strftime('%Y%m%d')}-{slot + 1}"
                    )

                    fixtures.append({
                        "fixture_id": fixture_id,
                        "team_id": f"usiu-{slug}",
                        "team_name": f"USIU-Africa {sport}",
                        "sport": sport,
                        "opponent": opponent,
                        "start_time": start,
                        "end_time": end,
                        "venue": venue,
                        "league": f"Fall 2026 Inter-University Series (DEMO)",
                        "fan_count": 120 + ((weekend_number * 47 + slot * 83) % 850),
                        "score_usiu": score_usiu,
                        "score_opponent": score_opponent,
                        "is_home": is_home,
                    })

                weekend_number += 1

            current += timedelta(days=1)

        completed = sum(1 for f in fixtures if f["score_usiu"] is not None)
        upcoming = len(fixtures) - completed

        self.stdout.write(self.style.WARNING(
            "SIMULATED DEMO DATA - NOT OFFICIAL USIU-AFRICA RESULTS"
        ))
        self.stdout.write(f"Total fixtures planned: {len(fixtures)}")
        self.stdout.write(f"Completed with sample scores: {completed}")
        self.stdout.write(f"Upcoming without scores: {upcoming}")
        self.stdout.write(f"Sports: {', '.join(SPORTS)}")

        for fixture in fixtures[:8]:
            self.stdout.write(
                f'{fixture["start_time"]:%Y-%m-%d %H:%M} | '
                f'{fixture["team_name"]} vs {fixture["opponent"]} | '
                f'{fixture["score_usiu"]} - {fixture["score_opponent"]}'
            )

        if dry_run:
            self.stdout.write(self.style.SUCCESS(
                "Preview complete. No database records were changed."
            ))
            return

        created = 0
        unchanged = 0

        with transaction.atomic():
            for fixture in fixtures:
                _, was_created = Fixture.objects.get_or_create(
                    fixture_id=fixture["fixture_id"],
                    defaults=fixture,
                )
                if was_created:
                    created += 1
                else:
                    unchanged += 1

        from django.db.models import Q
        corrected = []
        with transaction.atomic():
            matching_ids = [f["fixture_id"] for f in fixtures]
            future_scored = Fixture.objects.filter(
                fixture_id__in=matching_ids, end_time__gt=now
            ).filter(Q(score_usiu__isnull=False) | Q(score_opponent__isnull=False))
            for existing in future_scored.iterator():
                corrected.append(existing.fixture_id)
                existing.score_usiu = None
                existing.score_opponent = None
                existing.save(update_fields=['score_usiu', 'score_opponent'])

            from api.sports import ensure_fixture_teams
            for existing in Fixture.objects.filter(fixture_id__in=matching_ids).iterator():
                ensure_fixture_teams(existing)

        self.stdout.write(self.style.SUCCESS(
            f"Import complete: {created} created, {unchanged} existing fixtures left unchanged."
        ))
        if corrected:
            self.stdout.write(self.style.WARNING(
                'Cleared scores from future fixtures: ' + ', '.join(corrected)
            ))

