package com.example.velocitysuites;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Step 4: representative guest's Last/First/Middle name (middle optional).
 * Ported from BookingAndReservationActivity's Step 2 card guest-name fields
 * (validateForm()'s first/last-name checks). Deliberately always starts
 * blank - only ever reflects what BookingWizardState already has from
 * earlier in this same wizard session (e.g. after Back navigation), never
 * seeded from the logged-in account's own profile name.
 */
public class Step4GuestIdentificationFragment extends WizardStepFragment {

    private TextInputLayout tilFirstName;
    private TextInputLayout tilLastName;
    private TextInputEditText etFirstName;
    private TextInputEditText etMiddleName;
    private TextInputEditText etLastName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step4_guest_id, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tilFirstName = view.findViewById(R.id.tilPrimaryGuestFirstName);
        tilLastName = view.findViewById(R.id.tilPrimaryGuestLastName);
        etFirstName = view.findViewById(R.id.etPrimaryGuestFirstName);
        etMiddleName = view.findViewById(R.id.etPrimaryGuestMiddleName);
        etLastName = view.findViewById(R.id.etPrimaryGuestLastName);

        // Letters (incl. accented), spaces, hyphens, and apostrophes only -
        // blocks disallowed characters at the point of typing rather than
        // validating after the fact, so it applies equally to Middle Name
        // "if a value is entered" with no extra logic needed.
        InputFilter nameFilter = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!Character.isLetter(c) && c != ' ' && c != '-' && c != '\'') {
                    return "";
                }
            }
            return null;
        };
        etFirstName.setFilters(new InputFilter[]{nameFilter});
        etLastName.setFilters(new InputFilter[]{nameFilter});
        etMiddleName.setFilters(new InputFilter[]{nameFilter});

        BookingWizardState state = getState();
        etFirstName.setText(state.guestFirstName);
        etMiddleName.setText(state.guestMiddleName);
        etLastName.setText(state.guestLastName);
    }

    @Override
    public String stepTitle() {
        return "Guest and Identification";
    }

    @Override
    public boolean validateBeforeNext() {
        BookingWizardState state = getState();
        state.guestFirstName = etFirstName.getText() != null ? etFirstName.getText().toString().trim() : "";
        state.guestMiddleName = etMiddleName.getText() != null ? etMiddleName.getText().toString().trim() : "";
        state.guestLastName = etLastName.getText() != null ? etLastName.getText().toString().trim() : "";

        // Last Name is checked/focused first since it's the first field on
        // screen (matches the field order) - only one field ever ends up
        // focused, whichever is the *first* invalid one found.
        boolean valid = true;
        View firstInvalidField = null;

        if (state.guestLastName.isEmpty()) {
            tilLastName.setError(getString(R.string.error_last_name_required));
            valid = false;
            firstInvalidField = etLastName;
        } else {
            tilLastName.setError(null);
        }
        if (state.guestFirstName.isEmpty()) {
            tilFirstName.setError(getString(R.string.error_first_name_required));
            valid = false;
            if (firstInvalidField == null) firstInvalidField = etFirstName;
        } else {
            tilFirstName.setError(null);
        }

        if (firstInvalidField != null) {
            firstInvalidField.requestFocus();
        }
        return valid;
    }
}
