package com.example.velocitysuites;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Final review + T&amp;C gate step - "Bill Summary and Review" for Booking
 * mode and a Reservation Modify, "Total Amount to Pay and Review" (Step 8 of
 * 8) for a fresh Reservation creation (see stepTitle()). Occupies step 7 for
 * the first two cases, step 8 for the third - see BookingWizardActivity's
 * mode/edit-dependent stepCount and createFragmentForStep().
 * <ul>
 *   <li>Booking mode: unchanged - stashes the reviewed BookingWizardState in
 *   PendingBookingPayload and opens PaymentActivity in "pending booking" mode
 *   (GCash-only). PaymentActivity itself calls
 *   RoomRepository#createDirectBooking() only after the GCash portal step
 *   succeeds - see PaymentActivity#submitPendingBookingGroups().</li>
 *   <li>Reservation Modify (edit mode): unchanged - saves via
 *   updateReservationFull(), optionally switching payment method via this
 *   step's own editable chip picker.</li>
 *   <li>Reservation mode, fresh (not editing), Cash or GCash: Confirm
 *   Reservation is the terminal action - calls RoomRepository#createReservation()
 *   directly with no payment attached, regardless of payment method, and
 *   never opens PaymentActivity. A GCash Reservation is created in an
 *   awaiting-payment state and paid later via a separate Pay Now action from
 *   the reservation's own details screen - deliberately reverted away from a
 *   same-day GCash-at-creation experiment (PendingReservationPayload /
 *   RoomRepository#createReservationWithPayment(), still present but
 *   intentionally unused - see PaymentActivity#EXTRA_PENDING_RESERVATION's
 *   docblock) after product direction confirmed Confirm Reservation must
 *   never redirect into the payment workflow.</li>
 * </ul>
 * A guest may have staged more than one distinct room TYPE in step 2 (e.g.
 * 2 Deluxe + 1 Suite) - createReservation(List, ...) sends every selected
 * room type/quantity as one atomic call, never a sequential
 * per-room-type-group loop - see MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md.
 */
public class Step8ReviewPaymentFragment extends WizardStepFragment {

    private RoomRepository repository;

    private LinearLayout layoutReviewSections;
    private LinearLayout layoutBillRows;
    private LinearLayout layoutPayNow;
    private TextView tvSummaryTotal;
    private View layoutEditTotals;
    private TextView tvSummaryDiscountNote;
    private LinearLayout layoutEditTotalsRows;
    /** The server's old/new/paid/balance for the edit that was just saved (the payment-method switch response doesn't carry it). */
    private Booking.EditSummary lastEditSummary;
    private boolean editIdUploadFailed;
    private View cardPaymentNextNotice;
    private View cardPaymentMethodSummary;
    private TextView tvSummaryPaymentMethod;
    private TextView tvSummaryPaymentMethodNote;
    private View layoutEditPaymentMethod;
    private com.google.android.material.chip.ChipGroup cgEditPaymentMethod;

    private TermsConsentView termsConsent;
    private TextView tvConfirmHint;
    private MaterialButton btnConfirm;

    private boolean submitting = false;
    /** The "Are you sure?" dialog is up - a fast second tap on Confirm must not stack a second one. */
    private boolean confirmDialogOpen = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step8_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = RoomRepository.getInstance(requireContext());

        layoutReviewSections = view.findViewById(R.id.layoutReviewSections);
        layoutBillRows = view.findViewById(R.id.layoutBillRows);
        layoutPayNow = view.findViewById(R.id.layoutPayNow);
        tvSummaryTotal = view.findViewById(R.id.tvSummaryTotal);
        layoutEditTotals = view.findViewById(R.id.layoutEditTotals);
        tvSummaryDiscountNote = view.findViewById(R.id.tvSummaryDiscountNote);
        layoutEditTotalsRows = view.findViewById(R.id.layoutEditTotalsRows);
        cardPaymentNextNotice = view.findViewById(R.id.cardPaymentNextNotice);
        cardPaymentMethodSummary = view.findViewById(R.id.cardPaymentMethodSummary);
        tvSummaryPaymentMethod = view.findViewById(R.id.tvSummaryPaymentMethod);
        tvSummaryPaymentMethodNote = view.findViewById(R.id.tvSummaryPaymentMethodNote);
        layoutEditPaymentMethod = view.findViewById(R.id.layoutEditPaymentMethod);
        cgEditPaymentMethod = view.findViewById(R.id.cgEditPaymentMethod);

        termsConsent = view.findViewById(R.id.termsConsent);
        tvConfirmHint = view.findViewById(R.id.tvConfirmHint);
        btnConfirm = view.findViewById(R.id.btnConfirmTransaction);

        BookingWizardState state = getState();
        boolean editMode = getWizardActivity().isEditMode();
        boolean freshReservation = !editMode && !state.isBookingMode();

