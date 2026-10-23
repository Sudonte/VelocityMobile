package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.AddOnAmenity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.RoomRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Collections;
import java.util.Map;

import okhttp3.RequestBody;
import okio.Buffer;

/** The amenities chosen per room go to the server per room: amenity, quantity, the room type and which room of that type. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoomAmenityRequestTest {

    private Context app;
    private ScreenTestSupport.FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new ScreenTestSupport.FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> sentFields(String method) throws Exception {
        for (int i = 0; i < 300 && api.count(method) == 0; i++) {
            Thread.sleep(20);
            shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(method + " was called once", 1, api.count(method));
        Map<String, RequestBody> raw = (Map<String, RequestBody>) api.lastArgs.get(method)[0];
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, RequestBody> e : raw.entrySet()) {
            Buffer buffer = new Buffer();
            e.getValue().writeTo(buffer);
            out.put(e.getKey(), buffer.readUtf8());
        }
        return out;
    }

    private static BookingWizardState.Stay stay(boolean perRoom) {
        BookingWizardState.Stay stay = new BookingWizardState.Stay();
        stay.checkIn = WizardDriver.day(5);
        stay.checkOut = WizardDriver.day(6);
        stay.rooms.add(WizardDriver.room("7", 1000));
        stay.rooms.add(WizardDriver.room("7", 1000));
        stay.rooms.add(WizardDriver.room("8", 2000));
        AddOnAmenity bed = new AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5);
        if (perRoom) {
            stay.setAmenityQty("7#2", bed, 2);
            stay.setAmenityQty("8#1", bed, 1);
        } else {
            AddOnAmenity whole = new AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5);
            whole.setQuantity(3);
            stay.amenities.add(whole);
        }
        return stay;
    }

    @Test
    public void everyAmenityIsSentWithItsRoomTypeAndWhichRoomOfThatType() throws Exception {
        repository.createWithStays(true, Collections.singletonList(stay(true)), 1, 0, "Ana", null, "Cruz", "None", null, null, null, "cash",
                null, null, null, 0, null, "key-1", null);
        Map<String, String> f = sentFields("createReservationWithPayment");

        assertEquals("9", f.get("stays[0][amenities][0][amenity_id]"));
        assertEquals("2", f.get("stays[0][amenities][0][quantity]"));
        assertEquals("the second Deluxe", "7", f.get("stays[0][amenities][0][room_type_id]"));
        assertEquals("2", f.get("stays[0][amenities][0][room_slot]"));
        assertEquals("1", f.get("stays[0][amenities][1][quantity]"));
        assertEquals("8", f.get("stays[0][amenities][1][room_type_id]"));
        assertEquals("1", f.get("stays[0][amenities][1][room_slot]"));
        assertFalse("two entries only", f.containsKey("stays[0][amenities][2][amenity_id]"));
    }

    @Test
    public void aStayWithoutAPerRoomChoice_isSentAsTheWholeStay_asBefore() throws Exception {
        repository.createWithStays(true, Collections.singletonList(stay(false)), 1, 0, "Ana", null, "Cruz", "None", null, null, null, "cash",
                null, null, null, 0, null, "key-2", null);
        Map<String, String> f = sentFields("createReservationWithPayment");

        assertEquals("3", f.get("stays[0][amenities][0][quantity]"));
        assertFalse(f.containsKey("stays[0][amenities][0][room_type_id]"));
        assertFalse(f.containsKey("stays[0][amenities][0][room_slot]"));
    }

    @Test
    public void theAnyRoomGroupIsSentWithoutARoom_andStaysThatWayUnlessMoved() throws Exception {
        BookingWizardState.Stay stay = stay(true);
        AddOnAmenity breakfast = new AddOnAmenity("10", "Breakfast", "Food", "d", 150, 5);
        stay.setAmenityQty(BookingWizardState.Stay.ANY_ROOM, breakfast, 2);
        repository.createWithStays(true, java.util.Collections.singletonList(stay), 1, 0, "Ana", null, "Cruz", "None", null, null, null, "cash",
                null, null, null, 0, null, "key-3", null);
        Map<String, String> f = sentFields("createReservationWithPayment");
        assertEquals("the Any room entry comes first and has no room", "10", f.get("stays[0][amenities][0][amenity_id]"));
        assertEquals("2", f.get("stays[0][amenities][0][quantity]"));
        assertFalse(f.containsKey("stays[0][amenities][0][room_type_id]"));
        assertFalse(f.containsKey("stays[0][amenities][0][room_slot]"));
        assertEquals("the room ones follow with their room", "7", f.get("stays[0][amenities][1][room_type_id]"));

        // moving it to a room: out of Any room, into room 1 of type 7
        stay.setAmenityQty(BookingWizardState.Stay.ANY_ROOM, breakfast, 0);
        stay.setAmenityQty("7#1", breakfast, 2);
        assertFalse(stay.hasAnyRoomAmenities());
        assertEquals(2, stay.amenityQty("7#1", "10"));
        assertEquals("the stay total is the same either way", 2, stay.amenityTotalQty("10"));
    }

    @Test
    public void aSingleStayWithRoomAmenities_goesOutAsStays_soTheRoomIsKept() {
        BookingWizardState state = new BookingWizardState(BookingWizardState.Mode.BOOKING);
        state.checkIn = WizardDriver.day(5);
        state.checkOut = WizardDriver.day(6);
        state.selectedRooms.add(WizardDriver.room("7", 1000));
        assertFalse("no amenities: the flat form, as before", state.sendsAsStays());
        state.committedStays().get(0).setAmenityQty("7#1", new AddOnAmenity("9", "Extra bed", "Comfort", "d", 300, 5), 1);
        assertTrue(state.sendsAsStays());
    }

    // ---- Modify: the room choices go back to the server, and come back into the wizard ----

    @Test
    public void modifyingAReservation_resendsEveryAmenityWithItsRoom() {
        BookingWizardState.Stay stay = stay(true);
        java.util.List<java.util.List<com.example.velocitysuites.Room>> groups = new java.util.ArrayList<>(stay.roomsGroupedByType().values());
        repository.updateReservationFull("588", groups, stay.checkIn, stay.checkOut, 1, 0, "None", null, false, null, stay.amenities, stay, null);

        com.example.velocitysuites.network.dto.ReservationUpdateRequest sent =
                (com.example.velocitysuites.network.dto.ReservationUpdateRequest) api.lastArgs.get("updateReservation")[1];
        assertEquals(2, sent.amenities.size());
        assertEquals(Long.valueOf(7), sent.amenities.get(0).room_type_id);
        assertEquals(Integer.valueOf(2), sent.amenities.get(0).room_slot);
        assertEquals(2, sent.amenities.get(0).quantity);
        assertEquals(Long.valueOf(8), sent.amenities.get(1).room_type_id);
        assertEquals(Integer.valueOf(1), sent.amenities.get(1).room_slot);
    }

    @Test
    public void modifyingAReservationWithoutAPerRoomChoice_sendsTheWholeStayAsBefore() {
        BookingWizardState.Stay stay = stay(false);
        java.util.List<java.util.List<com.example.velocitysuites.Room>> groups = new java.util.ArrayList<>(stay.roomsGroupedByType().values());
        repository.updateReservationFull("588", groups, stay.checkIn, stay.checkOut, 1, 0, "None", null, false, null, stay.amenities, stay, null);

        com.example.velocitysuites.network.dto.ReservationUpdateRequest sent =
                (com.example.velocitysuites.network.dto.ReservationUpdateRequest) api.lastArgs.get("updateReservation")[1];
        assertEquals(1, sent.amenities.size());
        assertEquals(3, sent.amenities.get(0).quantity);
        org.junit.Assert.assertNull(sent.amenities.get(0).room_type_id);
        org.junit.Assert.assertNull(sent.amenities.get(0).room_slot);
    }

    @Test
    public void openingAReservationToModify_putsEachAmenityBackInItsRoom() {
        com.example.velocitysuites.Booking reservation = new com.example.velocitysuites.Booking("588", "7", "Room 7", "Deluxe", "Nov 05, 2026", "Nov 06, 2026", 2, 3000, "Pending", "Oct 01, 2026");
        reservation.setRooms(java.util.Arrays.asList(new com.example.velocitysuites.BookingRoom("7", "Deluxe", 3, 1000, 1, 3000, java.util.Collections.<String>emptyList())));
        reservation.setAmenities(java.util.Arrays.asList(
                new com.example.velocitysuites.BookingAmenity("9", "Extra bed", 1, 300, 300, "Deluxe (room 2)", "7", 2),
                new com.example.velocitysuites.BookingAmenity("9", "Extra bed", 2, 300, 600, "Deluxe (room 3)", "7", 3),
                new com.example.velocitysuites.BookingAmenity("10", "Breakfast", 1, 150, 150))); // an older row: no room
        com.example.velocitysuites.BookingWizardActivity wizard = org.robolectric.Robolectric.buildActivity(com.example.velocitysuites.BookingWizardActivity.class,
                com.example.velocitysuites.BookingWizardActivity.newEditIntent(app, reservation)).create().get();

        BookingWizardState.Stay stay = wizard.getState().committedStays().get(0);
        assertEquals(1, stay.amenityQty("7#2", "9"));
        assertEquals(2, stay.amenityQty("7#3", "9"));
        assertEquals("an older row with no room waits in the Any room group", 1, stay.amenityQty(BookingWizardState.Stay.ANY_ROOM, "10"));
        assertEquals("and is NOT put in the first room", 0, stay.amenityQty("7#1", "10"));
        assertTrue(stay.hasAnyRoomAmenities());
        assertEquals("what is priced and sent per stay is the sum", 3, stay.amenityTotalQty("9"));
    }
}
