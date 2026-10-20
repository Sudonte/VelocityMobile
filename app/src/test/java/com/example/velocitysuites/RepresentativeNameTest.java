package com.example.velocitysuites;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** The optional representative name: validation, how it is sent, and the lines every screen shows. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class RepresentativeNameTest {

    private Context c;

    @Before
    public void setUp() {
        c = ApplicationProvider.getApplicationContext();
        c.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit()
                .putString("userFirstName", "Maria").putString("userLastName", "Santos").putString("userName", "Maria Santos").apply();
    }

    private static Booking booking(String first, String last) {
        Booking b = new Booking("1", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 02, 2026", 2, 1000, "Confirmed", "Oct 01, 2026");
        b.setGuestFirstName(first);
        b.setGuestLastName(last);
        return b;
    }

    @Test
    public void empty_isAlwaysValid_filledIsChecked() {
        assertTrue(RepresentativeName.isValid(null));
        assertTrue(RepresentativeName.isValid(""));
        assertTrue(RepresentativeName.isValid("    "));
        assertTrue(RepresentativeName.isValid("Juan Dela Cruz"));
        assertTrue(RepresentativeName.isValid("  Mary-Ann O'Neil Jr.  "));
        assertTrue(RepresentativeName.isValid("Jos\u00e9 Pe\u00f1a"));
        assertFalse("one word is not a full name", RepresentativeName.isValid("Juan"));
        assertFalse(RepresentativeName.isValid("J"));
        assertFalse("digits", RepresentativeName.isValid("Juan 2 Cruz"));
        assertFalse("symbols", RepresentativeName.isValid("Juan @ Cruz"));
        assertFalse("starts with a symbol", RepresentativeName.isValid("-Juan Cruz"));
        assertFalse("too long", RepresentativeName.isValid("Juan " + "a".repeat(60)));
        assertTrue("exactly 60", RepresentativeName.isValid("Ab " + "c".repeat(57)));
    }

    @Test
    public void aRepresentativeIsSplitForTheApi_andComposesBackToTheSameName() {
        assertArrayEquals(new String[]{"Juan Dela", "Cruz"}, RepresentativeName.split("  Juan   Dela Cruz "));
        assertArrayEquals(new String[]{"Ana", "Reyes"}, RepresentativeName.split("Ana Reyes"));
        assertNull(RepresentativeName.split("  "));
        assertArrayEquals(new String[]{"Juan Dela", "Cruz"}, RepresentativeName.namesToSend(c, "Juan Dela Cruz"));
        assertEquals("Juan Dela Cruz", booking("Juan Dela", "Cruz").getRepresentativeName());
    }

    @Test
    public void blank_sendsTheAccountOwnersName() {
        assertArrayEquals(new String[]{"Maria", "Santos"}, RepresentativeName.namesToSend(c, ""));
        assertArrayEquals(new String[]{"Maria", "Santos"}, RepresentativeName.namesToSend(c, null));
    }

    @Test
    public void storedTransactions_readAsRepresentativeOrAccountOwner() {
        Booking rep = booking("Juan Dela", "Cruz");
        assertEquals("Juan Dela Cruz", RepresentativeName.representativeOf(c, rep));
        assertEquals("Representative: Juan Dela Cruz \u2014 must present 1 valid ID", RepresentativeName.summaryLine(c, rep));
        assertEquals("Representative", RepresentativeName.rowLabel(c, rep));
        assertEquals("Juan Dela Cruz \u2014 must present 1 valid ID", RepresentativeName.rowValue(c, rep));
        assertEquals("Juan Dela Cruz", RepresentativeName.fieldValueFor(c, rep));

        Booking owner = booking("Maria", "Santos");
        assertNull(RepresentativeName.representativeOf(c, owner));
        assertEquals("Check-in by: account owner \u2014 present 1 valid ID", RepresentativeName.summaryLine(c, owner));
        assertEquals("Check-in by", RepresentativeName.rowLabel(c, owner));
        assertEquals("Account owner \u2014 present 1 valid ID", RepresentativeName.rowValue(c, owner));
        assertEquals("the field starts empty when the owner checks in", "", RepresentativeName.fieldValueFor(c, owner));

        Booking none = booking(null, null);
        assertEquals("Check-in by: account owner \u2014 present 1 valid ID", RepresentativeName.summaryLine(c, none));
        assertEquals("Check-in by: account owner \u2014 present 1 valid ID", RepresentativeName.summaryLine(c, ""));
        assertEquals("Representative: Ana Cruz \u2014 must present 1 valid ID", RepresentativeName.summaryLine(c, "Ana Cruz"));
    }
}
