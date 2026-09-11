package com.tahlor.smsforwarder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class MessageFingerprint {
    private MessageFingerprint() {}

    static String of(String sender, long smsTimestampMillis, String body) {
        String payload = safe(sender) + "\n" + smsTimestampMillis + "\n" + safe(body);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
