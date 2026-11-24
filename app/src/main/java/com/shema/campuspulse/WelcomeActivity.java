package com.shema.campuspulse;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.shema.campuspulse.data.TokenManager;
import com.shema.campuspulse.data.UserPrefs;

public class WelcomeActivity extends AppCompatActivity {

    private ObjectAnimator zoomAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // App launch session check: route directly to MainActivity if valid auth session exists
        if (TokenManager.getAccessToken(this) != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_welcome);

        applyInsets();

        MaterialButton btnExplore = findViewById(R.id.btn_explore_guest);
        MaterialButton btnSignup = findViewById(R.id.btn_signup_custom);
        android.widget.TextView btnWelcomeLogin = findViewById(R.id.btn_welcome_login);

        btnExplore.setOnClickListener(v -> {
            UserPrefs.clearSelection(this);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        btnSignup.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        });

        if (btnWelcomeLogin != null) {
            String text = "Already have an account? <font color='#F2A900'><u><b>Sign in</b></u></font>";
            btnWelcomeLogin.setText(android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY));
            btnWelcomeLogin.setOnClickListener(v -> {
                startActivity(new Intent(this, LoginActivity.class));
            });
        }

        playEntrance();
    }

    /** Keeps content clear of the status bar and gesture/navigation bar. */
    private void applyInsets() {
        final View column = findViewById(R.id.content_column);
        final int baseTop = column.getPaddingTop();
        final int baseBottom = column.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(column, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), baseTop + bars.top,
                    v.getPaddingRight(), baseBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(column);
    }

    /** Slow, subtle zoom on the campus photo. */
    private void startHeroZoom() {
        View hero = findViewById(R.id.hero_image);
        zoomAnimator = ObjectAnimator.ofPropertyValuesHolder(hero,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        zoomAnimator.setDuration(14000);
        zoomAnimator.setInterpolator(new LinearInterpolator());
        zoomAnimator.setRepeatCount(ValueAnimator.INFINITE);
        zoomAnimator.setRepeatMode(ValueAnimator.REVERSE);
        zoomAnimator.start();
    }

    /** Staggered fade + rise for each element in the content column. */
    private void playEntrance() {
        ViewGroup column = findViewById(R.id.content_column);
        float rise = 24f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            if (child.getLayoutParams() instanceof LinearLayout.LayoutParams
                    && ((LinearLayout.LayoutParams) child.getLayoutParams()).weight > 0f) {
                continue;
            }
            child.setAlpha(0f);
            child.setTranslationY(rise);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(150L + i * 80L)
                    .setDuration(450)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    @Override
    protected void onDestroy() {
        if (zoomAnimator != null) zoomAnimator.cancel();
        super.onDestroy();
    }
}