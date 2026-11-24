from datetime import timedelta
from django import forms
from django.utils import timezone
from .models import Event, RSVP


class EventAdminForm(forms.ModelForm):
    total_seats = forms.IntegerField(required=False, min_value=1)
    venue_select = forms.CharField(required=False)
    custom_venue = forms.CharField(required=False)
    start_time = forms.DateTimeField(
        required=False,
        input_formats=['%Y-%m-%dT%H:%M', '%Y-%m-%d %H:%M:%S', '%Y-%m-%d %H:%M'],
    )
    end_time = forms.DateTimeField(
        required=False,
        input_formats=['%Y-%m-%dT%H:%M', '%Y-%m-%d %H:%M:%S', '%Y-%m-%d %H:%M'],
    )

    class Meta:
        model = Event
        fields = [
            'title', 'description', 'event_type', 'school_code',
            'major', 'start_time', 'end_time', 'total_seats', 'organizer',
            'image', 'image_url', 'video'
        ]

    def clean(self):
        cleaned_data = super().clean()

        video = cleaned_data.get('video')
        if video and hasattr(video, 'size'):
            if video.size > 50 * 1024 * 1024:
                self.add_error('video', 'Video file size cannot exceed 50 MB.')
            else:
                name_lower = video.name.lower()
                if not any(name_lower.endswith(ext) for ext in ['.mp4', '.mov', '.webm', '.m4v']):
                    self.add_error('video', 'Unsupported video format. Permitted formats: MP4, MOV, WEBM, M4V.')

        venue_select = (cleaned_data.get('venue_select') or '').strip()
        custom_venue = (cleaned_data.get('custom_venue') or '').strip()
        venue = custom_venue if venue_select == 'CUSTOM' else venue_select
        if not venue and self.instance and self.instance.pk:
            venue = self.instance.venue
        if not venue:
            self.add_error('venue_select', 'Please select or enter a venue.')
        else:
            cleaned_data['venue'] = venue

        now = timezone.now().replace(second=0, microsecond=0)
        start_time = cleaned_data.get('start_time')
        end_time = cleaned_data.get('end_time')

        if not start_time:
            start_time = (
                self.instance.start_time
                if (self.instance and self.instance.pk and self.instance.start_time)
                else now + timedelta(hours=1)
            )
            cleaned_data['start_time'] = start_time

        if not end_time:
            end_time = (
                self.instance.end_time
                if (self.instance and self.instance.pk and self.instance.end_time)
                else start_time + timedelta(hours=2)
            )
            cleaned_data['end_time'] = end_time

        if end_time <= start_time:
            self.add_error('end_time', 'End time must be after start time.')

        total_seats = cleaned_data.get('total_seats')
        if total_seats is not None and total_seats < 1:
            self.add_error('total_seats', 'Total seats must be at least 1.')

        if total_seats is None:
            self.instance.seats_left = None
        else:
            confirmed_rsvps = RSVP.objects.filter(
                event=self.instance, status=RSVP.Status.CONFIRMED
            ).count() if self.instance and self.instance.pk else 0
            if total_seats < confirmed_rsvps:
                self.add_error(
                    'total_seats',
                    f'Cannot reduce total seats below current confirmed RSVPs ({confirmed_rsvps}).',
                )
            else:
                self.instance.seats_left = max(0, total_seats - confirmed_rsvps)

        cleaned_data['school_code'] = (cleaned_data.get('school_code') or '').strip() or None
        cleaned_data['major'] = (cleaned_data.get('major') or '').strip() or None
        cleaned_data['image_url'] = (cleaned_data.get('image_url') or '').strip() or None
        return cleaned_data

    def save(self, commit=True):
        event = super().save(commit=False)
        event.venue = self.cleaned_data['venue']
        event.start_time = self.cleaned_data['start_time']
        event.end_time = self.cleaned_data['end_time']

        if event.total_seats is None:
            event.seats_left = None
        elif event.pk:
            confirmed_rsvps = RSVP.objects.filter(
                event=event, status=RSVP.Status.CONFIRMED
            ).count()
            event.seats_left = max(0, event.total_seats - confirmed_rsvps)
        else:
            event.seats_left = event.total_seats

        if commit:
            event.save()
        return event


class EventMediaAdminForm(forms.ModelForm):
    clear_image = forms.BooleanField(required=False)
    clear_video = forms.BooleanField(required=False)

    class Meta:
        model = Event
        fields = ['image', 'image_url', 'video']

    def clean(self):
        cleaned_data = super().clean()
        video = cleaned_data.get('video')
        if video and hasattr(video, 'size'):
            if video.size > 50 * 1024 * 1024:
                self.add_error('video', 'Video file size cannot exceed 50 MB.')
            elif not any(video.name.lower().endswith(ext) for ext in ('.mp4', '.mov', '.webm', '.m4v')):
                self.add_error('video', 'Use an MP4, MOV, WEBM, or M4V video.')
        image = cleaned_data.get('image')
        if image and hasattr(image, 'size') and image.size > 10 * 1024 * 1024:
            self.add_error('image', 'Image file size cannot exceed 10 MB.')
        cleaned_data['image_url'] = (cleaned_data.get('image_url') or '').strip() or None
        return cleaned_data
