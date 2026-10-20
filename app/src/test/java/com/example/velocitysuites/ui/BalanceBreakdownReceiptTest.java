package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

import com.example.velocitysuites.BalanceBreakdown;
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
import java.util.List;

/**
 * Receipt 3 of a stay paid in full at check-in: the server's balance breakdown (check-in summary, additional charges,
 * remaining balance, final grand total) is printed in the agreed order with the server's own figures, and a receipt
 * without that breakdown keeps its previous layout.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BalanceBreakdownReceiptTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    /** 2 nights x 2,500 = 5,000, Senior 20% = -1,000 -> paid 4,000 at check-in; towels 2 x 50 + lamp 300 -> 400 at check-out. */
    private static String receipt(String stayBillExtra, String highlight) {
        return "{\"receipt\":{\"receipt_type\":\"OFFICIAL_RECEIPT\",\"receipt_number\":\"OR-20261017-000900\",\"receipt_stage\":3,"
                + "\"booking_id\":900,\"guest_account_name\":\"Juan Dela Cruz\",\"representative_name\":\"Juan Dela Cruz\","
                + "\"room_type\":\"Deluxe Room\",\"room_lines\":[{\"room_type_id\":\"3\",\"room_type\":\"Deluxe Room\",\"quantity\":1,"
                + "\"price_per_night\":2500.0,\"nights\":2,\"subtotal\":5000.0,\"assigned_room_numbers\":[\"305\"]}],"
                + "\"check_in\":\"2026-10-15\",\"check_out\":\"2026-10-17\",\"number_of_nights\":2,\"assigned_room_numbers\":[\"305\"],"
                + "\"adults\":1,\"children\":0,\"issued_at\":\"2026-10-17T03:00:00.000000Z\","
                + "\"payment_summary\":{\"grand_total\":4400.0,\"total_amount_paid\":4400.0,\"remaining_balance\":0.0,"
                + "\"payment_status\":\"PAID\",\"payment_percentage\":100,\"official_receipt_available\":true,\"discount\":1000},"
                + "\"stay_bill\":{\"check_in\":\"2026-10-15\",\"scheduled_check_out\":\"2026-10-17\",\"actual_check_out\":\"2026-10-17\","
                + "\"scheduled_nights\":2,\"actual_nights\":2,\"extra_nights\":0,\"room_charge\":5000.0,\"subtotal\":5400.0,"
                + "\"discount\":1000,\"total\":4400.0,\"total_paid\":4400.0,\"balance\":0" + stayBillExtra + "},"
                + "\"payment_transactions\":[]" + highlight + "}}";
    }

    private static final String BREAKDOWN = ",\"receipt_breakdown\":{"
            + "\"checkin\":{\"room_charge\":5000.0,\"booked_amenities\":0,\"discount_name\":\"Senior Citizen\",\"discount_percent\":20.0,"
            + "\"discount\":1000.0,\"grand_total\":4000.0,\"amount_paid\":4000.0,\"paid_in_full\":true,\"status\":\"PAID IN FULL\"},"
            + "\"after_checkin\":{\"amenities\":[{\"name\":\"Extra Towel\",\"quantity\":2,\"unit_price\":50.0,\"subtotal\":100.0}],"
            + "\"amenities_total\":100.0,\"charges\":[{\"name\":\"Broken lamp\",\"quantity\":1,\"unit_price\":300.0,\"subtotal\":300.0}],"
            + "\"charges_total\":300.0,\"stay_adjustment\":0.0,\"subtotal\":400.0},"
            + "\"remaining_balance\":400.0,\"final_grand_total\":4400.0,\"total_paid\":4400.0,\"balance_due\":0.0}";

    private static final String HIGHLIGHT = ",\"highlight\":{\"kind\":\"checkout_final\",\"room_charges\":5000.0,\"amenities_total\":100,"
            + "\"additional_charges_total\":300,\"discount\":1000,\"grand_total\":4400.0,\"paid_receipt_1\":0,\"paid_receipt_2\":4000,"
            + "\"paid_at_checkout\":400,\"total_paid\":4400.0,\"remaining_balance\":0.0}";

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

    private List<String> texts(String json) {
        ReceiptDetail detail = ApiMapper.toReceiptDetail(new Gson().fromJson(json, ReceiptDetailResponse.class));
        PaymentReceiptActivity.debugPreviewFixtures.put(detail.getReceiptNumber(), detail);
        Intent intent = PaymentReceiptActivity.newIntentForReceipt(app, detail.getReceiptNumber());
        ActivityController<PaymentReceiptActivity> controller = Robolectric.buildActivity(PaymentReceiptActivity.class, intent);
        controller.setup();
        shadowOf(Looper.getMainLooper()).idle();
        List<String> out = new ArrayList<>();
        gather(controller.get().findViewById(R.id.layoutDynamicReceiptContent), out);
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

    private static int indexOf(List<String> texts, String needle) {
        for (int i = 0; i < texts.size(); i++) if (texts.get(i).contains(needle)) return i;
        return -1;
    }

    @Test
    public void receipt3_printsTheBreakdownInTheAgreedOrderWithTheServersFigures() {
        List<String> t = texts(receipt(BREAKDOWN, HIGHLIGHT));
        String[] order = {"Check-in summary (fully paid)", "Room rate × 2 nights", "₱5,000.00", "Discount (Senior Citizen, 20%)", "-₱1,000.00",
                "Check-in Grand Total", "₱4,000.00", "Amount Paid at Check-in", "PAID IN FULL", "Extra Towel × 2 @ ₱50.00", "Broken lamp × 1 @ ₱300.00",
                "Subtotal of Additional", "₱400.00", "REMAINING BALANCE", "Check-in Grand Total (after discount)", "+ Remaining Balance (amenities + additional charges)", "+₱400.00", "Final grand total", "₱4,400.00", "Total paid", "-₱4,400.00", "Balance due", "₱0.00"};
        int last = -1;
        for (String needle : order) {
            int at = indexOf(t.subList(Math.max(0, last), t.size()), needle);
            assertTrue(needle + " missing or out of order in " + t, at >= 0);
            last = Math.max(0, last) + at;
        }
        assertFalse("the old summary block is replaced", t.contains("Paid - Receipt 1 (booking)"));
    }

    @Test
    public void aStayWithNothingAddedSaysSoAndKeepsRemainingBalanceAtZero() {
        String none = ",\"receipt_breakdown\":{\"checkin\":{\"room_charge\":5000.0,\"booked_amenities\":0,\"discount_name\":null,\"discount_percent\":null,"
                + "\"discount\":0,\"grand_total\":5000.0,\"amount_paid\":5000.0,\"paid_in_full\":true,\"status\":\"PAID IN FULL\"},"
                + "\"after_checkin\":{\"amenities\":[],\"amenities_total\":0,\"charges\":[],\"charges_total\":0,\"stay_adjustment\":0,\"subtotal\":0},"
                + "\"remaining_balance\":0,\"final_grand_total\":5000.0,\"total_paid\":5000.0,\"balance_due\":0}";
        List<String> t = texts(receipt(none, HIGHLIGHT));
        assertTrue(t.toString(), indexOf(t, "No additional charges") >= 0);
        assertTrue(indexOf(t, "REMAINING BALANCE") >= 0);
        assertEquals("no discount row when there is no discount", -1, indexOf(t, "Discount ("));
    }

    @Test
    public void aReceiptWithoutTheBreakdownKeepsItsPreviousLayout() {
        List<String> t = texts(receipt("", HIGHLIGHT));
        assertEquals(-1, indexOf(t, "Check-in summary"));
        assertTrue(indexOf(t, "Paid - Receipt 2 (check-in)") >= 0);
    }

    @Test
    public void theBreakdownModelAddsUp() {
        ReceiptDetail detail = ApiMapper.toReceiptDetail(new Gson().fromJson(receipt(BREAKDOWN, HIGHLIGHT), ReceiptDetailResponse.class));
        BalanceBreakdown b = detail.getStay().balanceBreakdown;
        assertNotNull(b);
        assertTrue(b.addsUp());
        assertEquals(b.checkinGrandTotal + b.additionalSubtotal, b.finalGrandTotal, 0.001);
        assertEquals(b.amenitiesTotal + b.chargesTotal, b.remainingBalance, 0.001);
        assertNull(ApiMapper.toReceiptDetail(new Gson().fromJson(receipt("", HIGHLIGHT), ReceiptDetailResponse.class)).getStay().balanceBreakdown);
    }
}
