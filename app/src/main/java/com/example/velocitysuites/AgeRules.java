package com.example.velocitysuites;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.Period;

/** The sign-up age rule as plain code: the age comes only from the date of birth, and the account holder must be 18 or older. */
public final class AgeRules {

    public static final int MIN_AGE = 18;

    private AgeRules() {
    }

    /** Whole years between {@code birth} and {@code today} (never negative). */
    public static int ageOn(@NonNull LocalDate birth, @NonNull LocalDate today) {
        return Math.max(0, Period.between(birth, today).getYears());
    }

    public static boolean isAdult(@NonNull LocalDate birth, @NonNull LocalDate today) {
        return ageOn(birth, today) >= MIN_AGE;
    }

    /** The latest date of birth that is still 18 or older today (the date picker's upper limit). */
    @NonNull
    public static LocalDate latestAllowedBirthDate(@NonNull LocalDate today) {
        return today.minusYears(MIN_AGE);
    }
}
