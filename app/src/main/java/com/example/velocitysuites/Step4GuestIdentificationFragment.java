package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Representative: who will check in. Two large choices, neither selected at first:
 * <ul>
 *   <li><b>Owner (me)</b> - Last, First and Middle name come from the account profile and are shown read-only. A profile without a middle
 *   name leaves it empty (it is optional). The booking is sent exactly as before for "no separate representative": the owner's first and
 *   last name (see {@link RepresentativeName#namesToSend}).</li>
 *   <li><b>Someone else</b> - three empty, editable fields: last and first name required, middle name optional, the same character rules
 *   as ever.</li>
 * </ul>
 * Next stays blocked, with an inline message, until a choice is made (and, for Someone else, the names are valid).
 */
public class Step4GuestIdentificationFragment extends WizardStepFragment {

    private MaterialButtonToggleGroup toggle;
    private MaterialButton btnOwner;
    private MaterialButton btnOther;
    private TextView tvWhoError;
    private View layoutOwner;
    private View layoutOther;
    private TextView tvOwnerMissing;

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
        toggle = view.findViewById(R.id.toggleWho);
        btnOwner = view.findViewById(R.id.btnWhoOwner);
        btnOther = view.findViewById(R.id.btnWhoOther);
        tvWhoError = view.findViewById(R.id.tvWhoError);
        layoutOwner = view.findViewById(R.id.layoutWhoOwner);
        layoutOther = view.findViewById(R.id.layoutWhoOther);
        tvOwnerMissing = view.findViewById(R.id.tvOwnerProfileMissing);

        // The two copies of the three name fields live under their own parent, so look each up inside it.
        View other = view.findViewById(R.id.otherFields);
        tilLast = other.findViewById(R.id.tilRepLast);
        tilFirst = other.findViewById(R.id.tilRepFirst);
        tilMiddle = other.findViewById(R.id.tilRepMiddle);
        etLast = other.findViewById(R.id.etRepLast);
        etFirst = other.findViewById(R.id.etRepFirst);
        etMiddle = other.findViewById(R.id.etRepMiddle);
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
        boolean typedByGuest = state.who == BookingWizardState.Who.OTHER;
        etLast.setText(typedByGuest ? state.repLast : state.otherLast);
        etFirst.setText(typedByGuest ? state.repFirst : state.otherFirst);
        etMiddle.setText(typedByGuest ? state.repMiddle : state.otherMiddle);

        bindOwnerFields(view.findViewById(R.id.ownerFields));

        // Restore the choice without triggering the listener's side effects, then listen.
        if (state.who == BookingWizardState.Who.OWNER) toggle.check(R.id.btnWhoOwner);
        else if (state.who == BookingWizardState.Who.OTHER) toggle.check(R.id.btnWhoOther);
        applyChoice(state.who);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                if (group.getCheckedButtonId() == View.NO_ID) { // the guest tapped the selected one again: back to "nothing chosen"
                    getState().who = BookingWizardState.Who.NONE;
                    applyChoice(BookingWizardState.Who.NONE);
                }
                return;
            }
            getState().who = checkedId == R.id.btnWhoOwner ? BookingWizardState.Who.OWNER : BookingWizardState.Who.OTHER;
            applyChoice(getState().who);
        });
        view.findViewById(R.id.tvOwnerNotYou).setOnClickListener(v -> toggle.check(R.id.btnWhoOther));

        boolean open = savedInstanceState != null && savedInstanceState.getBoolean("whoOpen");
        showWho(open);
        rowWho.setOnClickListener(v -> showWho(whoBody.getVisibility() != View.VISIBLE));
    }

    /** The owner's three names from the account profile, read-only. */
    private void bindOwnerFields(View ownerFields) {
        String[] names = ownerNames(requireContext());
        TextInputEditText last = ownerFields.findViewById(R.id.etRepLast);
        TextInputEditText first = ownerFields.findViewById(R.id.etRepFirst);
        TextInputEditText middle = ownerFields.findViewById(R.id.etRepMiddle);
        last.setText(names[0]);
        first.setText(names[1]);
        middle.setText(names[2]);
        for (TextInputEditText f : new TextInputEditText[]{last, first, middle}) {
            f.setEnabled(false);
            f.setFocusable(false);
            f.setTextColor(requireContext().getColor(R.color.velocity_text_primary));
        }
        ((TextInputLayout) ownerFields.findViewById(R.id.tilRepMiddle)).setHint(getString(R.string.who_owner_middle_hint));
    }

    /** {last, first, middle} of the signed-in account; empty strings for what the profile does not have. */
    static String[] ownerNames(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE);
        return new String[]{RepresentativeName.clean(p.getString("userLastName", "")),
                RepresentativeName.clean(p.getString("userFirstName", "")),
                RepresentativeName.clean(p.getString("userMiddleName", ""))};
    }

    private boolean ownerProfileComplete() {
        String[] n = ownerNames(requireContext());
        return !n[0].isEmpty() && !n[1].isEmpty();
    }

    private void applyChoice(BookingWizardState.Who who) {
        layoutOwner.setVisibility(who == BookingWizardState.Who.OWNER ? View.VISIBLE : View.GONE);
        layoutOther.setVisibility(who == BookingWizardState.Who.OTHER ? View.VISIBLE : View.GONE);
        tvOwnerMissing.setVisibility(who == BookingWizardState.Who.OWNER && !ownerProfileComplete() ? View.VISIBLE : View.GONE);
        if (who != BookingWizardState.Who.NONE) tvWhoError.setVisibility(View.GONE);
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

    /** Copies the choice into the wizard state: what the API sends and what the review shows. */
    private void store() {
        BookingWizardState state = getState();
        RepresentativeName.Parts typed = typed();
        if (state.who == BookingWizardState.Who.OTHER) {
            state.repLast = typed.last;
            state.repFirst = typed.first;
            state.repMiddle = typed.middle;
        } else {
            // The owner checks in (or nothing is chosen yet): no separate representative.
            state.repLast = "";
            state.repFirst = "";
            state.repMiddle = "";
            // Keep what was typed for "Someone else" in case the guest switches back.
            state.otherLast = typed.last;
            state.otherFirst = typed.first;
            state.otherMiddle = typed.middle;
        }
        String[] names = RepresentativeName.namesToSend(requireContext(), state.representative());
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
        return getString(R.string.step_title_representative);
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
        RepresentativeName.Parts p = typed();
        RepresentativeName.Result r = RepresentativeName.validate(p);
        // "Someone else" with nothing typed is not "no representative" - the names are required.
        RepresentativeName.Problem last = p.isBlank() ? RepresentativeName.Problem.REQUIRED : r.last;
        RepresentativeName.Problem first = p.isBlank() ? RepresentativeName.Problem.REQUIRED : r.first;
        set(tilLast, messageFor(last, R.string.error_rep_last_required));
        set(tilFirst, messageFor(first, R.string.error_rep_first_required));
        set(tilMiddle, messageFor(r.middle, 0));
        if (last != RepresentativeName.Problem.NONE) return etLast;
        if (first != RepresentativeName.Problem.NONE) return etFirst;
        if (r.middle != RepresentativeName.Problem.NONE) return etMiddle;
        return null;
    }

    private void set(TextInputLayout til, @StringRes int message) {
        CharSequence error = message == 0 ? null : getString(message);
        if (!java.util.Objects.equals(String.valueOf(til.getError()), String.valueOf(error))) til.setError(error);
    }

    @Override
    public boolean validateBeforeNext() {
        BookingWizardState state = getState();
        if (state.who == BookingWizardState.Who.NONE) {
            tvWhoError.setVisibility(View.VISIBLE);
            tvWhoError.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
            return false;
        }
        if (state.who == BookingWizardState.Who.OWNER) {
            if (!ownerProfileComplete()) {
                tvOwnerMissing.setVisibility(View.VISIBLE);
                return false;
            }
            store();
            return true;
        }
        View firstProblem = showErrors();
        if (firstProblem != null) {
            firstProblem.requestFocus();
            return false;
        }
        store();
        return true;
    }

    @Override
    public int validationMessageRes() {
        return 0; // inline messages
    }

    @Override
    public String nextConfirmTitle() {
        return getString(R.string.proceed_to_guest_details);
    }

    @Override
    public String nextConfirmSummary() {
        BookingWizardState state = getState();
        if (state.who == BookingWizardState.Who.OWNER) {
            String[] n = ownerNames(requireContext());
            return getString(R.string.who_summary_owner_format, new RepresentativeName.Parts(n[0], n[1], n[2]).formatted());
        }
        return getString(R.string.who_summary_other_format, state.representative().formatted());
    }
}
