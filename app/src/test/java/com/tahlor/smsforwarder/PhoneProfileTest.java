package com.tahlor.smsforwarder;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneProfileTest {
    @Test
    public void trustedPhoneCanAuthorizeAnyoneButAutoForwardOnlyCodes() {
        PhoneProfile profile = new PhoneProfile(
                "6105737638",
                PhoneProfile.IncomingAuthorization.ANY,
                Collections.emptyList(),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.SECURITY_CODES,
                Collections.emptyList(),
                true,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());

        assertTrue(profile.permitsIncomingAuthorization("CREDITKARMA"));
        assertTrue(profile.permitsIncoming("CREDITKARMA", "Code: 571782"));
        assertFalse(profile.permitsIncoming("CREDITKARMA", "Your statement is ready"));
    }

    @Test
    public void creditKarmaReportedBodyPassesSecurityCodeProfile() {
        String body = "Credit Karma will NEVER call for this code. To prevent fraud, "
                + "don't share it with anyone. Code: 571782.\n\n"
                + "@creditkarma.com #571782";
        PhoneProfile profile = new PhoneProfile(
                "6105737638",
                PhoneProfile.IncomingAuthorization.ANY,
                Collections.emptyList(),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.SECURITY_CODES,
                Collections.emptyList(),
                true,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());

        assertTrue(profile.permitsIncoming("CREDITKARMA", body));
        assertTrue(profile.permitsIncoming("+18015551212", body));
    }

    @Test
    public void limitedAuthorizationCannotBeExpandedByBroadPreference() {
        PhoneProfile profile = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingAuthorization.SELECTED,
                Arrays.asList("+15551112222", "BANKALERT"),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.ALL_AUTHORIZED,
                Collections.emptyList(),
                true,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());

        assertTrue(profile.permitsIncoming("bankalert", "hello"));
        assertTrue(profile.permitsIncoming("+15551112222", "hello"));
        assertFalse(profile.permitsIncoming("+15553334444", "hello"));
    }

    @Test
    public void selectedPreferenceIsIntersectedWithAuthorization() {
        PhoneProfile profile = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingAuthorization.SELECTED,
                Arrays.asList("+15551112222", "+15553334444"),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.SELECTED,
                Arrays.asList("+15553334444", "+15557778888"),
                true,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());

        assertFalse(profile.permitsIncoming("+15551112222", "hello"));
        assertTrue(profile.permitsIncoming("+15553334444", "hello"));
        assertFalse(profile.permitsIncoming("+15557778888", "hello"));
    }

    @Test
    public void incomingBlocklistAlwaysOverridesAuthorizationAndPreference() {
        PhoneProfile profile = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingAuthorization.ANY,
                Collections.emptyList(),
                Arrays.asList("+15551112222"),
                PhoneProfile.IncomingPreference.ALL_AUTHORIZED,
                Collections.emptyList(),
                true,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());

        assertTrue(profile.permitsIncoming("+15553334444", "hello"));
        assertFalse(profile.permitsIncoming("+15551112222", "hello"));
    }


    @Test
    public void remoteManagementIsOffByDefaultAndCountsAsExplicitCapabilityOnlyWhenEnabled() {
        PhoneProfile defaultProfile = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingAuthorization.NONE,
                Collections.emptyList(),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.OFF,
                Collections.emptyList(),
                false,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());
        assertFalse(defaultProfile.allowRemoteCommands);
        assertFalse(defaultProfile.hasAnyCapabilityEnabled());

        PhoneProfile remoteAdmin = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingAuthorization.NONE,
                Collections.emptyList(),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.OFF,
                Collections.emptyList(),
                false,
                PhoneProfile.OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList(),
                true);
        assertTrue(remoteAdmin.allowRemoteCommands);
        assertTrue(remoteAdmin.hasAnyCapabilityEnabled());
    }

    @Test
    public void outgoingModesAndListsAreSecurityCapabilities() {
        PhoneProfile shortCodes = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingAuthorization.NONE,
                Collections.emptyList(),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.OFF,
                Collections.emptyList(),
                false,
                PhoneProfile.OutgoingMode.SHORT_CODES,
                Collections.emptyList(),
                Collections.emptyList());
        assertTrue(shortCodes.permitsOutgoing("711711"));
        assertFalse(shortCodes.permitsOutgoing("15551112222"));

        PhoneProfile selected = new PhoneProfile(
                "+15550000000",
                PhoneProfile.IncomingAuthorization.NONE,
                Collections.emptyList(),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.OFF,
                Collections.emptyList(),
                false,
                PhoneProfile.OutgoingMode.SELECTED,
                Arrays.asList("711711", "+15551112222"),
                Arrays.asList("711711"));
        assertFalse(selected.permitsOutgoing("711711"));
        assertTrue(selected.permitsOutgoing("+15551112222"));
        assertFalse(selected.permitsOutgoing("+15553334444"));

        PhoneProfile any = new PhoneProfile(
                "6105737638",
                PhoneProfile.IncomingAuthorization.NONE,
                Collections.emptyList(),
                Collections.emptyList(),
                PhoneProfile.IncomingPreference.OFF,
                Collections.emptyList(),
                false,
                PhoneProfile.OutgoingMode.ANY,
                Collections.emptyList(),
                Collections.emptyList());
        assertTrue(any.permitsOutgoing("8552448147"));
    }
}
