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
import androidx.annotation.StringRes;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Step 4: the OPTIONAL representative - the person who will check in on the guest's behalf - as Last name, First name and Middle name
 * (optional). All three empty is valid and never blocks Next (the account owner checks in); if any is filled, last and first name are
 * required. How it is sent is in {@link RepresentativeName}.
 */
public class Step4GuestIdentificationFragment extends WizardStepFragment {

    private TextInputLayout tilLast;
    private TextInputLayout tilFirst;
    private TextInputLayout tilMiddle;
    private TextInputEditText etLast;
    private TextInputEditText etFirst;
    private TextInputEditText etMiddle;
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
        tilLast = view.findViewById(R.id.tilRepLast);
        tilFirst = view.findViewById(R.id.tilRepFirst);
        tilMiddle = view.findViewById(R.id.tilRepMiddle);
        etLast = view.findViewById(R.id.etRepLast);
        etFirst = view.findViewById(R.id.etRepFirst);
        etMiddle = view.findViewById(R.id.etRepMiddle);
        rowWho = view.findViewById(R.id.rowRepresentativeWho);
        whoBody = view.findViewById(R.id.tvRepresentativeWhoBody);
        whoChevron = view.findViewById(R.id.ivRepresentativeWhoChevron);

        // Blocks characters a name never has while typing: letters (incl. accented), spaces, periods, hyphens, apostrophes.
        InputFilter nameFilter = (source, start, end, dest, dstart, dend) -> {
            StringBuilder kept = null;
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                boolean ok = Character.isLetter(c) || c == ' ' || c == '.' || c == '-' || c == (char) 39 || c == (char) 0x2019;
                if (!ok && kept == null) kept = new StringBuilder(source.subSequence(start, i));
                if (ok && kept != null) kept.append(c);
            }
            return kept; // null keeps everything; otherwise the text without the characters a name never has
        };
        for (TextInputEditText field : new TextInputEditText[]{etLast, etFirst, etMiddle}) {
            field.setFilters(new InputFilter[]{nameFilter, new InputFilter.LengthFilter(RepresentativeName.NAME_MAX)});
            field.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

                @Override
                public void afterTextChanged(Editable s) {
                    if (hasAnyError()) showErrors(); // errors follow the text: they clear as soon as a field is right
                }
            });
        }
        BookingWizardState state = getState();
        etLast.setText(state.repLast);
        etFirst.setText(state.repFirst);
        etMiddle.setText(state.repMiddle);

        boolean open = savedInstanceState != null && savedInstanceState.getBoolean("whoOpen");
        showWho(open);
        rowWho.setOnClickListener(v -> showWho(whoBody.getVisibility() != View.VISIBLE));
    }

    private boolean hasAnyError() {
        return tilLast.getError() != null || tilFirst.getError() != null || tilMiddle.getError() != null;
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

    private RepresentativeName.Parts typed() {
        return new RepresentativeName.Parts(text(etLast), text(etFirst), text(etMiddle));
    }

    private static String text(TextInputEditText f) {
        return f.getText() != null ? f.getText().toString() : "";
    }

    private void store() {
        RepresentativeName.Parts p = typed();
        BookingWizardState state = getState();
        state.repLast = p.last;
        state.repFirst = p.first;
        state.repMiddle = p.middle;
        // The API's fields: the representative's names, or the account owner's when the block is empty.
        String[] names = RepresentativeName.namesToSend(requireContext(), p);
        state.guestFirstName = names[0];
        state.guestMiddleName = names[1];
        state.guestLastName = names[2];
    }

    @Override
    protected void saveDraft() {
        if (etLast == null) return;
        store();
    }

    @Override
    public String stepTitle() {
        return "Guest and Identification";
    }

    @StringRes
    private static int messageFor(RepresentativeName.Problem problem, @StringRes int required) {
        switch (problem) {
            case REQUIRED: return required;
            case TOO_SHORT: return R.string.error_rep_too_short;
            case TOO_LONG: return R.string.error_rep_too_long;
            case LETTERS_ONLY: return R.string.error_rep_letters_only;
            default: return 0;
        }
    }

    /** Shows the current problem under each field (none when it is fine). Returns the first field with a problem, or null. */
    @Nullable
    private View showErrors() {
        RepresentativeName.Result r = RepresentativeName.validate(typed());
        set(tilLast, messageFor(r.last, R.string.error_rep_last_required));
        set(tilFirst, messageFor(r.first, R.string.error_rep_first_required));
        set(tilMiddle, messageFor(r.middle, 0));
        if (r.last != RepresentativeName.Problem.NONE) return etLast;
        if (r.first != RepresentativeName.Problem.NONE) return etFirst;
        if (r.middle != RepresentativeName.Problem.NONE) return etMiddle;
        return null;
    }

    private void set(TextInputLayout til, @StringRes int message) {
        CharSequence error = message == 0 ? null : getString(message);
        if (!java.util.Objects.equals(String.valueOf(til.getError()), String.valueOf(error))) til.setError(error);
    }

    /** All empty is valid and never blocks Next; any field filled makes last and first name required. */
    @Override
    public boolean validateBeforeNext() {
        store();
        View firstProblem = showErrors();
        if (firstProblem != null) {
            firstProblem.requestFocus();
            return false;
        }
        return true;
    }
}
