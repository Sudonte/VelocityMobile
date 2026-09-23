package com.example.velocitysuites.network;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.PaymentPercentageUtil;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.network.dto.DirectBookingResponseDto;
import com.example.velocitysuites.network.dto.PaymentSummaryDto;
import com.example.velocitysuites.network.dto.PaymentTransactionDto;
import com.example.velocitysuites.network.dto.ReceiptDetailDto;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;
import com.example.velocitysuites.network.dto.ReceiptSummaryDto;
import com.example.velocitysuites.network.dto.ReservationDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM coverage for the Phase 3 Android integration of the backend's
 * payment_summary/payment_transactions/receipts contract (see
 * PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md) - both the raw JSON-to-DTO wire
 * shape (Gson, no Android runtime needed) and ApiMapper's DTO-to-Booking
 * wiring, covering the exact scenarios that spec's own §21 calls out.
 */
public class PaymentReceiptHistoryMappingTest {

    private static final Gson GSON = new Gson();

    private static ReservationDto baseReservationDto() {
        ReservationDto dto = new ReservationDto();
        dto.id = 439;
        dto.room_type_id = 1;
        dto.room_type = new RoomTypeDto();
        dto.room_type.name = "Deluxe";
        dto.room_type.rate = "2500";
        dto.check_in = "2026-09-20";
        dto.check_out = "2026-09-23";
        dto.rooms_requested = 1;
        dto.adults = 2;
        dto.number_of_guests = 2;
        dto.status = "CONVERTED_TO_BOOKING";
        dto.total_amount_due = 10000.0;
        return dto;
    }

    // ---- Raw JSON wire-shape parsing (no Android runtime) ---------------

    @Test
    public void paymentSummaryDto_parsesRealBackendJsonShape() {
        String json = "{"
                + "\"grand_total\":10000.0,"
                + "\"total_amount_paid\":10000.0,"
                + "\"remaining_balance\":0.0,"
                + "\"payment_status\":\"PAID\","
                + "\"payment_percentage\":50,"
                + "\"official_receipt_available\":true"
                + "}";

        PaymentSummaryDto dto = GSON.fromJson(json, PaymentSummaryDto.class);

        assertEquals(10000.0, dto.grand_total, 0.001);
        assertEquals(10000.0, dto.total_amount_paid, 0.001);
        assertEquals(0.0, dto.remaining_balance, 0.001);
        assertEquals("PAID", dto.payment_status);
        assertEquals(Integer.valueOf(50), dto.payment_percentage);
        assertTrue(dto.official_receipt_available);
        // The exact bug this whole feature guards against - 50 must stay 50, not become 5000.
        assertEquals("50%", PaymentPercentageUtil.formatApiPercentageForDisplay(dto.payment_percentage));
    }

    @Test
    public void paymentTransactionDto_cashCheckoutRow_parsesWithoutGcashFields() {
        // A receptionist-recorded Cash checkout row legitimately has every
        // GCash/verification field null - see PaymentTransactionDto's own doc.
        String json = "{"
                + "\"id\":2,"
                + "\"payment_method\":\"cash\","
                + "\"payment_stage\":\"final\","
                + "\"transaction_type\":\"CHECKOUT_PAYMENT\","
                + "\"amount_paid\":5000.0,"
                + "\"payment_status\":\"completed\","
                + "\"verification_status\":null,"
                + "\"gcash_number\":null,"
                + "\"gcash_reference_number\":null,"
                + "\"reference_number\":\"PAY-ABC123\","
                + "\"payment_percentage\":null,"
                + "\"verified_by\":null,"
                + "\"verified_at\":null,"
                + "\"rejection_reason\":null,"
                + "\"payment_date\":\"2026-09-23T11:30:00+08:00\","
                + "\"total_paid_after_transaction\":10000.0,"
                + "\"remaining_balance_after_transaction\":0.0,"
                + "\"receipt_type\":null,"
                + "\"receipt_number\":null"
                + "}";

        PaymentTransactionDto dto = GSON.fromJson(json, PaymentTransactionDto.class);

        assertEquals("cash", dto.payment_method);
        assertEquals("CHECKOUT_PAYMENT", dto.transaction_type);
        assertNull(dto.verification_status);
        assertNull(dto.gcash_number);
        assertNull(dto.receipt_type);
        assertNull(dto.receipt_number);
        assertEquals(10000.0, dto.total_paid_after_transaction, 0.001);
    }

