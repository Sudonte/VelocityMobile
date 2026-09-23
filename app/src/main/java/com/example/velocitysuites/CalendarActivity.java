package com.example.velocitysuites;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Opened by tapping the dashboard's date/time widget. Shows a real calendar
 * (defaulting to today) plus an agenda of every upcoming check-in/check-out
 * across Bookings and Reservations - completed (Checked-Out) and cancelled/
 * rejected records are never shown. A top filter narrows the agenda to
 * All / Upcoming Booking / Upcoming Reservation, and tapping a date on the
 * calendar narrows it further to just that day.
 */
public class CalendarActivity extends BaseNavigationActivity {

    private static class UpcomingEvent {
        final Booking booking;
        final String title;
        final long timeMillis;
        final String dateLabel;
        final boolean isCheckIn;

        UpcomingEvent(Booking booking, String title, long timeMillis, String dateLabel, boolean isCheckIn) {
            this.booking = booking;
            this.title = title;
            this.timeMillis = timeMillis;
            this.dateLabel = dateLabel;
            this.isCheckIn = isCheckIn;
        }
    }

    private LinearLayout listContainer;
    private View emptyState, loadingOverlay;
    private ChipGroup chipGroupFilters;
    private CalendarView calendarView;
    private View layoutSelectedDateFilter;
    private TextView tvSelectedDateLabel, calendarDateText, calendarTimeText;
    private RoomRepository repository;
    private final List<UpcomingEvent> allEvents = new ArrayList<>();
    private String currentFilter = "All";
    private Calendar selectedDate = null;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        setupGuestNavigation(View.NO_ID);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);

        listContainer = findViewById(R.id.calendarEventsListContainer);
        emptyState = findViewById(R.id.layoutCalendarEmptyState);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        chipGroupFilters = findViewById(R.id.chipGroupCalendarFilters);
        calendarView = findViewById(R.id.calendarView);
        layoutSelectedDateFilter = findViewById(R.id.layoutSelectedDateFilter);
        tvSelectedDateLabel = findViewById(R.id.tvSelectedDateLabel);
        calendarDateText = findViewById(R.id.calendarDateText);
        calendarTimeText = findViewById(R.id.calendarTimeText);

        setupFilters();
        setupCalendarSelection();
        loadEvents();
        initClock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clockRunnable != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }
    }

    private void setupFilters() {
        if (chipGroupFilters == null) return;
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipFilterUpcomingBooking)) currentFilter = "Bookings";
            else if (checkedIds.contains(R.id.chipFilterUpcomingReservation)) currentFilter = "Reservations";
            else currentFilter = "All";
            renderEvents();
        });
    }

    private void setupCalendarSelection() {
        if (calendarView == null) return;
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = Calendar.getInstance();
            selectedDate.set(year, month, dayOfMonth, 0, 0, 0);
            selectedDate.set(Calendar.MILLISECOND, 0);

            SimpleDateFormat labelFormat = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
            tvSelectedDateLabel.setText(getString(R.string.calendar_selected_date_format, labelFormat.format(selectedDate.getTime())));
            layoutSelectedDateFilter.setVisibility(View.VISIBLE);
            renderEvents();
        });

        View btnClear = findViewById(R.id.btnClearSelectedDate);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                selectedDate = null;
                layoutSelectedDateFilter.setVisibility(View.GONE);
                renderEvents();
            });
        }
    }

    private void loadEvents() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                buildEvents(result != null ? result : repository.getBookings());
            }

            @Override
            public void onError(String message) {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                buildEvents(repository.getBookings());
                Toast.makeText(CalendarActivity.this,
                        "Couldn't refresh calendar: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Every future check-in (not yet checked in) or future check-out (already
     * checked in) across non-cancelled, non-completed bookings/reservations -
     * completed (Checked-Out) and cancelled/rejected stays never appear here.
     */
    private void buildEvents(List<Booking> bookings) {
        allEvents.clear();
        SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
        long now = System.currentTimeMillis();

        for (Booking b : bookings) {
            String status = b.getStatus();
            if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)
                    || "Checked-Out".equalsIgnoreCase(status)) continue;

            try {
                long checkInTime = sdf.parse(b.getCheckInDate()).getTime();
                long checkOutTime = sdf.parse(b.getCheckOutDate()).getTime();

                if (checkInTime >= now - 86400000 && !"Checked-In".equalsIgnoreCase(status)) {
                    String kind = getString(b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
                    allEvents.add(new UpcomingEvent(b,
                            getString(R.string.upcoming_event_title_format, kind, b.getRoomName()),
                            checkInTime, b.getCheckInDate(), true));
                }
                if (checkOutTime >= now - 86400000 && "Checked-In".equalsIgnoreCase(status)) {
                    String kind = getString(b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
                    allEvents.add(new UpcomingEvent(b,
                            getString(R.string.upcoming_event_title_format, kind, b.getRoomName()),
                            checkOutTime, b.getCheckOutDate(), false));
                }
            } catch (Exception ignored) {
                // Unparseable dates on a record can't be placed on the timeline - skip it.
            }
        }

        Collections.sort(allEvents, (a, c) -> Long.compare(a.timeMillis, c.timeMillis));
        renderEvents();
    }

    private void renderEvents() {
        listContainer.removeAllViews();
        SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);

        List<UpcomingEvent> filtered = new ArrayList<>();
        for (UpcomingEvent e : allEvents) {
            boolean typeMatches;
            switch (currentFilter) {
                case "Bookings": typeMatches = e.booking.isHasBooking(); break;
                case "Reservations": typeMatches = !e.booking.isHasBooking(); break;
                default: typeMatches = true;
            }
            if (!typeMatches) continue;

            if (selectedDate != null && !sdf.format(selectedDate.getTime()).equals(e.dateLabel)) {
                continue;
            }
            filtered.add(e);
        }

        if (filtered.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
            for (UpcomingEvent e : filtered) {
                addEventCard(e);
            }
        }
    }

    private void addEventCard(UpcomingEvent e) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_upcoming_transaction, listContainer, false);

        TextView tvTitle = card.findViewById(R.id.tvTransactionTitle);
        TextView tvDate = card.findViewById(R.id.tvTransactionDate);
        TextView tvTime = card.findViewById(R.id.tvTimeLabel);
        ImageView ivIcon = card.findViewById(R.id.ivTransactionIcon);
        View iconContainer = card.findViewById(R.id.transactionIconContainer);

        tvTitle.setText(e.title);
        tvDate.setText(e.dateLabel);

        if (e.isCheckIn) {
            tvTime.setText(R.string.check_in_time);
            ivIcon.setImageResource(R.drawable.ic_calendar);
            if (iconContainer instanceof com.google.android.material.card.MaterialCardView) {
                ((com.google.android.material.card.MaterialCardView) iconContainer)
                        .setCardBackgroundColor(getResources().getColorStateList(R.color.velocity_red_soft, getTheme()));
            }
        } else {
            tvTime.setText(R.string.check_out_time);
            ivIcon.setImageResource(R.drawable.ic_clock);
            ivIcon.setImageTintList(getResources().getColorStateList(R.color.velocity_green_primary, getTheme()));
            if (iconContainer instanceof com.google.android.material.card.MaterialCardView) {
                ((com.google.android.material.card.MaterialCardView) iconContainer)
                        .setCardBackgroundColor(getResources().getColorStateList(R.color.velocity_green_soft, getTheme()));
            }
            tvTime.setBackgroundTintList(getResources().getColorStateList(R.color.velocity_green_soft, getTheme()));
            tvTime.setTextColor(getResources().getColor(R.color.velocity_green_dark, getTheme()));
        }

        card.setOnClickListener(v -> {
            String section = e.booking.isHasBooking()
                    ? BookingAndReservationActivity.SECTION_BOOKING
                    : BookingAndReservationActivity.SECTION_RESERVATION;
            android.content.Intent intent = new android.content.Intent(this, BookingAndReservationActivity.class);
            intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, section);
            intent.putExtra(BookingAndReservationActivity.EXTRA_HIGHLIGHT_ID, e.booking.getId());
            startActivity(intent);
        });

        listContainer.addView(card);
    }

    private void initClock() {
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                updateDateTime();
                clockHandler.postDelayed(this, 1000);
            }
        };
        updateDateTime();
        clockHandler.postDelayed(clockRunnable, 1000);
    }

    private void updateDateTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.date_format_full), Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat(getString(R.string.time_format_seconds), Locale.getDefault());

        if (calendarDateText != null) {
            calendarDateText.setText(dateFormat.format(calendar.getTime()));
        }
        if (calendarTimeText != null) {
            calendarTimeText.setText(timeFormat.format(calendar.getTime()));
        }
    }
}
