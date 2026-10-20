package com.example.velocitysuites.ui;

import static org.junit.Assert.assertEquals;
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

import com.example.velocitysuites.Booking;
import com.example.velocitysuites.BookingDetailsActivity;
import com.example.velocitysuites.PaymentReceiptActivity;
import com.example.velocitysuites.R;
import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.BookingDto;
import com.example.velocitysuites.network.dto.ReservationDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * One stay, one number, and never the wrong word for it: a reservation that has not converted is "Reservation #606"
 * everywhere; once it converts it is "Booking #521" - the bookings.id the receptionist web shows - and the
 * reservation's own 606 is no longer shown as a booking number.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BookingIdLabelsTest {

    @org.junit.Rule
    public final com.example.velocitysuites.ThemeResetRule themeReset = new com.example.velocitysuites.ThemeResetRule();

    private Context app;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        ScreenTestSupport.freshRepository(app, new ScreenTestSupport.FakeApi());
        ScreenTestSupport.goOnline(app);
    }

    private static Booking unconverted() {
        return ApiMapper.toBooking(ScreenTestSupport.reservation(606, 1800));
    }

    private static Booking converted() {
        ReservationDto dto = ScreenTestSupport.reservation(606, 1800,
                ScreenTestSupport.payment(552, "900.00", "completed", "gcash"));
        dto.status = "CONVERTED_TO_BOOKING";
        dto.booking = new BookingDto();
        dto.booking.id = 521;
        dto.booking.booking_status = "CONFIRMED";
        return ApiMapper.toBooking(dto);
    }

    private static void collect(View view, List<String> out) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE) {
            CharSequence t = ((TextView) view).getText();
            if (t != null && t.length() > 0) out.add(t.toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    private String headline(Booking booking) {
        Intent intent = new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, booking);
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        return ((TextView) a.findViewById(R.id.tvDetailsRoomHeadline)).getText().toString();
    }

    private List<String> allTexts(BookingDetailsActivity a) {
        List<String> out = new ArrayList<>();
        collect(a.getWindow().getDecorView(), out);
        return out;
    }

    @Test
    public void detailsHeadline_unconvertedReservation_isReservationNumber() {
        assertEquals("Reservation #606", headline(unconverted()));
    }

    @Test
    public void detailsHeadline_convertedReservation_isTheBookingNumber() {
        assertEquals("Booking #521", headline(converted()));
    }

    @Test
    public void details_convertedReservation_neverShowsTheReservationIdAsABooking() {
        Intent intent = new Intent(app, BookingDetailsActivity.class).putExtra(BookingDetailsActivity.EXTRA_BOOKING, converted());
        BookingDetailsActivity a = Robolectric.buildActivity(BookingDetailsActivity.class, intent).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        for (String t : allTexts(a)) {
            assertTrue("'" + t + "' must not call 606 a booking", !t.contains("Booking #606") && !t.contains("Booking ID: 606"));
        }
    }

    private String receiptIdRow(Booking booking) {
        booking.setStaffVerified(true);
        PaymentReceiptActivity a = Robolectric.buildActivity(PaymentReceiptActivity.class,
                PaymentReceiptActivity.newIntent(app, booking, false)).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        View row = a.findViewById(R.id.rowBookingId);
        return ((TextView) row.findViewById(R.id.tvRowLabel)).getText() + "|" + ((TextView) row.findViewById(R.id.tvRowValue)).getText();
    }

    @Test
    public void receipt_convertedReservation_showsBookingIdLabelAndNumber() {
        assertEquals(app.getString(R.string.receipt_label_booking_id) + "|521", receiptIdRow(converted()));
    }

    @Test
    public void receipt_unconvertedReservation_showsReservationIdLabelAndNumber() {
        Booking b = ApiMapper.toBooking(ScreenTestSupport.reservation(606, 1800,
                ScreenTestSupport.payment(552, "900.00", "completed", "gcash")));
        assertEquals(app.getString(R.string.receipt_label_reservation_id) + "|606", receiptIdRow(b));
    }
}
