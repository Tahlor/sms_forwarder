package com.tahlor.smsforwarder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES/GCM envelope used for private connected-edition credentials and queued message events. */
final class CredentialCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private CredentialCipher() {}

    static byte[] encrypt(SecretKey key, String value) throws Exception {
        byte[] nonce = new byte[NONCE_BYTES];
        new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, result, 0, nonce.length);
        System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
        return result;
    }

    static String decrypt(SecretKey key, byte[] envelope) throws Exception {
        if (envelope == null || envelope.length <= NONCE_BYTES) {
            throw new IllegalArgumentException("Encrypted value is incomplete.");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        byte[] encrypted = new byte[envelope.length - NONCE_BYTES];
        System.arraycopy(envelope, 0, nonce, 0, nonce.length);
        System.arraycopy(envelope, nonce.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
