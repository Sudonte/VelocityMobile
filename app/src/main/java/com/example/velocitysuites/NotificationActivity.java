package com.example.velocitysuites;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationActivity extends BaseNavigationActivity {

    /** Optional: when set, only this notification's detail dialog is auto-opened on load. */
    public static final String EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID";
    private static final String FILTER_UNREAD = "Unread";

    /** Internal filter keys, position-matched with NOTIF_FILTER_LABEL_RES below - the dropdown's selection maps back to the correct key regardless of locale. Required order: All, Unread, Booking, Reservation, Payment, Check-in, Promotions, System. */
    private static final String[] NOTIF_FILTER_KEYS = {
            "All", FILTER_UNREAD, Notification.TYPE_BOOKING, Notification.TYPE_RESERVATION, Notification.TYPE_PAYMENT,
            Notification.TYPE_CHECK_IN, Notification.TYPE_PROMOTION, Notification.TYPE_SYSTEM
    };
    private static final int[] NOTIF_FILTER_LABEL_RES = {
            R.string.notif_filter_all_label, R.string.filter_unread, R.string.quick_action_booking, R.string.quick_action_reservation,
            R.string.payment_status, R.string.notif_filter_checkin, R.string.notif_filter_promotion,
            R.string.notif_filter_system
    };

    // Silent polling refresh, matching the website's 30s auto-refresh on
    // its own notifications page - keeps the list current without the
    // guest needing to pull-to-refresh manually.
    private static final long AUTO_REFRESH_MS = 30000;
    private final android.os.Handler autoRefreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            // Silent - no visible spinner - so the auto-poll doesn't flash the
            // pull-to-refresh indicator every 30s while the guest is reading.
            loadNotifications(false);
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    private RecyclerView rvNotifications;
    private View layoutEmptyState;
    private TextView emptyTitle, emptyDesc;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearchNotifications;
    private AutoCompleteTextView dropdownNotificationStatus;
    private NotificationAdapter adapter;
    private RoomRepository repository;
    private List<Notification> allNotifications = new ArrayList<>();
    private List<Notification> notificationList = new ArrayList<>();
    private String searchQuery = "";
    private String currentFilter = "All";
    /** Notification id to auto-open the detail dialog for, from the dashboard's per-notification deep link. */
    private String pendingDetailId;
    /** Same id as pendingDetailId, but kept around (not cleared after first use) to keep the row highlighted/scrolled-to across refreshes. */
    private String selectedNotificationId;
    private boolean pendingScrollToSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notification);
        setupGuestNavigation(View.NO_ID);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);
        
        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        etSearchNotifications = findViewById(R.id.etSearchNotifications);
        dropdownNotificationStatus = findViewById(R.id.dropdownNotificationStatus);
        pendingDetailId = getIntent().getStringExtra(EXTRA_NOTIFICATION_ID);
        selectedNotificationId = pendingDetailId;
        pendingScrollToSelected = selectedNotificationId != null;

        // The empty state is a shared include (view_empty_state.xml) whose title/description
        // are populated in code - without this it renders as a blank card with just an icon.
        // Kept as fields (not locals) since applyFilters() updates the text dynamically -
        // "No unread notifications" when the Unread filter is active vs. the generic message.
        emptyTitle = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.emptyTitle) : null;
        emptyDesc = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.emptyDesc) : null;
        ImageView emptyIcon = layoutEmptyState != null ? layoutEmptyState.findViewById(R.id.emptyIcon) : null;
        if (emptyTitle != null) emptyTitle.setText(R.string.no_notifications_title);
        if (emptyDesc != null) emptyDesc.setText(R.string.no_notifications_desc);
        if (emptyIcon != null) emptyIcon.setImageResource(R.drawable.ic_notifications);

        setupRecyclerView();
        setupSwipeRefresh();
        setupSearchAndFilters();

        View btnRefreshEmpty = findViewById(R.id.btnEmptyAction);
        if (btnRefreshEmpty != null) {
            btnRefreshEmpty.setVisibility(View.VISIBLE);
            if (btnRefreshEmpty instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) btnRefreshEmpty).setText(R.string.refresh_label);
                ((com.google.android.material.button.MaterialButton) btnRefreshEmpty).setIconResource(R.drawable.ic_clock);
            }
            btnRefreshEmpty.setOnClickListener(v -> loadNotifications(true));
        }

        View btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
        btnMarkAllRead.setOnClickListener(v -> confirmMarkAllRead(btnMarkAllRead));

        loadNotifications(true);
    }

    /**
     * Marking every notification read is one-way (there's no bulk "mark unread"), so
     * confirm before applying it - the same confirm-dialog convention used for other
     * one-way actions across the app (see ProfileManagementActivity's confirm dialogs).
     */
    private void confirmMarkAllRead(View triggerButton) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.confirm_mark_all_read_msg)
                .setPositiveButton(R.string.confirm_dialog_positive, (d, w) -> {
                    // Disabled for the duration of the request so a repeated tap
                    // right after this dialog's auto-dismiss can't fire a second
                    // concurrent mark-all-read call.
                    triggerButton.setEnabled(false);

                    // Optimistic: flip every currently-unread item immediately, remembering
                    // exactly which ones so a failed request can revert precisely those -
                    // never an item that was already read before this tap.
                    List<Notification> flipped = new ArrayList<>();
                    for (Notification n : allNotifications) {
                        if (!n.isRead()) {
                            n.setRead(true);
                            flipped.add(n);
                        }
                    }
                    updateNotificationBadge();
                    updateFilterLabelsWithCounts();
                    applyFilters();

                    repository.markAllNotificationsAsRead(success -> {
                        triggerButton.setEnabled(true);
                        if (!success) {
                            for (Notification n : flipped) n.setRead(false);
                            updateNotificationBadge();
                            updateFilterLabelsWithCounts();
                            applyFilters();
                        }
                        Toast.makeText(this, success ? R.string.msg_mark_all_read : R.string.network_error, Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void setupRecyclerView() {
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(notificationList, new NotificationAdapter.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(Notification notification) {
                showNotificationDetails(notification);
                setReadStateOptimistic(notification, true);
            }

            @Override
            public void onToggleReadClick(Notification notification) {
                setReadStateOptimistic(notification, !notification.isRead());
            }
        });
        rvNotifications.setAdapter(adapter);
    }

    private void showNotificationDetails(Notification notification) {
        startActivity(NotificationDetailsActivity.newIntent(this, notification));
    }

    /**
     * Flips a notification's read state immediately (before the network call resolves)
     * so the row/badges/counts update instantly, then confirms with the backend in the
     * background and reverts on failure. Single entry point for every read-state-changing
     * action on this screen (tap-to-view, the per-row toggle, the deep-link auto-open, see
     * openPendingDetailIfAny()) - replaces the old pattern of a full loadNotifications()
     * reload after every mark-as-read, which cost a second, redundant network round-trip
     * just to repaint the row, and left the dot visibly stale if that second call had a
     * transient failure even though the mark-as-read itself had already genuinely succeeded.
     */
    private void setReadStateOptimistic(Notification notification, boolean newReadState) {
        boolean previousState = notification.isRead();
        if (previousState == newReadState) return;

        notification.setRead(newReadState);
        notifyNotificationChanged(notification);

        java.util.function.Consumer<Boolean> onDone = success -> {
            if (!success) {
                notification.setRead(previousState);
                notifyNotificationChanged(notification);
                Toast.makeText(this, R.string.network_error, Toast.LENGTH_SHORT).show();
            }
        };
        if (newReadState) {
            repository.markNotificationAsRead(notification.getId(), onDone);
        } else {
            repository.markNotificationAsUnread(notification.getId(), onDone);
        }
    }

    /** Repaints exactly this notification's row (if currently visible under the active filter) plus the badges/per-filter counts that depend on read state - avoids a full list rebuild for a single-row change. */
    private void notifyNotificationChanged(Notification notification) {
        updateNotificationBadge();
        updateFilterLabelsWithCounts();
        int position = notificationList.indexOf(notification);
        if (position >= 0) {
            adapter.notifyItemChanged(position);
        }
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        // The pull gesture already shows the spinner itself, so this listener doesn't
        // need to turn it on again - loadNotifications() turns it off once done.
        swipeRefresh.setOnRefreshListener(() -> loadNotifications(false));
    }

    /**
     * @param showLoadingIndicator whether to show the pull-to-refresh spinner while this
     *                             fetch is in flight. False for the silent 30s auto-poll
     *                             (see autoRefreshRunnable) so it doesn't flash the spinner
     *                             without the guest asking for it; true for the initial
     *                             load and any explicit user-triggered refresh.
     */
    /**
     * This screen keeps its own data fresh (this method, called from onCreate/onResume/
     * swipe-refresh/the 30s auto-poll), so the shared header's own badge-only refetch
     * (BaseNavigationActivity#refreshNotificationBadge()) would just be a second,
     * redundant network call every time this screen resumes - suppressed below.
     */
    @Override
    protected void refreshNotificationBadge() {
        updateNotificationBadge();
    }

    private void loadNotifications(boolean showLoadingIndicator) {
        if (showLoadingIndicator && swipeRefresh != null) swipeRefresh.setRefreshing(true);
        repository.refreshNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                allNotifications = result != null ? result : new ArrayList<>();
                updateNotificationBadge();
                updateFilterLabelsWithCounts();
                applyFilters();
                openPendingDetailIfAny();
            }

            @Override
            public void onError(String message) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                // A transient refresh/auto-poll failure with a list already on screen just
                // gets a toast - replacing a working list with a scary error screen over a
                // momentary network blip would be worse than doing nothing. Only the
                // genuine "never successfully loaded anything yet" case gets the full error
                // state (reusing the same empty-state view, retry button included).
                if (allNotifications.isEmpty()) {
                    rvNotifications.setVisibility(View.GONE);
                    layoutEmptyState.setVisibility(View.VISIBLE);
                    if (emptyTitle != null) emptyTitle.setText(R.string.no_notifications_load_error_title);
                    if (emptyDesc != null) emptyDesc.setText(R.string.no_notifications_load_error_desc);
                } else {
                    Toast.makeText(NotificationActivity.this, getString(R.string.error_load_notifications_format, message), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setupSearchAndFilters() {
        if (etSearchNotifications != null) {
            etSearchNotifications.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    searchQuery = s.toString().trim().toLowerCase(Locale.US);
                    applyFilters();
                }
            });
        }
        if (dropdownNotificationStatus != null) {
            updateFilterLabelsWithCounts();
            dropdownNotificationStatus.setOnItemClickListener((parent, view, position, id) -> {
                currentFilter = NOTIF_FILTER_KEYS[position];
                applyFilters();
            });
        }
    }

    /**
     * Rebuilds the filter dropdown's option labels with an unread-count suffix, e.g.
     * "Booking (3)" - omitted (no "(0)") for a filter with nothing unread, so an
     * already-caught-up category stays visually quiet. "All" and "Unread" both show
     * the same total-unread count; a category's count is only its own unread rows,
     * not its total volume. Re-set on every successful load and after every optimistic
     * read/unread mutation so the counts never visibly lag what the rows themselves
     * show. Rebuilding the AutoCompleteTextView's suggestion adapter doesn't change
     * whatever text is currently displayed in the box, so this never disturbs the
     * guest's active filter selection.
     */
    private void updateFilterLabelsWithCounts() {
        if (dropdownNotificationStatus == null) return;

        int totalUnread = 0;
        java.util.Map<String, Integer> unreadByType = new java.util.HashMap<>();
        for (Notification n : allNotifications) {
            if (n.isRead()) continue;
            totalUnread++;
            String type = n.getType();
            // "System" filter merges TYPE_SYSTEM + TYPE_ANNOUNCEMENT - see applyFilters()'s identical rule.
            String bucket = (Notification.TYPE_SYSTEM.equals(type) || Notification.TYPE_ANNOUNCEMENT.equals(type))
                    ? Notification.TYPE_SYSTEM : type;
            unreadByType.merge(bucket, 1, Integer::sum);
        }

        String[] labels = new String[NOTIF_FILTER_LABEL_RES.length];
        for (int i = 0; i < NOTIF_FILTER_LABEL_RES.length; i++) {
            String base = getString(NOTIF_FILTER_LABEL_RES[i]);
            String key = NOTIF_FILTER_KEYS[i];
            int count = ("All".equals(key) || FILTER_UNREAD.equals(key))
                    ? totalUnread
                    : (unreadByType.containsKey(key) ? unreadByType.get(key) : 0);
            labels[i] = count > 0 ? getString(R.string.notif_filter_count_format, base, count) : base;
        }
        dropdownNotificationStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels));
    }

    /**
     * Title/message match first (cheap, always available); when those miss and the
     * notification links to a Booking/Reservation still resident in RoomRepository's
     * cache (same lookup NotificationDetailsActivity#bindRelatedRecord() already uses),
     * also matches against that transaction's reference number, room type, and payment
     * status - so searching "GCash" or a booking reference finds the right update even
     * when neither word appears in the notification's own title/message text.
     */
    private boolean matchesSearch(Notification n) {
        if (searchQuery.isEmpty()) return true;
        if (n.getTitle() != null && n.getTitle().toLowerCase(Locale.US).contains(searchQuery)) return true;
        if (n.getMessage() != null && n.getMessage().toLowerCase(Locale.US).contains(searchQuery)) return true;

        String referenceId = n.getReferenceId();
        if (referenceId == null) return false;
        Booking linked = null;
        for (Booking b : repository.getBookings()) {
            if (referenceId.equals(b.getId())) {
                linked = b;
                break;
            }
        }
        if (linked == null) return false;

        return containsIgnoreCase(linked.getId(), searchQuery)
                || containsIgnoreCase(linked.getTransactionRef(), searchQuery)
                || linked.anyRoomTypeContains(searchQuery.toLowerCase(Locale.US))
                || containsIgnoreCase(BookingStatusPresenter.paymentStatusPillText(this, linked), searchQuery)
                || containsIgnoreCase(linked.getStatus(), searchQuery);
    }

    private boolean containsIgnoreCase(@Nullable String value, String query) {
        return value != null && value.toLowerCase(Locale.US).contains(query);
    }

    /** The active filter's plain display label (no unread-count suffix) - for the per-category empty-state message, not the dropdown itself. */
    private String currentFilterDisplayLabel() {
        for (int i = 0; i < NOTIF_FILTER_KEYS.length; i++) {
            if (NOTIF_FILTER_KEYS[i].equals(currentFilter)) return getString(NOTIF_FILTER_LABEL_RES[i]);
        }
        return currentFilter;
    }

    private void applyFilters() {
        notificationList.clear();
        for (Notification n : allNotifications) {
            // "System" groups both TYPE_SYSTEM and TYPE_ANNOUNCEMENT under one filter chip -
            // there's no separate Announcement chip, and an announcement is, from the
            // guest's point of view, exactly a system-level message (matches the
            // "System Announcements" category guests actually expect that chip to mean).
            boolean matchesFilter = "All".equals(currentFilter)
                    || (FILTER_UNREAD.equals(currentFilter) ? !n.isRead()
                        : Notification.TYPE_SYSTEM.equals(currentFilter)
                            ? (Notification.TYPE_SYSTEM.equals(n.getType()) || Notification.TYPE_ANNOUNCEMENT.equals(n.getType()))
                            : currentFilter.equals(n.getType()));
            if (matchesFilter && matchesSearch(n)) notificationList.add(n);
        }

        if (notificationList.isEmpty()) {
            rvNotifications.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            if (!searchQuery.isEmpty()) {
                // A search query with zero matches is a distinct case from "genuinely
                // no notifications exist" - the guest typed something specific, so the
                // message should point at refining the search/filter, not imply the
                // account has no notifications at all.
                if (emptyTitle != null) emptyTitle.setText(R.string.no_search_results_title);
                if (emptyDesc != null) emptyDesc.setText(R.string.no_search_results_desc);
            } else if (FILTER_UNREAD.equals(currentFilter)) {
                if (emptyTitle != null) emptyTitle.setText(R.string.no_unread_notifications_title);
                if (emptyDesc != null) emptyDesc.setText(R.string.no_unread_notifications_desc);
            } else if (!"All".equals(currentFilter)) {
                // A specific category filter (Booking/Reservation/Payment/CheckIn/
                // Promotions/System) with zero matches - a friendlier, more specific
                // message than the generic "no notifications yet" below, which would
                // otherwise wrongly imply the account has no notifications at all.
                String label = currentFilterDisplayLabel();
                if (emptyTitle != null) emptyTitle.setText(getString(R.string.no_category_notifications_title_format, label));
                if (emptyDesc != null) emptyDesc.setText(getString(R.string.no_category_notifications_desc_format, label));
            } else {
                if (emptyTitle != null) emptyTitle.setText(R.string.no_notifications_title);
                if (emptyDesc != null) emptyDesc.setText(R.string.no_notifications_desc);
            }
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            applySelectedNotificationHighlight();
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Highlights the notification passed via {@link #EXTRA_NOTIFICATION_ID} (e.g. from a
     * dashboard update item tap) and scrolls to it once on initial load - re-applied on every
     * refresh so the highlight survives, but the scroll only happens once so it doesn't yank
     * the guest's scroll position mid-use. Coexists with openPendingDetailIfAny()'s detail
     * dialog - the row is highlighted underneath while the dialog shows on top.
     */
    private void applySelectedNotificationHighlight() {
        if (selectedNotificationId == null) return;
        adapter.setHighlightedNotificationId(selectedNotificationId);
        if (!pendingScrollToSelected) return;
        int position = -1;
        for (int i = 0; i < notificationList.size(); i++) {
            if (selectedNotificationId.equals(notificationList.get(i).getId())) {
                position = i;
                break;
            }
        }
        if (position >= 0) {
            final int scrollPosition = position;
            rvNotifications.post(() -> rvNotifications.smoothScrollToPosition(scrollPosition));
            pendingScrollToSelected = false;
        }
    }

    /**
     * When opened from the dashboard's "Recent Notifications" section with a specific
     * notification id, auto-opens just that notification's detail dialog on top of the
     * list instead of making the guest find it themselves - fires once per launch.
     */
    private void openPendingDetailIfAny() {
        if (pendingDetailId == null) return;
        String targetId = pendingDetailId;
        pendingDetailId = null;
        for (Notification n : allNotifications) {
            if (targetId.equals(n.getId())) {
                showNotificationDetails(n);
                setReadStateOptimistic(n, true);
                break;
            }
        }
    }
}

