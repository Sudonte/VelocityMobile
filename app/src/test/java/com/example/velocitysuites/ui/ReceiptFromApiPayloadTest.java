package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.ReceiptDetail;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The three receipts of one stay, built from realistic GET guest/receipts/{n} JSON bodies (the way Retrofit/Gson hand
 * them over) -> ApiMapper -> the real Payment Receipt screen. Every label/value row must carry a real value: nothing
 * blank, "N/A", "null", "NaN" or "Invalid Date" when the payload has the data.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceiptFromApiPayloadTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    /** The shared head of every payload: the stay itself (2 nights x 2,500.00 = 5,000.00). */
    private static String stay(String type, int stage, String number, String issuedAt) {
        return "\"receipt_type\":\"" + type + "\",\"receipt_number\":\"" + number + "\",\"receipt_stage\":" + stage
                + ",\"booking_id\":250,\"reservation_id\":588,\"guest_account_name\":\"Juan Dela Cruz\","
                + "\"representative_name\":\"Juan Dela Cruz\",\"room_type\":\"Deluxe Room\","
                + "\"room_lines\":[{\"room_type_id\":\"3\",\"room_type\":\"Deluxe Room\",\"quantity\":1,"
                + "\"price_per_night\":2500.0,\"nights\":2,\"subtotal\":5000.0,\"assigned_room_numbers\":[\"305\"]}],"
                + "\"check_in\":\"2026-09-25\",\"check_out\":\"2026-09-27\",\"number_of_nights\":2,"
                + "\"assigned_room_numbers\":[\"305\"],\"adults\":2,\"children\":1,\"issued_at\":\"" + issuedAt + "\"";
    }

    /** Receipt 1: a 20% GCash deposit verified in the Booking module. */
    static final String RECEIPT_1 = "{\"receipt\":{" + stay("PARTIAL_RECEIPT", 1, "PR-20260920-000501", "2026-09-20T06:15:00.000000Z") + ","
            + "\"payment_summary\":{\"grand_total\":5000.0,\"total_amount_paid\":1000.0,\"remaining_balance\":4000.0,"
            + "\"payment_status\":\"PARTIALLY_PAID\",\"payment_percentage\":20,\"official_receipt_available\":false,\"discount\":0},"
            + "\"anchor_payment\":{\"amount_paid\":1000.0,\"payment_method\":\"gcash\",\"payment_percentage\":20,"
            + "\"gcash_number\":\"09171234567\",\"gcash_reference_number\":\"1234567890123\","
            + "\"verified_at\":\"2026-09-20T06:15:00.000000Z\",\"verified_by\":\"Maria Santos\"},"
            + "\"payment_transactions\":[{\"id\":71,\"payment_method\":\"gcash\",\"payment_stage\":\"booking\","
            + "\"transaction_type\":\"PARTIAL_PAYMENT\",\"amount_paid\":1000.0,\"payment_status\":\"completed\","
            + "\"verification_status\":\"verified\",\"gcash_number\":\"09171234567\",\"gcash_reference_number\":\"1234567890123\","
            + "\"payment_percentage\":20,\"verified_by\":\"Maria Santos\",\"verified_at\":\"2026-09-20T06:15:00.000000Z\","
            + "\"payment_date\":\"2026-09-20T06:10:00.000000Z\",\"created_at\":\"2026-09-20T06:10:00.000000Z\","
            + "\"total_paid_after_transaction\":1000.0,\"remaining_balance_after_transaction\":4000.0,"
            + "\"receipt_type\":\"PARTIAL_RECEIPT\",\"receipt_number\":\"PR-20260920-000501\"}],"
            + "\"highlight\":{\"kind\":\"booking_verification\",\"payment_type\":\"partial\",\"payment_method\":\"gcash\","
            + "\"amount_paid_now\":1000.0,\"total_amount\":5000.0,\"total_paid\":1000.0,\"remaining_balance\":4000.0,"
            + "\"amount_to_pay\":4000.0,\"amount_to_pay_note\":\"Due at check-in\"}}}";

    /** Receipt 2: the remaining balance paid in cash at check-in (SR-). */
    static final String RECEIPT_2 = "{\"receipt\":{" + stay("CHECKIN_SETTLEMENT_RECEIPT", 2, "SR-20260925-000077", "2026-09-25T04:30:00.000000Z") + ","
            + "\"payment_summary\":{\"grand_total\":5000.0,\"total_amount_paid\":5000.0,\"remaining_balance\":0.0,"
            + "\"payment_status\":\"PAID\",\"payment_percentage\":100,\"official_receipt_available\":false,\"discount\":0},"
            + "\"checkin_settlement\":{\"settlement_type\":\"partial_to_full\",\"amount_settled\":4000.0,\"payment_method\":\"cash\","
            + "\"change_returned\":0.0,\"total_amount\":5000.0,\"total_paid_before\":1000.0,\"total_paid\":5000.0,\"balance\":0.0,"
            + "\"discount\":{\"requested\":false,\"status\":\"not_requested\",\"amount\":0},"
            + "\"settled_at\":\"2026-09-25T04:30:00.000000Z\",\"verified_by\":\"Maria Santos\"},"
            + "\"payment_transactions\":[{\"id\":71,\"payment_method\":\"gcash\",\"transaction_type\":\"PARTIAL_PAYMENT\","
            + "\"amount_paid\":1000.0,\"payment_status\":\"completed\",\"verification_status\":\"verified\","
            + "\"gcash_number\":\"09171234567\",\"gcash_reference_number\":\"1234567890123\",\"payment_percentage\":20,"
            + "\"verified_at\":\"2026-09-20T06:15:00.000000Z\",\"payment_date\":\"2026-09-20T06:10:00.000000Z\","
            + "\"total_paid_after_transaction\":1000.0,\"remaining_balance_after_transaction\":4000.0},"
            + "{\"id\":88,\"payment_method\":\"cash\",\"transaction_type\":\"CHECKIN_PAYMENT\",\"amount_paid\":4000.0,"
            + "\"payment_status\":\"completed\",\"verification_status\":\"verified\",\"payment_percentage\":100,"
            + "\"verified_by\":\"Maria Santos\",\"verified_at\":\"2026-09-25T04:30:00.000000Z\","
            + "\"payment_date\":\"2026-09-25T04:30:00.000000Z\",\"total_paid_after_transaction\":5000.0,"
            + "\"remaining_balance_after_transaction\":0.0,\"receipt_number\":\"SR-20260925-000077\","
            + "\"receipt_type\":\"CHECKIN_SETTLEMENT_RECEIPT\"}]}}";

    /** Receipt 3: the official receipt after check-out, with the itemized stay bill. */
    static final String RECEIPT_3 = "{\"receipt\":{" + stay("OFFICIAL_RECEIPT", 3, "OR-20260927-000310", "2026-09-27T03:00:00.000000Z") + ","
            + "\"payment_summary\":{\"grand_total\":5000.0,\"total_amount_paid\":5000.0,\"remaining_balance\":0.0,"
            + "\"payment_status\":\"PAID\",\"payment_percentage\":100,\"official_receipt_available\":true,\"discount\":0},"
            + "\"stay_bill\":{\"check_in\":\"2026-09-25T06:00:00.000000Z\",\"scheduled_check_out\":\"2026-09-27\","
            + "\"actual_check_out\":\"2026-09-27T03:00:00.000000Z\",\"scheduled_nights\":2,\"actual_nights\":2,\"extra_nights\":0,"
            + "\"room_charge\":5000.0,\"extra_nights_charge\":0,\"amenity_charge\":0,\"additional_charges_total\":0,"
            + "\"subtotal\":5000.0,\"discount\":0,\"total\":5000.0,\"total_paid\":5000.0,\"balance\":0,"
            + "\"rooms\":[{\"room_number\":\"305\",\"room_type\":\"Deluxe Room\",\"room_type_id\":\"3\",\"rate\":2500.0,"
            + "\"nights\":2,\"extra_nights\":0,\"subtotal\":5000.0,\"status\":\"checked_out\"}]},"
            + "\"payment_transactions\":[{\"id\":71,\"payment_method\":\"gcash\",\"transaction_type\":\"PARTIAL_PAYMENT\","
            + "\"amount_paid\":1000.0,\"payment_status\":\"completed\",\"verification_status\":\"verified\","
            + "\"gcash_number\":\"09171234567\",\"gcash_reference_number\":\"1234567890123\",\"payment_percentage\":20,"
            + "\"verified_at\":\"2026-09-20T06:15:00.000000Z\",\"payment_date\":\"2026-09-20T06:10:00.000000Z\","
            + "\"total_paid_after_transaction\":1000.0,\"remaining_balance_after_transaction\":4000.0},"
            + "{\"id\":99,\"payment_method\":\"cash\",\"transaction_type\":\"CHECKOUT_PAYMENT\",\"amount_paid\":4000.0,"
            + "\"cash_received\":5000.0,\"change_given\":1000.0,\"payment_status\":\"completed\",\"payment_percentage\":100,"
            + "\"payment_date\":\"2026-09-27T03:00:00.000000Z\",\"total_paid_after_transaction\":5000.0,"
            + "\"remaining_balance_after_transaction\":0.0,\"receipt_number\":\"OR-20260927-000310\","
            + "\"receipt_type\":\"OFFICIAL_RECEIPT\"}],"
            + "\"highlight\":{\"kind\":\"checkout_final\",\"room_charges\":5000.0,\"amenities_total\":0,"
            + "\"additional_charges_total\":0,\"discount\":0,\"grand_total\":5000.0,\"paid_receipt_1\":1000.0,"
            + "\"paid_receipt_2\":0,\"paid_at_checkout\":4000.0,\"total_paid\":5000.0,\"remaining_balance\":0.0}}}";

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    @After
    public void tearDown() {
        PaymentReceiptActivity.debugPreviewFixtures.clear();
        PaymentReceiptActivity.debugPreviewAmenities.clear();
    }

    private PaymentReceiptActivity open(String json) {
        ReceiptDetail detail = ApiMapper.toReceiptDetail(new Gson().fromJson(json, ReceiptDetailResponse.class));
        PaymentReceiptActivity.debugPreviewFixtures.put(detail.getReceiptNumber(), detail);
        Intent intent = PaymentReceiptActivity.newIntentForReceipt(app, detail.getReceiptNumber());
        ActivityController<PaymentReceiptActivity> controller = Robolectric.buildActivity(PaymentReceiptActivity.class, intent);
        controller.setup();
        shadowOf(Looper.getMainLooper()).idle();
        dump(detail.getReceiptNumber(), controller.get());
        return controller.get();
    }

    /** Writes every row to build/receipt-rows/NUMBER.txt so the rendered fields can be read without a device. */
    private static void dump(String number, PaymentReceiptActivity activity) {
        try {
            java.io.File dir = new java.io.File("build/receipt-rows");
            dir.mkdirs();
            String nl = System.lineSeparator();
            String lf = String.valueOf((char) 10);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> row : rows(activity).entrySet()) {
                sb.append(row.getKey()).append(" = ").append(row.getValue().replace(nl, "|").replace(lf, "|")).append(nl);
            }
            sb.append("--- all text ---").append(nl);
            for (String t : allTexts(activity.findViewById(R.id.layoutDynamicReceiptContent))) sb.append(t.replace(lf, "|")).append(nl);
            java.nio.file.Files.write(new java.io.File(dir, number + ".txt").toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) {
        }
    }

    /** Every label -> value row of the receipt, in order. */
    private static Map<String, String> rows(PaymentReceiptActivity activity) {
        Map<String, String> out = new LinkedHashMap<>();
        collectRows(activity.findViewById(R.id.layoutDynamicReceiptContent), out);
        return out;
    }

    private static void collectRows(View view, Map<String, String> out) {
        if (!(view instanceof ViewGroup) || view.getVisibility() != View.VISIBLE) return;
        ViewGroup group = (ViewGroup) view;
        // a row of the shared structure: a label and its amount, side by side
        if (group instanceof android.widget.LinearLayout && ((android.widget.LinearLayout) group).getOrientation() == android.widget.LinearLayout.HORIZONTAL
                && group.getChildCount() == 2 && group.getChildAt(0) instanceof TextView && group.getChildAt(1) instanceof TextView) {
            out.put(((TextView) group.getChildAt(0)).getText().toString(), ((TextView) group.getChildAt(1)).getText().toString());
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) collectRows(group.getChildAt(i), out);
    }

    private static List<String> allTexts(View view) {
        List<String> out = new ArrayList<>();
        gather(view, out);
        return out;
    }

    private static void gather(View view, List<String> out) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE && ((TextView) view).getText().length() > 0) {
            out.add(((TextView) view).getText().toString());
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) gather(((ViewGroup) view).getChildAt(i), out);
        }
    }

    private static void assertNoBlankField(String name, PaymentReceiptActivity activity) {
        Map<String, String> rows = rows(activity);
        assertTrue(name + " has rows", rows.size() >= 8);
        for (Map.Entry<String, String> row : rows.entrySet()) {
            String v = row.getValue().trim();
            assertFalse(name + " / " + row.getKey() + " is blank", v.isEmpty());
            for (String bad : new String[]{"N/A", "null", "NaN", "Invalid", "undefined", "—"}) {
                assertFalse(name + " / " + row.getKey() + " reads \"" + v + "\"", v.contains(bad));
            }
        }
        for (String text : allTexts(activity.findViewById(R.id.layoutDynamicReceiptContent))) {
            for (String bad : new String[]{"null", "NaN", "Invalid", "undefined"}) {
                assertFalse(name + " prints \"" + text + "\"", text.contains(bad));
            }
        }
    }

    @Test
    public void receipt1_partialGcashDeposit_hasEveryField() {
        PaymentReceiptActivity a = open(RECEIPT_1);
        assertNoBlankField("Receipt 1", a);
        Map<String, String> r = rows(a);
        List<String> all = allTexts(a.findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals("PR-20260920-000501", r.get("Payment Reference"));
        assertEquals("Reservation #588", r.get("Transaction ID"));
        assertTrue(all.toString(), all.contains("Payment verified") && all.contains("Partially Paid"));
        assertTrue(all.toString(), all.contains("Sep 25 → Sep 27, 2026 · 2 nights"));
        assertEquals("₱5,000.00", r.get("Deluxe Room × 1 · ₱2,500.00 × 2 nights"));
        assertTrue("the room number once assigned", all.contains("Room 305"));
        assertEquals("Juan Dela Cruz", r.get("Representative"));
        assertEquals("2", r.get("Adults"));
        assertEquals("₱5,000.00", r.get("Grand total"));
        assertEquals("₱1,000.00", r.get("Paid so far"));
        assertEquals("₱4,000.00", r.get("Balance due"));
        assertEquals("₱1,000.00", r.get("GCash · Payment"));
        assertTrue(all.toString(), all.contains("Sep 20, 2026 • 02:10:00 PM · Ref. 1234 567 890123 · Verified"));
    }

    @Test
    public void receipt2_checkinSettlement_hasEveryField() {
        PaymentReceiptActivity a = open(RECEIPT_2);
        assertNoBlankField("Receipt 2", a);
        Map<String, String> r = rows(a);
        List<String> all = allTexts(a.findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals("SR-20260925-000077", r.get("Payment Reference"));
        assertTrue(all.toString(), all.contains("Check-in") && all.contains("Paid"));
        assertEquals("₱5,000.00", r.get("Grand total"));
        assertEquals("₱5,000.00", r.get("Paid so far"));
        assertEquals("the settled balance reads green and plain", "₱0.00 · Fully paid", r.get("Balance due"));
        assertEquals("₱4,000.00", r.get("Cash · Payment"));
        assertEquals("₱1,000.00", r.get("GCash · Payment"));
        assertTrue(all.toString(), all.contains("Sep 25, 2026 • 12:30:00 PM · Verified"));
    }

    @Test
    public void receipt3_officialReceipt_hasEveryField() {
        PaymentReceiptActivity a = open(RECEIPT_3);
        assertNoBlankField("Receipt 3", a);
        Map<String, String> r = rows(a);
        List<String> all = allTexts(a.findViewById(R.id.layoutDynamicReceiptContent));
        assertEquals("OR-20260927-000310", r.get("Payment Reference"));
        assertTrue(all.toString(), all.contains("Check-out (final)") && all.contains("Sep 25 → Sep 27, 2026 · 2 nights"));
        assertEquals("₱0.00 · Fully paid", r.get("Balance due"));
        assertEquals("₱4,000.00", r.get("Cash · Balance"));
        assertTrue("what was handed over and the change", all.toString().contains("Cash received ₱5,000.00 · Change ₱1,000.00"));
        assertTrue("the no-refund note ends the final receipt", all.contains("No refunds for early check-out (hotel policy)."));
    }

    @Test
    public void aPayloadWithoutTheReceiptEnvelope_isRejectedInsteadOfCrashing() {
        ReceiptDetailResponse empty = new Gson().fromJson("{}", ReceiptDetailResponse.class);
        org.junit.Assert.assertNull(ApiMapper.toReceiptDetailOrNull(empty));
        org.junit.Assert.assertNull(ApiMapper.toReceiptDetailOrNull(null));
    }
}
