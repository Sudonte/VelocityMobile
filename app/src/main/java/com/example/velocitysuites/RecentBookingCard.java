package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.ChipGroup;

import java.time.LocalDate;

/**
 * The Dashboard's Recent Bookings card, built the way every stay card is: the bold ID with a chevron, one wrapping row of
 * chips (an optional "Today" / "Tomorrow", the neutral booking status, the payment chip in Payment Status's colours), the
 * room type and "Oct 20 → Oct 21, 2026 · 1 night".
 */
final class RecentBookingCard {

    enum DayChip { NONE, TODAY, TOMORROW }

    private RecentBookingCard() {
    }

    /**
     * Only a stay that has NOT checked in yet (Pending or Confirmed) can be "Today" or "Tomorrow", and only when its check-in
     * is that day. A checked-in, checked-out, cancelled or rejected stay never gets one - "Tomorrow" on a guest who is
     * already in the room was wrong.
     */
    @NonNull
    static DayChip dayChip(@Nullable Booking b, @NonNull LocalDate today) {
        if (b == null || !RecentUpcomingStays.qualifies(b)) return DayChip.NONE;
        LocalDate checkIn = PaymentDates.parseDate(b.getCheckInDate());
        if (checkIn == null) return DayChip.NONE;
        if (checkIn.equals(today)) return DayChip.TODAY;
        if (checkIn.equals(today.plusDays(1))) return DayChip.TOMORROW;
        return DayChip.NONE;
    }

    @NonNull
    static View add(@NonNull Context context, @NonNull ViewGroup parent, @NonNull Booking b, @NonNull LocalDate today,
                    @NonNull Runnable onClick) {
        View card = LayoutInflater.from(context).inflate(R.layout.item_recent_booking, parent, false);
        ((TextView) card.findViewById(R.id.tvRecentBookingRef)).setText(TransactionText.reference(context, b.isHasBooking(), b.getDisplayId()));

        ChipGroup chips = card.findViewById(R.id.chipsRecentBooking);
        DayChip day = dayChip(b, today);
        if (day != DayChip.NONE) {
            addChip(context, chips, day == DayChip.TODAY ? R.string.today_badge_short : R.string.tomorrow_badge_short,
                    R.color.velocity_red_status_soft, R.color.velocity_red_primary);
        }
        String status = BookingStatusWords.label(context, b.getStatus());
        if (!status.isEmpty()) {
            TextView chip = addChip(context, chips, 0, R.color.status_cancelled_bg, R.color.status_cancelled_fg);
            chip.setText(status);
        }
        PaymentBalanceTone tone = PaymentBalanceTone.of(TransactionStatusHelper.grandTotalOf(b), TransactionStatusHelper.verifiedPaidOf(b));
        addChip(context, chips, tone.labelRes, tone.chipBgRes, tone.colorRes);

        TextView room = card.findViewById(R.id.tvRecentBookingRoom);
        String roomText = TransactionRow.roomTextOf(b);
        room.setText(roomText);
        room.setVisibility(roomText.isEmpty() ? View.GONE : View.VISIBLE);

        TextView dates = card.findViewById(R.id.tvRecentBookingDates);
        String datesText = StayCardText.datesAndNights(context, b);
        dates.setText(datesText);
        dates.setVisibility(datesText.isEmpty() ? View.GONE : View.VISIBLE);

        card.setOnClickListener(NavUtils.debounce(v -> onClick.run()));
        parent.addView(card);
        return card;
    }

    private static TextView addChip(Context context, ChipGroup group, @StringRes int label, @ColorRes int bg, @ColorRes int fg) {
        TextView chip = (TextView) LayoutInflater.from(context).inflate(R.layout.view_stay_chip, group, false);
        if (label != 0) chip.setText(label);
        chip.setBackgroundTintList(ContextCompat.getColorStateList(context, bg));
        chip.setTextColor(ContextCompat.getColor(context, fg));
        group.addView(chip);
        return chip;
    }
}
