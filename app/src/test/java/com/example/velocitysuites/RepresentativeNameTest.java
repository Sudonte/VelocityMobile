package com.example.velocitysuites;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.RepresentativeName.Problem;
import com.example.velocitysuites.RepresentativeName.Parts;
import com.example.velocitysuites.RepresentativeName.Result;
import com.example.velocitysuites.network.dto.ReservationRequest;
import com.example.velocitysuites.ui.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** The optional representative: Last name, First name, Middle name (optional) - validation, API payload, display, pre-fill. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class RepresentativeNameTest {

    @org.junit.Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private Context c;

    @Before
    public void setUp() {
        c = ApplicationProvider.getApplicationContext();
        c.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit()
                .putString("userFirstName", "Maria").putString("userLastName", "Santos").putString("userName", "Maria Santos").apply();
    }

    private static Result check(String last, String first, String middle) {
        return RepresentativeName.validate(new Parts(last, first, middle));
    }

    private static Booking stored(String first, String middle, String last) {
        Booking b = new Booking("1", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 02, 2026", 2, 1000, "Confirmed", "Oct 01, 2026");
        b.setGuestFirstName(first);
        b.setGuestMiddleName(middle);
        b.setGuestLastName(last);
        return b;
    }

    // ---- validation

    @Test
    public void allEmpty_isValid_andNeverBlocks() {
        assertTrue(check("", "", "").isValid());
        assertTrue(check("   ", null, " ").isValid());
        assertTrue(new Parts("", "", "").isBlank());
    }

    @Test
    public void lastNameOnly_asksForTheFirstName() {
        Result r = check("Dela Cruz", "", "");
        assertEquals(Problem.NONE, r.last);
        assertEquals(Problem.REQUIRED, r.first);
        assertEquals(Problem.NONE, r.middle);
        assertFalse(r.isValid());
    }

    @Test
    public void firstNameOnly_asksForTheLastName() {
        Result r = check("", "Juan", "");
        assertEquals(Problem.REQUIRED, r.last);
        assertEquals(Problem.NONE, r.first);
    }

    @Test
    public void firstAndLast_isValid_withOrWithoutAMiddleName() {
        assertTrue(check("Dela Cruz", "Juan", "").isValid());
        assertTrue(check("Dela Cruz", "Juan", "Santos").isValid());
        assertTrue("a one-letter middle name is allowed", check("Dela Cruz", "Juan", "S").isValid());
    }

    @Test
    public void middleOnly_asksForBothLastAndFirstName() {
        Result r = check("", "", "Santos");
        assertEquals(Problem.REQUIRED, r.last);
        assertEquals(Problem.REQUIRED, r.first);
        assertEquals(Problem.NONE, r.middle);
        assertFalse(r.isValid());
    }

    @Test
    public void characterRules_lettersIncludingNtildeAndAccents_spacesPeriodsHyphensApostrophes() {
        assertTrue(check("Peña", "José María", "Muñoz").isValid());
        assertTrue(check("O'Neil", "Mary-Ann", "A.").isValid());
        assertTrue(check("De la Cruz Jr.", "Juan", "").isValid());
        assertEquals(Problem.LETTERS_ONLY, check("Cruz2", "Juan", "").last);
        assertEquals(Problem.LETTERS_ONLY, check("Cruz", "Ju@n", "").first);
        assertEquals(Problem.LETTERS_ONLY, check("Cruz", "Juan", "S4ntos").middle);
        assertEquals("must start with a letter", Problem.LETTERS_ONLY, check("-Cruz", "Juan", "").last);
    }

    @Test
    public void lengthRules_twoToFortyForFirstAndLast_oneToFortyForMiddle() {
        assertEquals(Problem.TOO_SHORT, check("C", "Juan", "").last);
        assertEquals(Problem.TOO_SHORT, check("Cruz", "J", "").first);
        assertTrue(check("Cr", "Ju", "").isValid());
        String forty = "a".repeat(40);
        assertTrue(check(forty, forty, forty).isValid());
        String tooLong = "a".repeat(41);
        assertEquals(Problem.TOO_LONG, check(tooLong, "Juan", "").last);
        assertEquals(Problem.TOO_LONG, check("Cruz", tooLong, "").first);
        assertEquals(Problem.TOO_LONG, check("Cruz", "Juan", tooLong).middle);
        assertTrue("a long field name with spaces is trimmed first", check("  Dela   Cruz  ", " Juan ", "").isValid());
    }

    // ---- display

    @Test
    public void display_isLastNameCommaFirstNameMiddleName() {
        assertEquals("Dela Cruz, Juan Santos", new Parts("Dela Cruz", "Juan", "Santos").formatted());
        assertEquals("Dela Cruz, Juan", new Parts("Dela Cruz", "Juan", "").formatted());
        assertEquals("", new Parts("", "", "").formatted());
        assertEquals("Representative: Dela Cruz, Juan Santos — must present 1 valid ID",
                RepresentativeName.summaryLine(c, new Parts("Dela Cruz", "Juan", "Santos")));
        assertEquals("Representative: Dela Cruz, Juan — must present 1 valid ID",
                RepresentativeName.summaryLine(c, new Parts("Dela Cruz", "Juan", "")));
        assertEquals("Check-in by: account owner — present 1 valid ID", RepresentativeName.summaryLine(c, new Parts("", "", "")));
        assertEquals("Check-in by: account owner — present 1 valid ID", RepresentativeName.summaryLine(c, (Parts) null));
    }

    @Test
    public void storedTransactions_readAsRepresentativeOrAccountOwner() {
        Booking rep = stored("Juan", "Santos", "Dela Cruz");
        assertEquals("Dela Cruz, Juan Santos", RepresentativeName.representativeOf(c, rep));
        assertEquals("Representative", RepresentativeName.rowLabel(c, rep));
        assertEquals("Dela Cruz, Juan Santos — must present 1 valid ID", RepresentativeName.rowValue(c, rep));
        assertEquals("Representative: Dela Cruz, Juan Santos — must present 1 valid ID", RepresentativeName.summaryLine(c, rep));

        Booking owner = stored("Maria", "", "Santos");
        assertNull(RepresentativeName.representativeOf(c, owner));
        assertEquals("Check-in by", RepresentativeName.rowLabel(c, owner));
        assertEquals("Account owner — present 1 valid ID", RepresentativeName.rowValue(c, owner));
        assertEquals("Check-in by: account owner — present 1 valid ID", RepresentativeName.summaryLine(c, owner));
        assertEquals("Check-in by: account owner — present 1 valid ID", RepresentativeName.summaryLine(c, stored(null, null, null)));
    }

    // ---- API payload

    @Test
    public void payload_aRepresentativeGoesInThreeSeparateFields() {
        assertArrayEquals(new String[]{"Juan", "Santos", "Dela Cruz"},
                RepresentativeName.namesToSend(c, new Parts("Dela Cruz", "Juan", "Santos")));
        assertArrayEquals("no middle name: an empty middle (not sent)", new String[]{"Juan", "", "Dela Cruz"},
                RepresentativeName.namesToSend(c, new Parts("Dela Cruz", "Juan", "")));
    }

    @Test
    public void payload_emptyBlock_sendsTheAccountOwnersNames_asBefore() {
        assertArrayEquals(new String[]{"Maria", "", "Santos"}, RepresentativeName.namesToSend(c, new Parts("", "", "")));
    }

    @Test
    public void payload_theRequestCarriesTheNamesInTheApiFields() {
        String[] names = RepresentativeName.namesToSend(c, new Parts("Dela Cruz", "Juan", "Santos"));
        ReservationRequest request = new ReservationRequest(1, 1, "2026-11-01", "2026-11-02", 2, 0, names[0], names[2]);
        request.guest_middle_name = names[1].isEmpty() ? null : names[1];
        assertEquals("Juan", request.guest_first_name);
        assertEquals("Santos", request.guest_middle_name);
        assertEquals("Dela Cruz", request.guest_last_name);

        String[] none = RepresentativeName.namesToSend(c, new Parts("", "", ""));
        ReservationRequest owner = new ReservationRequest(1, 1, "2026-11-01", "2026-11-02", 2, 0, none[0], none[2]);
        owner.guest_middle_name = none[1].isEmpty() ? null : none[1];
        assertEquals("Maria", owner.guest_first_name);
        assertNull("no middle name is sent", owner.guest_middle_name);
        assertEquals("Santos", owner.guest_last_name);
    }

    // ---- pre-fill on edit / book again

    @Test
    public void editPrefill_fillsTheThreeFieldsFromSavedData_andLeavesThemEmptyForTheOwner() {
        Parts p = RepresentativeName.fieldsFor(c, stored("Juan", "Santos", "Dela Cruz"));
        assertEquals("Dela Cruz", p.last);
        assertEquals("Juan", p.first);
        assertEquals("Santos", p.middle);
        assertTrue(RepresentativeName.fieldsFor(c, stored("Maria", null, "Santos")).isBlank());
        assertTrue(RepresentativeName.fieldsFor(c, stored(null, null, null)).isBlank());
    }

    @Test
    public void stayGuestInTheAppsOrder_forPaymentAndReceiptRows() {
        assertEquals("Dela Cruz, Juan Santos", RepresentativeName.stayGuestFormatted(stored("Juan", "Santos", "Dela Cruz")));
        assertEquals("Santos, Maria", RepresentativeName.stayGuestFormatted(stored("Maria", null, "Santos")));
        assertEquals("", RepresentativeName.stayGuestFormatted(stored(null, null, null)));
        // a receipt that only has the composed name keeps it as sent
        assertEquals("Juan Dela Cruz", RepresentativeName.representativeOf(c, "Juan Dela Cruz"));
    }
}
