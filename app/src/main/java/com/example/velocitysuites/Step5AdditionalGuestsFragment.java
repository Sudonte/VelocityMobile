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
 * field per declared child - each required 0-7. Adults + children COMBINED
 * must not EXCEED step 1's total room capacity (BookingWizardState#
 * totalSelectedCapacity(), summed across every selected room including
 * quantity) before Next is allowed (see validateBeforeNext()), per spec -
 * matching this same app's Hotel Terms & Policy wording ("must not exceed
 * the selected room's stated capacity") and BookingAndReservationActivity's
 * own Modify-guest-count check. The live +/- steppers enforce the same
 * not-exceed guard as it's adjusted (changeAdults()/changeChildren()).
 * There is no separate cap on children - only the shared room-capacity
 * limit (see GuestCapacity); at least 1 adult is always required, and once
 * the total reaches capacity both + buttons are disabled. If the rooms
 * changed since the counts were set, they're lowered to fit on entry.
 * (BookingAndReservationActivity only ever derived
 * adults/children after the fact from a flat guest list's ages - see
 * finalizeBooking()); this step collects the counts up front instead, per
 * spec.
 */
public class Step5AdditionalGuestsFragment extends WizardStepFragment {

    private static final int MIN_CHILD_AGE = 0;
    private static final int MAX_CHILD_AGE = 7;

    private TextView tvCapacityHint;
    private TextView tvGuestCounter;
    private View btnAdultsMinus;
    private View btnAdultsPlus;
    private View btnChildrenMinus;
    private View btnChildrenPlus;
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

        tvGuestCounter = view.findViewById(R.id.tvGuestCounter);
        btnAdultsMinus = view.findViewById(R.id.btnAdultsMinus);
        btnAdultsPlus = view.findViewById(R.id.btnAdultsPlus);
        btnChildrenMinus = view.findViewById(R.id.btnChildrenMinus);
        btnChildrenPlus = view.findViewById(R.id.btnChildrenPlus);
        btnAdultsMinus.setOnClickListener(v -> changeAdults(-1));
        btnAdultsPlus.setOnClickListener(v -> changeAdults(1));
        btnChildrenMinus.setOnClickListener(v -> changeChildren(-1));
        btnChildrenPlus.setOnClickListener(v -> changeChildren(1));

        // The guest may have gone back and swapped to a smaller room since
        // these counts were set - never let an over-capacity count through.
        if (getState().clampGuestsToCapacity()) {
            Toast.makeText(requireContext(),
                    getString(R.string.guest_count_adjusted_to_capacity, getState().maxGuests()),
                    Toast.LENGTH_LONG).show();
        }
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
        if (delta > 0 && !GuestCapacity.canAddGuest(state.adults, state.children, state.totalSelectedCapacity())) {
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
        if (delta > 0 && !GuestCapacity.canAddGuest(state.adults, state.children, state.totalSelectedCapacity())) {
            Toast.makeText(requireContext(), R.string.error_guests_exceed_capacity, Toast.LENGTH_SHORT).show();
            return;
        }
        state.children = next;
        refreshCounts();
        rebuildChildAgeFields();
    }

    /** Re-renders the counts, the "N of M guests" counter, and enables/disables each stepper button. */
    private void refreshCounts() {
        BookingWizardState state = getState();
        tvAdultsCount.setText(String.valueOf(state.adults));
        tvChildrenCount.setText(String.valueOf(state.children));
        int total = state.adults + state.children;
        int max = state.maxGuests();
        tvGuestCounter.setText(getString(R.string.guest_counter_format, total,
                getResources().getQuantityString(R.plurals.guests_noun, max, max)));
        boolean atCapacity = !GuestCapacity.canAddGuest(state.adults, state.children, state.totalSelectedCapacity());
        btnAdultsPlus.setEnabled(!atCapacity);
        btnChildrenPlus.setEnabled(!atCapacity);
        btnAdultsMinus.setEnabled(state.adults > 1);
        btnChildrenMinus.setEnabled(state.children > 0);
        tvCapacityHint.setText(atCapacity
                ? getString(R.string.guest_capacity_reached_hint)
                : getString(R.string.capacity_hint_format, max));
    }

