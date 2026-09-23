package com.example.velocitysuites;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

/**
 * Common base for the 7 BookingWizardActivity step screens. Each step reads/
 * writes the one shared BookingWizardState instance the host Activity owns -
 * there's no per-fragment ViewModel or saved-state bundle round-tripping,
 * since the whole wizard lives and dies with one BookingWizardActivity
 * instance (see BookingWizardState's own class docblock for why).
 */
public abstract class WizardStepFragment extends Fragment {

    protected BookingWizardState getState() {
        return ((BookingWizardActivity) requireActivity()).getState();
    }

    protected BookingWizardActivity getWizardActivity() {
        return (BookingWizardActivity) requireActivity();
    }

    /** Human-readable label shown in the step-progress header, e.g. "Room Selection". */
    public abstract String stepTitle();

    /** Called by the host right before this step becomes visible - use to refresh UI derived from state set by an earlier step. */
    public void onWizardStepShown() {
    }

    /** Validates this step's own data and blocks Next when false - implementations surface their own error UI (Toast/inline) before returning false. */
    public boolean validateBeforeNext() {
        return true;
    }

    /**
     * Dismisses a loading dialog shown around a Retrofit call (room/reservation
     * creation, ID card upload) - safe to call from that call's onSuccess()/onError()
     * even if the guest has already navigated away (backed out of the wizard) or the
     * Fragment was otherwise detached while the request was in flight. Without the
     * isAdded() guard, dismiss() itself (or whatever the caller does right after it)
     * can throw against a Context that's already gone; the isShowing()+try/catch
     * additionally covers the window already having been torn down out from under a
     * dialog that still reports itself as attached.
     */
    protected void dismissSafely(@Nullable AlertDialog dialog) {
        if (dialog == null || !isAdded()) return;
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (IllegalArgumentException ignored) {
            // Window already gone - nobody is looking at this dialog either way.
        }
    }
}
