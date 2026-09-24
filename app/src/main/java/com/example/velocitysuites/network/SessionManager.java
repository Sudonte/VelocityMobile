package com.example.velocitysuites.network;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Wraps the same "VelocityPrefs" SharedPreferences the rest of the app
 * already reads user fields (name/email/etc.) from, so existing screens
 * (header greeting, dashboard account summary) keep working unchanged once
 * real login/registration populate it.
 *
 * The auth token and Remember Me state below live in a SEPARATE prefs file
 * ("VelocitySecurePrefs") instead, and that file is explicitly excluded from
 * both Android cloud backup and device-to-device transfer (see
 * res/xml/backup_rules.xml and res/xml/data_extraction_rules.xml) - a
 * restored/transferred copy of this app must never come back from a fresh
 * install already carrying a working bearer token. "VelocityPrefs" itself
 * (name/email/theme/etc.) stays backup-eligible on purpose - none of that is
 * a credential, and restoring it is a normal, desirable convenience.
 */
public final class SessionManager {

    private static final String PREFS_NAME = "VelocityPrefs";
    /** Never included in backup/device-transfer - see class doc. */
    private static final String SECURE_PREFS_NAME = "VelocitySecurePrefs";
    private static final String KEY_TOKEN = "apiToken";

    /**
     * Remember Me: a separate opt-in flag from the plain session token above.
     * The token alone means "authenticated for this process"; these three
     * keys mean "skip login.xml on a fresh cold start too, but only for up
     * to 24 hours from the login that set them." Kept as three plain keys
     * (not one bundled object) since that's the existing convention every
     * other field in this same prefs file already follows.
     */
    private static final String KEY_REMEMBER_ME = "rememberMeEnabled";
    private static final String KEY_REMEMBER_ME_LOGIN_AT = "rememberMeLoginAt";
    private static final String KEY_REMEMBER_ME_EXPIRES_AT = "rememberMeExpiresAt";
    private static final long REMEMBER_ME_DURATION_MS = 24L * 60 * 60 * 1000;

    private SessionManager() {}

    public static String getToken(Context context) {
        return securePrefs(context).getString(KEY_TOKEN, null);
    }

    public static boolean isLoggedIn(Context context) {
        return getToken(context) != null;
    }

    public static void saveSession(Context context, String token, long userId, String firstName, String lastName,
                                    String middleName, String fullName, String email, String mobile,
                                    String gender, String dob, String profilePictureUrl) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putLong("userId", userId);
        editor.putString("userName", fullName);
        editor.putString("userFirstName", firstName);
        editor.putString("userLastName", lastName != null ? lastName : "");
        editor.putString("userMiddleName", middleName != null ? middleName : "");
        editor.putString("userEmail", email);
        if (mobile != null) editor.putString("userMobile", mobile);
        if (gender != null) editor.putString("userGender", gender);
        if (dob != null) editor.putString("userDob", dob);
        editor.putString("profilePictureUrl", profilePictureUrl);
        editor.apply();

        // Every fresh login/registration starts with Remember Me off - a
        // caller with the checkbox (LoginActivity) opts back in explicitly
        // via setRememberMe() right after this call. Without this reset, a
        // stale remembered session from a previous account could otherwise
        // survive into a brand-new login for a different guest.
        securePrefs(context).edit()
                .putString(KEY_TOKEN, token)
                .putBoolean(KEY_REMEMBER_ME, false)
                .remove(KEY_REMEMBER_ME_LOGIN_AT)
                .remove(KEY_REMEMBER_ME_EXPIRES_AT)
                .apply();
    }

    /**
     * Call once, immediately after saveSession(), with whatever the Remember
     * Me checkbox was set to. Recording false is not a no-op: it explicitly
     * clears any Remember Me state saveSession() itself already reset, so
     * this is safe to call unconditionally rather than only on the true path.
     */
    public static void setRememberMe(Context context, boolean remembered) {
        if (remembered) {
            long now = System.currentTimeMillis();
            securePrefs(context).edit()
                    .putBoolean(KEY_REMEMBER_ME, true)
                    .putLong(KEY_REMEMBER_ME_LOGIN_AT, now)
                    .putLong(KEY_REMEMBER_ME_EXPIRES_AT, now + REMEMBER_ME_DURATION_MS)
                    .apply();
        } else {
            clearRememberMe(context);
        }
    }

    /**
     * Pure, Context-free decision this whole class exists to make for
     * Remember Me - separated out purely so it's JVM-unit-testable (see
     * SessionManagerTest). Mirrors PaymentStatusResolver's own
     * resolveStatusKey()/resolve() split for the same reason.
     */
    static boolean isRememberMeStillValid(boolean rememberMeEnabled, long expiresAtMillis, long nowMillis, boolean hasToken) {
        return rememberMeEnabled && nowMillis < expiresAtMillis && hasToken;
    }

    /**
     * True only while a Remember Me session exists LOCALLY and its 24-hour
     * window hasn't elapsed. An expired session is cleared as a side effect
     * of checking it, so no separate cleanup step is needed and an expired
     * session can never be silently reused.
     *
     * This is deliberately NOT sufficient by itself to grant entry to the
     * app - it only proves "this device thinks it was remembered," never
     * that the token is still valid/authorized server-side (revoked,
     * password-changed, or restored via backup/device-transfer onto a
     * device that never actually held a live session). Callers that skip
     * the login form on this alone (see LoginActivity) MUST still validate
     * the token against the backend before entering the dashboard - see
     * LoginActivity#onCreate()'s own doc.
     */
    public static boolean hasValidRememberedSession(Context context) {
        SharedPreferences securePreferences = securePrefs(context);
        boolean stillValid = isRememberMeStillValid(
                securePreferences.getBoolean(KEY_REMEMBER_ME, false),
                securePreferences.getLong(KEY_REMEMBER_ME_EXPIRES_AT, 0L),
                System.currentTimeMillis(),
                getToken(context) != null);
        if (!stillValid) {
            clearRememberMe(context);
            return false;
        }
        return true;
    }

    public static void clearRememberMe(Context context) {
        securePrefs(context).edit()
                .putBoolean(KEY_REMEMBER_ME, false)
                .remove(KEY_REMEMBER_ME_LOGIN_AT)
                .remove(KEY_REMEMBER_ME_EXPIRES_AT)
                .apply();
    }

    public static void setProfilePictureUrl(Context context, String url) {
        prefs(context).edit().putString("profilePictureUrl", url).apply();
    }

    public static String getProfilePictureUrl(Context context) {
        return prefs(context).getString("profilePictureUrl", null);
    }

    /** Full logout - clears both the ordinary account-info prefs and the secure token/Remember Me prefs. */
    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
        securePrefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Never included in Android backup or device-to-device transfer - see class doc. */
    private static SharedPreferences securePrefs(Context context) {
        return context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
