package com.example.velocitysuites;

/**
 * Single source of truth for the app-wide GCash reference number display format
 * ("XXXX XXX XXXXXX", 4-3-6 grouping of the 13 raw digits) - shared by the live
 * input formatting in PaymentActivity's Step 3 and every read-only screen that
 * later displays a reference number that was stored (correctly) as raw digits.
 */
public final class GcashReferenceFormatter {

    private GcashReferenceFormatter() {}

    public static String digitsOnly(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Groups raw digits into "XXXX XXX XXXXXX". Idempotent - safe to call on a value
     * that's already formatted (or that has stray separators like hyphens from a
     * paste), since it strips to digits first rather than assuming a clean input.
     * Never drops, pads, or reorders a digit - a value that isn't exactly 13 digits
     * still gets grouped digit-for-digit, it just won't land on the 4-3-6 boundary.
     */
    public static String format(String value) {
        String digits = digitsOnly(value);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i == 4 || i == 7) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /**
     * The single source of truth for displaying a stored GCash reference: the
     * properly grouped 13-digit value, or one of two caller-supplied fallback
     * messages - never the raw partial digits themselves. Distinguishes "no
     * value at all" (missingMessage - e.g. a Cash record, or a GCash payment
     * whose reference genuinely never got stored) from "a value exists but
     * isn't a complete 13-digit reference" (incompleteMessage - a legacy
     * record from before this field was fully validated server-side). Neither
     * branch invents digits; both are explicit, honest fallbacks.
     */
    public static String formatOrFallback(String value, String missingMessage, String incompleteMessage) {
        String digits = digitsOnly(value);
        if (digits.length() == 13) return format(digits);
        return digits.isEmpty() ? missingMessage : incompleteMessage;
    }

    /**
     * Displays a GCash mobile number in whichever shape it was actually
     * stored in - local "09XX XXX XXXX" for this app's own 10-digit
     * "9XXXXXXXXX" storage format (PaymentActivity's Step 2 input), or
     * international "+63 9XX XXX XXXX" for a value already stored with the
     * 63 country code. Never converts between the two forms, only groups
     * whichever shape the raw value already is; every digit stays visible
     * either way. Falls back to the raw value verbatim for any other
     * length/shape rather than mangling real (if unusual) data.
     */
    public static String formatMobileNumber(String raw) {
        if (raw == null) return null;
        String digits = digitsOnly(raw);
        if (digits.length() == 12 && digits.startsWith("63")) {
            String local = digits.substring(2);
            return "+63 " + local.substring(0, 3) + " " + local.substring(3, 6) + " " + local.substring(6);
        }
        if (digits.length() == 10 && digits.startsWith("9")) {
            String local = "0" + digits;
            return local.substring(0, 4) + " " + local.substring(4, 7) + " " + local.substring(7);
        }
        if (digits.length() == 11 && digits.startsWith("09")) {
            return digits.substring(0, 4) + " " + digits.substring(4, 7) + " " + digits.substring(7);
        }
        return raw;
    }
}
