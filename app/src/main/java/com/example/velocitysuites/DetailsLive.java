package com.example.velocitysuites;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The "Last updated" row of a transaction details screen: when the data was last confirmed with the server, a refresh button, and -
 * when an update fails - "Couldn't update. Showing info from 2:10 PM" with Try again. The screen keeps showing its last data; this
 * only tells the guest how fresh it is. Also decides whether a transaction is still worth polling.
 */
public final class DetailsLive {

    /** The details screens re-read the transaction this often while visible. */
    public static final long POLL_INTERVAL_MS = 20_000L;

    private final View root;
    private final TextView lastUpdated;
    private final View errorRow;
    private final TextView errorText;
    private long lastOkMillis;

    private DetailsLive(View root, Runnable onRefresh) {
        this.root = root;
        lastUpdated = root.findViewById(R.id.tvLastUpdated);
        errorRow = root.findViewById(R.id.rowUpdateError);
        errorText = root.findViewById(R.id.tvUpdateError);
        View.OnClickListener click = v -> onRefresh.run();
        root.findViewById(R.id.btnDetailsRefresh).setOnClickListener(click);
        root.findViewById(R.id.btnUpdateRetry).setOnClickListener(click);
        markUpdated(System.currentTimeMillis());
    }

    /** The scrolling page inside a SwipeRefreshLayout (its first child is the spinner, not the page). */
    @NonNull
    public static androidx.core.widget.NestedScrollView scrollerOf(@NonNull androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh) {
        for (int i = 0; i < refresh.getChildCount(); i++) {
            View v = refresh.getChildAt(i);
            if (v instanceof androidx.core.widget.NestedScrollView) return (androidx.core.widget.NestedScrollView) v;
        }
        throw new IllegalStateException("no NestedScrollView in the refresh layout");
    }

    /** The column inside that page, where the "Last updated" row goes. */
    @NonNull
    public static ViewGroup contentOf(@NonNull androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh) {
        return (ViewGroup) scrollerOf(refresh).getChildAt(0);
    }

    /** Adds the row as the first child of {@code content}. */
    @NonNull
    public static DetailsLive attach(@NonNull Context c, @NonNull ViewGroup content, @NonNull Runnable onRefresh) {
        View row = LayoutInflater.from(c).inflate(R.layout.view_details_live_status, content, false);
        content.addView(row, 0);
        return new DetailsLive(row, onRefresh);
    }

    public void markUpdated(long nowMillis) {
        lastOkMillis = nowMillis;
        errorRow.setVisibility(View.GONE);
        lastUpdated.setText(root.getContext().getString(R.string.details_last_updated_format, time(nowMillis)));
    }

    public void markFailed() {
        errorText.setText(root.getContext().getString(R.string.details_update_failed_format, time(lastOkMillis)));
        errorRow.setVisibility(View.VISIBLE);
    }

    public boolean isShowingError() {
        return errorRow.getVisibility() == View.VISIBLE;
    }

    public long lastOkMillis() {
        return lastOkMillis;
    }

    /** "2:15 PM" in hotel time. */
    @NonNull
    public static String time(long millis) {
        return java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)
                .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("Asia/Manila")));
    }

    /**
     * True while something can still change: false once the stay is checked out or completed, or the transaction was cancelled,
     * rejected or failed - polling then stops.
     */
    public static boolean isActive(@Nullable Booking b) {
        if (b == null) return false;
        String s = b.getStatus() == null ? "" : b.getStatus();
        return !("Checked-Out".equalsIgnoreCase(s) || "Completed".equalsIgnoreCase(s) || "Cancelled".equalsIgnoreCase(s)
                || "Rejected".equalsIgnoreCase(s) || b.isHistoricalReservation());
    }
}
