package com.example.velocitysuites.network;

import android.util.Log;

import com.example.velocitysuites.BuildConfig;

import java.util.UUID;

/**
 * Debug-build-only structured logging for the Booking/Reservation transaction-visibility
 * flow (Login -> auth/session -> create -> backend -> DB -> retrieve -> API response ->
 * Android model -> adapter -> list). Every call is a no-op in a release build (BuildConfig.DEBUG
 * guard, same convention RoomRepository#logRoomsShapeIfDebug() already uses) - this exists to
 * make a failure OBSERVABLE during testing, never to change behavior.
 * <p>
 * Never pass a token, password, OTP, or full payment/GCash credential to any method here -
 * only structural/correlation data (ids, counts, status codes, booleans).
 */
public final class DiagnosticLog {

    private static final String TAG = "VSDiagnostic";

    private DiagnosticLog() {}

    /** New short correlation id for one logical operation, e.g. newRequestId("BOOKINGS_LOAD") -> "BOOKINGS_LOAD_a1b2c3d4". Sent to the backend as the X-Request-Id header (see ApiService) so a failed mobile request can be matched to its server-side log line. */
    public static String newRequestId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static void d(String event, String detail) {
        if (!BuildConfig.DEBUG) return;
        Log.d(TAG, detail == null || detail.isEmpty() ? event : event + " | " + detail);
    }

    public static void w(String event, String detail) {
        if (!BuildConfig.DEBUG) return;
        Log.w(TAG, detail == null || detail.isEmpty() ? event : event + " | " + detail);
    }

    /** For a caught exception that must NOT crash the app (e.g. a single malformed record failing to map) - logs the full stack trace but always lets the caller continue/degrade gracefully. */
    public static void e(String event, String detail, Throwable t) {
        if (!BuildConfig.DEBUG) return;
        Log.e(TAG, detail == null || detail.isEmpty() ? event : event + " | " + detail, t);
    }
}
