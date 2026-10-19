package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * How raw server values are SHOWN. The raw values ("CONFIRMED", "Checked-In", "GCASH") stay as they are for comparisons and
 * logic; every screen that displays one goes through here and gets it in Title Case / sentence case: "Confirmed",
 * "Checked in", "GCash", "Cash".
 */
public final class DisplayLabels {

    private DisplayLabels() {
    }

    /** "CONFIRMED" -> "Confirmed", "Checked-In" -> "Checked in", "Checked_Out" -> "Checked out", "NO-SHOW" -> "No-show"; "" for null. */
    @NonNull
    public static String status(@Nullable String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.equals("no-show") || lower.equals("no_show") || lower.equals("no show") || lower.equals("noshow")) return "No-show";
        lower = lower.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ");
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** "GCASH" / "gcash" -> "GCash", "CASH" -> "Cash", anything else in sentence case; "" for null or blank. */
    @NonNull
    public static String paymentMethod(@NonNull Context context, @Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String s = raw.trim();
        if ("gcash".equalsIgnoreCase(s)) return context.getString(R.string.payment_method_gcash);
        if ("cash".equalsIgnoreCase(s)) return context.getString(R.string.payment_method_cash);
        return status(s);
    }
}
