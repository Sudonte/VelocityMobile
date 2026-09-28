package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM coverage for PhoneNumberValidator - regression test for the Edit Profile
 * bug where the Philippines mobile mask ("+63 917 123 4567") produced spaced text
 * that isValid()'s PH_PATTERN (no separators allowed) always rejected, making it
 * impossible to ever save a changed PH mobile number. stripFormatting() must be
 * applied before isValid()/submission - this test locks in that contract.
 */
public class PhoneNumberValidatorTest {

    @Test
    public void maskedPhNumber_failsValidationWithoutStripping() {
        assertFalse(PhoneNumberValidator.isValid("Philippines", "+63 917 123 4567"));
    }

    @Test
    public void maskedPhNumber_passesValidationAfterStripping() {
        String masked = "+63 917 123 4567";
        assertTrue(PhoneNumberValidator.isValid("Philippines", PhoneNumberValidator.stripFormatting(masked)));
    }

    @Test
    public void stripFormatting_removesAllWhitespace() {
        assertEquals("+639171234567", PhoneNumberValidator.stripFormatting("+63 917 123 4567"));
        assertEquals("09171234567", PhoneNumberValidator.stripFormatting("0917 123 4567"));
    }

    @Test
    public void stripFormatting_leavesAlreadyCanonicalValueUnchanged() {
        assertEquals("09171234567", PhoneNumberValidator.stripFormatting("09171234567"));
        assertEquals("+639171234567", PhoneNumberValidator.stripFormatting("+639171234567"));
    }

    @Test
    public void stripFormatting_nullReturnsNull() {
        assertNull(PhoneNumberValidator.stripFormatting(null));
    }

    @Test
    public void canonicalPhFormats_areValid() {
        assertTrue(PhoneNumberValidator.isValid("Philippines", "09171234567"));
        assertTrue(PhoneNumberValidator.isValid("Philippines", "+639171234567"));
    }

    @Test
    public void malformedPhNumbers_areInvalid() {
        assertFalse(PhoneNumberValidator.isValid("Philippines", "091712345"));
        assertFalse(PhoneNumberValidator.isValid("Philippines", "08171234567"));
    }
}
