package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

/**
 * How complete the guest's profile is: the share of six profile fields that are filled in - name, email, mobile number,
 * gender, date of birth and a real profile photo (the same six the Dashboard has always counted, moved here so the donut,
 * the account status and any other completeness text can never disagree). A field still holding the app's placeholder
 * ("Guest User", the sample email / mobile) counts as missing. Pure rules, so they are unit-testable.
 */
final class ProfileCompleteness {

    /** The profile fields, in the order they are suggested. */
    enum Field {
        NAME(R.string.profile_missing_name),
        EMAIL(R.string.profile_missing_email),
        MOBILE(R.string.pc_profile_missing_mobile),
        GENDER(R.string.pc_profile_missing_gender),
        BIRTH_DATE(R.string.profile_missing_birth_date),
        PHOTO(R.string.profile_missing_photo);

        @StringRes final int phraseRes;

        Field(int phraseRes) {
            this.phraseRes = phraseRes;
        }
    }

    final int percent;
    final List<Field> missing;

    private ProfileCompleteness(int percent, List<Field> missing) {
        this.percent = percent;
        this.missing = missing;
    }

    /** True when {@code value} is present and is not the app's stand-in for "nothing entered". */
    private static boolean filled(@Nullable String value, @Nullable String placeholder) {
        if (value == null || value.trim().isEmpty()) return false;
        return placeholder == null || !value.trim().equals(placeholder.trim());
    }

    @NonNull
    static ProfileCompleteness of(@Nullable String name, @Nullable String email, @Nullable String mobile, @Nullable String gender,
                                  @Nullable String birthDate, @Nullable String pictureUrl,
                                  @Nullable String namePlaceholder, @Nullable String emailPlaceholder, @Nullable String mobilePlaceholder) {
        List<Field> missing = new ArrayList<>();
        if (!filled(name, namePlaceholder)) missing.add(Field.NAME);
        if (!filled(email, emailPlaceholder)) missing.add(Field.EMAIL);
        if (!filled(mobile, mobilePlaceholder)) missing.add(Field.MOBILE);
        if (!filled(gender, null)) missing.add(Field.GENDER);
        if (!filled(birthDate, null)) missing.add(Field.BIRTH_DATE);
        if (!filled(pictureUrl, null)) missing.add(Field.PHOTO);
        int total = Field.values().length;
        return new ProfileCompleteness(((total - missing.size()) * 100) / total, missing);
    }

    /** "Add your phone number to complete your profile" - at most two items named, then "+N more"; null when complete. */
    @Nullable
    String missingLine(@NonNull Context context) {
        if (missing.isEmpty()) return null;
        String first = context.getString(missing.get(0).phraseRes);
        String list;
        if (missing.size() == 1) list = first;
        else if (missing.size() == 2) list = context.getString(R.string.profile_missing_two, first, context.getString(missing.get(1).phraseRes));
        else list = context.getResources().getQuantityString(R.plurals.profile_missing_more, missing.size() - 2, first,
                    context.getString(missing.get(1).phraseRes), missing.size() - 2);
        return context.getString(R.string.profile_missing_line, list);
    }
}
