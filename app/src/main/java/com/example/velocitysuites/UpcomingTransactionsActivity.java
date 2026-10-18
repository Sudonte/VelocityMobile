package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * "Upcoming Hotel Transactions": EVERY booking and reservation the guest has, whatever its status (pending, confirmed,
 * checked in, checked out, cancelled, rejected), one card per transaction. Nothing is dropped by the client - the only
 * things that narrow the list are the filters the guest picks (type, status, date - three combo boxes, or a day on the
 * Stay Calendar), and a transaction that was checked
 * out or cancelled stays here for tracking.
 * <p>
 * Deep link: {@link #EXTRA_SELECTED_BOOKING_ID} (+ {@link #EXTRA_SELECTED_DIRECT} to tell a direct booking from a
 * reservation, whose ids are separate sequences) scrolls to and highlights that transaction. If the active filters would
 * hide it they are reset first. Each card links to the transaction's record in Transaction History, where the full
 * details live.
 */
public class UpcomingTransactionsActivity extends BaseNavigationActivity {

    /** Optional: scrolls to and highlights this exact Booking ID, from a specific dashboard item tap. */
    public static final String EXTRA_SELECTED_BOOKING_ID = "EXTRA_SELECTED_BOOKING_ID";
    /** Optional, with the id: true for a direct booking, false for a reservation-derived transaction (their ids overlap). */
    public static final String EXTRA_SELECTED_DIRECT = "EXTRA_SELECTED_DIRECT";
    /** Optional: opens with this status filter (a {@link StatusFilter} name); absent means All Statuses. */
    public static final String EXTRA_STATUS_FILTER = "EXTRA_STATUS_FILTER";

    /** The Status filter - All Statuses plus each individual status. */
    enum StatusFilter {
        ALL(R.string.upcoming_filter_all, null),
        PENDING(R.string.upcoming_status_pending, "Pending"),
        CONFIRMED(R.string.upcoming_status_confirmed, "Confirmed"),
        CHECKED_IN(R.string.upcoming_status_checked_in, "Checked-In"),
        CHECKED_OUT(R.string.upcoming_status_checked_out, "Checked-Out"),
        CANCELLED(R.string.upcoming_status_cancelled, "Cancelled"),
        REJECTED(R.string.upcoming_status_rejected, "Rejected");

        final int labelRes;
        @Nullable final String status;

        StatusFilter(int labelRes, @Nullable String status) {
            this.labelRes = labelRes;
            this.status = status;
        }

        boolean matches(@NonNull Booking b) {
            if (status == null) return true;
            String s = b.getStatus();
            if (status.equalsIgnoreCase(s)) return true;
            // "Completed" is the same lifecycle step as Checked-Out.
            return this == CHECKED_OUT && "Completed".equalsIgnoreCase(s);
        }

        static StatusFilter fromName(@Nullable String name) {
            if (name != null) {
                for (StatusFilter f : values()) {
                    if (f.name().equalsIgnoreCase(name)) return f;
                }
            }
            return ALL;
        }
    }

    private enum TypeFilter {
        ALL(R.string.upcoming_filter_all),
        BOOKINGS(R.string.upcoming_type_bookings),
        RESERVATIONS(R.string.upcoming_type_reservations);

        final int labelRes;

        TypeFilter(int labelRes) {
            this.labelRes = labelRes;
        }
    }

    /** The Date filter's choices: no date filter, or the calendar's Daily / Weekly / Monthly window around the chosen day. */
    private static final StayCalendarModel.Mode[] PERIOD_CHOICES = {
            null, StayCalendarModel.Mode.DAILY, StayCalendarModel.Mode.WEEKLY, StayCalendarModel.Mode.MONTHLY};

