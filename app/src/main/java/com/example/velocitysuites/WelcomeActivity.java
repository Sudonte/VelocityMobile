package com.example.velocitysuites;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class WelcomeActivity extends SafeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome);

        View welcomeRoot = findViewById(R.id.welcomeRoot);
        if (welcomeRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(welcomeRoot, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
                return insets;
            });
        }
        // Entry: logo (fading and scaling), brand, heading, line, then the four cards one after another.
        WelcomeEffects.enter(this, new View[]{findViewById(R.id.logoBorder), findViewById(R.id.welcomeBrandText), findViewById(R.id.welcomeTitleText),
                findViewById(R.id.welcomeDescriptionText), findViewById(R.id.welcomeCardRooms), findViewById(R.id.welcomeCardPay),
                findViewById(R.id.welcomeCardTrack), findViewById(R.id.welcomeCardSupport)});

        // The four cards open their step-by-step sheet.
        int[] cards = {R.id.welcomeCardRooms, R.id.welcomeCardPay, R.id.welcomeCardTrack, R.id.welcomeCardSupport};
        for (int i = 0; i < cards.length; i++) {
            final int topic = i;
            View card = findViewById(cards[i]);
            WelcomeEffects.pressScale(card);
            card.setOnClickListener(v -> openTopic(topic));
        }
        if (savedInstanceState != null && savedInstanceState.getInt(STATE_TOPIC, -1) >= 0) {
            // rotation or a restart: the sheet that was open comes back
            final int saved = savedInstanceState.getInt(STATE_TOPIC);
            findViewById(R.id.welcomeRoot).post(() -> openTopic(saved));
        }

        // "Already have an account? Sign in": the line is one button; "Sign in" is picked out in bold primary red.
        com.google.android.material.button.MaterialButton signIn = findViewById(R.id.loginButton);
        android.text.SpannableString line = new android.text.SpannableString(getString(R.string.welcome_action_sign_in));
        int at = line.toString().lastIndexOf(getString(R.string.welcome_sign_in_link));
        if (at >= 0) {
            int end = at + getString(R.string.welcome_sign_in_link).length();
            line.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, end, 0);
            line.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.velocity_action_text)), at, end, 0);
        }
        signIn.setText(line);

        // Setup Buttons
        findViewById(R.id.loginButton).setOnClickListener(v -> openSignIn());
        findViewById(R.id.registrationButton).setOnClickListener(v -> openCreateAccount());

        findViewById(R.id.btnWelcomeBack).setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
        });
    }

    private static final String STATE_TOPIC = "welcome_topic";
    /** The topic whose sheet is open, or -1. */
    private int openTopic = -1;

    private void openTopic(int topic) {
        if (WelcomeSheet.isShowing(this)) return; // a repeat tap opens only one sheet
        openTopic = topic;
        WelcomeSheet.show(this, topic, new WelcomeSheet.Listener() {
            @Override
            public void onCreateAccount() {
                openCreateAccount();
            }

            @Override
            public void onSignIn() {
                openSignIn();
            }

            @Override
            public void onDismissed() {
                if (!isChangingConfigurations() && !isFinishing()) openTopic = -1;
            }
        });
    }

    private void openCreateAccount() {
        startActivity(new Intent(WelcomeActivity.this, RegistrationActivity.class));
        overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
    }

    private void openSignIn() {
        startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
        overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TOPIC, WelcomeSheet.isShowing(this) ? openTopic : -1);
    }
}
