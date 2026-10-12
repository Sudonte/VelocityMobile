package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LoginFailureTest {

    @Test
    public void unknownAccountCode_isAccountNotFound() {
        LoginFailure f = LoginFailure.parse("{\"code\":\"ACCOUNT_NOT_FOUND\",\"message\":\"Account does not exist.\"}");
        assertEquals(LoginFailure.Kind.ACCOUNT_NOT_FOUND, f.kind);
        assertEquals("Account does not exist.", f.message);
    }

    @Test
    public void wrongPasswordCode_isWrongPassword() {
        LoginFailure f = LoginFailure.parse("{\"code\":\"WRONG_PASSWORD\",\"message\":\"Incorrect password. Please try again.\"}");
        assertEquals(LoginFailure.Kind.WRONG_PASSWORD, f.kind);
        assertEquals("Incorrect password. Please try again.", f.message);
    }

    @Test
    public void olderServerGenericBody_isOtherButKeepsItsMessage() {
        LoginFailure f = LoginFailure.parse("{\"message\":\"Invalid credentials.\"}");
        assertEquals(LoginFailure.Kind.OTHER, f.kind);
        assertEquals("Invalid credentials.", f.message);
    }

    @Test
    public void blankOrBrokenBody_isOtherWithNoMessage() {
        assertEquals(LoginFailure.Kind.OTHER, LoginFailure.parse(null).kind);
        assertEquals(LoginFailure.Kind.OTHER, LoginFailure.parse("").kind);
        LoginFailure html = LoginFailure.parse("<html>502 Bad Gateway</html>");
        assertEquals(LoginFailure.Kind.OTHER, html.kind);
        assertNull(html.message);
    }
}
