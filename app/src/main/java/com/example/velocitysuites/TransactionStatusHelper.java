package com.example.velocitysuites;

import androidx.annotation.DrawableRes;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.List;

/**
 * The ONE place a transaction's payment status is decided and styled. Transaction History's cards and
 * filters, the transaction detail screen, the Payment Receipt header and the Notification cards all ask
 * this class, so the same transaction can never read "Paid" on one screen and "Pending" on another.
 * (It replaces five near-duplicate classifiers that had drifted apart: PaymentStatusResolver,
 * PaymentTransactionStatus, BookingStatusPresenter's payment pill, NotificationStatusResolver's payment
 * states and ReceiptCardHelper#statusLabelFor.)
 *
 * <h3>The rules</h3>
 * Everything is derived from what the backend reported, never assumed client-side. The deciding number is
 * the <b>receptionist-verified</b> amount: only payments the backend marks {@code completed} count toward
 * "paid" - a payment the guest has merely submitted (still {@code pending}) never does.
 * <ol>
 *   <li>The transaction was cancelled -> {@link Status#CANCELLED}; rejected -> {@link Status#REJECTED}.</li>
 *   <li>Verified amount &gt;= grand total (and something was actually verified) -> {@link Status#PAID}.</li>
 *   <li>Verified amount &gt; 0 but below the grand total -> {@link Status#PARTIALLY_PAID}.</li>
 *   <li>Nothing verified, and the latest payment attempt was rejected with none awaiting review ->
 *       {@link Status#REJECTED} (the guest has to pay again).</li>
 *   <li>Otherwise -> {@link Status#PENDING}: the default for every new transaction and for every
 *       submitted payment until the receptionist verifies it on the web system.</li>
 * </ol>
 * Where the backend attached its own authoritative {@code payment_summary} (a show() response) its grand
 * total and total-amount-paid are used as they are; otherwise the itemized payment rows are summed here by
 * the same "completed only" rule.
 * <p>
 * Pure decision logic with no Context (mirrors the classes it replaces), so every rule is JVM-unit-testable
 * - see TransactionStatusHelperTest. {@link #styleFor(Status)} hands out resource ids only; callers resolve
 * strings/colors/drawables themselves.
 */
public final class TransactionStatusHelper {

    private TransactionStatusHelper() {
    }

    public enum Status {
        /** New, or a payment was submitted and awaits the receptionist's verification. */
        PENDING,
        /** The verified amount covers the grand total. */
        PAID,
        /** Something is verified, but less than the grand total. */
        PARTIALLY_PAID,
        CANCELLED,
        /** Rejected by the receptionist (the whole transaction, or the only payment attempt). */
        REJECTED
    }

    /** One transaction's money and status, computed once so every caller shows the same numbers. */
    public static final class Summary {
        public final Status status;
        public final double grandTotal;
        /** Sum of receptionist-verified payments only. */
        public final double verifiedPaid;
        /** Sum of payments the guest submitted that still await verification (0 when the amount is unknown). */
        public final double pendingSubmitted;
        /** True when at least one payment is awaiting verification, whether or not its amount is known. */
        public final boolean hasPendingPayment;
        /** What the guest still owes after the verified payments - never negative. */
        public final double balance;

        Summary(Status status, double grandTotal, double verifiedPaid, double pendingSubmitted, boolean hasPendingPayment) {
            this.status = status;
            this.grandTotal = grandTotal;
            this.verifiedPaid = verifiedPaid;
            this.pendingSubmitted = pendingSubmitted;
            this.hasPendingPayment = hasPendingPayment;
            this.balance = Math.max(0, grandTotal - verifiedPaid);
        }
    }

    // ---- The decision ----

