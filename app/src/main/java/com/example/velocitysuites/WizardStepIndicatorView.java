package com.example.velocitysuites;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 * A reusable N-step progress row: numbered circle badges (checkmark once a
 * step is fully completed) joined by connector lines, reusing the same
 * shape_step_badge/shape_step_badge_inactive drawables the bookingandreservation.xml
 * edit-mode form's 3-step indicator and payment.xml's 2-step indicator
 * already use - one shared component instead of a 3rd/4th bespoke variant.
 * Replaces the earlier plain-dot version (no numbers/checkmarks) generalized
 * from RegistrationActivity#updateStepDots().
 */
public class WizardStepIndicatorView extends LinearLayout {

    private static final int BADGE_SIZE_DP = 26;
    private static final int CONNECTOR_HEIGHT_DP = 2;

    private int stepCount = 7;
    private int currentStep = 1;
    private final java.util.List<FrameLayout> badges = new java.util.ArrayList<>();
    private final java.util.List<TextView> badgeLabels = new java.util.ArrayList<>();
    private final java.util.List<View> connectors = new java.util.ArrayList<>();

    public WizardStepIndicatorView(Context context) {
        super(context);
        init();
    }

    public WizardStepIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        rebuild();
    }

    /** Must be called before the first render if the step count differs from the default of 7. */
    public void setStepCount(int stepCount) {
        this.stepCount = Math.max(1, stepCount);
        rebuild();
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
        updateColors();
    }

    private void rebuild() {
        removeAllViews();
        badges.clear();
        badgeLabels.clear();
        connectors.clear();

        float density = getResources().getDisplayMetrics().density;
        int badgeSize = (int) (BADGE_SIZE_DP * density);
        int connectorHeight = (int) (CONNECTOR_HEIGHT_DP * density);

        for (int i = 0; i < stepCount; i++) {
            FrameLayout badge = new FrameLayout(getContext());
            LayoutParams badgeParams = new LayoutParams(badgeSize, badgeSize);
            addView(badge, badgeParams);
            badges.add(badge);

            TextView label = new TextView(getContext());
            label.setText(String.valueOf(i + 1));
            label.setTextSize(11);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            badge.addView(label, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            badgeLabels.add(label);

            if (i < stepCount - 1) {
                View connector = new View(getContext());
                LayoutParams connectorParams = new LayoutParams(0, connectorHeight, 1f);
                addView(connector, connectorParams);
                connectors.add(connector);
            }
        }
        updateColors();
    }

    private void updateColors() {
        for (int i = 0; i < badges.size(); i++) {
            int stepNumber = i + 1;
            boolean completed = stepNumber < currentStep;
            boolean active = stepNumber == currentStep;
            boolean filled = completed || active;

            badges.get(i).setBackgroundResource(filled ? R.drawable.shape_step_badge : R.drawable.shape_step_badge_inactive);
            TextView label = badgeLabels.get(i);
            label.setText(completed ? "✓" : String.valueOf(stepNumber));
            label.setTextColor(ContextCompat.getColor(getContext(),
                    filled ? R.color.white : R.color.velocity_inactive_gray));
        }
        for (int i = 0; i < connectors.size(); i++) {
            boolean completed = (i + 1) < currentStep;
            connectors.get(i).setBackgroundColor(ContextCompat.getColor(getContext(),
                    completed ? R.color.velocity_red_primary : R.color.velocity_red_soft));
        }
    }
}
