package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** The hotel-wide facts live in HotelPolicy only, and raw server values are displayed in Title Case through DisplayLabels. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class HotelPolicyAndDisplayLabelsTest {

    @org.junit.Rule
    public final ThemeResetRule themeReset = new ThemeResetRule();

    private final Context app = ApplicationProvider.getApplicationContext();

    // ---- HotelPolicy

    @Test
    public void theFactsAreStatedOnce_andFormattedFromThere() {
        assertEquals("2:00 PM", HotelPolicy.checkInText());
        assertEquals("12:00 PM", HotelPolicy.checkOutText());
        assertEquals("From 2:00 PM", HotelPolicy.checkInFrom(app));
        assertEquals("Until 12:00 PM", HotelPolicy.checkOutUntil(app));
        assertEquals("Non-refundable", HotelPolicy.refundValue(app));
        assertEquals("No refund", HotelPolicy.refundChip(app));
        assertEquals("Front Desk: 24 / 7\nCheck-In: From 2:00 PM\nCheck-Out: Until 12:00 PM", HotelPolicy.hoursBlock(app));
        String policies = HotelPolicy.policiesText(app);
        assertTrue(policies, policies.contains("Check-In Time: Starts at 2:00 PM."));
        assertTrue(policies, policies.contains("Check-Out Time: Until 12:00 PM."));
        assertTrue(policies, policies.contains("No Refund Policy"));
    }

    @Test
    public void theStayCalendarUsesTheSameTimes() {
        StayCalendarModel.Entry in = new StayCalendarModel(java.util.Arrays.asList(booking("Confirmed")))
                .entriesBetween(java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 3)).get(0);
        assertEquals(HotelPolicy.CHECK_IN_TIME, in.time());
    }

    private static Booking booking(String status) {
        Booking b = new Booking("1", "1", "Deluxe", "Deluxe", "Nov 01, 2026", "Nov 03, 2026", 2, 1000, status, "Oct 01, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        return b;
    }

    private static void collect(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".java") || f.getName().endsWith(".xml")) out.add(f);
        }
    }

    @Test
    public void noOtherFileHardcodesTheTimes() throws Exception {
        File src = new File("src/main");
        if (!src.isDirectory()) src = new File("app/src/main");
        List<File> files = new ArrayList<>();
        collect(src, files);
        List<String> bad = new ArrayList<>();
        for (File f : files) {
            if (f.getName().equals("HotelPolicy.java")) continue;
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (text.matches("(?s).*(\\b2:00 ?PM|\\b12:00 ?PM|LocalTime\\.of\\(1[24], ?0).*")) bad.add(f.getName());
        }
        assertTrue("hardcoded check-in/check-out times in: " + bad, bad.isEmpty());
    }

    // ---- DisplayLabels

    @Test
    public void statusesAreShownInTitleOrSentenceCase() {
        assertEquals("Confirmed", DisplayLabels.status("CONFIRMED"));
        assertEquals("Checked in", DisplayLabels.status("Checked-In"));
        assertEquals("Checked out", DisplayLabels.status("CHECKED_OUT"));
        assertEquals("Pending", DisplayLabels.status("pending"));
        assertEquals("No-show", DisplayLabels.status("NO-SHOW"));
        assertEquals("Cancelled", DisplayLabels.status("  Cancelled "));
        assertEquals("", DisplayLabels.status(null));
        assertEquals("", DisplayLabels.status("   "));
    }

    @Test
    public void paymentMethodsAreShownAsGCashAndCash() {
        assertEquals("GCash", DisplayLabels.paymentMethod(app, "GCASH"));
        assertEquals("GCash", DisplayLabels.paymentMethod(app, "gcash"));
        assertEquals("Cash", DisplayLabels.paymentMethod(app, "CASH"));
        assertEquals("Bank transfer", DisplayLabels.paymentMethod(app, "BANK_TRANSFER"));
        assertEquals("", DisplayLabels.paymentMethod(app, null));
    }

    @Test
    public void theRawValuesStillDriveTheLogic_whileTheDisplayedTextIsTitleCase() {
        Booking b = booking("CHECKED-IN");
        b.setPaymentMethod("GCASH");
        // logic reads the raw values...
        assertTrue("GCASH".equalsIgnoreCase(b.getPaymentMethod()));
        assertEquals("CHECKED-IN", b.getStatus());
        // ...the badge shows them formatted.
        String label = BookingStatusPresenter.computeStatusLabel(app, b);
        assertEquals("Checked in", label);
        assertFalse(label.equals(label.toUpperCase()));
        Booking pending = booking("Pending");
        assertEquals("Pending", BookingStatusPresenter.computeStatusLabel(app, pending));
        assertEquals("the no-show label is sentence case too", "No-show", app.getString(R.string.status_no_show));
        assertEquals("Transaction failed", app.getString(R.string.transaction_failed_label));
    }
}
