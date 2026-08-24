package com.tahlor.smsforwarder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ShortCodeRelayTest {
    @Test
    public void parsesBracketedSixDigitTargetAndTrimsPayload() {
        ShortCodeRelay.Command command = ShortCodeRelay.parseCommand(" [711711]   Y  ");
        assertEquals("711711", command.shortCode);
        assertEquals("Y", command.payload);

        command = ShortCodeRelay.parseCommand("[123456] hello there");
        assertEquals("123456", command.shortCode);
        assertEquals("hello there", command.payload);
    }

    @Test
    public void permitsEmptyPayloadToOpenReplyWindow() {
        ShortCodeRelay.Command command = ShortCodeRelay.parseCommand("[711711]   ");
        assertEquals("711711", command.shortCode);
        assertEquals("", command.payload);
    }

    @Test
    public void rejectsNonSixDigitOrUnbracketedTargets() {
        assertNull(ShortCodeRelay.parseCommand("711711 Y"));
        assertNull(ShortCodeRelay.parseCommand("[71171] Y"));
        assertNull(ShortCodeRelay.parseCommand("[7117111] Y"));
        assertNull(ShortCodeRelay.parseCommand(null));
    }

    @Test
    public void comparesControllerAddressesIgnoringFormattingOnly() {
        assertTrue(ShortCodeRelay.sameAddress("+1 (801) 555-1212", "+18015551212"));
        assertFalse(ShortCodeRelay.sameAddress("+18015551212", "+18015559999"));
    }

    @Test
    public void matchesExactSixDigitShortcodeSender() {
        assertTrue(ShortCodeRelay.senderIsShortCode("711711", "711711"));
        assertFalse(ShortCodeRelay.senderIsShortCode("1711711", "711711"));
        assertFalse(ShortCodeRelay.senderIsShortCode("711711", "71171"));
    }
}
