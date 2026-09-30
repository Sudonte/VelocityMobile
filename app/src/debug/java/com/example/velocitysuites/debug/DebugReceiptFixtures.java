package com.example.velocitysuites.debug;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingAmenity;
import com.example.velocitysuites.BookingRoom;
import com.example.velocitysuites.Notification;
import com.example.velocitysuites.ReceiptDetail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * DEBUG-ONLY static fixture data for visually previewing the Payment
 * Receipt / Transaction History / Notification screens without a live
 * backend - see PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Phase 6B. Lives
 * entirely under src/debug - this whole package does not exist in a
 * release build, so none of this data or the preview screen it feeds
 * (DebugReceiptPreviewActivity) can ever be reachable by a real user or
 * ship in a release APK/AAB, regardless of BuildConfig.DEBUG. No real
 * guest information, no production credentials - every id/name/reference
 * number here is invented for this preview only. Never touches
 * RoomRepository/ApiService/Retrofit - production data-fetching is
 * completely untouched; this is purely local, in-memory model construction
 * fed directly into the same Activities/rendering code real data would use.
 */
public final class DebugReceiptFixtures {

    private DebugReceiptFixtures() {
    }

    // ---- A. Partial Receipt ----

    public static ReceiptDetail partialReceipt() {
        Booking.PaymentTransactionRecord tx = new Booking.PaymentTransactionRecord(
                501L, "gcash", "deposit", "PARTIAL_PAYMENT",
                5000.0, "completed", "verified",
                "09171234567", "4136202609205", "4136202609205", 50,
                "Maria Santos", "2026-09-20T14:30:00+08:00",
                null, "2026-09-20T14:15:00+08:00",
                5000.0, 5000.0,
                "PARTIAL_RECEIPT", "PR-20260920-000501"
        );
        Booking.PaymentSummary summary = new Booking.PaymentSummary(
                10000.0, 5000.0, 5000.0, "PARTIALLY_PAID", 50, false, 0);
        ReceiptDetail.AnchorPayment anchor = new ReceiptDetail.AnchorPayment(
                5000.0, "gcash", 50, "09171234567", "4136202609205",
                "2026-09-20T14:30:00+08:00", "Maria Santos");

        return new ReceiptDetail("PARTIAL_RECEIPT", "PR-20260920-000501", "250", null,
                "Juan Dela Cruz", "Juan Dela Cruz", "Deluxe Room", deluxeRoomLines(),
                "Sep 25, 2026", "Sep 27, 2026", 2,
                Arrays.asList("204"), 2, 0, summary, Arrays.asList(tx), anchor,
                "2026-09-20T14:30:00+08:00");
    }

    // ---- B. Full Pre-Checkout Payment Receipt (never Partial, never Official) ----

    public static ReceiptDetail fullPaymentReceipt() {
        Booking.PaymentTransactionRecord tx = new Booking.PaymentTransactionRecord(
                502L, "gcash", "final", "FULL_PAYMENT",
                10000.0, "completed", "verified",
                "09181234567", "4136202609215", "4136202609215", 100,
                "Maria Santos", "2026-09-21T09:10:00+08:00",
                null, "2026-09-21T09:00:00+08:00",
                10000.0, 0.0,
                "FULL_PAYMENT_RECEIPT", "FR-20260921-000502"
        );
        Booking.PaymentSummary summary = new Booking.PaymentSummary(
                10000.0, 10000.0, 0.0, "PAID", 100, false, 0);
        ReceiptDetail.AnchorPayment anchor = new ReceiptDetail.AnchorPayment(
                10000.0, "gcash", 100, "09181234567", "4136202609215",
                "2026-09-21T09:10:00+08:00", "Maria Santos");

        return new ReceiptDetail("FULL_PAYMENT_RECEIPT", "FR-20260921-000502", "251", null,
                "Juan Dela Cruz", "Juan Dela Cruz", "Suite Room", suiteRoomLines(),
                "Sep 28, 2026", "Sep 30, 2026", 2,
                Arrays.asList("305"), 2, 0, summary, Arrays.asList(tx), anchor,
                "2026-09-21T09:10:00+08:00");
    }

