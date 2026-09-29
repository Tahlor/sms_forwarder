package com.tahlor.smsforwarder;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

final class RcsNotificationBridge {
    private RcsNotificationBridge() {}

    static boolean isAccessGranted(Context context) {
        ComponentName component = new ComponentName(
                context, RcsCommandNotificationListener.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager != null
                    && manager.isNotificationListenerAccessGranted(component);
        }

        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;
        for (String flattened : enabled.split(":")) {
            ComponentName candidate = ComponentName.unflattenFromString(flattened);
            if (component.equals(candidate)) return true;
        }
        return false;
    }

    static void openSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        activity.startActivity(intent);
    }

    static boolean looksLikeCommand(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        return ShortCodeRelay.parseManagementCommand(text) != null
                || ShortCodeRelay.parseCommand(text) != null;
    }
}
