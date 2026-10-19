package com.example.velocitysuites;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.graphics.Color;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.example.velocitysuites.network.SessionManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends BaseNavigationActivity {

    private TextView dateText;
    private TextView timeText;
    /** The four mutually exclusive states of the Next Stay card. */
    private View nextStayLoading, nextStayError, nextStayEmpty, nextStayContent;
    /** True once the bookings request has answered (success or failure) - until then the card says it is loading. */
    private boolean bookingsResolved = false;
    /** True when the last bookings refresh failed (the sections then offer Retry / a pull-to-refresh hint if they have nothing to show). */
    private boolean bookingsLoadFailed = false;
    /** Same two flags for the notifications request. */
    private boolean notificationsResolved = false;
    private boolean notificationsLoadFailed = false;

    /** What a data section shows when it has nothing to list: still loading, could not load (Retry), or loaded and genuinely empty (OK). */
    private enum SectionState { OK, LOADING, ERROR }
    private TextView activeReservationsCountText, cancelReservationsCountText, activeBookingsCountText, cancelBookingsCountText;
    private TextView pastBookingsCountText, pastReservationsCountText;
    private LinearLayout bookingListContainer, paymentListContainer, notificationListContainer;
    private TextView noBookingsText, noNotificationsText, noPaymentsText;
    private View noBookingsIcon, btnEmptyBookNow;
    private TextView bookingCountBadge, notificationCountBadge, paymentCountBadge;
    /** Collapse / expand controls: every dashboard section except Account Summary. (Stay Calendar owns its own.) */
    private CollapsibleSection nextStaySection, moreStaysSection, bookingsSection, notificationsSection, paymentsSection;
    private CollapsibleSection recentUpcomingSection;
    /** The data the list sections were last drawn from, so a collapse / expand can redraw without asking the network. */
    private List<Booking> lastBookings = new ArrayList<>();
    private List<Notification> lastNotifications = new ArrayList<>();
    private CollapsibleSection summarySection, supportSection;
    /** Every stay Other Stays lists: upcoming soonest first, finished latest first. */
    private List<Booking> moreStaysUpcoming = new ArrayList<>(), moreStaysRecent = new ArrayList<>();
    /** "Pending & Confirmed Stays": every Pending / Confirmed transaction, and whether the full list is showing. */
    private List<Booking> recentUpcoming = new ArrayList<>();
    /** The day chosen in Your Next Stay's day selector (null until first drawn: then Today), and the data it draws from. */
    private java.time.LocalDate selectedStayDay;
    private List<Booking> nextStayBookings = new ArrayList<>();
    private boolean nextStayStripBuilt;
    /** Every transaction Other Stays is drawn from (independent of what Your Next Stay shows). */
    private List<Booking> otherStaysSource = new ArrayList<>();
    /** Set when the day strip should scroll the selected day into view on its next draw (first draw, Today shortcut). */
    private boolean scrollStripToSelected;
    /** Daily / Weekly / Monthly of Your Next Stay; saved with the other section state. */
    private StayCalendarModel.Mode nextStayMode;
    private boolean bindingNextStayMode;
    private static final String KEY_NEXT_STAY_MODE = "next_stay_mode_v1";

    private TextView accountFullNameText, accountEmailText, accountMobileText, membershipStatusText, accountStatusText;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;
    /** (TextView, Notification) pairs for every compact card currently shown in the "Recent Notifications" widget - lets notifTimeRefreshRunnable below re-render just their relative time text without rebuilding the section (which would also re-trigger the header/unread-dot/icon binding and the row's expand/collapse animation state). Rebuilt from scratch each time updateNotificationsSection() rebuilds the section itself. */
    private final List<androidx.core.util.Pair<TextView, Notification>> compactNotificationTimeViews = new ArrayList<>();
    // Independent of loadDashboardData()'s own refresh (which already re-renders this
    // section with fresh data whenever the guest navigates back to this screen) - this
    // is purely for the guest who stays on Dashboard without navigating away at all,
    // where "5 minutes ago" would otherwise silently go stale for as long as they stay.
    private static final long NOTIF_TIME_REFRESH_MS = 60000;
    private final Handler notifTimeRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable notifTimeRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            for (androidx.core.util.Pair<TextView, Notification> pair : compactNotificationTimeViews) {
                pair.first.setText(NotificationRowBinder.timeOf(DashboardActivity.this, pair.second, System.currentTimeMillis()));
            }
            notifTimeRefreshHandler.postDelayed(this, NOTIF_TIME_REFRESH_MS);
        }
    };
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshDashboard;
    /** What populateDashboard() last drew - lets the silent 30s check skip a redraw when nothing changed (see onVisiblePoll()). */
    private final ChangeGate dashboardGate = new ChangeGate();
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
                // Denial is handled gracefully everywhere else too (NotificationHelper checks
                // areNotificationsEnabled()/catches SecurityException before every post) - this
                // callback only exists so the system prompt actually fires; there's nothing
                // further to do on either outcome.
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Guards against reaching the dashboard with no valid session at all
        // (e.g. a stale deep link/task-stack entry surviving a Logout or an
        // expired-and-cleared Remember Me session elsewhere) - normal entry
        // is already gated upstream by LoginActivity/RegistrationActivity,
        // so this is a defensive backstop, not the primary auth check.
        if (!SessionManager.isLoggedIn(this)) {
            android.content.Intent intent = new android.content.Intent(this, WelcomeActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.dashboard);

        setupGuestNavigation(R.id.nav_dashboard);
        wireDashboardActions();
        requestNotificationPermissionIfNeeded();

        dateText = findViewById(R.id.dateText);
        timeText = findViewById(R.id.timeText);

        initializeNewComponents();
        // Until the first answer arrives every data section says "Loading…" (never "you have none").
        applySectionStates(RoomRepository.getInstance(this).getBookings(), RoomRepository.getInstance(this).getNotifications());

        swipeRefreshDashboard = findViewById(R.id.swipeRefreshDashboard);
        if (swipeRefreshDashboard != null) {
            swipeRefreshDashboard.setColorSchemeResources(R.color.velocity_red_primary);
            swipeRefreshDashboard.setOnRefreshListener(() -> {
                loadUserInfo();
                loadDashboardData();
            });
        }

        // Not loaded here - onResume() always fires immediately after
        // onCreate() on first launch (standard Activity lifecycle), and
        // already does both these calls unconditionally on every resume
        // (including this first one) so the dashboard also refreshes
        // correctly when returning from another screen. Calling them here
        // too previously fired every network request in loadDashboardData()
        // (bookings + notifications refresh) twice on every single launch.

        startAnimations();
        initClock();
        stayCalendar = new DashboardStayCalendar(findViewById(R.id.stayCalendar), hotelToday());
    }

    private void initializeNewComponents() {
        nextStayLoading = findViewById(R.id.nextStayLoading);
        nextStayError = findViewById(R.id.nextStayError);
        nextStayEmpty = findViewById(R.id.nextStayEmpty);
        nextStayContent = findViewById(R.id.nextStayContent);

        activeReservationsCountText = findViewById(R.id.activeReservationsCountText);
        cancelReservationsCountText = findViewById(R.id.cancelReservationsCountText);
        activeBookingsCountText = findViewById(R.id.activeBookingsCountText);
        cancelBookingsCountText = findViewById(R.id.cancelBookingsCountText);
        pastBookingsCountText = findViewById(R.id.pastBookingsCountText);
        pastReservationsCountText = findViewById(R.id.pastReservationsCountText);

        bookingListContainer = findViewById(R.id.bookingListContainer);
        paymentListContainer = findViewById(R.id.paymentListContainer);
        notificationListContainer = findViewById(R.id.notificationListContainer);

        noBookingsText = findViewById(R.id.noBookingsText);
        noBookingsIcon = findViewById(R.id.noBookingsIcon);
        btnEmptyBookNow = findViewById(R.id.btnEmptyBookNow);
        if (btnEmptyBookNow != null) {
            btnEmptyBookNow.setOnClickListener(v -> openBookingSection(BookingAndReservationActivity.SECTION_BOOKING));
        }
        noNotificationsText = findViewById(R.id.noNotificationsText);
        noPaymentsText = findViewById(R.id.noPaymentsText);

        bookingCountBadge = findViewById(R.id.bookingCountBadge);
        notificationCountBadge = findViewById(R.id.notificationCountBadge);
        paymentCountBadge = findViewById(R.id.paymentCountBadge);

        // List sections keep their list on screen: collapsed = the first few items + "Show all (N)", expanded = everything.
        // Your Next Stay collapses completely (header only) and opens to the strip, the context line and every card.
        // It opens expanded; "next_stay_full" is a new key so the old collapsed-preview choice does not carry over.
        nextStaySection = new CollapsibleSection(this, "next_stay_full", findViewById(R.id.nextStayToggle),
                null, true, findViewById(R.id.nextStayCard))
                .named(getString(R.string.next_stay_section_title)).animated().onChange(this::onNextStayToggled);
        moreStaysSection = new CollapsibleSection(this, "more_stays", findViewById(R.id.moreStaysToggle),
                findViewById(R.id.moreStaysSummary), findViewById(R.id.moreStaysBody)).previewList(this::renderMoreStays);
        // Quick Actions has no collapse control: all four actions are always shown.
        // Booking & Reservation Summary opens expanded (Active, Past and Cancelled rows); the guest can still collapse it to Active only.
        summarySection = new CollapsibleSection(this, "booking_summary", findViewById(R.id.summaryToggle),
                findViewById(R.id.summarySummary), true, findViewById(R.id.summaryBody))
                .collapsedHides(findViewById(R.id.tilePastBooking), findViewById(R.id.tilePastReservation),
                        findViewById(R.id.tileCancelBooking), findViewById(R.id.tileCancelReservation));
        recentUpcomingSection = new CollapsibleSection(this, "recent_upcoming", findViewById(R.id.recentUpcomingToggle),
                null, findViewById(R.id.recentUpcomingList)).previewList(this::renderRecentUpcoming)
                .named(getString(R.string.recent_upcoming_title));
        supportSection = new CollapsibleSection(this, "support", findViewById(R.id.supportToggle),
                findViewById(R.id.supportSummary), findViewById(R.id.supportDesc), findViewById(R.id.btnContactSupport));
        supportSection.setSummary(getString(R.string.support_collapsed_summary));
        bookingsSection = new CollapsibleSection(this, "recent_bookings", findViewById(R.id.bookingsToggle),
                findViewById(R.id.bookingsSummary), findViewById(R.id.bookingsBody)).previewList(() -> populateBookings(lastBookings));
        notificationsSection = new CollapsibleSection(this, "notifications", findViewById(R.id.notificationsToggle),
                findViewById(R.id.notificationsSummary), findViewById(R.id.notificationsBody)).previewList(() -> populateNotifications(lastNotifications));
        paymentsSection = new CollapsibleSection(this, "payment_status", findViewById(R.id.paymentsToggle),
                findViewById(R.id.paymentsSummary), findViewById(R.id.paymentsBody)).previewList(() -> populatePayments(lastBookings));

        accountFullNameText = findViewById(R.id.accountFullNameText);
        accountEmailText = findViewById(R.id.accountEmailText);
        accountMobileText = findViewById(R.id.accountMobileText);
        membershipStatusText = findViewById(R.id.membershipStatusText);
        accountStatusText = findViewById(R.id.accountStatusText);
        bindAccountSummary(); // the profile is cached on the phone: show it (and the donut) at once, not after the network answers
    }

    private void loadDashboardData() {
        RoomRepository repository = RoomRepository.getInstance(this);
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                if (isFinishing() || isDestroyed()) return;
                bookingsResolved = true;
                bookingsLoadFailed = false;
                populateDashboard(repository.getBookings(), repository.getNotifications());
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                bookingsResolved = true;
                bookingsLoadFailed = true;
                populateDashboard(repository.getBookings(), repository.getNotifications());
            }
        });
        repository.refreshNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                if (isFinishing() || isDestroyed()) return;
                notificationsResolved = true;
                notificationsLoadFailed = false;
                updateNotificationBadge();
                populateDashboard(repository.getBookings(), repository.getNotifications());
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                // A failure is shown in the Notifications section (never as "no notifications").
                notificationsResolved = true;
                notificationsLoadFailed = true;
                populateDashboard(repository.getBookings(), repository.getNotifications());
            }
        });
    }

    /**
     * This screen's own loadDashboardData() (called from onResume(), right after
     * super.onResume()) already does its own fresh refreshNotifications() call above -
     * the shared header's badge-only refetch would just be a second, redundant network
     * call on every single dashboard resume.
     */
    @Override
    protected void refreshNotificationBadge() {
        updateNotificationBadge();
    }

    /**
     * The 15s beat (started/stopped with the screen by BaseNavigationActivity): while the guest stays on the
     * dashboard, quietly check for new or changed bookings and notifications - the same light checks
     * Transaction History and Notifications use - and redraw ONLY if something really changed. No spinner and no
     * error message (a background miss is invisible; the next beat or any real action tries again), and an
     * unchanged dashboard is left exactly as it is, so a card the guest has open stays open.
     */
    @Override
    protected long visiblePollIntervalMs() {
        return VisiblePoller.LIVE_BOOKING_INTERVAL_MS; // "Your Next Stay" follows the front desk closely
    }

    @Override
    protected void onVisiblePoll() {
        RoomRepository repository = RoomRepository.getInstance(this);
        final int[] pending = {2};
        Runnable oneSettled = () -> {
            if (--pending[0] > 0 || isFinishing() || isDestroyed()) return;
            List<Booking> bookings = repository.getBookings();
            List<Notification> notifications = repository.getNotifications();
            if (dashboardGate.accept(bookings, notifications, ChangeGate.today())) populateDashboard(bookings, notifications);
        };
        repository.pollBookingsSplit((merged, reservationsError, directError) -> oneSettled.run());
        repository.pollNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                if (!isFinishing() && !isDestroyed()) updateNotificationBadge();
                oneSettled.run();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                oneSettled.run();
            }
        });
    }

    private void populateDashboard(List<Booking> allBookings, List<Notification> allNotifications) {
        dashboardGate.accept(allBookings, allNotifications, ChangeGate.today());
        if (swipeRefreshDashboard != null) swipeRefreshDashboard.setRefreshing(false);
        RoomRepository repository = RoomRepository.getInstance(this);

        updateNextStay(allBookings);
        updateRecentUpcoming(allBookings);

        // Booking and Reservation Summary: reservations (no payment yet) vs.
        // bookings (paid), each split by TransactionCategorizer - the same
        // single source of truth bookingandreservation.xml's inline filter
        // and TransactionListActivity use. "Active" here includes Upcoming
        // (future-dated) items too, matching bookingandreservation.xml's
        // combined Active list - see
        // BookingAndReservationActivity#itemsForCurrentTab().
        int activeReservations = 0, cancelReservations = 0, activeBookings = 0, cancelBookings = 0;
        int pastReservations = 0, pastBookings = 0;
        for (Booking b : allBookings) {
            TransactionCategorizer.Category category = TransactionCategorizer.categorize(b);
            boolean terminal = category == TransactionCategorizer.Category.CANCELLED;
            boolean past = category == TransactionCategorizer.Category.COMPLETED;
            boolean activeOrUpcoming = category == TransactionCategorizer.Category.ACTIVE || category == TransactionCategorizer.Category.UPCOMING;
            if (b.isHasBooking()) {
                if (terminal) cancelBookings++;
                else if (past) pastBookings++;
                else if (activeOrUpcoming) activeBookings++;
            } else {
                if (terminal) cancelReservations++;
                else if (past) pastReservations++;
                else if (activeOrUpcoming) activeReservations++;
            }
        }
        // A reservation that's since converted to a Booking is kept as a frozen,
        // view-only historical record under the Reservation tab's Completed list
        // (see RoomRepository#getCompletedHistoricalReservations() and
        // BookingAndReservationActivity#itemsForCurrentTab()) rather than living in
        // allBookings itself, so it's added here too - Past Reservations must always
        // equal that list's exact total, not just the non-converted subset above.
        pastReservations += repository.getCompletedHistoricalReservations().size();
        summarySection.setSummary(getString(R.string.summary_overview_format,
                activeBookings + activeReservations, pastBookings + pastReservations, cancelBookings + cancelReservations));
        if (activeReservationsCountText != null) activeReservationsCountText.setText(String.valueOf(activeReservations));
        if (cancelReservationsCountText != null) cancelReservationsCountText.setText(String.valueOf(cancelReservations));
        if (activeBookingsCountText != null) activeBookingsCountText.setText(String.valueOf(activeBookings));
        if (cancelBookingsCountText != null) cancelBookingsCountText.setText(String.valueOf(cancelBookings));
        if (pastBookingsCountText != null) pastBookingsCountText.setText(String.valueOf(pastBookings));
        if (pastReservationsCountText != null) pastReservationsCountText.setText(String.valueOf(pastReservations));

        // Dim empty tiles so the guest's eye is drawn to whichever categories
        // actually have something to look at.
        dimSummaryTileIfEmpty(R.id.tileActiveBooking, activeBookings);
        dimSummaryTileIfEmpty(R.id.tileActiveReservation, activeReservations);
        dimSummaryTileIfEmpty(R.id.tilePastBooking, pastBookings);
        dimSummaryTileIfEmpty(R.id.tilePastReservation, pastReservations);
        dimSummaryTileIfEmpty(R.id.tileCancelBooking, cancelBookings);
        dimSummaryTileIfEmpty(R.id.tileCancelReservation, cancelReservations);

        // Populate Sections
        lastBookings = allBookings;
        lastNotifications = allNotifications;
        populateBookings(allBookings);
        populatePayments(allBookings);
        populateNotifications(allNotifications);
        applySectionStates(allBookings, allNotifications);

        bindAccountSummary();
    }

    private boolean profileDonutShown;

    /**
     * Account Summary: the guest's name, email and phone, the profile-completeness donut (computed by
     * {@link ProfileCompleteness} from the same cached profile fields the Dashboard always counted), what is still
     * missing, and the account status. Runs on every dashboard load AND every time the screen comes back to the front,
     * so the donut is current as soon as the guest returns from Edit Profile.
     */
    private void bindAccountSummary() {
        SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        String fullName = prefs.getString("userName", getString(R.string.guest_user));
        String email = prefs.getString("userEmail", getString(R.string.guest_email));
        String mobile = prefs.getString("userMobile", getString(R.string.mock_mobile));
        accountFullNameText.setText(fullName);
        accountEmailText.setText(email);
        accountMobileText.setText(mobile);

        ProfileCompleteness completeness = ProfileCompleteness.of(fullName, email, mobile,
                prefs.getString("userGender", ""), prefs.getString("userDob", ""), SessionManager.getProfilePictureUrl(this),
                getString(R.string.guest_user), getString(R.string.guest_email), getString(R.string.mock_mobile));
        ProfileDonutView donut = findViewById(R.id.profileDonut);
        donut.setProgress(completeness.percent, true);
        profileDonutShown = true;
        TextView missing = findViewById(R.id.profileMissingText);
        String line = completeness.missingLine(this);
        missing.setText(line == null ? "" : line);
        missing.setVisibility(line == null ? View.GONE : View.VISIBLE);
        View.OnClickListener openEditProfile = NavUtils.debounce(v -> startActivity(new android.content.Intent(this, ProfileManagementActivity.class)));
        findViewById(R.id.profileDonutArea).setOnClickListener(openEditProfile);
        missing.setOnClickListener(openEditProfile);

        membershipStatusText.setText(R.string.account_type_guest);
        boolean sessionActive = SessionManager.isLoggedIn(this);
        accountStatusText.setText(sessionActive ? R.string.account_status_value_active : R.string.account_status_value_signed_out);
        View accountStatusDot = findViewById(R.id.accountStatusDot);
        accountStatusDot.setBackgroundTintList(ContextCompat.getColorStateList(this,
                sessionActive ? R.color.status_paid_fg : R.color.velocity_red_primary));
    }

    private void populateBookings(List<Booking> bookings) {
        bookingListContainer.removeAllViews();
        List<Booking> displayBookings = new java.util.ArrayList<>();
        for (Booking b : bookings) {
            // TransactionCategorizer.CANCELLED already covers both Cancelled and Rejected
            // (see its own docblock) - using it here instead of a bare "Cancelled" string
            // check was a real gap: a Rejected reservation-turned-booking record could
            // otherwise still show up as a "recent booking".
            if (b.isHasBooking() && TransactionCategorizer.categorize(b) != TransactionCategorizer.Category.CANCELLED) {
                displayBookings.add(b);
            }
        }
        SimpleDateFormat bookingDateFormat = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
        displayBookings.sort((firstBooking, secondBooking) ->
                Long.compare(parseDateSafe(bookingDateFormat, secondBooking.getBookingDate()),
                        parseDateSafe(bookingDateFormat, firstBooking.getBookingDate())));
        final int totalBookings = displayBookings.size();
        List<Booking> allDisplay = displayBookings;
        displayBookings = SectionPreview.visible(allDisplay, bookingsSection.isExpanded());

        if (displayBookings.isEmpty()) {

            noBookingsText.setVisibility(View.VISIBLE);
            if (noBookingsIcon != null) noBookingsIcon.setVisibility(View.VISIBLE);
            if (btnEmptyBookNow != null) btnEmptyBookNow.setVisibility(View.VISIBLE);
        } else {
            noBookingsText.setVisibility(View.GONE);
            if (noBookingsIcon != null) noBookingsIcon.setVisibility(View.GONE);
            if (btnEmptyBookNow != null) btnEmptyBookNow.setVisibility(View.GONE);
            for (Booking booking : displayBookings) {
                addBookingCard(booking);
            }
            SectionPreview.addToggle(this, bookingListContainer, totalBookings, bookingsSection.isExpanded(), bookingsSection::toggle);
        }
        setCountBadge(bookingCountBadge, totalBookings);
        if (displayBookings.isEmpty()) {
            bookingsSection.setSummary(getString(R.string.no_bookings_found));
        } else {
            Booking latest = displayBookings.get(0);
            bookingsSection.setSummary(getString(R.string.summary_with_latest,
                    getResources().getQuantityString(R.plurals.summary_bookings_count, totalBookings, totalBookings),
                    getString(R.string.summary_ref_status, TransactionText.reference(this, true, latest.getDisplayId()),
                            TransactionStatusChip.label(latest.getStatus()))));
        }
    }
    /**
     * Recent Bookings reuses the same compact row design as Upcoming Stays/
     * Next Hotel Transaction (room + type pill + booking id + check-in date +
     * status + payment status) so every dashboard transaction list looks and
     * behaves consistently - only the tap target differs: it deep-links
     * straight to that exact record in Transaction History's Bookings list
     * (scrolled to + highlighted), where the full detail dialog lives.
     * Sourced from the same RoomRepository bookings list Transaction History
     * reads, so a booking made via bookingandreservation.xml shows up here
     * as soon as the dashboard reloads.
     */
    private void addBookingCard(Booking b) {
        RecentBookingCard.add(this, bookingListContainer, b, TimeUtils.nowInManila().toLocalDate(), () -> openTransactionHistoryForRecentBooking(b));
    }

    private long parseDateSafe(SimpleDateFormat format, String date) {
        if (date == null || date.trim().isEmpty()) return 0;
        try {
            return format.parse(date).getTime();
        } catch (Exception e) {
            return 0;
        }
    }


    private void populatePayments(List<Booking> bookings) {
        paymentListContainer.removeAllViews();
        // Every active transaction - unpaid, partly paid or fully paid; amounts owed first, then nearest check-in.
        // A checked-out, cancelled or rejected one leaves the section at once.
        List<Booking> paymentBookings = PaymentStatusList.select(bookings);
        final int totalPayments = paymentBookings.size();
        List<Booking> allPayments = paymentBookings;
        paymentBookings = SectionPreview.visible(allPayments, paymentsSection.isExpanded());

        if (paymentBookings.isEmpty()) {
            if (noPaymentsText != null) noPaymentsText.setVisibility(View.VISIBLE);
        } else {
            if (noPaymentsText != null) noPaymentsText.setVisibility(View.GONE);
            for (Booking booking : paymentBookings) {
                addPaymentItem(booking);
            }
            SectionPreview.addToggle(this, paymentListContainer, totalPayments, paymentsSection.isExpanded(), paymentsSection::toggle);
        }
        setCountBadge(paymentCountBadge, totalPayments);
        if (paymentBookings.isEmpty()) {
            paymentsSection.setSummary(getString(R.string.no_active_payment_status));
        } else {
            Booking first = paymentBookings.get(0);
            paymentsSection.setSummary(getString(R.string.summary_with_first,
                    getResources().getQuantityString(R.plurals.summary_payments_count, totalPayments, totalPayments),
                    getString(R.string.summary_ref_status, TransactionText.reference(this, first.isHasBooking(), first.getDisplayId()),
                            PaymentStatusResolver.resolve(this, first).label)));
        }
    }
    /**
     * One payment card: ID and status chip, Total and Balance in two aligned columns (the balance is green when nothing is
     * owed, amber when partly paid, red only when money is owed), a thin progress bar with "₱X of ₱Y paid", and the actions.
     * Only receptionist-verified money counts as paid (TransactionStatusHelper); every amount uses the app's currency format.
     */
    private void addPaymentItem(Booking b) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_dashboard_payment, paymentListContainer, false);
        bindPaymentCard(card, b);
        paymentListContainer.addView(card);
    }

    private void bindPaymentCard(View card, Booking b) {
        ((TextView) card.findViewById(R.id.tvPaymentBookingId)).setText(TransactionText.reference(this, b.isHasBooking(), b.getDisplayId()));

        double total = TransactionStatusHelper.grandTotalOf(b);
        double paid = TransactionStatusHelper.verifiedPaidOf(b);
        double remaining = Math.max(0, total - paid);
        PaymentBalanceTone tone = PaymentBalanceTone.of(total, paid);

        // One colour scheme for the whole card: the chip, the balance and the progress bar all follow the tone.
        TextView tvStatus = card.findViewById(R.id.tvPaymentStatus);
        tvStatus.setText(tone.labelRes);
        tvStatus.setBackgroundTintList(ContextCompat.getColorStateList(this, tone.chipBgRes));
        tvStatus.setTextColor(getColor(tone.colorRes));

        // Which stay: its dates and booking status ("Oct 21 → Oct 22, 2026 · Pending approval").
        String stayDates = StayCardText.dates(b);
        String lifecycle = BookingStatusWords.label(this, b.getStatus());
        TextView meta = card.findViewById(R.id.tvPaymentStayMeta);
        String metaText = stayDates.isEmpty() ? lifecycle : lifecycle.isEmpty() ? stayDates : getString(R.string.payment_card_meta_format, stayDates, lifecycle);
        meta.setText(metaText);
        meta.setVisibility(metaText.isEmpty() ? View.GONE : View.VISIBLE);

        ((TextView) card.findViewById(R.id.tvTotalAmount)).setText(MoneyFormat.format(total));
        TextView balance = card.findViewById(R.id.tvPaymentRemainingBalance);
        balance.setText(MoneyFormat.format(remaining));
        balance.setTextColor(getColor(tone.colorRes));
        card.findViewById(R.id.tvPaymentBalanceNote).setVisibility(tone == PaymentBalanceTone.PAID && total > 0 ? View.VISIBLE : View.GONE);

        com.google.android.material.progressindicator.LinearProgressIndicator progress = card.findViewById(R.id.progressPayment);
        TextView paidOf = card.findViewById(R.id.tvPaymentPaidOf);
        boolean hasTotal = total > MoneyFormat.EPSILON;
        progress.setVisibility(hasTotal ? View.VISIBLE : View.GONE);
        paidOf.setVisibility(hasTotal ? View.VISIBLE : View.GONE);
        if (hasTotal) {
            progress.setProgress((int) Math.min(100, Math.round(paid * 100 / total)));
            progress.setIndicatorColor(getColor(tone.colorRes));
            progress.setTrackColor(androidx.core.graphics.ColorUtils.setAlphaComponent(getColor(tone.colorRes), 48));
            paidOf.setText(getString(R.string.payment_card_paid_of_format, MoneyFormat.format(Math.min(paid, total)), MoneyFormat.format(total)));
        }

        // Tapping the card, or View details, opens it in Transaction History under the matching Bookings / Reservations filter.
        View.OnClickListener open = v -> openTransactionHistoryForPaymentItem(b);
        card.setOnClickListener(open);
        card.findViewById(R.id.tvPaymentViewDetails).setOnClickListener(open);

        // The left action, by one rule for every card (PaymentEligibility.payActionFor).
        View btnQuickPay = card.findViewById(R.id.btnQuickPay);
        TextView note = card.findViewById(R.id.tvPaymentNote);
        PaymentEligibility.PayAction action = PaymentEligibility.payActionFor(b);
        btnQuickPay.setVisibility(action == PaymentEligibility.PayAction.PAY_NOW ? View.VISIBLE : View.GONE);
        boolean hasNote = action == PaymentEligibility.PayAction.UNDER_REVIEW || action == PaymentEligibility.PayAction.FRONT_DESK;
        note.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) note.setText(action == PaymentEligibility.PayAction.UNDER_REVIEW ? R.string.payment_under_review : R.string.payment_pay_at_front_desk);
        card.findViewById(R.id.paymentLeftAction).setVisibility(action == PaymentEligibility.PayAction.NONE ? View.GONE : View.VISIBLE);
        if (action == PaymentEligibility.PayAction.PAY_NOW) {
            btnQuickPay.setOnClickListener(v -> new SafeDialogs.Material(this)
                    .setTitle(R.string.pay_now_confirm_title)
                    .setMessage(getString(R.string.pay_now_confirm_msg, b.getDisplayId()))
                    .setPositiveButton(R.string.confirm_dialog_positive, (dialog, which) -> {
                        android.content.Intent intent = new android.content.Intent(this, BillingSummaryActivity.class);
                        intent.putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, b.getId());
                        startActivity(intent);
                    })
                    .setNegativeButton(R.string.no_label, null)
                    .show());
        }
    }

    private void populateNotifications(List<Notification> notifications) {
        notificationListContainer.removeAllViews();
        compactNotificationTimeViews.clear();
        if (notifications.isEmpty()) {
            noNotificationsText.setVisibility(View.VISIBLE);
        } else {
            noNotificationsText.setVisibility(View.GONE);
            // The first few while collapsed, every notification once expanded.
            for (Notification n : SectionPreview.visible(notifications, notificationsSection.isExpanded())) {
                addNotificationRow(n, notificationListContainer);
            }
            SectionPreview.addToggle(this, notificationListContainer, notifications.size(), notificationsSection.isExpanded(),
                    notificationsSection::toggle);
        }
        updateNotificationSectionBadge();
        if (notifications.isEmpty()) {
            notificationsSection.setSummary(getString(R.string.no_notifications));
        } else {
            int unread = 0;
            for (Notification n : notifications) if (!n.isRead()) unread++;
            String counts = getResources().getQuantityString(R.plurals.summary_notifications_count, notifications.size(), notifications.size());
            if (unread > 0) counts = getString(R.string.summary_with_first, counts, getString(R.string.summary_unread_count, unread));
            notificationsSection.setSummary(getString(R.string.summary_with_latest, counts, NotificationText.of(this, notifications.get(0)).title));
        }
    }

    /**
     * The Notifications section's count badge is the UNREAD count - the very number the bell badge in the top bar shows
     * (RoomRepository#getUnreadNotificationCount()), "99+" above 99, hidden at 0. It used to count every loaded
     * notification (the 200-row page window), so it read "200" for a guest with nothing unread.
     */
    private void updateNotificationSectionBadge() {
        if (notificationCountBadge == null) return;
        int unread = RoomRepository.getInstance(this).getUnreadNotificationCount();
        notificationCountBadge.setText(NotificationRowBinder.badgeText(this, unread));
        notificationCountBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        notificationCountBadge.setContentDescription(unread > 0 ? getString(R.string.notif_unread_badge_cd, notificationCountBadge.getText()) : null);
    }

    @Override
    protected void updateNotificationBadge() {
        super.updateNotificationBadge();
        updateNotificationSectionBadge();
    }

    /** Promotions / announcements finished loading: they are part of the Notifications section too. */
    @Override
    protected void onNotificationsListChanged() {
        lastNotifications = RoomRepository.getInstance(this).getNotifications();
        populateNotifications(lastNotifications);
    }


    /** One notification on the Dashboard: the shared row (round category icon, title, 2-line preview, time and unread dot). A tap opens its target and marks it read. */
    private void addNotificationRow(Notification n, LinearLayout container) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_notification_dashboard, container, false);
        NotificationRowBinder.bind(card, n, n.isRead(), System.currentTimeMillis());
        compactNotificationTimeViews.add(new androidx.core.util.Pair<>(card.findViewById(R.id.tvNotifTime), n));
        card.findViewById(R.id.rowNotification).setOnClickListener(NavUtils.debounce(v ->
                NotificationOpener.open(this, n, () -> {
                    if (isFinishing() || isDestroyed()) return;
                    updateNotificationBadge();
                    populateNotifications(lastNotifications);
                })));
        container.addView(card);
    }


    private void loadUserInfo() {
        TextView userNameText = findViewById(R.id.userNameText);
        TextView welcomeText = findViewById(R.id.welcomeText);
        SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        String fullName = prefs.getString("userName", getString(R.string.guest_user));
        String email = prefs.getString("userEmail", getString(R.string.guest_email));
        String mobile = prefs.getString("userMobile", getString(R.string.mock_mobile));

        if (userNameText != null) {
            userNameText.setText(getString(R.string.welcome_name_format, getWelcomeFullName(prefs, fullName)));
        }
        if (welcomeText != null) {
            welcomeText.setText(getTimeBasedGreeting());
        }

        if (accountFullNameText != null) accountFullNameText.setText(fullName);
        if (accountEmailText != null) accountEmailText.setText(email);
        if (accountMobileText != null) accountMobileText.setText(mobile);

        loadProfilePicture();
    }

    /** "Good Morning,"/"Good Afternoon,"/"Good Evening," based on the device's current hour - never hardcoded. */
    private String getTimeBasedGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int greetingRes;
        if (hour < 12) {
            greetingRes = R.string.greeting_good_morning;
        } else if (hour < 18) {
            greetingRes = R.string.greeting_good_afternoon;
        } else {
            greetingRes = R.string.greeting_good_evening;
        }
        return getString(greetingRes);
    }

    /**
     * Welcome card greets the user by first + last name, sourced from the
     * same prefs Profile Management writes to, so a name edit there shows
     * up here as soon as the user comes back. Falls back to the cached full
     * name string (already "first [middle] last" as returned by the API) if
     * the separate first/last prefs aren't populated, then to a generic
     * "Guest" label if nothing usable is available at all.
     */
    private String getWelcomeFullName(SharedPreferences prefs, String fullName) {
        String firstName = prefs.getString("userFirstName", "");
        String lastName = prefs.getString("userLastName", "");
        StringBuilder combined = new StringBuilder();
        if (firstName != null && !firstName.trim().isEmpty()) {
            combined.append(firstName.trim());
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            if (combined.length() > 0) combined.append(' ');
            combined.append(lastName.trim());
        }
        if (combined.length() > 0) {
            return combined.toString();
        }

        if (fullName != null) {
            String cleanedName = fullName.trim().replaceAll("\\s+", " ");
            if (!cleanedName.isEmpty() && !cleanedName.equals(getString(R.string.guest_user))) {
                return cleanedName;
            }
        }

        return getString(R.string.guest_user);
    }

    /**
     * Shows the same picture Profile Management uploads (server URL cached
     * in the session); falls back to the neutral avatar when the account
     * has no photo yet.
     */
    private void loadProfilePicture() {
        String pictureUrl = SessionManager.getProfilePictureUrl(this);
        ImageView welcomeImage = findViewById(R.id.profileImage);
        ImageView accountImage = findViewById(R.id.accountProfileImage);
        for (ImageView target : new ImageView[]{welcomeImage, accountImage}) {
            if (target == null) continue;
            if (pictureUrl == null || pictureUrl.isEmpty()) {
                target.setImageResource(R.drawable.img_profile_placeholder);
            } else {
                Glide.with(this)
                        .load(pictureUrl)
                        .circleCrop()
                        .placeholder(R.drawable.img_profile_placeholder)
                        .error(R.drawable.img_profile_placeholder)
                        .into(target);
            }
        }
    }

    /**
     * POST_NOTIFICATIONS is only a runtime-requestable permission from Android 13
     * (API 33) onward - on every earlier supported version (this app's minSdk is
     * 29) the manifest declaration alone is enough and there's no prompt to show.
     * A denial here is never fatal: NotificationHelper independently checks
     * areNotificationsEnabled() and catches SecurityException before every post,
     * so the rest of the app (including the in-app Notification Module, which
     * doesn't depend on OS-level permission at all) keeps working either way.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        boolean alreadyGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void wireDashboardActions() {
        // Outbound navigation below fires startActivity() directly rather than through
        // BaseNavigationActivity#openScreen() (which already applies CLEAR_TOP|SINGLE_TOP),
        // so each listener is wrapped in NavUtils.debounce() to stop a fast double-tap from
        // pushing two stacked instances of the destination screen.
        findViewById(R.id.actionBrowseRooms).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, RoomBrowsingActivity.class))));
        // Both quick actions open the guest's existing list on the matching tab
        // (Bookings/Reservations), landing on the "All" filter by default - same
        // EXTRA_OPEN_SECTION-only mechanism PaymentActivity#navigateToBookingSection()/
        // navigateToReservationSection() already use, just without EXTRA_LIST_FILTER
        // (which is what leaves showAllCategories at its default true - see
        // BookingAndReservationActivity#onCreate()'s own comment on that). To start a
        // brand-new transaction instead, the guest already has Browse Rooms/Book Now/
        // Reserve Now elsewhere (StartingTransactionActivity).
        findViewById(R.id.actionBooking).setOnClickListener(NavUtils.debounce(v ->
                openBookingAndReservationSection(BookingAndReservationActivity.SECTION_BOOKING)));
        findViewById(R.id.actionReservation).setOnClickListener(NavUtils.debounce(v ->
                openBookingAndReservationSection(BookingAndReservationActivity.SECTION_RESERVATION)));
        findViewById(R.id.actionHistory).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, TransactionHistoryActivity.class))));

        findViewById(R.id.btnViewAllBookings).setOnClickListener(NavUtils.debounce(v -> openTransactionHistoryBookings()));
        findViewById(R.id.btnViewAllNotifications).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, NotificationActivity.class))));
        findViewById(R.id.btnViewAllPayments).setOnClickListener(NavUtils.debounce(v -> openTransactionHistoryPayments()));

        findViewById(R.id.tileActiveBooking).setOnClickListener(NavUtils.debounce(v -> openBookingSectionFiltered(
                BookingAndReservationActivity.SECTION_BOOKING, BookingAndReservationActivity.FILTER_ACTIVE)));
        findViewById(R.id.tileActiveReservation).setOnClickListener(NavUtils.debounce(v -> openBookingSectionFiltered(
                BookingAndReservationActivity.SECTION_RESERVATION, BookingAndReservationActivity.FILTER_ACTIVE)));
        findViewById(R.id.tilePastBooking).setOnClickListener(NavUtils.debounce(v -> openBookingSectionFiltered(
                BookingAndReservationActivity.SECTION_BOOKING, BookingAndReservationActivity.FILTER_COMPLETED)));
        findViewById(R.id.tilePastReservation).setOnClickListener(NavUtils.debounce(v -> openBookingSectionFiltered(
                BookingAndReservationActivity.SECTION_RESERVATION, BookingAndReservationActivity.FILTER_COMPLETED)));
        findViewById(R.id.tileCancelBooking).setOnClickListener(NavUtils.debounce(v -> openBookingSectionFiltered(
                BookingAndReservationActivity.SECTION_BOOKING, BookingAndReservationActivity.FILTER_CANCELLED)));
        findViewById(R.id.tileCancelReservation).setOnClickListener(NavUtils.debounce(v -> openBookingSectionFiltered(
                BookingAndReservationActivity.SECTION_RESERVATION, BookingAndReservationActivity.FILTER_CANCELLED)));
        findViewById(R.id.btnNextStayBook).setOnClickListener(NavUtils.debounce(v ->
                startActivity(new android.content.Intent(this, RoomBrowsingActivity.class))));
        findViewById(R.id.btnNextStayRetry).setOnClickListener(NavUtils.debounce(v -> retryDashboardLoad()));
        for (int stateId : new int[]{R.id.stateSummary, R.id.stateRecent, R.id.stateNotifications, R.id.statePayments}) {
            findViewById(stateId).findViewById(R.id.sectionStateRetry).setOnClickListener(NavUtils.debounce(v -> retryDashboardLoad()));
        }
        findViewById(R.id.btnEditProfile).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, ProfileManagementActivity.class))));
        findViewById(R.id.dateTimeCard).setOnClickListener(NavUtils.debounce(v -> startActivity(UpcomingTransactionsActivity.intentAllStatuses(this))));

        View headerNotification = findViewById(R.id.headerNotificationContainer);
        if (headerNotification != null) {
            headerNotification.setOnClickListener(NavUtils.debounce(this::onHeaderBellTapped));
        }

        findViewById(R.id.bookingDetailsCard).setOnClickListener(NavUtils.debounce(v -> openTransactionHistoryBookings()));
        findViewById(R.id.paymentStatusCard).setOnClickListener(NavUtils.debounce(v -> openTransactionHistoryPayments()));
        findViewById(R.id.notificationsCard).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, NotificationActivity.class))));
        findViewById(R.id.accountSummaryCard).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, ProfileManagementActivity.class))));

        View btnContactSupport = findViewById(R.id.btnContactSupport);
        if (btnContactSupport != null) {
            btnContactSupport.setOnClickListener(v -> contactSupport());
        }
    }

    /**
     * Opens the device's email app addressed to the hotel's real support
     * inbox - same contact details landing.xml's "Contact Us" section
     * already shows the guest, not a fake/placeholder address. Falls back
     * to surfacing the phone number if no email app is installed, rather
     * than silently doing nothing.
     */
    private void contactSupport() {
        String supportEmail = getString(R.string.landing_hotel_email);
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
        intent.setData(android.net.Uri.parse("mailto:" + supportEmail));
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.support_email_subject));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.error_no_email_app, getString(R.string.landing_hotel_phone)), Toast.LENGTH_LONG).show();
        }
    }


    private void openBookingSection(String section) {
        android.content.Intent intent = new android.content.Intent(this, BookingAndReservationActivity.class);
        intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, section);
        startActivity(intent);
    }

    /**
     * Opens the Booking/Reservation screen on the given section and narrows its
     * bottom list to a status subset - used by the Booking & Reservation Summary
     * tiles to deep-link straight into the matching Active/Past/Cancel list.
     */
    private void openBookingSectionFiltered(String section, String listFilter) {
        android.content.Intent intent = new android.content.Intent(this, BookingAndReservationActivity.class);
        intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, section);
        intent.putExtra(BookingAndReservationActivity.EXTRA_LIST_FILTER, listFilter);
        startActivity(intent);
    }

    /**
     * Dashboard Quick Actions (Booking/Reservation): opens the guest's existing
     * list on the matching tab, deliberately with no EXTRA_LIST_FILTER - see
     * openBookingSectionFiltered() above for the filtered variant used by the
     * Booking & Reservation Summary tiles. Omitting it is what leaves
     * showAllCategories at its default true, i.e. "All Bookings"/"All
     * Reservations", per BookingAndReservationActivity#onCreate().
     */
    private void openBookingAndReservationSection(String section) {
        android.content.Intent intent = new android.content.Intent(this, BookingAndReservationActivity.class);
        intent.putExtra(BookingAndReservationActivity.EXTRA_OPEN_SECTION, section);
        startActivity(intent);
    }

    /** Dims a Booking & Reservation Summary tile to de-emphasize empty categories. */
    private void dimSummaryTileIfEmpty(int tileViewId, int count) {
        View tile = findViewById(tileViewId);
        if (tile != null) {
            tile.setAlpha(count == 0 ? 0.5f : 1f);
        }
    }

    /** Shows how many items a Dashboard Overview section is currently displaying, hidden when empty. */
    private void setCountBadge(TextView badge, int count) {
        if (badge == null) return;
        badge.setText(String.valueOf(count));
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }



    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Prioritizes every booking/reservation with a check-in date of tomorrow (there can be
     * more than one) - if none, falls back to just the single nearest upcoming transaction
     * (which may itself be a check-out, if the guest is already checked in) so the section
     * is never empty while there's genuinely something upcoming.
     */
    /** Retry for any section that could not load: show "Loading…" everywhere again and ask the server once more. */
    private void retryDashboardLoad() {
        bookingsResolved = false;
        bookingsLoadFailed = false;
        notificationsResolved = false;
        notificationsLoadFailed = false;
        RoomRepository repository = RoomRepository.getInstance(this);
        updateNextStay(repository.getBookings());
        applySectionStates(repository.getBookings(), repository.getNotifications());
        loadDashboardData();
    }

    private static SectionState stateOf(boolean hasData, boolean resolved, boolean failed) {
        if (hasData) return SectionState.OK;
        if (!resolved) return SectionState.LOADING;
        return failed ? SectionState.ERROR : SectionState.OK;
    }

    /**
     * Every data section is one of: its list, a loading line, an error with Retry, or - only when it LOADED and is truly
     * empty - its "you have none" message. A load that failed must never read as "no bookings" / "no payments".
     */
    private void applySectionStates(List<Booking> bookings, List<Notification> notifications) {
        SectionState fromBookings = stateOf(!bookings.isEmpty(), bookingsResolved, bookingsLoadFailed);
        SectionState fromNotifications = stateOf(!notifications.isEmpty(), notificationsResolved, notificationsLoadFailed);

        // the summary's tiles live in a grid; their counts are meaningless until the bookings are known
        View summaryGrid = (View) findViewById(R.id.tileActiveBooking).getParent();
        bindSectionState(R.id.stateSummary, fromBookings, summaryGrid);
        if (fromBookings == SectionState.OK) summaryGrid.setVisibility(View.VISIBLE);
        bindSectionState(R.id.stateRecent, fromBookings, noBookingsText, noBookingsIcon, btnEmptyBookNow);
        bindSectionState(R.id.statePayments, fromBookings, noPaymentsText);
        bindSectionState(R.id.stateNotifications, fromNotifications, noNotificationsText);
    }

    private void bindSectionState(int includeId, SectionState state, View... hiddenUnlessOk) {
        View include = findViewById(includeId);
        include.setVisibility(state == SectionState.OK ? View.GONE : View.VISIBLE);
        if (state != SectionState.OK) {
            ((TextView) include.findViewById(R.id.sectionStateText))
                    .setText(state == SectionState.LOADING ? R.string.dashboard_section_loading : R.string.dashboard_section_error);
            include.findViewById(R.id.sectionStateRetry).setVisibility(state == SectionState.ERROR ? View.VISIBLE : View.GONE);
        }
        for (View v : hiddenUnlessOk) {
            if (v == null) continue;
            // (when the state is OK each section's own populate*() has just decided what its empty message does)
            if (state != SectionState.OK) v.setVisibility(View.GONE);
        }
    }

    /** Today's date at the hotel (Asia/Manila) - the day every "tomorrow" / "in N days" hint is counted from. */
    private static java.time.LocalDate hotelToday() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila"));
    }

    /** The calendar under Next Stay, created on first use (the card is always in the layout). */
    private DashboardStayCalendar stayCalendar;

    /**
     * Next Stay shows every transaction on the nearest day (a guest can hold several, even on the same day), so every
     * other upcoming stay (soonest first) and the latest finished ones are listed under it as compact rows, and the
     * calendar is refreshed from the same bookings. Tapping a row opens that transaction in Upcoming Hotel
     * Transactions.
     */
    private void updateMoreStaysAndCalendar(List<Booking> bookings) {
        if (stayCalendar == null) {
            stayCalendar = new DashboardStayCalendar(findViewById(R.id.stayCalendar), hotelToday());
        }
        stayCalendar.setBookings(bookings);

        otherStaysSource = bookings;
        renderMoreStays();
    }

    /** Every stay (upcoming first, then the finished ones); collapsing the section hides the list and leaves the summary. */
    private void renderMoreStays() {
        OtherStays others = OtherStays.build(otherStaysSource);
        moreStaysUpcoming = others.upcoming;
        moreStaysRecent = others.recent;
        LinearLayout list = findViewById(R.id.moreStaysList);
        View section = findViewById(R.id.moreStaysSection);
        list.removeAllViews();
        int total = moreStaysUpcoming.size() + moreStaysRecent.size();
        List<Booking> ordered = new ArrayList<>(moreStaysUpcoming);
        ordered.addAll(moreStaysRecent);
        for (Booking b : SectionPreview.visible(ordered, moreStaysSection.isExpanded())) {
            StayCardBinder.add(this, list, b, StayCardBinder.tagFor(b));
        }
        SectionPreview.addToggle(this, list, total, moreStaysSection.isExpanded(), moreStaysSection::toggle);
        TextView moreCount = findViewById(R.id.moreStaysCount);
        moreCount.setText(String.valueOf(total));
        moreCount.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        // Never hidden: with nothing to list it says so.
        section.setVisibility(View.VISIBLE);
        findViewById(R.id.moreStaysEmpty).setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        Booking first = !moreStaysUpcoming.isEmpty() ? moreStaysUpcoming.get(0) : (!moreStaysRecent.isEmpty() ? moreStaysRecent.get(0) : null);
        if (first == null) {
            moreStaysSection.setSummary(getString(R.string.more_stays_empty));
        } else {
            moreStaysSection.setSummary(getString(R.string.summary_with_first,
                    getResources().getQuantityString(R.plurals.summary_stays_count, total, total),
                    getString(R.string.summary_ref_status, TransactionText.reference(this, first.isHasBooking(), first.getDisplayId()),
                            TransactionStatusChip.label(first.getStatus()))));
        }
    }

    /** Redraws "Pending & Confirmed Stays" from these bookings (Pending / Confirmed only). */
    private void updateRecentUpcoming(List<Booking> bookings) {
        recentUpcoming = RecentUpcomingStays.select(bookings);
        renderRecentUpcoming();
    }

    /** Collapsed: the first three. Expanded: all. Empty: a message. The Show all / Show less button shows only past three. */
    private void renderRecentUpcoming() {
        LinearLayout list = findViewById(R.id.recentUpcomingList);
        TextView count = findViewById(R.id.recentUpcomingCount);
        View empty = findViewById(R.id.recentUpcomingEmpty);
        list.removeAllViews();
        int total = recentUpcoming.size();
        for (Booking b : SectionPreview.visible(recentUpcoming, recentUpcomingSection.isExpanded())) addRecentUpcomingRow(b, list);
        SectionPreview.addToggle(this, list, total, recentUpcomingSection.isExpanded(), recentUpcomingSection::toggle);
        empty.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        count.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        count.setText(String.valueOf(total));
    }

    /** Type, reference, room, dates and status; a tap opens Upcoming Hotel Transactions filtered to this type and status, on this transaction. */
    private void addRecentUpcomingRow(Booking b, LinearLayout container) {
        StayCardBinder.add(this, container, b, StayCardBinder.Tag.UPCOMING);
    }

    private void showNextStayState(View visible) {
        for (View state : new View[]{nextStayLoading, nextStayError, nextStayEmpty, nextStayContent}) {
            state.setVisibility(state == visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * The Next Stay card: loading until the first answer, an error with Retry when nothing could be loaded, an empty state
     * with one action, or the stays themselves - ALL the transactions on the nearest day, whether one or several and
     * whether they are bookings or reservations.
     */
    private void updateNextStay(List<Booking> bookings) {
        if (!bookingsResolved && bookings.isEmpty()) {
            showNextStayState(nextStayLoading);
            nextStaySection.setSummary(getString(R.string.dashboard_section_loading));
            return;
        }
        if (bookingsLoadFailed && bookings.isEmpty()) {
            showNextStayState(nextStayError);
            nextStaySection.setSummary(getString(R.string.dashboard_section_error));
            return;
        }
        if (!hasAnyStay(bookings)) {
            showNextStayState(nextStayEmpty);
            nextStaySection.setSummary(getString(R.string.next_stay_empty_title));
            updateMoreStaysAndCalendar(bookings);
            return;
        }
        // Other Stays leaves out exactly what the selected day shows (renderNextStayDays refreshes it on every change).
        updateMoreStaysAndCalendar(bookings);
        nextStayBookings = bookings;
        renderNextStayDays();
        showNextStayState(nextStayContent);
    }

    /** The one context row: "Your next stay · in 2 days" (Daily) or "This week · 3 stays" / "October 2026 · 5 stays", and the small jump button when the current day / week / month is not selected. */
    private void bindNextStayInfo(LinearLayout content, java.time.LocalDate today, List<NextStay> dayStays) {
        StayCalendarModel.Mode mode = nextStayMode();
        TextView info = content.findViewById(R.id.nextStayDayInfo);
        com.google.android.material.button.MaterialButton todayButton = content.findViewById(R.id.btnNextStayToday);
        if (mode == StayCalendarModel.Mode.DAILY) {
            boolean nearest = selectedStayDay.equals(NextStay.nearestStayDay(nextStayBookings, today));
            boolean staying = false;
            for (NextStay stay : dayStays) staying |= stay.phase == NextStay.Phase.IN_HOUSE;
            info.setText(NextStayDayText.line(this, selectedStayDay, today, nearest, staying));
            todayButton.setVisibility(selectedStayDay.equals(today) ? View.GONE : View.VISIBLE);
        } else {
            info.setText(NextStayPeriods.contextLine(this, mode, selectedStayDay, today, dayStays.size()));
            todayButton.setVisibility(NextStayPeriods.isCurrent(mode, selectedStayDay, today) ? View.GONE : View.VISIBLE);
        }
        todayButton.setText(NextStayPeriods.jumpLabel(this, mode));
        todayButton.setOnClickListener(v -> {
            selectedStayDay = hotelToday();
            scrollStripToSelected = true;
            renderNextStayDays();
        });
    }

    /** Scrolls the strip so this chip is centred (and so fully visible); waits for the first layout if it has not happened yet. */
    private static void scrollChipIntoView(android.widget.HorizontalScrollView scroll, View chip) {
        Runnable scrollNow = () -> {
            int x = chip.getLeft() + chip.getWidth() / 2 - scroll.getWidth() / 2;
            scroll.scrollTo(Math.max(0, x), 0);
        };
        if (androidx.core.view.ViewCompat.isLaidOut(scroll) && chip.getWidth() > 0) {
            scrollNow.run();
            return;
        }
        Object previous = scroll.getTag(R.id.sectionAnimator);
        if (previous instanceof View.OnLayoutChangeListener) scroll.removeOnLayoutChangeListener((View.OnLayoutChangeListener) previous);
        View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (chip.getWidth() == 0 || scroll.getWidth() == 0) return;
                scroll.removeOnLayoutChangeListener(this);
                scroll.setTag(R.id.sectionAnimator, null);
                scrollNow.run();
            }
        };
        scroll.setTag(R.id.sectionAnimator, listener);
        scroll.addOnLayoutChangeListener(listener);
    }

    /** Opening the section re-centres the selected chip (it could not be measured while hidden); the chosen day is kept. */
    private void onNextStayToggled() {
        if (!nextStaySection.isExpanded()) return;
        if (nextStayContent.getVisibility() == View.VISIBLE && selectedStayDay != null) {
            scrollStripToSelected = true;
            renderNextStayDays();
        }
    }

    /** True when at least one non-cancelled transaction has a readable check-in date (so the day selector has something to show). */
    private static boolean hasAnyStay(List<Booking> bookings) {
        for (Booking b : bookings) {
            if (b != null && !StayCalendarModel.isClosed(b) && PaymentDates.parseDate(b.getCheckInDate()) != null) return true;
        }
        return false;
    }

    private static final java.time.format.DateTimeFormatter DAY_DATE = java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final java.time.format.DateTimeFormatter DAY_WEEKDAY = java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final java.time.format.DateTimeFormatter DAY_FULL = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    /** "Yesterday" / "Today" / "Tomorrow" for the nearby days, otherwise "Wed, Oct 21". */
    private String dayName(java.time.LocalDate day, java.time.LocalDate today) {
        long diff = java.time.temporal.ChronoUnit.DAYS.between(today, day);
        if (diff == -1) return getString(R.string.next_stay_day_yesterday);
        if (diff == 0) return getString(R.string.next_stay_day_today);
        if (diff == 1) return getString(R.string.next_stay_day_tomorrow);
        return DAY_FULL.format(day);
    }

    private StayCalendarModel.Mode nextStayMode() {
        if (nextStayMode == null) {
            String saved = getSharedPreferences("VelocityPrefs", MODE_PRIVATE).getString(KEY_NEXT_STAY_MODE, "DAILY");
            try {
                nextStayMode = StayCalendarModel.Mode.valueOf(saved);
            } catch (IllegalArgumentException e) {
                nextStayMode = StayCalendarModel.Mode.DAILY;
            }
        }
        return nextStayMode;
    }

    /** Wires the Daily / Weekly / Monthly control once per inflated body: the choice is saved and redraws the strip and the cards. */
    private void bindNextStayModeGroup(LinearLayout content) {
        com.google.android.material.button.MaterialButtonToggleGroup group = content.findViewById(R.id.nextStayMode);
        StayCalendarModel.Mode mode = nextStayMode();
        int wanted = mode == StayCalendarModel.Mode.WEEKLY ? R.id.nextStayModeWeekly
                : mode == StayCalendarModel.Mode.MONTHLY ? R.id.nextStayModeMonthly : R.id.nextStayModeDaily;
        if (group.getTag(R.id.sectionAnimator) == null) {
            group.setTag(R.id.sectionAnimator, Boolean.TRUE);
            group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked || bindingNextStayMode) return;
                StayCalendarModel.Mode chosen = checkedId == R.id.nextStayModeWeekly ? StayCalendarModel.Mode.WEEKLY
                        : checkedId == R.id.nextStayModeMonthly ? StayCalendarModel.Mode.MONTHLY : StayCalendarModel.Mode.DAILY;
                if (chosen == nextStayMode) return;
                nextStayMode = chosen;
                getSharedPreferences("VelocityPrefs", MODE_PRIVATE).edit().putString(KEY_NEXT_STAY_MODE, chosen.name()).apply();
                scrollStripToSelected = true;
                renderNextStayDays();
            });
        }
        if (group.getCheckedButtonId() != wanted) {
            bindingNextStayMode = true;
            group.check(wanted);
            bindingNextStayMode = false;
        }
    }

    /**
     * The selector (days, weeks or months) and the cards of the chosen one. Days are plain calendar dates in hotel time,
     * so a stay lands on the right day whatever the phone's zone. Weekly and Monthly list every transaction that overlaps
     * the window - each once, nearest check-in first - using the Stay Calendar's own rules, so the two always agree.
     * The strip keeps its scroll position across refreshes; the first time, the selected chip is scrolled into view.
     */
    private void renderNextStayDays() {
        LinearLayout content = (LinearLayout) nextStayContent;
        if (content.findViewById(R.id.nextStayDayStrip) == null) {
            content.removeAllViews();
            LayoutInflater.from(this).inflate(R.layout.view_next_stay_days, content, true);
            nextStayStripBuilt = false;
        }
        bindNextStayModeGroup(content);
        StayCalendarModel.Mode mode = nextStayMode();
        java.time.LocalDate today = hotelToday();
        if (selectedStayDay == null) {
            // On open: Today if it has a transaction, else the nearest later day that has one, else Today.
            selectedStayDay = NextStay.defaultDay(nextStayBookings, today);
            scrollStripToSelected = true;
        }

        android.widget.HorizontalScrollView scroll = content.findViewById(R.id.nextStayDayScroll);
        LinearLayout strip = content.findViewById(R.id.nextStayDayStrip);
        int keepScroll = scroll.getScrollX();
        boolean firstBuild = !nextStayStripBuilt || scrollStripToSelected;
        scrollStripToSelected = false;
        strip.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        View selectedChip;
        List<NextStay> dayStays = new ArrayList<>();
        if (mode == StayCalendarModel.Mode.DAILY) {
            selectedChip = buildDayChips(strip, inflater, today);
            dayStays = NextStay.forDay(nextStayBookings, selectedStayDay, today);
        } else {
            StayCalendarModel model = new StayCalendarModel(nextStayBookings);
            selectedChip = buildPeriodChips(strip, inflater, today, mode, model);
            for (Booking b : model.transactionsFor(mode, selectedStayDay)) {
                NextStay stay = NextStay.of(b, today);
                if (stay != null) dayStays.add(stay);
            }
        }
        if (firstBuild && selectedChip != null) {
            nextStayStripBuilt = true;
            scrollChipIntoView(scroll, selectedChip);
        } else {
            scroll.post(() -> scroll.scrollTo(keepScroll, 0));
        }

        LinearLayout cards = content.findViewById(R.id.nextStayDayCards);
        TextView empty = content.findViewById(R.id.nextStayDayEmpty);
        cards.removeAllViews();
        bindNextStayInfo(content, today, dayStays);
        empty.setText(mode == StayCalendarModel.Mode.DAILY ? R.string.next_stay_day_empty : R.string.next_stay_period_empty);
        empty.setVisibility(dayStays.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < dayStays.size(); i++) {
            View item = inflater.inflate(R.layout.item_next_stay, cards, false);
            NextStay stay = dayStays.get(i);
            bindNextStayItem(item, stay, selectedStayDay);
            cards.addView(item);
        }
        TextView stayCount = findViewById(R.id.nextStayCount);
        stayCount.setText(String.valueOf(dayStays.size()));
        stayCount.setVisibility(dayStays.isEmpty() ? View.GONE : View.VISIBLE);
        nextStaySection.setSummary(mode == StayCalendarModel.Mode.DAILY
                ? NextStayDayText.line(this, selectedStayDay, today, false, false)
                : NextStayPeriods.contextLine(this, mode, selectedStayDay, today, dayStays.size()));
    }

    /** Two weeks back through a month ahead, widened to cover every stay; returns the selected chip. */
    private View buildDayChips(LinearLayout strip, LayoutInflater inflater, java.time.LocalDate today) {
        java.time.LocalDate first = today.minusDays(14);
        java.time.LocalDate last = today.plusDays(30);
        for (Booking b : nextStayBookings) {
            if (b == null || StayCalendarModel.isClosed(b)) continue;
            java.time.LocalDate in = PaymentDates.parseDate(b.getCheckInDate());
            java.time.LocalDate out = PaymentDates.parseDate(b.getCheckOutDate());
            if (in != null && in.isBefore(first) && in.isAfter(today.minusDays(60))) first = in;
            if (out != null && out.isAfter(last) && out.isBefore(today.plusDays(120))) last = out;
        }
        if (selectedStayDay.isBefore(first)) selectedStayDay = first;
        if (selectedStayDay.isAfter(last)) selectedStayDay = last;
        View selectedChip = null;
        for (java.time.LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            final java.time.LocalDate day = d;
            int count = NextStay.forDay(nextStayBookings, day, today).size();
            View chip = inflater.inflate(R.layout.item_next_stay_day, strip, false);
            long diff = java.time.temporal.ChronoUnit.DAYS.between(today, day);
            boolean friendly = diff >= -1 && diff <= 1;
            boolean selected = day.equals(selectedStayDay);
            styleChip(chip, friendly ? dayName(day, today) : DAY_WEEKDAY.format(day), DAY_DATE.format(day), count, selected,
                    diff == 0, getResources().getQuantityString(R.plurals.next_stay_day_cd, count, dayName(day, today), count));
            chip.setOnClickListener(v -> {
                selectedStayDay = day;
                renderNextStayDays();
            });
            if (selected) selectedChip = chip;
            strip.addView(chip);
        }
        return selectedChip;
    }

    /** Week or month chips, each with its count badge; returns the selected one. */
    private View buildPeriodChips(LinearLayout strip, LayoutInflater inflater, java.time.LocalDate today,
                                  StayCalendarModel.Mode mode, StayCalendarModel model) {
        java.time.LocalDate selectedStart = StayCalendarModel.windowStart(mode, selectedStayDay);
        View selectedChip = null;
        for (java.time.LocalDate start : NextStayPeriods.starts(mode, nextStayBookings, today)) {
            int count = model.transactionsFor(mode, start).size();
            View chip = inflater.inflate(R.layout.item_next_stay_day, strip, false);
            boolean selected = start.equals(selectedStart);
            boolean current = NextStayPeriods.isCurrent(mode, start, today);
            String title = NextStayPeriods.title(this, mode, start, today);
            styleChip(chip, title, NextStayPeriods.subtitle(this, mode, start, today), count, selected, current,
                    getResources().getQuantityString(R.plurals.next_stay_day_cd, count, title, count));
            chip.setOnClickListener(v -> {
                selectedStayDay = current ? today : start;
                renderNextStayDays();
            });
            if (selected) selectedChip = chip;
            strip.addView(chip);
        }
        return selectedChip;
    }

    /** One selector chip: selected is white on the red fill, the current one is outlined, a quiet one is lighter; the badge sits in the same place on every chip. */
    private void styleChip(View chip, String title, String subtitle, int count, boolean selected, boolean current, String description) {
        TextView name = chip.findViewById(R.id.tvDayName);
        TextView date = chip.findViewById(R.id.tvDayDate);
        TextView badge = chip.findViewById(R.id.tvDayCount);
        name.setText(title);
        date.setText(subtitle);
        chip.setSelected(selected);
        chip.setActivated(current && !selected);
        int textColor = selected ? getColor(R.color.white)
                : getColor(count > 0 ? R.color.velocity_text_primary : R.color.velocity_text_secondary);
        name.setTextColor(textColor);
        date.setTextColor(textColor);
        name.setAlpha(selected || count > 0 ? 1f : 0.7f);
        date.setAlpha(selected || count > 0 ? 1f : 0.7f);
        badge.setText(String.valueOf(count));
        badge.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);
        badge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getColor(selected ? R.color.white : R.color.velocity_red_primary)));
        badge.setTextColor(getColor(selected ? R.color.velocity_red_primary : R.color.white));
        chip.setContentDescription(description);
    }

    /** Fills one stay's block and wires its taps: the block opens its Booking Details, the button asks for amenities. */
    private void bindNextStayItem(View item, NextStay stay, java.time.LocalDate day) {
        // The day role (Check-in day / Staying / Check-out day) describes one day, so only the Daily view shows it.
        TextView role = item.findViewById(R.id.tvNextStayHint);
        role.setText(stay.dayRole(this, day));
        role.setVisibility(nextStayMode() == StayCalendarModel.Mode.DAILY ? View.VISIBLE : View.GONE);
        TextView ref = item.findViewById(R.id.tvNextStayRef);
        ref.setText(TransactionText.reference(this, stay.booking.isHasBooking(), stay.booking.getDisplayId()));
        View.OnClickListener openInUpcoming = NavUtils.debounce(v ->
                startActivity(UpcomingTransactionsActivity.intentWithFilters(this, stay.booking)));
        ref.setOnClickListener(openInUpcoming);
        ((TextView) item.findViewById(R.id.tvNextStayRooms)).setText(StayCardText.roomsAndGuests(this, stay.booking));
        ((TextView) item.findViewById(R.id.tvNextStayType)).setText(
                stay.booking.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
        ((TextView) item.findViewById(R.id.tvNextStayDates)).setText(StayCardText.datesAndNights(this, stay.booking));
        TextView statusChip = item.findViewById(R.id.tvNextStayStatus);
        if (stay.isFinished()) {
            TransactionStatusChip.apply(this, statusChip, stay.booking.getStatus());
            statusChip.setCompoundDrawables(null, null, null, null);
        } else {
            styleNextStayStatus(statusChip, stay.status);
        }

        // The payment row: "Balance due" and the amount in bold red - or "Paid" with a green check.
        TextView balanceLabel = item.findViewById(R.id.tvNextStayBalanceLabel);
        TextView balance = item.findViewById(R.id.tvNextStayBalance);
        if (stay.hasBalanceDue()) {
            balanceLabel.setText(R.string.next_stay_balance_due);
            balance.setText(MoneyFormat.format(stay.balance));
            balance.setTextColor(getColor(R.color.velocity_red_primary));
            balance.setCompoundDrawables(null, null, null, null);
        } else {
            balanceLabel.setText(R.string.next_stay_payment_label);
            balance.setText(R.string.next_stay_paid);
            balance.setTextColor(getColor(R.color.status_paid_fg));
            android.graphics.drawable.Drawable check = ContextCompat.getDrawable(this, R.drawable.ic_check_circle);
            if (check != null) {
                check = check.mutate();
                int size = dp(16);
                check.setBounds(0, 0, size, size);
                check.setTint(getColor(R.color.status_paid_fg));
                balance.setCompoundDrawables(check, null, null, null);
            }
        }
        item.findViewById(R.id.nextStayAccent).setBackgroundColor(getColor(TransactionStatusChip.accentColor(stay.booking.getStatus())));
        bindNextStayMoney(item, stay);

        View amenities = item.findViewById(R.id.btnNextStayRequestAmenities);
        amenities.setVisibility(stay.status == NextStay.PlainStatus.STAYING ? View.VISIBLE : View.GONE);
        amenities.setOnClickListener(NavUtils.debounce(v -> startActivity(AdditionalAmenitiesEntry.intent(this, stay.booking))));
        item.setOnClickListener(openInUpcoming);
        TextView open = item.findViewById(R.id.tvNextStayViewDetails);
        open.setText(stay.booking.isHasBooking() ? R.string.next_stay_open_booking : R.string.next_stay_open_reservation);
        open.setOnClickListener(NavUtils.debounce(v ->
                startActivity(new android.content.Intent(this, BookingDetailsActivity.class)
                        .putExtra(BookingDetailsActivity.EXTRA_BOOKING, stay.booking))));
    }

    /**
     * The full details of a stay - room charges, approved amenities (grouped by item), total, paid and ONE balance line -
     * hidden until the guest taps "Show full details"; "Hide full details" folds them again. Which cards are open is kept
     * for as long as the app lives (see {@link NextStayDetails}), so it survives scrolling, rotation and another screen.
     */
    private void bindNextStayMoney(View stayView, NextStay stay) {
        LinearLayout box = stayView.findViewById(R.id.nextStayMoney);
        com.google.android.material.button.MaterialButton toggle = stayView.findViewById(R.id.btnNextStayToggleDetails);
        NextStayDetails.render(this, box, NextStayDetails.rows(this, stay.booking));
        boolean open = NextStayDetails.isOpen(stay.booking);
        box.setVisibility(open ? View.VISIBLE : View.GONE);
        applyDetailsToggle(toggle, open);
        toggle.setOnClickListener(NavUtils.debounce(v -> {
            boolean show = !NextStayDetails.isOpen(stay.booking);
            NextStayDetails.setOpen(stay.booking, show);
            SectionAnimator.set(box, show, true);
            applyDetailsToggle(toggle, show);
        }));
    }

    private void applyDetailsToggle(com.google.android.material.button.MaterialButton toggle, boolean open) {
        toggle.setText(open ? R.string.next_stay_hide_details : R.string.next_stay_show_details);
        toggle.setIconResource(open ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** Status chip: colour AND icon AND words, so it never relies on colour alone. */
    private void styleNextStayStatus(TextView chip, NextStay.PlainStatus status) {
        int bg, fg, icon;
        switch (status.tone) {
            case GOOD:
                bg = R.color.status_paid_bg; fg = R.color.status_paid_fg; icon = R.drawable.ic_check_circle;
                break;
            case INFO:
                bg = R.color.velocity_blue_soft; fg = R.color.velocity_blue_primary; icon = R.drawable.ic_info;
                break;
            default:
                bg = R.color.status_pending_bg; fg = R.color.status_pending_fg; icon = R.drawable.ic_clock;
                break;
        }
        chip.setText(status.labelRes);
        chip.setBackgroundTintList(ContextCompat.getColorStateList(this, bg));
        chip.setTextColor(ContextCompat.getColor(this, fg));
        android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(this, icon);
        if (drawable != null) {
            int size = (int) (12 * getResources().getDisplayMetrics().density);
            drawable = drawable.mutate();
            drawable.setBounds(0, 0, size, size);
            drawable.setTint(ContextCompat.getColor(this, fg));
        }
        chip.setCompoundDrawables(drawable, null, null, null);
        chip.setCompoundDrawablePadding((int) (4 * getResources().getDisplayMetrics().density));
    }




    /** Payment Status section (card + "See all") always opens the Payments section of Transaction History. */
    private void openTransactionHistoryPayments() {
        android.content.Intent intent = new android.content.Intent(this, TransactionHistoryActivity.class);
        intent.putExtra(TransactionHistoryActivity.EXTRA_OPEN_FILTER, TransactionHistoryActivity.FILTER_PAYMENTS);
        startActivity(intent);
    }

    /**
     * Recent Bookings section (card + "See all") - same pattern as
     * openTransactionHistoryPayments(): opens Transaction History with the
     * Bookings chip active, showing every booking, not just this card's
     * capped preview.
     */
    private void openTransactionHistoryBookings() {
        android.content.Intent intent = new android.content.Intent(this, TransactionHistoryActivity.class);
        intent.putExtra(TransactionHistoryActivity.EXTRA_OPEN_FILTER, TransactionHistoryActivity.FILTER_BOOKINGS);
        startActivity(intent);
    }

    /**
     * Payment Status card tap ("View Details") - routes to the type-correct chip
     * (Bookings vs Reservations, never the generic Payments chip) so a Booking
     * transaction always lands view-only among other Bookings, and a still-
     * payable Reservation lands where its own Pay Now button is expected, with
     * this exact transaction scrolled to, highlighted, and auto-opened.
     */
    private void openTransactionHistoryForPaymentItem(Booking booking) {
        android.content.Intent intent = new android.content.Intent(this, TransactionHistoryActivity.class);
        intent.putExtra(TransactionHistoryActivity.EXTRA_OPEN_FILTER, booking.isHasBooking()
                ? TransactionHistoryActivity.FILTER_BOOKINGS
                : TransactionHistoryActivity.FILTER_RESERVATIONS);
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, booking.getId());
        // The exact family (a reservation and a direct booking can share an id).
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_DIRECT, booking.isDirectBooking());
        startActivity(intent);
    }

    /** Recent Bookings section - opens Transaction History on the Bookings chip, scrolled to and highlighting this exact Booking ID. */
    private void openTransactionHistoryForRecentBooking(Booking booking) {
        android.content.Intent intent = new android.content.Intent(this, TransactionHistoryActivity.class);
        intent.putExtra(TransactionHistoryActivity.EXTRA_OPEN_FILTER, TransactionHistoryActivity.FILTER_BOOKINGS);
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, booking.getId());
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_DIRECT, booking.isDirectBooking());
        startActivity(intent);
    }

    private void startAnimations() {
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in_slide_up);
        startAnimationIfPresent(R.id.header, fadeIn);
        startAnimationIfPresent(R.id.welcomeText, fadeIn);
        startAnimationIfPresent(R.id.userNameText, fadeIn);
        startAnimationIfPresent(R.id.dateTimeCard, fadeIn);
        startAnimationIfPresent(R.id.nextStayCard, fadeIn);
    }

    private void startAnimationIfPresent(int viewId, Animation animation) {
        View view = findViewById(viewId);
        if (view != null) {
            view.startAnimation(animation);
        }
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
    }

    private SimpleDateFormat clockDateFormat;
    private SimpleDateFormat clockTimeFormat;

    /** The date ("Sunday, October 11, 2026") with the time ("9:19 AM") below it, in hotel time (Asia/Manila), whatever the phone's own zone is. */
    private void updateDateTime() {
        if (clockDateFormat == null) {
            // built once, not twice a second
            java.util.TimeZone manila = java.util.TimeZone.getTimeZone("Asia/Manila");
            clockDateFormat = new SimpleDateFormat(getString(R.string.dashboard_date_format), Locale.US);
            clockTimeFormat = new SimpleDateFormat(getString(R.string.dashboard_time_format), Locale.US);
            clockDateFormat.setTimeZone(manila);
            clockTimeFormat.setTimeZone(manila);
        }
        java.util.Date now = new java.util.Date();
        if (dateText != null) dateText.setText(clockDateFormat.format(now));
        if (timeText != null) timeText.setText(clockTimeFormat.format(now));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (profileDonutShown) bindAccountSummary(); // back from Edit Profile: the donut is current at once
        loadUserInfo();
        loadDashboardData();
        notifTimeRefreshHandler.postDelayed(notifTimeRefreshRunnable, NOTIF_TIME_REFRESH_MS);
        // The clock only ticks while the screen is in front (it used to keep waking the phone from the background).
        if (clockRunnable != null) {
            clockHandler.removeCallbacks(clockRunnable);
            updateDateTime();
            clockHandler.postDelayed(clockRunnable, 1000);
        }
    }

    /**
     * A booking / payment / check-in notification just arrived (the receptionist verified, approved an amenity, checked
     * the guest in, added a charge, took a payment): re-read the bookings now, so "Your Next Stay" shows the new
     * status and balance at once instead of waiting for the next beat. Redraws only if something really changed.
     */
    private final RoomRepository.NotificationArrivalListener arrivalListener = arrived -> {
        if (isFinishing() || isDestroyed()) return;
        for (Notification n : arrived) {
            if (NotificationPrimaryActionResolver.isTransactionCategory(n.getType())) {
                RoomRepository repository = RoomRepository.getInstance(this);
                repository.pollBookingsSplit((merged, reservationsError, directError) -> {
                    if (isFinishing() || isDestroyed()) return;
                    List<Booking> bookings = repository.getBookings();
                    List<Notification> notifications = repository.getNotifications();
                    if (dashboardGate.accept(bookings, notifications, ChangeGate.today())) populateDashboard(bookings, notifications);
                });
                return;
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        RoomRepository.getInstance(this).addNotificationArrivalListener(arrivalListener);
    }

    @Override
    protected void onStop() {
        RoomRepository.getInstance(this).removeNotificationArrivalListener(arrivalListener);
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        notifTimeRefreshHandler.removeCallbacks(notifTimeRefreshRunnable);
        if (clockRunnable != null) clockHandler.removeCallbacks(clockRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clockRunnable != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }
    }
}




