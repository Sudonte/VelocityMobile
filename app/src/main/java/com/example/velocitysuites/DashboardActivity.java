package com.example.velocitysuites;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
    private TextView nextHotelTransactionText;
    private LinearLayout nextTransactionListContainer;
    private TextView activeReservationsCountText, cancelReservationsCountText, activeBookingsCountText, cancelBookingsCountText;
    private TextView pastBookingsCountText, pastReservationsCountText;
    private LinearLayout bookingListContainer, paymentListContainer, upcomingListContainer, notificationListContainer;
    private TextView noBookingsText, noUpcomingText, noNotificationsText, noPaymentsText;
    private View noBookingsIcon, btnEmptyBookNow;
    private TextView bookingCountBadge, upcomingCountBadge, notificationCountBadge, paymentCountBadge;

    /**
     * Dashboard preview cards (Recent Bookings, Upcoming Transactions, Notifications) use a
     * select-then-view-details flow: tapping a card highlights it and reveals its "View Details"
     * button rather than navigating immediately. Each section tracks its own single selection so
     * picking a different card in the same section collapses the previous one.
     */
    private static class SelectableCard {
        final com.google.android.material.card.MaterialCardView card;
        final View viewDetailsButton;
        SelectableCard(com.google.android.material.card.MaterialCardView card, View viewDetailsButton) {
            this.card = card;
            this.viewDetailsButton = viewDetailsButton;
        }
    }
    private SelectableCard selectedUpcomingCard;
    private SelectableCard selectedNotificationCard;
    private TextView accountFullNameText, accountEmailText, accountMobileText, membershipStatusText, profileCompletionText, accountStatusText;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshDashboard;
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
    }

    private void initializeNewComponents() {
        nextHotelTransactionText = findViewById(R.id.nextHotelTransactionText);
        nextTransactionListContainer = findViewById(R.id.nextTransactionListContainer);

        activeReservationsCountText = findViewById(R.id.activeReservationsCountText);
        cancelReservationsCountText = findViewById(R.id.cancelReservationsCountText);
        activeBookingsCountText = findViewById(R.id.activeBookingsCountText);
        cancelBookingsCountText = findViewById(R.id.cancelBookingsCountText);
        pastBookingsCountText = findViewById(R.id.pastBookingsCountText);
        pastReservationsCountText = findViewById(R.id.pastReservationsCountText);

        bookingListContainer = findViewById(R.id.bookingListContainer);
        paymentListContainer = findViewById(R.id.paymentListContainer);
        upcomingListContainer = findViewById(R.id.upcomingListContainer);
        notificationListContainer = findViewById(R.id.notificationListContainer);

        noBookingsText = findViewById(R.id.noBookingsText);
        noBookingsIcon = findViewById(R.id.noBookingsIcon);
        btnEmptyBookNow = findViewById(R.id.btnEmptyBookNow);
        if (btnEmptyBookNow != null) {
            btnEmptyBookNow.setOnClickListener(v -> openBookingSection(BookingAndReservationActivity.SECTION_BOOKING));
        }
        noUpcomingText = findViewById(R.id.noUpcomingText);
        noNotificationsText = findViewById(R.id.noNotificationsText);
        noPaymentsText = findViewById(R.id.noPaymentsText);

        bookingCountBadge = findViewById(R.id.bookingCountBadge);
        upcomingCountBadge = findViewById(R.id.upcomingCountBadge);
        notificationCountBadge = findViewById(R.id.notificationCountBadge);
        paymentCountBadge = findViewById(R.id.paymentCountBadge);

        accountFullNameText = findViewById(R.id.accountFullNameText);
        accountEmailText = findViewById(R.id.accountEmailText);
        accountMobileText = findViewById(R.id.accountMobileText);
        membershipStatusText = findViewById(R.id.membershipStatusText);
        profileCompletionText = findViewById(R.id.profileCompletionText);
        accountStatusText = findViewById(R.id.accountStatusText);
    }

    private void loadDashboardData() {
        RoomRepository repository = RoomRepository.getInstance(this);
        repository.refreshBookings(new RoomRepository.RepositoryCallback<List<Booking>>() {
            @Override
            public void onSuccess(List<Booking> result) {
                populateDashboard(repository.getBookings(), repository.getNotifications());
            }

            @Override
            public void onError(String message) {
                populateDashboard(repository.getBookings(), repository.getNotifications());
            }
        });
        repository.refreshNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                updateNotificationBadge();
                populateDashboard(repository.getBookings(), repository.getNotifications());
            }

            @Override
            public void onError(String message) {
                // Bookings refresh above already re-populates; nothing further needed on notification failure.
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

    private void populateDashboard(List<Booking> allBookings, List<Notification> allNotifications) {
        if (swipeRefreshDashboard != null) swipeRefreshDashboard.setRefreshing(false);
        RoomRepository repository = RoomRepository.getInstance(this);

        updateDashboardHub(allBookings, allNotifications);

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
        populateBookings(allBookings);
        populatePayments(allBookings);
        populateUpcoming(allBookings);
        populateNotifications(allNotifications);

        // Account Summary
        SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        String fullName = prefs.getString("userName", getString(R.string.guest_user));
        String email = prefs.getString("userEmail", getString(R.string.guest_email));
        String mobile = prefs.getString("userMobile", getString(R.string.mock_mobile));
        accountFullNameText.setText(fullName);
        accountEmailText.setText(email);
        accountMobileText.setText(mobile);

        // Widened from the previous 3-field check (name/email/mobile) to every
        // guest field already cached in the session from login/registration -
        // gender, date of birth, and a real (non-placeholder) profile photo -
        // without an extra network call just to compute a percentage.
        String gender = prefs.getString("userGender", "");
        String dob = prefs.getString("userDob", "");
        String pictureUrl = SessionManager.getProfilePictureUrl(this);
        int totalFields = 6;
        int completedFields = 0;
        if (!fullName.trim().isEmpty() && !fullName.equals(getString(R.string.guest_user))) completedFields++;
        if (!email.trim().isEmpty() && !email.equals(getString(R.string.guest_email))) completedFields++;
        if (!mobile.trim().isEmpty() && !mobile.equals(getString(R.string.mock_mobile))) completedFields++;
        if (gender != null && !gender.trim().isEmpty()) completedFields++;
        if (dob != null && !dob.trim().isEmpty()) completedFields++;
        if (pictureUrl != null && !pictureUrl.trim().isEmpty()) completedFields++;
        int profilePercent = (completedFields * 100) / totalFields;
        membershipStatusText.setText(getString(R.string.membership_label,
                profilePercent == 100 ? getString(R.string.premium_guest) : getString(R.string.profile_incomplete)));
        profileCompletionText.setText(getString(R.string.profile_completion_format, profilePercent));
        profileCompletionText.setTextColor(ContextCompat.getColor(this,
                profilePercent == 100 ? R.color.velocity_green_primary : R.color.velocity_red_primary));

        boolean sessionActive = SessionManager.isLoggedIn(this);
        if (accountStatusText != null) {
            accountStatusText.setText(sessionActive ? R.string.account_status_active : R.string.account_status_signed_out);
        }
        View accountStatusDot = findViewById(R.id.accountStatusDot);
        int statusColor = sessionActive ? R.color.velocity_green_primary : R.color.velocity_red_primary;
        if (accountStatusDot != null) {
            accountStatusDot.setBackgroundTintList(ContextCompat.getColorStateList(this, statusColor));
        }
    }

    /** How many of the most recent paid bookings the Recent Bookings section shows. */
    private static final int RECENT_BOOKINGS_LIMIT = 5;

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
        if (displayBookings.size() > RECENT_BOOKINGS_LIMIT) {
            displayBookings = new java.util.ArrayList<>(displayBookings.subList(0, RECENT_BOOKINGS_LIMIT));
        }

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
        }
        setCountBadge(bookingCountBadge, displayBookings.size());
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
        addTransactionItemCard(b, true, bookingListContainer, false, () -> openTransactionHistoryForRecentBooking(b));
    }

    private long parseDateSafe(SimpleDateFormat format, String date) {
        if (date == null || date.trim().isEmpty()) return 0;
        try {
            return format.parse(date).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private int getBookingPriority(Booking booking) {
        String status = booking.getStatus();
        if ("Checked-In".equalsIgnoreCase(status)) return 0;
        if ("Pending".equalsIgnoreCase(status) || "Confirmed".equalsIgnoreCase(status)) return 1;
        if ("Checked-Out".equalsIgnoreCase(status)) return 2;
        if ("Cancelled".equalsIgnoreCase(status)) return 3;
        return 4;
    }

    /** How many Payment Status items the dashboard preview shows at most. */
    private static final int PAYMENT_STATUS_LIMIT = 5;

    /**
     * True when {@code booking} is relevant to the Payment Status preview "today":
     * a currently-due balance (remaining > 0, not cancelled/checked-out), a payment
     * or the booking itself created today, or a check-in/check-out happening today -
     * matches the dashboard spec's "most recent, newly created, currently due, or
     * upcoming... within today" - not simply every booking regardless of relevance.
     */
    private boolean isRelevantToPaymentStatusToday(Booking b) {
        String status = b.getStatus();
        // Same relational check as everywhere else: this Booking record IS the parent
        // transaction (this app has no separate Payment entity/status to cross-check),
        // and Cancelled/Rejected must never show an "active" payment card regardless
        // of any outstanding balance.
        if (TransactionCategorizer.categorize(b) == TransactionCategorizer.Category.CANCELLED) return false;

        boolean currentlyDue = !"Checked-Out".equalsIgnoreCase(status) && b.getEffectiveRemainingBalance() > 0.009;
        if (currentlyDue) return true;

        boolean createdToday = isSameCalendarDay(b.getBookingDate(), 0);
        boolean checkInToday = isSameCalendarDay(b.getCheckInDate(), 0);
        boolean checkOutToday = isSameCalendarDay(b.getCheckOutDate(), 0);

        return createdToday || checkInToday || checkOutToday;
    }

    /** True when dateStr falls exactly on today's calendar date, offset by {@code dayOffset} days. */
    private boolean isSameCalendarDay(String dateStr, int dayOffset) {
        if (dateStr == null) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
            long time = sdf.parse(dateStr).getTime();
            long dayStart = getTodayStartMillis() + (dayOffset * 86400000L);
            long dayEnd = dayStart + 86400000L;
            return time >= dayStart && time < dayEnd;
        } catch (Exception e) {
            return false;
        }
    }

    private void populatePayments(List<Booking> bookings) {
        paymentListContainer.removeAllViews();
        List<Booking> paymentBookings = new java.util.ArrayList<>();
        for (Booking b : bookings) {
            if (isRelevantToPaymentStatusToday(b)) paymentBookings.add(b);
        }
        // Most urgent first: a currently-due balance outranks a same-day event with
        // nothing owed, then fall back to the existing status ordering.
        paymentBookings.sort((firstBooking, secondBooking) -> {
            boolean firstDue = !"Checked-Out".equalsIgnoreCase(firstBooking.getStatus()) && firstBooking.getEffectiveRemainingBalance() > 0.009;
            boolean secondDue = !"Checked-Out".equalsIgnoreCase(secondBooking.getStatus()) && secondBooking.getEffectiveRemainingBalance() > 0.009;
            if (firstDue != secondDue) return firstDue ? -1 : 1;
            return getBookingPriority(firstBooking) - getBookingPriority(secondBooking);
        });
        if (paymentBookings.size() > PAYMENT_STATUS_LIMIT) {
            paymentBookings = new java.util.ArrayList<>(paymentBookings.subList(0, PAYMENT_STATUS_LIMIT));
        }

        if (paymentBookings.isEmpty()) {
            if (noPaymentsText != null) noPaymentsText.setVisibility(View.VISIBLE);
        } else {
            if (noPaymentsText != null) noPaymentsText.setVisibility(View.GONE);
            for (Booking booking : paymentBookings) {
                addPaymentItem(booking);
            }
        }
        setCountBadge(paymentCountBadge, paymentBookings.size());
    }
    private void addPaymentItem(Booking b) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_payment_status, paymentListContainer, false);
        
        TextView tvTypePill = card.findViewById(R.id.tvTransactionTypePill);
        TextView tvRoomName = card.findViewById(R.id.tvPaymentRoomName);
        TextView tvRef = card.findViewById(R.id.tvTransactionRef);
        TextView tvStatus = card.findViewById(R.id.tvPaymentStatus);
        TextView tvVerification = card.findViewById(R.id.tvVerificationStatus);
        TextView tvTotal = card.findViewById(R.id.tvTotalAmount);
        TextView tvPaid = card.findViewById(R.id.tvAmountPaid);
        View layoutRemaining = card.findViewById(R.id.layoutPaymentRemainingBalance);
        TextView tvRemaining = card.findViewById(R.id.tvPaymentRemainingBalance);
        View btnQuickPay = card.findViewById(R.id.btnQuickPay);
        ImageView ivRoomImage = card.findViewById(R.id.ivPaymentRoomImage);

        tvRoomName.setText(b.getRoomName());
        if (ivRoomImage != null) {
            int fallbackImage = RoomVisuals.getRoomImage(b.getRoomType());
            if (b.getRoomImageUrl() != null && !b.getRoomImageUrl().isEmpty()) {
                Glide.with(this).load(b.getRoomImageUrl()).placeholder(fallbackImage).error(fallbackImage).into(ivRoomImage);
            } else {
                ivRoomImage.setImageResource(fallbackImage);
            }
        }
        String ref = b.getTransactionRef() != null ? b.getTransactionRef() : b.getId();
        tvRef.setText(getString(R.string.transaction_ref_format, ref));

        TextView tvDeadline = card.findViewById(R.id.tvPaymentDeadline);
        if (tvDeadline != null) {
            boolean hasDeadline = PaymentDeadlineFormatter.hasActiveDeadline(b);
            tvDeadline.setVisibility(hasDeadline ? View.VISIBLE : View.GONE);
            if (hasDeadline) {
                tvDeadline.setText(PaymentDeadlineFormatter.formatChipText(this, b));
            }
        }

        TextView tvMethod = card.findViewById(R.id.tvPaymentMethod);
        if (tvMethod != null) {
            boolean hasMethod = b.getPaymentMethod() != null && !b.getPaymentMethod().trim().isEmpty();
            tvMethod.setVisibility(hasMethod ? View.VISIBLE : View.GONE);
            if (hasMethod) {
                tvMethod.setText(getString(R.string.payment_method_colon_format,
                        "cash".equalsIgnoreCase(b.getPaymentMethod()) ? getString(R.string.payment_method_cash) : getString(R.string.payment_method_gcash)));
            }
        }

        // Transaction lifecycle badge - shows the specific Reservation
        // Pending / Booking Pending / Validated / Completed Reservation
        // state (PaymentStatusResolver#resolveLifecycleBadge(), same
        // hasBooking/isStaffVerified signals TransactionCategorizer uses) -
        // strictly more informative than a bare "Booking"/"Reservation"
        // label, so it replaces rather than sits alongside that. hasBooking
        // is still the server-authoritative flag driving the Pay Now
        // gating below - never derived from status text alone.
        if (tvTypePill != null) {
            PaymentStatusResolver.Result lifecycleResult = PaymentStatusResolver.resolveLifecycleBadge(this, b);
            tvTypePill.setText(lifecycleResult.label);
            tvTypePill.setBackgroundTintList(ContextCompat.getColorStateList(this, lifecycleResult.bgColorRes));
            tvTypePill.setTextColor(ContextCompat.getColor(this, lifecycleResult.fgColorRes));
            applyPillIcon(tvTypePill, lifecycleResult.iconRes, lifecycleResult.fgColorRes);
        }

        double total = b.getTotalAmount();
        // Backend-authoritative payment_summary totals when attached, else the
        // legacy fields - see Booking#getEffectiveTotalAmountPaid()'s own doc
        // (PAYMENT_RECEIPT_HISTORY_BACKEND_SPEC.md Phase 6 §1) - must never
        // disagree with the PaymentStatusResolver pill shown right below.
        double paid = b.getEffectiveTotalAmountPaid();
        double remaining = b.getEffectiveRemainingBalance();

        tvTotal.setText(String.format(Locale.getDefault(), getString(R.string.price_format_night), total));
        tvPaid.setText(String.format(Locale.getDefault(), getString(R.string.price_format_night), paid));

        // Remaining Balance column: only meaningful once some payment activity exists -
        // an unpaid Reservation has nothing "remaining" to speak of yet.
        boolean showRemaining = b.isHasBooking() || paid > 0.009;
        if (layoutRemaining != null) layoutRemaining.setVisibility(showRemaining ? View.VISIBLE : View.INVISIBLE);
        if (tvRemaining != null) {
            tvRemaining.setText(String.format(Locale.getDefault(), getString(R.string.price_format_night), Math.max(0, remaining)));
        }

        PaymentStatusResolver.Result statusResult = PaymentStatusResolver.resolve(this, b);
        tvStatus.setText(statusResult.label);
        int fgColor = ContextCompat.getColor(this, statusResult.fgColorRes);
        tvStatus.setBackgroundTintList(ContextCompat.getColorStateList(this, statusResult.bgColorRes));
        tvStatus.setTextColor(fgColor);

        // Verification Status - distinct from Payment Status above (see
        // item_payment_status.xml's comment): a GCash payment can be "Partially Paid"
        // while still awaiting receptionist review, or already verified/rejected.
        if (tvVerification != null) {
            String verification = b.getPaymentVerificationStatus();
            if ("pending_verification".equalsIgnoreCase(verification)) {
                tvVerification.setText(R.string.awaiting_verification_label);
                tvVerification.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.velocity_orange_soft));
                tvVerification.setTextColor(ContextCompat.getColor(this, R.color.velocity_orange_primary));
                tvVerification.setVisibility(View.VISIBLE);
            } else if ("verified".equalsIgnoreCase(verification)) {
                tvVerification.setText(R.string.status_verified);
                tvVerification.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.velocity_green_soft));
                tvVerification.setTextColor(ContextCompat.getColor(this, R.color.velocity_green_dark));
                tvVerification.setVisibility(View.VISIBLE);
            } else if ("rejected".equalsIgnoreCase(verification)) {
                tvVerification.setText(R.string.status_rejected);
                tvVerification.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.velocity_red_subtle));
                tvVerification.setTextColor(ContextCompat.getColor(this, R.color.velocity_red_dark));
                tvVerification.setVisibility(View.VISIBLE);
            } else {
                tvVerification.setVisibility(View.GONE);
            }
        }

        // Tapping the card is always "View Details" - it opens Transaction History
        // under the matching type filter (Bookings vs Reservations, never just
        // "Payments") with this exact transaction selected/highlighted. A Booking
        // transaction is strictly view-only here: whether it's Full Payment or
        // Partial Payment, it never gets a Pay Now action on this card. Only a
        // Reservation that hasn't been converted to a Booking yet (isHasBooking()
        // == false, the server-authoritative flag - see the type-pill comment
        // above) can ever show one, and even then only while it still has a real
        // balance due and isn't rejected/cancelled.
        card.setOnClickListener(v -> openTransactionHistoryForPaymentItem(b));

        if (btnQuickPay != null) {
            // Same single-source-of-truth rule BookingAndReservationActivity's
            // Reservation List Pay Now button uses (see PaymentEligibility) -
            // previously this had its own, independently-drifted condition
            // that (unlike the list's) correctly excluded a rejected payment
            // from re-showing Pay Now/Resubmit, but (like the list's) never
            // excluded a payment still awaiting verification. Both screens now
            // agree: a rejected payment still shows this button (doubling as
            // Resubmit), a pending-verification one never does.
            boolean payable = remaining > 0.009 && PaymentEligibility.canPayNow(b);
            btnQuickPay.setVisibility(payable ? View.VISIBLE : View.GONE);
            if (payable) {
                // Same Billing-Summary-first flow as the Active Reservation
                // List's own Pay Now (BookingAndReservationActivity#
                // showPayNowConfirmation()) - the guest must review the full
                // bill before reaching PaymentActivity's GCash portal,
                // regardless of which screen Pay Now was tapped from.
                btnQuickPay.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.pay_now_confirm_title)
                        .setMessage(getString(R.string.pay_now_confirm_msg, b.getId()))
                        .setPositiveButton(R.string.confirm_dialog_positive, (dialog, which) -> {
                            android.content.Intent intent = new android.content.Intent(this, BillingSummaryActivity.class);
                            intent.putExtra(BillingSummaryActivity.EXTRA_RESERVATION_ID, b.getId());
                            startActivity(intent);
                        })
                        .setNegativeButton(R.string.no_label, null)
                        .show());
            }
        }

        paymentListContainer.addView(card);
    }
    /** How many of the soonest upcoming check-ins/check-outs the Upcoming Transactions preview shows. */
    private static final int UPCOMING_PREVIEW_LIMIT = 5;

    /**
     * Every future check-in (not yet checked in) or future check-out (already
     * checked in), soonest first - same window/eligibility rule as
     * UpcomingTransactionsActivity.buildEvents(), just capped for a compact
     * dashboard preview. Each card carries its Booking so tapping it deep-links
     * straight to that exact record in the full Upcoming Hotel Transactions List.
     */
    private void populateUpcoming(List<Booking> bookings) {
        upcomingListContainer.removeAllViews();
        selectedUpcomingCard = null;

        class UpcomingEvent {
            final Booking booking;
            final boolean isCheckIn;
            final long timeMillis;

            UpcomingEvent(Booking booking, boolean isCheckIn, long timeMillis) {
                this.booking = booking;
                this.isCheckIn = isCheckIn;
                this.timeMillis = timeMillis;
            }
        }

        List<UpcomingEvent> events = new java.util.ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
        long now = System.currentTimeMillis();

        for (Booking b : bookings) {
            if (TransactionCategorizer.categorize(b) == TransactionCategorizer.Category.CANCELLED) continue;

            try {
                long checkInTime = sdf.parse(b.getCheckInDate()).getTime();
                long checkOutTime = sdf.parse(b.getCheckOutDate()).getTime();

                // Add Check-in if in future or today and not checked in yet
                if (checkInTime >= now - 86400000 && !"Checked-In".equalsIgnoreCase(b.getStatus()) && !"Checked-Out".equalsIgnoreCase(b.getStatus())) {
                    events.add(new UpcomingEvent(b, true, checkInTime));
                }

                // Add Check-out if checked in and not checked out yet
                if (checkOutTime >= now - 86400000 && "Checked-In".equalsIgnoreCase(b.getStatus())) {
                    events.add(new UpcomingEvent(b, false, checkOutTime));
                }
            } catch (Exception ignored) {}
        }

        java.util.Collections.sort(events, (a, c) -> Long.compare(a.timeMillis, c.timeMillis));

        if (events.isEmpty()) {
            noUpcomingText.setVisibility(View.VISIBLE);
            setCountBadge(upcomingCountBadge, 0);
        } else {
            noUpcomingText.setVisibility(View.GONE);
            int count = 0;
            for (UpcomingEvent e : events) {
                if (count >= UPCOMING_PREVIEW_LIMIT) break;
                addTransactionItemCard(e.booking, e.isCheckIn, upcomingListContainer, true);
                count++;
            }
            setCountBadge(upcomingCountBadge, count);
        }
    }


    private void populateNotifications(List<Notification> notifications) {
        notificationListContainer.removeAllViews();
        selectedNotificationCard = null;
        if (notifications.isEmpty()) {
            noNotificationsText.setVisibility(View.VISIBLE);
            setCountBadge(notificationCountBadge, 0);
        } else {
            noNotificationsText.setVisibility(View.GONE);
            // Show only top 3 recent notifications
            int count = 0;
            for (Notification n : notifications) {
                if (count >= 3) break;
                addNotificationCardCompact(n, notificationListContainer);
                count++;
            }
            setCountBadge(notificationCountBadge, count);
        }
    }

    /**
     * Applies a PaymentStatusResolver.Result's leading icon to a status pill
     * TextView, tinted to match its own text color - the app stays strictly
     * red &amp; white (per product decision), so every status is differentiated
     * by icon/shape/label instead of a distinct hue.
     */
    private void applyPillIcon(TextView pill, int iconRes, int fgColorRes) {
        android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(this, iconRes);
        if (icon == null) return;
        int size = (int) (12 * getResources().getDisplayMetrics().density);
        icon.setBounds(0, 0, size, size);
        icon.setTint(ContextCompat.getColor(this, fgColorRes));
        pill.setCompoundDrawables(icon, null, null, null);
        pill.setCompoundDrawablePadding((int) (4 * getResources().getDisplayMetrics().density));
    }

    /**
     * Compact preview used only by the dashboard's "Notifications" section -
     * "See All" already opens the full NotificationActivity list where the
     * message body, category, and status pills live, so this card shows
     * just title, time, and unread state.
     */
    private void addNotificationCardCompact(Notification n, LinearLayout container) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_notification_compact, container, false);
        com.google.android.material.card.MaterialCardView cardRoot = (com.google.android.material.card.MaterialCardView) card;

        TextView tvTitle = card.findViewById(R.id.tvNotifCompactTitle);
        TextView tvTime = card.findViewById(R.id.tvNotifCompactTime);
        View unreadDot = card.findViewById(R.id.unreadDotCompact);
        ImageView ivIcon = card.findViewById(R.id.ivNotifCompactIcon);

        tvTitle.setText(n.getTitle());
        tvTime.setText(n.getTimestamp());
        unreadDot.setVisibility(n.isRead() ? View.GONE : View.VISIBLE);

        if (Notification.TYPE_PAYMENT.equals(n.getType())) {
            ivIcon.setImageResource(R.drawable.ic_payment_card);
        } else if (Notification.TYPE_BOOKING.equals(n.getType())) {
            ivIcon.setImageResource(R.drawable.ic_reservation);
        }

        View header = card.findViewById(R.id.rowNotifCompactHeader);
        View btnViewDetails = card.findViewById(R.id.btnNotifCompactViewDetails);
        header.setOnClickListener(v -> selectedNotificationCard = toggleSelection(selectedNotificationCard, cardRoot, btnViewDetails));
        btnViewDetails.setOnClickListener(v -> {
            RoomRepository.getInstance(this).markNotificationAsRead(n.getId(), success -> loadDashboardData());
            android.content.Intent intent = new android.content.Intent(this, NotificationActivity.class);
            intent.putExtra(NotificationActivity.EXTRA_NOTIFICATION_ID, n.getId());
            startActivity(intent);
        });

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
        findViewById(R.id.btnViewAllUpcoming).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, UpcomingTransactionsActivity.class))));
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
        findViewById(R.id.nextTransactionCard).setOnClickListener(NavUtils.debounce(v ->
                startActivity(new android.content.Intent(this, UpcomingTransactionsActivity.class))));
        findViewById(R.id.btnEditProfile).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, ProfileManagementActivity.class))));
        findViewById(R.id.dateTimeCard).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, CalendarActivity.class))));

        View headerNotification = findViewById(R.id.headerNotificationContainer);
        if (headerNotification != null) {
            headerNotification.setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, NotificationActivity.class))));
        }

        findViewById(R.id.bookingDetailsCard).setOnClickListener(NavUtils.debounce(v -> openTransactionHistoryBookings()));
        findViewById(R.id.upcomingTransactionsCard).setOnClickListener(NavUtils.debounce(v -> startActivity(new android.content.Intent(this, UpcomingTransactionsActivity.class))));
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

    /**
     * Tapping a card in a select-then-view-details section: collapses whichever card in this
     * section was previously selected, then toggles selection on the tapped card. Returns the
     * new selection state for the caller to store (null when the tap deselected it).
     */
    private SelectableCard toggleSelection(SelectableCard previous, com.google.android.material.card.MaterialCardView card, View viewDetailsButton) {
        if (previous != null && previous.card != card) {
            setCardSelected(previous.card, previous.viewDetailsButton, false);
        }
        boolean nowSelected = viewDetailsButton.getVisibility() != View.VISIBLE;
        setCardSelected(card, viewDetailsButton, nowSelected);
        return nowSelected ? new SelectableCard(card, viewDetailsButton) : null;
    }

    private void setCardSelected(com.google.android.material.card.MaterialCardView card, View viewDetailsButton, boolean selected) {
        viewDetailsButton.setVisibility(selected ? View.VISIBLE : View.GONE);
        card.setStrokeWidth(dpToPx(selected ? 2 : 1));
        card.setStrokeColor(ContextCompat.getColor(this, selected ? R.color.velocity_red_primary : R.color.velocity_red_alpha_20));
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
    private void updateDashboardHub(List<Booking> bookings, List<Notification> notifications) {
        nextTransactionListContainer.removeAllViews();
        List<Booking> tomorrowCheckIns = getTomorrowCheckIns(bookings);

        if (!tomorrowCheckIns.isEmpty()) {
            if (nextHotelTransactionText != null) nextHotelTransactionText.setVisibility(View.GONE);
            for (Booking b : tomorrowCheckIns) {
                addTransactionItemCard(b, true, nextTransactionListContainer, false);
            }
            return;
        }

        Booking nextBooking = getNextHotelTransactionBooking(bookings);
        if (nextBooking == null) {
            if (nextHotelTransactionText != null) nextHotelTransactionText.setVisibility(View.VISIBLE);
            return;
        }
        if (nextHotelTransactionText != null) nextHotelTransactionText.setVisibility(View.GONE);
        // "Checked-In" means the relevant next event is the check-out, not the
        // check-in - matches the exact selection rule in getNextHotelTransactionBooking().
        boolean isCheckInEvent = !"Checked-In".equalsIgnoreCase(nextBooking.getStatus());
        addTransactionItemCard(nextBooking, isCheckInEvent, nextTransactionListContainer, false);
    }

    /** Every non-cancelled/non-rejected, not-yet-checked-in booking/reservation whose check-in date is tomorrow. */
    private List<Booking> getTomorrowCheckIns(List<Booking> bookings) {
        List<Booking> result = new ArrayList<>();
        for (Booking b : bookings) {
            String status = b.getStatus();
            if (TransactionCategorizer.categorize(b) == TransactionCategorizer.Category.CANCELLED
                    || "Checked-In".equalsIgnoreCase(status) || "Checked-Out".equalsIgnoreCase(status)) {
                continue;
            }
            if (isDateTomorrow(b.getCheckInDate())) {
                result.add(b);
            }
        }
        return result;
    }

    /**
     * Shared by the Next Hotel Transaction and Upcoming Transactions dashboard
     * sections - both show the same compact, fully-detailed card (type, room,
     * Booking ID, date, status, payment status) and deep-link the same way,
     * they just differ in which bookings feed into them.
     */
    private void addTransactionItemCard(Booking b, boolean isCheckInEvent, LinearLayout container, boolean selectableTwoStep) {
        addTransactionItemCard(b, isCheckInEvent, container, selectableTwoStep, null);
    }

    /**
     * @param customClickAction when non-null, replaces the default "open
     *                          UpcomingTransactionsActivity" tap behavior -
     *                          used by Recent Bookings to deep-link into
     *                          Transaction History instead, while reusing
     *                          this same compact row design (room/type pill/
     *                          booking id/date/status/payment-status) so
     *                          every dashboard transaction list looks and
     *                          behaves consistently.
     */
    private void addTransactionItemCard(Booking b, boolean isCheckInEvent, LinearLayout container, boolean selectableTwoStep, Runnable customClickAction) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_next_transaction, container, false);

        TextView tvTypePill = card.findViewById(R.id.tvNextItemTypePill);
        TextView tvRoomName = card.findViewById(R.id.tvNextItemRoomName);
        TextView tvBookingId = card.findViewById(R.id.tvNextItemBookingId);
        TextView tvDate = card.findViewById(R.id.tvNextItemDate);
        TextView tvTomorrowBadge = card.findViewById(R.id.tvNextItemTomorrowBadge);
        TextView tvStatus = card.findViewById(R.id.tvNextItemStatus);
        TextView tvPaymentStatus = card.findViewById(R.id.tvNextItemPaymentStatus);
        ImageView ivIcon = card.findViewById(R.id.ivNextItemIcon);
        com.google.android.material.card.MaterialCardView iconContainer = card.findViewById(R.id.iconContainerNextItem);

        tvTypePill.setText(b.isHasBooking() ? R.string.upcoming_event_booking_prefix : R.string.upcoming_event_reservation_prefix);
        tvRoomName.setText(b.getRoomName());
        tvBookingId.setText(getString(R.string.booking_id_format, b.getId()));

        String relevantDate = isCheckInEvent ? b.getCheckInDate() : b.getCheckOutDate();
        tvDate.setText(getString(isCheckInEvent ? R.string.upcoming_check_in_format : R.string.upcoming_check_out_format, relevantDate));

        if (isCheckInEvent) {
            ivIcon.setImageResource(R.drawable.ic_calendar);
            iconContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.velocity_red_soft));
            ivIcon.setImageTintList(ContextCompat.getColorStateList(this, R.color.velocity_red_primary));
        } else {
            ivIcon.setImageResource(R.drawable.ic_clock);
            iconContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.velocity_green_soft));
            ivIcon.setImageTintList(ContextCompat.getColorStateList(this, R.color.velocity_green_primary));
        }

        if (isDateTomorrow(relevantDate)) {
            tvTomorrowBadge.setVisibility(View.VISIBLE);
            tvTomorrowBadge.setText(R.string.tomorrow_badge_short);
        } else {
            tvTomorrowBadge.setVisibility(View.GONE);
        }

        String status = b.getStatus();
        tvStatus.setText(status);
        if ("Confirmed".equalsIgnoreCase(status) || "Checked-In".equalsIgnoreCase(status) || "Checked-Out".equalsIgnoreCase(status) || "Verified".equalsIgnoreCase(status)) {
            tvStatus.setBackgroundResource(R.drawable.bg_badge_success);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.velocity_green_dark));
        } else if ("Cancelled".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
            tvStatus.setBackgroundResource(R.drawable.bg_badge_error);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.velocity_red_dark));
        } else {
            tvStatus.setBackgroundResource(R.drawable.bg_badge_warning);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.velocity_orange_primary));
        }

        PaymentStatusResolver.Result paymentStatus = PaymentStatusResolver.resolve(this, b);
        tvPaymentStatus.setText(paymentStatus.label);
        tvPaymentStatus.setBackgroundTintList(ContextCompat.getColorStateList(this, paymentStatus.bgColorRes));
        tvPaymentStatus.setTextColor(ContextCompat.getColor(this, paymentStatus.fgColorRes));
        applyPillIcon(tvPaymentStatus, paymentStatus.iconRes, paymentStatus.fgColorRes);

        View btnViewDetails = card.findViewById(R.id.btnNextItemViewDetails);
        // rowNextItemHeader (not the outer card) is the view that's actually clickable+has a
        // ripple foreground, so the listener must go here - a click on the outer MaterialCardView
        // would never fire, since this inner row consumes the touch first.
        View header = card.findViewById(R.id.rowNextItemHeader);
        View.OnClickListener openThisTransaction = v -> {
            if (customClickAction != null) {
                customClickAction.run();
                return;
            }
            android.content.Intent intent = new android.content.Intent(this, UpcomingTransactionsActivity.class);
            intent.putExtra(UpcomingTransactionsActivity.EXTRA_SELECTED_BOOKING_ID, b.getId());
            startActivity(intent);
        };

        if (selectableTwoStep) {
            // Upcoming Transactions section: tapping the card only selects it and reveals
            // "View Details" - that button is what deep-links to the exact transaction in the
            // Upcoming Hotel Transaction List (scrolled to + highlighted + opened).
            com.google.android.material.card.MaterialCardView cardRoot = (com.google.android.material.card.MaterialCardView) card;
            header.setOnClickListener(v -> selectedUpcomingCard = toggleSelection(selectedUpcomingCard, cardRoot, btnViewDetails));
            btnViewDetails.setOnClickListener(openThisTransaction);
        } else {
            // Next Hotel Transaction hub: tapping the card deep-links straight to that exact
            // transaction, same as before - this preview isn't part of the select-then-view-details flow.
            if (btnViewDetails != null) btnViewDetails.setVisibility(View.GONE);
            header.setOnClickListener(openThisTransaction);
        }

        container.addView(card);
    }

    /**
     * Nearest future check-in not yet checked-in, or nearest future check-out
     * while checked-in - same selection rule as before, just returning the
     * Booking itself instead of a pre-formatted single-line string so the
     * card can show full detail (Booking ID, room, dates, status).
     */
    private Booking getNextHotelTransactionBooking(List<Booking> bookings) {
        SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
        long todayStart = getTodayStartMillis();
        long bestTime = Long.MAX_VALUE;
        Booking best = null;

        for (Booking booking : bookings) {
            if (TransactionCategorizer.categorize(booking) == TransactionCategorizer.Category.CANCELLED
                    || "Checked-Out".equalsIgnoreCase(booking.getStatus())) {
                continue;
            }
            try {
                long checkInTime = sdf.parse(booking.getCheckInDate()).getTime();
                long checkOutTime = sdf.parse(booking.getCheckOutDate()).getTime();

                if (checkInTime >= todayStart && !"Checked-In".equalsIgnoreCase(booking.getStatus()) && checkInTime < bestTime) {
                    bestTime = checkInTime;
                    best = booking;
                }
                if (checkOutTime >= todayStart && "Checked-In".equalsIgnoreCase(booking.getStatus()) && checkOutTime < bestTime) {
                    bestTime = checkOutTime;
                    best = booking;
                }
            } catch (Exception ignored) {}
        }

        return best;
    }

    private long getTodayStartMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** True when dateStr (yyyy/M/d-style, per date_format_short) falls exactly on tomorrow's calendar date. */
    private boolean isDateTomorrow(String dateStr) {
        if (dateStr == null) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.date_format_short), Locale.US);
            long time = sdf.parse(dateStr).getTime();
            long tomorrowStart = getTodayStartMillis() + 86400000L;
            long tomorrowEnd = tomorrowStart + 86400000L;
            return time >= tomorrowStart && time < tomorrowEnd;
        } catch (Exception e) {
            return false;
        }
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
        startActivity(intent);
    }

    /** Recent Bookings section - opens Transaction History on the Bookings chip, scrolled to and highlighting this exact Booking ID. */
    private void openTransactionHistoryForRecentBooking(Booking booking) {
        android.content.Intent intent = new android.content.Intent(this, TransactionHistoryActivity.class);
        intent.putExtra(TransactionHistoryActivity.EXTRA_OPEN_FILTER, TransactionHistoryActivity.FILTER_BOOKINGS);
        intent.putExtra(TransactionHistoryActivity.EXTRA_SELECTED_BOOKING_ID, booking.getId());
        startActivity(intent);
    }

    private void startAnimations() {
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in_slide_up);
        startAnimationIfPresent(R.id.header, fadeIn);
        startAnimationIfPresent(R.id.welcomeText, fadeIn);
        startAnimationIfPresent(R.id.userNameText, fadeIn);
        startAnimationIfPresent(R.id.dateTimeCard, fadeIn);
        startAnimationIfPresent(R.id.nextTransactionCard, fadeIn);
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
        clockHandler.postDelayed(clockRunnable, 1000);
    }

    private void updateDateTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.date_format_full), Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat(getString(R.string.time_format_seconds), Locale.getDefault());

        if (dateText != null) {
            dateText.setText(dateFormat.format(calendar.getTime()));
        }
        if (timeText != null) {
            timeText.setText(timeFormat.format(calendar.getTime()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserInfo();
        loadDashboardData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clockRunnable != null) {
            clockHandler.removeCallbacks(clockRunnable);
        }
    }
}




