package com.example.velocitysuites;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Drives the Stay Calendar card (view_stay_calendar.xml), shared by the Dashboard and Upcoming Hotel Transactions: a
 * month grid with a dot on every day that has a check-in or check-out, a segmented Daily / Weekly / Monthly filter, and
 * the list of transactions that overlap the chosen window (each once, nearest check-in first) as cards with a count.
 * Tapping one opens it in Upcoming Hotel Transactions, filtered to its type and status. A host screen can follow the guest's choices through
 * {@link Listener}, move the calendar with {@link #setWindow}, and hide the list with {@link #setSummaryVisible} when
 * it shows the transactions itself. What is listed and how the window is cut lives in {@link StayCalendarModel};
 * this class only draws it.
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
    private final MaterialButtonToggleGroup modeGroup;
    private final TextView countLabel;
    private final android.widget.CompoundButton showCancelled;
    private final CollapsibleSection section;

    /** Told when the guest picks a different day or window (not when code sets it through {@link #setWindow}). */
    interface Listener {
        void onWindowChanged(@NonNull StayCalendarModel.Mode mode, @NonNull LocalDate selected);
    }

    private StayCalendarModel model = new StayCalendarModel(Collections.emptyList());
    private StayCalendarModel.Mode mode = StayCalendarModel.Mode.DAILY;
    private LocalDate selected;
    private YearMonth shown;
    @Nullable private Listener listener;
    @Nullable private List<Booking> bookings;
    @Nullable private java.util.function.Predicate<Booking> shade;
    @Nullable private java.util.function.Predicate<Booking> mutedShade;
    /** True while {@link #setWindow} drives the mode buttons, so that is not reported back as a guest choice. */
    private boolean settingWindow;

    DashboardStayCalendar(@NonNull View root, @NonNull LocalDate today) {
        this.context = root.getContext();
        this.root = root;
        this.grid = root.findViewById(R.id.stayCalGrid);
        this.monthLabel = root.findViewById(R.id.stayCalMonth);
        this.rangeLabel = root.findViewById(R.id.stayCalRange);
        this.summary = root.findViewById(R.id.stayCalSummary);
        this.emptyText = root.findViewById(R.id.stayCalEmpty);
        this.modeGroup = root.findViewById(R.id.stayCalMode);
        this.countLabel = root.findViewById(R.id.stayCalCount);
        this.showCancelled = root.findViewById(R.id.stayCalShowCancelled);
        // Collapsed: the filter, the count and the first few transactions; the month grid opens with the section.
        this.section = new CollapsibleSection(context, "stay_calendar", root.findViewById(R.id.stayCalToggle),
                root.findViewById(R.id.stayCalCollapsedSummary), root.findViewById(R.id.stayCalBody))
                .previewList(this::renderSummary)
                .collapsedHides(root.findViewById(R.id.stayCalNavRow), grid);
        this.selected = today;
        this.shown = YearMonth.from(today);

        root.findViewById(R.id.stayCalPrev).setOnClickListener(v -> shiftMonth(-1));
        root.findViewById(R.id.stayCalNext).setOnClickListener(v -> shiftMonth(1));
        showCancelled.setOnCheckedChangeListener((b, checked) -> render());
        modeGroup.check(R.id.stayCalModeDaily);
        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.stayCalModeWeekly) mode = StayCalendarModel.Mode.WEEKLY;
            else if (checkedId == R.id.stayCalModeMonthly) mode = StayCalendarModel.Mode.MONTHLY;
            else mode = StayCalendarModel.Mode.DAILY;
            render();
            notifyListener();
        });
        render();
    }

    /** Replaces the data (called whenever the Dashboard's bookings change) and redraws, keeping the chosen day. */
    void setBookings(@Nullable List<Booking> bookings) {
        this.bookings = bookings;
        rebuildModel();
    }

    /**
     * Which stays have their days shaded: {@code shade} in red, {@code muted} dashed and grey (for stays that are not
     * active bookings - cancelled, rejected); where both cover a day, red wins. Default: every active stay, in red.
     */
    void setShading(@Nullable java.util.function.Predicate<Booking> shade, @Nullable java.util.function.Predicate<Booking> muted) {
        this.shade = shade;
        this.mutedShade = muted;
        rebuildModel();
    }

    private void rebuildModel() {
        model = new StayCalendarModel(bookings, shade, mutedShade);
        render();
    }

    private void shiftMonth(int delta) {
        shown = shown.plusMonths(delta); // only the month on show changes - the selected day and any filter stay as they are
        render();
    }

    private void render() {
        monthLabel.setText(context.getString(R.string.month_year_format, shown.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), shown.getYear()));
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
        if (model.isStayDay(date)) cell.setBackgroundResource(R.drawable.bg_calendar_stay_day);
        else if (model.isMutedStayDay(date)) cell.setBackgroundResource(R.drawable.bg_calendar_stay_day_muted);
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
        cell.setContentDescription(context.getResources().getQuantityString(R.plurals.stay_calendar_day_cd_format, count, DAY.format(date), count));
        cell.setOnClickListener(v -> {
            selected = date;
            render();
            notifyListener();
        });
        return cell;
    }

    private void renderSummary() {
        LocalDate start = StayCalendarModel.windowStart(mode, selected);
        LocalDate end = StayCalendarModel.windowEnd(mode, selected);
        switch (mode) {
            case WEEKLY:
                rangeLabel.setText(context.getString(R.string.date_range_format, SHORT_DAY.format(start), DAY.format(end)));
                break;
            case MONTHLY:
                rangeLabel.setText(context.getString(R.string.month_year_format, selected.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), selected.getYear()));
                break;
            default:
                rangeLabel.setText(DAY.format(selected));
        }

        summary.removeAllViews();
        List<Booking> entries = model.transactionsFor(mode, selected, showCancelled.isChecked());
        emptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        countLabel.setText(context.getResources().getQuantityString(R.plurals.summary_transactions_count, entries.size(), entries.size()));
        for (Booking b : SectionPreview.visible(entries, section.isExpanded())) StayCardBinder.add(context, summary, b, StayCardBinder.Tag.NONE);
        SectionPreview.addToggle(context, summary, entries.size(), section.isExpanded(), section::toggle);

        // The one-line summary shown while the section is collapsed: how many, and what comes first.
        if (entries.isEmpty()) {
            section.setSummary(context.getString(R.string.summary_calendar_format,
                    rangeLabel.getText(), context.getString(R.string.summary_nothing_scheduled)));
        } else {
            Booking first = entries.get(0);
            String firstText = context.getString(R.string.summary_ref_status,
                    TransactionText.reference(context, first.isHasBooking(), first.getDisplayId()),
                    TransactionStatusChip.label(context, first));
            section.setSummary(context.getString(R.string.summary_calendar_format,
                    rangeLabel.getText(),
                    context.getString(R.string.summary_with_first,
                            context.getResources().getQuantityString(R.plurals.summary_transactions_count, entries.size(), entries.size()),
                            firstText)));
        }
    }

    void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * Shows or hides the card's own list of the chosen window's transactions. A screen that already lists them below the
     * calendar hides it so nothing is shown twice.
     */
    void setSummaryVisible(boolean visible) {
        root.findViewById(R.id.stayCalSummaryGroup).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Moves the calendar to this mode and day (a screen's own filter changed). Does not call the listener. */
    void setWindow(@NonNull StayCalendarModel.Mode newMode, @NonNull LocalDate newSelected) {
        settingWindow = true;
        mode = newMode;
        selected = newSelected;
        shown = YearMonth.from(newSelected);
        modeGroup.check(newMode == StayCalendarModel.Mode.WEEKLY ? R.id.stayCalModeWeekly
                : newMode == StayCalendarModel.Mode.MONTHLY ? R.id.stayCalModeMonthly : R.id.stayCalModeDaily);
        settingWindow = false;
        render();
    }

    private void notifyListener() {
        if (listener != null && !settingWindow) listener.onWindowChanged(mode, selected);
    }

    View root() {
        return root;
    }
}
