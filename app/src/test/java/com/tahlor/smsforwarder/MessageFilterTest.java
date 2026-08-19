package com.tahlor.smsforwarder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MessageFilterTest {
    @Test
    public void codeOnlyMatchesSixOrMoreConsecutiveAsciiDigits() {
        assertTrue(MessageFilter.shouldForward("Your code is 123456", true));
        assertTrue(MessageFilter.shouldForward("Code: 123456789", true));
        assertFalse(MessageFilter.shouldForward("Code: 12345", true));
        assertFalse(MessageFilter.shouldForward("Code: 123 456", true));
        assertFalse(MessageFilter.shouldForward("No code here", true));
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
        assertNull(MessageFilter.extractCode("Code 12345"));
        assertNull(MessageFilter.extractCode(""));
        assertNull(MessageFilter.extractCode(null));
    }
}
