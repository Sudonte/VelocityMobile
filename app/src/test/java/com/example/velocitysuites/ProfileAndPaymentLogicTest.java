package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The profile-completeness rule and donut, the payment balance colours, and the Filters sheet's room-type options. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class ProfileAndPaymentLogicTest {

    private static Context themed() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VelocitySuites);
    }

    private static ProfileCompleteness complete(String name, String email, String mobile, String gender, String dob, String photo) {
        return ProfileCompleteness.of(name, email, mobile, gender, dob, photo, "Guest User", "guest@example.com", "09123456789");
    }

    // ---- profile completeness ----

    @Test
    public void profile_fullyFilledIn_is100Percent_andNothingIsMissing() {
        ProfileCompleteness c = complete("Ana Reyes", "ana@example.com", "09171234567", "Female", "Jan 01, 1990", "https://x/photo.jpg");
        assertEquals(100, c.percent);
        assertTrue(c.missing.isEmpty());
        assertNull(c.missingLine(themed()));
    }

    @Test
    public void profile_partlyFilledIn_countsTheShareOfFilledFields_andNamesWhatIsMissing() {
        ProfileCompleteness c = complete("Ana Reyes", "ana@example.com", "", "Female", "Jan 01, 1990", "https://x/photo.jpg");
        assertEquals("5 of 6", 83, c.percent);
        assertEquals(Collections.singletonList(ProfileCompleteness.Field.MOBILE), c.missing);
        assertEquals("Add your phone number to complete your profile", c.missingLine(themed()));
    }

    @Test
    public void profile_aPlaceholderCountsAsMissing_andOnlyTwoItemsAreNamedThenPlusMore() {
        ProfileCompleteness c = complete("Guest User", "guest@example.com", "09123456789", "", null, "");
        assertEquals("nothing real has been entered", 0, c.percent);
        assertEquals(6, c.missing.size());
        assertEquals("Add your name, your email +4 more to complete your profile", c.missingLine(themed()));

        ProfileCompleteness two = complete("Ana", "ana@example.com", "09171234567", "", "", "https://x/p.jpg");
        assertEquals(66, two.percent);
        assertEquals("Add your gender and your birth date to complete your profile", two.missingLine(themed()));
    }

    // ---- donut ----

    @Test
    public void donut_colourFollowsThePercentage_theDescriptionSaysIt_andItSweepsOnFirstShow() {
        Context context = themed();
        assertEquals(context.getColor(R.color.velocity_green_primary), ProfileDonutView.colorFor(context, 100));
        assertEquals(context.getColor(R.color.velocity_orange_primary), ProfileDonutView.colorFor(context, 99));
        assertEquals(context.getColor(R.color.velocity_orange_primary), ProfileDonutView.colorFor(context, 50));
        assertEquals(context.getColor(R.color.velocity_red_primary), ProfileDonutView.colorFor(context, 49));

        ProfileDonutView donut = new ProfileDonutView(context);
        donut.setProgress(80, false);
        assertEquals(80, donut.getPercent());
        assertEquals("Profile 80% complete", donut.getContentDescription().toString());
        assertEquals("no animation requested: it is there at once", 80f, donut.getShownPercent(), 0.01f);

        ProfileDonutView sweeping = new ProfileDonutView(context);
        sweeping.setProgress(100, true);
        assertEquals("it starts from nothing and sweeps", 0f, sweeping.getShownPercent(), 0.01f);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(100f, sweeping.getShownPercent(), 0.01f);
    }

    // ---- balance colours and currency ----

    @Test
    public void balanceTone_greenWhenNothingIsOwed_amberWhenPartlyPaid_redOnlyWhenOwed() {
        assertEquals(PaymentBalanceTone.PAID, PaymentBalanceTone.of(2800, 2800));
        assertEquals(PaymentBalanceTone.PAID, PaymentBalanceTone.of(2800, 3000));
        assertEquals(PaymentBalanceTone.PARTIAL, PaymentBalanceTone.of(2800, 1000));
        assertEquals(PaymentBalanceTone.OWED, PaymentBalanceTone.of(2800, 0));
        assertEquals(R.color.status_paid_fg, PaymentBalanceTone.PAID.colorRes);
        assertEquals(R.color.status_pending_fg, PaymentBalanceTone.PARTIAL.colorRes);
        assertEquals(R.color.status_unpaid_fg, PaymentBalanceTone.OWED.colorRes);
    }

    @Test
    public void amounts_useThePesoSignThousandsSeparatorAndTwoDecimals() {
        assertEquals("₱2,800.00", MoneyFormat.format(2800));
        assertEquals("₱0.00", MoneyFormat.format(0));
        assertEquals("₱1,750.50", MoneyFormat.format(1750.5));
    }

    @Test
    public void historyCard_balanceIsColouredByWhatIsOwed() {
        Context context = themed();
        TransactionRow paid = TransactionRow.from(booking("1", "Confirmed", 2800, 2800));
        CharSequence paidLine = TransactionHistoryAdapter.paymentWithToneOnBalance(context, TransactionText.payment(context, paid.summary), paid.summary);
        assertEquals("₱2,800.00 paid • ₱0.00 balance", paidLine.toString());
        assertEquals("₱0.00 balance is green", context.getColor(R.color.status_paid_fg), colourAt(paidLine, paidLine.toString().indexOf("0.00 balance")));

        TransactionRow partial = TransactionRow.from(booking("2", "Confirmed", 2800, 1000));
        CharSequence partialLine = TransactionHistoryAdapter.paymentWithToneOnBalance(context, TransactionText.payment(context, partial.summary), partial.summary);
        assertEquals("partly paid is amber", context.getColor(R.color.status_pending_fg), colourAt(partialLine, partialLine.toString().indexOf("1,800.00 balance")));

        Booking unpaid = booking("3", "Pending", 2800, 0);
        unpaid.setHasBooking(false); // a reservation nothing has been paid on
        unpaid.setDirectBooking(false);
        unpaid.setAmountPaid(0);
        TransactionRow owed = TransactionRow.from(unpaid);
        CharSequence owedLine = TransactionHistoryAdapter.paymentWithToneOnBalance(context, TransactionText.payment(context, owed.summary), owed.summary);
        assertEquals("owing is red", context.getColor(R.color.status_unpaid_fg), colourAt(owedLine, owedLine.toString().indexOf("2,800.00 balance")));
    }

    private static int colourAt(CharSequence text, int position) {
        assertTrue("the balance is in the text: [" + text + "]", position >= 0);
        ForegroundColorSpan[] spans = ((Spanned) text).getSpans(position, position + 1, ForegroundColorSpan.class);
        assertEquals("the balance carries a colour", 1, spans.length);
        return spans[0].getForegroundColor();
    }

    private static Booking booking(String id, String status, double total, double paid) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", "Oct 20, 2026", "Oct 21, 2026", 2, total, status, "");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        if (paid > 0) {
            List<Booking.PaymentRecord> rows = new ArrayList<>();
            rows.add(new Booking.PaymentRecord(String.format(Locale.US, "%.2f", paid), "CASH", "REF" + id, "Sep 30, 2026 • 10:09 PM", "completed"));
            b.setPaymentHistory(rows);
        }
        return b;
    }

    // ---- the Filters sheet's room types ----

    private static Room room(String name) {
        return new Room("1", name, name, 2, 1000, "", 0, true, new ArrayList<>(), "", "", "");
    }

    @Test
    public void roomTypes_comeFromTheBrowseRoomsCatalog_notAHardcodedList() {
        List<TransactionRow> rows = TransactionRow.fromAll(Collections.singletonList(booking("1", "Confirmed", 2800, 0)));
        List<String> options = TransactionFilterOptions.roomTypes(Arrays.asList(room("Suite"), room("Deluxe"), room("Presidential"), room("deluxe")), rows);
        assertEquals("every catalog room type, sorted, no duplicates", Arrays.asList("Deluxe", "Presidential", "Suite"), options);
    }

    @Test
    public void roomTypes_fallBackToTheGuestsOwnTransactions_whenTheCatalogIsUnavailable() {
        List<TransactionRow> rows = TransactionRow.fromAll(Arrays.asList(booking("1", "Confirmed", 2800, 0), booking("2", "Confirmed", 1000, 0)));
        assertEquals(Collections.singletonList("Deluxe"), TransactionFilterOptions.roomTypes(Collections.emptyList(), rows));
        assertEquals(Collections.singletonList("Deluxe"), TransactionFilterOptions.roomTypes(null, rows));
        assertEquals(7, TransactionFilterOptions.bookingStatuses().size());
    }
}