    // ---- C. Official Receipt (live totals, complete history, Cash row has no GCash fields) ----

    public static ReceiptDetail officialReceipt() {
        Booking.PaymentTransactionRecord gcashTx = new Booking.PaymentTransactionRecord(
                501L, "gcash", "deposit", "PARTIAL_PAYMENT",
                5000.0, "completed", "verified",
                "09171234567", "4136202609205", "4136202609205", 50,
                "Maria Santos", "2026-09-20T14:30:00+08:00",
                null, "2026-09-20T14:15:00+08:00",
                5000.0, 5000.0,
                "PARTIAL_RECEIPT", "PR-20260920-000501"
        );
        Booking.PaymentTransactionRecord cashTx = new Booking.PaymentTransactionRecord(
                510L, "cash", "final", "CHECKOUT_PAYMENT",
                5000.0, "completed", null,
                null, null, "PAY-CASH00510", null,
                null, null,
                null, "2026-09-23T11:00:00+08:00",
                10000.0, 0.0,
                null, null
        );
        Booking.PaymentSummary summary = new Booking.PaymentSummary(
                10000.0, 10000.0, 0.0, "PAID", 100, true, 0);

        return new ReceiptDetail("OFFICIAL_RECEIPT", "OR-20260923-000210", "250", null,
                "Juan Dela Cruz", "Juan Dela Cruz", "Deluxe Room", deluxeRoomLines(),
                "Sep 25, 2026", "Sep 27, 2026", 2,
                Arrays.asList("204"), 2, 0, summary, Arrays.asList(gcashTx, cashTx), null,
                "2026-09-23T11:00:00+08:00");
    }

    /** The Official Receipt for bookingWithFullPaymentAndOfficial() - same shape as officialReceipt() but its own distinct receipt_number/booking, so tapping it resolves to this one, not the other OR. */
    public static ReceiptDetail officialReceiptForFullPaymentBooking() {
        Booking.PaymentTransactionRecord gcashTx = new Booking.PaymentTransactionRecord(
                502L, "gcash", "final", "FULL_PAYMENT",
                10000.0, "completed", "verified",
                "09181234567", "4136202609215", "4136202609215", 100,
                "Maria Santos", "2026-09-21T09:10:00+08:00",
                null, "2026-09-21T09:00:00+08:00",
                10000.0, 0.0,
                "FULL_PAYMENT_RECEIPT", "FR-20260921-000502"
        );
        Booking.PaymentSummary summary = new Booking.PaymentSummary(
                10000.0, 10000.0, 0.0, "PAID", 100, true, 0);

        return new ReceiptDetail("OFFICIAL_RECEIPT", "OR-20260924-000211", "251", null,
                "Juan Dela Cruz", "Juan Dela Cruz", "Suite Room", suiteRoomLines(),
                "Sep 28, 2026", "Sep 30, 2026", 2,
                Arrays.asList("305"), 2, 0, summary, Arrays.asList(gcashTx), null,
                "2026-09-24T10:00:00+08:00");
    }

    // ---- C2. Receipt Payment Summary stress cases (rooms/amenities breakdown) ----
    // Every figure below is arithmetically consistent on purpose: Rooms Total + Amenities Total
    // - Discount == the receipt's Grand Total, so the preview also visually proves the math.

    /** Long room/amenity names, two room types, two amenities AND a discount - the layout stress case (wrapping names, larger amounts, the deduction line). Rooms 49,500.00 + amenities 4,300.00 - discount 5,380.00 = Grand Total 48,420.00. */
    public static ReceiptDetail officialReceiptWithDiscountAndLongNames() {
        Booking.PaymentTransactionRecord cashTx = new Booking.PaymentTransactionRecord(
                511L, "cash", "final", "CHECKOUT_PAYMENT",
                48420.0, "completed", null,
                null, null, "PAY-CASH00511", null,
                null, null,
                null, "2026-09-28T11:00:00+08:00",
                48420.0, 0.0,
                null, null
        );
        Booking.PaymentSummary summary = new Booking.PaymentSummary(
                48420.0, 48420.0, 0.0, "PAID", 100, true, 5380.0);
        List<BookingRoom> rooms = Arrays.asList(
                new BookingRoom("3", "Presidential Executive Ocean-View Penthouse Suite", 1, 12500.0, 3, 37500.0, null),
                new BookingRoom("1", "Deluxe Room", 2, 2000.0, 3, 12000.0, null));

        return new ReceiptDetail("OFFICIAL_RECEIPT", "OR-20260928-000212", "252", null,
                "Juan Dela Cruz", "Juan Dela Cruz", "Presidential Executive Ocean-View Penthouse Suite", rooms,
                "Sep 25, 2026", "Sep 28, 2026", 3,
                Arrays.asList("901", "204", "205"), 4, 2, summary, Arrays.asList(cashTx), null,
                "2026-09-28T11:00:00+08:00");
    }

