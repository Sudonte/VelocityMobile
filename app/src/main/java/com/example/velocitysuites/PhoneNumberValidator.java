package com.example.velocitysuites;

import android.content.Context;
import android.text.TextUtils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Single source of truth for mobile-number validation on Android, mirroring the backend's
 * PhoneValidationService exactly: Philippines keeps this app's own long-standing exact-shape
 * rule (09XXXXXXXXX or +639XXXXXXXXX) rather than Google's libphonenumber, which - confirmed via
 * direct backend testing against giggsey/libphonenumber-for-php - is measurably more permissive
 * than that (it accepts numbers like 08171234567 or +6309171234567 that this app has always
 * deliberately rejected). Every other supported country genuinely had no real per-country
 * validation before (just the same generic 7-15-digit regex regardless of country) - those now
 * use the real library, replacing hundreds-of-regexes territory with actual numbering-plan data.
 * Used by both RegistrationActivity and ProfileManagementActivity so the two never disagree.
 */
public final class PhoneNumberValidator {

    private PhoneNumberValidator() {
    }

    private static final Pattern PH_PATTERN = Pattern.compile("^(09|\\+639|639)\\d{9}$");
    private static final Pattern FALLBACK_PATTERN = Pattern.compile("^\\+?\\d{7,15}$");

    /** Every named country except Philippines (kept above) and "Other" (no fixed region -
     *  falls back to the generic pattern below). */
    private static final Map<String, String> COUNTRY_TO_REGION = new HashMap<>();

    static {
        COUNTRY_TO_REGION.put("United States", "US");
        COUNTRY_TO_REGION.put("Canada", "CA");
        COUNTRY_TO_REGION.put("United Kingdom", "GB");
        COUNTRY_TO_REGION.put("Australia", "AU");
        COUNTRY_TO_REGION.put("Singapore", "SG");
        COUNTRY_TO_REGION.put("Malaysia", "MY");
        COUNTRY_TO_REGION.put("Japan", "JP");
        COUNTRY_TO_REGION.put("South Korea", "KR");
        COUNTRY_TO_REGION.put("United Arab Emirates", "AE");
        COUNTRY_TO_REGION.put("Saudi Arabia", "SA");
        COUNTRY_TO_REGION.put("Qatar", "QA");
        COUNTRY_TO_REGION.put("Hong Kong", "HK");
        COUNTRY_TO_REGION.put("New Zealand", "NZ");
    }

    public static boolean isValid(String country, String number) {
        if (TextUtils.isEmpty(number)) return false;
        String trimmed = number.trim();

        if ("Philippines".equalsIgnoreCase(country)) {
            return PH_PATTERN.matcher(trimmed).matches();
        }

        String region = COUNTRY_TO_REGION.get(country);
        if (region == null) {
            return FALLBACK_PATTERN.matcher(trimmed).matches();
        }

        try {
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            return util.isValidNumberForRegion(util.parse(trimmed, region), region);
        } catch (NumberParseException e) {
            return false;
        }
    }

    /** User-facing message matching the backend's PhoneValidationService wording exactly. */
    public static String errorMessage(Context context, String country) {
        if ("Philippines".equalsIgnoreCase(country)) {
            return context.getString(R.string.mobile_invalid_ph);
        }
        if (COUNTRY_TO_REGION.containsKey(country)) {
            return context.getString(R.string.mobile_invalid_for_country);
        }
        return context.getString(R.string.mobile_required_intl);
    }

    /** Calling code to show as a live hint as the guest picks a country - reuses the same
     *  COUNTRY_TO_REGION map validation already relies on, rather than a second hardcoded table. */
    public static String callingCodeHint(String country) {
        if ("Philippines".equalsIgnoreCase(country)) {
            return "+63";
        }
        String region = COUNTRY_TO_REGION.get(country);
        if (region == null) {
            return "+";
        }
        int code = PhoneNumberUtil.getInstance().getCountryCodeForRegion(region);
        return code > 0 ? "+" + code : "+";
    }
}
