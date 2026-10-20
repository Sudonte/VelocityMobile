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

/** Request Additional Amenities: blocked stays say why, load failures offer Retry, and a request carries each room's chosen amenities. */
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

    /** The "Send this request?" confirmation: answer Send request. */
    private static void confirmSend() {
        androidx.appcompat.app.AlertDialog d = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        org.junit.Assert.assertNotNull("the guest is asked before the request is sent", d);
        assertEquals("Send this request?", String.valueOf(((TextView) d.findViewById(androidx.appcompat.R.id.alertTitle)).getText()));
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private AdditionalAmenitiesActivity open() {
        Intent intent = new Intent(app, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_DIRECT_BOOKING, true)
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

    private static AdditionalAmenityInfoDto.Room room(long id, String number, String type) {
        AdditionalAmenityInfoDto.Room r = new AdditionalAmenityInfoDto.Room();
        r.room_id = id;
        r.room_number = number;
        r.room_type = type;
        return r;
    }

    private static AdditionalAmenityInfoDto infoWithRooms(AdditionalAmenityInfoDto.Room... rooms) {
        AdditionalAmenityInfoDto dto = info(true, null);
        dto.rooms = Arrays.asList(rooms);
        return dto;
    }

    /** Taps room card {@code index} and returns the picker's amenity list. */
    private static android.widget.LinearLayout openPicker(AdditionalAmenitiesActivity a, int index) {
        ((android.widget.LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildAt(index).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        return dialog.findViewById(R.id.pickerList);
    }

    private static void closePicker() {
        org.robolectric.shadows.ShadowDialog.getLatestDialog().findViewById(R.id.btnPickerDone).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private AdditionalAmenitiesActivity openWithRooms(AdditionalAmenityInfoDto info) {
        AdditionalAmenitiesActivity a = open();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info);
        shadowOf(Looper.getMainLooper()).idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(Arrays.asList(amenity(7, "Extra Bed", "200.00", 5), amenity(8, "Breakfast", "75.50", 10)));
        shadowOf(Looper.getMainLooper()).idle();
        return a;
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

        assertEquals(a.getString(R.string.error_server_unreachable), text(a, R.id.additionalAmenitiesStateText));
        assertTrue(visible(a, R.id.additionalAmenitiesRetry));

        a.findViewById(R.id.additionalAmenitiesRetry).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, api.count("getBookingAdditionalAmenities"));
        assertEquals(a.getString(R.string.additional_amenities_loading), text(a, R.id.additionalAmenitiesStateText));
    }

    @Test
    public void theFormListsEveryRoomOfTheStay_andHasNoGuestNoteField() {
        AdditionalAmenitiesActivity a = openWithRooms(infoWithRooms(
                room(11, "101", "Standard"), room(12, "201", "Deluxe"), room(13, "202", "Deluxe")));
        assertTrue(visible(a, R.id.additionalAmenitiesForm));
        android.widget.LinearLayout rooms = a.findViewById(R.id.additionalAmenitiesRooms);
        assertEquals(3, rooms.getChildCount());
        assertEquals("Room 101", ((TextView) rooms.getChildAt(0).findViewById(R.id.tvAmenityRoomTitle)).getText().toString());
        assertEquals("Deluxe", ((TextView) rooms.getChildAt(2).findViewById(R.id.tvAmenityRoomType)).getText().toString());
        assertEquals(a.getString(R.string.additional_amenities_none_selected),
                ((TextView) rooms.getChildAt(0).findViewById(R.id.tvAmenityRoomSelection)).getText().toString());
        assertEquals("the Guest Note field is gone", 0, a.getResources().getIdentifier("additionalAmenitiesNote", "id", a.getPackageName()));
        assertFalse("nothing chosen yet", a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());
    }

    @Test
    public void severalAmenitiesCanBePickedForOneRoom_withSubtotals_andTheTotalFollows() {
        AdditionalAmenitiesActivity a = openWithRooms(infoWithRooms(room(11, "101", "Standard")));
        android.widget.LinearLayout list = openPicker(a, 0);
        assertEquals(2, list.getChildCount());
        View bed = list.getChildAt(0);
        bed.findViewById(R.id.btnAmenityOptionPlus).performClick();
        bed.findViewById(R.id.btnAmenityOptionPlus).performClick();
        assertEquals("2", ((TextView) bed.findViewById(R.id.tvAmenityOptionQty)).getText().toString());
        assertEquals("2 \u00d7 \u20b1200.00 = \u20b1400.00", ((TextView) bed.findViewById(R.id.tvAmenityOptionSubtotal)).getText().toString());
        list.getChildAt(1).findViewById(R.id.btnAmenityOptionPlus).performClick();
        closePicker();

        assertEquals("\u20b1475.50", text(a, R.id.additionalAmenitiesTotal));
        assertTrue(a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());
        assertEquals("3 items \u00b7 \u20b1475.50",
                ((TextView) ((android.widget.LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildAt(0)
                        .findViewById(R.id.tvAmenityRoomSelection)).getText().toString());

        // minus never goes below zero
        list = openPicker(a, 0);
        for (int i = 0; i < 3; i++) list.getChildAt(0).findViewById(R.id.btnAmenityOptionMinus).performClick();
        assertEquals("0", ((TextView) list.getChildAt(0).findViewById(R.id.tvAmenityOptionQty)).getText().toString());
    }

    @Test
    public void theStepperIsCappedAtStock_andTouchTargetsAre48dp() {
        AdditionalAmenitiesActivity a = open();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(infoWithRooms(room(11, "101", "Standard")));
        shadowOf(Looper.getMainLooper()).idle();
        api.<List<AmenityDto>>last("getAmenities").succeed(Arrays.asList(amenity(7, "Extra Bed", "200.00", 2)));
        shadowOf(Looper.getMainLooper()).idle();

        View row = openPicker(a, 0).getChildAt(0);
        for (int i = 0; i < 5; i++) row.findViewById(R.id.btnAmenityOptionPlus).performClick();
        assertEquals("only 2 in stock", "2", ((TextView) row.findViewById(R.id.tvAmenityOptionQty)).getText().toString());

        float density = a.getResources().getDisplayMetrics().density;
        for (int id : new int[]{R.id.btnAmenityOptionPlus, R.id.btnAmenityOptionMinus}) {
            ViewGroupLayout.assertMinTouch(row.findViewById(id), density);
        }
        ViewGroupLayout.assertMinTouch(a.findViewById(R.id.additionalAmenitiesSubmit), density);
        ViewGroupLayout.assertMinTouch(a.findViewById(R.id.btnAdditionalAmenitiesBack), density);
    }

    @Test
    public void twoRoomTypesThreeRooms_getDifferentAmenities_inOneSubmission_withASummaryFirst() {
        AdditionalAmenitiesActivity a = openWithRooms(infoWithRooms(
                room(11, "101", "Standard"), room(12, "201", "Deluxe"), room(13, "202", "Deluxe")));

        // room 101: 2 x Extra Bed + 1 x Breakfast; room 201: 1 x Breakfast; room 202: nothing
        android.widget.LinearLayout list = openPicker(a, 0);
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        list.getChildAt(1).findViewById(R.id.btnAmenityOptionPlus).performClick();
        closePicker();
        list = openPicker(a, 1);
        list.getChildAt(1).findViewById(R.id.btnAmenityOptionPlus).performClick();
        closePicker();

        // the summary shows each room that has something, with its amenities
        android.widget.LinearLayout summary = a.findViewById(R.id.additionalAmenitiesSummary);
        assertTrue(visible(a, R.id.additionalAmenitiesSummaryHeader));
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < summary.getChildCount(); i++) all.append(((TextView) summary.getChildAt(i)).getText()).append('\n');
        String text = all.toString();
        assertTrue(text, text.contains("Room 101"));
        assertTrue(text, text.contains("Extra Bed x 2"));
        assertTrue(text, text.contains("Room 201"));
        assertFalse("a room with nothing chosen is not in the summary", text.contains("Room 202"));

        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        confirmSend();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, api.count("submitBookingAdditionalAmenities"));
        Object[] args = api.lastArgs.get("submitBookingAdditionalAmenities");
        assertEquals("42", args[0]);
        AdditionalAmenitySubmitRequest request = (AdditionalAmenitySubmitRequest) args[1];
        assertEquals("two rooms asked for something", 2, request.rooms.size());
        assertEquals(11L, request.rooms.get(0).room_id);
        assertEquals(2, request.rooms.get(0).items.size());
        assertEquals(7L, request.rooms.get(0).items.get(0).amenity_id);
        assertEquals(2, request.rooms.get(0).items.get(0).quantity);
        assertEquals(12L, request.rooms.get(1).room_id);
        assertEquals(1, request.rooms.get(1).items.size());
        assertEquals(8L, request.rooms.get(1).items.get(0).amenity_id);
        assertEquals("the flat form is not also sent", null, request.items);
        assertFalse("can't be sent twice while in flight", a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());

        api.<AdditionalAmenityInfoDto>last("submitBookingAdditionalAmenities").succeed(info(true, null));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(a.isFinishing());
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(a).getResultCode());
    }

    @Test
    public void aServerWithoutARoomList_stillWorks_withOneGenericRoomAndTheFlatForm() {
        AdditionalAmenitiesActivity a = openWithCatalog(); // info() has no rooms
        android.widget.LinearLayout rooms = a.findViewById(R.id.additionalAmenitiesRooms);
        assertEquals(1, rooms.getChildCount());
        openPicker(a, 0).getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        closePicker();

        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        confirmSend();
        shadowOf(Looper.getMainLooper()).idle();
        AdditionalAmenitySubmitRequest request = (AdditionalAmenitySubmitRequest) api.lastArgs.get("submitBookingAdditionalAmenities")[1];
        assertEquals(null, request.rooms);
        assertEquals(1, request.items.size());
        assertEquals(7L, request.items.get(0).amenity_id);
    }

    @Test
    public void aRefusedSubmit_keepsTheFormAndTheChoices_andSaysWhy() {
        AdditionalAmenitiesActivity a = openWithRooms(infoWithRooms(room(11, "101", "Standard")));
        openPicker(a, 0).getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        closePicker();

        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        confirmSend();
        shadowOf(Looper.getMainLooper()).idle();
        api.<AdditionalAmenityInfoDto>last("submitBookingAdditionalAmenities").httpWithBody(422, "{\"message\":\"Only 1 of Extra Bed left\"}");
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(a.isFinishing());
        assertTrue(visible(a, R.id.additionalAmenitiesForm));
        assertEquals("1 item · ₱200.00",
                ((TextView) ((android.widget.LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildAt(0)
                        .findViewById(R.id.tvAmenityRoomSelection)).getText().toString());
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

    // ---- Part 6: confirm before sending, discard guard ----

    @Test
    public void backWithAnItemPicked_asksBeforeDiscarding_andABlankFormLeavesAtOnce() {
        AdditionalAmenitiesActivity blank = openWithRooms(infoWithRooms(room(11, "101", "Standard")));
        blank.getOnBackPressedDispatcher().onBackPressed();
        assertTrue("nothing entered: leaves at once", blank.isFinishing());

        AdditionalAmenitiesActivity a = openWithRooms(infoWithRooms(room(11, "101", "Standard")));
        android.widget.LinearLayout list = openPicker(a, 0);
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        closePicker();
        a.getOnBackPressedDispatcher().onBackPressed();
        assertFalse("something picked: stays and asks", a.isFinishing());
        androidx.appcompat.app.AlertDialog d = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertEquals("Discard your changes?", String.valueOf(((TextView) d.findViewById(androidx.appcompat.R.id.alertTitle)).getText()));
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse("Keep editing stays", a.isFinishing());
        a.getOnBackPressedDispatcher().onBackPressed();
        ((androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog()).getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Discard leaves", a.isFinishing());
    }

    @Test
    public void sendingARequest_asksFirst_andNothingIsSentUntilTheGuestConfirms() {
        AdditionalAmenitiesActivity a = openWithRooms(infoWithRooms(room(11, "101", "Standard")));
        android.widget.LinearLayout list = openPicker(a, 0);
        list.getChildAt(0).findViewById(R.id.btnAmenityOptionPlus).performClick();
        closePicker();
        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        assertEquals("not sent yet", 0, api.count("submitBookingAdditionalAmenities"));
        androidx.appcompat.app.AlertDialog d = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        assertEquals(0, api.count("submitBookingAdditionalAmenities"));
        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        confirmSend();
        assertEquals("one request, after Send request", 1, api.count("submitBookingAdditionalAmenities"));
    }
}
