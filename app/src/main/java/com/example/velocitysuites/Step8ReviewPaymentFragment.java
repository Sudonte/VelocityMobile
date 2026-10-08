package com.example.velocitysuites;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    private TextView tvSummaryDates;
    private TextView tvSummaryGuests;
    private TextView tvSummaryAdultsChildren;
    private TextView tvSummaryRepresentative;
    private LinearLayout layoutSummaryRooms;
    private LinearLayout layoutSummaryAmenities;
    private LinearLayout layoutSummaryAmenitiesRows;
    private TextView tvSummaryAmenitiesSubtotal;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step8_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = RoomRepository.getInstance(requireContext());

        tvSummaryDates = view.findViewById(R.id.tvSummaryDates);
        tvSummaryGuests = view.findViewById(R.id.tvSummaryGuests);
        tvSummaryAdultsChildren = view.findViewById(R.id.tvSummaryAdultsChildren);
        tvSummaryRepresentative = view.findViewById(R.id.tvSummaryRepresentative);
        layoutSummaryRooms = view.findViewById(R.id.layoutSummaryRooms);
        layoutSummaryAmenities = view.findViewById(R.id.layoutSummaryAmenities);
        layoutSummaryAmenitiesRows = view.findViewById(R.id.layoutSummaryAmenitiesRows);
        tvSummaryAmenitiesSubtotal = view.findViewById(R.id.tvSummaryAmenitiesSubtotal);
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

        renderSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Returning here via Back from payment.xml (e.g. the guest backed out
        // before completing GCash) must not leave Confirm permanently
        // disabled - re-evaluate against the still-accepted T&C checkbox.
        setSubmitting(false);
    }

    @Override
    public String stepTitle() {
        BookingWizardState state = getState();
        boolean freshReservation = !getWizardActivity().isEditMode() && !state.isBookingMode();
        return freshReservation ? getString(R.string.wizard_step_title_total_review) : "Bill Summary and Review";
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
        int missing = TermsGate.missingHintRes(state.termsViewed, state.termsAccepted);
        tvConfirmHint.setVisibility(missing == 0 ? View.GONE : View.VISIBLE);
        if (missing != 0) tvConfirmHint.setText(missing);
    }

    private long nights() {
        return getState().nights();
    }

    private double roomsTotal() {
        return getState().roomsTotal();
    }

    private double amenitiesTotal() {
        return getState().amenitiesTotal();
    }

    private void renderSummary() {
        BookingWizardState state = getState();

        if (state.checkIn != null && state.checkOut != null) {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US);
            int nights = (int) nights();
            tvSummaryDates.setText(getString(R.string.summary_dates_nights_format,
                    fmt.format(state.checkIn.getTime()), fmt.format(state.checkOut.getTime()),
                    getResources().getQuantityString(R.plurals.nights_count_plain, nights, nights)));
        } else {
            tvSummaryDates.setText(R.string.summary_dates_placeholder);
        }

        int guestCount = state.adults + state.children;
        tvSummaryGuests.setText(getResources().getQuantityString(R.plurals.summary_guest_count, guestCount, guestCount));
        tvSummaryAdultsChildren.setText(getString(R.string.summary_adults_children_format,
                getResources().getQuantityString(R.plurals.adults_count, state.adults, state.adults),
                getResources().getQuantityString(R.plurals.children_count, state.children, state.children)));

        String representative = buildRepresentativeName(state);
        if (representative != null) {
            tvSummaryRepresentative.setVisibility(View.VISIBLE);
            tvSummaryRepresentative.setText(getString(R.string.details_label_representative_name) + ": " + representative);
        } else {
            tvSummaryRepresentative.setVisibility(View.GONE);
        }

        layoutSummaryRooms.removeAllViews();
        for (List<Room> group : state.selectedRoomsGroupedByType().values()) {
            Room representativeRoom = group.get(0);
            int qty = group.size();
            double subtotal = representativeRoom.getPricePerNight() * nights() * qty;
            layoutSummaryRooms.addView(buildRow(
                    getString(R.string.summary_room_row_qty_format, qty, representativeRoom.getName(), (int) nights()),
                    String.format(Locale.US, getString(R.string.price_format), subtotal)));
        }

        layoutSummaryAmenitiesRows.removeAllViews();
        if (state.selectedAmenities.isEmpty()) {
            layoutSummaryAmenities.setVisibility(View.GONE);
        } else {
            layoutSummaryAmenities.setVisibility(View.VISIBLE);
            for (AddOnAmenity a : state.selectedAmenities) {
                layoutSummaryAmenitiesRows.addView(buildRow(
                        getString(R.string.summary_amenity_row_qty_format, a.getName(), a.getQuantity()),
                        String.format(Locale.US, getString(R.string.price_format), a.getSubtotal())));
            }
            tvSummaryAmenitiesSubtotal.setText(String.format(Locale.US, getString(R.string.price_format), amenitiesTotal()));
        }

        tvSummaryTotal.setText(String.format(Locale.US, getString(R.string.price_format), roomsTotal() + amenitiesTotal()));
        renderDiscountEstimate(state);
        renderEditTotals();
    }

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

    @Nullable
    private String buildRepresentativeName(BookingWizardState state) {
        if (state.guestFirstName == null || state.guestFirstName.trim().isEmpty()) return null;
        StringBuilder sb = new StringBuilder(state.guestFirstName.trim());
        if (state.guestMiddleName != null && !state.guestMiddleName.trim().isEmpty()) {
            sb.append(" ").append(state.guestMiddleName.trim());
        }
        if (state.guestLastName != null && !state.guestLastName.trim().isEmpty()) {
            sb.append(" ").append(state.guestLastName.trim());
        }
        return sb.toString();
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
        if (submitting) return;
        BookingWizardState state = getState();

        if (!state.termsViewed || !state.termsAccepted) {
            Toast.makeText(requireContext(), R.string.wizard_step_incomplete, Toast.LENGTH_SHORT).show();
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

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setMessage(confirmMessageRes)
                .setPositiveButton(isEditMode ? R.string.confirm_update_reservation_positive : R.string.confirm_dialog_positive,
                        (dialog, which) -> {
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
        if (state.selectedRooms.isEmpty() || state.checkIn == null || state.checkOut == null) {
            proceedAfterConfirm();
            return;
        }

        repository.refreshRooms(state.checkIn, state.checkOut, new RoomRepository.RepositoryCallback<List<Room>>() {
            @Override
            public void onSuccess(List<Room> freshRooms) {
                if (!isAdded()) return;

                java.util.Map<String, Integer> freshAvailableById = new java.util.HashMap<>();
                for (Room r : freshRooms) {
                    freshAvailableById.put(r.getId(), r.getAvailableCount());
                }

                StringBuilder issues = new StringBuilder();
                for (java.util.Map.Entry<String, List<Room>> entry : state.selectedRoomsGroupedByType().entrySet()) {
                    List<Room> group = entry.getValue();
                    Room representative = group.get(0);
                    Integer fresh = freshAvailableById.get(entry.getKey());
                    int available = fresh != null ? fresh : 0;

                    if (available < group.size()) {
                        if (issues.length() > 0) issues.append("\n");
                        if (available <= 0) {
                            issues.append(getString(R.string.error_room_no_longer_available_format, representative.getName()));
                            state.selectedRooms.removeAll(group);
                        } else {
                            issues.append(getString(R.string.error_room_availability_decreased_format, available, representative.getName()));
                            for (int i = group.size() - 1; i >= available; i--) {
                                state.selectedRooms.remove(group.get(i));
                            }
                        }
                    }
                }

                if (issues.length() > 0) {
                    Toast.makeText(requireContext(), issues.toString(), Toast.LENGTH_LONG).show();
                    // Aborting without ever reaching proceedAfterConfirm() - undo the guard
                    // armed in onConfirmClicked() so btnConfirm is usable again once the
                    // guest comes back through Step 8 after adjusting their room selection.
                    setSubmitting(false);
                    getWizardActivity().goToStep(2);
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
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(buildLoadingView())
                .setCancelable(false)
                .create();
        dialog.show();

        // One call covering every selected room type - see
        // RoomRepository#createReservation(List<List<Room>>, ...)'s own
        // doc. Replaces the old one-request-per-room-type loop
        // (submitReservationGroups()), which produced one separate
        // Reservation per distinct room type instead of a single combined
        // transaction.
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
        Toast.makeText(requireContext(), errorMessage != null ? errorMessage : getString(R.string.error_room_unavailable), Toast.LENGTH_LONG).show();
        getWizardActivity().goToStep(2);
    }

    private void onReservationCreated(Booking created) {
        if (!isAdded()) return;
        setSubmitting(false);

        boolean gcash = "gcash".equalsIgnoreCase(getState().paymentMethod);
        String newReservationId = created.getId();
        new MaterialAlertDialogBuilder(requireContext())
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
                        Toast.makeText(requireContext(), message != null ? message : getString(R.string.error_room_unavailable), Toast.LENGTH_LONG).show();
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
        new MaterialAlertDialogBuilder(requireContext())
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
