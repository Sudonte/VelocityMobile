package com.example.velocitysuites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Step 2 of 3 (booking) / 4 (reservation) - Representative and guests on one screen: the representative section first (Account owner /
 * Someone else, the names, the helper about presenting 1 valid ID), then directly below it the Guests section (adults, minors with their
 * ages, the capacity check inline). Each section is the same fragment the wizard used as separate steps, so the rules and the inline
 * messages are unchanged; Next validates both (every problem shows at once) and asks one confirmation.
 */
public class StepRepresentativeGuestsFragment extends WizardStepFragment {

    private static final String TAG_REP = "rep";
    private static final String TAG_GUESTS = "guests";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step_rep_guests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getChildFragmentManager().findFragmentByTag(TAG_REP) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.repContainer, new Step4GuestIdentificationFragment(), TAG_REP)
                    .replace(R.id.guestsContainer, new Step5AdditionalGuestsFragment(), TAG_GUESTS)
                    .commitNow();
        }
    }

    @Nullable
    private WizardStepFragment child(String tag) {
        androidx.fragment.app.Fragment f = getChildFragmentManager().findFragmentByTag(tag);
        return f instanceof WizardStepFragment ? (WizardStepFragment) f : null;
    }

    @Override
    public String stepTitle() {
        return getString(R.string.step_title_rep_guests);
    }

    @Override
    public void onSelectionChanged() {
        for (String tag : new String[]{TAG_REP, TAG_GUESTS}) {
            WizardStepFragment f = child(tag);
            if (f != null) f.onSelectionChanged();
        }
    }

    @Override
    public void onWizardStepShown() {
        for (String tag : new String[]{TAG_REP, TAG_GUESTS}) {
            WizardStepFragment f = child(tag);
            if (f != null) f.onWizardStepShown();
        }
    }

    /** Both parts are checked every time, so every problem on the screen shows at once. */
    @Override
    public boolean validateBeforeNext() {
        WizardStepFragment rep = child(TAG_REP);
        WizardStepFragment guests = child(TAG_GUESTS);
        boolean repOk = rep == null || rep.validateBeforeNext();
        boolean guestsOk = guests == null || guests.validateBeforeNext();
        return repOk && guestsOk;
    }

    @Override
    public int validationMessageRes() {
        return 0; // inline messages under the fields
    }

    @Override
    public String nextConfirmTitle() {
        return getString(R.string.confirm_rep_guests_title);
    }

    @Override
    public String nextConfirmSummary() {
        WizardStepFragment rep = child(TAG_REP);
        WizardStepFragment guests = child(TAG_GUESTS);
        String a = rep == null ? null : rep.nextConfirmSummary();
        String b = guests == null ? null : guests.nextConfirmSummary();
        if (a == null) return b;
        return b == null ? a : a + "\n" + b;
    }
}