    @Test
    public void receiptDetailResponse_parsesTheReceiptWrapperEnvelope() {
        String json = "{\"receipt\":{"
                + "\"receipt_type\":\"PARTIAL_RECEIPT\","
                + "\"receipt_number\":\"PR-20260920-000001\","
                + "\"booking_id\":439,"
                + "\"reservation_id\":100,"
                + "\"guest_account_name\":\"Juan Dela Cruz\","
                + "\"representative_name\":\"Juan Dela Cruz\","
                + "\"room_type\":\"Deluxe\","
                + "\"room_lines\":[],"
                + "\"check_in\":\"2026-09-20\","
                + "\"check_out\":\"2026-09-23\","
                + "\"number_of_nights\":3,"
                + "\"assigned_room_numbers\":[],"
                + "\"payment_summary\":{\"grand_total\":10000.0,\"total_amount_paid\":2000.0,\"remaining_balance\":8000.0,\"payment_status\":\"PARTIALLY_PAID\",\"payment_percentage\":20,\"official_receipt_available\":true},"
                + "\"payment_transactions\":[],"
                + "\"anchor_payment\":{\"amount_paid\":2000.0,\"payment_method\":\"gcash\",\"payment_percentage\":20,\"gcash_number\":\"09171234567\",\"gcash_reference_number\":\"REF001\",\"verified_at\":\"2026-09-20T08:00:00+08:00\",\"verified_by\":\"Jane\"},"
                + "\"issued_at\":\"2026-09-20T08:00:00+08:00\""
                + "}}";

        ReceiptDetailResponse response = GSON.fromJson(json, ReceiptDetailResponse.class);

        assertNotNull(response.receipt);
        assertEquals("PARTIAL_RECEIPT", response.receipt.receipt_type);
        assertEquals("PR-20260920-000001", response.receipt.receipt_number);
        assertEquals(Long.valueOf(100), response.receipt.reservation_id);
        assertNotNull(response.receipt.anchor_payment);
        assertEquals("09171234567", response.receipt.anchor_payment.gcash_number);
    }

    // ---- Scenario A: 20% PR + final Cash checkout + OR -------------------

    @Test
    public void scenarioA_partialGcashThenCashCheckout_mapsCorrectlyIntoBooking() {
        ReservationDto dto = baseReservationDto();

        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.grand_total = 10000.0;
        summary.total_amount_paid = 10000.0;
        summary.remaining_balance = 0.0;
        summary.payment_status = "PAID";
        summary.payment_percentage = 20;
        summary.official_receipt_available = true;
        dto.payment_summary = summary;

        PaymentTransactionDto partial = new PaymentTransactionDto();
        partial.id = 1;
        partial.payment_method = "gcash";
        partial.payment_stage = "deposit";
        partial.transaction_type = "PARTIAL_PAYMENT";
        partial.amount_paid = 2000.0;
        partial.payment_status = "completed";
        partial.verification_status = "verified";
        partial.gcash_number = "09171234567";
        partial.gcash_reference_number = "REF001";
        partial.payment_percentage = 20;
        partial.total_paid_after_transaction = 2000.0;
        partial.remaining_balance_after_transaction = 8000.0;
        partial.receipt_type = "PARTIAL_RECEIPT";
        partial.receipt_number = "PR-20260920-000001";

        PaymentTransactionDto checkout = new PaymentTransactionDto();
        checkout.id = 2;
        checkout.payment_method = "cash";
        checkout.payment_stage = "final";
        checkout.transaction_type = "CHECKOUT_PAYMENT";
        checkout.amount_paid = 8000.0;
        checkout.payment_status = "completed";
        checkout.total_paid_after_transaction = 10000.0;
        checkout.remaining_balance_after_transaction = 0.0;
        dto.payment_transactions = Arrays.asList(partial, checkout);

        ReceiptSummaryDto pr = new ReceiptSummaryDto();
        pr.receipt_number = "PR-20260920-000001";
        pr.receipt_type = "PARTIAL_RECEIPT";
        pr.status = "VERIFIED";
        pr.amount = 2000.0;
        pr.payment_percentage = 20;

        ReceiptSummaryDto or = new ReceiptSummaryDto();
        or.receipt_number = "OR-20260923-000082";
        or.receipt_type = "OFFICIAL_RECEIPT";
        or.status = "PAID";
        or.amount = 10000.0;
        or.payment_percentage = 100;
        dto.receipts = Arrays.asList(pr, or);

        Booking booking = ApiMapper.toBooking(dto);

        assertTrue(booking.hasAuthoritativePaymentSummary());
        assertEquals(10000.0, booking.getEffectiveTotalAmountPaid(), 0.001);
        assertEquals(0.0, booking.getEffectiveRemainingBalance(), 0.001);
        assertTrue(booking.isOfficialReceiptAvailable());
        assertEquals(2, booking.getPaymentTransactions().size());
        assertEquals(2, booking.getReceipts().size());
        // Both PR and OR coexist, distinctly - see §9.
        assertNotNull(booking.findReceipt("PR-20260920-000001"));
        assertNotNull(booking.findReceipt("OR-20260923-000082"));
        assertEquals("20%", PaymentPercentageUtil.formatApiPercentageForDisplay(booking.getPaymentSummary().paymentPercentage));
    }

