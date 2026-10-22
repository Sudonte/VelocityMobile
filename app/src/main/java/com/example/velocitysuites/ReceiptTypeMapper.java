package com.example.velocitysuites;

/**
 * Single source of truth for turning the backend's receipt_type/
 * transaction_type strings into guest-facing labels - mirrors the
 * receptionist web side's own status-badge.blade.php label map exactly
 * (see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md) so both surfaces show the
 * same wording for the same booking. Deliberately never infers a receipt's
 * type from payment percentage or from remaining balance - always reads the
 * backend's own receipt_type/transaction_type field.
 */
public final class ReceiptTypeMapper {

    public static final String PARTIAL_RECEIPT = "PARTIAL_RECEIPT";
    public static final String FULL_PAYMENT_RECEIPT = "FULL_PAYMENT_RECEIPT";
    public static final String OFFICIAL_RECEIPT = "OFFICIAL_RECEIPT";
    public static final String CHECKIN_SETTLEMENT_RECEIPT = "CHECKIN_SETTLEMENT_RECEIPT";

    public static final String PARTIAL_PAYMENT = "PARTIAL_PAYMENT";
    public static final String FULL_PAYMENT = "FULL_PAYMENT";
    public static final String CHECKOUT_PAYMENT = "CHECKOUT_PAYMENT";

    private ReceiptTypeMapper() {
    }

    /**
     * The stay's three receipts by their names: PARTIAL_RECEIPT / FULL_PAYMENT_RECEIPT -> "Payment verified" (the receptionist verified
     * the booking's payment), CHECKIN_SETTLEMENT_RECEIPT -> "Check-in", OFFICIAL_RECEIPT -> "Check-out (final)". An unrecognized or
     * future receipt_type falls back to the generic "Payment receipt" rather than crashing or showing a raw enum string - and it can
     * never read as the final one.
     */
    public static String labelForReceiptType(String receiptType) {
        if (receiptType == null) {
            return "Payment receipt";
        }
        switch (receiptType) {
            case PARTIAL_RECEIPT:
            case FULL_PAYMENT_RECEIPT:
                return "Payment verified";
            case OFFICIAL_RECEIPT:
                return "Check-out (final)";
            case CHECKIN_SETTLEMENT_RECEIPT:
                return "Check-in";
            default:
                return "Payment receipt";
        }
    }

    /**
     * Which of a stay's three receipts this is: 1 = a payment verified in the Booking module (partial or full),
     * 2 = the balance settled at check-in, 3 = the official receipt at check-out; 0 when the type is unknown.
     */
    public static int stageOf(String receiptType) {
        if (receiptType == null) return 0;
        switch (receiptType) {
            case PARTIAL_RECEIPT:
            case FULL_PAYMENT_RECEIPT:
                return 1;
            case CHECKIN_SETTLEMENT_RECEIPT:
                return 2;
            case OFFICIAL_RECEIPT:
                return 3;
            default:
                return 0;
        }
    }

    /** "Receipt 2 - Check-in": the label with its place in the stay's three receipts. */
    public static String labelWithStage(String receiptType) {
        int stage = stageOf(receiptType);
        String label = labelForReceiptType(receiptType);
        return stage == 0 ? label : "Receipt " + stage + " - " + label;
    }

    /**
     * PARTIAL_PAYMENT -> "Partial Payment", FULL_PAYMENT -> "Full Payment",
     * CHECKOUT_PAYMENT -> "Checkout Payment". An unrecognized/future
     * transaction_type falls back to a title-cased rendering of the raw
     * value (e.g. "SOME_NEW_TYPE" -> "Some New Type") rather than crashing.
     */
    public static String labelForTransactionType(String transactionType) {
        if (transactionType == null) {
            return "Payment";
        }
        switch (transactionType) {
            case PARTIAL_PAYMENT:
                return "Partial Payment";
            case FULL_PAYMENT:
                return "Full Payment";
            case CHECKOUT_PAYMENT:
                return "Checkout Payment";
            default:
                return titleCase(transactionType);
        }
    }

    private static String titleCase(String value) {
        String[] words = value.toLowerCase(java.util.Locale.US).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : value;
    }
}
