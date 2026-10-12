package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

/** Every format the saved date of birth can arrive in must resolve to the same calendar day. */
public class ProfileDobParsingTest {

    private static String iso(String value) {
        java.util.Date d = ProfileManagementActivity.parseDob(value);
        assertNotNull(value, d);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
    }

    @Test
    public void everyStoredFormatMapsToTheSameDay() {
        assertEquals("1990-05-09", iso("1990-05-09"));
        assertEquals("1990-05-09", iso("1990-05-09T00:00:00.000000Z"));
        assertEquals("1990-05-09", iso("05/09/1990"));
        assertEquals("1990-05-09", iso("May 09, 1990"));
    }

    @Test
    public void notShiftedByTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[]{"UTC", "Asia/Manila", "America/Los_Angeles", "Pacific/Auckland"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals(zone, "1990-05-09", iso("1990-05-09T00:00:00.000000Z"));
                assertEquals(zone, "1990-05-09", iso("May 09, 1990"));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void garbageIsNullNotToday() {
        assertNull(ProfileManagementActivity.parseDob(""));
        assertNull(ProfileManagementActivity.parseDob(null));
        assertNull(ProfileManagementActivity.parseDob("not a date"));
    }
}
