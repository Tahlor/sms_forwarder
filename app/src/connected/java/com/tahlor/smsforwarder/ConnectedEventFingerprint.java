package com.tahlor.smsforwarder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stable retry key derived only from the content the current opt-in allows us to upload. */
final class ConnectedEventFingerprint {
    private ConnectedEventFingerprint() {}

    static String create(String sender, long timestamp, String sharedContent, boolean fullBody) {
        String canonical = sender + "\n" + timestamp + "\n"
                + (fullBody ? "body" : "code") + "\n" + sharedContent;
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
        StringBuilder result = new StringBuilder("sms-");
        for (byte value : digest) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", value));
        }
        return result.toString();
    }
}
