package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingAmenity;
import com.example.velocitysuites.BookingRoom;
import com.example.velocitysuites.BookingWizardActivity;
import com.example.velocitysuites.BookingWizardState;
import com.example.velocitysuites.Room;
import com.example.velocitysuites.RoomAmenity;
import com.example.velocitysuites.RoomRepository;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.ReservationUpdateRequest;
import com.example.velocitysuites.network.dto.RoomOptionDto;
import com.example.velocitysuites.network.dto.RoomTypeDto;
import com.example.velocitysuites.network.dto.AmenitySelectionDto;
import com.example.velocitysuites.ui.ScreenTestSupport.FakeApi;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Editing a reservation that has a standard Deluxe room AND two "other" Deluxe rooms (4 guests, 4,200), each with its own amenity: the wizard puts every
 * amenity back on the room it was chosen for, and saving sends each one with its room type AND option, so the server keeps it on that room.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReservationEditOptionsTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;
    private FakeApi api;
    private RoomRepository repository;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        api = new FakeApi();
        repository = ScreenTestSupport.freshRepository(app, api);
    }

    private static String day(int offset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, offset);
        return new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US).format(c.getTime());
    }

    /** Deluxe (3,500, 2 guests) with one other option "r4200c4" (4,200, 4 guests). */
    private Room deluxeWithOption() {
        RoomTypeDto dto = new RoomTypeDto();
        dto.id = 1;
        dto.name = "Deluxe";
        dto.capacity = 2;
        dto.rate = "3500.00";
        dto.available_count = 3;
        RoomOptionDto o = new RoomOptionDto();
        o.option_key = "r4200c4";
        o.price = 4200;
        o.capacity = 4;
        o.available_count = 2;
        dto.other_options = Collections.singletonList(o);
        return ApiMapper.toRoom(dto);
    }

    private void cacheRooms(Room deluxe) {
        try {
            java.lang.reflect.Method m = RoomRepository.class.getDeclaredMethod("setRoomsForTesting", List.class);
            m.setAccessible(true);
            m.invoke(repository, Collections.singletonList(deluxe));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 1 standard Deluxe + 2 "other" Deluxe rooms; Breakfast x1 on the standard room, Towel x2 on the OTHER room 2 (as the server reports them). */
    private Booking reservation() {
        Booking b = new Booking("77", "1", "Deluxe", "Deluxe", day(1), day(3), 3, 25400.0, "Pending", day(0));
        b.setTotalIncludesAmenities(true);
        b.setRoomTypeId("1");
        b.setRoomsRequested(3);
        b.setAdults(4);
        b.setGuestFirstName("Ana");
        b.setGuestLastName("Cruz");
        b.setPaymentMethod("CASH");
        b.setRooms(Arrays.asList(
                new BookingRoom("1", "Deluxe", 1, 3500.0, 2, 7000.0, null),
                new BookingRoom(ApiMapper.roomKey("1", "r4200c4"), "Deluxe (up to 4 guests)", 2, 4200.0, 2, 16800.0, null)));
        b.setAmenities(Arrays.asList(
                new BookingAmenity("9", "Breakfast", 1, 150.0, 150.0, "Deluxe (room 1)", ApiMapper.roomKey("1", "std"), 1),
                new BookingAmenity("8", "Towel", 2, 100.0, 200.0, "Deluxe (up to 4 guests) (room 2)", ApiMapper.roomKey("1", "r4200c4"), 2)));
        return b;
    }

    private BookingWizardActivity openEdit(Booking b) {
        ActivityController<BookingWizardActivity> controller =
                Robolectric.buildActivity(BookingWizardActivity.class, BookingWizardActivity.newEditIntent(app, b));
        controller.setup();
        shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }

    @Test
    public void everyAmenityGoesBackToTheRoomItWasChosenFor() {
        cacheRooms(deluxeWithOption());
        BookingWizardState s = openEdit(reservation()).getState();

        assertEquals("1 standard + 2 other rooms", 3, s.selectedRooms.size());
        BookingWizardState.Stay stay = s.committedStays().get(0);
        assertEquals("Breakfast stays on the standard room 1", 1, stay.amenityQty("1#1", "9"));
        assertEquals("Towel stays on the OTHER Deluxe room 2, not on a standard room", 2, stay.amenityQty("1:r4200c4#2", "8"));
        assertEquals(0, stay.amenityQty("1#1", "8"));
        assertEquals(0, stay.amenityQty("1:r4200c4#1", "8"));
        assertEquals(0, stay.amenityQty("1:r4200c4#2", "9"));
    }

    @Test
    public void savingSendsEachAmenityWithItsRoomTypeAndOption() {
        cacheRooms(deluxeWithOption());
        BookingWizardState s = openEdit(reservation()).getState();
        BookingWizardState.Stay stay = s.committedStays().get(0);

        List<List<Room>> groups = new ArrayList<>(stay.roomsGroupedByType().values());
        repository.updateReservationFull("77", groups, s.checkIn, s.checkOut, 4, 0, "None", null, false, new ArrayList<>(), stay.amenities, stay, null);
        shadowOf(Looper.getMainLooper()).idle();

        ReservationUpdateRequest request = (ReservationUpdateRequest) api.lastArgs.get("updateReservation")[1];
        assertNotNull(request.amenities);
        AmenitySelectionDto breakfast = null, towel = null;
        for (AmenitySelectionDto a : request.amenities) {
            if (a.amenity_id == 9) breakfast = a;
            if (a.amenity_id == 8) towel = a;
        }
        assertNotNull(breakfast);
        assertNotNull(towel);
        assertEquals(Long.valueOf(1), breakfast.room_type_id);
        assertNull("the standard option sends no option", breakfast.option_key);
        assertEquals(Integer.valueOf(1), breakfast.room_slot);
        assertEquals("a numeric room type id, never \"1:r4200c4\"", Long.valueOf(1), towel.room_type_id);
        assertEquals("r4200c4", towel.option_key);
        assertEquals(Integer.valueOf(2), towel.room_slot);
        assertEquals(2, request.rooms.size());
        assertEquals("r4200c4", request.rooms.get(1).option_key);
    }

    @Test
    public void theServersRoomAndOptionBecomeTheWizardsRoomId() {
        assertEquals("5", ApiMapper.roomKey("5", null));
        assertEquals("5", ApiMapper.roomKey("5", "std"));
        assertEquals("5", ApiMapper.roomKey("5", ""));
        assertEquals("5:r4200c4", ApiMapper.roomKey("5", "r4200c4"));
        assertNull(ApiMapper.roomKey(null, "r4200c4"));
    }
}
