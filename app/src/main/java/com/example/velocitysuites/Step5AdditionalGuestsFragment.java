package com.example.velocitysuites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Step 5: declared adults/children counts, plus a dynamic "Child N Age"
 * field per declared child - each required 0-7. Adults are bounded by step
 * 1's total room capacity (BookingWizardState#totalSelectedCapacity());
 * children are never counted against that capacity (only against
 * MAX_CHILDREN) - the two are deliberately independent validations. New
 * logic (BookingAndReservationActivity only ever derived adults/children
 * after the fact from a flat guest list's ages - see finalizeBooking());
 * this step collects the counts up front instead, per spec.
 */
public class Step5AdditionalGuestsFragment extends WizardStepFragment {

    private static final int MIN_CHILD_AGE = 0;
    private static final int MAX_CHILD_AGE = 7;
    private static final int MAX_CHILDREN = 3;

    private TextView tvCapacityHint;
    private TextView tvAdultsCount;
    private TextView tvChildrenCount;
    private TextView tvChildAgeNotice;
    private LinearLayout layoutChildAgeFields;
    private final List<TextInputLayout> childAgeInputLayouts = new ArrayList<>();
    private final List<TextInputEditText> childAgeInputs = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step5_additional_guests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvCapacityHint = view.findViewById(R.id.tvCapacityHint);
        tvAdultsCount = view.findViewById(R.id.tvAdultsCount);
        tvChildrenCount = view.findViewById(R.id.tvChildrenCount);
        tvChildAgeNotice = view.findViewById(R.id.tvChildAgeNotice);
        layoutChildAgeFields = view.findViewById(R.id.layoutChildAgeFields);

        view.findViewById(R.id.btnAdultsMinus).setOnClickListener(v -> changeAdults(-1));
        view.findViewById(R.id.btnAdultsPlus).setOnClickListener(v -> changeAdults(1));
        view.findViewById(R.id.btnChildrenMinus).setOnClickListener(v -> changeChildren(-1));
        view.findViewById(R.id.btnChildrenPlus).setOnClickListener(v -> changeChildren(1));

        tvCapacityHint.setText(getString(R.string.capacity_hint_format, getState().totalSelectedCapacity()));
        refreshCounts();
        rebuildChildAgeFields();
    }

    @Override
    public String stepTitle() {
        return "Additional Guest Information";
    }

    private void changeAdults(int delta) {
        BookingWizardState state = getState();
        int next = state.adults + delta;
        if (next < 1) return;
        // Room capacity governs adults only - children never consume it (see
        // Step 5's class doc / spec: "2 Adults + 3 Children" against a
        // capacity-2 room is valid, only the adult count is capped).
        if (next > state.totalSelectedCapacity()) {
            Toast.makeText(requireContext(), R.string.error_guests_exceed_capacity, Toast.LENGTH_SHORT).show();
            return;
        }
        state.adults = next;
        refreshCounts();
    }

    private void changeChildren(int delta) {
        BookingWizardState state = getState();
        int next = state.children + delta;
        if (next < 0) return;
        // Children are bounded only by MAX_CHILDREN, never by room capacity
        // (that's an adults-only rule - see changeAdults()).
        if (next > MAX_CHILDREN) {
            Toast.makeText(requireContext(), R.string.error_children_exceed_max, Toast.LENGTH_SHORT).show();
            return;
        }
        state.children = next;
        refreshCounts();
        rebuildChildAgeFields();
    }

    private void refreshCounts() {
        tvAdultsCount.setText(String.valueOf(getState().adults));
        tvChildrenCount.setText(String.valueOf(getState().children));
    }

    private void rebuildChildAgeFields() {
        layoutChildAgeFields.removeAllViews();
        childAgeInputLayouts.clear();
        childAgeInputs.clear();

        int children = getState().children;
        tvChildAgeNotice.setVisibility(children > 0 ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < children; i++) {
            TextInputLayout til = new TextInputLayout(requireContext());
            til.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            til.setPadding(0, 0, 0, dp(10));
            til.setHint(getString(R.string.child_age_field_format, i + 1));

            TextInputEditText input = new TextInputEditText(til.getContext());
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            if (i < getState().additionalGuests.size()) {
                input.setText(String.valueOf(getState().additionalGuests.get(i).age));
            }
            til.addView(input);

            layoutChildAgeFields.addView(til);
            childAgeInputLayouts.add(til);
            childAgeInputs.add(input);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean validateBeforeNext() {
        BookingWizardState state = getState();
        if (state.children > MAX_CHILDREN) {
            Toast.makeText(requireContext(), R.string.error_children_exceed_max, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (state.adults > state.totalSelectedCapacity()) {
            Toast.makeText(requireContext(), R.string.error_guests_exceed_capacity, Toast.LENGTH_SHORT).show();
            return false;
        }

        List<BookingAndReservationActivity.AdditionalGuest> childGuests = new ArrayList<>();
        for (int i = 0; i < childAgeInputs.size(); i++) {
            TextInputEditText input = childAgeInputs.get(i);
            TextInputLayout til = childAgeInputLayouts.get(i);
            String text = input.getText() != null ? input.getText().toString().trim() : "";
            if (text.isEmpty()) {
                til.setError(getString(R.string.error_child_age_required));
                return false;
            }
            int age;
            try {
                age = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                til.setError(getString(R.string.error_child_age_required));
                return false;
            }
            if (age < MIN_CHILD_AGE) {
                til.setError(getString(R.string.error_child_age_too_young));
                return false;
            }
            if (age > MAX_CHILD_AGE) {
                til.setError(getString(R.string.error_child_age_too_old));
                return false;
            }
            til.setError(null);
            childGuests.add(new BookingAndReservationActivity.AdditionalGuest("Child " + (i + 1), age, null, "Child"));
        }

        state.additionalGuests.clear();
        state.additionalGuests.addAll(childGuests);

        clampAmenityQuantitiesToGuestCount();
        return true;
    }

    /**
     * Amenities (Step 3) are picked before adults/children are known (this
     * step), so their quantity is only capped by room capacity at
     * selection time (see Step3AmenitiesFragment). Now that the real
     * adults+children total is known, silently trim any amenity whose
     * quantity exceeds it - a guest picking amenities for a family of 3
     * shouldn't end up with, say, 6 breakfasts selected just because the
     * room capacity was higher.
     */
    private void clampAmenityQuantitiesToGuestCount() {
        int totalGuests = Math.max(1, getState().adults + getState().children);
        boolean anyClamped = false;
        for (AddOnAmenity amenity : getState().selectedAmenities) {
            if (amenity.getQuantity() > totalGuests) {
                amenity.setQuantity(totalGuests);
                anyClamped = true;
            }
        }
        if (anyClamped) {
            Toast.makeText(requireContext(), R.string.amenity_quantity_clamped_to_guests, Toast.LENGTH_LONG).show();
        }
    }
}
