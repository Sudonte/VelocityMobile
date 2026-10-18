package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The guest's next stay for the Dashboard: which booking/reservation it is, whether they are still to arrive or already
 * in house, how far away the check-in / check-out is (for the plain hint such as "Check-in tomorrow"), the stay's
 * nights, and a plain-language status. Pure data and rules (days are hotel-local calendar days, passed in), so the
 * selection and wording are unit-testable.
 */
public final class NextStay {

    /** Where the guest is in the stay. */
    public enum Phase { UPCOMING, IN_HOUSE }

    /** Plain-language status - always shown as colour + icon + text, never colour alone. */
    public enum PlainStatus {
        STAYING(R.string.next_stay_status_staying, Tone.GOOD),
        CONFIRMED(R.string.next_stay_status_confirmed, Tone.GOOD),
        WAITING_CONFIRMATION(R.string.next_stay_status_waiting_confirmation, Tone.WAITING),
        PAYMENT_REVIEW(R.string.next_stay_status_payment_review, Tone.INFO),
        WAITING_PAYMENT(R.string.next_stay_status_waiting_payment, Tone.WAITING);

        @StringRes public final int labelRes;
        public final Tone tone;

        PlainStatus(int labelRes, Tone tone) {
            this.labelRes = labelRes;
            this.tone = tone;
        }
    }

    public enum Tone { GOOD, WAITING, INFO }

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    public final Booking booking;
    public final Phase phase;
    public final LocalDate checkIn;
    public final LocalDate checkOut;
    /** Days from today to the check-in (UPCOMING) or the check-out (IN_HOUSE); negative when that day has passed. */
    public final long daysToEvent;
    /** Days from today to the scheduled check-in day; positive for a guest who was checked in before their stay's first day. */
    public final long daysToCheckIn;
    public final long nights;
    public final PlainStatus status;
    public final double balance;

    private NextStay(Booking booking, Phase phase, LocalDate checkIn, LocalDate checkOut, long daysToEvent, LocalDate today) {
        this.booking = booking;
        this.phase = phase;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.daysToEvent = daysToEvent;
        this.daysToCheckIn = ChronoUnit.DAYS.between(today, checkIn);
        this.nights = Math.max(1, ChronoUnit.DAYS.between(checkIn, checkOut));
        this.status = plainStatus(booking);
        this.balance = TransactionStatusHelper.summarize(booking).balance;
    }

    /**
     * The nearest event: for a stay not yet started its check-in, for a guest in house their check-out. Cancelled /
     * rejected and finished stays never qualify; a past check-in that was never checked in is not "next" either.
     * An in-house guest whose check-out day has already passed still shows (it is the most urgent thing).
     */
    @Nullable
    public static NextStay select(@Nullable List<Booking> bookings, @NonNull LocalDate today) {
        List<NextStay> all = selectAll(bookings, today);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Every transaction that falls on the nearest event day - one stay or several, bookings and reservations alike.
     * A guest can hold more than one transaction on the same day, and none of them may be hidden behind "the" next
     * stay. In-house stays come first, then by reference so the order is stable between refreshes. Empty when
     * nothing qualifies.
     */
    @NonNull
    public static List<NextStay> selectAll(@Nullable List<Booking> bookings, @NonNull LocalDate today) {
        List<NextStay> candidates = new ArrayList<>();
        if (bookings == null) return candidates;
        for (Booking b : bookings) {
            if (b == null) continue;
            if (TransactionCategorizer.categorize(b) == TransactionCategorizer.Category.CANCELLED) continue;
            String status = b.getStatus() == null ? "" : b.getStatus();
            if ("Checked-Out".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status)) continue;
            LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
            LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
            if (in == null || out == null) continue;

            if ("Checked-In".equalsIgnoreCase(status)) {
                candidates.add(new NextStay(b, Phase.IN_HOUSE, in, out, ChronoUnit.DAYS.between(today, out), today));
            } else if (!in.isBefore(today)) {
                candidates.add(new NextStay(b, Phase.UPCOMING, in, out, ChronoUnit.DAYS.between(today, in), today));
            }
        }
        if (candidates.isEmpty()) return candidates;
        long nearest = Long.MAX_VALUE;
        for (NextStay c : candidates) nearest = Math.min(nearest, c.daysToEvent);
        List<NextStay> out = new ArrayList<>();
        for (NextStay c : candidates) {
            if (c.daysToEvent == nearest) out.add(c);
        }
        Collections.sort(out, (x, y) -> {
            if (x.phase != y.phase) return x.phase == Phase.IN_HOUSE ? -1 : 1;
            String a = x.booking.getDisplayId() == null ? "" : x.booking.getDisplayId();
            String c = y.booking.getDisplayId() == null ? "" : y.booking.getDisplayId();
            return a.compareTo(c);
        });
        return out;
    }

