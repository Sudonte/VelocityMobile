package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
 * The server's itemized stay as the guest sees it: the BOOKED nights (the hotel has no refunds, so an early check-out is still
 * charged in full and there is no stay-length adjustment), the total, and the per-room lines that replace the booked lines on the
 * receipt and Booking Details.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class StayInfoTest {

    @org.junit.Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private static StayBillDto.Room room(String number, String type, double rate, int nights, double subtotal, String status) {
        StayBillDto.Room r = new StayBillDto.Room();
        r.room_number = number;
        r.room_type = type;
        r.room_type_id = "7";
        r.rate = rate;
        r.nights = nights;
        r.subtotal = subtotal;
        r.status = status;
        r.checked_out_on = "checked_out".equals(status) ? "2026-10-24" : null;
        return r;
    }

    /** Booked Oct 23 - Oct 25 (2 nights), the guest left on Oct 24: still 2 nights, one P1,000 room. */
    private static StayBillDto earlyLeaver() {
        StayBillDto d = new StayBillDto();
        d.check_in = "2026-10-23";
        d.scheduled_check_out = "2026-10-25";
        d.actual_check_out = "2026-10-25";
        d.scheduled_nights = 2;
        d.actual_nights = 2;
        d.room_charge = 2000;
        d.total = 2000;
        d.rooms = new ArrayList<>();
        d.rooms.add(room("101", "Deluxe", 1000, 2, 2000, "checked_out"));
        return d;
    }

    @Test
    public void nothingInHouseYetMeansNoItemizedStay() {
        assertNull(StayInfo.from(null));
        assertNull(StayInfo.from(new StayBillDto()));
    }

    @Test
    public void anEarlyCheckOutIsStillTheBookedNights() {
        StayInfo s = StayInfo.from(earlyLeaver());
        assertEquals(2, s.actualNights);
        assertEquals(2, s.scheduledNights);
        assertEquals(2000.0, s.roomCharge, 0.001);
        assertEquals(2000.0, s.total, 0.001);
    }

    @Test
    public void roomsOfTheSameTypeRateAndNightsAreOneLine() {
        StayBillDto d = earlyLeaver();
        d.rooms = new ArrayList<>();
        d.rooms.add(room("101", "Deluxe", 1000, 2, 2000, "checked_out"));
        d.rooms.add(room("102", "Deluxe", 1000, 2, 2000, "checked_out"));
        d.rooms.add(room("103", "Suite", 2000, 2, 4000, "active"));

        List<BookingRoom> lines = StayInfo.from(d).asBookingRooms();

        assertEquals(2, lines.size());
        assertEquals(2, lines.get(0).getQuantity());
        assertEquals(2, lines.get(0).getNights());
        assertEquals(4000.0, lines.get(0).getSubtotal(), 0.001);
        assertEquals(java.util.Arrays.asList("101", "102"), lines.get(0).getAssignedRoomNumbers());
        assertEquals(1, lines.get(1).getQuantity());
        assertEquals(4000.0, lines.get(1).getSubtotal(), 0.001);
    }

    @Test
    public void rowsReadTheBookedDatesTheNightsAndTheTotal_withNoActualCheckOutAndNoAdjustment() {
        Context c = ApplicationProvider.getApplicationContext();
        List<StayInfo.Row> rows = StayInfo.from(earlyLeaver()).rows(c);

        assertEquals(4, rows.size());
        assertEquals("Oct 23, 2026", rows.get(0).value);
        assertEquals("Oct 25, 2026", rows.get(1).value);
        assertEquals("2 nights", rows.get(2).value);
        assertEquals(c.getString(R.string.stay_total), rows.get(3).label);
        assertEquals("₱2,000.00", rows.get(3).value);
        for (StayInfo.Row row : rows) {
            assertEquals(row.label, false, row.label.toLowerCase().contains("actual") || row.label.toLowerCase().contains("adjust"));
        }
    }

    @Test
    public void approvedAdditionalAmenitiesGetTheirOwnRowsBeforeTheTotal() {
        Context c = ApplicationProvider.getApplicationContext();
        StayBillDto d = earlyLeaver();
        StayBillDto.Amenity bed = new StayBillDto.Amenity();
        bed.amenity_name = "Extra Bed";
        bed.quantity = 2;
        bed.unit_price = 200;
        bed.subtotal = 400;
        d.additional_amenities = new ArrayList<>();
        d.additional_amenities.add(bed);
        d.additional_amenities_total = 400;
        d.total = 2400;

        List<StayInfo.Row> rows = StayInfo.from(d).rows(c);

        assertEquals(5, rows.size());
        assertEquals(c.getString(R.string.stay_additional_amenity_label, "Extra Bed × 2"), rows.get(3).label);
        assertEquals(MoneyFormat.format(400), rows.get(3).value);
        assertEquals(c.getString(R.string.stay_total), rows.get(4).label);
        assertEquals(MoneyFormat.format(2400), rows.get(4).value);
    }
}
