from django.db.models.signals import post_save
from django.dispatch import receiver

from .models import Fixture
from .sports import ensure_fixture_teams


@receiver(post_save, sender=Fixture)
def sync_fixture_participant_teams(sender, instance, created, **kwargs):
    if created:
        ensure_fixture_teams(instance)
