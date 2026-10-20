package com.example.velocitysuites;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Step 4: the OPTIONAL representative name - the person who will check in on the guest's behalf. Blank is always valid (the
 * account owner then checks in); filled, it must be a real full name. How it is sent is in {@link RepresentativeName}.
 */
public class Step4GuestIdentificationFragment extends WizardStepFragment {

    private TextInputLayout tilName;
    private TextInputEditText etName;
    private View rowWho;
    private TextView whoBody;
    private ImageView whoChevron;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step4_guest_id, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tilName = view.findViewById(R.id.tilRepresentativeName);
        etName = view.findViewById(R.id.etRepresentativeName);
        rowWho = view.findViewById(R.id.rowRepresentativeWho);
        whoBody = view.findViewById(R.id.tvRepresentativeWhoBody);
        whoChevron = view.findViewById(R.id.ivRepresentativeWhoChevron);

        // Blocks characters a name never has while typing: letters (incl. accented), spaces, periods, hyphens, apostrophes.
        InputFilter nameFilter = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (!Character.isLetter(c) && c != ' ' && c != '.' && c != '-' && c != '\'' && c != '\u2019') return "";
            }
            return null;
        };
        etName.setFilters(new InputFilter[]{nameFilter, new InputFilter.LengthFilter(RepresentativeName.MAX_LENGTH)});
        etName.setText(getState().representativeName);
        etName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (tilName.getError() != null && RepresentativeName.isValid(s.toString())) tilName.setError(null); // clears as soon as it is right
            }
        });

        boolean open = savedInstanceState != null && savedInstanceState.getBoolean("whoOpen");
        showWho(open);
        rowWho.setOnClickListener(v -> showWho(whoBody.getVisibility() != View.VISIBLE));
    }

    private void showWho(boolean open) {
        whoBody.setVisibility(open ? View.VISIBLE : View.GONE);
        whoChevron.setRotation(open ? 180f : 0f);
        rowWho.setContentDescription(getString(R.string.representative_who_title));
        rowWho.setSelected(open);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (whoBody != null) outState.putBoolean("whoOpen", whoBody.getVisibility() == View.VISIBLE);
    }

    private void store() {
        BookingWizardState state = getState();
        state.representativeName = RepresentativeName.clean(etName.getText() != null ? etName.getText().toString() : "");
        // The API's fields: the representative's name, or the account owner's when the field is blank.
        String[] names = RepresentativeName.namesToSend(requireContext(), state.representativeName);
        state.guestFirstName = names[0];
        state.guestMiddleName = "";
        state.guestLastName = names[1];
    }

    @Override
    protected void saveDraft() {
        if (etName == null) return;
        store();
    }

    @Override
    public String stepTitle() {
        return "Guest and Identification";
    }

    /** Blank is valid and never blocks Next; a filled name must be a valid full name. */
    @Override
    public boolean validateBeforeNext() {
        store();
        if (!RepresentativeName.isValid(getState().representativeName)) {
            tilName.setError(getString(R.string.error_representative_invalid));
            etName.requestFocus();
            return false;
        }
        tilName.setError(null);
        return true;
    }
}
