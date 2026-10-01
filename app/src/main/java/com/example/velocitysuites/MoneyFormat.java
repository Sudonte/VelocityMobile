package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The one way this app prints or reads a peso amount on the Transaction History, Notifications and
 * Payment Receipt screens, so the same money never shows as "₱1,800.00" in one place and "PHP1,800.0"
 * or "₱1800" in another (the screens used to mix NumberFormat's locale-dependent currency instance,
 * String.format and a string resource).
 * <p>
 * Locale-independent on purpose ({@link Locale#US} grouping/decimal symbols): a device set to a
 * locale that writes "1.800,00" must not make a hotel bill read differently from the receipt the
 * receptionist prints. Pure Java, JVM-unit-testable.
 */
public final class MoneyFormat {

    public static final String PESO = "₱";

    /** Two amounts closer together than this are the same money (half a centavo). */
    public static final double EPSILON = 0.005;

    private MoneyFormat() {
    }

    /** "₱1,800.00". NaN/infinite/null-ish input prints as "₱0.00" rather than "NaN"; a negative prints as "-₱5.00". */
    public static String format(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) return PESO + "0.00";
        String digits = String.format(Locale.US, "%,.2f", Math.abs(amount));
        boolean roundsToZero = "0.00".equals(digits);
        return (amount < 0 && !roundsToZero ? "-" : "") + PESO + digits;
    }

    /**
     * Reads an amount off the wire (a JSON decimal string, or an already-formatted display string).
     * Tolerates a leading currency mark, thousands separators and stray whitespace; anything that is
     * not a finite number - null, blank, "N/A", "abc" - is 0 rather than an exception, so one bad
     * API field can never crash a list.
     */
    public static double parse(@Nullable String raw) {
        if (raw == null) return 0;
        String cleaned = raw.trim().replace(PESO, "").replace("PHP", "").replace("Php", "")
                .replace(",", "").replace(" ", "");
        if (cleaned.isEmpty()) return 0;
        try {
            double value = Double.parseDouble(cleaned);
            return Double.isNaN(value) || Double.isInfinite(value) ? 0 : value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** True when the amount is worth showing/counting - more than half a centavo. */
    public static boolean isPositive(double amount) {
        return amount > EPSILON;
    }

    /** True when {@code paid} covers {@code total} (within half a centavo of rounding). */
    public static boolean covers(double paid, double total) {
        return paid + EPSILON >= total;
    }
}
