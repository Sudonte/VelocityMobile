package com.example.velocitysuites;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

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
 *   <li>Reservation mode, fresh (not editing): payment method was already
 *   chosen on the preceding Step7PaymentMethodFragment and is shown here
 *   read-only. Confirm calls RoomRepository#createReservation() directly -
 *   for BOTH Cash and GCash - with no payment attached either way (GCash
 *   always defers to a later Pay Now action; Cash always means full payment
 *   walk-in at the hotel). The 20/30/40/50%/Full payment-amount choice is
 *   deliberately NOT collected here - it only exists later, inside
 *   payment.xml's Review Billing, for a GCash reservation's own Pay Now
 *   action. payment.xml is never opened during reservation creation
 *   itself.</li>
 * </ul>
 * A guest may have staged more than one distinct room TYPE in step 2 (e.g.
 * 2 Deluxe + 1 Suite); createReservation() only accepts one room_type_id per
 * call, so the fresh-Reservation path below submits one sequential call per
 * room-type group, attaching the selected amenities to only the first
 * group's call (never double-billed) - same convention as PaymentActivity's
 * equivalent grouped-submission methods.
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
    private View cardPaymentNextNotice;
    private View cardPaymentMethodSummary;
    private TextView tvSummaryPaymentMethod;
    private TextView tvSummaryPaymentMethodNote;
    private View layoutEditPaymentMethod;
    private com.google.android.material.chip.ChipGroup cgEditPaymentMethod;

    private MaterialButton btnViewHotelTerms;
    private MaterialCheckBox cbTerms;
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
        cardPaymentNextNotice = view.findViewById(R.id.cardPaymentNextNotice);
        cardPaymentMethodSummary = view.findViewById(R.id.cardPaymentMethodSummary);
        tvSummaryPaymentMethod = view.findViewById(R.id.tvSummaryPaymentMethod);
        tvSummaryPaymentMethodNote = view.findViewById(R.id.tvSummaryPaymentMethodNote);
        layoutEditPaymentMethod = view.findViewById(R.id.layoutEditPaymentMethod);
        cgEditPaymentMethod = view.findViewById(R.id.cgEditPaymentMethod);

        btnViewHotelTerms = view.findViewById(R.id.btnViewHotelTerms);
        cbTerms = view.findViewById(R.id.cbTermsAgreement);
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
        btnViewHotelTerms.setOnClickListener(v -> showHotelTermsDialog());
        cbTerms.setEnabled(state.termsViewed);
        cbTerms.setChecked(state.termsAccepted);
        cbTerms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            state.termsAccepted = isChecked;
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
        btnConfirm.setEnabled(state.termsViewed && state.termsAccepted && !submitting);
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
            tvSummaryDates.setText(getString(R.string.summary_dates_format, fmt.format(state.checkIn.getTime()), fmt.format(state.checkOut.getTime())));
        } else {
            tvSummaryDates.setText(R.string.summary_dates_placeholder);
        }

        int guestCount = state.adults + state.children;
        tvSummaryGuests.setText(getResources().getQuantityString(R.plurals.summary_guest_count, guestCount, guestCount));
        tvSummaryAdultsChildren.setText(getString(R.string.summary_adults_format, state.adults)
                + "   " + getString(R.string.summary_children_format, state.children));

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
        int confirmMessageRes = isEditMode
                ? R.string.confirm_update_reservation_msg
                : (state.isBookingMode() ? R.string.confirm_create_booking_msg : R.string.confirm_create_reservation_msg);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setMessage(confirmMessageRes)
                .setPositiveButton(isEditMode ? R.string.confirm_update_reservation_positive : R.string.confirm_dialog_positive,
                        (dialog, which) -> recheckAvailabilityThenProceed())
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

        // Fresh Reservation: create directly here - for both Cash and GCash -
        // no payment call, no payment.xml hand-off. GCash always defers to a
        // later Pay Now action (per explicit product decision); Cash was
        // already payment.xml-free in spirit (walk-in payment), this just
        // removes the brief detour through PaymentActivity's chip screen too.
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
                state.idCardType, state.additionalGuests, state.selectedAmenities, state.paymentMethod,
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
                    TransactionCreatedDialogHelper.show(requireContext(), false, newReservationId,
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
        // createReservation(List<List<Room>>, ...) already sends for a brand
        // new reservation, now reused for the one-time Modify too (see
        // RoomRepository#updateReservationFull(List<List<Room>>, ...) and
        // Api\ReservationController::update()'s rooms[]/amenities[] support).
        List<List<Room>> roomGroups = new ArrayList<>(state.selectedRoomsGroupedByType().values());

        repository.updateReservationFull(reservationId, roomGroups, state.checkIn, state.checkOut,
                state.adults, state.children, state.idCardType, state.additionalGuests,
                state.selectedAmenities,
                new RoomRepository.RepositoryCallback<Booking>() {
                    @Override
                    public void onSuccess(Booking updated) {
                        boolean wantsGcash = "gcash".equalsIgnoreCase(state.paymentMethod);
                        boolean wasGcash = "GCASH".equalsIgnoreCase(originalMethod);
                        if (wantsGcash != wasGcash) {
                            if (wantsGcash) {
                                repository.switchReservationToGcash(reservationId, new RoomRepository.RepositoryCallback<Booking>() {
                                    @Override
                                    public void onSuccess(Booking switched) {
                                        onEditSaved(switched, true);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        // Dates/guests/room already saved successfully - only the
                                        // payment-method switch failed, so still report success on
                                        // the part that worked rather than a confusing full failure.
                                        onEditSaved(updated, false);
                                    }
                                });
                            } else {
                                repository.switchReservationToCash(reservationId, new RoomRepository.RepositoryCallback<Booking>() {
                                    @Override
                                    public void onSuccess(Booking switched) {
                                        onEditSaved(switched, false);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        onEditSaved(updated, false);
                                    }
                                });
                            }
                        } else {
                            onEditSaved(updated, false);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        setSubmitting(false);
                        Toast.makeText(requireContext(), message != null ? message : getString(R.string.error_room_unavailable), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void onEditSaved(Booking updated, boolean offerPayNow) {
        if (!isAdded()) return;
        setSubmitting(false);
        String reservationId = getWizardActivity().getEditingReservationId();
        LocalTransactionState.markModifiedOnce(requireContext(), reservationId);
        Toast.makeText(requireContext(), getString(R.string.reservation_updated_msg, reservationId), Toast.LENGTH_LONG).show();

        Activity activity = getActivity();
        if (activity == null) return;
        if (offerPayNow) {
            Intent intent = new Intent(activity, PaymentActivity.class);
            intent.putExtra("BOOKING_ID", reservationId);
            intent.putExtra("ALLOW_CASH", true);
            activity.startActivity(intent);
        }
        activity.setResult(Activity.RESULT_OK);
        activity.finish();
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        updateConfirmButtonEnabled();
    }

    /**
     * Shows the full Velocity Suites Hotel Terms, Conditions, and Policy as a
     * full-screen scrollable dialog - same dialog_terms_agreement.xml layout
     * and scroll-to-bottom-marks-viewed interaction RegistrationActivity
     * already uses for its own account Terms and Agreement (see
     * RegistrationActivity#showTermsDialog()), just with this step's own
     * title/body substituted in and a payment-method-specific addendum
     * (GCash vs. Cash) appended. The agreement checkbox only becomes
     * enabled once the guest actually scrolls this to the end - opening and
     * immediately dismissing it does not count as having viewed it.
     */
    private void showHotelTermsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_terms_agreement, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        TextView title = dialogView.findViewById(R.id.termsDialogTitle);
        if (title != null) title.setText(R.string.hotel_terms_policy_title);

        TextView bodyText = dialogView.findViewById(R.id.termsBodyText);
        boolean gcash = "gcash".equalsIgnoreCase(getState().paymentMethod);
        bodyText.setText(getString(R.string.hotel_terms_policy_body_general) + "\n\n"
                + getString(gcash ? R.string.hotel_terms_policy_gcash_addendum : R.string.hotel_terms_policy_cash_addendum));

        NestedScrollView scrollView = dialogView.findViewById(R.id.termsScrollView);
        TextView scrollHintText = dialogView.findViewById(R.id.termsScrollHintText);
        ImageView scrollHintIcon = dialogView.findViewById(R.id.termsScrollHintIcon);
        View closeButton = dialogView.findViewById(R.id.termsCloseButton);
        closeButton.setOnClickListener(v -> dialog.dismiss());

        Runnable markViewedIfAtBottom = () -> {
            if (!isAdded()) return;
            View content = scrollView.getChildAt(0);
            if (content == null) return;
            boolean atBottom = scrollView.getScrollY() + scrollView.getHeight() >= content.getHeight() - 8;
            if (atBottom && !getState().termsViewed) {
                getState().termsViewed = true;
                cbTerms.setEnabled(true);
                scrollHintText.setText(R.string.terms_scroll_complete);
                scrollHintIcon.setImageResource(R.drawable.ic_check_circle);
            }
        };

        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> markViewedIfAtBottom.run());
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                markViewedIfAtBottom.run();
                scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
