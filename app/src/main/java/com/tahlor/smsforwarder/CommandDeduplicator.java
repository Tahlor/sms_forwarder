package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

final class CommandDeduplicator {
    private static final String PREFS = "sms_forwarder_command_dedupe";
    private static final String KEY_PREFIX = "seen_";
    private static final long RETENTION_MS = 20L * 1000L;

    private CommandDeduplicator() {}

    static synchronized boolean shouldProcess(
            Context context, String sender, String body, long nowMillis) {
        String fingerprint = MessageFingerprint.of(
                sender == null ? "" : sender, 0L,
                body == null ? "" : body.trim());
        String key = KEY_PREFIX + fingerprint;
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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
