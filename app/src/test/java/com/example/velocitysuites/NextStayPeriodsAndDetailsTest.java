package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.velocitysuites.ui.TestApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Your Next Stay: week / month chips and windows (agreeing with the Stay Calendar), and the full-details rows. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 35, application = TestApplication.class)
public class NextStayPeriodsAndDetailsTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19); // a Monday

    private static Context ctx() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VelocitySuites);
    }

    private static Booking stay(String id, LocalDate in, LocalDate out, String status) {
        Booking b = new Booking(id, "1", "Deluxe", "Deluxe", in.format(FMT), out.format(FMT), 2, 1000, status, "Oct 01, 2026");
        b.setHasBooking(true);
        b.setDirectBooking(true);
        return b;
    }

    @Test
    public void weekChips_areNamedThisWeekNextWeekThenRanges() {
        Context c = ctx();
        StayCalendarModel.Mode w = StayCalendarModel.Mode.WEEKLY;
        LocalDate thisWeek = StayCalendarModel.windowStart(w, TODAY);
        assertEquals("This week", NextStayPeriods.title(c, w, thisWeek, TODAY));
        assertEquals("Next week", NextStayPeriods.title(c, w, thisWeek.plusWeeks(1), TODAY));
        assertEquals("Last week", NextStayPeriods.title(c, w, thisWeek.minusWeeks(1), TODAY));
        assertEquals("Nov 8–14", NextStayPeriods.title(c, w, LocalDate.of(2026, 11, 8), TODAY));
        assertEquals("Oct 25–31", NextStayPeriods.weekRange(LocalDate.of(2026, 10, 28)));
        assertEquals("across two months", "Nov 29–Dec 5", NextStayPeriods.weekRange(LocalDate.of(2026, 12, 2)));
    }

    @Test
    public void monthChips_andTheContextLine() {
        Context c = ctx();
        StayCalendarModel.Mode m = StayCalendarModel.Mode.MONTHLY;
        assertEquals("Oct 2026", NextStayPeriods.title(c, m, LocalDate.of(2026, 10, 1), TODAY));
        assertEquals("Nov 2026", NextStayPeriods.title(c, m, LocalDate.of(2026, 11, 1), TODAY));
        assertEquals("October 2026 · 5 stays", NextStayPeriods.contextLine(c, m, TODAY, TODAY, 5));
        assertEquals("October 2026 · 1 stay", NextStayPeriods.contextLine(c, m, TODAY, TODAY, 1));
        assertEquals("This week · 3 stays", NextStayPeriods.contextLine(c, StayCalendarModel.Mode.WEEKLY, TODAY, TODAY, 3));
        assertEquals("Today", NextStayPeriods.jumpLabel(c, StayCalendarModel.Mode.DAILY));
        assertEquals("This week", NextStayPeriods.jumpLabel(c, StayCalendarModel.Mode.WEEKLY));
        assertEquals("This month", NextStayPeriods.jumpLabel(c, m));
    }

    @Test
    public void stripsCoverPastAndFutureAndEveryStay() {
        List<Booking> all = Arrays.asList(stay("1", TODAY.plusDays(200), TODAY.plusDays(202), "Confirmed"));
        List<LocalDate> weeks = NextStayPeriods.starts(StayCalendarModel.Mode.WEEKLY, all, TODAY);
        assertTrue(weeks.get(0).isBefore(TODAY));
        assertFalse("widened to the far stay", weeks.get(weeks.size() - 1).isBefore(StayCalendarModel.windowStart(StayCalendarModel.Mode.WEEKLY, TODAY.plusDays(200))));
        assertEquals("every start is a Sunday", java.time.DayOfWeek.SUNDAY, weeks.get(3).getDayOfWeek());
    }

    @Test
    public void aWindowListsEveryOverlappingTransactionOnce_agreeingWithTheStayCalendar() {
        Booking spans = stay("1", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 27), "Confirmed");
        Booking inside = stay("2", LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22), "Confirmed");
        Booking cancelled = stay("3", LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22), "Cancelled");
        StayCalendarModel model = new StayCalendarModel(Arrays.asList(spans, inside, cancelled, spans));
        List<Booking> week = model.transactionsFor(StayCalendarModel.Mode.WEEKLY, TODAY);
        assertEquals(2, week.size());
        assertEquals("sorted by check-in", "1", week.get(0).getId());
        assertEquals(1, model.transactionsFor(StayCalendarModel.Mode.WEEKLY, LocalDate.of(2026, 10, 27)).size());
        assertEquals(2, model.transactionsFor(StayCalendarModel.Mode.MONTHLY, TODAY).size());
    }

    @Test
    public void rows_withoutAStayBill_areTotalPaidAndOneBalanceLine() {
        Context c = ctx();
        Booking b = stay("9", TODAY.plusDays(2), TODAY.plusDays(3), "Confirmed");
        List<NextStayDetails.Row> rows = NextStayDetails.rows(c, b);
        int balance = 0;
        for (NextStayDetails.Row r : rows) if ("Balance due".equals(r.label)) balance++;
        assertEquals(1, balance);
        assertEquals("Total", rows.get(0).label);
        assertEquals("Paid", rows.get(1).label);
    }

    @Test
    public void amenitiesAreGroupedByItem_withSummedQuantity() {
        List<BalanceBreakdown.Item> in = new ArrayList<>();
        in.add(BalanceBreakdown.itemOf("Additional Blanket", 1, 100, 100));
        in.add(BalanceBreakdown.itemOf("Additional Towel", 1, 50, 50));
        in.add(BalanceBreakdown.itemOf("additional blanket", 1, 100, 100));
        List<BalanceBreakdown.Item> out = NextStayDetails.grouped(in);
        assertEquals(2, out.size());
        assertEquals("Additional Blanket", out.get(0).name);
        assertEquals(2, out.get(0).quantity);
        assertEquals(200.0, out.get(0).subtotal, 0.001);
        assertEquals(1, out.get(1).quantity);
    }

    @Test
    public void openState_isKeptPerTransaction() {
        Booking b = stay("4242", TODAY, TODAY.plusDays(1), "Confirmed");
        assertFalse(NextStayDetails.isOpen(b));
        NextStayDetails.setOpen(b, true);
        assertTrue(NextStayDetails.isOpen(stay("4242", TODAY, TODAY.plusDays(1), "Confirmed")));
        NextStayDetails.setOpen(b, false);
        assertFalse(NextStayDetails.isOpen(b));
    }
}