    // ---- Scenario B: 50% PR + 50% Cash checkout ---------------------------

    @Test
    public void scenarioB_fiftyPercentDisplaysAsFiftyPercent_notFiveThousandPercent() {
        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.payment_percentage = 50;

        Booking.PaymentSummary mapped = invokeToPaymentSummary(summary);

        assertEquals(Integer.valueOf(50), mapped.paymentPercentage);
        assertEquals("50%", PaymentPercentageUtil.formatApiPercentageForDisplay(mapped.paymentPercentage));
    }

    @Test
    public void scenarioB_bothTransactionsParseAndBothReceiptsCoexist() {
        ReservationDto dto = baseReservationDto();

        PaymentTransactionDto partial = new PaymentTransactionDto();
        partial.id = 1;
        partial.payment_method = "gcash";
        partial.payment_stage = "deposit";
        partial.amount_paid = 5000.0;
        partial.payment_percentage = 50;
        partial.receipt_type = "PARTIAL_RECEIPT";
        partial.receipt_number = "PR-20260920-000001";

        PaymentTransactionDto checkout = new PaymentTransactionDto();
        checkout.id = 2;
        checkout.payment_method = "cash";
        checkout.payment_stage = "final";
        checkout.amount_paid = 5000.0;
        dto.payment_transactions = Arrays.asList(partial, checkout);

        ReceiptSummaryDto pr = new ReceiptSummaryDto();
        pr.receipt_number = "PR-20260920-000001";
        pr.receipt_type = "PARTIAL_RECEIPT";
        ReceiptSummaryDto or = new ReceiptSummaryDto();
        or.receipt_number = "OR-20260923-000082";
        or.receipt_type = "OFFICIAL_RECEIPT";
        dto.receipts = Arrays.asList(pr, or);

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals(2, booking.getPaymentTransactions().size());
        assertEquals(2, booking.getReceipts().size());
    }

    // ---- Scenario C: 100% pre-checkout GCash - FR, never Official -------

    @Test
    public void scenarioC_fullPrecheckoutPayment_isFullPaymentReceiptNotOfficial_beforeCheckout() {
        ReservationDto dto = baseReservationDto();

        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.grand_total = 10000.0;
        summary.total_amount_paid = 10000.0;
        summary.remaining_balance = 0.0;
        summary.payment_status = "PAID";
        summary.payment_percentage = 100;
        summary.official_receipt_available = false; // checkout has NOT happened yet
        dto.payment_summary = summary;

        ReceiptSummaryDto fr = new ReceiptSummaryDto();
        fr.receipt_number = "FR-20260920-000001";
        fr.receipt_type = "FULL_PAYMENT_RECEIPT";
        fr.status = "VERIFIED";
        fr.amount = 10000.0;
        fr.payment_percentage = 100;
        dto.receipts = Arrays.asList(fr);

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals(0.0, booking.getEffectiveRemainingBalance(), 0.001);
        // Remaining balance is already zero, but Official Receipt must NOT
        // be inferred from that - only from the backend's own flag.
        assertFalse(booking.isOfficialReceiptAvailable());
        Booking.ReceiptSummary receipt = booking.findReceipt("FR-20260920-000001");
        assertNotNull(receipt);
        assertEquals("FULL_PAYMENT_RECEIPT", receipt.receiptType);
    }

    @Test
    public void scenarioC_afterCheckout_frStillExistsAlongsideOr() {
        ReservationDto dto = baseReservationDto();

        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.official_receipt_available = true; // checkout now complete
        dto.payment_summary = summary;

        ReceiptSummaryDto fr = new ReceiptSummaryDto();
        fr.receipt_number = "FR-20260920-000001";
        fr.receipt_type = "FULL_PAYMENT_RECEIPT";
        ReceiptSummaryDto or = new ReceiptSummaryDto();
        or.receipt_number = "OR-20260923-000082";
        or.receipt_type = "OFFICIAL_RECEIPT";
        dto.receipts = Arrays.asList(fr, or);

        Booking booking = ApiMapper.toBooking(dto);

        assertNotNull(booking.findReceipt("FR-20260920-000001"));
        assertNotNull(booking.findReceipt("OR-20260923-000082"));
        assertTrue(booking.isOfficialReceiptAvailable());
    }

    // ---- Scenario D: Reservation converted to Booking ---------------------

