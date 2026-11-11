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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
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
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 1.74.0 Part 5, amenities after the booking is created (the same on the guest web): a booking can request amenities - included and
 * paid - any time until check-out. Before check-in the guest picks the date range (when there are several) and the room that was BOOKED
 * ("Deluxe Room (room 1)"); during the stay, the room the front desk assigned. Every request goes to the front desk for approval. A
 * reservation has none until it becomes a booking.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Release174AmenitiesAfterBookingTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private ScreenTestSupport.FakeApi api;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        ScreenTestSupport.freshRepository(app, api);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static AmenityDto amenity(long id, String name, String price) {
        AmenityDto dto = new AmenityDto();
        dto.id = id;
        dto.amenity_name = name;
        dto.charge = price;
        dto.quantity = 10;
        return dto;
    }

    private static AdditionalAmenityInfoDto.Slot slot(long type, int n, String label) {
        AdditionalAmenityInfoDto.Slot s = new AdditionalAmenityInfoDto.Slot();
        s.room_type_id = type;
        s.option_key = "standard";
        s.room_slot = n;
        s.label = label;
        return s;
    }

    private static AdditionalAmenityInfoDto.Stay stay(long id, String in, String out, String state, AdditionalAmenityInfoDto.Slot... slots) {
        AdditionalAmenityInfoDto.Stay s = new AdditionalAmenityInfoDto.Stay();
        s.stay_id = id;
        s.position = (int) id;
        s.check_in = in;
        s.check_out = out;
        s.nights = 1;
        s.state = state;
        s.slots = Arrays.asList(slots);
        return s;
    }

    private AdditionalAmenitiesActivity open(boolean booking) {
        Intent intent = new Intent(app, AdditionalAmenitiesActivity.class)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_DIRECT_BOOKING, booking)
                .putExtra(AdditionalAmenitiesActivity.EXTRA_ID, "42");
        AdditionalAmenitiesActivity a = Robolectric.buildActivity(AdditionalAmenitiesActivity.class, intent).setup().get();
        idle();
        return a;
    }

    /** A booking that has not checked in yet, with the given stays; the catalog is one included and one paid amenity. */
    private AdditionalAmenitiesActivity beforeCheckIn(AdditionalAmenityInfoDto.Stay... stays) {
        AdditionalAmenitiesActivity a = open(true);
        AdditionalAmenityInfoDto info = new AdditionalAmenityInfoDto();
        info.can_request = true;
        info.requests = new ArrayList<>();
        info.rooms = new ArrayList<>(); // no room is assigned before check-in
        info.stays = Arrays.asList(stays);
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info);
        idle();
        return a;
    }

    private void catalogArrives() {
        api.<List<AmenityDto>>last("getAmenities").succeed(Arrays.asList(amenity(7, "Extra Bed", "200.00"), amenity(9, "Extra Pillow", "0.00")));
        idle();
    }

    private static String allText(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof TextView) sb.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) sb.append(allText(((ViewGroup) v).getChildAt(i)));
        return sb.toString();
    }

    private static LinearLayout pickerOf(AdditionalAmenitiesActivity a, int roomIndex) {
        ((LinearLayout) a.findViewById(R.id.additionalAmenitiesRooms)).getChildAt(roomIndex).performClick();
        idle();
        return ShadowDialog.getLatestDialog().findViewById(R.id.pickerList);
    }

    private AdditionalAmenitySubmitRequest send(AdditionalAmenitiesActivity a) {
        ShadowDialog.getLatestDialog().findViewById(R.id.btnPickerDone).performClick();
        idle();
        assertTrue("Send is ready", a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());
        a.findViewById(R.id.additionalAmenitiesSubmit).performClick();
        idle();
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        Object[] args = api.lastArgs.get("submitBookingAdditionalAmenities");
        assertNotNull("the request was sent", args);
        return (AdditionalAmenitySubmitRequest) args[1];
    }

    // ---- before check-in: the booked rooms

    @Test
    public void beforeCheckIn_theGuestPicksABookedRoom_byItsTypeAndNumber() {
        AdditionalAmenitiesActivity a = beforeCheckIn(stay(5, "2026-12-01", "2026-12-02", "upcoming", slot(3, 1, "Deluxe Room (room 1)"), slot(3, 2, "Deluxe Room (room 2)")));
        catalogArrives();

        LinearLayout rooms = a.findViewById(R.id.additionalAmenitiesRooms);
        assertEquals("the two rooms that were booked", 2, rooms.getChildCount());
        assertEquals("Deluxe Room (room 1)", ((TextView) rooms.getChildAt(0).findViewById(R.id.tvAmenityRoomTitle)).getText().toString());
        assertEquals("Deluxe Room (room 2)", ((TextView) rooms.getChildAt(1).findViewById(R.id.tvAmenityRoomTitle)).getText().toString());
        assertEquals("Booked room · before check-in", ((TextView) rooms.getChildAt(0).findViewById(R.id.tvAmenityRoomType)).getText().toString());
        assertEquals(View.VISIBLE, a.findViewById(R.id.additionalAmenitiesForm).getVisibility());
    }

    @Test
    public void beforeCheckIn_theRequestNamesTheBookedRoom_notARoomId_andCarriesIncludedAndPaid() {
        AdditionalAmenitiesActivity a = beforeCheckIn(stay(5, "2026-12-01", "2026-12-02", "upcoming", slot(3, 1, "Deluxe Room (room 1)"), slot(3, 2, "Deluxe Room (room 2)")));
        catalogArrives();
        LinearLayout list = pickerOf(a, 1); // Deluxe Room (room 2)
        assertTrue(allText(list), allText(list).contains("Included with your room") && allText(list).contains("Additional amenities"));
        list.getChildAt(1).findViewById(R.id.btnAmenityOptionPlus).performClick(); // Extra Pillow (included)
        list.getChildAt(3).findViewById(R.id.btnAmenityOptionPlus).performClick(); // Extra Bed (paid)

        AdditionalAmenitySubmitRequest request = send(a);
        assertEquals(Long.valueOf(5), request.stay_id);
        assertNull("no flat list", request.items);
        assertEquals(1, request.rooms.size());
        AdditionalAmenitySubmitRequest.RoomLine line = request.rooms.get(0);
        assertNull("no room is assigned yet", line.room_id);
        assertEquals(Long.valueOf(3), line.room_type_id);
        assertEquals("standard", line.option_key);
        assertEquals(Integer.valueOf(2), line.room_slot);
        List<Long> ids = new ArrayList<>();
        for (AdditionalAmenitySubmitRequest.Item item : line.items) ids.add(item.amenity_id);
        assertTrue(ids.toString(), ids.size() == 2 && ids.contains(7L) && ids.contains(9L));

        String json = new com.google.gson.Gson().toJson(request);
        assertTrue(json, json.contains("\"room_type_id\":3") && json.contains("\"room_slot\":2"));
        assertFalse("room_id is left out, not sent as 0: " + json, json.contains("room_id"));
    }

    @Test
    public void anIncludedAmenityAlone_canBeRequested_thoughItAddsNothing() {
        AdditionalAmenitiesActivity a = beforeCheckIn(stay(5, "2026-12-01", "2026-12-02", "upcoming", slot(3, 1, "Deluxe Room (room 1)")));
        catalogArrives();
        assertFalse("nothing chosen yet", a.findViewById(R.id.additionalAmenitiesSubmit).isEnabled());
        LinearLayout list = pickerOf(a, 0);
        list.getChildAt(1).findViewById(R.id.btnAmenityOptionPlus).performClick(); // Extra Pillow (included, 0)

        AdditionalAmenitySubmitRequest request = send(a);
        assertEquals(1, request.rooms.get(0).items.size());
        assertEquals(9L, request.rooms.get(0).items.get(0).amenity_id);
        assertEquals("₱0.00", ((TextView) a.findViewById(R.id.additionalAmenitiesTotal)).getText().toString().replace((char) 160, ' '));
    }

    @Test
    public void withSeveralDateRanges_theGuestPicksTheDateRangeFirst_thenItsBookedRooms() {
        AdditionalAmenitiesActivity a = beforeCheckIn(
                stay(5, "2026-12-01", "2026-12-02", "upcoming", slot(3, 1, "Deluxe Room (room 1)")),
                stay(6, "2026-12-10", "2026-12-12", "upcoming", slot(4, 1, "Family Room (room 1)"), slot(4, 2, "Family Room (room 2)")));

        AlertDialog ask = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("which date range?", ask);
        assertEquals(2, ask.getListView().getCount());
        String second = String.valueOf(ask.getListView().getItemAtPosition(1));
        assertTrue(second, second.contains("2026-12-10") && second.contains("2026-12-12"));
        shadowOf(ask.getListView()).performItemClick(1);
        idle();
        catalogArrives();

        LinearLayout rooms = a.findViewById(R.id.additionalAmenitiesRooms);
        assertEquals(2, rooms.getChildCount());
        assertEquals("Family Room (room 1)", ((TextView) rooms.getChildAt(0).findViewById(R.id.tvAmenityRoomTitle)).getText().toString());
        LinearLayout list = pickerOf(a, 0);
        list.getChildAt(3).findViewById(R.id.btnAmenityOptionPlus).performClick();
        AdditionalAmenitySubmitRequest request = send(a);
        assertEquals("the date range that was picked", Long.valueOf(6), request.stay_id);
        assertEquals(Long.valueOf(4), request.rooms.get(0).room_type_id);
        assertEquals(Integer.valueOf(1), request.rooms.get(0).room_slot);
    }

    // ---- during the stay: the assigned rooms, as before

    @Test
    public void duringTheStay_theGuestPicksTheAssignedRoom_andTheRequestNamesItsRoomId() {
        AdditionalAmenitiesActivity a = open(true);
        AdditionalAmenityInfoDto info = new AdditionalAmenityInfoDto();
        info.can_request = true;
        info.requests = new ArrayList<>();
        AdditionalAmenityInfoDto.Room room = new AdditionalAmenityInfoDto.Room();
        room.stay_id = 5L;
        room.room_id = 11;
        room.room_number = "101";
        room.room_type = "Deluxe Room";
        info.rooms = Arrays.asList(room);
        info.stays = Arrays.asList(stay(5, "2026-12-01", "2026-12-02", "in_house"));
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info);
        idle();
        catalogArrives();

        LinearLayout rooms = a.findViewById(R.id.additionalAmenitiesRooms);
        assertEquals(1, rooms.getChildCount());
        assertEquals("Room 101", ((TextView) rooms.getChildAt(0).findViewById(R.id.tvAmenityRoomTitle)).getText().toString());
        LinearLayout list = pickerOf(a, 0);
        list.getChildAt(3).findViewById(R.id.btnAmenityOptionPlus).performClick();
        AdditionalAmenitySubmitRequest request = send(a);
        assertEquals(Long.valueOf(11), request.rooms.get(0).room_id);
        assertNull(request.rooms.get(0).room_type_id);
        assertNull(request.rooms.get(0).room_slot);
        String json = new com.google.gson.Gson().toJson(request);
        assertTrue(json, json.contains("\"room_id\":11") && !json.contains("room_type_id") && !json.contains("room_slot"));
    }

    // ---- a reservation has none until it becomes a booking

    @Test
    public void aReservation_cannotRequestAmenities_andIsToldWhy() {
        AdditionalAmenitiesActivity a = open(false);
        AdditionalAmenityInfoDto info = new AdditionalAmenityInfoDto();
        info.can_request = false;
        info.reason = "Amenities can be requested once your reservation becomes a booking.";
        info.rooms = new ArrayList<>();
        info.requests = new ArrayList<>();
        api.<AdditionalAmenityInfoDto>last("getReservationAdditionalAmenities").succeed(info);
        idle();

        assertEquals(View.GONE, a.findViewById(R.id.additionalAmenitiesForm).getVisibility());
        assertEquals("Amenities can be requested once your reservation becomes a booking.",
                ((TextView) a.findViewById(R.id.additionalAmenitiesStateText)).getText().toString());
        assertEquals("no catalog is loaded for it", 0, api.count("getAmenities"));
    }

    // ---- an older server (no stays, no slots) keeps working

    @Test
    public void anOlderServerWithoutBookedRooms_stillGetsTheGenericRoom() {
        AdditionalAmenitiesActivity a = open(true);
        AdditionalAmenityInfoDto info = new AdditionalAmenityInfoDto();
        info.can_request = true;
        info.requests = new ArrayList<>();
        api.<AdditionalAmenityInfoDto>last("getBookingAdditionalAmenities").succeed(info);
        idle();
        catalogArrives();

        LinearLayout rooms = a.findViewById(R.id.additionalAmenitiesRooms);
        assertEquals(1, rooms.getChildCount());
        assertEquals("Your room", ((TextView) rooms.getChildAt(0).findViewById(R.id.tvAmenityRoomTitle)).getText().toString());
    }

    // ---- layout

    @Test
    public void theBookedRoomsFitAt360dp() {
        AdditionalAmenitiesActivity a = beforeCheckIn(stay(5, "2026-12-01", "2026-12-02", "upcoming",
                slot(3, 1, "Presidential Family Suite with Balcony (room 1)"), slot(3, 2, "Presidential Family Suite with Balcony (room 2)")));
        catalogArrives();
        View decor = a.getWindow().getDecorView();
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
        LayoutAssert.assertFits("booked rooms at 360dp", a.findViewById(R.id.additionalAmenitiesForm), w);
    }
}
