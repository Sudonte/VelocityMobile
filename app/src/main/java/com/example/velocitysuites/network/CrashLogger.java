package com.example.velocitysuites.network;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.example.velocitysuites.BuildConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Debug-build-only global uncaught-exception handler. This app previously had NO crash
 * reporter at all (confirmed by grep - zero matches for
 * Thread#setDefaultUncaughtExceptionHandler anywhere), so a real runtime crash left no
 * artifact either side of this codebase could ever retrieve - only a live Logcat session
 * happened to catch it, if anyone was watching at the exact moment. Installed once from
 * VelocitySuitesApp#onCreate(), debug builds only - a release build never installs this and
 * a guest never sees a stack trace; the platform's normal crash handling is untouched either
 * way (see uncaughtException() below - this always re-delivers to the previous handler after
 * writing the file, it never swallows or suppresses the crash).
 */
public final class CrashLogger {

    private static final String LOG_FILE_NAME = "vs_crash_log.txt";

    /** Updated by VelocitySuitesApp's ActivityLifecycleCallbacks - best-effort "what screen was on screen" context, not a hard guarantee (e.g. null during a very early startup crash). */
    private static volatile String currentActivityName = null;

    private CrashLogger() {}

    public static void setCurrentActivityName(String name) {
        currentActivityName = name;
    }

    public static void install(Context context) {
        if (!BuildConfig.DEBUG) return;

        Context appContext = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrashReport(appContext, thread, throwable);
            } catch (Throwable loggingFailure) {
                // Never let the crash logger itself be the reason the real crash report
                // is lost - fall straight through to the previous handler regardless.
                Log.e("VSDiagnostic", "CrashLogger itself failed", loggingFailure);
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private static void writeCrashReport(Context appContext, Thread thread, Throwable throwable) {
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));

        String versionName = "unknown";
        int versionCode = -1;
        try {
            PackageInfo info = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
            versionName = info.versionName != null ? info.versionName : "unknown";
            versionCode = info.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        String report = "===== VelocitySuites crash =====\n"
                + "timestamp: " + timestamp + "\n"
                + "app_version: " + versionName + " (code " + versionCode + ", git " + BuildConfig.GIT_COMMIT + ")\n"
                + "android_version: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "current_activity: " + (currentActivityName != null ? currentActivityName : "unknown") + "\n"
                + "thread: " + thread.getName() + "\n"
                + "exception: " + throwable.getClass().getName() + "\n"
                + "message: " + throwable.getMessage() + "\n"
                + "stack_trace:\n" + stackTrace + "\n";

        Log.e("VSDiagnostic", report);

        File logFile = new File(appContext.getFilesDir(), LOG_FILE_NAME);
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(report);
            writer.write("\n");
        } catch (IOException e) {
            Log.e("VSDiagnostic", "Failed to write crash log file", e);
        }
    }
}
