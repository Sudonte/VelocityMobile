package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AdditionalAmenitiesActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.dto.AdditionalAmenityInfoDto;
import com.example.velocitysuites.network.dto.AdditionalAmenitySubmitRequest;
import com.example.velocitysuites.network.dto.AmenityDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Request Additional Amenities: blocked stays say why, load failures offer Retry, and a request carries the chosen items and note. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AdditionalAmenitiesScreenTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
        ScreenTestSupport.goOnline(app);
    }

    private AdditionalAmenitiesActivity open() {
        Intent intent = new Intent(app, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_HAS_BOOKING, true)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_ID, "42");
        AdditionalAmenitiesActivity a = Robolectric.buildActivity(AdditionalAmenitiesActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return a;
    }

    private static AdditionalAmenityInfoDto info(boolean canRequest, String reason) {
        AdditionalAmenityInfoDto dto = new AdditionalAmenityInfoDto();
        dto.can_request = canRequest;
        dto.reason = reason;
        dto.requests = new ArrayList<>();
        return dto;
    }

    private static AmenityDto amenity(long id, String name, String price, int stock) {
        AmenityDto dto = new AmenityDto();
        dto.id = id;
        dto.amenity_name = name;
        dto.charge = price;
        dto.quantity = stock;
        return dto;
    }

    private static boolean visible(AdditionalAmenitiesActivity a, int id) {
        return a.findViewById(id).getVisibility() == View.VISIBLE;
    }

    private static String text(AdditionalAmenitiesActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private AdditionalAmenitiesActivity openWithCatalog() {
        AdditionalAmenitiesActivity a = open();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info(true, null));
        shadowOf(Looper.getMainLooper()).idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(Arrays.asList(amenity(7, "Extra Bed", "200.00", 5), amenity(8, "Breakfast", "75.50", 10)));
        shadowOf(Looper.getMainLooper()).idle();
        return a;
    }

    @Test
    public void whileLoading_itSaysSo_andOffersNoForm() {
        AdditionalAmenitiesActivity a = open();
        assertTrue(visible(a, R.id.additionalAmenitiesState));
        assertEquals(a.getString(R.string.additional_amenities_loading), text(a, R.id.additionalAmenitiesStateText));
        assertFalse(visible(a, R.id.additionalAmenitiesForm));
    }

    @Test
    public void aStayThatCantRequest_showsTheServersReason_andNoForm() {
        AdditionalAmenitiesActivity a = open();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info(false, "You have already checked out."));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("You have already checked out.", text(a, R.id.additionalAmenitiesStateText));
        assertFalse(visible(a, R.id.additionalAmenitiesForm));
        assertFalse("nothing to retry - the answer was no", visible(a, R.id.additionalAmenitiesRetry));
        assertEquals("the catalog is never even fetched", 0, api.count("getAmenities"));
    }

    @Test
    public void aLoadFailure_showsAnErrorWithRetry_andRetryAsksAgain() {
        AdditionalAmenitiesActivity a = open();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").offline();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(a.getString(R.string.additional_amenities_load_error), text(a, R.id.additionalAmenitiesStateText));
        assertTrue(visible(a, R.id.additionalAmenitiesRetry));

        a.findViewById(R.id.additionalAmenitiesRetry).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, api.count("getBookingAdditionalAmenities"));
        assertEquals(a.getString(R.string.additional_amenities_loading), text(a, R.id.additionalAmenitiesStateText));
    }

    @Test
    public void pickingQuantities_showsEachSubtotal_andTheTotal_andSubmitNeedsAtLeastOneItem() {
        AdditionalAmenitiesActivity a = openWithCatalog();
        assertTrue(visible(a, R.id.additionalAmenitiesForm));
        assertFalse("nothing chosen yet", a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());

        android.widget.LinearLayout list = a.findViewById(R.id.additionalAmenitiesList);
        assertEquals(2, list.getChildCount());
        View bed = list.getChildAt(0);
        View plus = bed.findViewById(R.id.btnAmenityOptionPlus);
        plus.performClick();
        plus.performClick();
        assertEquals("2", ((TextView) bed.findViewById(R.id.tvAmenityOptionQty)).getText().toString());
        assertEquals("₱400.00", ((TextView) bed.findViewById(R.id.tvAmenityOptionSubtotal)).getText().toString());
        list.getChildAt(1).findViewById(R.id.btnAmenityOptionPlus).performClick();

        assertEquals(a.getString(R.string.additional_amenities_total_format, "₱475.50"), text(a, R.id.additionalAmenitiesTotal));
        assertTrue(a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());

        bed.findViewById(R.id.btnAmenityOptionMinus).performClick();
        bed.findViewById(R.id.btnAmenityOptionMinus).performClick();
        bed.findViewById(R.id.btnAmenityOptionMinus).performClick(); // already 0: stays 0
        assertEquals("0", ((TextView) bed.findViewById(R.id.tvAmenityOptionQty)).getText().toString());
    }

    @Test
    public void theStepperIsCappedAtStock_andTouchTargetsAre48dp() {
        AdditionalAmenitiesActivity a = open();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info(true, null));
        shadowOf(Looper.getMainLooper()).idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(Arrays.asList(amenity(7, "Extra Bed", "200.00", 2)));
        shadowOf(Looper.getMainLooper()).idle();

        View row = ((android.widget.LinearLayout) a.findViewById(R.id.additionalAmenitiesList)).getChildAt(0);
        for (int i = 0; i < 5; i++) row.findViewById(R.id.btnAmenityOptionPlus).performClick();
        assertEquals("only 2 in stock", "2", ((TextView) row.findViewById(R.id.tvAmenityOptionQty)).getText().toString());

        float density = a.getResources().getDisplayMetrics().density;
        int[] targets = {R.id.btnAmenityOptionPlus, R.id.btnAmenityOptionMinus};
        for (int id : targets) {
            ViewGroupLayout.assertMinTouch(row.findViewById(id), density);
        }
        ViewGroupLayout.assertMinTouch(a.findViewById(R.id.additionalAmenitiesSubmit), density);
        ViewGroupLayout.assertMinTouch(a.findViewById(R.id.btnAdditionalAmenitiesBack), density);
    }

    @Test
    public void submitting_sendsTheChosenItemsAndNote_thenClosesWithAnOkResult() {
        AdditionalAmenitiesActivity a = openWithCatalog();
        android.widget.LinearLayout list = a.findViewById(R.id.additionalAmenitiesList);
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        ((android.widget.EditText) a.findViewById(R.id.additionalAmenitiesNote)).setText("  Before 8pm  ");

        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, api.count("submitBookingAdditionalAmenities"));
        Object[] args = api.lastArgs.get("submitBookingAdditionalAmenities");
        assertEquals("42", args[0]);
        AdditionalAmenitySubmitRequest request = (AdditionalAmenitySubmitRequest) args[1];
        assertEquals(1, request.items.size());
        assertEquals(7L, request.items.get(0).amenity_id);
        assertEquals(2, request.items.get(0).quantity);
        assertEquals("Before 8pm", request.note);
        assertFalse("can't be sent twice while in flight", a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());

        api.<AdditionalAmenityInfoDto>last("submitBookingAdditionalAmenities").succeed(info(true, null));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(a.isFinishing());
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(a).getResultCode());
    }

    @Test
    public void aRefusedSubmit_keepsTheFormAndTheChoices_andSaysWhy() {
        AdditionalAmenitiesActivity a = openWithCatalog();
        android.widget.LinearLayout list = a.findViewById(R.id.additionalAmenitiesList);
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();

        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        api.<AdditionalAmenityInfoDto>last("submitBookingAdditionalAmenities").httpWithBody(422, "{\"message\":\"Only 1 of Extra Bed left\"}");
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(a.isFinishing());
        assertTrue(visible(a, R.id.additionalAmenitiesForm));
        assertEquals("1", ((TextView) list.getChildAt(0).findViewById(R.id.tvAmenityOptionQty)).getText().toString());
        assertTrue("can try again", a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());
        assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("Extra Bed"));
    }

    /** 48dp minimum touch target (layout size, not the visible glyph). */
    private static final class ViewGroupLayout {
        static void assertMinTouch(View v, float density) {
            assertTrue("touch target " + v.getId() + " is " + v.getLayoutParams().width + "x" + v.getLayoutParams().height,
                    v.getLayoutParams().height >= Math.round(48 * density) || v.getLayoutParams().height == -2 || v.getMinimumHeight() >= Math.round(48 * density));
            assertTrue(v.getLayoutParams().width >= Math.round(48 * density) || v.getLayoutParams().width < 0 || v.getMinimumWidth() >= Math.round(48 * density));
        }
    }
}
