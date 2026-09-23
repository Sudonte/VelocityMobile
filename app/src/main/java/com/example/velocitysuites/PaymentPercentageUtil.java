package com.example.velocitysuites;

/**
 * Single source of truth for converting between this app's two payment-percentage
 * representations, plus display formatting - extracted from PaymentActivity so the
 * pure math is unit-testable without an Android runtime (see PaymentPercentageTest).
 *
 * <p>Two representations exist by design, never three:
 * <ul>
 *   <li><b>Fraction</b> (0.20, 0.30, 0.40, 0.50, 1.00) - used only as PaymentActivity's
 *   own in-memory chip-selection state ({@code selectedPartialPercent}), since it
 *   multiplies directly against a peso amount ({@code grandTotalValue * fraction}).</li>
 *   <li><b>API/whole percentage</b> (20, 30, 40, 50, 100) - the shape sent to and
 *   returned by the backend (Reservation::selected_payment_percentage,
 *   Api\PaymentController::store()'s $selectedPercentage) and shown on every
 *   percentage display (Booking Details/Payment Receipt/Billing Summary).</li>
 * </ul>
 * A value already in the API/whole shape (e.g. Booking#getSelectedPaymentPercentage())
 * must never be multiplied by 100 again - that was the historical "50% -> 5000%" bug
 * this class exists to make structurally impossible to reintroduce.
 */
public final class PaymentPercentageUtil {

    private PaymentPercentageUtil() {
    }

    /** 0.20 -> 20, 1.00 -> 100. Rounds to the nearest whole percentage point. */
    public static int fractionToApiPercentage(double fraction) {
        return (int) Math.round(fraction * 100.0);
    }

    /** 20 -> 0.20, 100 -> 1.00. */
    public static double apiPercentageToFraction(double apiPercentage) {
        return apiPercentage / 100.0;
    }

    /**
     * "20%"/"50%"/"100%" from an already-whole API percentage value (never a
     * 0.20-1.00 fraction - passing a fraction here would render as "0%").
     * Rounds to the nearest whole number, matching every existing display call
     * site (BookingDetailsActivity#formatPercentage()/PaymentReceiptActivity's
     * inline equivalent), so a stray ".0" is never shown.
     */
    public static String formatApiPercentageForDisplay(double apiPercentage) {
        return Math.round(apiPercentage) + "%";
    }
}