    /**
     * The status rule itself, on already-resolved numbers - the single implementation behind every
     * entry point below.
     *
     * @param transactionStatus  the parent Booking/Reservation's status ("Pending", "Confirmed",
     *                           "Cancelled", "Rejected", ...), or null
     * @param lastAttemptRejected true when the most recent payment attempt was rejected and none is
     *                           awaiting review - only ever consulted when nothing has been verified
     * @param grandTotal         what the transaction costs
     * @param verifiedPaid       receptionist-verified payments only
     */
    public static Status resolve(@Nullable String transactionStatus, boolean lastAttemptRejected,
                                 double grandTotal, double verifiedPaid) {
        if (isCancelled(transactionStatus)) return Status.CANCELLED;
        if (isRejected(transactionStatus)) return Status.REJECTED;
        if (MoneyFormat.isPositive(verifiedPaid)) {
            return MoneyFormat.covers(verifiedPaid, grandTotal) ? Status.PAID : Status.PARTIALLY_PAID;
        }
        return lastAttemptRejected ? Status.REJECTED : Status.PENDING;
    }

    public static Summary summarize(@NonNull Booking booking) {
        double total = grandTotalOf(booking);
        double verified = verifiedPaidOf(booking);
        double submitted = pendingSubmittedOf(booking);
        boolean hasPending = booking.isPaymentPendingVerification() || MoneyFormat.isPositive(submitted);
        Status status = resolve(booking.getStatus(), booking.isPaymentRejected() && !hasPending, total, verified);
        return new Summary(status, total, verified, submitted, hasPending);
    }

    public static Status statusOf(@NonNull Booking booking) {
        return summarize(booking).status;
    }

    /**
     * A receipt describes one or more <i>verified</i> payments, so its status is the same rule applied to the
     * figures printed on it (a Partial Receipt reads "Partially Paid" next to its "Remaining Balance At This
     * Point"; a Full/Official Receipt reads "Paid"). A payload with no payment summary falls back to the
     * backend-stated receipt type - never to a guess.
     */
    public static Summary summarize(@NonNull ReceiptDetail receipt) {
        Booking.PaymentSummary s = receipt.getPaymentSummary();
        if (s == null) {
            boolean partial = "PARTIAL_RECEIPT".equals(receipt.getReceiptType());
            return new Summary(partial ? Status.PARTIALLY_PAID : Status.PAID, 0, 0, 0, false);
        }
        double total = Math.max(0, s.grandTotal);
        double verified = Math.max(0, s.totalAmountPaid);
        // A receipt exists only once a payment was verified; a zero here is a backend gap, and "Paid"
        // with nothing paid would be a lie - so it reads Pending, the honest default.
        return new Summary(resolve(null, false, total, verified), total, verified, 0, false);
    }

    // ---- Inputs (all derived from backend data) ----

    /** The backend's grand total when a payment_summary is attached (show() responses), else the transaction's own total. */
    public static double grandTotalOf(@NonNull Booking booking) {
        Booking.PaymentSummary s = booking.getPaymentSummary();
        if (s != null && MoneyFormat.isPositive(s.grandTotal)) return s.grandTotal;
        return Math.max(0, booking.getTotalAmount());
    }

    /**
     * Receptionist-verified money only. Booking#getAmountPaid() is deliberately NOT used when itemized
     * payments exist: for a direct booking it counts payments still awaiting verification too (the
     * guest-facing "Amount Submitted" view), which would make a just-submitted payment read as paid.
     */
    public static double verifiedPaidOf(@NonNull Booking booking) {
        Booking.PaymentSummary s = booking.getPaymentSummary();
        if (s != null) return Math.max(0, s.totalAmountPaid);

        List<Booking.PaymentRecord> rows = booking.getPaymentHistory();
        if (rows != null && !rows.isEmpty()) {
            double sum = 0;
            for (Booking.PaymentRecord row : rows) {
                if (row != null && isCompleted(row.status)) sum += MoneyFormat.parse(row.amount);
            }
            // The backend's own billing status says the bill was settled without a verified online
            // payment row (a desk settlement) - honor it rather than show a paid bill as unpaid.
            if (!MoneyFormat.isPositive(sum) && "paid".equalsIgnoreCase(booking.getBillingStatus())) {
                return Math.max(0, booking.getTotalAmount());
            }
            return sum;
        }
        return Math.max(0, booking.getAmountPaid());
    }

