package com.example.velocitysuites;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs file / bitmap work off the main thread and hands the result back on it - only if the screen is still alive.
 * {@code work} that throws delivers {@code null}, so a caller always gets exactly one callback and never a crash.
 */
final class BackgroundWork {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private BackgroundWork() {
    }

    static <T> void run(@NonNull Activity activity, @NonNull Callable<T> work, @NonNull Consumer<T> onDone) {
        IO.execute(() -> {
            T result = null;
            try {
                result = work.call();
            } catch (Exception | OutOfMemoryError ignored) {
                // delivered as null
            }
            final T delivered = result;
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) onDone.accept(delivered);
            });
        });
    }

    /** Convenience for work with no result. */
    static void run(@NonNull Activity activity, @NonNull Runnable work, @Nullable Runnable onDone) {
        run(activity, () -> {
            work.run();
            return Boolean.TRUE;
        }, ok -> {
            if (onDone != null) onDone.run();
        });
    }
}