    @Test
    public void scenarioD_convertedReservation_toHistoricalReservation_carriesPaymentHistoryOver() {
        ReservationDto dto = baseReservationDto();
        dto.booking = new com.example.velocitysuites.network.dto.BookingDto();
        dto.booking.id = 250;
        dto.booking.booking_status = "COMPLETED_BOOKING";

        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.total_amount_paid = 10000.0;
        summary.official_receipt_available = true;
        dto.payment_summary = summary;

        PaymentTransactionDto deposit = new PaymentTransactionDto();
        deposit.id = 1;
        deposit.reference_number = "REF001";
        dto.payment_transactions = Arrays.asList(deposit);

        ReceiptSummaryDto or = new ReceiptSummaryDto();
        or.receipt_number = "OR-20260923-000082";
        or.receipt_type = "OFFICIAL_RECEIPT";
        dto.receipts = Arrays.asList(or);

        Booking historical = ApiMapper.toHistoricalReservation(dto);

        assertEquals("250", historical.getConvertedBookingId());
        assertTrue(historical.hasAuthoritativePaymentSummary());
        assertEquals(10000.0, historical.getEffectiveTotalAmountPaid(), 0.001);
        assertEquals(1, historical.getPaymentTransactions().size());
        assertNotNull(historical.findReceipt("OR-20260923-000082"));
    }

    // ---- Scenario E: Rejected payment --------------------------------------

    @Test
    public void scenarioE_rejectedPayment_parsesButNeverBecomesAReceipt() {
        ReservationDto dto = baseReservationDto();

        PaymentTransactionDto rejected = new PaymentTransactionDto();
        rejected.id = 1;
        rejected.payment_method = "gcash";
        rejected.payment_status = "rejected";
        rejected.rejection_reason = "Receipt image did not match the declared amount.";
        rejected.amount_paid = 3000.0;
        rejected.receipt_type = null; // backend never issues a receipt for a rejected payment
        rejected.receipt_number = null;
        dto.payment_transactions = Arrays.asList(rejected);
        dto.receipts = new ArrayList<>(); // backend's receiptsList() omits it entirely

        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.total_amount_paid = 0.0; // Android must trust this, never recompute from the rejected row
        dto.payment_summary = summary;

        Booking booking = ApiMapper.toBooking(dto);

        assertEquals(1, booking.getPaymentTransactions().size());
        assertEquals("rejected", booking.getPaymentTransactions().get(0).paymentStatus);
        assertNull(booking.getPaymentTransactions().get(0).receiptType);
        assertTrue(booking.getReceipts().isEmpty());
        // Authoritative total stays whatever the backend said - 0, not
        // Android summing the rejected row's own amount_paid itself.
        assertEquals(0.0, booking.getEffectiveTotalAmountPaid(), 0.001);
    }

    // ---- Backward compatibility: payment_summary absent entirely ----------

    @Test
    public void olderResponseWithNoPaymentSummary_fallsBackSafelyWithoutCrashing() {
        ReservationDto dto = baseReservationDto();
        // payment_summary/payment_transactions/receipts all left null, as a
        // response from before this feature deployed would arrive.

        Booking booking = ApiMapper.toBooking(dto);

        assertFalse(booking.hasAuthoritativePaymentSummary());
        assertNull(booking.getPaymentSummary());
        assertTrue(booking.getPaymentTransactions().isEmpty());
        assertTrue(booking.getReceipts().isEmpty());
        // Falls back to the legacy fields without crashing.
        assertEquals(booking.getAmountPaid(), booking.getEffectiveTotalAmountPaid(), 0.001);
        assertEquals(booking.isStaffVerified(), booking.isOfficialReceiptAvailable());
    }

    @Test
    public void directBookingResponse_withNoPaymentSummary_fallsBackSafely() {
        DirectBookingResponseDto dto = new DirectBookingResponseDto();
        dto.id = 555;
        dto.room_type = new RoomTypeDto();
        dto.room_type.name = "Suite";
        dto.check_in = "2026-09-20";
        dto.check_out = "2026-09-23";
        dto.total_amount_due = 8000.0;

        Booking booking = ApiMapper.toBooking(dto);

        assertFalse(booking.hasAuthoritativePaymentSummary());
        assertTrue(booking.getPaymentTransactions().isEmpty());
        assertTrue(booking.getReceipts().isEmpty());
    }

    /** Reaches ApiMapper's package-private-by-convention mapping via the public toBooking() pipeline, since the helper itself is private. */
    private static Booking.PaymentSummary invokeToPaymentSummary(PaymentSummaryDto dto) {
        ReservationDto reservation = baseReservationDto();
        reservation.payment_summary = dto;
        return ApiMapper.toBooking(reservation).getPaymentSummary();
    }
}
