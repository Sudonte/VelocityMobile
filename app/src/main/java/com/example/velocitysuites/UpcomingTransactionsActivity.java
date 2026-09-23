package com.example.velocitysuites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated "Upcoming Hotel Transactions" screen: every future check-in/check-out
 * across both Bookings and Reservations, in chronological order, filterable by
 * All Transactions / Upcoming Bookings / Upcoming Reservations. Supports a
 * deep-link entry point from the dashboard's Next Hotel Transaction and Upcoming
 * Transactions sections: {@link #EXTRA_SELECTED_BOOKING_ID} scrolls to, highlights,
 * and opens the full detail dialog for one specific item.
 */
public class UpcomingTransactionsActivity extends BaseNavigationActivity {

    /** Optional: scrolls to and highlights this exact Booking ID, from a specific dashboard item tap. */
    public static final String EXTRA_SELECTED_BOOKING_ID = "EXTRA_SELECTED_BOOKING_ID";

    private static class UpcomingEvent {
        final Booking booking;
        final String title;
        final long timeMillis;
        final String dateLabel;
        final boolean isCheckIn;

        UpcomingEvent(Booking booking, String title, long timeMillis, String dateLabel, boolean isCheckIn) {
            this.booking = booking;
            this.title = title;
            this.timeMillis = timeMillis;
            this.dateLabel = dateLabel;
            this.isCheckIn = isCheckIn;
        }
    }

    private LinearLayout listContainer;
    private View emptyState, loadingOverlay;
    private androidx.core.widget.NestedScrollView screenContent;
    private ChipGroup chipGroupFilters;
    private RoomRepository repository;
    private final List<UpcomingEvent> allEvents = new ArrayList<>();
    private String currentFilter = "All";
    private String selectedBookingId;
    private boolean pendingScrollToSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.upcomingtransactions);
        setupGuestNavigation(View.NO_ID);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);

        listContainer = findViewById(R.id.upcomingTransactionsListContainer);
        emptyState = findViewById(R.id.layoutUpcomingEmptyState);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        screenContent = findViewById(R.id.screenContent);
        chipGroupFilters = findViewById(R.id.chipGroupUpcomingFilters);

        selectedBookingId = getIntent().getStringExtra(EXTRA_SELECTED_BOOKING_ID);
        pendingScrollToSelected = selectedBookingId != null;

        setupFilters();

        loadEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }

    private void setupFilters() {
        if (chipGroupFilters == null) return;
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipFilterBookings)) currentFilter = "Bookings";
            else if (checkedIds.contains(R.id.chipFilterReservations)) currentFilter = "Reservations";
            else currentFilter = "All";
            renderEvents();
        });
    }

    private void loadEvents() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                buildEvents(result != null ? result : repository.getBookings());
            }

            @Override
            public void onError(String message) {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                buildEvents(repository.getBookings());
                Toast.makeText(UpcomingTransactionsActivity.this,
                        "Couldn't refresh upcoming transactions: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Every future check-in (not yet checked in) or future check-out (already
     * checked in) across all non-cancelled bookings/reservations, recomputed
     * fresh from "now" on every load so the list rolls forward automatically -
     * no manual refresh or daily maintenance required.
     */
    private void buildEvents(List<Booking> bookings) {
        allEvents.clear();
        SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
        long now = System.currentTimeMillis();

        for (Booking b : bookings) {
            String status = b.getStatus();
            if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) continue;

            try {
                long checkInTime = sdf.parse(b.getCheckInDate()).getTime();
                long checkOutTime = sdf.parse(b.getCheckOutDate()).getTime();

                if (checkInTime >= now - 86400000 && !"Checked-In".equalsIgnoreCase(status) && !"Checked-Out".equalsIgnoreCase(status)) {
                    String kind = getString(b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
                    allEvents.add(new UpcomingEvent(b,
                            getString(R.string.upcoming_event_title_format, kind, b.getRoomName()),
                            checkInTime, b.getCheckInDate(), true));
                }
                if (checkOutTime >= now - 86400000 && "Checked-In".equalsIgnoreCase(status)) {
                    String kind = getString(b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
                    allEvents.add(new UpcomingEvent(b,
                            getString(R.string.upcoming_event_title_format, kind, b.getRoomName()),
                            checkOutTime, b.getCheckOutDate(), false));
                }
            } catch (Exception ignored) {
                // Unparseable dates on a record can't be placed on the timeline - skip it.
            }
        }

        Collections.sort(allEvents, (a, c) -> Long.compare(a.timeMillis, c.timeMillis));
        renderEvents();
    }

    private void renderEvents() {
        listContainer.removeAllViews();
        List<UpcomingEvent> filtered = new ArrayList<>();
        for (UpcomingEvent e : allEvents) {
            boolean matches;
            switch (currentFilter) {
                case "Bookings": matches = e.booking.isHasBooking(); break;
                case "Reservations": matches = !e.booking.isHasBooking(); break;
                default: matches = true;
            }
            if (matches) filtered.add(e);
        }

        if (filtered.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
            View selectedCard = null;
            Booking selectedBooking = null;
            for (int i = 0; i < filtered.size(); i++) {
                UpcomingEvent e = filtered.get(i);
                View card = addEventCard(e, i == 0);
                if (selectedBookingId != null && selectedBookingId.equals(e.booking.getId())) {
                    selectedCard = card;
                    selectedBooking = e.booking;
                }
            }
            scrollToSelectedIfPending(selectedCard, selectedBooking);
        }
    }

    /**
     * Scrolls to, highlights, and opens the full detail dialog for the Booking ID passed via
     * {@link #EXTRA_SELECTED_BOOKING_ID} (e.g. tapping one specific item in the dashboard's
     * Next Hotel Transaction / Upcoming Transactions list) - fires once per launch so it
     * doesn't fight the guest's own scrolling afterwards. The highlight itself is re-applied
     * on every render (filter change, refresh) so it survives those, but the scroll/auto-open
     * only fire once.
     */
    private void scrollToSelectedIfPending(View selectedCard, Booking selectedBooking) {
        if (selectedCard == null) return;
        if (selectedCard instanceof com.google.android.material.card.MaterialCardView) {
            com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) selectedCard;
            cardView.setStrokeColor(getColor(R.color.velocity_red_primary));
            cardView.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
        }
        if (!pendingScrollToSelected || screenContent == null) return;
        pendingScrollToSelected = false;
        final View target = selectedCard;
        screenContent.post(() -> {
            int offset = 0;
            View v = target;
            while (v != null && v != screenContent) {
                offset += v.getTop();
                Object parent = v.getParent();
                if (!(parent instanceof View)) break;
                v = (View) parent;
            }
            screenContent.smoothScrollTo(0, Math.max(0, offset - 24));
        });
        if (selectedBooking != null) {
            showTransactionDetailsDialog(selectedBooking);
        }
    }

    /**
     * Full booking/reservation/payment/hotel detail dialog for the selected upcoming
     * transaction - same dialog_payment_details.xml layout the dashboard's Recent Bookings
     * and Payment Status sections open in TransactionHistoryActivity, so a guest deep-linked
     * here from a specific dashboard item sees the exact record's complete details, not just
     * a highlighted card. Download-receipt is hidden here since export isn't wired on this
     * screen (available from Transaction History instead).
     */
    private void showTransactionDetailsDialog(Booking booking) {
        View detailView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_details, null);
        java.text.NumberFormat currencyFormat = java.text.NumberFormat.getCurrencyInstance(new Locale("en", "PH"));

        TextView tvBookingId = detailView.findViewById(R.id.detailPaymentBookingId);
        TextView tvBookingRef = detailView.findViewById(R.id.detailPaymentBookingRef);
        TextView tvRoomInfo = detailView.findViewById(R.id.detailPaymentRoomInfo);
        TextView tvCurrentStatus = detailView.findViewById(R.id.detailPaymentCurrentStatus);
        TextView tvLastMethod = detailView.findViewById(R.id.detailPaymentLastMethod);
        TextView tvLastDate = detailView.findViewById(R.id.detailPaymentLastDate);

        View cardCancellation = detailView.findViewById(R.id.cardPaymentCancellation);
        TextView tvCancelDate = detailView.findViewById(R.id.detailPaymentCancelDate);
        TextView tvCancelReason = detailView.findViewById(R.id.detailPaymentCancelReason);

        TextView tvTotal = detailView.findViewById(R.id.detailPaymentTotal);
        TextView tvPaid = detailView.findViewById(R.id.detailPaymentPaid);
        TextView tvRemaining = detailView.findViewById(R.id.detailPaymentRemaining);
        com.google.android.material.progressindicator.LinearProgressIndicator progress =
                detailView.findViewById(R.id.detailPaymentProgress);

        LinearLayout historyContainer = detailView.findViewById(R.id.detailPaymentHistoryContainer);
        TextView tvHistoryEmpty = detailView.findViewById(R.id.detailPaymentHistoryEmpty);

        tvBookingId.setText(getString(R.string.booking_id_format, booking.getId()));

        String payRef;
        if (booking.getTransactionRef() != null) {
            payRef = booking.getTransactionRef();
            if (booking.isPaymentPendingVerification()) {
                payRef += " (" + getString(R.string.awaiting_verification_label) + ")";
            }
        } else if (!booking.isHasBooking()) {
            payRef = getString(R.string.no_payment_yet_label);
        } else {
            payRef = getString(R.string.label_pending_payment);
        }
        tvBookingRef.setText(getString(R.string.transaction_ref_label, payRef));

        String cin = booking.getActualCheckIn() != null ? booking.getActualCheckIn() : booking.getCheckInDate();
        String cout = booking.getActualCheckOut() != null ? booking.getActualCheckOut() : booking.getCheckOutDate();
        tvRoomInfo.setText(getString(R.string.booking_item_format, booking.getRoomName(),
                        android.text.TextUtils.join(", ", booking.getAllRoomTypeNames()))
                + "  •  " + cin + " - " + cout);

        PaymentStatusResolver.Result statusResult = PaymentStatusResolver.resolve(this, booking);
        tvCurrentStatus.setText(statusResult.label);
        tvCurrentStatus.setBackgroundTintList(getColorStateList(statusResult.bgColorRes));
        tvCurrentStatus.setTextColor(getColor(statusResult.fgColorRes));

        tvLastMethod.setText(getString(R.string.payment_method_label,
                booking.getPaymentMethod() != null ? booking.getPaymentMethod() : getString(R.string.label_not_available)));
        String payDate = booking.getPaymentDate() != null ? booking.getPaymentDate() : getString(R.string.label_not_available);
        tvLastDate.setText(getString(R.string.label_paid_on, payDate));

        if ("Cancelled".equalsIgnoreCase(booking.getStatus())) {
            cardCancellation.setVisibility(View.VISIBLE);
            tvCancelDate.setText(getString(R.string.label_cancelled_on,
                    booking.getCancellationDate() != null ? booking.getCancellationDate() : getString(R.string.label_not_available)));
            tvCancelReason.setText(getString(R.string.label_reason,
                    booking.getCancellationReason() != null ? booking.getCancellationReason() : getString(R.string.label_customer_request)));
        } else {
            cardCancellation.setVisibility(View.GONE);
        }

        double total = booking.getTotalAmount();
        double paid = booking.getAmountPaid();
        double remaining = Math.max(0, booking.getRemainingBalance());
        tvTotal.setText(currencyFormat.format(total));
        tvPaid.setText(currencyFormat.format(paid));
        tvRemaining.setText(currencyFormat.format(remaining));
        progress.setProgress(total > 0 ? (int) Math.min(100, Math.round((paid / total) * 100)) : 0);

        List<Booking.PaymentRecord> history = booking.getPaymentHistory();
        historyContainer.removeAllViews();
        if (history == null || history.isEmpty()) {
            tvHistoryEmpty.setVisibility(View.VISIBLE);
        } else {
            tvHistoryEmpty.setVisibility(View.GONE);
            for (Booking.PaymentRecord record : history) {
                historyContainer.addView(buildPaymentHistoryRow(historyContainer, record, currencyFormat));
            }
        }

        View btnDownload = detailView.findViewById(R.id.btnDownloadPaymentReceipt);
        if (btnDownload != null) btnDownload.setVisibility(View.GONE);

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setView(detailView);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        detailView.findViewById(R.id.btnClosePaymentDetail).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private View buildPaymentHistoryRow(ViewGroup parent, Booking.PaymentRecord record, java.text.NumberFormat currencyFormat) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_payment_history_entry, parent, false);
        TextView tvMethod = row.findViewById(R.id.tvHistoryMethod);
        TextView tvDate = row.findViewById(R.id.tvHistoryDate);
        TextView tvAmount = row.findViewById(R.id.tvHistoryAmount);
        TextView tvStatus = row.findViewById(R.id.tvHistoryStatus);

        tvMethod.setText(record.method != null ? record.method : getString(R.string.label_not_available));
        tvDate.setText(record.date != null ? record.date : getString(R.string.label_not_available));
        try {
            tvAmount.setText(currencyFormat.format(Double.parseDouble(record.amount)));
        } catch (NumberFormatException | NullPointerException e) {
            tvAmount.setText(record.amount != null ? record.amount : "");
        }
        tvStatus.setText(record.status != null ? record.status : "");

        return row;
    }

    private View addEventCard(UpcomingEvent e, boolean isNext) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_upcoming_transaction_detail, listContainer, false);
        Booking b = e.booking;

        TextView tvTomorrowBadge = card.findViewById(R.id.tvTomorrowBadge);
        TextView tvTypePill = card.findViewById(R.id.tvTransactionTypePill);
        TextView tvBookingId = card.findViewById(R.id.tvTransactionBookingId);
        TextView tvTitle = card.findViewById(R.id.tvTransactionTitle);
        TextView tvRoomType = card.findViewById(R.id.tvTransactionRoomType);
        TextView tvCheckIn = card.findViewById(R.id.tvTransactionCheckIn);
        TextView tvCheckInTime = card.findViewById(R.id.tvTransactionCheckInTime);
        TextView tvCheckOut = card.findViewById(R.id.tvTransactionCheckOut);
        TextView tvCheckOutTime = card.findViewById(R.id.tvTransactionCheckOutTime);
        TextView tvGuests = card.findViewById(R.id.tvTransactionGuests);
        TextView tvTotal = card.findViewById(R.id.tvTransactionTotal);
        TextView tvStatus = card.findViewById(R.id.tvTransactionStatus);
        TextView tvPaymentStatus = card.findViewById(R.id.tvTransactionPaymentStatus);

        tvTypePill.setText(b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
        tvBookingId.setText(getString(R.string.booking_id_format, b.getId()));
        tvTitle.setText(b.getRoomName());
        tvRoomType.setText(getString(R.string.booking_detail_room_type,
                android.text.TextUtils.join(", ", b.getAllRoomTypeNames())));
        tvCheckIn.setText(b.getCheckInDate());
        tvCheckInTime.setText(R.string.check_in_time);
        tvCheckOut.setText(b.getCheckOutDate());
        tvCheckOutTime.setText(R.string.check_out_time);
        tvGuests.setText(getString(R.string.guests_count_format, b.getGuests()));

        java.text.NumberFormat currencyFormat = java.text.NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        tvTotal.setText(currencyFormat.format(b.getTotalAmount()));

        // Booking/reservation status - same color convention as TransactionAdapter.
        String status = b.getStatus();
        tvStatus.setText(status);
        if ("Confirmed".equalsIgnoreCase(status) || "Checked-In".equalsIgnoreCase(status) || "Checked-Out".equalsIgnoreCase(status) || "Verified".equalsIgnoreCase(status)) {
            tvStatus.setBackgroundResource(R.drawable.bg_badge_success);
            tvStatus.setTextColor(getColor(R.color.velocity_green_dark));
        } else if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            tvStatus.setBackgroundResource(R.drawable.bg_badge_error);
            tvStatus.setTextColor(getColor(R.color.velocity_red_dark));
        } else {
            tvStatus.setBackgroundResource(R.drawable.bg_badge_warning);
            tvStatus.setTextColor(getColor(R.color.velocity_orange_primary));
        }

        PaymentStatusResolver.Result paymentStatus = PaymentStatusResolver.resolve(this, b);
        tvPaymentStatus.setText(paymentStatus.label);
        tvPaymentStatus.setBackgroundTintList(getColorStateList(paymentStatus.bgColorRes));
        tvPaymentStatus.setTextColor(getColor(paymentStatus.fgColorRes));

        if (isDateTomorrow(e.dateLabel)) {
            tvTomorrowBadge.setVisibility(View.VISIBLE);
            tvTomorrowBadge.setText(e.isCheckIn ? R.string.check_in_tomorrow_label : R.string.check_out_tomorrow_label);
        } else {
            tvTomorrowBadge.setVisibility(View.GONE);
        }

        // Visually highlight the very next (soonest) upcoming transaction so it's
        // unmistakable at a glance which one is coming up first.
        com.google.android.material.card.MaterialCardView cardView = card.findViewById(R.id.cardUpcomingTransaction);
        if (isNext) {
            cardView.setStrokeColor(getColor(R.color.velocity_red_primary));
            cardView.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
            cardView.setCardElevation(8f);
        }

        card.setOnClickListener(v -> {
            String section = b.isHasBooking()
                    ? BookingAndReservationActivity.SECTION_BOOKING
                    : BookingAndReservationActivity.SECTION_RESERVATION;
            android.content.Intent intent = new android.content.Intent(this, BookingAndReservationActivity.class);
            intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, section);
            intent.putExtra(BookingAndReservationActivity.EXTRA_HIGHLIGHT_ID, b.getId());
            startActivity(intent);
        });

        listContainer.addView(card);
        return card;
    }

    /** True when dateStr (yyyy/M/d-style, per date_format_short) falls exactly on tomorrow's calendar date. */
    private boolean isDateTomorrow(String dateStr) {
        if (dateStr == null) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
            long time = sdf.parse(dateStr).getTime();
            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0);
            todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0);
            todayCal.set(Calendar.MILLISECOND, 0);
            long tomorrowStart = todayCal.getTimeInMillis() + 86400000L;
            long tomorrowEnd = tomorrowStart + 86400000L;
            return time >= tomorrowStart && time < tomorrowEnd;
        } catch (Exception ex) {
            return false;
        }
    }
}