        if (editMode) {
            cardPaymentNextNotice.setVisibility(View.GONE);
            cardPaymentMethodSummary.setVisibility(View.GONE);
            layoutEditPaymentMethod.setVisibility(View.VISIBLE);
            cgEditPaymentMethod.check("gcash".equalsIgnoreCase(state.paymentMethod) ? R.id.chipEditGcash : R.id.chipEditCash);
            cgEditPaymentMethod.setOnCheckedStateChangeListener((group, checkedIds) ->
                    state.paymentMethod = checkedIds.contains(R.id.chipEditGcash) ? "gcash" : "cash");
            btnConfirm.setText(R.string.save_changes);
        } else if (state.isBookingMode()) {
            cardPaymentNextNotice.setVisibility(View.VISIBLE);
            cardPaymentMethodSummary.setVisibility(View.GONE);
            layoutEditPaymentMethod.setVisibility(View.GONE);
            btnConfirm.setText(R.string.confirm_booking_button);
        } else {
            cardPaymentNextNotice.setVisibility(View.GONE);
            layoutEditPaymentMethod.setVisibility(View.GONE);
            cardPaymentMethodSummary.setVisibility(View.VISIBLE);
            boolean gcash = "gcash".equalsIgnoreCase(state.paymentMethod);
            tvSummaryPaymentMethod.setText(getString(R.string.payment_method_selected_format,
                    getString(gcash ? R.string.payment_method_gcash : R.string.payment_method_cash)));
            tvSummaryPaymentMethodNote.setText(gcash ? R.string.payment_method_gcash_note : R.string.payment_method_cash_note);
            btnConfirm.setText(R.string.confirm_reservation_button);
        }

        // Restore both flags from BookingWizardState (not fragment-local
        // fields) since this fragment is recreated fresh every time Step 8 is
        // shown - without this, going Back to an earlier step and Next again
        // would silently re-lock a checkbox the guest already unlocked/
        // checked, and re-disable Confirm even though nothing actually
        // changed. setChecked() runs before the listener is attached below so
        // restoring it doesn't re-trigger side effects.
        termsConsent.setActionLabel(btnConfirm.getText());
        termsConsent.setAddendum(() -> getString("gcash".equalsIgnoreCase(getState().paymentMethod)
                ? R.string.hotel_terms_policy_gcash_addendum : R.string.hotel_terms_policy_cash_addendum));
        termsConsent.restore(state.termsViewed, state.termsAccepted);
        termsConsent.setListener((viewed, accepted) -> {
            state.termsViewed = viewed;
            state.termsAccepted = accepted;
            updateConfirmButtonEnabled();
        });
        updateConfirmButtonEnabled();

        btnConfirm.setOnClickListener(v -> onConfirmClicked());
        tvConfirmHint.setOnClickListener(v -> showMissingAndScroll());

        renderSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Returning here via Back from payment.xml (e.g. the guest backed out
        // before completing GCash) must not leave Confirm permanently
        // disabled - re-evaluate against the still-accepted T&C checkbox.
        setSubmitting(false);
        String rejection = PendingBookingPayload.takeRoomsUnavailable();
        if (rejection != null) sendBackAfterRoomRejected(rejection);
    }

