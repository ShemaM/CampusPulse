from datetime import timedelta
from io import StringIO
from zoneinfo import ZoneInfo

from django.core.management import call_command
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from .models import FavoriteTeam, Fixture, FixtureScorer, Team, User
from .sports import build_standings, ensure_fixture_teams, opponent_team_id


NAIROBI = ZoneInfo('Africa/Nairobi')


def fixture(team_id, team_name, opponent, start, home=True, sport='Football',
            league='Fall 2026 Series (DEMO)', score=(None, None), venue='Main Ground'):
    return Fixture.objects.create(
        team_id=team_id,
        team_name=team_name,
        opponent=opponent,
        sport=sport,
        league=league,
        start_time=start,
        end_time=start + timedelta(hours=2),
        venue=venue,
        is_home=home,
        score_usiu=score[0],
        score_opponent=score[1],
    )


class SportsApiTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user('sports_user', password='Test-password-123!')
        self.other = User.objects.create_user('other_user', password='Test-password-123!')
        now = timezone.now()
        self.past = fixture('campus-football', 'Campus FC', 'Rivals FC', now - timedelta(days=3), True, score=(2, 0))
        self.draw = fixture('campus-football', 'Campus FC', 'City FC', now - timedelta(days=2), False, score=(1, 1))
        self.future = fixture('campus-football', 'Campus FC', 'Valley FC', now + timedelta(days=3), False)
        self.team = Team.objects.get(team_id='campus-football')
        ensure_fixture_teams(self.past)
        ensure_fixture_teams(self.draw)
        ensure_fixture_teams(self.future)

    def test_combined_fixture_filters(self):
        response = self.client.get('/api/fixtures/', {
            'sport': 'football', 'status': 'completed', 'home': 'away', 'search': 'City',
        })
        self.assertEqual(response.status_code, 200)
        self.assertEqual([row['id'] for row in response.data], [self.draw.pk])

    def test_invalid_fixture_filter_is_rejected(self):
        self.assertEqual(self.client.get('/api/fixtures/', {'date_from': 'not-a-date'}).status_code, 400)
        self.assertEqual(self.client.get('/api/fixtures/', {'status': 'live'}).status_code, 400)

    def test_favorites_require_auth_and_are_isolated_and_idempotent(self):
        self.assertEqual(self.client.get('/api/favorites/teams/').status_code, 401)
        self.client.force_authenticate(self.user)
        first = self.client.post('/api/favorites/teams/', {'team_id': self.team.team_id}, format='json')
        second = self.client.post('/api/favorites/teams/', {'team_id': self.team.team_id}, format='json')
        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.status_code, 200)
        self.assertEqual(FavoriteTeam.objects.filter(user=self.user).count(), 1)
        self.client.force_authenticate(self.other)
        self.assertEqual(self.client.get('/api/favorites/teams/').data, [])
        self.client.delete(f'/api/favorites/teams/{self.team.team_id}/')
        self.assertEqual(FavoriteTeam.objects.filter(user=self.user).count(), 1)
        self.client.force_authenticate(self.user)
        self.client.delete(f'/api/favorites/teams/{self.team.team_id}/')
        self.assertEqual(FavoriteTeam.objects.filter(user=self.user).count(), 0)

    def test_team_history_includes_both_fixture_sides_and_filters_status(self):
        all_response = self.client.get(f'/api/teams/{self.team.team_id}/fixtures/')
        self.assertEqual(all_response.status_code, 200, all_response.data)
        all_rows = all_response.data
        self.assertEqual(len(all_rows), 3)
        completed = self.client.get(f'/api/teams/{self.team.team_id}/fixtures/', {'status': 'completed'}).data
        upcoming = self.client.get(f'/api/teams/{self.team.team_id}/fixtures/', {'status': 'upcoming'}).data
        self.assertEqual(len(completed), 2)
        self.assertEqual(len(upcoming), 1)
        opponent = Team.objects.get(team_id=opponent_team_id('Football', 'Rivals FC'))
        opponent_rows = self.client.get(f'/api/teams/{opponent.team_id}/fixtures/').data
        self.assertEqual(len(opponent_rows), 1)

    def test_football_standings_use_completed_scores_and_football_points(self):
        standings = build_standings(self.team)
        campus = next(row for row in standings if row['team_id'] == self.team.team_id)
        rivals = next(row for row in standings if row['team'] == 'Rivals FC')
        self.assertEqual((campus['played'], campus['won'], campus['drawn'], campus['points']), (2, 1, 1, 4))
        self.assertEqual((campus['points_for'], campus['points_against'], campus['score_difference']), (3, 1, 2))
        self.assertEqual((rivals['lost'], rivals['points']), (1, 0))

    def test_standings_ignore_scored_future_fixture(self):
        self.future.score_usiu = 5
        self.future.score_opponent = 0
        self.future.save(update_fields=['score_usiu', 'score_opponent'])
        campus = next(row for row in build_standings(self.team) if row['team_id'] == self.team.team_id)
        self.assertEqual(campus['played'], 2)

    def test_future_scores_are_not_exposed_as_results(self):
        self.future.score_usiu = 5
        self.future.score_opponent = 0
        self.future.save(update_fields=['score_usiu', 'score_opponent'])
        response = self.client.get(f'/api/fixtures/{self.future.pk}/')
        self.assertEqual(response.data['status'], 'upcoming')
        self.assertIsNone(response.data['score_usiu'])
        self.assertIsNone(response.data['score_opponent'])

    def test_completed_football_fixture_returns_stored_scorer_minutes(self):
        FixtureScorer.objects.create(fixture=self.past, player_name='Shema', minute=87)
        response = self.client.get(f'/api/fixtures/{self.past.pk}/')
        self.assertEqual(response.data['scorers'][0]['player_name'], 'Shema')
        self.assertEqual(response.data['scorers'][0]['display_minute'], "87'")

    def test_non_football_standings_are_labeled_demonstration(self):
        basketball = fixture('campus-basketball', 'Campus Hoops', 'Other Hoops', timezone.now() - timedelta(days=5), sport='Basketball', score=(75, 70))
        ensure_fixture_teams(basketball)
        team = Team.objects.get(team_id='campus-basketball')
        response = self.client.get(f'/api/teams/{team.team_id}/standings/')
        self.assertEqual(response.status_code, 200, response.data)
        self.assertIn('DEMONSTRATION ONLY', response.data['rules_label'])
        self.assertTrue(response.data['is_demo'])

    def test_import_command_does_not_overwrite_existing_fixture(self):
        original = (self.past.team_name, self.past.score_usiu, self.past.score_opponent)
        call_command('import_fall2026_fixtures', stdout=StringIO())
        second_run = StringIO()
        call_command('import_fall2026_fixtures', stdout=second_run)
        self.past.refresh_from_db()
        self.assertEqual((self.past.team_name, self.past.score_usiu, self.past.score_opponent), original)
        self.assertIn('0 created, 88 existing fixtures left unchanged', second_run.getvalue())
        self.assertFalse(Fixture.objects.filter(
            fixture_id__startswith='F26-', end_time__gt=timezone.now()
        ).filter(score_usiu__isnull=False).exists())

