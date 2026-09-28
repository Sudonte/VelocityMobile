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
 * branching): Cash vs GCash. No payment is collected on this step itself
 * either way - Cash always means full payment walk-in at the hotel,
 * collected in person, never through this app. GCash's actual payment
 * collection (reference number, mobile number, receipt, amount) happens one
 * step later, at Step 8's Confirm, via PaymentActivity's GCash portal (Step 5
 * of 5) - see Step8ReviewPaymentFragment's own doc and PaymentActivity#
 * EXTRA_PENDING_RESERVATION. The resulting Reservation stays a Reservation
 * (does not auto-convert to a Booking). Selection is stored in
 * BookingWizardState.paymentMethod ("cash"/"gcash", matching every other
 * payment-method call site's casing convention).
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
