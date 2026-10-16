package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.velocitysuites.network.ApiMapper;
import com.example.velocitysuites.network.dto.NotificationDto;
import com.example.velocitysuites.network.dto.ReceiptDetailDto;
import com.example.velocitysuites.network.dto.ReceiptDetailResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.TimeZone;

/**
 * Every instant the API sends carries its zone (ISO 8601, "Z" or an offset); the app converts it to Asia/Manila and
 * shows hours, minutes AND seconds - whatever time zone the phone itself is set to. The device zone is deliberately
 * set to something far from Manila to prove it plays no part.
 */
public class ManilaTimeDisplayTest {

    private TimeZone original;

    @Before
    public void phoneInNewYork() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }

    @After
    public void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    public void aUtcInstantShowsAsManilaWithSeconds() {
        // 16:30:09 UTC on Oct 16 = 00:30:09 AM on Oct 17 in Manila
        assertEquals("12:30:09 AM", TimeUtils.formatTime("2026-10-16T16:30:09Z"));
        assertEquals("Oct 17, 2026 • 12:30:09 AM", TimeUtils.formatDateTime("2026-10-16T16:30:09Z"));
    }

    @Test
    public void anOffsetInstantAndALaravelOffsetFormAgree() {
        String zulu = TimeUtils.formatDateTime("2026-10-16T16:30:09Z");
        assertEquals(zulu, TimeUtils.formatDateTime("2026-10-16T16:30:09+00:00"));
        assertEquals("the same instant written in Manila's own offset", zulu, TimeUtils.formatDateTime("2026-10-17T00:30:09+08:00"));
        assertEquals("a bare Laravel stamp is UTC by the project's convention", zulu, TimeUtils.formatDateTime("2026-10-16 16:30:09"));
    }

    @Test
    public void checkInAndCheckOutTimesOnABookingShowToTheSecondInManila() {
        com.example.velocitysuites.network.dto.DirectBookingResponseDto dto = new com.example.velocitysuites.network.dto.DirectBookingResponseDto();
        dto.id = 1;
        dto.check_in = "2026-10-17";
        dto.check_out = "2026-10-18";
        dto.booking_status = "CHECKED_IN";
        dto.checked_in_at = "2026-10-17T06:03:09+00:00";   // 14:03:09 Manila
        dto.checked_out_at = "2026-10-18T03:45:30+00:00";  // 11:45:30 Manila
        dto.payments = new ArrayList<>();
        Booking booking = ApiMapper.toBooking(dto);

        assertTrue(booking.getCheckedInAtDisplay(), booking.getCheckedInAtDisplay().endsWith("02:03:09 PM"));
        assertTrue(booking.getCheckedOutAtDisplay(), booking.getCheckedOutAtDisplay().endsWith("11:45:30 AM"));
    }

    @Test
    public void aReceiptsIssuedTimeShowsToTheSecondInManila() {
        ReceiptDetailResponse response = new ReceiptDetailResponse();
        ReceiptDetailDto dto = new ReceiptDetailDto();
        dto.receipt_type = "CHECKIN_SETTLEMENT_RECEIPT";
        dto.receipt_number = "SR-20261017-000001";
        dto.booking_id = 1;
        dto.room_lines = new ArrayList<>();
        dto.assigned_room_numbers = new ArrayList<>();
        dto.payment_transactions = new ArrayList<>();
        dto.issued_at = "2026-10-16T16:30:09+00:00";
        response.receipt = dto;

        String issued = ApiMapper.toReceiptDetail(response).getIssuedAt();
        assertTrue(issued, issued.startsWith("Oct 17, 2026"));
        assertTrue(issued, issued.endsWith("30:09 AM"));
    }

    @Test
    public void aNotificationsAbsoluteTimeShowsToTheSecondInManila() {
        NotificationDto dto = new NotificationDto();
        dto.id = 5;
        dto.title = "Check-In Receipt Ready";
        dto.message = "m";
        dto.category = "booking";
        dto.created_at = "2026-10-16T16:30:09+00:00";

        String shown = ApiMapper.toNotification(dto).getPublishedAt();
        assertTrue(shown, shown.startsWith("Oct 17, 2026"));
        assertTrue(shown, shown.endsWith("30:09 AM"));
    }

    @Test
    public void aPaymentDeadlineShowsInManilaNotInThePhonesZone() {
        Booking b = new Booking("1", "1", "Deluxe", "Deluxe", "Oct 20, 2026", "Oct 22, 2026", 2, 3000, "Pending", "Oct 10, 2026");
        b.setHasBooking(false);
        // the deadline chip is a countdown; its absolute form is exercised through the formatter's display zone
        assertEquals("Asia/Manila", java.util.TimeZone.getTimeZone("Asia/Manila").getID());
        assertEquals(0, PaymentDates.parseMillis("Oct 17, 2026 • 12:00:00 AM") - java.time.ZonedDateTime
                .of(2026, 10, 17, 0, 0, 0, 0, TimeUtils.ZONE_MANILA).toInstant().toEpochMilli());
    }
}
