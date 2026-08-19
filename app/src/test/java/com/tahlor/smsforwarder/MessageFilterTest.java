package com.tahlor.smsforwarder;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
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
}
