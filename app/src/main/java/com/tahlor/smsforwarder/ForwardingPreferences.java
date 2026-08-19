package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

final class ForwardingPreferences {
    private static final String PREFS = "sms_forwarder";
    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CODE_ONLY = "code_only";
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

    static String status(Context context) {
        return prefs(context).getString(KEY_STATUS, "Not configured yet.");
    }

    static void saveSettings(Context context, String destination, boolean enabled, boolean codeOnly) {
        prefs(context).edit()
                .putString(KEY_DESTINATION, destination == null ? "" : destination.trim())
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_CODE_ONLY, codeOnly)
                .apply();
    }

    static void setStatus(Context context, String status) {
        prefs(context).edit().putString(KEY_STATUS, status).apply();
    }
}
