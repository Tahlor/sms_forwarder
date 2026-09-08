package com.tahlor.smsforwarder;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneProfileTest {
    @Test
    public void incomingBlocklistOverridesAllMode() {
        PhoneProfile profile = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingMode.ALL,
                Collections.emptyList(),
                Arrays.asList("+15551112222"),
                true,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());

        assertTrue(profile.permitsIncoming("+15553334444", "hello"));
        assertFalse(profile.permitsIncoming("+15551112222", "hello"));
    }

    @Test
    public void selectedIncomingRequiresAllowlistAndBlocklistWins() {
        PhoneProfile profile = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingMode.SELECTED,
                Arrays.asList("+15551112222", "BANKALERT"),
                Arrays.asList("+15551112222"),
                true,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());

        assertFalse(profile.permitsIncoming("+15551112222", "hello"));
        assertTrue(profile.permitsIncoming("bankalert", "hello"));
        assertFalse(profile.permitsIncoming("+15553334444", "hello"));
    }

    @Test
    public void outgoingModesAndListsAreApplied() {
        PhoneProfile shortCodes = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                PhoneProfile.OutgoingMode.SHORT_CODES,
                Collections.emptyList(),
                Collections.emptyList());
        assertTrue(shortCodes.permitsOutgoing("711711"));
        assertFalse(shortCodes.permitsOutgoing("15551112222"));

        PhoneProfile selected = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                PhoneProfile.OutgoingMode.SELECTED,
                Arrays.asList("711711", "+15551112222"),
                Arrays.asList("711711"));
        assertFalse(selected.permitsOutgoing("711711"));
        assertTrue(selected.permitsOutgoing("+15551112222"));
        assertFalse(selected.permitsOutgoing("+15553334444"));
    }
}
