package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The optional "Representative name" of a booking or reservation: the person who checks in on the guest's behalf.
 *
 * <p><b>API contract (unchanged):</b> the server stores the stay guest as {@code guest_first_name} / {@code guest_last_name}
 * (+ optional middle) and the app has always sent both. A representative's full name is split into those two fields
 * ("Juan Dela Cruz" is first "Juan Dela", last "Cruz", which the server composes back to the same text). With NO representative
 * the account owner's own first and last name are sent, so the server keeps getting the fields it expects and the stay guest is
 * the account owner. A stored name equal to the account owner's therefore reads as "no representative" everywhere.
 */
public final class RepresentativeName {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 60;
    /** Letters (incl. accented), spaces, periods, hyphens and apostrophes only; starts with a letter. */
    private static final Pattern ALLOWED = Pattern.compile("^\\p{L}[\\p{L} .'\\-\u2019]*$");

    private RepresentativeName() {
    }

    /** Trimmed, inner whitespace collapsed; "" for null. */
    @NonNull
    public static String clean(@Nullable String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** Empty is always valid (no representative). Filled: 2-60 characters, allowed characters only, and a first AND last name. */
    public static boolean isValid(@Nullable String raw) {
        String s = clean(raw);
        if (s.isEmpty()) return true;
        if (s.length() < MIN_LENGTH || s.length() > MAX_LENGTH) return false;
        if (!ALLOWED.matcher(s).matches()) return false;
        return s.indexOf(' ') > 0 && s.substring(s.indexOf(' ')).replaceAll("[ .'\\-\u2019]", "").length() > 0;
    }

    /** {first, last}: everything before the last word, and the last word. Null when {@code full} is blank. */
    @Nullable
    public static String[] split(@Nullable String full) {
        String s = clean(full);
        if (s.isEmpty()) return null;
        int cut = s.lastIndexOf(' ');
        if (cut < 0) return new String[]{s, ""};
        return new String[]{s.substring(0, cut).trim(), s.substring(cut + 1).trim()};
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("VelocityPrefs", Context.MODE_PRIVATE);
    }

    /** The account owner's {first, last} name as signed in. */
    @NonNull
    public static String[] owner(@NonNull Context c) {
        SharedPreferences p = prefs(c);
        return new String[]{clean(p.getString("userFirstName", "")), clean(p.getString("userLastName", ""))};
    }

    /**
     * The {first, last} to send: the representative's name, or the account owner's when the field was left blank.
     */
    @NonNull
    public static String[] namesToSend(@NonNull Context c, @Nullable String representative) {
        String[] rep = split(representative);
        return rep != null ? rep : owner(c);
    }

    /** True when the stored stay-guest name is the account owner (or there is none): nobody else will check in. */
    public static boolean isOwner(@NonNull Context c, @NonNull Booking b) {
        return representativeOf(c, b) == null;
    }

    /** The representative's full name, or null when the stay guest is the account owner (or unknown). */
    @Nullable
    public static String representativeOf(@NonNull Context c, @NonNull Booking b) {
        return representativeOf(c, b.getRepresentativeName());
    }

    /** Same, for a stored stay-guest name from any source (a receipt, for one). */
    @Nullable
    public static String representativeOf(@NonNull Context c, @Nullable String storedName) {
        String stored = clean(storedName);
        if (stored.isEmpty()) return null;
        String[] owner = owner(c);
        String ownerFull = clean(owner[0] + " " + owner[1]);
        if (!ownerFull.isEmpty() && stored.equalsIgnoreCase(ownerFull)) return null;
        String fullName = clean(prefs(c).getString("userName", ""));
        if (!fullName.isEmpty() && stored.toLowerCase(Locale.ROOT).equals(fullName.toLowerCase(Locale.ROOT))) return null;
        return stored;
    }

    /** What a wizard's field should show again when a stored transaction is edited or booked again. */
    @NonNull
    public static String fieldValueFor(@NonNull Context c, @NonNull Booking b) {
        String rep = representativeOf(c, b);
        return rep == null ? "" : rep;
    }

    /** "Representative: Juan Dela Cruz — must present 1 valid ID" or "Check-in by: account owner — present 1 valid ID". */
    @NonNull
    public static String summaryLine(@NonNull Context c, @Nullable String representative) {
        String rep = clean(representative);
        return rep.isEmpty() ? c.getString(R.string.check_in_by_owner_line) : c.getString(R.string.representative_line_format, rep);
    }

    /** The same line for a stored transaction. */
    @NonNull
    public static String summaryLine(@NonNull Context c, @NonNull Booking b) {
        return summaryLine(c, representativeOf(c, b));
    }

    /** The details-row label for a stored transaction: "Representative" or "Check-in by". */
    @NonNull
    public static String rowLabel(@NonNull Context c, @NonNull Booking b) {
        return c.getString(representativeOf(c, b) == null ? R.string.check_in_by_label : R.string.representative_label);
    }

    /** The details-row value: "Juan Dela Cruz — must present 1 valid ID" or "Account owner — present 1 valid ID". */
    @NonNull
    public static String rowValue(@NonNull Context c, @NonNull Booking b) {
        String rep = representativeOf(c, b);
        return rep == null ? c.getString(R.string.check_in_by_owner_value) : c.getString(R.string.representative_value_format, rep);
    }
}
