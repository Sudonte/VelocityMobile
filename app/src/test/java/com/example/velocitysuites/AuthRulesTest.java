package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

/**
 * The sign-up rules as plain code, with the same examples as the server's RegistrationRulesTest: a password needs 8+ characters, a
 * letter and a number (and its confirmation must match); the account holder must be 18 or older, judged from the date of birth.
 */
public class AuthRulesTest {

    @Test
    public void password_needsEightCharacters_aLetter_andANumber() {
        assertTrue(PasswordRules.isValid("abcdefg1"));
        assertTrue(PasswordRules.isValid("Str0ngPass!"));
        assertFalse("too short", PasswordRules.isValid("abc12"));
        assertFalse("no number", PasswordRules.isValid("abcdefgh"));
        assertFalse("no letter", PasswordRules.isValid("12345678"));
        assertFalse("seven characters", PasswordRules.isValid("abcdef1"));
        assertFalse(PasswordRules.isValid(""));
    }

    @Test
    public void eachRule_isReportedOnItsOwn_forTheChecklist() {
        assertTrue(PasswordRules.hasMinLength("12345678"));
        assertFalse(PasswordRules.hasMinLength("1234567"));
        assertTrue(PasswordRules.hasLetter("1234567a"));
        assertFalse(PasswordRules.hasLetter("12345678"));
        assertTrue(PasswordRules.hasNumber("abcdefg1"));
        assertFalse(PasswordRules.hasNumber("abcdefgh"));
    }

    @Test
    public void confirmation_mustMatchExactly() {
        assertTrue(PasswordRules.matches("abcdefg1", "abcdefg1"));
        assertFalse(PasswordRules.matches("abcdefg1", "abcdefg2"));
        assertFalse(PasswordRules.matches("abcdefg1", "Abcdefg1"));
        assertFalse("nothing typed is not a match", PasswordRules.matches("", ""));
    }

    @Test
    public void strength_grows_withLengthAndVariety_andNeverRatesAWeakPasswordHigh() {
        assertEquals(0, PasswordRules.strength(""));
        assertTrue(PasswordRules.strength("abc") <= 2);
        assertTrue("fails the rule", PasswordRules.strength("abcdefghij") <= 2);
        assertEquals(3, PasswordRules.strength("abcdefg1"));
        assertEquals(4, PasswordRules.strength("Str0ngPass!"));
        assertTrue(PasswordRules.strength("Str0ngPass!") > PasswordRules.strength("abcdefg1"));
    }

    @Test
    public void age_comesFromTheDateOfBirth_andEighteenIsTheLine() {
        LocalDate today = LocalDate.of(2026, 10, 25);
        assertEquals(23, AgeRules.ageOn(LocalDate.of(2003, 3, 1), today));
        assertEquals("the day before the birthday", 22, AgeRules.ageOn(LocalDate.of(2003, 10, 26), today));
        assertEquals("on the birthday", 23, AgeRules.ageOn(LocalDate.of(2003, 10, 25), today));
        assertTrue("turned 18 today", AgeRules.isAdult(LocalDate.of(2008, 10, 25), today));
        assertFalse("turns 18 tomorrow", AgeRules.isAdult(LocalDate.of(2008, 10, 26), today));
        assertFalse(AgeRules.isAdult(LocalDate.of(2012, 1, 1), today));
        assertEquals(LocalDate.of(2008, 10, 25), AgeRules.latestAllowedBirthDate(today));
        assertEquals("a future date is never negative", 0, AgeRules.ageOn(LocalDate.of(2030, 1, 1), today));
    }
}
