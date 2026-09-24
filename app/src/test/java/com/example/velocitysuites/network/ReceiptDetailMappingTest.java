package com.example.velocitysuites.network;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.PaymentPercentageUtil;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.ReceiptTypeMapper;
import com.example.velocitysuites.network.dto.PaymentSummaryDto;
import com.example.velocitysuites.network.dto.PaymentTransactionDto;
import com.example.velocitysuites.network.dto.ReceiptDetailDto;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM coverage for ApiMapper#toReceiptDetail() (GET guest/receipts/{receiptNumber})
 * and the presentation-layer contracts PaymentReceiptActivity's rendering
 * depends on (PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Phase 4 §33).
 * Deliberately does NOT instantiate PaymentReceiptActivity itself - this
 * project has no Robolectric, and Activity/Intent/Context are framework
 * classes not usable in a plain JVM unit test here (see the Phase 4
 * report's own "Unit test results" section for this explicitly-acknowledged
 * limitation) - every field/behavior this Activity's rendering reads is
 * instead verified at the DTO->ReceiptDetail mapping layer, which is where
 * the real "never infer, never recompute" logic actually lives.
 */
public class ReceiptDetailMappingTest {

    private static ReceiptDetailResponse baseResponse() {
        ReceiptDetailResponse response = new ReceiptDetailResponse();
        ReceiptDetailDto dto = new ReceiptDetailDto();
        dto.receipt_type = "PARTIAL_RECEIPT";
        dto.receipt_number = "PR-20260920-000001";
        dto.booking_id = 439;
        dto.room_lines = new ArrayList<>();
        dto.assigned_room_numbers = new ArrayList<>();
        dto.payment_transactions = new ArrayList<>();
        response.receipt = dto;
        return response;
    }

    @Test
    public void extraReceiptNumberConstant_isStable() {
        // Locks the exact Intent-extra key contract notification deep-links
        // and any future caller must use - see EXTRA_RECEIPT_NUMBER's own
        // doc. Doesn't instantiate Intent/Context (framework classes) - just
        // asserts the static String constant's literal value.
        assertEquals("EXTRA_RECEIPT_NUMBER", PaymentReceiptActivity.EXTRA_RECEIPT_NUMBER);
    }

    @Test
    public void nullableReservationId_staysNullForADirectBooking() {
        ReceiptDetailResponse response = baseResponse();
        response.receipt.reservation_id = null;

        ReceiptDetail detail = ApiMapper.toReceiptDetail(response);

        assertNull(detail.getReservationId());
    }

    @Test
    public void reservationId_presentForAConvertedBooking() {
        ReceiptDetailResponse response = baseResponse();
        response.receipt.reservation_id = 100L;

        ReceiptDetail detail = ApiMapper.toReceiptDetail(response);

        assertEquals("100", detail.getReservationId());
    }

    @Test
    public void emptyPaymentTransactions_mapsToEmptyListNeverNull() {
        ReceiptDetailResponse response = baseResponse();
        response.receipt.payment_transactions = new ArrayList<>();

        ReceiptDetail detail = ApiMapper.toReceiptDetail(response);

        assertTrue(detail.getPaymentTransactions().isEmpty());
    }

    @Test
    public void cashCheckoutTransaction_hasNoGcashFieldsAfterMapping() {
        ReceiptDetailResponse response = baseResponse();
        PaymentTransactionDto cash = new PaymentTransactionDto();
        cash.id = 2;
        cash.payment_method = "cash";
        cash.transaction_type = "CHECKOUT_PAYMENT";
        cash.amount_paid = 5000.0;
        cash.payment_status = "completed";
        cash.gcash_number = null;
        cash.gcash_reference_number = null;
        cash.verification_status = null;
        cash.verified_at = null;
        response.receipt.payment_transactions = Arrays.asList(cash);

        ReceiptDetail detail = ApiMapper.toReceiptDetail(response);
        Booking.PaymentTransactionRecord mapped = detail.getPaymentTransactions().get(0);

        assertEquals("cash", mapped.paymentMethod);
        assertNull(mapped.gcashNumber);
        assertNull(mapped.gcashReferenceNumber);
        assertNull(mapped.verificationStatus);
        assertNull(mapped.verifiedAt);
    }

