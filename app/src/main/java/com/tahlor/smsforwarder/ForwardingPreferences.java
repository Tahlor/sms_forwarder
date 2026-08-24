package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

final class ForwardingPreferences {
    private static final String PREFS = "sms_forwarder";
    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CODE_ONLY = "code_only";
    private static final String KEY_CODE_COPY_FOLLOWUP = "code_copy_followup";
    private static final String KEY_SHORT_CODE_RELAY_ENABLED = "short_code_relay_enabled";
    private static final String KEY_RELAY_CONTROLLER = "relay_controller";
    private static final String KEY_ACTIVE_SHORT_CODE = "active_short_code";
    private static final String KEY_ACTIVE_SHORT_CODE_EXPIRES_AT = "active_short_code_expires_at";
    private static final String KEY_STATUS = "status";

    private ForwardingPreferences() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String destination(Context context) {
        return prefs(context).getString(KEY_DESTINATION, "");
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static boolean codeOnly(Context context) {
        return prefs(context).getBoolean(KEY_CODE_ONLY, true);
    }

    static boolean codeCopyFollowup(Context context) {
        return prefs(context).getBoolean(KEY_CODE_COPY_FOLLOWUP, true);
    }

    static boolean shortCodeRelayEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SHORT_CODE_RELAY_ENABLED, true);
    }

    static String relayController(Context context) {
        String configured = prefs(context).getString(KEY_RELAY_CONTROLLER, "");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        return destination(context);
    }

    static String relayControllerOverride(Context context) {
        return prefs(context).getString(KEY_RELAY_CONTROLLER, "");
    }

    static String status(Context context) {
        return prefs(context).getString(KEY_STATUS, "Not configured yet.");
    }

    static void saveSettings(Context context, String destination, boolean enabled, boolean codeOnly,
                             boolean codeCopyFollowup, boolean shortCodeRelayEnabled,
                             String relayControllerOverride) {
        prefs(context).edit()
                .putString(KEY_DESTINATION, destination == null ? "" : destination.trim())
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_CODE_ONLY, codeOnly)
                .putBoolean(KEY_CODE_COPY_FOLLOWUP, codeCopyFollowup)
                .putBoolean(KEY_SHORT_CODE_RELAY_ENABLED, shortCodeRelayEnabled)
                .putString(KEY_RELAY_CONTROLLER,
                        relayControllerOverride == null ? "" : relayControllerOverride.trim())
                .apply();
    }

    static void startShortCodeRelay(Context context, String shortCode, long nowMillis) {
        prefs(context).edit()
                .putString(KEY_ACTIVE_SHORT_CODE, shortCode)
                .putLong(KEY_ACTIVE_SHORT_CODE_EXPIRES_AT, nowMillis + ShortCodeRelay.WINDOW_MS)
                .apply();
    }

    static String activeShortCode(Context context, long nowMillis) {
        SharedPreferences preferences = prefs(context);
        long expiresAt = preferences.getLong(KEY_ACTIVE_SHORT_CODE_EXPIRES_AT, 0L);
        String shortCode = preferences.getString(KEY_ACTIVE_SHORT_CODE, "");
        if (shortCode == null || shortCode.isEmpty() || nowMillis > expiresAt) {
            if ((shortCode != null && !shortCode.isEmpty()) || expiresAt != 0L) {
                preferences.edit()
                        .remove(KEY_ACTIVE_SHORT_CODE)
                        .remove(KEY_ACTIVE_SHORT_CODE_EXPIRES_AT)
                        .apply();
            }
            return "";
        }
        return shortCode;
    }

    static void setStatus(Context context, String status) {
        prefs(context).edit().putString(KEY_STATUS, status).apply();
    }
}
