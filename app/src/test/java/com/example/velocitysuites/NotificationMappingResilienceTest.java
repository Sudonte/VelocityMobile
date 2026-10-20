package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.network.dto.NotificationDto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** One bad row in a notifications page must not throw out of the (main-thread) Retrofit callback. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = com.example.velocitysuites.ui.TestApplication.class)
public class NotificationMappingResilienceTest {

    @org.junit.Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private static NotificationDto row(long id, String createdAt, String category) {
        NotificationDto dto = new NotificationDto();
        dto.id = id;
        dto.title = "Payment verified";
        dto.message = "Your payment was verified.";
        dto.category = category;
        dto.created_at = createdAt;
        return dto;
    }

    @Test
    public void aNullRowIsSkipped_theOthersSurvive() {
        List<Notification> out = RoomRepository.mapNotificationsSkippingMalformed(
                Arrays.asList(row(1, "2026-09-20T06:15:00.000000Z", "payment"), null, row(3, "2026-09-21T06:15:00.000000Z", "booking")));
        assertEquals(2, out.size());
        assertEquals("1", out.get(0).getId());
        assertEquals("3", out.get(1).getId());
    }

    @Test
    public void rowsWithNoTimestampOrCategory_stillMap() {
        List<Notification> out = RoomRepository.mapNotificationsSkippingMalformed(
                Arrays.asList(row(7, null, null), row(8, "not-a-date", "mystery")));
        assertEquals(2, out.size());
    }

    @Test
    public void aNullOrEmptyPageGivesAnEmptyList_notACrash() {
        assertTrue(RoomRepository.mapNotificationsSkippingMalformed(null).isEmpty());
        assertTrue(RoomRepository.mapNotificationsSkippingMalformed(new ArrayList<>()).isEmpty());
    }
}
