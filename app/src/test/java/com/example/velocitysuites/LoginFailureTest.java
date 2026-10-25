package com.example.velocitysuites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LoginFailureTest {

    @Test
    public void theSharedCode_isInvalidCredentials() {
        LoginFailure f = LoginFailure.parse("{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"Incorrect email or password.\"}");
        assertEquals(LoginFailure.Kind.INVALID_CREDENTIALS, f.kind);
        assertEquals("Incorrect email or password.", f.message);
    }

    @Test
    public void anOlderServersTwoCodes_arethesameKind_soNothingTellsThemApart() {
        assertEquals(LoginFailure.Kind.INVALID_CREDENTIALS, LoginFailure.parse("{\"code\":\"ACCOUNT_NOT_FOUND\",\"message\":\"Account does not exist.\"}").kind);
        assertEquals(LoginFailure.Kind.INVALID_CREDENTIALS, LoginFailure.parse("{\"code\":\"WRONG_PASSWORD\",\"message\":\"Incorrect password.\"}").kind);
    }

    @Test
    public void anUnverifiedSignUp_isItsOwnKind() {
        assertEquals(LoginFailure.Kind.ACCOUNT_NOT_VERIFIED, LoginFailure.parse("{\"code\":\"ACCOUNT_NOT_VERIFIED\",\"message\":\"x\"}").kind);
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
