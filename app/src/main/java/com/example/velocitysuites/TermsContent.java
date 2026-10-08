package com.example.velocitysuites;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.regex.Pattern;

/**
 * The single Terms and Policy document shown on every screen that asks for agreement: account terms, the
 * 48-hour advance check-in policy, hotel policies, and privacy/data handling (RA 10173). The text itself lives
 * in strings.xml; this only assembles it and gives the part and section headings a readable style.
 */
public final class TermsContent {

    /** A short numbered line without sentence punctuation ("3. Check-out") is a section heading; long numbered sentences are body text. */
    private static final Pattern SUBHEADING = Pattern.compile("^\\d+\\.\\s[^.!?:]{1,60}$");

    private TermsContent() {
    }

    public static boolean isSubheading(String line) {
        return line != null && SUBHEADING.matcher(line.trim()).matches();
    }

    /** @param addendum optional transaction-specific text (e.g. the GCash/Cash payment note) appended at the end. */
    public static CharSequence build(Context context, @Nullable CharSequence addendum) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        int accent = ContextCompat.getColor(context, R.color.velocity_red_primary);
        appendPart(out, context.getString(R.string.terms_part_account), context.getString(R.string.terms_and_agreement_body), accent);
        appendPart(out, context.getString(R.string.terms_part_advance), context.getString(R.string.terms_advance_body), accent);
        appendPart(out, context.getString(R.string.terms_part_hotel), context.getString(R.string.hotel_terms_policy_body_general), accent);
        appendPart(out, context.getString(R.string.terms_part_privacy), context.getString(R.string.terms_privacy_body), accent);
        if (addendum != null && addendum.length() > 0) {
            out.append(addendum);
        }
        return out;
    }

    private static void appendPart(SpannableStringBuilder out, String heading, String body, int accent) {
        int start = out.length();
        out.append(heading).append("\n\n");
        int end = start + heading.length();
        out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(1.2f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(accent), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        for (String line : body.split("\n", -1)) {
            int lineStart = out.length();
            out.append(line).append("\n");
            if (isSubheading(line)) {
                out.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineStart + line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        out.append("\n");
    }
}
