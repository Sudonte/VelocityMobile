package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.ChipGroup;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Drives the Dashboard's Stay Calendar card (view_stay_calendar.xml): a month grid with a dot on every day that
 * has a check-in or check-out, a Daily / Weekly / Monthly filter, and a short summary of the transactions in the
 * chosen window. "View Full Details" opens that transaction inside Upcoming Transactions. What is listed and how
 * the window is cut lives in {@link StayCalendarModel}; this class only draws it.
 */
final class DashboardStayCalendar {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final Context context;
    private final View root;
    private final GridLayout grid;
    private final TextView monthLabel;
    private final TextView rangeLabel;
    private final LinearLayout summary;
    private final TextView emptyText;
    private final ChipGroup modeGroup;

    private StayCalendarModel model = new StayCalendarModel(Collections.emptyList());
    private StayCalendarModel.Mode mode = StayCalendarModel.Mode.DAILY;
    private LocalDate selected;
    private YearMonth shown;

    DashboardStayCalendar(@NonNull View root, @NonNull LocalDate today) {
        this.context = root.getContext();
        this.root = root;
        this.grid = root.findViewById(R.id.stayCalGrid);
        this.monthLabel = root.findViewById(R.id.stayCalMonth);
        this.rangeLabel = root.findViewById(R.id.stayCalRange);
        this.summary = root.findViewById(R.id.stayCalSummary);
        this.emptyText = root.findViewById(R.id.stayCalEmpty);
        this.modeGroup = root.findViewById(R.id.stayCalMode);
        this.selected = today;
        this.shown = YearMonth.from(today);

        root.findViewById(R.id.stayCalPrev).setOnClickListener(v -> shiftMonth(-1));
        root.findViewById(R.id.stayCalNext).setOnClickListener(v -> shiftMonth(1));
        root.findViewById(R.id.stayCalOpenAll).setOnClickListener(v -> openUpcoming(null));
        modeGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.contains(R.id.stayCalModeWeekly)) mode = StayCalendarModel.Mode.WEEKLY;
            else if (ids.contains(R.id.stayCalModeMonthly)) mode = StayCalendarModel.Mode.MONTHLY;
            else mode = StayCalendarModel.Mode.DAILY;
            render();
        });
        render();
    }

    /** Replaces the data (called whenever the Dashboard's bookings change) and redraws, keeping the chosen day. */
    void setBookings(@Nullable List<Booking> bookings) {
        model = new StayCalendarModel(bookings);
        render();
    }

    private void shiftMonth(int delta) {
        shown = shown.plusMonths(delta);
        // Keep the selection inside the month being looked at, so the summary never describes an invisible day.
        selected = shown.atDay(Math.min(selected.getDayOfMonth(), shown.lengthOfMonth()));
        render();
    }

    private void render() {
        monthLabel.setText(shown.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + shown.getYear());
        drawGrid();
        renderSummary();
    }

    private void drawGrid() {
        grid.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;
        int cellHeight = (int) (48 * density);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Manila"));

        for (java.time.DayOfWeek dow : new java.time.DayOfWeek[]{
                java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
                java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY,
                java.time.DayOfWeek.SATURDAY}) {
            TextView head = new TextView(context);
            head.setText(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 1));
            head.setGravity(Gravity.CENTER);
            head.setTextSize(11);
            head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            head.setTextColor(ContextCompat.getColor(context, R.color.velocity_text_secondary));
            head.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            grid.addView(head, cellParams((int) (28 * density)));
        }

        int leading = shown.atDay(1).getDayOfWeek().getValue() % 7; // Sunday = 0
        for (int i = 0; i < leading; i++) grid.addView(new View(context), cellParams(cellHeight));

        LocalDate windowStart = StayCalendarModel.windowStart(mode, selected);
        LocalDate windowEnd = StayCalendarModel.windowEnd(mode, selected);
        for (int d = 1; d <= shown.lengthOfMonth(); d++) {
            LocalDate date = shown.atDay(d);
            grid.addView(dayCell(date, date.equals(today), date.equals(selected),
                    !date.isBefore(windowStart) && !date.isAfter(windowEnd), cellHeight, density), cellParams(cellHeight));
        }
    }

    private GridLayout.LayoutParams cellParams(int height) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        p.width = 0;
        p.height = height;
        return p;
    }

    private View dayCell(LocalDate date, boolean isToday, boolean isSelected, boolean inWindow, int cellHeight, float density) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClickable(true);
        cell.setFocusable(true);
        android.util.TypedValue ripple = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            cell.setForeground(ContextCompat.getDrawable(context, ripple.resourceId));
        }

        TextView number = new TextView(context);
        number.setText(String.valueOf(date.getDayOfMonth()));
        number.setGravity(Gravity.CENTER);
        number.setTextSize(13);
        number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = (int) (30 * density);
        number.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        if (isSelected) {
            number.setBackgroundResource(R.drawable.bg_calendar_day_selected);
            number.setTextColor(ContextCompat.getColor(context, R.color.white));
            number.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        } else {
            if (isToday) number.setBackgroundResource(R.drawable.bg_calendar_day_today);
            number.setTextColor(ContextCompat.getColor(context, R.color.velocity_text_primary));
            number.setTypeface(inWindow ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        }
        cell.addView(number);

        View dot = new View(context);
        int dotSize = (int) (6 * density);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotParams.topMargin = (int) (2 * density);
        dot.setLayoutParams(dotParams);
        boolean marked = model.hasEntries(date);
        if (marked) dot.setBackgroundResource(R.drawable.bg_calendar_dot);
        else dot.setVisibility(View.INVISIBLE);
        cell.addView(dot);

        int count = marked ? model.entriesBetween(date, date).size() : 0;
        cell.setContentDescription(context.getString(R.string.stay_calendar_day_cd_format, DAY.format(date), count));
        cell.setOnClickListener(v -> {
            selected = date;
            render();
        });
        return cell;
    }

    private void renderSummary() {
        LocalDate start = StayCalendarModel.windowStart(mode, selected);
        LocalDate end = StayCalendarModel.windowEnd(mode, selected);
        switch (mode) {
            case WEEKLY:
                rangeLabel.setText(SHORT_DAY.format(start) + " - " + DAY.format(end));
                break;
            case MONTHLY:
                rangeLabel.setText(selected.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + selected.getYear());
                break;
            default:
                rangeLabel.setText(DAY.format(selected));
        }

        summary.removeAllViews();
        List<StayCalendarModel.Entry> entries = model.entriesFor(mode, selected);
        emptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (StayCalendarModel.Entry e : entries) {
            View row = inflater.inflate(R.layout.item_stay_calendar_entry, summary, false);
            Booking b = e.booking;
            ((TextView) row.findViewById(R.id.tvEntryRef)).setText(
                    TransactionText.reference(context, b.isHasBooking(), b.getDisplayId()));
            TextView kind = row.findViewById(R.id.tvEntryKind);
            kind.setText(e.isActual()
                    ? (e.checkIn ? R.string.stay_calendar_kind_checked_in : R.string.stay_calendar_kind_checked_out)
                    : (e.checkIn ? R.string.stay_calendar_kind_checkin : R.string.stay_calendar_kind_checkout));
            ((TextView) row.findViewById(R.id.tvEntryRooms)).setText(TransactionRow.roomTextOf(b));
            ((TextView) row.findViewById(R.id.tvEntryWhen)).setText(context.getString(
                    R.string.stay_calendar_when_format, DAY.format(e.date), e.timeText()));
            ((TextView) row.findViewById(R.id.tvEntryStay)).setText(context.getString(
                    R.string.stay_calendar_stay_format, b.getCheckInDate(), b.getCheckOutDate()));
            ((TextView) row.findViewById(R.id.tvEntryBalance)).setText(context.getString(
                    R.string.stay_calendar_balance_format, MoneyFormat.format(TransactionStatusHelper.summarize(b).balance)));
            row.findViewById(R.id.btnEntryDetails).setOnClickListener(v -> openUpcoming(b));
            summary.addView(row);
        }
    }

    private void openUpcoming(@Nullable Booking booking) {
        Intent intent = new Intent(context, UpcomingTransactionsActivity.class);
        if (booking != null) intent.putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID, booking.getId());
        context.startActivity(intent);
    }

    View root() {
        return root;
    }
}
