package com.example.velocitysuites;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The optional representative of a booking or reservation: the person who checks in on the guest's behalf, as THREE names -
 * last name, first name, middle name (optional).
 *
 * <p><b>The whole block is optional.</b> All three empty means no representative (the account owner checks in). If ANY field is
 * filled, last name and first name become required; middle name always stays optional.
 *
 * <p><b>API contract (unchanged):</b> the booking and reservation APIs take {@code guest_first_name}, {@code guest_middle_name}
 * (only sent when not empty) and {@code guest_last_name}; each name goes in its own field. With NO representative the account owner's
 * own first and last name are sent in those fields (no middle name), exactly as before, so a stored stay guest equal to the account
 * owner reads as "no representative" everywhere.
 */
public final class RepresentativeName {

    public static final int NAME_MAX = 40;
    public static final int FIRST_LAST_MIN = 2;
    public static final int MIDDLE_MIN = 1;
    /** Letters (including ñ and accented ones), spaces, periods, hyphens and apostrophes; starts with a letter. */
    private static final Pattern ALLOWED = Pattern.compile("^\\p{L}[\\p{L} .'\\-\u2019]*$");

    /** What is wrong with one field. */
    public enum Problem { NONE, REQUIRED, TOO_SHORT, TOO_LONG, LETTERS_ONLY }

    /** The three names as typed (cleaned of stray spaces). */
    public static final class Parts {
        @NonNull public final String last;
        @NonNull public final String first;
        @NonNull public final String middle;

        public Parts(@Nullable String last, @Nullable String first, @Nullable String middle) {
            this.last = clean(last);
            this.first = clean(first);
            this.middle = clean(middle);
        }

        public boolean isBlank() {
            return last.isEmpty() && first.isEmpty() && middle.isEmpty();
        }

        /** "Dela Cruz, Juan Santos" / "Dela Cruz, Juan"; "" when blank. */
        @NonNull
        public String formatted() {
            if (isBlank()) return "";
            StringBuilder sb = new StringBuilder(last);
            String given = (first + (middle.isEmpty() ? "" : " " + middle)).trim();
            if (!given.isEmpty()) sb.append(sb.length() > 0 ? ", " : "").append(given);
            return sb.toString();
        }
    }

    /** The result of checking the block. */
    public static final class Result {
        @NonNull public final Problem last;
        @NonNull public final Problem first;
        @NonNull public final Problem middle;

        Result(@NonNull Problem last, @NonNull Problem first, @NonNull Problem middle) {
            this.last = last;
            this.first = first;
            this.middle = middle;
        }

        public boolean isValid() {
            return last == Problem.NONE && first == Problem.NONE && middle == Problem.NONE;
        }
    }

    private RepresentativeName() {
    }

    /** Trimmed, inner whitespace collapsed; "" for null. */
    @NonNull
    public static String clean(@Nullable String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** Empty block is valid; otherwise last and first are required and every filled field must be a plausible name. */
    @NonNull
    public static Result validate(@NonNull Parts p) {
        if (p.isBlank()) return new Result(Problem.NONE, Problem.NONE, Problem.NONE);
        return new Result(check(p.last, true, FIRST_LAST_MIN), check(p.first, true, FIRST_LAST_MIN), check(p.middle, false, MIDDLE_MIN));
    }

    private static Problem check(String value, boolean required, int min) {
        if (value.isEmpty()) return required ? Problem.REQUIRED : Problem.NONE;
        if (!ALLOWED.matcher(value).matches()) return Problem.LETTERS_ONLY;
        if (value.length() < min) return Problem.TOO_SHORT;
        if (value.length() > NAME_MAX) return Problem.TOO_LONG;
        return Problem.NONE;
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
     * The {first, middle, last} to send to the API: the representative's names (middle may be ""), or the account owner's first and
     * last name (no middle) when the block is empty.
     */
    @NonNull
    public static String[] namesToSend(@NonNull Context c, @NonNull Parts p) {
        if (p.isBlank()) {
            String[] owner = owner(c);
            return new String[]{owner[0], "", owner[1]};
        }
        return new String[]{p.first, p.middle, p.last};
    }

    /** The stored stay guest of a transaction as the block's three fields, or null when it is the account owner (or unknown). */
    @Nullable
    public static Parts partsOf(@NonNull Context c, @NonNull Booking b) {
        String first = clean(b.getGuestFirstName());
        String last = clean(b.getGuestLastName());
        String middle = clean(b.getGuestMiddleName());
        if (first.isEmpty() && last.isEmpty()) return null;
        String[] owner = owner(c);
        boolean ownerSame = !owner[0].isEmpty() && first.equalsIgnoreCase(owner[0]) && last.equalsIgnoreCase(owner[1]) && middle.isEmpty();
        String stored = clean(first + " " + middle + " " + last);
        String fullName = clean(prefs(c).getString("userName", ""));
        if (ownerSame || (!fullName.isEmpty() && stored.equalsIgnoreCase(fullName))) return null;
        return new Parts(last, first, middle);
    }

    /** The stay guest of a transaction in the app's order, "Last, First Middle" - the account owner included ("" when no name was captured). */
    @NonNull
    public static String stayGuestFormatted(@NonNull Booking b) {
        return new Parts(b.getGuestLastName(), b.getGuestFirstName(), b.getGuestMiddleName()).formatted();
    }

    /** What the wizard's three fields show when a stored transaction is edited or booked again (all empty for the account owner). */
    @NonNull
    public static Parts fieldsFor(@NonNull Context c, @NonNull Booking b) {
        Parts p = partsOf(c, b);
        return p != null ? p : new Parts("", "", "");
    }

    /** "Dela Cruz, Juan Santos", or null for the account owner - the plain name for a receipt or billing row. */
    @Nullable
    public static String representativeOf(@NonNull Context c, @NonNull Booking b) {
        Parts p = partsOf(c, b);
        return p == null ? null : p.formatted();
    }

    /** Same, for a stored name that only exists as one composed string ("First Middle Last", from a receipt). */
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

    /** "Representative: Dela Cruz, Juan Santos — must present 1 valid ID" or "Check-in by: account owner — present 1 valid ID". */
    @NonNull
    public static String summaryLine(@NonNull Context c, @Nullable Parts p) {
        return p == null || p.isBlank() ? c.getString(R.string.check_in_by_owner_line) : c.getString(R.string.representative_line_format, p.formatted());
    }

    @NonNull
    public static String summaryLine(@NonNull Context c, @NonNull Booking b) {
        return summaryLine(c, partsOf(c, b));
    }

    /** The details-row label for a stored transaction: "Representative" or "Check-in by". */
    @NonNull
    public static String rowLabel(@NonNull Context c, @NonNull Booking b) {
        return c.getString(partsOf(c, b) == null ? R.string.check_in_by_label : R.string.representative_label);
    }

    /** The details-row value: "Dela Cruz, Juan — must present 1 valid ID" or "Account owner — present 1 valid ID". */
    @NonNull
    public static String rowValue(@NonNull Context c, @NonNull Booking b) {
        Parts p = partsOf(c, b);
        return p == null ? c.getString(R.string.check_in_by_owner_value) : c.getString(R.string.representative_value_format, p.formatted());
    }
}