    public static List<BookingAmenity> longNamesAmenities() {
        return Arrays.asList(
                new BookingAmenity("7", "Complimentary Welcome Champagne & Chocolate Basket for Two", 2, 1250.0, 2500.0),
                new BookingAmenity("8", "Airport Transfer (Round Trip)", 1, 1800.0, 1800.0));
    }

    /** The receipt loaded but its amenity lines could not be resolved: the summary must say so and must NOT claim "No amenities selected". Grand Total 5,650.00 vs rooms 5,000.00 leaves 650.00, which shows as Other Charges instead of vanishing. */
    public static ReceiptDetail officialReceiptAmenitiesUnavailable() {
        Booking.PaymentTransactionRecord cashTx = new Booking.PaymentTransactionRecord(
                512L, "cash", "final", "CHECKOUT_PAYMENT",
                5650.0, "completed", null,
                null, null, "PAY-CASH00512", null,
                null, null,
                null, "2026-09-29T11:00:00+08:00",
                5650.0, 0.0,
                null, null
        );
        Booking.PaymentSummary summary = new Booking.PaymentSummary(
                5650.0, 5650.0, 0.0, "PAID", 100, true, 0);

        return new ReceiptDetail("OFFICIAL_RECEIPT", "OR-20260929-000213", "253", null,
                "Juan Dela Cruz", "Juan Dela Cruz", "Deluxe Room",
                Arrays.asList(new BookingRoom("1", "Deluxe Room", 1, 2500.0, 2, 5000.0, null)),
                "Sep 27, 2026", "Sep 29, 2026", 2,
                Arrays.asList("206"), 2, 0, summary, Arrays.asList(cashTx), null,
                "2026-09-29T11:00:00+08:00");
    }

    /** Deluxe Room x2 at 2,000/night x 2 nights = 8,000.00 - the Partial Receipt and Official Receipt for booking 250, which also has amenities (2,000.00): Grand Total 10,000.00. */
    private static List<BookingRoom> deluxeRoomLines() {
        return Arrays.asList(new BookingRoom("1", "Deluxe Room", 2, 2000.0, 2, 8000.0, null));
    }

    /** Suite Room x1 at 5,000/night x 2 nights = 10,000.00, NO amenities - the "without amenities" case: Grand Total 10,000.00. */
    private static List<BookingRoom> suiteRoomLines() {
        return Arrays.asList(new BookingRoom("2", "Suite Room", 1, 5000.0, 2, 10000.0, null));
    }

    /** Booking 250's amenities: Breakfast Package x2 at 500 (1,000.00) + Airport Transfer x1 at 1,000 (1,000.00) = 2,000.00. */
    public static List<BookingAmenity> deluxeBookingAmenities() {
        return Arrays.asList(
                new BookingAmenity("5", "Breakfast Package", 2, 500.0, 1000.0),
                new BookingAmenity("6", "Airport Transfer", 1, 1000.0, 1000.0));
    }

    /** Every ReceiptDetail fixture, keyed by its own receipt_number - see PaymentReceiptActivity#debugPreviewFixtures's own doc for why lookup must be keyed like this rather than a single last-wins field. */
    public static java.util.Map<String, ReceiptDetail> allReceiptDetails() {
        java.util.Map<String, ReceiptDetail> map = new java.util.HashMap<>();
        for (ReceiptDetail r : Arrays.asList(partialReceipt(), fullPaymentReceipt(), officialReceipt(),
                officialReceiptForFullPaymentBooking(), officialReceiptWithDiscountAndLongNames(),
                officialReceiptAmenitiesUnavailable())) {
            map.put(r.getReceiptNumber(), r);
        }
        return map;
    }

