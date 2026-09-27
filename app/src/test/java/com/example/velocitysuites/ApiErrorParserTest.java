package com.example.velocitysuites;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pure-JVM coverage for ApiErrorParser - regression test for the Requirement
 * 4 bug: a naive substring scan for "message":" used to surface Laravel's
 * generic top-level message instead of the specific field message (e.g. the
 * mobile-number-already-registered error), even though the backend was
 * already sending the correct message all along.
 */
public class ApiErrorParserTest {

    @Test
    public void fieldError_winsOverGenericTopLevelMessage() {
        String body = "{\"message\":\"The given data was invalid.\","
                + "\"errors\":{\"mobile_number\":[\"This mobile number is already registered to another account.\"]}}";
        assertEquals("This mobile number is already registered to another account.",
                ApiErrorParser.extractMessage(body, "fallback"));
    }

    @Test
    public void noErrorsField_fallsBackToTopLevelMessage() {
        String body = "{\"message\":\"Something went wrong.\"}";
        assertEquals("Something went wrong.", ApiErrorParser.extractMessage(body, "fallback"));
    }

    @Test
    public void emptyErrorsObject_fallsBackToTopLevelMessage() {
        String body = "{\"message\":\"The given data was invalid.\",\"errors\":{}}";
        assertEquals("The given data was invalid.", ApiErrorParser.extractMessage(body, "fallback"));
    }

    @Test
    public void malformedBody_returnsFallback() {
        assertEquals("fallback", ApiErrorParser.extractMessage("not json at all", "fallback"));
    }

    @Test
    public void nullBody_returnsFallback() {
        assertEquals("fallback", ApiErrorParser.extractMessage((String) null, "fallback"));
    }

    @Test
    public void neitherMessageNorErrors_returnsFallback() {
        assertEquals("fallback", ApiErrorParser.extractMessage("{}", "fallback"));
    }
}
