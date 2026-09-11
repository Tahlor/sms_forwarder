package com.tahlor.smsforwarder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class MessageFingerprintTest {
    @Test
    public void identicalDeliveryGetsIdenticalFingerprint() {
        String first = MessageFingerprint.of("711711", 123456789L, "Your code is 123456");
        String second = MessageFingerprint.of("711711", 123456789L, "Your code is 123456");
        assertEquals(first, second);
    }

    @Test
    public void distinctSmsTimestampDoesNotCollapseLegitimateRepeat() {
        String first = MessageFingerprint.of("711711", 123456789L, "Your code is 123456");
        String second = MessageFingerprint.of("711711", 123456790L, "Your code is 123456");
        assertNotEquals(first, second);
    }

    @Test
    public void senderAndBodyArePartOfFingerprint() {
        String base = MessageFingerprint.of("711711", 123456789L, "Your code is 123456");
        assertNotEquals(base, MessageFingerprint.of("722722", 123456789L, "Your code is 123456"));
        assertNotEquals(base, MessageFingerprint.of("711711", 123456789L, "Your code is 654321"));
    }
}
