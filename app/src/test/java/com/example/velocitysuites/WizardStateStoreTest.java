package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.Collections;

/**
 * An unfinished booking survives the app being killed in the background: the wizard state is written to a private cache file and read
 * back whole - stays and their confirmation, per-room amenities, names, ages, the discount and the picked image - for the same kind of
 * wizard only, and gone once cleared.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = com.example.velocitysuites.ui.TestApplication.class)
public class WizardStateStoreTest {

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        WizardStateStore.clear(app);
    }

    @After
    public void tearDown() {
        WizardStateStore.clear(app);
    }

    private static Room room(String id, double price) {
        return new Room(id, "Room " + id, "Deluxe", 2, price, "desc", 0, true, Collections.<RoomAmenity>emptyList(), "Queen", "24 sqm", "No smoking");
    }

    private static Calendar day(int offset) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 1 + offset, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private static BookingWizardState filledState() {
        BookingWizardState s = new BookingWizardState(BookingWizardState.Mode.BOOKING);
        s.checkIn = day(0);
        s.checkOut = day(2);
        s.selectedRooms.add(room("1", 1000));
        s.selectedRooms.add(room("1", 1000));
        s.confirmActive();
        s.addStay();
        s.checkIn = day(10);
        s.checkOut = day(11);
        s.selectedRooms.add(room("2", 3000));
        s.confirmActive();
        AddOnAmenity bed = new AddOnAmenity("9", "Extra bed", "Comfort", "A bed", 300, 5);
        s.committedStays().get(0).setAmenityQty("1#2", bed, 2);
        s.who = BookingWizardState.Who.OTHER;
        s.repLast = "Dela Cruz";
        s.repFirst = "Juan";
        s.adults = 2;
        s.children = 1;
        s.additionalGuests.add(new BookingAndReservationActivity.AdditionalGuest("Minor 1", 5, null, "Child"));
        s.setDiscount(new Discount("3", "Senior Citizen", "percentage", 20, "d", "active", null, null));
        s.idCardImageUri = Uri.parse("content://velocity.test/id.jpg");
        s.termsViewed = true;
        return s;
    }

    @Test
    public void aSavedWizardComesBackWhole() {
        WizardStateStore.save(app, filledState());
        BookingWizardState back = WizardStateStore.load(app, BookingWizardState.Mode.BOOKING);
        assertNotNull(back);
        assertEquals(2, back.stayCount());
        assertTrue("both stays are still confirmed", back.allStaysConfirmed());
        assertEquals(2, back.allStays().get(0).rooms.size());
        assertEquals("the per-room amenity choice", 2, back.allStays().get(0).amenityQty("1#2", "9"));
        assertEquals("and what the server will be sent: 2 beds at 300", 600.0, back.amenitiesTotal(), 0.001);
        assertEquals(BookingWizardState.Who.OTHER, back.who);
        assertEquals("Dela Cruz", back.repLast);
        assertEquals(1, back.children);
        assertEquals(5, back.additionalGuests.get(0).age);
        assertEquals("Senior Citizen", back.discount.getName());
        assertEquals("a picked image is kept as text", Uri.parse("content://velocity.test/id.jpg"), back.idCardImageUri);
        assertEquals(day(0).getTimeInMillis(), back.allStays().get(0).checkIn.getTimeInMillis());
        assertTrue(back.termsViewed);
        assertFalse(back.termsAccepted);
    }

    @Test
    public void aBookingWizardIsNeverRestoredAsAReservation() {
        WizardStateStore.save(app, filledState());
        assertNull(WizardStateStore.load(app, BookingWizardState.Mode.RESERVATION));
    }

    @Test
    public void clearedMeansGone_andABrokenFileIsIgnored() throws Exception {
        WizardStateStore.save(app, filledState());
        WizardStateStore.clear(app);
        assertNull(WizardStateStore.load(app, BookingWizardState.Mode.BOOKING));

        java.io.File f = new java.io.File(app.getCacheDir(), "wizard_state.bin");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(new byte[]{1, 2, 3, 4});
        }
        assertNull("garbage is not a wizard", WizardStateStore.load(app, BookingWizardState.Mode.BOOKING));
    }

    @Test
    public void hasUnsavedChoices_isFalseForAFreshWizard_andTrueOnceAnythingIsChosen() {
        assertFalse(new BookingWizardState(BookingWizardState.Mode.BOOKING).hasUnsavedChoices());
        BookingWizardState s = new BookingWizardState(BookingWizardState.Mode.RESERVATION);
        s.who = BookingWizardState.Who.OWNER;
        assertTrue(s.hasUnsavedChoices());
        assertTrue(filledState().hasUnsavedChoices());
    }
}
