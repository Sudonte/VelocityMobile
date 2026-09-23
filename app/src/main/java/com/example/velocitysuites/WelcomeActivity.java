package com.example.velocitysuites;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome);

        // Entry animation for the welcome screen
        View welcomeRoot = findViewById(R.id.welcomeRoot);
        if (welcomeRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(welcomeRoot, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
                return insets;
            });
            welcomeRoot.setAlpha(0f);
            welcomeRoot.setTranslationY(20f);
            welcomeRoot.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(600L)
                    .start();
        }

        // Setup Buttons
        findViewById(R.id.loginButton).setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
            overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
        });

        findViewById(R.id.registrationButton).setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, RegistrationActivity.class));
            overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
        });

        findViewById(R.id.btnWelcomeBack).setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
        });
    }
}
