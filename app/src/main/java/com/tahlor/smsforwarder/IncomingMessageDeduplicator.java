package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

final class IncomingMessageDeduplicator {
    private static final String PREFS = "sms_forwarder_dedupe";
    private static final String KEY_PREFIX = "seen_";
    private static final String CONTENT_KEY_PREFIX = "recent_content_";
    private static final long RETENTION_MS = 5L * 60L * 1000L;
    private static final long RECENT_CONTENT_RETENTION_MS = 30L * 1000L;

    private IncomingMessageDeduplicator() {}

    static synchronized boolean shouldProcess(Context context, String sender,
                                              long smsTimestampMillis, String body,
                                              long nowMillis) {
        String fingerprint = MessageFingerprint.of(sender, smsTimestampMillis, body);
        String contentFingerprint = MessageFingerprint.of(sender, 0L, body);
        String key = KEY_PREFIX + fingerprint;
        String contentKey = CONTENT_KEY_PREFIX + contentFingerprint;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long existingExpiry = prefs.getLong(key, 0L);
        long existingContentExpiry = prefs.getLong(contentKey, 0L);
        if (existingExpiry > nowMillis || existingContentExpiry > nowMillis) return false;

        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String entryKey = entry.getKey();
            if (!entryKey.startsWith(KEY_PREFIX)
                    && !entryKey.startsWith(CONTENT_KEY_PREFIX)) continue;
            Object value = entry.getValue();
            if (value instanceof Long && (Long) value <= nowMillis) {
                editor.remove(entryKey);
            }
        }
        editor.putLong(key, nowMillis + RETENTION_MS);
        editor.putLong(contentKey, nowMillis + RECENT_CONTENT_RETENTION_MS);
        editor.apply();
        return true;
    }
}