    private void rebuildChildAgeFields() {
        // Snapshot whatever the guest already typed in the still-visible fields
        // before tearing them down - additionalGuests only gets (re)populated on
        // a successful validateBeforeNext(), so without this, bumping the
        // children count again (e.g. 2 -> 3) after typing ages but before
        // tapping Next would silently wipe every age field back to blank.
        List<String> previousAges = new ArrayList<>();
        for (TextInputEditText input : childAgeInputs) {
            previousAges.add(input.getText() != null ? input.getText().toString().trim() : "");
        }

        layoutChildAgeFields.removeAllViews();
        childAgeInputLayouts.clear();
        childAgeInputs.clear();

        int children = getState().children;
        tvChildAgeNotice.setVisibility(children > 0 ? View.VISIBLE : View.GONE);

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
            List<BookingAndReservationActivity.AdditionalGuest> savedChildren = childEntries(getState());
            if (i < previousAges.size() && !previousAges.get(i).isEmpty()) {
                input.setText(previousAges.get(i));
            } else if (i < savedChildren.size() && savedChildren.get(i).age >= 0) {
                input.setText(String.valueOf(savedChildren.get(i).age));
            }
            til.addView(input);

            layoutChildAgeFields.addView(til);
            childAgeInputLayouts.add(til);
            childAgeInputs.add(input);
        }
    }

    private static boolean isChildEntry(BookingAndReservationActivity.AdditionalGuest g) {
        return "Child".equalsIgnoreCase(g.relationship);
    }

    private static List<BookingAndReservationActivity.AdditionalGuest> childEntries(BookingWizardState state) {
        List<BookingAndReservationActivity.AdditionalGuest> result = new ArrayList<>();
        for (BookingAndReservationActivity.AdditionalGuest g : state.additionalGuests) {
            if (isChildEntry(g)) result.add(g);
        }
        return result;
    }

    private static List<BookingAndReservationActivity.AdditionalGuest> nonChildEntries(BookingWizardState state) {
        List<BookingAndReservationActivity.AdditionalGuest> result = new ArrayList<>();
        for (BookingAndReservationActivity.AdditionalGuest g : state.additionalGuests) {
            if (!isChildEntry(g)) result.add(g);
        }
        return result;
    }

    /** Going Back keeps the ages typed so far (blank or invalid ones are remembered as "not entered yet" = -1 and shown empty). */
    @Override
    protected void saveDraft() {
        if (childAgeInputs.isEmpty() && tvAdultsCount == null) return;
        BookingWizardState state = getState();
        List<BookingAndReservationActivity.AdditionalGuest> draft = new ArrayList<>();
        for (int i = 0; i < childAgeInputs.size(); i++) {
            String text = childAgeInputs.get(i).getText() != null ? childAgeInputs.get(i).getText().toString().trim() : "";
            int age = -1;
            try {
                age = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                // left as "not entered"
            }
            draft.add(new BookingAndReservationActivity.AdditionalGuest("Child " + (i + 1), age, null, "Child"));
        }
        List<BookingAndReservationActivity.AdditionalGuest> kept = nonChildEntries(state);
        state.additionalGuests.clear();
        state.additionalGuests.addAll(kept);
        state.additionalGuests.addAll(draft);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean validateBeforeNext() {
        BookingWizardState state = getState();
        if (!GuestCapacity.isValid(state.adults, state.children, state.totalSelectedCapacity())) {
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

        // Only the child entries are this step's to rewrite; any other companion already on the
        // record (e.g. one added by an older version of the app) is carried over untouched.
        List<BookingAndReservationActivity.AdditionalGuest> kept = nonChildEntries(state);
        state.additionalGuests.clear();
        state.additionalGuests.addAll(kept);
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
