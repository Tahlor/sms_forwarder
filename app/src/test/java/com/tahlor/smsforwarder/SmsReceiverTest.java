package com.tahlor.smsforwarder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmsReceiverTest {
    @Test
    public void compactCreditKarmaForwardFitsSingleAsciiSmsSegment() {
        String body = "Credit Karma will NEVER call for this code. To prevent fraud, "
                + "don't share it with anyone. Code: 571782.\n\n"
                + "@creditkarma.com #571782";

        String forwarded = SmsReceiver.buildForwardedMessage("CREDITKARMA", body);

        assertTrue(forwarded.startsWith("[FWD] CREDITKARMA\n"));
        assertTrue(forwarded.contains(body));
        assertTrue("Expected common Credit Karma forward to stay under 160 ASCII chars",
                forwarded.length() <= 160);
    }

    @Test
    public void equivalentDestinationIsDetectedAcrossUsNumberFormats() {
        List<String> delivered = new ArrayList<>();
        delivered.add("+1 610-573-7638");

        assertTrue(SmsReceiver.containsEquivalentDestination(
                delivered, "6105737638"));
        assertFalse(SmsReceiver.containsEquivalentDestination(
                delivered, "8015551212"));
    }
}
