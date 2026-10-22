package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/** The "Group · Stay 1 of 2" label: a single stay has none, and each stay of a group knows its place whatever order the list shows. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = com.example.velocitysuites.ui.TestApplication.class)
public class GroupStaysTest {

    private static Booking stay(String id, Long groupId, Long... siblings) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 02, 2026", 2, 3000, "Active", "Oct 01, 2026");
        b.setHasBooking(true);
        if (groupId != null) b.setGroup(groupId, Arrays.asList(siblings));
        return b;
    }

    @Test
    public void aSingleStayHasNoLabel() {
        Context c = ApplicationProvider.getApplicationContext();
        assertNull(GroupStays.label(c, stay("5", null)));
        assertEquals(1, GroupStays.size(stay("5", null)));
    }

    @Test
    public void stagesAreNumberedByTheirOrderInTheGroup() {
        Context c = ApplicationProvider.getApplicationContext();
        assertEquals("Group · Stay 1 of 2", GroupStays.label(c, stay("10", 3L, 11L)));
        assertEquals("Group · Stay 2 of 2", GroupStays.label(c, stay("11", 3L, 10L)));
        assertEquals("Group · Stay 3 of 3", GroupStays.label(c, stay("12", 3L, 10L, 11L)));
    }

    @Test
    public void aGroupWithNoSiblingsListedIsNotLabelled() {
        Context c = ApplicationProvider.getApplicationContext();
        assertNull(GroupStays.label(c, stay("10", 3L, new Long[0])));
        assertEquals(Collections.singletonList("10"), Collections.singletonList(GroupStays.members(c, stay("10", null)).get(0).getId()));
    }
}