    /** Payments submitted but not yet verified. When only the flag is known (no itemized rows), the amount the reservation recorded with the submission. */
    public static double pendingSubmittedOf(@NonNull Booking booking) {
        List<Booking.PaymentRecord> rows = booking.getPaymentHistory();
        if (rows != null && !rows.isEmpty()) {
            double sum = 0;
            for (Booking.PaymentRecord row : rows) {
                if (row != null && isPending(row.status)) sum += MoneyFormat.parse(row.amount);
            }
            return sum;
        }
        if (booking.isPaymentPendingVerification() && booking.getRequiredPaymentAmount() != null) {
            return Math.max(0, booking.getRequiredPaymentAmount());
        }
        return 0;
    }

    private static boolean isCompleted(@Nullable String paymentStatus) {
        return paymentStatus != null && "completed".equalsIgnoreCase(paymentStatus.trim());
    }

    private static boolean isPending(@Nullable String paymentStatus) {
        return paymentStatus != null && "pending".equalsIgnoreCase(paymentStatus.trim());
    }

    private static boolean isCancelled(@Nullable String status) {
        if (status == null) return false;
        String s = status.trim();
        return s.equalsIgnoreCase("cancelled") || s.equalsIgnoreCase("canceled");
    }

    private static boolean isRejected(@Nullable String status) {
        return status != null && status.trim().equalsIgnoreCase("rejected");
    }

    // ---- Presentation (resource ids only) ----

    /** How a Status looks. The same icon/colors are used for the list badge, the detail header, the receipt badge and the notification pill. */
    public static final class Style {
        /** Short badge text - "Paid", "Partially Paid", ... (badges show it upper-cased). */
        @StringRes public final int labelRes;
        @DrawableRes public final int iconRes;
        @ColorRes public final int bgColorRes;
        @ColorRes public final int fgColorRes;

        Style(@StringRes int labelRes, @DrawableRes int iconRes, @ColorRes int bgColorRes, @ColorRes int fgColorRes) {
            this.labelRes = labelRes;
            this.iconRes = iconRes;
            this.bgColorRes = bgColorRes;
            this.fgColorRes = fgColorRes;
        }
    }

    /**
     * Amber clock = pending, green check = paid, blue half-filled disc = partially paid, gray X =
     * cancelled, red X = rejected. Each state differs in icon AND color AND label, so none of them
     * depends on color vision alone.
     */
    public static Style styleFor(@NonNull Status status) {
        switch (status) {
            case PAID:
                return new Style(R.string.txn_status_paid, R.drawable.ic_check_circle,
                        R.color.status_paid_bg, R.color.status_paid_fg);
            case PARTIALLY_PAID:
                return new Style(R.string.txn_status_partially_paid, R.drawable.ic_status_partial,
                        R.color.status_partial_bg, R.color.status_partial_fg);
            case CANCELLED:
                return new Style(R.string.txn_status_cancelled, R.drawable.ic_close,
                        R.color.status_cancelled_bg, R.color.status_cancelled_fg);
            case REJECTED:
                return new Style(R.string.txn_status_rejected, R.drawable.ic_close,
                        R.color.status_rejected_bg, R.color.status_rejected_fg);
            case PENDING:
            default:
                return new Style(R.string.txn_status_pending, R.drawable.ic_clock,
                        R.color.status_pending_bg, R.color.status_pending_fg);
        }
    }

    /** The longer headline for the detail screen / receipt header - e.g. "Payment Awaiting Verification". */
    @StringRes
    public static int headlineRes(@NonNull Summary summary) {
        switch (summary.status) {
            case PAID:
                return R.string.txn_headline_paid;
            case PARTIALLY_PAID:
                return R.string.txn_headline_partially_paid;
            case CANCELLED:
                return R.string.txn_headline_cancelled;
            case REJECTED:
                return R.string.txn_headline_rejected;
            case PENDING:
            default:
                return summary.hasPendingPayment ? R.string.txn_headline_pending_verification : R.string.txn_headline_pending_payment;
        }
    }
}