    /**
     * The server refused the booking because a room is no longer free (see AvailabilityRejection). Read what is free
     * now, drop only the rooms that are gone from the selection, say so, and open Room Selection - dates, guest
     * details, amenities and the rest of what was entered stay as they are.
     */
    private void sendBackAfterRoomRejected(String serverMessage) {
        BookingWizardState state = getState();
        Runnable goBack = () -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), getString(R.string.error_booking_room_rejected, serverMessage), Toast.LENGTH_LONG).show();
            getWizardActivity().goToStep(1);
        };
        if (state.checkIn == null || state.checkOut == null) {
            goBack.run();
            return;
        }
        repository.refreshRoomsFresh(state.checkIn, state.checkOut, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> freshRooms) {
                if (!isAdded()) return;
                RoomAvailabilityReconciler.reconcile(state.selectedRooms, freshRooms);
                goBack.run();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                goBack.run(); // Room Selection re-reads availability itself
            }
        });
    }

    @Override
    public String stepTitle() {
        BookingWizardState state = getState();
        boolean freshReservation = !getWizardActivity().isEditMode() && !state.isBookingMode();
        return getString(R.string.step_title_review);
    }

    @Override
    public boolean validateBeforeNext() {
        return getState().termsViewed && getState().termsAccepted;
    }

    /** Confirm/Save is only ever enabled once the guest has both opened the Terms, Conditions, and Policy (scrolled it to the bottom) and checked the agreement box - re-evaluated on every change to either. */
    private void updateConfirmButtonEnabled() {
        if (btnConfirm == null) return;
        BookingWizardState state = getState();
        btnConfirm.setEnabled(TermsGate.isActionEnabled(state.termsViewed, state.termsAccepted) && !submitting);
        int[] missing = TermsGate.missingItems(state.termsViewed, state.termsAccepted);
        tvConfirmHint.setVisibility(missing.length == 0 ? View.GONE : View.VISIBLE);
        if (missing.length != 0) {
            StringBuilder list = new StringBuilder(getString(R.string.terms_missing_header));
            for (int item : missing) list.append('\n').append(getString(R.string.terms_missing_item_line_format, getString(item)));
            tvConfirmHint.setText(list);
            tvConfirmHint.setTextColor(requireContext().getColor(R.color.velocity_text_secondary));
        }
    }

    /** Shows what is missing in red under the button and scrolls the page to the first problem (the Terms block). */
    private void showMissingAndScroll() {
        updateConfirmButtonEnabled();
        tvConfirmHint.setTextColor(requireContext().getColor(R.color.velocity_red_primary));
        View page = getView() == null ? null : getView().findViewById(R.id.step8Scroll);
        if (page instanceof androidx.core.widget.NestedScrollView) {
            ((androidx.core.widget.NestedScrollView) page).smoothScrollTo(0, Math.max(0, termsConsent.getTop() - dp(12)));
        }
        termsConsent.requestFocus();
    }

    private double roomsTotal() {
        return getState().roomsTotal();
    }

    private double amenitiesTotal() {
        return getState().amenitiesTotal();
    }

    /** The collapsible sections, each with an Edit link to its step, then the bill. */
    private void renderSummary() {
        BookingWizardState state = getState();
        android.content.Context c = requireContext();
        boolean editMode = getWizardActivity().isEditMode();
        boolean freshReservation = !editMode && !state.isBookingMode();
        layoutReviewSections.removeAllViews();

        // 1. Stays: dates, nights, every room type x quantity x price x nights, subtotals
        List<View> stayRows = new ArrayList<>();
        for (ReviewSheet.Section section : WizardReview.stays(c, state)) {
            stayRows.add(ReviewSheet.heading(c, section.heading, stayRows.isEmpty()));
            for (ReviewSheet.Row row : section.rows) stayRows.add(ReviewSheet.row(c, row));
        }
        layoutReviewSections.addView(buildSection(R.string.review_section_stays, 1, stayRows));

        // 2. Amenities: per stay and room
        List<View> amenityRows = new ArrayList<>();
        for (ReviewSheet.Section section : WizardReview.amenities(c, state)) {
            amenityRows.add(ReviewSheet.heading(c, section.heading, amenityRows.isEmpty()));
            for (ReviewSheet.Row row : section.rows) amenityRows.add(ReviewSheet.row(c, row));
        }
        if (amenityRows.isEmpty()) amenityRows.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_no_amenities), null)));
        layoutReviewSections.addView(buildSection(R.string.review_section_amenities, 2, amenityRows));

        // 3. Representative
        List<View> who = new ArrayList<>();
        RepresentativeName.Parts rep = state.who == BookingWizardState.Who.OWNER
                ? ownerParts(c) : state.representative();
        who.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_check_in_by),
                getString(state.who == BookingWizardState.Who.OTHER ? R.string.who_someone_else : R.string.who_owner_me))));
        if (!rep.formatted().isEmpty()) who.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_name_label), rep.formatted())));
        who.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_present_id_note), null)));
        layoutReviewSections.addView(buildSection(R.string.review_section_representative, 3, who));

        // 4. Guests: adults, minors and their ages
        List<View> guests = new ArrayList<>();
        guests.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.adults_label), String.valueOf(state.adults))));
        guests.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.children_label), String.valueOf(state.children))));
        int minor = 0;
        for (BookingAndReservationActivity.AdditionalGuest g : state.additionalGuests) {
            if (!"Child".equalsIgnoreCase(g.relationship)) continue;
            minor++;
            guests.add(ReviewSheet.row(c, ReviewSheet.Row.indented(getString(R.string.child_age_field_format, minor), g.age >= 0 ? String.valueOf(g.age) : "-")));
        }
        layoutReviewSections.addView(buildSection(R.string.review_section_guests, 4, guests));

        // 5. ID / discount
        List<View> id = new ArrayList<>();
        if (state.discount == null) {
            id.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_discount_label), getString(R.string.review_discount_none))));
        } else {
            id.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_discount_label),
                    state.discount.getName() + " (" + state.discount.getValueLabel() + ")")));
            id.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_id_photo_label),
                    getString(state.idCardImageUri != null || state.idCardOnFile ? R.string.review_id_photo_attached : R.string.review_id_photo_missing))));
        }
        layoutReviewSections.addView(buildSection(R.string.review_section_id, 5, id));

        // 6. Payment method (a new reservation chooses it in its own step)
        if (freshReservation) {
            List<View> pay = new ArrayList<>();
            boolean gcash = "gcash".equalsIgnoreCase(state.paymentMethod);
            pay.add(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.payment_method_section_label),
                    getString(gcash ? R.string.payment_method_gcash : R.string.payment_method_cash))));
            layoutReviewSections.addView(buildSection(R.string.review_section_payment_method, 6, pay));
        }

        renderBill(state, freshReservation);
        renderEditTotals();
    }

    private RepresentativeName.Parts ownerParts(android.content.Context c) {
        String[] n = Step4GuestIdentificationFragment.ownerNames(c);
        return new RepresentativeName.Parts(n[0], n[1], n[2]);
    }

    /** A card with a title, an Edit link that jumps back to {@code editStep}, and a body the guest can collapse. */
    private View buildSection(int titleRes, int editStep, List<View> rows) {
        android.content.Context c = requireContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_flat_box);
        card.setPadding(dp(14), dp(4), dp(14), dp(10));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(48));
        header.setClickable(true);
        header.setFocusable(true);

        TextView title = new TextView(c);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        title.setText(titleRes);
        title.setTextSize(13);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_primary));
        header.addView(title);

        TextView edit = new TextView(c);
        edit.setText(R.string.review_edit);
        edit.setTextSize(13);
        edit.setTextColor(ContextCompat.getColor(c, R.color.velocity_action_text));
        edit.setGravity(android.view.Gravity.CENTER);
        edit.setMinimumHeight(dp(48));
        edit.setMinimumWidth(dp(48));
        edit.setPadding(dp(8), 0, dp(8), 0);
        edit.setContentDescription(getString(R.string.review_edit_cd_format, getString(titleRes)));
        edit.setTag("edit");
        edit.setOnClickListener(v -> {
            if (!editGuard.tryAcquire()) return;
            getWizardActivity().goToStep(editStep);
        });
        header.addView(edit);

        ImageView chevron = new ImageView(c);
        chevron.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
        chevron.setImageResource(R.drawable.ic_expand_more);
        chevron.setColorFilter(ContextCompat.getColor(c, R.color.velocity_text_secondary));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        chevron.setRotation(180f); // expanded
        header.addView(chevron);
        card.addView(header);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        for (View row : rows) body.addView(row);
        card.addView(body);

        header.setOnClickListener(v -> {
            boolean open = body.getVisibility() == View.VISIBLE;
            body.setVisibility(open ? View.GONE : View.VISIBLE);
            chevron.setRotation(open ? 0f : 180f);
            header.setContentDescription(getString(open ? R.string.review_section_collapsed_format : R.string.review_section_expanded_format, getString(titleRes)));
        });
        return card;
    }

    private final ClickGuard editGuard = new ClickGuard();

    /** Room charges per stay, amenities, the total (bold), the discount estimate, and for a booking what is paid now and at the hotel. */
    private void renderBill(BookingWizardState state, boolean freshReservation) {
        android.content.Context c = requireContext();
        layoutBillRows.removeAllViews();
        List<BookingWizardState.Stay> stays = state.allStays();
        boolean several = stays.size() > 1;
        for (int i = 0; i < stays.size(); i++) {
            layoutBillRows.addView(ReviewSheet.row(c, ReviewSheet.Row.of(several ? getString(R.string.review_bill_rooms_stay_format, i + 1) : getString(R.string.review_rooms_total_label),
                    money(stays.get(i).roomsTotal()))));
        }
        for (int i = 0; i < stays.size(); i++) {
            double amenities = stays.get(i).amenitiesTotal();
            if (several && amenities <= 0.004) continue;
            layoutBillRows.addView(ReviewSheet.row(c, ReviewSheet.Row.of(several ? getString(R.string.review_bill_amenities_stay_format, i + 1) : getString(R.string.review_amenities_total_label),
                    money(amenities))));
        }
        if (several && amenitiesTotal() <= 0.004) {
            layoutBillRows.addView(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_amenities_total_label), money(0))));
        }
        double total = roomsTotal() + amenitiesTotal();
        tvSummaryTotal.setText(money(total));
        renderDiscountEstimate(state);

        layoutPayNow.removeAllViews();
        boolean showPayNow = state.isBookingMode() && !getWizardActivity().isEditMode();
        layoutPayNow.setVisibility(showPayNow ? View.VISIBLE : View.GONE);
        if (showPayNow) {
            // The guest picks 20, 30, 40 or 50 percent on the payment screen; the rest is paid at the hotel.
            double min = total * PAY_NOW_MIN;
            double max = total * PAY_NOW_MAX;
            layoutPayNow.addView(ReviewSheet.row(c, ReviewSheet.Row.bold(getString(R.string.review_pay_now_label), null)));
            layoutPayNow.addView(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_pay_now_range_label),
                    getString(R.string.review_range_format, money(min), money(max)))));
            layoutPayNow.addView(ReviewSheet.row(c, ReviewSheet.Row.of(getString(R.string.review_balance_hotel_label),
                    getString(R.string.review_range_format, money(total - max), money(total - min)))));
            TextView note = new TextView(c);
            note.setText(R.string.review_pay_now_note);
            note.setTextSize(12);
            note.setTextColor(ContextCompat.getColor(c, R.color.velocity_text_secondary));
            note.setPadding(0, dp(4), 0, 0);
            layoutPayNow.addView(note);
        }
    }

    /** A booking's online payment is a partial one: 20% up to 50% of the total (see PaymentActivity). */
    private static final double PAY_NOW_MIN = 0.20;
    private static final double PAY_NOW_MAX = 0.50;

    /** Shows the claimed discount's estimated value on the WHOLE bill (rooms + add-ons) - read from the Discount module values the app loaded on Step 6, never a number of its own; the backend's BillDiscount uses the same basis. */
    private void renderDiscountEstimate(BookingWizardState state) {
        double off = state.discount != null ? state.discount.estimateOff(roomsTotal() + amenitiesTotal()) : 0;
        if (off <= 0) {
            tvSummaryDiscountNote.setVisibility(View.GONE);
            return;
        }
        tvSummaryDiscountNote.setText(getString(R.string.summary_discount_estimate_format,
                state.discount.getName(), state.discount.getValueLabel(), money(off)));
        tvSummaryDiscountNote.setVisibility(View.VISIBLE);
    }

    /** Edit Reservation: previous total, new total, already paid, and balance due / excess - before the guest confirms. */
    private void renderEditTotals() {
        boolean editMode = getWizardActivity().isEditMode();
        layoutEditTotals.setVisibility(editMode ? View.VISIBLE : View.GONE);
        if (!editMode) return;
        EditTotals totals = getState().editTotals();
        layoutEditTotalsRows.removeAllViews();
        layoutEditTotalsRows.addView(buildRow(getString(R.string.edit_old_total), money(totals.oldTotal)));
        layoutEditTotalsRows.addView(buildRow(getString(R.string.edit_new_total), money(totals.newTotal)));
        layoutEditTotalsRows.addView(buildRow(getString(R.string.edit_amount_paid), money(totals.amountPaid)));
        if (totals.excess > 0) {
            layoutEditTotalsRows.addView(buildRow(getString(R.string.edit_excess_paid), money(totals.excess)));
        } else if (totals.balanceDue > 0) {
            layoutEditTotalsRows.addView(buildRow(getString(R.string.edit_balance_due), money(totals.balanceDue)));
        } else {
            layoutEditTotalsRows.addView(buildRow(getString(R.string.edit_balance_due), getString(R.string.edit_settled)));
        }
    }

    private String money(double amount) {
        return String.format(Locale.US, getString(R.string.price_format), amount);
    }

    private View buildRow(String label, String amount) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, dp(3), 0, dp(3));

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvLabel.setText(label);
        tvLabel.setTextSize(12f);
        tvLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.velocity_text_secondary));

        TextView tvAmount = new TextView(requireContext());
        tvAmount.setText(amount);
        tvAmount.setTextSize(12f);
        tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.velocity_text_primary));

        row.addView(tvLabel);
        row.addView(tvAmount);
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void onConfirmClicked() {
        if (submitting || confirmDialogOpen) return;
        BookingWizardState state = getState();

        if (!state.termsViewed || !state.termsAccepted) {
            showMissingAndScroll(); // inline, under the button, and the page jumps to the first problem
            return;
        }

        boolean isEditMode = getWizardActivity().isEditMode();

        // The guest may have left this screen open past midnight: re-validate the check-in against the live
        // hotel date and, if it is no longer allowed, send them back to the Dates step, which shows the inline error.
        if (!state.isCheckInWithinWindow(isEditMode)) {
            Toast.makeText(requireContext(), CheckInNotice.outsideWindowError(requireContext()), Toast.LENGTH_LONG).show();
            getWizardActivity().goToStep(1);
            return;
        }

        int confirmMessageRes = isEditMode
                ? R.string.confirm_update_reservation_msg
                : (state.isBookingMode() ? R.string.confirm_create_booking_msg : R.string.confirm_create_reservation_msg);

        MaterialAlertDialogBuilder builder = new SafeDialogs.Material(requireContext())
                .setMessage(confirmMessageRes)
                .setPositiveButton(isEditMode ? R.string.confirm_update_reservation_positive : R.string.confirm_dialog_positive,
                        (dialog, which) -> {
                            if (submitting) return; // a repeat tap on a second copy of this dialog
                            // Armed HERE, synchronously, the instant the guest taps Yes - not
                            // inside proceedAfterConfirm(), which only runs once
                            // recheckAvailabilityThenProceed()'s async network round-trip
                            // resolves. Between those two points btnConfirm was still enabled
                            // with no guard active, so an impatient second tap+Yes during a
                            // slow connection could fire two independent submissions (each
                            // correctly idempotency-keyed on its own, but two different keys
                            // don't dedupe each other) and create two Bookings/Reservations.
                            // setSubmitting(true) both flips the guard and disables btnConfirm
                            // immediately via updateConfirmButtonEnabled().
                            setSubmitting(true);
                            recheckAvailabilityThenProceed();
                        })
                .setNegativeButton(R.string.cancel_label, null);
        if (isEditMode) {
            builder.setTitle(R.string.confirm_update_reservation_title);
        }
        confirmDialogOpen = true;
        builder.setOnDismissListener(d -> confirmDialogOpen = false);
        builder.show();
    }

    /**
     * Last-moment availability recheck against the live server, right before
     * a Reservation/Booking is actually created - Step 2's own quantity
     * controls only validated against whatever availableCount the room list
     * happened to report when it was last fetched, which can go stale if
     * another guest books the same room type in the meantime. Fails open on
     * a network error - this is a courtesy recheck, not the authoritative
     * gate (the backend still independently validates at creation time), so
     * a transient connectivity hiccup here must not block a guest who was
     * otherwise ready to submit.
     */
    private void recheckAvailabilityThenProceed() {
        BookingWizardState state = getState();
        if (state.isGroup() || state.selectedRooms.isEmpty() || state.checkIn == null || state.checkOut == null) {
            proceedAfterConfirm(); // several stays: the server checks each one (all or nothing) and names the stay that does not fit
            return;
        }

        repository.refreshRoomsFresh(state.checkIn, state.checkOut, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> freshRooms) {
                if (!isAdded()) return;

                List<RoomAvailabilityReconciler.Shortfall> shortfalls = RoomAvailabilityReconciler.reconcile(state.selectedRooms, freshRooms);
                String issues = getWizardActivity().describeShortfalls(shortfalls);

                if (!shortfalls.isEmpty()) {
                    Toast.makeText(requireContext(), issues, Toast.LENGTH_LONG).show();
                    // Aborting without ever reaching proceedAfterConfirm() - undo the guard
                    // armed in onConfirmClicked() so btnConfirm is usable again once the
                    // guest comes back through Step 8 after adjusting their room selection.
                    setSubmitting(false);
                    getWizardActivity().goToStep(1);
                    return;
                }

                proceedAfterConfirm();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                proceedAfterConfirm();
            }
        });
    }

    private void proceedAfterConfirm() {
        BookingWizardState state = getState();

        if (getWizardActivity().isEditMode()) {
            setSubmitting(true);
            saveEditedReservation();
            return;
        }

        if (state.isBookingMode()) {
            // No repository call here - PendingBookingPayload/PaymentActivity
            // owns the actual createDirectBooking() call, gated behind a
            // successful GCash submission. Deliberately not finishing this
            // Activity: backing out of payment.xml before completing GCash
            // should return here with the review intact, not restart the
            // wizard from Step 1.
            setSubmitting(true);
            PendingBookingPayload.set(state);
            Intent intent = new Intent(requireContext(), PaymentActivity.class);
            intent.putExtra(PaymentActivity.EXTRA_PENDING_BOOKING, true);
            intent.putExtra("ALLOW_CASH", false);
            startActivity(intent);
            return;
        }

        // Fresh Reservation, Cash or GCash: Step 8's Confirm Reservation is
        // the terminal action for a Reservation - create directly here, no
        // payment call, no payment.xml hand-off, regardless of payment
        // method. A GCash Reservation is created in an awaiting-payment
        // state and paid later via the separate Pay Now action from the
        // reservation's own details screen - see
        // RoomRepository#createReservation(List, ...)'s payment_method
        // pass-through. Confirm Reservation must never redirect into the
        // payment workflow (see PaymentActivity#EXTRA_PENDING_RESERVATION's
        // docblock - that hand-off path is intentionally unused again).
        setSubmitting(true);
        createReservationsThenShowSuccess();
    }

    private void createReservationsThenShowSuccess() {
        BookingWizardState state = getState();
        AlertDialog dialog = new SafeDialogs.Material(requireContext())
                .setView(buildLoadingView())
                .setCancelable(false)
                .create();
        SafeDialogs.show(dialog);

        // One call covering every selected room type - see
        // RoomRepository#createReservation(List<List<Room>>, ...)'s own
        // doc. Replaces the old one-request-per-room-type loop
        // (submitReservationGroups()), which produced one separate
        // Reservation per distinct room type instead of a single combined
        // transaction.
        if (state.isGroup()) {
            createReservationGroup(dialog);
            return;
        }
        List<List<Room>> groups = new ArrayList<>(state.selectedRoomsGroupedByType().values());
        // One id per Confirm-button tap (never per room/line) - lets a
        // double-tap or client/network retry of this same submission
        // attempt safely return the original reservation instead of
        // creating a duplicate. See MULTI_ROOM_TRANSACTION_BACKEND_SPEC.md
        // section 9b.
        repository.createReservation(groups, state.checkIn, state.checkOut,
                state.adults, state.children,
                state.guestFirstName, state.guestMiddleName, state.guestLastName,
                state.idCardType, state.discountIdOrNull(), state.additionalGuests, state.selectedAmenities, state.paymentMethod,
                UUID.randomUUID().toString(),
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking created) {
                        uploadIdCardIfNeeded(created, () -> {
                            dismissSafely(dialog);
                            onReservationCreated(created);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        dismissSafely(dialog);
                        onReservationCreationFailed(message);
                    }
                });
    }

    /** Several stays, one request: a reservation per stay, all or nothing (the error names the stay that failed). */
    private void createReservationGroup(AlertDialog dialog) {
        BookingWizardState state = getState();
        repository.createWithStays(true, state.allStays(), state.adults, state.children,
                state.guestFirstName, state.guestMiddleName, state.guestLastName,
                state.idCardType, state.discountIdOrNull(), null, state.additionalGuests,
                state.paymentMethod, null, null, null, 0, null, UUID.randomUUID().toString(),
                new RoomRepository.RepositoryCallback<List<Booking>>() {
                    @Override
                    public void onSuccess(List<Booking> created) {
                        if (created.isEmpty()) {
                            dismissSafely(dialog);
                            return;
                        }
                        uploadIdCardIfNeeded(created.get(0), () -> {
                            dismissSafely(dialog);
                            onReservationCreated(created.get(0));
                        });
                    }

                    @Override
                    public void onError(String message) {
                        dismissSafely(dialog);
                        setSubmitting(false);
                        showSubmitFailedDialog(message, () -> {
                            setSubmitting(true);
                            createReservationsThenShowSuccess();
                        });
                    }
                });
    }

    @SuppressLint("InflateParams") // a dialog's content view has no parent to inflate into
    private View buildLoadingView() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null);
        TextView tvMsg = dialogView.findViewById(R.id.loadingMessage);
        if (tvMsg != null) tvMsg.setText(R.string.submitting_reservation_msg);
        return dialogView;
    }

    /** Uploads the Senior/PWD ID photo (if any) against a just-created reservation before moving on - non-fatal either way. */
    private void uploadIdCardIfNeeded(Booking created, Runnable onDone) {
        BookingWizardState state = getState();
        if ("None".equals(state.idCardType) || state.idCardImageUri == null) {
            onDone.run();
            return;
        }
        repository.uploadIdCard(created.getId(), state.idCardImageUri, new RoomRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                onDone.run();
            }

            @Override
            public void onError(String message) {
                onDone.run();
            }
        });
    }

    /**
     * Nothing was created at all - most commonly the backend's own atomic
     * inventory check losing a race against another guest for the last
     * room of a selected type (the courtesy recheck right before this call
     * is best-effort, not authoritative - see
     * recheckAvailabilityThenProceed()'s docblock), or one of the other
     * selected room types no longer being offered. The whole submission is
     * one atomic request now (see RoomRepository#createReservation(List)),
     * so there's no more "partial failure across several room types" case
     * to handle - it either produces exactly one Reservation, or none at
     * all. Send the guest back to Room Selection to adjust their picks,
     * same as that courtesy recheck already does when IT catches the
     * shortfall.
     */
    private void onReservationCreationFailed(@Nullable String errorMessage) {
        if (!isAdded()) return;
        setSubmitting(false);
        showSubmitFailedDialog(errorMessage, () -> {
            setSubmitting(true);
            createReservationsThenShowSuccess();
        });
    }

    /**
     * A failed submit keeps the guest on the review step with everything they entered. They can retry (a lost
     * connection is the usual cause), or go change the rooms/dates when the server said they are no longer free.
     */
    private void showSubmitFailedDialog(@Nullable String serverMessage, Runnable retry) {
        if (!isAdded()) return;
        String message = serverMessage != null && !serverMessage.trim().isEmpty() ? serverMessage : getString(R.string.error_submit_generic);
        new SafeDialogs.Material(requireContext())
                .setTitle(R.string.submit_failed_title)
                .setMessage(message)
                .setPositiveButton(R.string.retry_label, (d, w) -> retry.run())
                .setNeutralButton(R.string.submit_failed_change, (d, w) -> getWizardActivity().goToStep(1))
                .setNegativeButton(R.string.close_label, null)
                .show();
    }

    private void onReservationCreated(Booking created) {
        if (!isAdded()) return;
        setSubmitting(false);

        boolean gcash = "gcash".equalsIgnoreCase(getState().paymentMethod);
        String newReservationId = created.getId();
        new SafeDialogs.Material(requireContext())
                .setTitle(gcash ? R.string.reservation_created_gcash_title : R.string.reservation_created_cash_title)
                .setMessage(gcash ? R.string.reservation_created_gcash_msg : R.string.reservation_created_cash_msg)
                .setPositiveButton(R.string.confirmed_button, (d, which) -> {
                    // Same "show the ID, large and prominent, before redirecting"
                    // step PaymentActivity's own creation paths already go through
                    // (see TransactionCreatedDialogHelper's own doc) - a fresh
                    // Reservation never passes through PaymentActivity at all, so
                    // it needs this same acknowledgement step here instead.
                    if (!isAdded()) return;
                    TransactionCreatedDialogHelper.show(requireContext(), false, newReservationId, created,
                            () -> navigateToReservationsListHighlighting(newReservationId));
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Lands the guest on Reservations - All Reservation with the transaction
     * they just created highlighted/scrolled-to, same EXTRA_OPEN_SECTION/
     * EXTRA_HIGHLIGHT_ID pattern PaymentActivity#navigateToReservationSection()
     * already uses after a Booking-mode GCash success - a fresh Reservation
     * never passes through PaymentActivity at all, so it needs its own call
     * site for the same destination instead of the generic Dashboard this
     * used to send the guest to.
     */
    private void navigateToReservationsListHighlighting(String reservationId) {
        Activity activity = getActivity();
        if (activity == null) return;
        Intent intent = new Intent(activity, BookingAndReservationActivity.class);
        intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, BookingAndReservationActivity.SECTION_RESERVATION);
        intent.putExtra(BookingAndReservationActivity.EXTRA_HIGHLIGHT_ID, reservationId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    /**
     * Save path for the one-time Modify flow: a single update() call for
     * dates/guests/rooms (every selected room type/quantity)/amenities/ID,
     * then - only if the guest actually changed payment method on this step -
     * a follow-up call to the already-tested switch-to-gcash/switch-to-cash
     * one-time-lock endpoints (reusing that logic rather than duplicating it
     * inside update()). Rooms and amenities are always sent as a full
     * replacement of the reservation's current selection (see
     * Api\ReservationController::update()'s rooms[]/amenities[] handling) -
     * whatever the guest staged across Steps 1 and 3 during this one-time
     * edit is exactly what gets saved.
     */
    private void saveEditedReservation() {
        BookingWizardState state = getState();
        String reservationId = getWizardActivity().getEditingReservationId();
        String originalMethod = getWizardActivity().getOriginalPaymentMethod();

        // One entry per DISTINCT room type the guest selected - same shape
        // createReservation(List<List<Room>>, ...) sends for a brand new reservation.
        List<List<Room>> roomGroups = new ArrayList<>(state.selectedRoomsGroupedByType().values());

        repository.updateReservationFull(reservationId, roomGroups, state.checkIn, state.checkOut,
                state.adults, state.children, state.idCardType, state.discountIdOrNull(), state.removeIdCard,
                state.additionalGuests, state.selectedAmenities,
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking saved) {
                        lastEditSummary = saved.getEditSummary();
                        // The stored ID is only replaced now that the edit itself has saved.
                        uploadReplacementIdThen(reservationId, () -> switchPaymentMethodThen(saved, reservationId, originalMethod));
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        setSubmitting(false);
                        showSubmitFailedDialog(message, () -> {
                            setSubmitting(true);
                            saveEditedReservation();
                        });
                    }
                });
    }

    /** A newly picked ID replaces the stored one only after the edit saved; a failed upload never undoes the saved edit. */
    private void uploadReplacementIdThen(String reservationId, Runnable next) {
        BookingWizardState state = getState();
        if (state.discount == null || state.idCardImageUri == null) {
            next.run();
            return;
        }
        repository.uploadIdCard(reservationId, state.idCardImageUri, new RoomRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                next.run();
            }

            @Override
            public void onError(String message) {
                editIdUploadFailed = true;
                next.run();
            }
        });
    }

    /**
     * Only if the guest changed payment method on this step, a follow-up call to the already-tested
     * switch-to-gcash / switch-to-cash one-time-lock endpoints (reusing that logic rather than
     * duplicating it inside update()). If the switch fails the edit itself is still reported as saved.
     */
    private void switchPaymentMethodThen(Booking saved, String reservationId, String originalMethod) {
        BookingWizardState state = getState();
        boolean wantsGcash = "gcash".equalsIgnoreCase(state.paymentMethod);
        boolean wasGcash = "GCASH".equalsIgnoreCase(originalMethod);
        if (wantsGcash == wasGcash) {
            onEditSaved(false);
            return;
        }
        RoomRepository.RepositoryCallback<Booking> after = new RoomRepository.RepositoryCallback<Booking>() {
            @Override
            public void onSuccess(Booking switched) {
                onEditSaved(wantsGcash);
            }

            @Override
            public void onError(String message) {
                onEditSaved(false);
            }
        };
        if (wantsGcash) {
            repository.switchReservationToGcash(reservationId, after);
        } else {
            repository.switchReservationToCash(reservationId, after);
        }
    }

    /** The edit is saved: show the server's old/new/paid/balance summary, then leave (to Pay Now if the guest just switched to GCash). */
    private void onEditSaved(boolean offerPayNow) {
        if (!isAdded()) return;
        setSubmitting(false);
        String reservationId = getWizardActivity().getEditingReservationId();
        LocalTransactionState.markModifiedOnce(requireContext(), reservationId);

        StringBuilder message = new StringBuilder(getString(R.string.reservation_updated_msg, reservationId));
        Booking.EditSummary summary = lastEditSummary;
        if (summary != null) {
            message.append("\n\n")
                    .append(getString(R.string.edit_old_total)).append(": ").append(money(summary.oldTotal)).append('\n')
                    .append(getString(R.string.edit_new_total)).append(": ").append(money(summary.newTotal)).append('\n')
                    .append(getString(R.string.edit_amount_paid)).append(": ").append(money(summary.amountPaid)).append('\n');
            if (summary.excess > 0) {
                message.append(getString(R.string.edit_excess_paid)).append(": ").append(money(summary.excess));
            } else {
                message.append(getString(R.string.edit_balance_due)).append(": ")
                        .append(summary.balanceDue > 0 ? money(summary.balanceDue) : getString(R.string.edit_settled));
            }
        }
        if (editIdUploadFailed) {
            message.append("\n\n").append(getString(R.string.edit_id_upload_failed));
        }

        Activity activity = requireActivity();
        new SafeDialogs.Material(requireContext())
                .setTitle(R.string.edit_saved_title)
                .setMessage(message.toString())
                .setCancelable(false)
                .setPositiveButton(R.string.confirmed_button, (d, which) -> {
                    if (offerPayNow) {
                        Intent intent = new Intent(activity, PaymentActivity.class);
                        intent.putExtra("BOOKING_ID", reservationId);
                        intent.putExtra("ALLOW_CASH", true);
                        activity.startActivity(intent);
                    }
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                })
                .show();
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        updateConfirmButtonEnabled();
    }
}