    /**
     * The amenity lines each fixture's transaction has, keyed by receipt_number - see
     * PaymentReceiptActivity#debugPreviewAmenities' own doc. A receipt with no entry is
     * previewed as having NO amenities (the Full Payment receipt and the Suite Official
     * Receipt); an entry mapped to null is previewed as "amenities could not be resolved".
     */
    public static java.util.Map<String, List<BookingAmenity>> allReceiptAmenities() {
        java.util.Map<String, List<BookingAmenity>> map = new java.util.HashMap<>();
        map.put(partialReceipt().getReceiptNumber(), deluxeBookingAmenities());
        map.put(officialReceipt().getReceiptNumber(), deluxeBookingAmenities());
        map.put(officialReceiptWithDiscountAndLongNames().getReceiptNumber(), longNamesAmenities());
        map.put(officialReceiptAmenitiesUnavailable().getReceiptNumber(), null);
        return map;
    }

    // ---- D. Converted Reservation -> Booking (RES-000100 -> BOOK-000250) ----
    // Two Booking fixtures exercising the exact existing "converted reservation" cross-
    // reference UI (BookingDetailsActivity's Converted Booking row / Booking's own
    // isHistoricalReservation()+getConvertedBookingId()) - not a fabricated new display.

    public static Booking convertedReservationOriginal() {
        Booking b = new Booking("100", "204", "Deluxe Room", "Deluxe",
                "Sep 25, 2026", "Sep 27, 2026", 2, 10000.0, "Converted", "Sep 18, 2026");
        b.setHasBooking(false);
        b.setDirectBooking(false);
        b.setHistoricalReservation(true);
        b.setConvertedBookingId("250");
        b.setStaffVerified(true);
        b.setAmountPaid(5000.0);
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 5000.0, 5000.0, "PARTIALLY_PAID", 50, false, 0));
        b.setPaymentTransactions(Arrays.asList(new Booking.PaymentTransactionRecord(
                501L, "gcash", "deposit", "PARTIAL_PAYMENT",
                5000.0, "completed", "verified",
                "09171234567", "4136202609205", "4136202609205", 50,
                "Maria Santos", "2026-09-20T14:30:00+08:00",
                null, "2026-09-20T14:15:00+08:00",
                5000.0, 5000.0,
                "PARTIAL_RECEIPT", "PR-20260920-000501"
        )));
        b.setReceipts(Arrays.asList(new Booking.ReceiptSummary(
                "PR-20260920-000501", "PARTIAL_RECEIPT", "VERIFIED", 5000.0, 50, "2026-09-20T14:30:00+08:00")));
        return b;
    }

    public static Booking convertedReservationBooking() {
        Booking b = new Booking("250", "204", "Deluxe Room", "Deluxe",
                "Sep 25, 2026", "Sep 27, 2026", 2, 10000.0, "Checked-Out", "Sep 20, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(false);
        b.setStaffVerified(true);
        b.setAmountPaid(10000.0);
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 10000.0, 0.0, "PAID", 100, true, 0));
        List<Booking.PaymentTransactionRecord> txs = new ArrayList<>();
        txs.add(new Booking.PaymentTransactionRecord(
                501L, "gcash", "deposit", "PARTIAL_PAYMENT",
                5000.0, "completed", "verified",
                "09171234567", "4136202609205", "4136202609205", 50,
                "Maria Santos", "2026-09-20T14:30:00+08:00",
                null, "2026-09-20T14:15:00+08:00",
                5000.0, 5000.0,
                "PARTIAL_RECEIPT", "PR-20260920-000501"
        ));
        txs.add(new Booking.PaymentTransactionRecord(
                510L, "cash", "final", "CHECKOUT_PAYMENT",
                5000.0, "completed", null,
                null, null, "PAY-CASH00510", null,
                null, null,
                null, "2026-09-23T11:00:00+08:00",
                10000.0, 0.0,
                null, null
        ));
        b.setPaymentTransactions(txs);
        b.setReceipts(Arrays.asList(
                new Booking.ReceiptSummary("PR-20260920-000501", "PARTIAL_RECEIPT", "VERIFIED", 5000.0, 50, "2026-09-20T14:30:00+08:00"),
                new Booking.ReceiptSummary("OR-20260923-000210", "OFFICIAL_RECEIPT", "PAID", 10000.0, 100, "2026-09-23T11:00:00+08:00")
        ));
        return b;
    }

    // ---- E. Multiple-receipt Transaction History fixtures ----

    /** PR + OR - reuses the converted-reservation Booking fixture above (already carries both). */
    public static Booking bookingWithPartialAndOfficial() {
        return convertedReservationBooking();
    }

    /** FR + OR - a 100%-pre-checkout booking whose Official Receipt was later issued at actual checkout. */
    public static Booking bookingWithFullPaymentAndOfficial() {
        Booking b = new Booking("251", "305", "Suite Room", "Suite",
                "Sep 28, 2026", "Sep 30, 2026", 2, 10000.0, "Checked-Out", "Sep 21, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        b.setStaffVerified(true);
        b.setAmountPaid(10000.0);
        b.setPaymentSummary(new Booking.PaymentSummary(10000.0, 10000.0, 0.0, "PAID", 100, true, 0));
        b.setPaymentTransactions(Arrays.asList(new Booking.PaymentTransactionRecord(
                502L, "gcash", "final", "FULL_PAYMENT",
                10000.0, "completed", "verified",
                "09181234567", "4136202609215", "4136202609215", 100,
                "Maria Santos", "2026-09-21T09:10:00+08:00",
                null, "2026-09-21T09:00:00+08:00",
                10000.0, 0.0,
                "FULL_PAYMENT_RECEIPT", "FR-20260921-000502"
        )));
        b.setReceipts(Arrays.asList(
                new Booking.ReceiptSummary("FR-20260921-000502", "FULL_PAYMENT_RECEIPT", "VERIFIED", 10000.0, 100, "2026-09-21T09:10:00+08:00"),
                new Booking.ReceiptSummary("OR-20260924-000211", "OFFICIAL_RECEIPT", "PAID", 10000.0, 100, "2026-09-24T10:00:00+08:00")
        ));
        return b;
    }

    // ---- F. Notification fixtures ----

    public static Notification partialReceiptNotification() {
        return new Notification("9001", "Payment Verified",
                "Your GCash payment of ₱5,000.00 for Deluxe Room has been verified. Thank you! Your Partial Payment Receipt (PR-20260920-000501) is now available.",
                "20 minutes ago", Notification.TYPE_PAYMENT, false, "250", null,
                "Sep 20, 2026 at 2:30 PM", "PR-20260920-000501", "PARTIAL_RECEIPT");
    }

    public static Notification fullPaymentReceiptNotification() {
        return new Notification("9002", "Payment Verified",
                "Your GCash payment of ₱10,000.00 for Suite Room has been verified. Thank you! Your Payment Receipt (FR-20260921-000502) is now available.",
                "1 day ago", Notification.TYPE_PAYMENT, false, "251", null,
                "Sep 21, 2026 at 9:10 AM", "FR-20260921-000502", "FULL_PAYMENT_RECEIPT");
    }

    public static Notification officialReceiptNotification() {
        return new Notification("9003", "Payment Complete",
                "Your payment is complete. Thank you for staying with us! Your Official Payment Receipt (OR-20260923-000210) is now available.",
                "2 days ago", Notification.TYPE_PAYMENT, false, "250", null,
                "Sep 23, 2026 at 11:00 AM", "OR-20260923-000210", "OFFICIAL_RECEIPT");
    }

    /** No structured receipt_number - exercises the pre-Phase-5 legacy fallback path. */
    public static Notification legacyNotification() {
        return new Notification("9004", "Booking Confirmed",
                "Great news! Your reservation for Deluxe Room has been confirmed.",
                "5 days ago", Notification.TYPE_BOOKING, false, "250", null,
                "Sep 18, 2026 at 10:00 AM");
    }
}
