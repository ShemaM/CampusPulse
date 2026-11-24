from django.contrib.auth import login, logout
from django.contrib.auth.decorators import user_passes_test
from django.contrib.auth.forms import AuthenticationForm
from django.shortcuts import redirect, render
from django.utils.http import url_has_allowed_host_and_scheme
from django.views.decorators.http import require_POST


def staff_required(view_func):
    return user_passes_test(
        lambda user: user.is_authenticated and user.is_active and user.is_staff,
        login_url='/api/dashboard/login/',
    )(view_func)


def staff_login_view(request):
    if request.user.is_authenticated and request.user.is_active and request.user.is_staff:
        return redirect('/api/dashboard/')

    form = AuthenticationForm(request, data=request.POST or None)

    if request.method == 'POST' and form.is_valid():
        user = form.get_user()

        if user.is_active and user.is_staff:
            login(request, user)

            next_url = request.POST.get('next') or request.GET.get('next')
            if next_url and url_has_allowed_host_and_scheme(
                next_url,
                allowed_hosts={request.get_host()},
                require_https=request.is_secure(),
            ):
                return redirect(next_url)

            return redirect('/api/dashboard/')

        form.add_error(None, 'Invalid username or password.')

    return render(request, 'dashboard/login.html', {'form': form})


@require_POST
def staff_logout_view(request):
    logout(request)
    return redirect('/api/dashboard/login/')
