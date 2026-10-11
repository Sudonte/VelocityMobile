package com.example.velocitysuites;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Sign-in and registration accept any well-formed email, not just Gmail. */
public class EmailValidatorTest {

    @Test
    public void anyWellFormedAddressIsAccepted() {
        for (String ok : new String[]{"guest@gmail.com", "ana.reyes@yahoo.com", "a_b+tag@outlook.ph", "team@mail.example.co.uk",
                "  spaced@example.org  ", "o'neil@example.com", "UPPER@EXAMPLE.COM", "x@sub-domain.example.io"}) {
            assertTrue(ok, EmailValidator.isValid(ok));
        }
    }

    @Test
    public void malformedAddressesAreRejected() {
        for (String bad : new String[]{null, "", "plain", "no-at.example.com", "two@@example.com", "a@b", "a@b.c", "@example.com",
                "a b@example.com", "a@exa mple.com", "a..b@example.com", "a@-example.com", "a@example..com", ".a@example.com"}) {
            assertFalse(String.valueOf(bad), EmailValidator.isValid(bad));
        }
    }
}
