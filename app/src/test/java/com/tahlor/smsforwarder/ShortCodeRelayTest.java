package com.tahlor.smsforwarder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ShortCodeRelayTest {
    @Test
    public void parsesBracketedSixDigitTargetAndTrimsPayload() {
        ShortCodeRelay.Command command = ShortCodeRelay.parseCommand(" [711711]   SAVE  ");
        assertEquals("711711", command.shortCode);
        assertEquals("SAVE", command.payload);

        command = ShortCodeRelay.parseCommand("[711-711] hello there");
        assertEquals("711711", command.shortCode);
        assertEquals("hello there", command.payload);
    }

    @Test
    public void permitsEmptyPayloadToOpenReplyWindow() {
        ShortCodeRelay.Command command = ShortCodeRelay.parseCommand("[711-711]   ");
        assertEquals("711711", command.shortCode);
        assertEquals("", command.payload);
    }

    @Test
    public void exactReportedSaveCommandMatchesRegisteredDownstreamPhone() {
        PhoneProfile downstream = new PhoneProfile(
                "+18015551212", true, true, true, true);
        List<PhoneProfile> profiles = new ArrayList<>();
        profiles.add(downstream);

        ShortCodeRelay.Command command = ShortCodeRelay.parseCommand("[711711] SAVE");
        PhoneProfile matched = ShortCodeRelay.findRegisteredProfile(profiles, "801-555-1212");

        assertEquals("711711", command.shortCode);
        assertEquals("SAVE", command.payload);
        assertSame(downstream, matched);
        assertTrue(matched.relayEnabled);
    }

    @Test
    public void registeredPhoneLookupRejectsDifferentSender() {
        List<PhoneProfile> profiles = new ArrayList<>();
        profiles.add(new PhoneProfile("+18015551212", true, true, true, true));
        assertNull(ShortCodeRelay.findRegisteredProfile(profiles, "+18015559999"));
    }

    @Test
    public void formatsSixDigitShortcodesToAvoidOtpConfusion() {
        assertEquals("711-711", ShortCodeRelay.formatShortCode("711711"));
        assertEquals("711-711", ShortCodeRelay.formatSenderForDisplay("711711"));
        assertEquals("+18015551212", ShortCodeRelay.formatSenderForDisplay("+18015551212"));
    }

    @Test
    public void rejectsNonSixDigitOrUnbracketedTargets() {
        assertNull(ShortCodeRelay.parseCommand("711711 SAVE"));
        assertNull(ShortCodeRelay.parseCommand("[71171] SAVE"));
        assertNull(ShortCodeRelay.parseCommand("[711-7111] SAVE"));
        assertNull(ShortCodeRelay.parseCommand(null));
    }

    @Test
    public void comparesControllerAddressesAcrossFormattingAndNanpCountryCode() {
        assertTrue(ShortCodeRelay.sameAddress("+1 (801) 555-1212", "+18015551212"));
        assertTrue(ShortCodeRelay.sameAddress("801-555-1212", "+18015551212"));
        assertTrue(ShortCodeRelay.sameAddress("+18015551212", "8015551212"));
        assertFalse(ShortCodeRelay.sameAddress("+18015551212", "+18015559999"));
    }

    @Test
    public void matchesExactSixDigitShortcodeSender() {
        assertTrue(ShortCodeRelay.senderIsShortCode("711711", "711711"));
        assertFalse(ShortCodeRelay.senderIsShortCode("1711711", "711711"));
        assertFalse(ShortCodeRelay.senderIsShortCode("711711", "71171"));
    }
}
