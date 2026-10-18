package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The shared stay card (a long ID is never cut off, the dates never split), the Dashboard's preview rule, and the
 * grouped Transaction Details (no placeholder values; derived values are right). All JVM-only; no device.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xxhdpi", application = TestApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StayCardAndDetailsTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);
    private static final String[] PLACEHOLDERS = {"N/A", "n/a", "null", "None", "-", "—", "–", "undefined"};

    private static Context themed() {
        Context app = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(app, R.style.Theme_VelocitySuites);
    }

    private static Booking stay(String id, LocalDate in, LocalDate out, String status, double total) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", in.format(FMT), out.format(FMT), 2, total, status, "Oct 01, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        return b;
    }

    // ---- 1. the stay card

    @Test
    public void aVeryLongId_isNeverEllipsized_andTheDatesStayOnOneLine_atAPhoneWidth() {
        Context context = themed();
        Booking b = stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), "Confirmed", 1800);
        b.setDisplayId("RES-2026-10-20-ABCDEFGHIJKLMNOP-000123456789");
        FrameLayout parent = new FrameLayout(context);
        View card = StayCardBinder.bind(context, parent, b, StayCardBinder.Tag.UPCOMING);
        int width = Math.round(360 * context.getResources().getDisplayMetrics().density);
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());

        TextView ref = card.findViewById(R.id.tvRecentStayRef);
        assertTrue(ref.getText().toString().contains("RES-2026-10-20-ABCDEFGHIJKLMNOP-000123456789"));
        assertNull("the ID is never ellipsized", ref.getEllipsize());
        assertEquals("the ID may wrap but never loses lines", Integer.MAX_VALUE, ref.getMaxLines());
        Layout layout = ref.getLayout();
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals("line " + line + " of the ID is complete", 0, layout.getEllipsisCount(line));
        }

        TextView dates = card.findViewById(R.id.tvRecentStayDates);
        assertEquals("Oct 20 → Oct 21, 2026", dates.getText().toString());
        assertEquals("dates are one line", 1, dates.getLineCount());
        assertEquals("1 night · 2 Guests", ((TextView) card.findViewById(R.id.tvRecentStayMeta)).getText().toString());
        assertEquals("the chips sit on their own row", View.VISIBLE, card.findViewById(R.id.tvRecentStayType).getVisibility());
        assertTrue("status chip shares the ID's row, to its right",
                card.findViewById(R.id.tvRecentStayStatus).getLeft() >= ref.getRight() - 1);
    }

    @Test
    public void compactDates_dropTheFirstYearOnlyWithinOneYear() {
        assertEquals("Oct 20 → Oct 21, 2026", StayCardText.compactDates(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)));
        assertEquals("Dec 30, 2026 → Jan 2, 2027", StayCardText.compactDates(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)));
    }

    // ---- 2. preview / collapse rules

    @Test
    public void preview_showsThreeWhenCollapsed_everythingWhenExpanded_andNoToggleForThreeOrFewer() {
        List<Integer> five = Arrays.asList(1, 2, 3, 4, 5);
        assertEquals(SectionPreview.PREVIEW_COUNT, SectionPreview.visible(five, false).size());
        assertEquals(5, SectionPreview.visible(five, true).size());
        assertTrue(SectionPreview.needsToggle(5));

        List<Integer> three = Arrays.asList(1, 2, 3);
        assertEquals(3, SectionPreview.visible(three, false).size());
        assertFalse("three or fewer: no Show all / Show less", SectionPreview.needsToggle(3));
        assertFalse(SectionPreview.needsToggle(0));
        assertTrue("the preview is between 3 and 5", SectionPreview.PREVIEW_COUNT >= 3 && SectionPreview.PREVIEW_COUNT <= 5);
    }

    @Test
    public void previewToggle_readsShowAllWithTheTotal_thenShowLess_andIsAbsentForShortLists() {
        Context context = themed();
        FrameLayout list = new FrameLayout(context);
        assertNull(SectionPreview.addToggle(context, list, 3, false, () -> { }));
        assertEquals(0, list.getChildCount());

        int[] taps = {0};
        com.google.android.material.button.MaterialButton collapsed = SectionPreview.addToggle(context, list, 7, false, () -> taps[0]++);
        assertEquals("Show all (7)", collapsed.getText().toString());
        collapsed.performClick();
        assertEquals(1, taps[0]);
        FrameLayout other = new FrameLayout(context);
        assertEquals("Show less", SectionPreview.addToggle(context, other, 7, true, () -> { }).getText().toString());
    }

    @Test
    public void aSectionKeepsItsExpandedStateForTheNextScreenOpening() {
        Context context = themed();
        android.widget.ImageButton chevron = new android.widget.ImageButton(context);
        TextView summary = new TextView(context);
        context.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit().clear().apply();

        CollapsibleSection first = new CollapsibleSection(context, "test_section", chevron, summary, new View(context));
        assertFalse("starts collapsed", first.isExpanded());
        first.setExpanded(true);

        CollapsibleSection reopened = new CollapsibleSection(context, "test_section", new android.widget.ImageButton(context), summary, new View(context));
        assertTrue("remembered", reopened.isExpanded());
    }

    @Test
    public void aListSection_reportsItsChange_soTheScreenCanRedrawThePreviewOrEverything() {
        Context context = themed();
        context.getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        int[] changes = {0};
        CollapsibleSection section = new CollapsibleSection(context, "test_list", new android.widget.ImageButton(context), null, new View(context))
                .previewList(() -> changes[0]++);
        section.toggle();
        section.toggle();
        assertEquals(2, changes[0]);
    }

    // ---- Your Next Stay: default day, day-role chip, context line

    @Test
    public void nextStay_defaultDay_isTheCheckInDay_notTheCheckOutDay_forAStayTwoDaysAway() {
        LocalDate today = LocalDate.of(2026, 10, 18); // Sunday
        Booking stay = stay("567", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), "Confirmed", 1750);
        assertEquals(LocalDate.of(2026, 10, 20), NextStay.defaultDay(Arrays.asList(stay), today));
    }

    @Test
    public void dayRole_isCheckInDay_Staying_thenCheckOutDay() {
        Context context = themed();
        LocalDate today = LocalDate.of(2026, 10, 18);
        Booking stay = stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 23), "Confirmed", 5400);
        NextStay entry = NextStay.forDay(Arrays.asList(stay), LocalDate.of(2026, 10, 20), today).get(0);
        assertEquals("Check-in day", entry.dayRole(context, LocalDate.of(2026, 10, 20)));
        assertEquals("Staying", entry.dayRole(context, LocalDate.of(2026, 10, 21)));
        assertEquals("Staying", entry.dayRole(context, LocalDate.of(2026, 10, 22)));
        assertEquals("Check-out day", entry.dayRole(context, LocalDate.of(2026, 10, 23)));
    }

    @Test
    public void contextLine_saysOnlyHowFarTheDayIs() {
        Context context = themed();
        LocalDate today = LocalDate.of(2026, 10, 18);
        // the nearest stay day
        assertEquals("Your next stay · in 2 days", NextStayDayText.line(context, LocalDate.of(2026, 10, 20), today, true, false));
        assertEquals("Your next stay · tomorrow", NextStayDayText.line(context, LocalDate.of(2026, 10, 19), today, true, false));
        assertEquals("Your next stay · today", NextStayDayText.line(context, today, today, true, false));
        // any other day
        assertEquals("In 5 days", NextStayDayText.line(context, LocalDate.of(2026, 10, 23), today, false, false));
        assertEquals("2 days ago", NextStayDayText.line(context, LocalDate.of(2026, 10, 16), today, false, false));
        assertEquals("Tomorrow", NextStayDayText.line(context, LocalDate.of(2026, 10, 19), today, false, false));
        assertEquals("Yesterday", NextStayDayText.line(context, LocalDate.of(2026, 10, 17), today, false, false));
        assertEquals("Today", NextStayDayText.line(context, today, today, false, false));
        // a past day is never "your next stay"
        assertEquals("Yesterday", NextStayDayText.line(context, LocalDate.of(2026, 10, 17), today, true, false));
    }

    @Test
    public void contextLine_forACheckedInGuest_saysYoureStayingWithUs_evenBeforeTheBookedCheckInDate() {
        Context context = themed();
        LocalDate today = LocalDate.of(2026, 10, 18);
        assertEquals("You're staying with us", NextStayDayText.line(context, LocalDate.of(2026, 10, 20), today, true, true));
        assertEquals("You're staying with us", NextStayDayText.line(context, today, today, false, true));
        assertEquals("other cases keep the wording", "Your next stay · in 2 days",
                NextStayDayText.line(context, LocalDate.of(2026, 10, 20), today, true, false));
    }

    @Test
    public void nearestStayDay_isTodayWhenItHasAStay_otherwiseTheNextDayThatDoes() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        Booking later = stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), "Confirmed", 1750);
        assertEquals(LocalDate.of(2026, 10, 20), NextStay.nearestStayDay(Arrays.asList(later), today));
        Booking now = stay("2", LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 19), "Checked-In", 1750);
        assertEquals(today, NextStay.nearestStayDay(Arrays.asList(later, now), today));
        assertNull(NextStay.nearestStayDay(new ArrayList<>(), today));
    }

    @Test
    public void contextRow_isOneLineAt360dp_evenForTheLongestText_andTheTodayButtonIsCentredWithIt() {
        Context context = themed();
        FrameLayout parent = new FrameLayout(context);
        View body = android.view.LayoutInflater.from(context).inflate(R.layout.view_next_stay_days, parent, false);
        TextView info = body.findViewById(R.id.nextStayDayInfo);
        View todayButton = body.findViewById(R.id.btnNextStayToday);
        todayButton.setVisibility(View.VISIBLE);
        info.setText(NextStayDayText.line(context, LocalDate.of(2027, 2, 14), LocalDate.of(2026, 10, 18), true, false)); // "Your next stay · in 119 days"

        int width = Math.round(360 * context.getResources().getDisplayMetrics().density);
        body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        body.layout(0, 0, body.getMeasuredWidth(), body.getMeasuredHeight());

        assertEquals("one line", 1, info.getLineCount());
        assertEquals(1, info.getMaxLines());
        View row = body.findViewById(R.id.nextStayDayInfoRow);
        int infoCentre = row.getTop() + info.getTop() + info.getHeight() / 2;
        int buttonCentre = row.getTop() + todayButton.getTop() + todayButton.getHeight() / 2;
        assertTrue("the Today button is centred with the text", Math.abs(infoCentre - buttonCentre) <= 2);
    }

    // ---- 4. transaction details

    private static void assertNoPlaceholders(List<TransactionDetailSections.Row> rows) {
        for (TransactionDetailSections.Row row : rows) {
            assertTrue("empty value for " + row.label, row.value != null && !row.value.trim().isEmpty());
            for (String bad : PLACEHOLDERS) {
                assertFalse(row.label + " shows \"" + row.value + "\"", row.value.trim().equalsIgnoreCase(bad));
            }
        }
    }

    @Test
    public void details_areGrouped_derivedValuesAreRight_andNothingIsAPlaceholder() {
        Context context = themed();
        Booking b = stay("338", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 23), "Confirmed", 5400);
        b.setRooms(new ArrayList<>(Arrays.asList(new BookingRoom("1", "Deluxe", 2, 900, 3, 5400, Arrays.asList("101", "102")))));
        TransactionDetailSections s = TransactionDetailSections.of(context, b, null);

        assertNoPlaceholders(s.all());
        assertEquals("Booking ID", s.stay.get(0).label);
        assertEquals("338", s.stay.get(0).value);
        assertEquals("3 nights", valueOf(s.stay, "Nights"));
        assertEquals("2 Guests", valueOf(s.stay, "Total Guests"));
        assertEquals("Deluxe", valueOf(s.room, "Room Type"));
        assertEquals("101, 102", valueOf(s.room, "Room Number"));
        assertEquals("2", valueOf(s.room, "Quantity"));
        assertEquals(MoneyFormat.format(900), valueOf(s.room, "Rate per Night"));
        assertEquals(MoneyFormat.format(5400), valueOf(s.payment, "Total Amount"));
        double paid = TransactionStatusHelper.summarize(b).verifiedPaid;
        assertEquals(MoneyFormat.format(paid), valueOf(s.payment, "Amount Paid"));
        assertEquals("Balance = Total - Paid", MoneyFormat.format(Math.max(0, 5400 - paid)), valueOf(s.payment, "Balance"));

        // a reservation nothing has been paid on yet owes the whole total
        Booking unpaid = stay("9", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), "Pending", 1800);
        unpaid.setHasBooking(false);
        unpaid.setDirectBooking(false);
        TransactionDetailSections u = TransactionDetailSections.of(context, unpaid, null);
        assertEquals(MoneyFormat.format(0), valueOf(u.payment, "Amount Paid"));
        assertEquals(MoneyFormat.format(1800), valueOf(u.payment, "Balance"));
        assertEquals("Reservation ID", u.stay.get(0).label);
    }

    @Test
    public void details_leaveOutWhatTheAppDoesNotHave_insteadOfShowingAPlaceholder() {
        Context context = themed();
        Booking sparse = new Booking("7", null, null, null, null, null, 0, 0, "Pending", null);
        sparse.setHasBooking(false);
        TransactionDetailSections s = TransactionDetailSections.of(context, sparse, null);

        assertNoPlaceholders(s.all());
        assertNull("no dates: no Nights row", find(s.stay, "Nights"));
        assertNull("no guests: no Guests row", find(s.stay, "Total Guests"));
        assertTrue("no room data: no Room Details at all", s.room.isEmpty());
        assertNull("no total: no Total Amount / Balance", find(s.payment, "Total Amount"));
        assertNull(find(s.payment, "Balance"));
        assertNull("no payment made: no method row", find(s.payment, "Payment Method"));
    }

    @Test
    public void isShowable_rejectsEveryPlaceholder() {
        for (String bad : PLACEHOLDERS) assertFalse("\"" + bad + "\"", TransactionDetailSections.isShowable(bad));
        assertFalse(TransactionDetailSections.isShowable(null));
        assertFalse(TransactionDetailSections.isShowable("   "));
        assertTrue(TransactionDetailSections.isShowable("Deluxe"));
    }

    @Test
    public void theTransactionDetailsScreen_neverRendersAPlaceholder_forASparseTransaction() {
        Context app = ApplicationProvider.getApplicationContext();
        Booking sparse = new Booking("7", null, null, null, "Oct 20, 2026", null, 0, 0, "Pending", null);
        sparse.setHasBooking(false);
        Intent intent = TransactionDetailsActivity.newIntent(app, new PaymentTransaction(sparse, null));
        TransactionDetailsActivity activity = Robolectric.buildActivity(TransactionDetailsActivity.class, intent).setup().get();

        List<String> texts = new ArrayList<>();
        collect(activity.findViewById(android.R.id.content), texts);
        for (String text : texts) {
            for (String bad : PLACEHOLDERS) assertFalse("a screen text reads \"" + text + "\"", text.trim().equalsIgnoreCase(bad));
        }
        assertFalse("something rendered", texts.isEmpty());
    }

    private static String valueOf(List<TransactionDetailSections.Row> rows, String label) {
        TransactionDetailSections.Row r = find(rows, label);
        assertNotNull("missing row " + label, r);
        return r.value;
    }

    private static TransactionDetailSections.Row find(List<TransactionDetailSections.Row> rows, String label) {
        for (TransactionDetailSections.Row r : rows) if (r.label.equals(label)) return r;
        return null;
    }

    private static void collect(View v, List<String> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
    }
}