    /** True when the stay is over (checked out / completed): its own status, not a "waiting" one, describes it. */
    public boolean isFinished() {
        String status = booking.getStatus();
        return "Checked-Out".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status);
    }

    /**
     * Every transaction whose stay covers this calendar day (check-in day through check-out day, both included),
     * cancelled and rejected ones left out. Finished stays are kept so past days still show what happened. In-house
     * first, then by reference, so the order is stable between refreshes. Days are compared as calendar dates.
     */
    @NonNull
    public static List<NextStay> forDay(@Nullable List<Booking> bookings, @NonNull LocalDate day, @NonNull LocalDate today) {
        List<NextStay> out = new ArrayList<>();
        if (bookings == null) return out;
        for (Booking b : bookings) {
            if (b == null || StayCalendarModel.isClosed(b)) continue;
            LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
            LocalDate end = PaymentDates.parseDate(b.getCheckOutDate());
            if (in == null) continue;
            if (end == null || end.isBefore(in)) end = in;
            if (day.isBefore(in) || day.isAfter(end)) continue;
            boolean inHouse = "Checked-In".equalsIgnoreCase(b.getStatus());
            out.add(new NextStay(b, inHouse ? Phase.IN_HOUSE : Phase.UPCOMING, in, end,
                    ChronoUnit.DAYS.between(today, inHouse ? end : in), today));
        }
        Collections.sort(out, (x, y) -> {
            if (x.phase != y.phase) return x.phase == Phase.IN_HOUSE ? -1 : 1;
            String a = x.booking.getDisplayId() == null ? "" : x.booking.getDisplayId();
            String c = y.booking.getDisplayId() == null ? "" : y.booking.getDisplayId();
            return a.compareTo(c);
        });
        return out;
    }

    /**
     * The day Your Next Stay opens on: Today when the guest has any transaction on it; otherwise the nearest later day
     * that has one (looking up to {@link #LOOKAHEAD_DAYS} ahead); otherwise Today (the empty state).
     */
    @NonNull
    public static LocalDate defaultDay(@Nullable List<Booking> bookings, @NonNull LocalDate today) {
        if (!forDay(bookings, today, today).isEmpty()) return today;
        LocalDate next = nextDayWithStay(bookings, today);
        return next != null ? next : today;
    }

    public static final int LOOKAHEAD_DAYS = 120;

    /** The nearest day from Today on that has a transaction (Today itself when it has one), or null when there is none. */
    @Nullable
    public static LocalDate nearestStayDay(@Nullable List<Booking> bookings, @NonNull LocalDate today) {
        if (!forDay(bookings, today, today).isEmpty()) return today;
        return nextDayWithStay(bookings, today);
    }

    /** The nearest day after {@code today} that has a transaction, or null when there is none. */
    @Nullable
    public static LocalDate nextDayWithStay(@Nullable List<Booking> bookings, @NonNull LocalDate today) {
        for (int i = 1; i <= LOOKAHEAD_DAYS; i++) {
            LocalDate d = today.plusDays(i);
            if (!forDay(bookings, d, today).isEmpty()) return d;
        }
        return null;
    }

    /** Where this day falls in the stay: "Check-in day", "Check-out day" or "Stay in progress". */
    public String dayRole(@NonNull Context c, @NonNull LocalDate day) {
        if (day.equals(checkIn)) return c.getString(R.string.next_stay_day_role_checkin);
        if (day.equals(checkOut)) return c.getString(R.string.next_stay_day_role_checkout);
        return c.getString(R.string.next_stay_day_role_staying);
    }

    public static PlainStatus plainStatus(@NonNull Booking b) {
        if ("Checked-In".equalsIgnoreCase(b.getStatus())) return PlainStatus.STAYING;
        boolean review = b.isPaymentPendingVerification() && !b.isStaffVerified();
        if (!b.isHasBooking()) return review ? PlainStatus.PAYMENT_REVIEW : PlainStatus.WAITING_PAYMENT;
        if (b.isStaffVerified()) return PlainStatus.CONFIRMED;
        return review ? PlainStatus.PAYMENT_REVIEW : PlainStatus.WAITING_CONFIRMATION;
    }

    /** The one-line hint: "Check-in tomorrow", "Checking out today", "Check-in on Oct 20, 2026", ... */
    public String hint(@NonNull Context c) {
        if (phase == Phase.IN_HOUSE) {
            // Checked in ahead of the booked dates: say so, rather than counting down to a check-out that is days away.
            if (daysToCheckIn > 0) return c.getString(R.string.next_stay_hint_checked_in_early, checkIn.format(SHORT));
            if (daysToEvent < 0) return c.getString(R.string.next_stay_hint_checkout_due, checkOut.format(SHORT));
            if (daysToEvent == 0) return c.getString(R.string.next_stay_hint_checkout_today);
            if (daysToEvent == 1) return c.getString(R.string.next_stay_hint_checkout_tomorrow);
            return c.getResources().getQuantityString(R.plurals.next_stay_hint_checkout_days, (int) daysToEvent, (int) daysToEvent);
        }
        if (daysToEvent == 0) return c.getString(R.string.next_stay_hint_checkin_today);
        if (daysToEvent == 1) return c.getString(R.string.next_stay_hint_checkin_tomorrow);
        if (daysToEvent <= 7) return c.getResources().getQuantityString(R.plurals.next_stay_hint_checkin_days, (int) daysToEvent, (int) daysToEvent);
        return c.getString(R.string.next_stay_hint_checkin_on, checkIn.format(SHORT));
    }

    /** "Oct 12, 2026 → Oct 14, 2026". */
    public String datesText(@NonNull Context c) {
        return c.getString(R.string.next_stay_dates_format, checkIn.format(SHORT), checkOut.format(SHORT));
    }

    public String nightsText(@NonNull Context c) {
        return c.getResources().getQuantityString(R.plurals.receipt_nights_count, (int) nights, (int) nights);
    }

    public boolean hasBalanceDue() {
        return balance > MoneyFormat.EPSILON;
    }
}
