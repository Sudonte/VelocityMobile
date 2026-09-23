package com.example.velocitysuites;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only transaction history for one lifecycle bucket, chosen via
 * EXTRA_LIST_TYPE: Cancelled/Completed Bookings, Cancelled/Completed
 * Reservations, or Upcoming Bookings/Reservations (any future check-in,
 * per TransactionCategorizer - recomputed fresh on every load, so the set
 * rolls forward automatically each day). One shared screen instead of
 * several near-identical ones - reuses the same TransactionAdapter/
 * item_transaction_card/detail dialog that TransactionHistoryActivity
 * already has.
 */
public class TransactionListActivity extends BaseNavigationActivity {

    public static final String EXTRA_LIST_TYPE = "EXTRA_LIST_TYPE";
    public static final String TYPE_CANCELLED_BOOKINGS = "CANCELLED_BOOKINGS";
    public static final String TYPE_COMPLETED_BOOKINGS = "COMPLETED_BOOKINGS";
    public static final String TYPE_CANCELLED_RESERVATIONS = "CANCELLED_RESERVATIONS";
    public static final String TYPE_COMPLETED_RESERVATIONS = "COMPLETED_RESERVATIONS";
    public static final String TYPE_UPCOMING_RESERVATIONS = "UPCOMING_RESERVATIONS";
    public static final String TYPE_UPCOMING_BOOKINGS = "UPCOMING_BOOKINGS";

