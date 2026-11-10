package com.tahlor.smsforwarder;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConnectedConsentAndCredentialsTest {
    @Test
    public void messageUploadRequiresAnExplicitLocalOptIn() {
        assertFalse(ConnectedOptions.shouldQueueMessage(false, false,
                "Your verification code is 123456."));
        assertFalse(ConnectedOptions.shouldQueueMessage(false, true,
                "The appointment is at 4pm."));
        assertTrue(ConnectedOptions.shouldQueueMessage(false, true,
                "Your verification code is 123456."));
        assertTrue(ConnectedOptions.shouldQueueMessage(true, false,
                "The appointment is at 4pm."));
    }

    @Test
    public void remoteSmsCannotWidenExistingOutgoingAuthorization() {
        String destination = "+15551234567";
        PhoneProfile noAuthorization = new PhoneProfile(
                "+15550000000", PhoneProfile.IncomingAuthorization.NONE,
                Collections.emptyList(), Collections.emptyList(),
                PhoneProfile.IncomingPreference.OFF, Collections.emptyList(), true,
                PhoneProfile.OutgoingMode.OFF, Collections.emptyList(), Collections.emptyList());
        assertNotNull(RemoteSmsPolicy.denialReason(true, true,
                Collections.singletonList(noAuthorization), destination));

        PhoneProfile allowed = new PhoneProfile(
                "+15550000000", PhoneProfile.IncomingAuthorization.NONE,
                Collections.emptyList(), Collections.emptyList(),
                PhoneProfile.IncomingPreference.OFF, Collections.emptyList(), true,
                PhoneProfile.OutgoingMode.ANY, Collections.emptyList(), Collections.emptyList());
        assertNull(RemoteSmsPolicy.denialReason(true, true,
                Collections.singletonList(allowed), destination));
        assertNotNull(RemoteSmsPolicy.denialReason(false, true,
                Collections.singletonList(allowed), destination));
        assertNotNull(RemoteSmsPolicy.denialReason(true, false,
                Collections.singletonList(allowed), destination));
        assertNotNull(RemoteSmsPolicy.denialReason(true, true,
                Collections.singletonList(allowed), "911"));
    }

    @Test
    public void deviceTokenIsEncryptedAndCanBeRecoveredOnlyWithItsKey() throws Exception {
        String token = "dtok_private_device_token_4c6530";
        SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
        byte[] encrypted = CredentialCipher.encrypt(key, token);
        String bytesAsText = new String(encrypted, StandardCharsets.ISO_8859_1);

        assertFalse(bytesAsText.contains(token));
        assertFalse(Arrays.equals(token.getBytes(StandardCharsets.UTF_8), encrypted));
        assertEquals(token, CredentialCipher.decrypt(key, encrypted));
    }

    @Test
    public void codeOnlyRetryFingerprintUsesOnlyTheSharedCode() throws Exception {
        String fullMessage = "Your secret verification details are code 123456.";
        String codeOnly = ConnectedEventFingerprint.create(
                "+15551234567", 1731172000000L, "123456", false);
        String retry = ConnectedEventFingerprint.create(
                "+15551234567", 1731172000000L, "123456", false);
        String bodySync = ConnectedEventFingerprint.create(
                "+15551234567", 1731172000000L, fullMessage, true);

        assertEquals(codeOnly, retry);
        assertNotEquals(bodySync, codeOnly);
        org.junit.Assert.assertFalse(codeOnly.contains(fullMessage));
    }
}
