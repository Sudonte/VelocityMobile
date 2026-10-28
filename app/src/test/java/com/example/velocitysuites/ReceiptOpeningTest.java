package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;
import com.google.gson.Gson;

import org.junit.Test;

/**
 * Receipt 2 (and the others) must open for every kind of check-in: no payment, a part payment, a full payment, a promo or no promo,
 * several date ranges, and a receipt issued by 1.68.0 whose promo and ID discount were stored as objects instead of amounts (which
 * made the whole receipt fail with "Something went wrong on our side").
 */
public class ReceiptOpeningTest {

    private static final String STAY = "{\"position\":1,\"check_in\":\"2026-10-28\",\"check_out\":\"2026-10-29\",\"status\":\"checked_in\",\"nights\":1,\"rooms\":[],\"amenities\":[],\"subtotal\":3500}";

    /** A Receipt 2 payload as the server sends it; {@code promo}/{@code discount} are raw JSON (a number, or the 1.68.0 object). */
    private static String receipt2(double paidNow, double totalPaid, double total, double balance, String promo, String discount, String stays) {
        return "{\"receipt\":{"
                + "\"receipt_type\":\"CHECKIN_SETTLEMENT_RECEIPT\",\"receipt_title\":\"Check-in\",\"receipt_stage\":2,\"receipt_number\":\"SR-20261028-000154\","
                + "\"booking_id\":596,\"reservation_id\":640,\"guest_account_name\":\"Test Guest\",\"representative_name\":\"Test Guest\",\"room_type\":\"Deluxe\","
                + "\"room_lines\":[],\"stays\":[" + stays + "],\"check_in\":\"2026-10-28T00:00:00+08:00\",\"check_out\":\"2026-10-29T00:00:00+08:00\","
                + "\"number_of_nights\":1,\"stay_bill\":null,\"assigned_room_numbers\":[\"101\"],\"adults\":1,\"children\":0,"
                + "\"checkin_settlement\":{\"settlement_type\":\"partial_at_checkin\",\"amount_settled\":" + paidNow + ",\"payment_method\":\"cash\",\"change_returned\":0,"
                + "\"total_amount\":" + total + ",\"total_paid_before\":0,\"total_paid\":" + totalPaid + ",\"balance\":" + balance + ","
                + "\"discount\":{\"requested\":true,\"status\":\"approved\",\"name\":\"PWD\",\"amount\":700,\"rejection_reason\":null},"
                + "\"promo\":{\"id\":9,\"name\":null,\"amount\":0},\"settled_at\":\"2026-10-28T07:04:48+00:00\",\"verified_by\":\"Desk\"},"
                + "\"payment_summary\":{\"grand_total\":" + total + ",\"total_amount_paid\":" + totalPaid + ",\"remaining_balance\":" + balance + ",\"payment_status\":\"PARTIALLY_PAID\","
                + "\"payment_percentage\":100,\"official_receipt_available\":false,\"discount\":700,\"promo_discount\":0},"
                + "\"payment_transactions\":[],\"anchor_payment\":null,\"issued_at\":\"2026-10-28T07:04:48+00:00\","
                + "\"highlight\":{\"kind\":\"checkin_payment\",\"payment_method\":\"cash\",\"previous_payments\":0,\"amount_paid_now\":" + paidNow + ","
                + "\"total_amount\":" + total + ",\"total_paid\":" + totalPaid + ",\"remaining_balance\":" + balance + ",\"amount_to_pay\":" + balance + ","
                + "\"amount_to_pay_note\":\"Due at check-out\",\"promo\":" + promo + ",\"discount\":" + discount + "},"
                + "\"receipt_numbers\":[],\"transaction_history\":[]}}";
    }

    private static ReceiptDetail open(String json) {
        ReceiptDetail detail = ApiMapper.toReceiptDetailOrNull(new Gson().fromJson(json, ReceiptDetailResponse.class));
        assertNotNull("the receipt must open", detail);
        return detail;
    }

    private static final String LEGACY_PROMO = "{\"id\":9,\"name\":null,\"amount\":0,\"verified_by\":\"Althea\",\"verified_at\":\"2026-10-28T07:04:12+00:00\"}";
    private static final String LEGACY_DISCOUNT = "{\"requested\":true,\"status\":\"approved\",\"name\":\"PWD\",\"amount\":700,\"rejection_reason\":null}";

    @Test
    public void receipt2_issuedBy168_withObjectPromoAndDiscount_stillOpens_andReadsTheAmounts() {
        ReceiptDetail d = open(receipt2(1000, 1000, 2800, 1800, LEGACY_PROMO, LEGACY_DISCOUNT, STAY));
        assertEquals(700.0, d.getHighlight().amount("discount"), 0.001);
        assertEquals(0.0, d.getHighlight().amount("promo"), 0.001);
        assertEquals(1000.0, d.getHighlight().amount("amount_paid_now"), 0.001);
    }

    @Test
    public void receipt2_afterANoPaymentCheckIn_opens_andOwesTheWholeBill() {
        ReceiptDetail d = open(receipt2(0, 0, 2800, 2800, "0", "700", STAY));
        assertEquals(0.0, d.getHighlight().amount("amount_paid_now"), 0.001);
        assertEquals(2800.0, d.getHighlight().amount("remaining_balance"), 0.001);
    }

    @Test
    public void receipt2_afterAPartPayment_opens() {
        ReceiptDetail d = open(receipt2(1000, 1000, 2800, 1800, "0", "700", STAY));
        assertEquals(1800.0, d.getHighlight().amount("remaining_balance"), 0.001);
    }

    @Test
    public void receipt2_afterAFullPayment_opens() {
        ReceiptDetail d = open(receipt2(2800, 2800, 2800, 0, "0", "700", STAY));
        assertEquals(0.0, d.getHighlight().amount("remaining_balance"), 0.001);
    }

    @Test
    public void receipt2_withAPromoApplied_andWithNone_bothOpen() {
        ReceiptDetail applied = open(receipt2(0, 0, 2100, 2100, "700", "0", STAY));
        assertEquals(700.0, applied.getHighlight().amount("promo"), 0.001);
        ReceiptDetail none = open(receipt2(0, 0, 3500, 3500, "0", "0", STAY));
        assertEquals(0.0, none.getHighlight().amount("promo"), 0.001);
        assertEquals(0.0, none.getHighlight().amount("discount"), 0.001);
    }

    @Test
    public void receipt2_ofAMultiRangeCheckIn_opens() {
        String second = STAY.replace("\"position\":1", "\"position\":2").replace("2026-10-28", "2026-11-05").replace("2026-10-29", "2026-11-06");
        ReceiptDetail d = open(receipt2(1000, 1000, 6300, 5300, "0", "700", STAY + "," + second));
        assertEquals(2, d.getStays().size());
    }

    @Test
    public void aMoneyFigureThatIsNotANumberNorAnObject_readsAsNothing_insteadOfFailingTheReceipt() {
        ReceiptDetail d = open(receipt2(0, 0, 2800, 2800, "null", "\"n/a\"", STAY));
        assertTrue(d.getHighlight().amount("discount") == 0.0);
    }
}
