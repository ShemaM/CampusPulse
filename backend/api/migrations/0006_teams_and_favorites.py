from django.db import migrations, models
import django.db.models.deletion
from django.utils.text import slugify


def seed_teams_from_fixtures(apps, schema_editor):
    Fixture = apps.get_model('api', 'Fixture')
    Team = apps.get_model('api', 'Team')
    alias = schema_editor.connection.alias
    for fixture in Fixture.objects.using(alias).all().iterator():
        Team.objects.using(alias).get_or_create(
            team_id=fixture.team_id,
            defaults={
                'name': fixture.team_name,
                'sport': fixture.sport,
                'competition': fixture.league,
            },
        )
        opponent_id = f"opponent-{slugify(fixture.sport)[:35]}-{slugify(fixture.opponent)[:70]}"
        Team.objects.using(alias).get_or_create(
            team_id=opponent_id,
            defaults={
                'name': fixture.opponent,
                'sport': fixture.sport,
                'competition': fixture.league,
            },
        )


class Migration(migrations.Migration):
    dependencies = [('api', '0005_authorization_foundation')]

    operations = [
        migrations.CreateModel(
            name='Team',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('team_id', models.CharField(max_length=120, unique=True)),
                ('name', models.CharField(max_length=120)),
                ('sport', models.CharField(db_index=True, max_length=50)),
                ('competition', models.CharField(blank=True, max_length=100)),
            ],
            options={'ordering': ['sport', 'name']},
        ),
        migrations.RunPython(seed_teams_from_fixtures, migrations.RunPython.noop),
        migrations.CreateModel(
            name='FavoriteTeam',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('team', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='followers', to='api.team')),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='favorite_teams', to='api.user')),
            ],
            options={'ordering': ['team__sport', 'team__name']},
        ),
        migrations.AddConstraint(
            model_name='favoriteteam',
            constraint=models.UniqueConstraint(fields=('user', 'team'), name='unique_user_favorite_team'),
        ),
        migrations.AddIndex(
            model_name='favoriteteam',
            index=models.Index(fields=['user', 'created_at'], name='favorite_user_created_idx'),
        ),
    ]
