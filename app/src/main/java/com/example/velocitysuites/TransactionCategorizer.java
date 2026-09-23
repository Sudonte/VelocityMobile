package com.example.velocitysuites;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Single source of truth for the Upcoming/Active/Completed/Cancelled bucket a
 * transaction belongs in - reused by BookingAndReservationActivity,
 * DashboardActivity, and TransactionListActivity instead of each keeping its
 * own drifted copy of this logic.
 */
public final class TransactionCategorizer {

    public enum Category { UPCOMING, ACTIVE, COMPLETED, CANCELLED }

    /** Same display format Booking.checkInDate is stored in (see ApiMapper#reformatDate). */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    private TransactionCategorizer() {}

    public static Category categorize(Booking b) {
        String status = b.getStatus();
        if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            return Category.CANCELLED;
        }
        // Deliberately status-only, never date-derived - a stay whose window
        // merely elapsed without an actual checkout stays ACTIVE until staff
        // check the guest out or the backend's No-Show automation cancels it
        // (see ReservationWorkflowService::processNoShow(), Booking#isNoShow()).
        if ("Checked-Out".equalsIgnoreCase(status)) {
            return Category.COMPLETED;
        }
        // A pure Reservation (never converted to a Booking) has no
        // check-in/check-out lifecycle of its own to reach "Checked-Out" -
        // its own terminal state is staff verification instead, matching
        // the receptionist web dashboard's own "Complete Reservation List"
        // tab exactly (Receptionist\ReservationController::index(), tab=
        // 'verified': any reservation with verified_at set). Deliberately
        // NOT applied to a Booking (hasBooking=true) - there, verified_at
        // only means "staff confirmed the booking exists", a separate,
        // earlier step from the guest's stay actually finishing
        // (Checked-Out above), so it must stay ACTIVE/UPCOMING until then.
        if (!b.isHasBooking() && b.isStaffVerified()) {
            return Category.COMPLETED;
        }
        if ("Checked-In".equalsIgnoreCase(status)) {
            return Category.ACTIVE;
        }
        // Only Pending/Confirmed reach here - resolved by comparing today to
        // check-in date. The check-in day itself counts as ACTIVE (not
        // UPCOMING), consistent with the backend's No-Show cutoff also
        // treating that day as when the stay "goes live".
        return isCheckInAfterToday(b.getCheckInDate()) ? Category.UPCOMING : Category.ACTIVE;
    }

    /**
     * True only when checkInDate parses cleanly to a calendar day strictly
     * after today. Unparseable/missing dates fall back to false (ACTIVE)
     * rather than throwing, matching the codebase's existing convention.
     */
    private static boolean isCheckInAfterToday(String checkInDate) {
        Calendar checkIn;
        try {
            checkIn = Calendar.getInstance();
            Date parsed = DATE_FORMAT.parse(checkInDate);
            if (parsed == null) return false;
            checkIn.setTime(parsed);
        } catch (ParseException | NullPointerException e) {
            return false;
        }
        return startOfDay(checkIn).after(startOfDay(Calendar.getInstance()));
    }

    private static Calendar startOfDay(Calendar source) {
        Calendar c = (Calendar) source.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }
}
