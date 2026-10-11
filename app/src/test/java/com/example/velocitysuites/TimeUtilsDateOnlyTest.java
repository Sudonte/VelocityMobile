package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/** The server sends some dates (announcement publish dates) with no time at all; they must read as that day, not "N/A". */
public class TimeUtilsDateOnlyTest {

    @Test
    public void bareDateIsThatDayInManila() {
        assertNotNull(TimeUtils.parseInstant("2026-10-05"));
        assertEquals("Oct 5, 2026", TimeUtils.formatDate("2026-10-05"));
    }
}
