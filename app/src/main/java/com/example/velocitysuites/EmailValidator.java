package com.example.velocitysuites;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Sign-in, registration and password-reset accept any well-formed email address (not only Gmail): a local part, one
 * "@", and a dotted domain ending in a letters-only top-level domain. Plain Java (no android.util.Patterns), so it
 * runs in unit tests; the server does the authoritative check.
 */
final class EmailValidator {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9._%+'-]{0,62}[A-Za-z0-9_%+'-])?"
                    + "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?[.])+[A-Za-z]{2,24}$");

    private EmailValidator() { }

    static boolean isValid(@Nullable CharSequence value) {
        if (value == null) return false;
        String s = value.toString().trim();
        return s.length() <= 254 && !s.contains("..") && EMAIL.matcher(s).matches();
    }
}
