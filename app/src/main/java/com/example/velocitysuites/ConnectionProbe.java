package com.example.velocitysuites;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

/**
 * A real reachability + speed check of the Velocity Suites backend. Being connected to Wi-Fi proves nothing, so the
 * start-up gate asks the server itself: one tiny request to the health endpoint, timing how long the first byte takes
 * (latency) and how long the whole small body takes (download). The result is classified as FAST, SLOW or OFFLINE.
 * The OS's own bandwidth estimate is only a hint (it is often wrong).
 *
 * measure() blocks - call it off the main thread.
 */
public final class ConnectionProbe {

    public enum Quality { FAST, SLOW, OFFLINE }

    /** The outcome of one probe. */
    public static final class Result {
        public final Quality quality;
        public final long latencyMs;
        public final long totalMs;

        Result(Quality quality, long latencyMs, long totalMs) {
            this.quality = quality;
            this.latencyMs = latencyMs;
            this.totalMs = totalMs;
        }
    }

    /** Laravel's built-in health route: a tiny page, no database, no login. */
    public static final String HEALTH_URL = "https://velocitysuites.com/up";

    static final int QUICK_TIMEOUT_MS = 4_000;
    /** Used when the quick try timed out but the phone says it is online - a slow link gets a longer chance. */
    static final int PATIENT_TIMEOUT_MS = 12_000;

    static final long SLOW_LATENCY_MS = 1_200;
    static final long SLOW_TOTAL_MS = 2_500;
    /** The OS bandwidth estimate below which a merely middling timing still counts as slow. */
    static final int SLOW_HINT_KBPS = 800;
    static final long HINT_CONFIRM_TOTAL_MS = 1_200;
    private static final int MAX_BODY_BYTES = 16 * 1024;

    private ConnectionProbe() { }

    /** Pure classification, so the thresholds are testable without a network. */
    public static Quality classify(boolean reachable, long latencyMs, long totalMs, int downstreamKbpsHint) {
        if (!reachable) return Quality.OFFLINE;
        if (latencyMs > SLOW_LATENCY_MS || totalMs > SLOW_TOTAL_MS) return Quality.SLOW;
        if (downstreamKbpsHint > 0 && downstreamKbpsHint < SLOW_HINT_KBPS && totalMs > HINT_CONFIRM_TOTAL_MS) return Quality.SLOW;
        return Quality.FAST;
    }

    /**
     * Probes the backend. A quick try first; if it only TIMED OUT while the phone reports a validated connection,
     * one patient retry decides between a very slow link (SLOW) and nothing getting through (OFFLINE).
     */
    public static Result run(String url, int downstreamKbpsHint, boolean phoneSaysOnline) {
        Attempt quick = attempt(url, QUICK_TIMEOUT_MS);
        if (quick.reachable) {
            return new Result(classify(true, quick.latencyMs, quick.totalMs, downstreamKbpsHint), quick.latencyMs, quick.totalMs);
        }
        if (quick.timedOut && phoneSaysOnline && !Thread.currentThread().isInterrupted()) {
            Attempt patient = attempt(url, PATIENT_TIMEOUT_MS);
            if (patient.reachable) {
                long total = Math.max(patient.totalMs, QUICK_TIMEOUT_MS);
                return new Result(Quality.SLOW, Math.max(patient.latencyMs, QUICK_TIMEOUT_MS), total);
            }
        }
        return new Result(Quality.OFFLINE, 0, 0);
    }

    private static final class Attempt {
        boolean reachable;
        boolean timedOut;
        long latencyMs;
        long totalMs;
    }

    private static Attempt attempt(String url, int timeoutMs) {
        Attempt out = new Attempt();
        HttpURLConnection connection = null;
        try {
            long start = System.nanoTime();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            int code = connection.getResponseCode();
            out.latencyMs = (System.nanoTime() - start) / 1_000_000L;
            // Any HTTP answer proves the server was reached; only a 2xx/3xx means it is healthy.
            if (code < 200 || code >= 400) return out;

            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = in.read(buffer)) != -1 && total < MAX_BODY_BYTES) {
                    total += read;
                }
            }
            out.totalMs = (System.nanoTime() - start) / 1_000_000L;
            out.reachable = true;
        } catch (SocketTimeoutException e) {
            out.timedOut = true;
        } catch (IOException e) {
            // DNS failure, refused, reset, no route: nothing got through.
        } finally {
            if (connection != null) connection.disconnect();
        }
        return out;
    }
}
