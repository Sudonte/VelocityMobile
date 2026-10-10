package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.network.dto.StayBillDto;
import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's itemized stay as the guest sees it: actual vs booked nights, the extra nights' charge, the updated
 * total, and the per-room lines (each room's own nights) that replace the booked lines on the receipt and Booking Details.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class StayInfoTest {

    private static StayBillDto.Room room(String number, String type, double rate, int nights, int extra, double subtotal, String status) {
        StayBillDto.Room r = new StayBillDto.Room();
        r.room_number = number;
        r.room_type = type;
        r.room_type_id = "7";
        r.rate = rate;
        r.nights = nights;
        r.extra_nights = extra;
        r.subtotal = subtotal;
        r.status = status;
        r.checked_out_on = "checked_out".equals(status) ? "2026-10-10" : null;
        return r;
    }

    /** Booked Oct 5 - Oct 6 (1 night), checked out Oct 10 (5 nights) - one ₱1,000 room. */
    private static StayBillDto lateStay() {
        StayBillDto d = new StayBillDto();
        d.check_in = "2026-10-05";
        d.scheduled_check_out = "2026-10-06";
        d.actual_check_out = "2026-10-10";
        d.scheduled_nights = 1;
        d.actual_nights = 5;
        d.extra_nights = 4;
        d.room_charge = 5000;
        d.extra_nights_charge = 4000;
        d.total = 5000;
        d.rooms = new ArrayList<>();
        d.rooms.add(room("101", "Deluxe", 1000, 5, 4, 5000, "checked_out"));
        return d;
    }

    @Test
    public void nothingInHouseYetMeansNoItemizedStay() {
        assertNull(StayInfo.from(null));
        assertNull(StayInfo.from(new StayBillDto()));
    }

    @Test
    public void aLateCheckOutIsLateWithItsExtraNights() {
        StayInfo s = StayInfo.from(lateStay());
        assertTrue(s.isLate());
        assertFalse(s.isEarly());
        assertEquals(5, s.actualNights);
        assertEquals(1, s.scheduledNights);
        assertEquals(4, s.extraNights);
        assertEquals(4000.0, s.extraNightsCharge, 0.001);
    }

    @Test
    public void anOnTimeStayDoesNotDifferFromBooked() {
        StayBillDto d = lateStay();
        d.actual_check_out = "2026-10-06";
        d.actual_nights = 1;
        d.extra_nights = 0;
        d.extra_nights_charge = 0;
        assertFalse(StayInfo.from(d).differsFromBooked());
    }

    @Test
    public void roomsOfTheSameTypeRateAndNightsAreOneLineAndAnExtendedRoomIsItsOwn() {
        StayBillDto d = lateStay();
        d.rooms = new ArrayList<>();
        d.rooms.add(room("101", "Deluxe", 1000, 2, 0, 2000, "checked_out"));
        d.rooms.add(room("102", "Deluxe", 1000, 2, 0, 2000, "checked_out"));
        d.rooms.add(room("103", "Deluxe", 1000, 5, 3, 5000, "active"));

        List<BookingRoom> lines = StayInfo.from(d).asBookingRooms();

        assertEquals(2, lines.size());
        assertEquals(2, lines.get(0).getQuantity());
        assertEquals(2, lines.get(0).getNights());
        assertEquals(4000.0, lines.get(0).getSubtotal(), 0.001);
        assertEquals(java.util.Arrays.asList("101", "102"), lines.get(0).getAssignedRoomNumbers());
        assertEquals(1, lines.get(1).getQuantity());
        assertEquals(5, lines.get(1).getNights());
        assertEquals(5000.0, lines.get(1).getSubtotal(), 0.001);
    }

    @Test
    public void rowsReadScheduledActualNightsExtraChargeAndTotal() {
        Context c = ApplicationProvider.getApplicationContext();
        List<StayInfo.Row> rows = StayInfo.from(lateStay()).rows(c);

        assertEquals(6, rows.size());
        assertEquals("Oct 5, 2026", rows.get(0).value);
        assertEquals("Oct 6, 2026", rows.get(1).value);
        assertEquals("Oct 10, 2026", rows.get(2).value);
        assertEquals("5 nights (booked: 1)", rows.get(3).value);
        assertEquals("₱4,000.00", rows.get(4).value);
        assertEquals("₱5,000.00", rows.get(5).value);
    }

    @Test
    public void anOnTimeStayHasNoExtraChargeRow() {
        Context c = ApplicationProvider.getApplicationContext();
        StayBillDto d = lateStay();
        d.actual_check_out = "2026-10-06";
        d.actual_nights = 1;
        d.extra_nights = 0;
        d.extra_nights_charge = 0;
        d.total = 1000;

        List<StayInfo.Row> rows = StayInfo.from(d).rows(c);

        assertEquals(5, rows.size());
        assertEquals("1 night", rows.get(3).value);
    }
}
