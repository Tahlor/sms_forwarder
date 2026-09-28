package com.tahlor.smsforwarder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MessageFilterTest {
    @Test
    public void codeOnlyMatchesVerificationPatternsAndLegacyDigitRuns() {
        assertTrue(MessageFilter.shouldForward("Your code is 123456", true));
        assertTrue(MessageFilter.shouldForward("Code: 123456789", true));
        assertTrue(MessageFilter.shouldForward("Code: 12345", true));
        assertTrue(MessageFilter.shouldForward("Code: 123 456", true));
        assertTrue(MessageFilter.shouldForward("Your Credit Karma verification code is: 123-456.", true));
        assertTrue(MessageFilter.shouldForward("G-123456 is your Google verification code.", true));
        assertTrue(MessageFilter.shouldForward("Your PIN is 1234.", true));
        assertTrue(MessageFilter.shouldForward("Order 987654 shipped", true));
        assertFalse(MessageFilter.shouldForward("Meet me at 1234 Main St at 5pm", true));
        assertFalse(MessageFilter.shouldForward("Call me at 801-555-1234", true));
        assertFalse(MessageFilter.shouldForward("No code here", true));
    }

    @Test
    public void creditKarmaMessageMatchesAndExtractsReportedCode() {
        String body = "Credit Karma will NEVER call for this code. To prevent fraud, "
                + "don't share it with anyone. Code: 571782.\n\n"
                + "@creditkarma.com #571782";

        assertTrue(MessageFilter.containsSecurityCode(body));
        assertTrue(MessageFilter.shouldForward(body, true));
        assertEquals("571782", MessageFilter.extractCode(body));
    }

    @Test
    public void disabledFilterForwardsAnyNonEmptyMessage() {
        assertTrue(MessageFilter.shouldForward("hello", false));
        assertFalse(MessageFilter.shouldForward("", false));
        assertFalse(MessageFilter.shouldForward(null, false));
    }

    @Test
    public void extractsFirstSixPlusDigitRunForCopyFollowup() {
        assertEquals("123456", MessageFilter.extractCode("Your code is 123456. Never share it."));
        assertEquals("987654321", MessageFilter.extractCode("Use 987654321 to continue"));
        assertEquals("123456", MessageFilter.extractCode("First 123456 then 654321"));
        assertEquals("12345", MessageFilter.extractCode("Code 12345"));
        assertEquals("123456", MessageFilter.extractCode("Code: 123 456"));
        assertEquals("123456", MessageFilter.extractCode("Your Credit Karma verification code is: 123-456."));
        assertEquals("123456", MessageFilter.extractCode("G-123456 is your Google verification code."));
        assertEquals("1234", MessageFilter.extractCode("Your PIN is 1234."));
        assertNull(MessageFilter.extractCode("Meet me at 1234 Main St"));
        assertNull(MessageFilter.extractCode(""));
        assertNull(MessageFilter.extractCode(null));
    }
}