    private static int periodLabel(@Nullable StayCalendarModel.Mode mode) {
        if (mode == StayCalendarModel.Mode.DAILY) return R.string.stay_calendar_daily;
        if (mode == StayCalendarModel.Mode.WEEKLY) return R.string.stay_calendar_weekly;
        if (mode == StayCalendarModel.Mode.MONTHLY) return R.string.stay_calendar_monthly;
        return R.string.upcoming_filter_all;
    }

    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy • h:mm:ss a", Locale.ENGLISH);

    /** Opens Upcoming Hotel Transactions on All Statuses (and All Upcoming), with nothing focused. */
    public static Intent intentAllStatuses(@NonNull Context context) {
        return new Intent(context, UpcomingTransactionsActivity.class)
                .putExtra(EXTRA_STATUS_FILTER, StatusFilter.ALL.name());
    }

    /** Opens Upcoming Hotel Transactions scrolled to and highlighting exactly this transaction. */
    public static Intent intentFor(@NonNull Context context, @NonNull Booking booking) {
        return new Intent(context, UpcomingTransactionsActivity.class)
                .putExtra(EXTRA_SELECTED_BOOKING_ID, booking.getId())
                .putExtra(EXTRA_SELECTED_DIRECT, booking.isDirectBooking());
    }

    private LinearLayout listContainer;
    private View emptyState, loadingOverlay;
    private TextView resultCount;
    private androidx.core.widget.NestedScrollView screenContent;
    private AutoCompleteTextView dropdownType, dropdownStatus, dropdownDate;
    private TextView clock;
    private DashboardStayCalendar calendar;
    private StayCalendarModel.Mode periodMode = null; // null = any date
    /** The day the Daily / Weekly / Monthly window is cut around - the calendar's selected day. */
    private LocalDate periodDate = LocalDate.now(TimeUtils.ZONE_MANILA);
    private RoomRepository repository;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            clock.setText(CLOCK_FORMAT.format(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())));
            clockHandler.postDelayed(this, 1000 - now % 1000); // land on the next whole second
        }
    };

    /** Every transaction the app holds for the guest - the full set, never trimmed by the client. */
    private final List<Booking> allTransactions = new ArrayList<>();
    private TypeFilter typeFilter = TypeFilter.ALL;
    private StatusFilter statusFilter = StatusFilter.ALL;

    private String selectedId;
    private Boolean selectedDirect;
    private boolean pendingScrollToSelected;
    private boolean attemptedLookup;
    /** The first answer (or cached list) has been drawn - before that an unknown id may just not have loaded yet. */
    private boolean loadedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.upcomingtransactions);
        setupGuestNavigation(R.id.nav_upcoming_transactions);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);

        listContainer = findViewById(R.id.upcomingTransactionsListContainer);
        emptyState = findViewById(R.id.layoutUpcomingEmptyState);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        resultCount = findViewById(R.id.tvUpcomingResultCount);
        screenContent = findViewById(R.id.screenContent);
        dropdownType = findViewById(R.id.dropdownUpcomingType);
        dropdownStatus = findViewById(R.id.dropdownUpcomingStatus);
        dropdownDate = findViewById(R.id.dropdownUpcomingDate);
        clock = findViewById(R.id.tvUpcomingClock);

        setupFilters();
        setupCalendar();
        findViewById(R.id.btnUpcomingClearFilters).setOnClickListener(v -> resetFilters());

        readIntent(getIntent());
        loadEvents();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
        renderEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.post(clockTick); // runs only while the screen is visible
        loadEvents();
    }

    @Override
    protected void onPause() {
        clockHandler.removeCallbacks(clockTick);
        super.onPause();
    }

    /** Reads the deep link (a focused transaction) and the opening status filter. */
    private void readIntent(Intent intent) {
        selectedId = intent.getStringExtra(EXTRA_SELECTED_BOOKING_ID);
        if (selectedId != null && selectedId.trim().isEmpty()) selectedId = null;
        selectedDirect = intent.hasExtra(EXTRA_SELECTED_DIRECT) ? intent.getBooleanExtra(EXTRA_SELECTED_DIRECT, false) : null;
        pendingScrollToSelected = selectedId != null;
        attemptedLookup = false;
        if (intent.hasExtra(EXTRA_STATUS_FILTER)) {
            statusFilter = StatusFilter.fromName(intent.getStringExtra(EXTRA_STATUS_FILTER));
            syncDropdowns();
        }
    }

    /** The three combo boxes: picking a value re-filters the list at once. */
    private void setupFilters() {
        bindDropdown(dropdownType, TypeFilter.values(), f -> f.labelRes, f -> {
            typeFilter = f;
            renderEvents();
        });
        bindDropdown(dropdownStatus, StatusFilter.values(), f -> f.labelRes, f -> {
            statusFilter = f;
            renderEvents();
        });
        bindDropdown(dropdownDate, PERIOD_CHOICES, UpcomingTransactionsActivity::periodLabel, mode -> {
            periodMode = mode;
            if (mode != null) calendar.setWindow(mode, periodDate); // the calendar shows the window the list is cut to
            renderEvents();
        });
        syncDropdowns();
    }

    private <T> void bindDropdown(AutoCompleteTextView view, T[] values, java.util.function.ToIntFunction<T> label, Consumer<T> onPicked) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = getString(label.applyAsInt(values[i]));
        view.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels));
        view.setOnItemClickListener((parent, v, position, id) -> onPicked.accept(values[position]));
    }

    /** Makes the combo boxes show the current filter values (set without filtering the dropdown's own list). */
    private void syncDropdowns() {
        if (dropdownType == null) return;
        dropdownType.setText(getString(typeFilter.labelRes), false);
        dropdownStatus.setText(getString(statusFilter.labelRes), false);
        dropdownDate.setText(getString(periodLabel(periodMode)), false);
    }

    /**
     * The same Stay Calendar the Dashboard shows. Picking a day (or Daily / Weekly / Monthly / a specific date) on it
     * becomes the Date filter, so the list below shows that window; its own list is hidden because the cards below are it.
     */
    private void setupCalendar() {
        calendar = new DashboardStayCalendar(findViewById(R.id.stayCalendar), periodDate);
        calendar.setSummaryVisible(false);
        calendar.setListener((mode, date) -> {
            periodMode = mode;
            periodDate = date;
            syncDropdowns();
            renderEvents();
        });
    }

    /** All Upcoming + All Statuses + Any date: the way out of an over-filtered list, and what a deep link needs to be visible. */
    private void resetFilters() {
        typeFilter = TypeFilter.ALL;
        statusFilter = StatusFilter.ALL;
        periodMode = null;
        syncDropdowns();
        renderEvents();
    }

    private boolean anyFilterActive() {
        return typeFilter != TypeFilter.ALL || statusFilter != StatusFilter.ALL || periodMode != null;
    }

    private boolean passesFilters(@NonNull Booking b) {
        if (typeFilter == TypeFilter.BOOKINGS && !b.isHasBooking()) return false;
        if (typeFilter == TypeFilter.RESERVATIONS && b.isHasBooking()) return false;
        if (!statusFilter.matches(b)) return false;
        return inPeriod(b);
    }

    /** True when the stay overlaps the chosen Daily / Weekly / Monthly window around the calendar's selected day; always true for "All". */
    private boolean inPeriod(@NonNull Booking b) {
        if (periodMode == null) return true;
        LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
        LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
        if (in == null) return false;
        if (out == null || out.isBefore(in)) out = in;
        return !in.isAfter(StayCalendarModel.windowEnd(periodMode, periodDate))
                && !out.isBefore(StayCalendarModel.windowStart(periodMode, periodDate));
    }

    private void loadEvents() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                if (isFinishing() || isDestroyed()) return;
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                List<Booking> shown = repository.getAllBookings();
                eventsGate.accept(shown, ChangeGate.today()); // what is about to be drawn - see onVisiblePoll()
                setTransactions(shown);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                List<Booking> shown = repository.getAllBookings();
                eventsGate.accept(shown, ChangeGate.today());
                setTransactions(shown);
                Toast.makeText(UpcomingTransactionsActivity.this,
                        "Couldn't refresh upcoming transactions: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** What the list was last drawn from, so the silent 30s check redraws only on a real change. */
    private final ChangeGate eventsGate = new ChangeGate();

    /**
     * The 30s beat (started/stopped with the screen by BaseNavigationActivity): a light, silent check for changes,
     * with no loading overlay and no message if it fails. The list is rebuilt only if the bookings changed or the
     * day rolled over. The base class's badge check is kept.
     */
    @Override
    protected void onVisiblePoll() {
        super.onVisiblePoll();
        repository.pollBookingsSplit((merged, reservationsError, directError) -> {
            if (isFinishing() || isDestroyed() || (reservationsError != null && directError != null)) return;
            if (eventsGate.accept(merged, ChangeGate.today())) setTransactions(repository.getAllBookings());
        });
    }

    private void setTransactions(List<Booking> bookings) {
        loadedOnce = true;
        allTransactions.clear();
        for (Booking b : bookings) {
            if (b != null) allTransactions.add(b);
        }
        calendar.setBookings(allTransactions, true); // every status is listed here, so every stay day is shaded
        renderEvents();
    }

    // ---- Ordering ----

    /** Pending / confirmed / in-house stays are still ahead of the guest; checked-out, cancelled and rejected are closed. */
    private static boolean isOpen(@NonNull Booking b) {
        String s = b.getStatus();
        return !("Checked-Out".equalsIgnoreCase(s) || "Completed".equalsIgnoreCase(s)
                || "Cancelled".equalsIgnoreCase(s) || "Rejected".equalsIgnoreCase(s));
    }

    private static final Comparator<Booking> BY_CHECK_IN_ASC = (x, y) -> compareDates(
            PaymentDates.parseDate(x.getCheckInDate()), PaymentDates.parseDate(y.getCheckInDate()), false);
    private static final Comparator<Booking> BY_CHECK_IN_DESC = (x, y) -> compareDates(
            PaymentDates.parseDate(x.getCheckInDate()), PaymentDates.parseDate(y.getCheckInDate()), true);

    /** Unreadable dates always sort last. */
    private static int compareDates(@Nullable LocalDate a, @Nullable LocalDate b, boolean descending) {
        if (a == null || b == null) return a == null ? (b == null ? 0 : 1) : -1;
        return descending ? b.compareTo(a) : a.compareTo(b);
    }

    // ---- Rendering ----

    private void renderEvents() {
        if (listContainer == null) return;
        List<Booking> open = new ArrayList<>();
        List<Booking> closed = new ArrayList<>();
        for (Booking b : allTransactions) {
            if (!passesFilters(b)) continue;
            (isOpen(b) ? open : closed).add(b);
        }
        Collections.sort(open, BY_CHECK_IN_ASC);
        Collections.sort(closed, BY_CHECK_IN_DESC);
        int shown = open.size() + closed.size();

        // A deep link must always be able to land: if the active filters hide the focused transaction, drop them.
        Booking focus = selectedId == null ? null
                : TransactionNavigator.pick(allTransactions, selectedId, selectedDirect, null, null);
        if (focus != null && !open.contains(focus) && !closed.contains(focus) && anyFilterActive()) {
            resetFilters(); // re-enters renderEvents() with nothing filtered
            return;
        }

        listContainer.removeAllViews();
        emptyState.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        resultCount.setVisibility(shown == 0 ? View.GONE : View.VISIBLE);
        resultCount.setText(getString(R.string.upcoming_results_format, shown, allTransactions.size()));

        View focusCard = null;
        if (!open.isEmpty()) {
            addGroupHeader(R.string.upcoming_group_active, !closed.isEmpty());
            for (Booking b : open) {
                View card = addEventCard(b);
                if (b == focus) focusCard = card;
            }
        }
        if (!closed.isEmpty()) {
            addGroupHeader(R.string.upcoming_group_closed, !open.isEmpty());
            for (Booking b : closed) {
                View card = addEventCard(b);
                if (b == focus) focusCard = card;
            }
        }
        applyFocus(focus, focusCard);
    }

    /** A small heading, only when both groups are showing (a single group needs no label). */
    private void addGroupHeader(int titleRes, boolean needed) {
        if (!needed) return;
        TextView header = new TextView(this);
        androidx.core.widget.TextViewCompat.setTextAppearance(header, R.style.TextAppearance_Velocity_Label_Section);
        header.setText(titleRes);
        header.setAccessibilityHeading(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        lp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        listContainer.addView(header, lp);
    }

    /**
     * Highlights the focused transaction on every render (so the highlight survives a refresh or filter change) and, once
     * per launch, scrolls it into view. A transaction that is not loaded at all is fetched by id exactly once.
     */
    private void applyFocus(@Nullable Booking focus, @Nullable View focusCard) {
        if (selectedId == null) return;
        if (focus == null || focusCard == null) {
            if (focus == null && !attemptedLookup && loadedOnce) lookUpSelected();
            return;
        }
        if (focusCard instanceof MaterialCardView) {
            MaterialCardView cardView = (MaterialCardView) focusCard;
            cardView.setStrokeColor(getColor(R.color.velocity_red_primary));
            cardView.setStrokeWidth((int) (3 * getResources().getDisplayMetrics().density));
            cardView.setCardElevation(6 * getResources().getDisplayMetrics().density);
        }
        if (!pendingScrollToSelected || screenContent == null) return;
        pendingScrollToSelected = false;
        final View target = focusCard;
        screenContent.post(() -> {
            int offset = 0;
            View v = target;
            while (v != null && v != screenContent) {
                offset += v.getTop();
                Object parent = v.getParent();
                if (!(parent instanceof View)) break;
                v = (View) parent;
            }
            screenContent.smoothScrollTo(0, Math.max(0, offset - 24));
        });
    }

    /** The focused transaction is older than the loaded window: ask for it by id once (a real 404 shows the friendly message once). */
    private void lookUpSelected() {
        attemptedLookup = true;
        List<RoomRepository.TransactionFamily> order = selectedDirect != null
                ? Collections.singletonList(selectedDirect ? RoomRepository.TransactionFamily.DIRECT_BOOKING : RoomRepository.TransactionFamily.RESERVATION)
                : TransactionNavigator.lookupOrder(null);
        repository.lookupTransaction(selectedId, order, new RoomRepository.TransactionLookupCallback() {
            @Override
            public void onFound(Booking booking) {
                if (isFinishing() || isDestroyed()) return;
                setTransactions(repository.getAllBookings()); // re-enters applyFocus(), which now finds it
            }

            @Override
            public void onNotFound() {
                if (isFinishing() || isDestroyed()) return;
                pendingScrollToSelected = false;
                TransactionNavigator.showNotFoundDialog(UpcomingTransactionsActivity.this);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                pendingScrollToSelected = false;
                Toast.makeText(UpcomingTransactionsActivity.this,
                        getString(R.string.upcoming_lookup_error_format, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---- Card ----

    private View addEventCard(@NonNull Booking b) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_upcoming_transaction_detail, listContainer, false);

        TextView tvTomorrowBadge = card.findViewById(R.id.tvTomorrowBadge);
        TextView tvPaymentStatus = card.findViewById(R.id.tvTransactionPaymentStatus);

        // The reference leads: "Booking #338" / "Reservation #588".
        ((TextView) card.findViewById(R.id.tvTransactionBookingId)).setText(TransactionText.reference(this, b.isHasBooking(), b.getDisplayId()));
        ((TextView) card.findViewById(R.id.tvTransactionTitle)).setText(TransactionRow.roomTextOf(b));
        TransactionStatusChip.apply(this, card.findViewById(R.id.tvTransactionStatus), b.getStatus());

        // What happens (or happened) and when.
        TextView tvKind = card.findViewById(R.id.tvEventKind);
        TextView tvWhen = card.findViewById(R.id.tvEventWhen);
        String status = b.getStatus();
        boolean upcomingCheckIn = false;
        String tomorrowDate = null;
        if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            tvKind.setText(R.string.upcoming_event_cancelled);
            tvWhen.setText(b.getCancellationDate() != null ? b.getCancellationDate() : b.getCheckInDate());
        } else if ("Checked-Out".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status)) {
            tvKind.setText(R.string.stay_calendar_kind_checked_out);
            tvWhen.setText(b.getActualCheckOut() != null ? b.getActualCheckOut() : b.getCheckOutDate());
        } else if ("Checked-In".equalsIgnoreCase(status)) {
            tvKind.setText(R.string.stay_calendar_kind_checkout);
            tvWhen.setText(getString(R.string.stay_calendar_when_format, b.getCheckOutDate(),
                    StayCalendarModel.formatTime(StayCalendarModel.CHECK_OUT_TIME)));
            tomorrowDate = b.getCheckOutDate();
        } else {
            tvKind.setText(R.string.stay_calendar_kind_checkin);
            tvWhen.setText(getString(R.string.stay_calendar_when_format, b.getCheckInDate(),
                    StayCalendarModel.formatTime(StayCalendarModel.CHECK_IN_TIME)));
            upcomingCheckIn = true;
            tomorrowDate = b.getCheckInDate();
        }
        ((TextView) card.findViewById(R.id.tvTransactionStay)).setText(getString(R.string.upcoming_stay_format,
                b.getCheckInDate(), b.getCheckOutDate(), getResources().getQuantityString(R.plurals.guests_count, b.getGuests(), b.getGuests())));

        // Balance still to pay, from the same helper as every other status in the app.
        ((TextView) card.findViewById(R.id.tvTransactionBalance)).setText(MoneyFormat.format(TransactionStatusHelper.summarize(b).balance));

        PaymentStatusResolver.Result paymentStatus = PaymentStatusResolver.resolve(this, b);
        tvPaymentStatus.setText(paymentStatus.label);
        tvPaymentStatus.setBackgroundTintList(getColorStateList(paymentStatus.bgColorRes));
        tvPaymentStatus.setTextColor(getColor(paymentStatus.fgColorRes));

        LocalDate tomorrow = LocalDate.now(TimeUtils.ZONE_MANILA).plusDays(1);
        if (tomorrowDate != null && tomorrow.equals(PaymentDates.parseDate(tomorrowDate))) {
            tvTomorrowBadge.setVisibility(View.VISIBLE);
            tvTomorrowBadge.setText(upcomingCheckIn ? R.string.check_in_tomorrow_label : R.string.check_out_tomorrow_label);
        } else {
            tvTomorrowBadge.setVisibility(View.GONE);
        }

        // The button (and a tap anywhere on the card) opens this transaction's record in Transaction History.
        View.OnClickListener openHistory = v -> openInTransactionHistory(b);
        card.findViewById(R.id.btnViewFullDetails).setOnClickListener(openHistory);
        card.setOnClickListener(openHistory);

        listContainer.addView(card);
        return card;
    }

    /** Transaction History, with this exact record highlighted and opened (its id plus which table it lives in). */
    private void openInTransactionHistory(@NonNull Booking b) {
        Intent intent = new Intent(this, TransactionHistoryActivity.class);
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, b.getId());
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_DIRECT, b.isDirectBooking());
        startActivity(intent);
    }
}
