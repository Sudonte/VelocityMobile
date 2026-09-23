package com.example.velocitysuites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

/**
 * Step 7 of 8 (New Reservation creation only, never shown for Booking mode
 * or for a Modify/edit run - see BookingWizardActivity's mode/step-count
 * branching): Cash vs GCash, no payment collected here or at any point
 * during creation - GCash always defers to a later Pay Now action, per an
 * explicit product decision; Cash always means full payment walk-in at the
 * hotel, collected in person, never through this app. The 20/30/40/50%/Full
 * payment-amount choice is deliberately NOT asked here - it only exists
 * later, inside payment.xml's Review Billing, for a GCash reservation's own
 * Pay Now/Pay Later action (see PaymentActivity's cgPaymentAmount chip
 * group). Selection is stored in BookingWizardState.paymentMethod
 * ("cash"/"gcash", matching every other payment-method call site's casing
 * convention).
 */
public class Step7PaymentMethodFragment extends WizardStepFragment {

    private MaterialCardView cardCash;
    private MaterialCardView cardGcash;
    private RadioButton radioCash;
    private RadioButton radioGcash;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step7_payment_method, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cardCash = view.findViewById(R.id.cardPaymentCash);
        cardGcash = view.findViewById(R.id.cardPaymentGcash);
        radioCash = view.findViewById(R.id.radioPaymentCash);
        radioGcash = view.findViewById(R.id.radioPaymentGcash);

        cardCash.setOnClickListener(v -> selectMethod("cash"));
        cardGcash.setOnClickListener(v -> selectMethod("gcash"));

        BookingWizardState state = getState();
        if (state.paymentMethodChosen) {
            applySelection("gcash".equalsIgnoreCase(state.paymentMethod));
        }
    }

    private void selectMethod(String method) {
        BookingWizardState state = getState();
        state.paymentMethod = method;
        state.paymentMethodChosen = true;
        applySelection("gcash".equals(method));
    }

    private void applySelection(boolean gcashSelected) {
        radioCash.setChecked(!gcashSelected);
        radioGcash.setChecked(gcashSelected);
        styleCard(cardCash, !gcashSelected);
        styleCard(cardGcash, gcashSelected);
    }

    private void styleCard(MaterialCardView card, boolean selected) {
        int strokeColorRes = selected ? R.color.velocity_red_primary : R.color.velocity_red_subtle;
        card.setStrokeColor(ContextCompat.getColor(requireContext(), strokeColorRes));
        card.setStrokeWidth(dp(selected ? 2 : 1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public String stepTitle() {
        return "Payment Method";
    }

    @Override
    public boolean validateBeforeNext() {
        if (!getState().paymentMethodChosen) {
            Toast.makeText(requireContext(), R.string.error_payment_method_required, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
