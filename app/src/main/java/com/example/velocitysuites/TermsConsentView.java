package com.example.velocitysuites;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.function.Supplier;

/**
 * The one Terms and Policy consent block used by Booking, Reservation and Registration: a guidance box
 * ("Before you continue: ..."), the View Terms and Policy button, and the agreement checkbox. The checkbox is
 * locked (with a reason shown) until the Terms have been opened and is never checked for the guest.
 * "Terms viewed" and "agreed" survive rotation/configuration changes. The owning screen decides what the main
 * action does with {@link TermsGate}.
 */
public class TermsConsentView extends LinearLayout {

    public interface Listener {
        void onTermsStateChanged(boolean viewed, boolean accepted);
    }

    private static final String KEY_SUPER = "super";
    private static final String KEY_VIEWED = "viewed";
    private static final String KEY_ACCEPTED = "accepted";

    private final TextView tvStep3;
    private final MaterialCheckBox checkBox;
    private final TextView tvLockedHelper;

    private boolean viewed;
    private Supplier<CharSequence> addendum;
    @Nullable
    private Listener listener;

    public TermsConsentView(Context context) {
        this(context, null);
    }

    public TermsConsentView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_terms_consent, this);
        tvStep3 = findViewById(R.id.tvTermsGuidanceStep3);
        checkBox = findViewById(R.id.cbTermsAgree);
        tvLockedHelper = findViewById(R.id.tvTermsLockedHelper);
        // This view saves the checkbox state itself (see onSaveInstanceState).
        checkBox.setSaveEnabled(false);

        findViewById(R.id.btnViewTerms).setOnClickListener(v ->
                TermsPolicyDialog.show(getContext(), addendum != null ? addendum.get() : null, () -> setViewed(true)));
        checkBox.setOnCheckedChangeListener((button, isChecked) -> notifyChanged());
        render();
    }

    /** The label of the screen's main button, quoted in step 3 of the guidance (e.g. "Confirm Booking"). */
    public void setActionLabel(CharSequence actionLabel) {
        tvStep3.setText(getContext().getString(R.string.terms_guidance_step3_format, actionLabel));
    }

    /** Optional transaction-specific text appended to the shared Terms document, read each time the Terms are opened. */
    public void setAddendum(@Nullable Supplier<CharSequence> addendum) {
        this.addendum = addendum;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public boolean isViewed() {
        return viewed;
    }

    public boolean isAccepted() {
        return checkBox.isChecked();
    }

    /** Restores previously held state (e.g. from the wizard state) without notifying the listener. */
    public void restore(boolean viewed, boolean accepted) {
        this.viewed = viewed;
        Listener saved = listener;
        listener = null;
        checkBox.setChecked(viewed && accepted);
        listener = saved;
        render();
    }

    private void setViewed(boolean value) {
        if (viewed == value) return;
        viewed = value;
        render();
        notifyChanged();
    }

    private void render() {
        checkBox.setEnabled(TermsGate.isCheckboxEnabled(viewed));
        tvLockedHelper.setVisibility(viewed ? GONE : VISIBLE);
    }

    private void notifyChanged() {
        if (listener != null) listener.onTermsStateChanged(viewed, checkBox.isChecked());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putParcelable(KEY_SUPER, super.onSaveInstanceState());
        state.putBoolean(KEY_VIEWED, viewed);
        state.putBoolean(KEY_ACCEPTED, checkBox.isChecked());
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(bundle.getParcelable(KEY_SUPER));
            viewed = bundle.getBoolean(KEY_VIEWED);
            checkBox.setChecked(viewed && bundle.getBoolean(KEY_ACCEPTED));
            render();
        } else {
            super.onRestoreInstanceState(state);
        }
    }
}
