package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * The Dashboard's one compact stay card (item_recent_upcoming_stay.xml): type chip, optional tag (Upcoming / Recent),
 * reference, room, "check-in → check-out · N nights", status chip and a chevron. Used by the Stay Calendar list, Other
 * Recent and Upcoming Stays and Recent Upcoming Stay so all three look and read the same. A tap opens Upcoming Hotel
 * Transactions with the transaction's type and status filters set, scrolled to and showing it.
 */
final class StayCardBinder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    enum Tag { NONE, UPCOMING, RECENT }

    private StayCardBinder() {
    }

    @NonNull
    static View add(@NonNull Context context, @NonNull ViewGroup parent, @NonNull Booking b, @NonNull Tag tag) {
        View card = bind(context, parent, b, tag);
        parent.addView(card);
        return card;
    }

    @NonNull
    static View bind(@NonNull Context context, @NonNull ViewGroup parent, @NonNull Booking b, @NonNull Tag tag) {
        View row = LayoutInflater.from(context).inflate(R.layout.item_recent_upcoming_stay, parent, false);
        ((TextView) row.findViewById(R.id.tvRecentStayType)).setText(
                b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
        ((TextView) row.findViewById(R.id.tvRecentStayRef)).setText(TransactionText.reference(context, b.isHasBooking(), b.getDisplayId()));
        ((TextView) row.findViewById(R.id.tvRecentStayRoom)).setText(TransactionRow.roomTextOf(b));
        ((TextView) row.findViewById(R.id.tvRecentStayDates)).setText(datesText(context, b));
        TransactionStatusChip.apply(context, row.findViewById(R.id.tvRecentStayStatus), b.getStatus());
        bindTag(context, row.findViewById(R.id.tvRecentStayTag), tag);
        if (StayCalendarModel.isClosed(b)) row.setAlpha(UpcomingTransactionsActivity.MUTED_ALPHA);
        row.setOnClickListener(NavUtils.debounce(v -> context.startActivity(UpcomingTransactionsActivity.intentWithFilters(context, b))));
        return row;
    }

    private static void bindTag(Context context, TextView chip, Tag tag) {
        if (tag == Tag.NONE) {
            chip.setVisibility(View.GONE);
            return;
        }
        @StringRes int label = tag == Tag.UPCOMING ? R.string.stay_card_tag_upcoming : R.string.stay_card_tag_recent;
        int bg = tag == Tag.UPCOMING ? R.color.velocity_blue_soft : R.color.status_cancelled_bg;
        int fg = tag == Tag.UPCOMING ? R.color.velocity_blue_primary : R.color.status_cancelled_fg;
        chip.setText(label);
        chip.setBackgroundTintList(ContextCompat.getColorStateList(context, bg));
        chip.setTextColor(ContextCompat.getColor(context, fg));
        chip.setVisibility(View.VISIBLE);
    }

    /** "Oct 20, 2026 → Oct 22, 2026 · 2 nights"; falls back to the raw dates when they cannot be read. */
    @NonNull
    static String datesText(@NonNull Context context, @NonNull Booking b) {
        LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
        LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
        if (in == null || out == null) {
            return context.getString(R.string.recent_upcoming_dates_format, text(b.getCheckInDate()), text(b.getCheckOutDate()));
        }
        int nights = (int) Math.max(1, ChronoUnit.DAYS.between(in, out));
        return context.getString(R.string.stay_card_dates_format, DAY.format(in), DAY.format(out),
                context.getResources().getQuantityString(R.plurals.receipt_nights_count, nights, nights));
    }

    private static String text(@Nullable String s) {
        return s == null ? "" : s;
    }
}
