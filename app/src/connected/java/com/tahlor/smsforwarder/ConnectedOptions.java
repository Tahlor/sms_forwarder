package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

/** Locally stored user choices. All sharing and remote-send choices default to off. */
final class ConnectedOptions {
    private static final String PREFS = "connected_options";
    private static final String BODY_SYNC = "body_sync";
    private static final String SECURITY_CODES = "security_codes";
    private static final String REMOTE_SMS = "remote_sms";
    private static final String NOTIFICATIONS = "server_notifications";
    private static final String LAST_SYNC = "last_sync";
    private static final String LAST_ERROR = "last_error";

    private ConnectedOptions() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean messageBodySync(Context context) {
        return prefs(context).getBoolean(BODY_SYNC, false);
    }

    static void setMessageBodySync(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(BODY_SYNC, enabled).apply();
    }

    static boolean securityCodes(Context context) {
        return prefs(context).getBoolean(SECURITY_CODES, false);
    }

    static void setSecurityCodes(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(SECURITY_CODES, enabled).apply();
    }

    static boolean remoteSms(Context context) {
        return prefs(context).getBoolean(REMOTE_SMS, false);
    }

    static void setRemoteSms(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(REMOTE_SMS, enabled).apply();
    }

    static boolean serverNotifications(Context context) {
        return prefs(context).getBoolean(NOTIFICATIONS, false);
    }

    static void setServerNotifications(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(NOTIFICATIONS, enabled).apply();
    }

    static boolean shouldQueueMessage(boolean bodySync, boolean codes, String body) {
        return bodySync || (codes && MessageFilter.extractCode(body) != null);
    }

    static String lastSync(Context context) {
        return prefs(context).getString(LAST_SYNC, "");
    }

    static String lastError(Context context) {
        return prefs(context).getString(LAST_ERROR, "");
    }

    static void setLastSync(Context context, String value) {
        prefs(context).edit().putString(LAST_SYNC, value).putString(LAST_ERROR, "").apply();
    }

    static void setError(Context context, String value) {
        prefs(context).edit().putString(LAST_ERROR, value == null ? "" : value).apply();
    }
}
