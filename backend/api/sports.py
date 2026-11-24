from django.utils.text import slugify
from django.utils import timezone

from .models import Fixture, Team


def opponent_team_id(sport, name):
    return f"opponent-{slugify(sport)[:35]}-{slugify(name)[:70]}"


def ensure_fixture_teams(fixture):
    """Create missing catalog identities without changing fixture records."""
    Team.objects.get_or_create(
        team_id=fixture.team_id,
        defaults={
            'name': fixture.team_name,
            'sport': fixture.sport,
            'competition': fixture.league,
        },
    )
    opponent_id = opponent_team_id(fixture.sport, fixture.opponent)
    Team.objects.get_or_create(
        team_id=opponent_id,
        defaults={
            'name': fixture.opponent,
            'sport': fixture.sport,
            'competition': fixture.league,
        },
    )


def fixture_queryset_for_team(team):
    from django.db.models import Q
    return Fixture.objects.filter(
        Q(team_id=team.team_id)
        | Q(sport__iexact=team.sport, opponent__iexact=team.name)
    )


def is_completed(fixture, now=None):
    now = now or timezone.now()
    return (
        fixture.score_usiu is not None
        and fixture.score_opponent is not None
        and fixture.end_time <= now
    )


def build_standings(team, fixtures=None, now=None):
    """Calculate completed-match tables; non-football points are illustrative."""
    now = now or timezone.now()
    fixtures = fixtures if fixtures is not None else Fixture.objects.filter(
        sport__iexact=team.sport,
        league=team.competition,
    )
    table = {}

    def row_for(identity, name):
        return table.setdefault(identity, {
            'team_id': identity,
            'team': name,
            'played': 0,
            'won': 0,
            'drawn': 0,
            'lost': 0,
            'points_for': 0,
            'points_against': 0,
            'score_difference': 0,
            'points': 0,
        })

    for fixture in fixtures:
        if fixture.sport.casefold() != team.sport.casefold() or fixture.league != team.competition:
            continue
        if not is_completed(fixture, now):
            continue

        home = row_for(fixture.team_id, fixture.team_name)
        away_id = opponent_team_id(fixture.sport, fixture.opponent)
        away = row_for(away_id, fixture.opponent)
        home_score = fixture.score_usiu
        away_score = fixture.score_opponent
        home['played'] += 1
        away['played'] += 1
        home['points_for'] += home_score
        home['points_against'] += away_score
        away['points_for'] += away_score
        away['points_against'] += home_score

        if home_score > away_score:
            winner, loser = home, away
        elif away_score > home_score:
            winner, loser = away, home
        else:
            winner = loser = None

        if winner is not None:
            winner['won'] += 1
            loser['lost'] += 1
            # Football's table points are official per the competition rules
            # supplied for this module. Other sports use this only as a
            # prominently labeled demonstration calculation.
            winner['points'] += 3
        else:
            home['drawn'] += 1
            away['drawn'] += 1
            home['points'] += 1
            away['points'] += 1

    for row in table.values():
        row['score_difference'] = row['points_for'] - row['points_against']

    rows = sorted(
        table.values(),
        key=lambda row: (
            -row['points'],
            -row['score_difference'],
            -row['points_for'],
            row['team'].casefold(),
        ),
    )
    for index, row in enumerate(rows, start=1):
        row['position'] = index
    return rows