    @Test
    public void partialReceiptSnapshot_isNotOverriddenByAnythingElseOnTheSamePayload() {
        // The exact "PR must not turn into OR's totals" guarantee - proven
        // here by asserting the mapped payment_summary is EXACTLY the
        // (deliberately partial-looking) values supplied, regardless of
        // what a live Booking might separately show.
        ReceiptDetailResponse response = baseResponse();
        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.grand_total = 10000.0;
        summary.total_amount_paid = 5000.0;
        summary.remaining_balance = 5000.0;
        summary.payment_status = "PARTIALLY_PAID";
        summary.payment_percentage = 50;
        summary.official_receipt_available = true; // an OR now ALSO exists separately
        response.receipt.payment_summary = summary;
        ReceiptDetailDto.AnchorPaymentDto anchor = new ReceiptDetailDto.AnchorPaymentDto();
        anchor.amount_paid = 5000.0;
        anchor.payment_method = "gcash";
        anchor.payment_percentage = 50;
        response.receipt.anchor_payment = anchor;

        ReceiptDetail detail = ApiMapper.toReceiptDetail(response);

        assertEquals(5000.0, detail.getPaymentSummary().totalAmountPaid, 0.001);
        assertEquals(5000.0, detail.getPaymentSummary().remainingBalance, 0.001);
        assertEquals(10000.0, detail.getPaymentSummary().grandTotal, 0.001);
        // Still correctly anchored on its own single payment - the presence
        // of official_receipt_available=true (informational) never turns
        // this into an OR-shaped payload.
        assertTrue(detail.isAnchoredOnSinglePayment());
        assertEquals(5000.0, detail.getAnchorPayment().amountPaid, 0.001);
    }

    @Test
    public void receiptTypeIsNeverInferredFromPercentageOrBalance_mapperPassesThroughExactly() {
        // A deliberately "looks fully paid" PaymentSummaryDto - the mapper
        // must never read remaining_balance/payment_percentage to decide
        // receipt_type; it only ever copies whatever string the backend
        // sent for receipt_type itself.
        ReceiptDetailResponse response = baseResponse();
        response.receipt.receipt_type = "PARTIAL_RECEIPT";
        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.remaining_balance = 0.0;
        summary.payment_percentage = 100;
        response.receipt.payment_summary = summary;

        ReceiptDetail detail = ApiMapper.toReceiptDetail(response);

        assertEquals("PARTIAL_RECEIPT", detail.getReceiptType());
        assertEquals("Partial Payment Receipt", ReceiptTypeMapper.labelForReceiptType(detail.getReceiptType()));
    }

    @Test
    public void officialReceipt_hasNoAnchorPayment_isNotAnchoredOnSinglePayment() {
        ReceiptDetailResponse response = baseResponse();
        response.receipt.receipt_type = "OFFICIAL_RECEIPT";
        response.receipt.anchor_payment = null;

        ReceiptDetail detail = ApiMapper.toReceiptDetail(response);

        assertFalse(detail.isAnchoredOnSinglePayment());
        assertEquals("Official Payment Receipt", ReceiptTypeMapper.labelForReceiptType(detail.getReceiptType()));
    }

    @Test
    public void partialReceipt_percentageFormatsCorrectly_neverFiveThousandPercent() {
        ReceiptDetailResponse response = baseResponse();
        PaymentSummaryDto summary = new PaymentSummaryDto();
        summary.payment_percentage = 50;
        response.receipt.payment_summary = summary;

        ReceiptDetail detail = ApiMapper.toReceiptDetail(response);

        assertEquals("50%", PaymentPercentageUtil.formatApiPercentageForDisplay(detail.getPaymentSummary().paymentPercentage));
    }
}