    private RecyclerView rvTransactions;
    private View layoutEmptyState;
    private SwipeRefreshLayout swipeRefresh;
    private RoomRepository repository;
    private String listType;
    private TransactionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_list);
        setupGuestNavigation(R.id.nav_booking_reservation);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);
        listType = getIntent().getStringExtra(EXTRA_LIST_TYPE);
        if (listType == null) listType = TYPE_CANCELLED_BOOKINGS;

        rvTransactions = findViewById(R.id.rvTransactions);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
            swipeRefresh.setOnRefreshListener(this::loadTransactions);
        }

        applyHeaderText();

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        // Delete is only offered on the three terminal, guest-facing-deletable
        // buckets (Cancelled/Completed Booking, Cancelled Reservation) - never
        // on Completed Reservation (still an active, in-progress reservation
        // from the guest's perspective - see TransactionCategorizer's own doc
        // on that bucket) or either Upcoming list. TransactionAdapter already
        // hides its own delete button whenever this callback is null.
        boolean deletionAllowed = TYPE_CANCELLED_BOOKINGS.equals(listType)
                || TYPE_COMPLETED_BOOKINGS.equals(listType)
                || TYPE_CANCELLED_RESERVATIONS.equals(listType);
        adapter = new TransactionAdapter(new ArrayList<>(), this::showDetailsDialog, deletionAllowed ? this::confirmDelete : null);
        rvTransactions.setAdapter(adapter);

        loadTransactions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTransactions();
    }

    private void applyHeaderText() {
        int titleRes;
        int descRes;
        int emptyRes;
        int emptyTitleRes;
        switch (listType) {
            case TYPE_COMPLETED_BOOKINGS:
                titleRes = R.string.completed_bookings_label;
                descRes = R.string.desc_completed_bookings;
                emptyTitleRes = R.string.empty_title_completed_bookings;
                emptyRes = R.string.empty_completed_bookings;
                break;
            case TYPE_CANCELLED_RESERVATIONS:
                titleRes = R.string.cancelled_reservations_label;
                descRes = R.string.desc_cancelled_reservations;
                emptyTitleRes = R.string.empty_title_cancelled_reservations;
                emptyRes = R.string.empty_cancelled_reservations;
                break;
            case TYPE_COMPLETED_RESERVATIONS:
                titleRes = R.string.completed_reservations_label;
                descRes = R.string.desc_completed_reservations;
                emptyTitleRes = R.string.empty_title_completed_reservations;
                emptyRes = R.string.empty_completed_reservations;
                break;
            case TYPE_UPCOMING_RESERVATIONS:
                titleRes = R.string.upcoming_reservations_label;
                descRes = R.string.desc_upcoming_reservations;
                emptyTitleRes = R.string.empty_title_upcoming_reservations;
                emptyRes = R.string.empty_upcoming_reservations;
                break;
            case TYPE_UPCOMING_BOOKINGS:
                titleRes = R.string.upcoming_bookings_label;
                descRes = R.string.desc_upcoming_bookings;
                emptyTitleRes = R.string.empty_title_upcoming_bookings;
                emptyRes = R.string.empty_upcoming_bookings;
                break;
            case TYPE_CANCELLED_BOOKINGS:
            default:
                titleRes = R.string.cancelled_bookings_label;
                descRes = R.string.desc_cancelled_bookings;
                emptyTitleRes = R.string.empty_title_cancelled_bookings;
                emptyRes = R.string.empty_cancelled_bookings;
                break;
        }

        TextView screenTitle = findViewById(R.id.screenTitle);
        TextView screenDescription = findViewById(R.id.screenDescription);
        TextView emptyTitle = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.emptyTitle) : null;
        TextView emptyDesc = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.emptyDesc) : null;

        if (screenTitle != null) screenTitle.setText(titleRes);
        if (screenDescription != null) screenDescription.setText(descRes);
        if (emptyTitle != null) emptyTitle.setText(emptyTitleRes);
        if (emptyDesc != null) emptyDesc.setText(emptyRes);
    }

    private void loadTransactions() {
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                applyFilter(result != null ? result : new ArrayList<>());
            }

            @Override
            public void onError(String message) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(TransactionListActivity.this, "Couldn't load records: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Booking vs Reservation mirrors isHasBooking() everywhere else in the
     * app. Lifecycle bucket is now delegated to TransactionCategorizer - the
     * same single source of truth BookingAndReservationActivity and
     * DashboardActivity use - so "Upcoming" here means any future check-in
     * (not just tomorrow), consistent with bookingandreservation.xml's own
     * inline Upcoming filter. Hidden (deleted) transactions never reach
     * `source` in the first place - the server excludes them from
     * GET guest/reservations.
     */
    private void applyFilter(List<Booking> source) {
        List<Booking> filtered = new ArrayList<>();

        if (TYPE_UPCOMING_RESERVATIONS.equals(listType) || TYPE_UPCOMING_BOOKINGS.equals(listType)) {
            boolean wantBooking = TYPE_UPCOMING_BOOKINGS.equals(listType);
            for (Booking b : source) {
                if (b.isHasBooking() != wantBooking) continue;
                if (TransactionCategorizer.categorize(b) == TransactionCategorizer.Category.UPCOMING) filtered.add(b);
            }
        } else {
            boolean wantBooking = TYPE_CANCELLED_BOOKINGS.equals(listType) || TYPE_COMPLETED_BOOKINGS.equals(listType);
            TransactionCategorizer.Category wantCategory =
                    (TYPE_COMPLETED_BOOKINGS.equals(listType) || TYPE_COMPLETED_RESERVATIONS.equals(listType))
                            ? TransactionCategorizer.Category.COMPLETED
                            : TransactionCategorizer.Category.CANCELLED;

            for (Booking b : source) {
                if (b.isHasBooking() != wantBooking) continue;
                if (TransactionCategorizer.categorize(b) == wantCategory) filtered.add(b);
            }
        }

        if (filtered.isEmpty()) {
            rvTransactions.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvTransactions.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            adapter.updateList(filtered);
        }
    }

    /**
     * Real, permanent, non-recoverable deletion, same as
     * bookingandreservation.xml's Delete Permanently button - the row and
     * its owned child records are actually removed from the hotel's
     * database, not just hidden from this guest's own view. See
     * BookingAndReservationActivity#confirmDeleteTransaction() for the
     * identical implementation and TRANSACTION_PERMANENT_DELETE_BACKEND_SPEC.md
     * for the server-side contract.
     */
    private void confirmDelete(Booking booking) {
        int messageRes = booking.isHasBooking() ? R.string.delete_booking_transaction_confirm : R.string.delete_reservation_transaction_confirm;
        int successMsgRes = booking.isHasBooking() ? R.string.delete_booking_transaction_success : R.string.delete_reservation_transaction_success;

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_transaction_permanently_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.delete_permanently_button_label, null)
                .setNegativeButton(R.string.cancel_label, null)
                .create();

        dialog.setOnShowListener(shownDialog -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            positive.setOnClickListener(v -> {
                positive.setEnabled(false);
                negative.setEnabled(false);
                positive.setText(R.string.deleting_in_progress_label);

                RoomRepository.RepositoryCallback<Void> deleteCallback = new RoomRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        dismissSafely(dialog);
                        if (isFinishing() || isDestroyed()) return;
                        new MaterialAlertDialogBuilder(TransactionListActivity.this)
                                .setTitle(R.string.delete_transaction_success_title)
                                .setMessage(successMsgRes)
                                .setPositiveButton(R.string.close_label, null)
                                .show();
                        loadTransactions();
                    }

                    @Override
                    public void onError(String message) {
                        positive.setEnabled(true);
                        negative.setEnabled(true);
                        positive.setText(R.string.delete_permanently_button_label);
                        Toast.makeText(TransactionListActivity.this,
                                getString(R.string.delete_transaction_failed_format, message), Toast.LENGTH_LONG).show();
                    }
                };

                // See BookingAndReservationActivity#confirmDeleteTransaction()'s
                // identical branch and Booking#getBookingEndpointDeleteId()'s doc -
                // only a genuinely direct Booking goes through the bookings
                // endpoint; every reservation-derived transaction (this screen's
                // Completed/Cancelled Bookings lists included) goes through the
                // reservations endpoint, which handles the nested Booking's own
                // status server-side.
                String bookingEndpointId = booking.getBookingEndpointDeleteId();
                if (bookingEndpointId != null) {
                    repository.deleteBookingPermanently(bookingEndpointId, deleteCallback);
                } else {
                    repository.deleteReservationPermanently(booking.getId(), deleteCallback);
                }
            });
        });
        dialog.show();
    }

    /** Read-only detail view - no Modify/Pay affordances, matching the "read-only format" requirement. */
    private void showDetailsDialog(Booking booking) {
        View detailView = getLayoutInflater().inflate(R.layout.dialog_transaction_details, null);

        TextView tvId = detailView.findViewById(R.id.detailBookingId);
        TextView tvRoom = detailView.findViewById(R.id.detailRoomName);
        TextView tvStatus = detailView.findViewById(R.id.detailStatus);
        TextView tvCheckIn = detailView.findViewById(R.id.detailCheckIn);
        TextView tvCheckOut = detailView.findViewById(R.id.detailCheckOut);
        TextView tvPayRef = detailView.findViewById(R.id.detailPaymentRef);
        TextView tvPayDate = detailView.findViewById(R.id.detailPaymentDate);
        TextView tvAmount = detailView.findViewById(R.id.detailAmount);
        View cardCancel = detailView.findViewById(R.id.cardCancellation);
        TextView tvCancelDate = detailView.findViewById(R.id.detailCancelDate);
        TextView tvCancelReason = detailView.findViewById(R.id.detailCancelReason);
        View btnDownload = detailView.findViewById(R.id.btnDownloadReceiptDetail);
        if (btnDownload != null) btnDownload.setVisibility(View.GONE);

        // Resolved once, up front, so the room text and the room-image cards
        // below both read the exact same sibling group - see
        // BookingGroupState#resolveGroupMembers()'s own doc for why this is
        // the single shared lookup every screen should use instead of each
        // re-resolving (or, as this dialog used to, never resolving at all).
        List<Booking> groupMembers = BookingGroupState.resolveGroupMembers(this, repository, booking.getId());

        LinearLayout layoutSelectedRooms = detailView.findViewById(R.id.layoutSelectedRoomsContainer);
        if (layoutSelectedRooms != null) {
            populateSelectedRoomCards(layoutSelectedRooms, booking, groupMembers);
        }

        boolean noShow = booking.isNoShow();
        String statusLabel = noShow ? getString(R.string.status_no_show) : booking.getStatus();

        tvId.setText(getString(R.string.booking_id_format, booking.getId()));
        // Same three-tier itemized/grouped-siblings/legacy-single summary
        // BookingDetailsActivity's header uses (Booking#buildRoomSelectionSummaryText())
        // instead of getAllRoomTypeNames(), which never considered grouped
        // siblings and so only ever showed this one record's own room type
        // for a multi-room-type transaction.
        tvRoom.setText(getString(R.string.booking_item_format, booking.getRoomName(),
                Booking.buildRoomSelectionSummaryText(booking, groupMembers)));
        tvStatus.setText(getString(R.string.label_current_status, statusLabel));

        String cin = booking.getActualCheckIn() != null ? booking.getActualCheckIn() : booking.getCheckInDate();
        String cout = booking.getActualCheckOut() != null ? booking.getActualCheckOut() : booking.getCheckOutDate();
        tvCheckIn.setText(cin);
        tvCheckOut.setText(cout);

        String payRef;
        if (!booking.isHasBooking()) {
            payRef = getString(R.string.no_payment_yet_label);
        } else if (booking.getTransactionRef() != null) {
            payRef = booking.getTransactionRef();
        } else {
            payRef = getString(R.string.label_pending_payment);
        }
        tvPayRef.setText(getString(R.string.transaction_ref_label, payRef));

        String payDate = booking.getPaymentDate() != null ? booking.getPaymentDate() : getString(R.string.label_not_available);
        tvPayDate.setText(getString(R.string.label_paid_on, payDate));

        // Must match the room-type summary above, which already reflects every
        // sibling in groupMembers (see BookingGroupAggregator's own doc) - showing
        // this one record's own total here would understate a multi-room-type
        // transaction's true Total Amount versus what BookingDetailsActivity/
        // PaymentReceiptActivity display for the identical transaction.
        double detailTotalAmount = groupMembers != null
                ? BookingGroupAggregator.sum(groupMembers).totalAmount
                : booking.getTotalAmount();
        tvAmount.setText(getString(R.string.details_total_amount_label, detailTotalAmount));

        boolean isCancelled = "Cancelled".equalsIgnoreCase(booking.getStatus()) || "Rejected".equalsIgnoreCase(booking.getStatus());
        if (isCancelled) {
            cardCancel.setVisibility(View.VISIBLE);
            String cancelDate = booking.getCancellationDate() != null ? booking.getCancellationDate() : getString(R.string.label_not_available);
            String reason = noShow
                    ? booking.getNoShowReason()
                    : (booking.getCancellationReason() != null ? booking.getCancellationReason() : getString(R.string.label_customer_request));
            tvCancelDate.setText(getString(R.string.label_cancelled_on, cancelDate));
            tvCancelReason.setText(getString(R.string.label_reason, reason));
        } else {
            cardCancel.setVisibility(View.GONE);
        }

        new MaterialAlertDialogBuilder(this)
                .setView(detailView)
                .setPositiveButton(R.string.close_label, null)
                .show();
    }

    /**
     * One item_selected_room_type_card per distinct room type - same
     * three-tier priority and image resolution as BookingDetailsActivity's
     * Selected Rooms section (buildRoomInfoSection()): itemized
     * Booking#getRooms() line items first (real per-type assigned room
     * numbers, confirmed live 2026-09-18 - see BookingRoomDto#
     * assigned_room_numbers's own doc), then a legacy grouped-sibling
     * transaction (BookingGroupState#resolveGroupMembers(), passed in from
     * showDetailsDialog() so the room text above and these cards can never
     * resolve a different sibling set for the same tap), then the single
     * legacy room type. Each tier's own source (itemized line item, sibling
     * record, or the transaction's own single room type) is already
     * guaranteed distinct-per-type by construction (see each branch's
     * upstream doc), so no additional de-duplication is needed here.
     */
    private void populateSelectedRoomCards(LinearLayout container, Booking booking, @Nullable List<Booking> groupMembers) {
        container.removeAllViews();
        if (!booking.getRooms().isEmpty()) {
            for (BookingRoom r : booking.getRooms()) {
                String imageUrl = repository.findRoomTypeImageUrl(r.getRoomTypeId());
                String assignedRoomsText = Booking.resolveAssignedRoomsText(this, r.getAssignedRoomNumbers());
                addSelectedRoomCard(container, r.getRoomTypeName(), r.getQuantity(), imageUrl, assignedRoomsText);
            }
        } else if (groupMembers != null && !groupMembers.isEmpty()) {
            // Each sibling is its own full Booking record, so its own
            // getRoomImageUrl()/getRoomNumber() are already correctly scoped
            // to that specific room type - no catalog lookup needed here,
            // unlike the itemized branch above (matches BookingDetailsActivity's
            // identical groupMembers branch exactly).
            for (Booking member : groupMembers) {
                String assignedRoomsText = Booking.resolveAssignedRoomsText(this, member.getRoomNumber());
                addSelectedRoomCard(container, member.getRoomType(), Math.max(1, member.getRoomsRequested()), member.getRoomImageUrl(), assignedRoomsText);
            }
        } else if (booking.getRoomType() != null && !booking.getRoomType().isEmpty()) {
            String assignedRoomsText = Booking.resolveAssignedRoomsText(this, booking.getRoomNumber());
            addSelectedRoomCard(container, booking.getRoomType(), Math.max(1, booking.getRoomsRequested()), booking.getRoomImageUrl(), assignedRoomsText);
        }
    }

    private void addSelectedRoomCard(LinearLayout container, String roomTypeName, int quantity, String imageUrl, String assignedRoomsText) {
        View card = getLayoutInflater().inflate(R.layout.item_selected_room_type_card, container, false);
        ImageView ivImage = card.findViewById(R.id.ivSelectedRoomTypeImage);
        int fallback = RoomVisuals.getRoomImage(roomTypeName);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).placeholder(fallback).error(fallback).into(ivImage);
        } else {
            ivImage.setImageResource(fallback);
        }
        ((TextView) card.findViewById(R.id.tvSelectedRoomTypeName)).setText(roomTypeName);
        ((TextView) card.findViewById(R.id.tvSelectedRoomTypeQuantity))
                .setText(getResources().getQuantityString(R.plurals.rooms_selected_count, Math.max(1, quantity), Math.max(1, quantity)));
        ((TextView) card.findViewById(R.id.tvSelectedRoomTypeAssigned)).setText(assignedRoomsText);
        container.addView(card);
    }
}
