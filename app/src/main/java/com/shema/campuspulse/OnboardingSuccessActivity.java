package com.shema.campuspulse;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class OnboardingSuccessActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_onboarding_success);

        final View content = findViewById(R.id.success_content);
        if (content != null) {
            final int baseTop = content.getPaddingTop();
            final int baseBottom = content.getPaddingBottom();
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
                androidx.core.graphics.Insets bars = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), baseTop + bars.top,
                        v.getPaddingRight(), baseBottom + bars.bottom);
                return windowInsets;
            });
        }

        String username = getIntent().getStringExtra("username");
        if (username == null || username.trim().isEmpty()) {
            username = "scholar";
        }

        TextView textTitle = findViewById(R.id.text_title);
        if (textTitle != null) {
            textTitle.setText("You're all set,\n" + username + "!");
        }

        View qrContainer = findViewById(R.id.qr_container);
        if (qrContainer != null) {
            qrContainer.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("USIU Digital Campus Pass")
                        .setMessage("Student Pass ID: #USIU-8821\nStatus: Verified Student\n\nPresent this digital pass at the Freida Brown Student Center, Library entrances, and campus event check-in desks.")
                        .setPositiveButton("Done", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        }

        MaterialButton btnLetsGo = findViewById(R.id.btn_lets_go);
        btnLetsGo.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
