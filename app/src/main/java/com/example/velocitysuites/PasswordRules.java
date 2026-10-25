package com.example.velocitysuites;

import androidx.annotation.NonNull;

/**
 * The sign-up password rule, as plain code (no Android) so it is unit tested with the same examples as the server: at least 8
 * characters, with at least one letter and one number (the backend's {@code Password::min(8)->letters()->numbers()}), and the
 * confirmation must match. The checklist on the sign-up screen and the strength bar read from here.
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 8;

    private PasswordRules() {
    }

    public static boolean hasMinLength(@NonNull CharSequence p) {
        return p.length() >= MIN_LENGTH;
    }

    public static boolean hasLetter(@NonNull CharSequence p) {
        for (int i = 0; i < p.length(); i++) if (Character.isLetter(p.charAt(i))) return true;
        return false;
    }

    public static boolean hasNumber(@NonNull CharSequence p) {
        for (int i = 0; i < p.length(); i++) if (Character.isDigit(p.charAt(i))) return true;
        return false;
    }

    /** True when the password satisfies the whole rule. */
    public static boolean isValid(@NonNull CharSequence p) {
        return hasMinLength(p) && hasLetter(p) && hasNumber(p);
    }

    public static boolean matches(@NonNull CharSequence password, @NonNull CharSequence confirm) {
        return password.length() > 0 && password.toString().equals(confirm.toString());
    }

    /**
     * 0 (nothing typed) to 4: one point each for the length, a letter and a number, and one more for a longer password (12+) that mixes
     * upper and lower case or adds a symbol. A password that does not meet the rule never scores above 2.
     */
    public static int strength(@NonNull CharSequence p) {
        if (p.length() == 0) return 0;
        int score = 0;
        if (hasMinLength(p)) score++;
        if (hasLetter(p)) score++;
        if (hasNumber(p)) score++;
        boolean upper = false, lower = false, symbol = false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (!Character.isLetterOrDigit(c)) symbol = true;
        }
        if (isValid(p) && ((p.length() >= 12 && upper && lower) || symbol || (upper && lower && p.length() >= 10))) score++;
        if (!isValid(p)) score = Math.min(score, 2);
        return Math.max(1, Math.min(4, score));
    }
}
