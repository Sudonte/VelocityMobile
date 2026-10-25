package com.example.velocitysuites;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * The one live password checklist: a strength bar and word, then "At least 8 characters", "A letter", "A number" and "Passwords match"
 * (each ✓ or ○). Sign-up, forgot-password reset and change password all show this view and gate their button on {@link PasswordRules}, the
 * same rule the server enforces for every new password.
 */
public class PasswordRulesView extends LinearLayout {

    private final LinearProgressIndicator bar;
    private final TextView strengthText;
    private final TextView[] rules;
    private boolean satisfied;

    public PasswordRulesView(Context context) {
        this(context, null);
    }

    public PasswordRulesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_password_rules, this);
        bar = findViewById(R.id.passwordStrengthBar);
        strengthText = findViewById(R.id.passwordStrengthText);
        rules = new TextView[]{findViewById(R.id.ruleLength), findViewById(R.id.ruleLetter), findViewById(R.id.ruleNumber), findViewById(R.id.ruleMatch)};
        update("", "");
    }

    /** Follows both fields as they are typed in; {@code onChange} runs after every update (to enable or disable the button). */
    public void bind(@NonNull TextView password, @NonNull TextView confirm, @Nullable Runnable onChange) {
        Runnable refresh = () -> {
            update(password.getText().toString(), confirm.getText().toString());
            if (onChange != null) onChange.run();
        };
        password.addTextChangedListener(new SimpleTextWatcher(refresh));
        confirm.addTextChangedListener(new SimpleTextWatcher(refresh));
        refresh.run();
    }

    /** True when the password meets the whole rule and the confirmation matches it. */
    public boolean isSatisfied() {
        return satisfied;
    }

    public void update(@NonNull String password, @NonNull String confirm) {
        boolean[] met = {PasswordRules.hasMinLength(password), PasswordRules.hasLetter(password), PasswordRules.hasNumber(password),
                PasswordRules.matches(password, confirm)};
        @StringRes int[] labels = {R.string.password_rule_length, R.string.password_rule_letter, R.string.password_rule_number, R.string.password_rule_match};
        for (int i = 0; i < rules.length; i++) {
            rules[i].setText(getContext().getString(met[i] ? R.string.password_rule_met_format : R.string.password_rule_unmet_format, getContext().getString(labels[i])));
            rules[i].setTextColor(ContextCompat.getColor(getContext(), met[i] ? R.color.velocity_text_primary : R.color.velocity_text_secondary));
            rules[i].setTypeface(null, met[i] ? Typeface.BOLD : Typeface.NORMAL);
        }
        satisfied = met[0] && met[1] && met[2] && met[3];
        int level = PasswordRules.strength(password);
        bar.setProgress(level);
        int[] colors = {R.color.velocity_action_text, R.color.velocity_action_text, R.color.velocity_orange_primary, R.color.velocity_gold_primary, R.color.velocity_green_primary};
        bar.setIndicatorColor(ContextCompat.getColor(getContext(), colors[level]));
        int[] words = {R.string.password_strength_none, R.string.password_strength_weak, R.string.password_strength_fair, R.string.password_strength_good, R.string.password_strength_strong};
        strengthText.setText(words[level]);
    }
}
