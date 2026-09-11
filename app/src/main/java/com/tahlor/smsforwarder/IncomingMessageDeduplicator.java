package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

final class IncomingMessageDeduplicator {
    private static final String PREFS = "sms_forwarder_dedupe";
    private static final String KEY_PREFIX = "seen_";
    private static final long RETENTION_MS = 5L * 60L * 1000L;

    private IncomingMessageDeduplicator() {}

    static synchronized boolean shouldProcess(Context context, String sender,
                                              long smsTimestampMillis, String body,
                                              long nowMillis) {
        String fingerprint = MessageFingerprint.of(sender, smsTimestampMillis, body);
        String key = KEY_PREFIX + fingerprint;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long existingExpiry = prefs.getLong(key, 0L);
        if (existingExpiry > nowMillis) return false;

        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(KEY_PREFIX)) continue;
            Object value = entry.getValue();
            if (value instanceof Long && (Long) value <= nowMillis) {
                editor.remove(entry.getKey());
            }
        }
        editor.putLong(key, nowMillis + RETENTION_MS).apply();
        return true;
    }
}
