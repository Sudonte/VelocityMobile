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

    public static final String PARTIAL_PAYMENT = "PARTIAL_PAYMENT";
    public static final String FULL_PAYMENT = "FULL_PAYMENT";
    public static final String CHECKOUT_PAYMENT = "CHECKOUT_PAYMENT";

    private ReceiptTypeMapper() {
    }

    /**
     * PARTIAL_RECEIPT -> "Partial Payment Receipt", FULL_PAYMENT_RECEIPT ->
     * "Payment Receipt", OFFICIAL_RECEIPT -> "Official Payment Receipt".
     * An unrecognized/future receipt_type falls back to the generic
     * "Payment Receipt" rather than crashing or showing a raw enum string -
     * a 20/30/40/50% deposit must never render as "Official", and this
     * fallback deliberately can't produce that label either.
     */
    public static String labelForReceiptType(String receiptType) {
        if (receiptType == null) {
            return "Payment Receipt";
        }
        switch (receiptType) {
            case PARTIAL_RECEIPT:
                return "Partial Payment Receipt";
            case OFFICIAL_RECEIPT:
                return "Official Payment Receipt";
            case FULL_PAYMENT_RECEIPT:
            default:
                return "Payment Receipt";
        }
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
