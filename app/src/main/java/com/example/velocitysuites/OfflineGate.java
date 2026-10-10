package com.example.velocitysuites;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Keeps a screen that is useless without internet (Landing) from being used offline: a full-screen "No internet
 * connection" state with Retry is laid over it whenever the phone has no validated connection - including when the
 * screen is reopened from the back stack after the connection dropped - and lifts by itself the moment a working
 * connection returns. Retry makes a real reachability request to the backend.
 *
 * Call start() from onStart() and stop() from onStop(); that also registers/unregisters the NetworkCallback.
 */
final class OfflineGate {

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> retryFuture;
    private View gate;
    private ConnectivityManager.NetworkCallback callback;

    OfflineGate(Activity activity) {
        this.activity = activity;
    }

    void start() {
        if (gate == null) {
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) return;
            gate = LayoutInflater.from(activity).inflate(R.layout.view_offline_gate, content, false);
            content.addView(gate, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            gate.findViewById(R.id.btnOfflineGateRetry).setOnClickListener(v -> retry());
        }
        refresh();

        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Activity.CONNECTIVITY_SERVICE);
        if (cm == null || callback != null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                main.post(OfflineGate.this::refresh);
            }

            @Override
            public void onLost(Network network) {
                main.post(OfflineGate.this::refresh);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                main.post(OfflineGate.this::refresh);
            }
        };
        try {
            cm.registerNetworkCallback(new NetworkRequest.Builder().build(), callback);
        } catch (RuntimeException e) {
            callback = null;
        }
    }

    void stop() {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Activity.CONNECTIVITY_SERVICE);
        if (cm != null && callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
        }
        callback = null;
        if (retryFuture != null) retryFuture.cancel(true);
        main.removeCallbacksAndMessages(null);
    }

    void destroy() {
        stop();
        executor.shutdownNow();
    }

    /** True while the "no internet" state is covering the screen. */
    boolean isBlocking() {
        return gate != null && gate.getVisibility() == View.VISIBLE;
    }

    private void refresh() {
        if (gate == null || activity.isFinishing()) return;
        gate.setVisibility(NetworkUtils.isOnline(activity) ? View.GONE : View.VISIBLE);
    }

    /** Retry: the phone must report a validated connection AND the backend must really answer. */
    private void retry() {
        if (retryFuture != null) retryFuture.cancel(true);
        final boolean phoneSaysOnline = NetworkUtils.isOnline(activity);
        final int hint = NetworkUtils.downstreamKbps(activity);
        retryFuture = executor.submit(() -> {
            ConnectionProbe.Result r = ConnectionProbe.run(ConnectionProbe.HEALTH_URL, hint, phoneSaysOnline);
            if (Thread.currentThread().isInterrupted()) return;
            main.post(() -> {
                if (gate == null || activity.isFinishing()) return;
                gate.setVisibility(r.quality == ConnectionProbe.Quality.OFFLINE ? View.VISIBLE : View.GONE);
            });
        });
    }
}
