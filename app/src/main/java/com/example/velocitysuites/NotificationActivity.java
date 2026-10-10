package com.example.velocitysuites;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Notifications screen: compact cards (see item_notification.xml / {@link NotificationAdapter}) with a
 * per-notification read/unread toggle, "View Transaction" for the ones about a booking/reservation/payment,
 * search, a category/unread filter and "Mark all as read".
 * <p>
 * The header's bell badge and the "N unread" / "You're all caught up" summary are repainted from the repository's
 * single unread count on the same beats, so they always agree - and "all caught up" is only ever said when the
 * count is actually known to be zero (never while the first load is still running or has failed).
 */
public class NotificationActivity extends BaseNavigationActivity {

    /** Optional: when set, only this notification's detail screen is auto-opened on load. */
    public static final String EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID";
    private static final String KEY_CURRENT_FILTER = "KEY_CURRENT_FILTER";
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

    private static final long SEARCH_DEBOUNCE_MS = 300;

    // Silent polling refresh, matching the website's 30s auto-refresh on its own notifications page - keeps
    // the list current without the guest needing to pull-to-refresh manually. The timer itself is the shared
    // visible-only one in BaseNavigationActivity (see onVisiblePoll() below).

    // Separate, more frequent timer purely for re-rendering the relative "5m ago" text on already-visible
    // rows - nothing to do with fetching (see onVisiblePoll()), so the two concerns can't interfere.
    private static final long TIME_TEXT_REFRESH_MS = 60000;
    private final Handler timeTextRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeTextRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (rvNotifications.getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager lm = (LinearLayoutManager) rvNotifications.getLayoutManager();
                int first = lm.findFirstVisibleItemPosition();
                int last = lm.findLastVisibleItemPosition();
                if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                    adapter.refreshVisibleTimestamps(first, last);
                }
            }
            timeTextRefreshHandler.postDelayed(this, TIME_TEXT_REFRESH_MS);
        }
    };

    private RecyclerView rvNotifications;
    private View scrollEmptyState;
    private View layoutEmptyState;
    private View layoutInitialLoading;
    private TextView emptyTitle, emptyDesc;
    /** "3 unread notifications" / "You're all caught up" under the sticky header's title - repainted from updateNotificationBadge(), so it changes on exactly the same beats as the header bell badge. */
    private TextView tvUnreadSummary;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearchNotifications;
    private com.google.android.material.textfield.TextInputLayout layoutNotificationStatusFilter;
    private com.google.android.material.textfield.MaterialAutoCompleteTextView dropdownNotificationStatus;
    private NotificationFilterDropdownAdapter filterDropdownAdapter;
    private NotificationAdapter adapter;
    private RoomRepository repository;
    private List<Notification> allNotifications = new ArrayList<>();
    private List<Notification> notificationList = new ArrayList<>();
    private String searchQuery = "";
    private String currentFilter = "All";
    /** Notification id to auto-open the detail screen for, from the dashboard's per-notification deep link. */
    private String pendingDetailId;
    /** Same id as pendingDetailId, but kept around (not cleared after first use) to keep the row highlighted/scrolled-to across refreshes. */
    private String selectedNotificationId;
    private boolean pendingScrollToSelected;
    /** Guards RecyclerView's scroll-near-bottom trigger against firing a second loadMoreNotifications() while one is already in flight. */
    private boolean loadingMoreNotifications = false;
    /** True until this Activity instance's first onResume() has run - onCreate() already performs the initial full load, so that very first onResume must not poll again; every SUBSEQUENT onResume (returning from another screen) does. */
    private boolean isFirstResume = true;
    /** At most one full refresh and one poll in flight at a time - a second tap/tick while one is running would only duplicate the request. */
    private boolean refreshInFlight = false;
    private boolean pollInFlight = false;
    /** The first load failed and nothing has loaded since - the header then says so instead of "all caught up". */
    private boolean initialLoadFailed = false;

    private final ClickGuard clickGuard = new ClickGuard();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable applyFiltersRunnable = this::applyFilters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notification);
        setupGuestNavigation(View.NO_ID);
        animateScreenContent();

        repository = RoomRepository.getInstance(this);

        rvNotifications = findViewById(R.id.rvNotifications);
        scrollEmptyState = findViewById(R.id.scrollEmptyState);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        layoutInitialLoading = findViewById(R.id.layoutInitialLoading);
        tvUnreadSummary = findViewById(R.id.tvUnreadSummary);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        etSearchNotifications = findViewById(R.id.etSearchNotifications);
        layoutNotificationStatusFilter = findViewById(R.id.layoutNotificationStatusFilter);
        dropdownNotificationStatus = findViewById(R.id.dropdownNotificationStatus);
        pendingDetailId = getIntent().getStringExtra(EXTRA_NOTIFICATION_ID);
        selectedNotificationId = pendingDetailId;
        pendingScrollToSelected = selectedNotificationId != null;

        // The empty state is a shared include (view_empty_state.xml) whose title/description are populated in
        // code - without this it renders as a blank card with just an icon. Fields (not locals) since
        // applyFilters() updates the text dynamically.
        emptyTitle = layoutEmptyState.findViewById(R.id.emptyTitle);
        emptyDesc = layoutEmptyState.findViewById(R.id.emptyDesc);
        ImageView emptyIcon = layoutEmptyState.findViewById(R.id.emptyIcon);
        if (emptyTitle != null) emptyTitle.setText(R.string.no_notifications_title);
        if (emptyDesc != null) emptyDesc.setText(R.string.no_notifications_desc);
        if (emptyIcon != null) emptyIcon.setImageResource(R.drawable.ic_notifications);

        setupRecyclerView();
        setupSwipeRefresh();
        setupSearchAndFilters();
        // Restores the previously selected filter if this Activity is being recreated after process death -
        // currentFilter is a plain field, not part of any View's own state.
        if (savedInstanceState != null) {
            String restoredFilter = savedInstanceState.getString(KEY_CURRENT_FILTER);
            if (restoredFilter != null) {
                currentFilter = restoredFilter;
                selectDropdownFilter(restoredFilter);
            }
        }

        View btnRefreshEmpty = findViewById(R.id.btnEmptyAction);
        if (btnRefreshEmpty != null) {
            btnRefreshEmpty.setVisibility(View.VISIBLE);
            if (btnRefreshEmpty instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) btnRefreshEmpty).setText(R.string.refresh_label);
                ((com.google.android.material.button.MaterialButton) btnRefreshEmpty).setIconResource(R.drawable.ic_clock);
            }
            btnRefreshEmpty.setOnClickListener(v -> {
                if (clickGuard.tryAcquire()) loadNotifications(true);
            });
        }

        View btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
        btnMarkAllRead.setOnClickListener(v -> {
            if (clickGuard.tryAcquire()) confirmMarkAllRead(btnMarkAllRead);
        });

        // Stale-while-revalidate: anything already in the shared cache (the dashboard usually loaded it) is
        // painted immediately, then refreshed behind it.
        if (repository.hasLoadedNotifications()) {
            allNotifications = repository.getNotifications();
            updateFilterLabelsWithCounts();
            applyFilters();
        }
        updateNotificationBadge();
        loadNotifications(true);
    }

    /**
     * Marking every notification read is one-way (there's no bulk "mark unread"), so confirm before
     * applying it - the same confirm-dialog convention used for other one-way actions across the app.
     */
    private void confirmMarkAllRead(View triggerButton) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.confirm_mark_all_read_msg)
                .setPositiveButton(R.string.confirm_dialog_positive, (d, w) -> {
                    // Disabled for the duration of the request so a repeated tap right after this dialog's
                    // auto-dismiss can't fire a second concurrent mark-all-read call.
                    triggerButton.setEnabled(false);

                    // The repository flips every currently-unread row (and the unread total) before
                    // returning - remembering exactly which rows, so a failed request reverts precisely
                    // those - so this repaint already shows the optimistic result; the callback repaints
                    // again with the final one (identical on success, reverted on failure).
                    repository.markAllNotificationsAsRead(success -> {
                        triggerButton.setEnabled(true);
                        if (!isUiAlive()) return;
                        syncFromRepository();
                        Toast.makeText(this, success ? R.string.msg_mark_all_read : R.string.network_error, Toast.LENGTH_SHORT).show();
                    });
                    syncFromRepository();
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Skip on the very first onResume (the one that always immediately follows onCreate) - onCreate()'s
        // own loadNotifications(true) already just did the real initial load. Every later onResume (e.g.
        // returning from a transaction) checks for changes instead - never a full reload, so it can never
        // re-download the whole loaded window or disturb the guest's filter/scroll position.
        if (isFirstResume) {
            isFirstResume = false;
        } else {
            pollForNewNotifications();
        }
        timeTextRefreshHandler.removeCallbacks(timeTextRefreshRunnable);
        timeTextRefreshHandler.postDelayed(timeTextRefreshRunnable, TIME_TEXT_REFRESH_MS);
    }

    /**
     * The 30s beat (started/stopped with the screen by BaseNavigationActivity): a lightweight check for changes
     * (see pollForNewNotifications()) rather than a full loadNotifications() reload - it fires every 30s for as
     * long as the guest stays here, so re-fetching however large the loaded window has grown would mean
     * repeatedly re-downloading the guest's entire history just to check for one new row. It also repaints the
     * header badge, so it replaces the base class's badge-only default.
     */
    @Override
    protected void onVisiblePoll() {
        pollForNewNotifications();
    }

    /** Pairs with onCreate()'s savedInstanceState restore above. */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT_FILTER, currentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        timeTextRefreshHandler.removeCallbacks(timeTextRefreshRunnable);
    }

    @Override
    protected void onDestroy() {
        // Nothing may fire against a screen that is gone: timers, the pending debounced search.
        timeTextRefreshHandler.removeCallbacksAndMessages(null);
        searchHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean isUiAlive() {
        return !isFinishing() && !isDestroyed();
    }

    /**
     * Sets the combo box's own displayed text/icon and the dropdown popup's checkmark to match filterKey,
     * WITHOUT re-triggering applyFilters() - used wherever the filter is reflected programmatically (the
     * savedInstanceState restore) rather than by the guest tapping a dropdown row (that path already calls
     * applyFilters() itself, see setupSearchAndFilters()'s OnItemClickListener).
     */
    private void selectDropdownFilter(String filterKey) {
        if (dropdownNotificationStatus == null) return;
        for (int i = 0; i < NOTIF_FILTER_KEYS.length; i++) {
            if (NOTIF_FILTER_KEYS[i].equals(filterKey)) {
                dropdownNotificationStatus.setText(getString(NOTIF_FILTER_LABEL_RES[i]), false);
                break;
            }
        }
        updateDropdownStartIcon(filterKey);
        if (filterDropdownAdapter != null) {
            filterDropdownAdapter.setSelectedKey(filterKey);
        }
    }

    /** The combo box shows the selected filter's own category icon at its start when closed - kept in sync with whichever row is actually selected. */
    private void updateDropdownStartIcon(String filterKey) {
        if (layoutNotificationStatusFilter == null) return;
        NotificationCategoryPresenter.Result category = NotificationCategoryPresenter.resolve(filterKey);
        layoutNotificationStatusFilter.setStartIconDrawable(category.iconRes);
        layoutNotificationStatusFilter.setStartIconTintList(android.content.res.ColorStateList.valueOf(getColor(category.fgColorRes)));
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvNotifications.setLayoutManager(layoutManager);
        // Lazy-load the next page (the API already paginates) once the guest scrolls within 5 rows of the
        // end. Reachable now that the RecyclerView is the screen's real scroll container.
        rvNotifications.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingMoreNotifications || !repository.hasMoreNotifications()) return;
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (lastVisible >= notificationList.size() - 5) {
                    loadingMoreNotifications = true;
                    repository.loadMoreNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
                        @Override
                        public void onSuccess(List<Notification> result) {
                            loadingMoreNotifications = false;
                            if (!isUiAlive()) return;
                            allNotifications = result != null ? result : allNotifications;
                            updateFilterLabelsWithCounts();
                            applyFilters();
                        }

                        @Override
                        public void onError(String message) {
                            // A failed "load more" leaves the already-visible page intact - no error state
                            // takeover, just a quiet toast; the guest can scroll away and back, or pull to refresh.
                            loadingMoreNotifications = false;
                            if (isUiAlive()) {
                                Toast.makeText(NotificationActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });
        adapter = new NotificationAdapter(this, notificationList, new NotificationAdapter.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(Notification notification) {
                if (!clickGuard.tryAcquire()) return;
                showNotificationDetails(notification);
                markReadIfUnread(notification);
            }

            @Override
            public void onToggleReadClick(Notification notification) {
                // The explicit "Mark as read"/"Mark as unread" button - the only read-state change that asks
                // first (opening a notification marking it read is implicit and expected).
                if (!clickGuard.tryAcquire()) return;
                confirmReadStateChange(notification);
            }

            @Override
            public void onViewTransactionDetailsClick(Notification notification) {
                if (!clickGuard.tryAcquire()) return;
                openTransaction(notification);
            }
        });
        rvNotifications.setAdapter(adapter);
    }

    private void showNotificationDetails(Notification notification) {
        startActivity(NotificationDetailsActivity.newIntent(this, notification));
    }

    /**
     * "View Transaction": opens the EXACT reservation/booking the notification refers to (see
     * TransactionNavigator - straight away when it is loaded, else after fetching it by id). A transaction that
     * no longer exists gets a friendly message; a failed lookup says why and can simply be tapped again.
     * Marks the notification read too, same as opening its own detail screen would - the guest has now seen
     * and acted on this update either way.
     */
    private void openTransaction(Notification notification) {
        markReadIfUnread(notification);
        TransactionNavigator.openFromNotification(this, notification, new TransactionNavigator.Listener() {
            @Override
            public void onLookupStarted() {
                swipeRefresh.setRefreshing(true); // the record is being fetched - show that something is happening
            }

            @Override
            public void onLookupFinished() {
                if (isUiAlive() && !refreshInFlight) swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onNotFound() {
                TransactionNavigator.showNotFoundDialog(NotificationActivity.this);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(NotificationActivity.this, getString(R.string.txn_lookup_error_format, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * The explicit per-notification "Mark as read"/"Mark as unread" flow: ask first, then change it, then
     * say so. Cancel (or tapping outside the dialog) leaves everything exactly as it was.
     * <p>
     * The target state is decided HERE, from the state the guest actually saw when they tapped, and captured
     * for the confirm callback - not re-derived on confirm, since a background poll can flip the row
     * underneath an open dialog and "Mark this notification as read?" must never end up doing the opposite.
     */
    private void confirmReadStateChange(Notification notification) {
        final String notificationId = notification.getId();
        final boolean markRead = !notification.isRead();
        new MaterialAlertDialogBuilder(this)
                .setMessage(markRead ? R.string.confirm_mark_read_msg : R.string.confirm_mark_unread_msg)
                .setPositiveButton(R.string.confirm_button_label, (d, w) -> changeReadState(notificationId, markRead, true))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    /** The implicit half of read-state changes - opening a notification (or its transaction) marks it read, with no dialog and no success message. Skipped for an already-read row, so re-opening one never fires a pointless request. */
    private void markReadIfUnread(Notification notification) {
        if (!notification.isRead()) {
            changeReadState(notification.getId(), true, false);
        }
    }

    /**
     * Single entry point for every read-state-changing action on this screen. RoomRepository applies the
     * change to its cache immediately - the row, the header bell badge, the filter counts and the unread
     * summary all repaint from that in the very same beat, before any network round-trip - then persists it
     * and reports back: on failure the change has already been reverted in the cache, so the repaint below
     * simply shows the truth again.
     *
     * @param announceSuccess true only for the explicit button, which owes the guest a confirmation message;
     *                        an implicit change stays silent unless it fails.
     */
    private void changeReadState(String notificationId, boolean markRead, boolean announceSuccess) {
        repository.setNotificationReadState(notificationId, markRead, success -> {
            if (isUiAlive()) syncFromRepository();
            if (success) {
                if (announceSuccess) {
                    Toast.makeText(getApplicationContext(),
                            markRead ? R.string.msg_notification_marked_read : R.string.msg_notification_marked_unread,
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.error_notification_update_failed, Toast.LENGTH_LONG).show();
            }
        });
        syncFromRepository();
    }

    /**
     * Re-reads the notification list from RoomRepository's cache and repaints everything that depends on it:
     * the badge, the filter counts, the unread summary, and the list itself (DiffUtil inside
     * NotificationAdapter#submitList() rebinds only the rows whose content actually changed). Reading from
     * the cache rather than mutating this screen's own copy means a row a background poll has replaced with a
     * fresh object since it was last bound can never be left showing stale state.
     */
    private void syncFromRepository() {
        allNotifications = repository.getNotifications();
        updateNotificationBadge();
        updateFilterLabelsWithCounts();
        applyFilters();
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.velocity_red_primary);
        // The list is wrapped in a FrameLayout (with the empty/loading states), so tell the layout whether the
        // LIST can still scroll up - otherwise pulling down mid-list would start a refresh.
        swipeRefresh.setOnChildScrollUpCallback((parent, child) ->
                rvNotifications.getVisibility() == View.VISIBLE && rvNotifications.canScrollVertically(-1));
        // The pull gesture already shows the spinner itself, so this listener doesn't need to turn it on
        // again - loadNotifications() turns it off once done.
        swipeRefresh.setOnRefreshListener(() -> loadNotifications(false));
    }

    /**
     * This screen keeps its own data fresh (this class's loadNotifications/pollForNewNotifications, from
     * onCreate/onResume/swipe-refresh/the 30s auto-poll), so the shared header's own badge-only refetch
     * (BaseNavigationActivity#refreshNotificationBadge()) would just be a second, redundant network call
     * every time this screen resumes - suppressed here.
     */
    @Override
    protected void refreshNotificationBadge() {
        updateNotificationBadge();
    }

    /**
     * Repaints the header bell badge (super) AND this screen's own "N unread" summary under the sticky header -
     * hooked here because every place that already updates the badge (each load, poll, load-more, and every
     * read/unread change via syncFromRepository()) is by definition also a place the summary has to change,
     * so the two can never drift apart.
     */
    @Override
    protected void updateNotificationBadge() {
        super.updateNotificationBadge();
        if (tvUnreadSummary == null || repository == null) return;
        if (!repository.hasLoadedNotifications()) {
            // Unknown is not zero: before the first successful load (or after a failed one) the count can't be
            // claimed to be 0, so the summary must not say "You're all caught up".
            tvUnreadSummary.setText(initialLoadFailed ? R.string.notif_unread_unavailable : R.string.notif_unread_checking);
            return;
        }
        int unread = repository.getUnreadNotificationCount();
        tvUnreadSummary.setText(unread > 0
                ? getResources().getQuantityString(R.plurals.notif_unread_summary, unread, unread)
                : getString(R.string.notif_all_caught_up));
    }

    /**
     * @param showLoadingIndicator whether to show the pull-to-refresh spinner while this fetch is in flight.
     *                             True for the initial load and any explicit user-triggered refresh; a pull
     *                             already shows its own spinner (false).
     */
    private void loadNotifications(boolean showLoadingIndicator) {
        if (refreshInFlight) return; // the in-flight one stops the spinner when it lands
        refreshInFlight = true;
        // The centered first-load indicator, not the swipe spinner - only for a genuine "nothing shown yet".
        boolean isInitialLoad = showLoadingIndicator && allNotifications.isEmpty();
        if (isInitialLoad) {
            layoutInitialLoading.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
            scrollEmptyState.setVisibility(View.GONE);
        }
        if (showLoadingIndicator) swipeRefresh.setRefreshing(true);
        repository.refreshNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                refreshInFlight = false;
                if (!isUiAlive()) return;
                swipeRefresh.setRefreshing(false);
                layoutInitialLoading.setVisibility(View.GONE);
                initialLoadFailed = false;
                allNotifications = result != null ? result : new ArrayList<>();
                updateNotificationBadge();
                updateFilterLabelsWithCounts();
                applyFilters();
                openPendingDetailIfAny();
            }

            @Override
            public void onError(String message) {
                refreshInFlight = false;
                if (!isUiAlive()) return;
                swipeRefresh.setRefreshing(false);
                layoutInitialLoading.setVisibility(View.GONE);
                // A transient refresh/auto-poll failure with a list already on screen just gets a toast -
                // replacing a working list with a scary error screen over a momentary network blip would be
                // worse than doing nothing. Only the genuine "never successfully loaded anything yet" case
                // gets the full error state (the same empty-state view, retry button included).
                if (allNotifications.isEmpty()) {
                    initialLoadFailed = true;
                    updateNotificationBadge();
                    rvNotifications.setVisibility(View.GONE);
                    scrollEmptyState.setVisibility(View.VISIBLE);
                    if (emptyTitle != null) emptyTitle.setText(R.string.no_notifications_load_error_title);
                    if (emptyDesc != null) emptyDesc.setText(R.string.no_notifications_load_error_desc);
                } else {
                    Toast.makeText(NotificationActivity.this, getString(R.string.error_load_notifications_format, message), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * The 30s timer's own check - repository.pollNotifications() only fetches and merges the first page
     * rather than replacing the whole (possibly grown) list loadNotifications() would re-fetch in full. Fully
     * silent on failure: this is a background timer the guest never directly triggered, so a transient miss
     * should be invisible; the next tick (or any real action) tries again. One poll at a time.
     */
    private void pollForNewNotifications() {
        if (pollInFlight || refreshInFlight) return;
        pollInFlight = true;
        // Captured BEFORE the fetch, not after - "was the guest at the top" has to reflect where they were reading.
        boolean wasAtTop = isRecyclerViewAtTop();
        String previousTopId = allNotifications.isEmpty() ? null : allNotifications.get(0).getId();

        repository.pollNotifications(new RoomRepository.RepositoryCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                pollInFlight = false;
                if (!isUiAlive()) return;
                initialLoadFailed = false;
                allNotifications = result != null ? result : allNotifications;
                updateNotificationBadge();
                updateFilterLabelsWithCounts();
                applyFilters();

                // A genuinely NEW notification always lands at index 0 (see RoomRepository#mergeNotifications())
                // - if the top id changed, at least one arrived. Only scroll if the guest was already at the
                // top: they're reading the newest items, so surfacing the new one is helpful. If they'd
                // scrolled into older history, leave them exactly where they are.
                boolean newItemArrived = !allNotifications.isEmpty()
                        && !java.util.Objects.equals(allNotifications.get(0).getId(), previousTopId);
                if (wasAtTop && newItemArrived) {
                    rvNotifications.scrollToPosition(0);
                }
            }

            @Override
            public void onError(String message) {
                pollInFlight = false; // silent by design - see this method's own doc
            }
        });
    }

    /** True when the first item in the list is fully visible at (or above) the top of the viewport - "the guest is reading the newest items". */
    private boolean isRecyclerViewAtTop() {
        RecyclerView.LayoutManager lm = rvNotifications.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return true;
        return ((LinearLayoutManager) lm).findFirstVisibleItemPosition() <= 0;
    }

    private void setupSearchAndFilters() {
        if (etSearchNotifications != null) {
            etSearchNotifications.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override
                public void afterTextChanged(Editable s) {
                    searchQuery = s.toString().trim().toLowerCase(Locale.US);
                    // Debounced: filtering on every keystroke re-ran the whole list per letter.
                    searchHandler.removeCallbacks(applyFiltersRunnable);
                    searchHandler.postDelayed(applyFiltersRunnable, SEARCH_DEBOUNCE_MS);
                }
            });
            etSearchNotifications.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
                searchHandler.removeCallbacks(applyFiltersRunnable);
                applyFilters();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            });
        }
        if (dropdownNotificationStatus != null) {
            String[] labels = new String[NOTIF_FILTER_LABEL_RES.length];
            for (int i = 0; i < NOTIF_FILTER_LABEL_RES.length; i++) labels[i] = getString(NOTIF_FILTER_LABEL_RES[i]);
            filterDropdownAdapter = new NotificationFilterDropdownAdapter(this, labels.clone(), NOTIF_FILTER_KEYS);
            dropdownNotificationStatus.setAdapter(filterDropdownAdapter);
            filterDropdownAdapter.setSelectedKey(currentFilter);
            updateDropdownStartIcon(currentFilter);
            updateFilterLabelsWithCounts();
            dropdownNotificationStatus.setOnItemClickListener((parent, view, position, id) -> {
                currentFilter = NOTIF_FILTER_KEYS[position];
                // The CLOSED box always shows the plain label (no count suffix) - the popup rows are where counts live.
                dropdownNotificationStatus.setText(labels[position], false);
                updateDropdownStartIcon(currentFilter);
                filterDropdownAdapter.setSelectedKey(currentFilter);
                applyFilters();
            });
        }
    }

    /**
     * Rebuilds the dropdown popup's row labels with an unread-count suffix, e.g. "Booking (3)" - omitted (no
     * "(0)") for a filter with nothing unread. "All" and "Unread" both show the guest's real total unread
     * count (RoomRepository#getUnreadNotificationCount()); a category's own count is the loaded-window count,
     * since there's no backend endpoint for a true per-category unread total. Re-set on every successful load
     * and after every optimistic read/unread mutation so the counts never visibly lag what the rows show.
     */
    private void updateFilterLabelsWithCounts() {
        if (filterDropdownAdapter == null) return;

        int totalUnread = repository.getUnreadNotificationCount();
        Map<String, Integer> unreadByType = new HashMap<>();
        for (Notification n : allNotifications) {
            if (n.isRead()) continue;
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
        filterDropdownAdapter.updateLabels(labels);
    }

    /**
     * Every search word must match. A notification's own title/message/category/status come first (cheap,
     * always available); when it links to a Booking/Reservation still in RoomRepository's cache, that
     * transaction's reference, room type, payment status and booking status are searchable too - so
     * "GCash" or a booking reference finds the right update even when neither appears in its own text.
     *
     * @param bookingsById the cache indexed by id, built once per filter pass (not per notification)
     */
    private boolean matchesSearch(Notification n, String[] tokens, Map<String, List<Booking>> bookingsById) {
        if (tokens.length == 0) return true;

        StringBuilder haystack = new StringBuilder(128);
        append(haystack, n.getTitle());
        append(haystack, n.getMessage());
        append(haystack, n.getType());
        NotificationStatusResolver.Result status = NotificationStatusResolver.resolve(this, n);
        if (status != null) append(haystack, status.label);

        String referenceId = n.getReferenceId();
        if (referenceId != null && bookingsById.containsKey(referenceId)) {
            Booking linked = TransactionNavigator.pick(bookingsById.get(referenceId), referenceId, null, n.getType(), n.getReceiptNumber());
            if (linked != null) {
                append(haystack, linked.getId());
                append(haystack, linked.getTransactionRef());
                for (String type : linked.getAllRoomTypeNames()) append(haystack, type);
                append(haystack, getString(TransactionStatusHelper.styleFor(TransactionStatusHelper.statusOf(linked)).labelRes));
                append(haystack, linked.getStatus());
            }
        }

        String text = haystack.toString().toLowerCase(Locale.US);
        for (String token : tokens) {
            if (!text.contains(token)) return false;
        }
        return true;
    }

    private static void append(StringBuilder sb, @Nullable String part) {
        if (part != null && !part.isEmpty()) sb.append(part).append(' ');
    }

    /** The active filter's plain display label (no unread-count suffix) - for the per-category empty-state message, not the dropdown itself. */
    private String currentFilterDisplayLabel() {
        for (int i = 0; i < NOTIF_FILTER_KEYS.length; i++) {
            if (NOTIF_FILTER_KEYS[i].equals(currentFilter)) return getString(NOTIF_FILTER_LABEL_RES[i]);
        }
        return currentFilter;
    }

    private void applyFilters() {
        searchHandler.removeCallbacks(applyFiltersRunnable);
        String[] tokens = searchQuery.isEmpty() ? new String[0] : searchQuery.split("\\s+");

        // Index the cache by id once, only when a search needs it.
        Map<String, List<Booking>> bookingsById = new HashMap<>();
        if (tokens.length > 0) {
            for (Booking b : repository.getBookings()) {
                if (b.getId() != null) bookingsById.computeIfAbsent(b.getId(), k -> new ArrayList<>(2)).add(b);
            }
        }

        notificationList.clear();
        for (Notification n : allNotifications) {
            // "System" groups both TYPE_SYSTEM and TYPE_ANNOUNCEMENT under one filter option - there's no
            // separate Announcement option, and an announcement is, from the guest's point of view, exactly a
            // system-level message.
            boolean matchesFilter = "All".equals(currentFilter)
                    || (FILTER_UNREAD.equals(currentFilter) ? !n.isRead()
                        : Notification.TYPE_SYSTEM.equals(currentFilter)
                            ? (Notification.TYPE_SYSTEM.equals(n.getType()) || Notification.TYPE_ANNOUNCEMENT.equals(n.getType()))
                            : currentFilter.equals(n.getType()));
            if (matchesFilter && matchesSearch(n, tokens, bookingsById)) notificationList.add(n);
        }

        if (notificationList.isEmpty()) {
            // Before the first successful load the loader / error state owns the screen - don't paint a
            // misleading "no notifications" over it.
            if (repository.hasLoadedNotifications() || !allNotifications.isEmpty()) {
                rvNotifications.setVisibility(View.GONE);
                scrollEmptyState.setVisibility(View.VISIBLE);
                layoutInitialLoading.setVisibility(View.GONE);
                if (!searchQuery.isEmpty()) {
                    // A search with zero matches is a distinct case from "genuinely no notifications exist".
                    if (emptyTitle != null) emptyTitle.setText(R.string.no_search_results_title);
                    if (emptyDesc != null) emptyDesc.setText(R.string.no_search_results_desc);
                } else if (FILTER_UNREAD.equals(currentFilter)) {
                    if (emptyTitle != null) emptyTitle.setText(R.string.no_unread_notifications_title);
                    if (emptyDesc != null) emptyDesc.setText(R.string.no_unread_notifications_desc);
                } else if (!"All".equals(currentFilter)) {
                    // A specific category filter with zero matches - more specific than the generic message,
                    // which would otherwise wrongly imply the account has no notifications at all.
                    String label = currentFilterDisplayLabel();
                    if (emptyTitle != null) emptyTitle.setText(getString(R.string.no_category_notifications_title_format, label));
                    if (emptyDesc != null) emptyDesc.setText(getString(R.string.no_category_notifications_desc_format, label));
                } else {
                    if (emptyTitle != null) emptyTitle.setText(R.string.no_notifications_title);
                    if (emptyDesc != null) emptyDesc.setText(R.string.no_notifications_desc);
                }
            }
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            scrollEmptyState.setVisibility(View.GONE);
            layoutInitialLoading.setVisibility(View.GONE);
            applySelectedNotificationHighlight();
        }
        // submitList() diffs against what's currently shown and dispatches only the precise resulting
        // changes - see NotificationAdapter's own doc. A filter switch that leaves the same items visible in
        // the same order dispatches zero operations.
        adapter.submitList(notificationList);
    }

    /**
     * Highlights the notification passed via {@link #EXTRA_NOTIFICATION_ID} (e.g. from a dashboard update item
     * tap) and scrolls to it once on initial load - re-applied on every refresh so the highlight survives, but
     * the scroll only happens once so it doesn't yank the guest's scroll position mid-use.
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
            rvNotifications.post(() -> {
                if (isUiAlive()) rvNotifications.smoothScrollToPosition(scrollPosition);
            });
            pendingScrollToSelected = false;
        }
    }

    /**
     * When opened from the dashboard's "Recent Notifications" section with a specific notification id,
     * auto-opens just that notification's detail screen on top of the list instead of making the guest find
     * it themselves - fires once per launch.
     */
    private void openPendingDetailIfAny() {
        if (pendingDetailId == null) return;
        String targetId = pendingDetailId;
        pendingDetailId = null;
        for (Notification n : allNotifications) {
            if (targetId.equals(n.getId())) {
                showNotificationDetails(n);
                markReadIfUnread(n);
                break;
            }
        }
    }
}
