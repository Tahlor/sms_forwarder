package com.tahlor.smsforwarder;

import android.app.Notification;
import android.app.Person;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;

public final class RcsCommandNotificationListener extends NotificationListenerService {
    private static final String GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !GOOGLE_MESSAGES_PACKAGE.equals(sbn.getPackageName())) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;
        Parcelable[] bundles =
                notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (bundles == null || bundles.length == 0) return;

        List<Notification.MessagingStyle.Message> messages =
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
        for (int i = messages.size() - 1; i >= 0; i--) {
            Notification.MessagingStyle.Message message = messages.get(i);
            if (message == null || message.getText() == null) continue;

            String text = message.getText().toString().trim();
            if (!RcsNotificationBridge.looksLikeCommand(text)) continue;

            String sender = verifiedPhoneSender(message.getSenderPerson());
            if (sender == null) {
                ForwardingPreferences.logActivity(this,
                        "Ignored a Google Messages command-like notification because "
                                + "Android did not expose a verified sender phone number.");
                return;
            }

            long now = System.currentTimeMillis();
            if (!IncomingMessageDeduplicator.shouldProcess(
                    this, "rcs:" + sender, message.getTimestamp(), text, now)) {
                ForwardingPreferences.logActivity(this,
                        "Ignored a duplicate Google Messages command notification.");
                return;
            }
            if (!CommandDeduplicator.shouldProcess(this, sender, text, now)) {
                ForwardingPreferences.logActivity(this,
                        "Ignored a command already handled through SMS or another notification.");
                return;
            }

            List<PhoneProfile> profiles = ForwardingPreferences.profiles(this);
            if (ShortCodeRelay.findRegisteredProfile(profiles, sender) == null) {
                ForwardingPreferences.logActivity(this,
                        "Ignored a Google Messages command from an unregistered phone.");
                return;
            }

            boolean handled = SmsReceiver.handleDownstreamCommand(
                    this, profiles, sender, text);
            if (handled) {
                ForwardingPreferences.logActivity(this,
                        "Processed a trusted command from Google Messages notification "
                                + "for " + ShortCodeRelay.formatSenderForDisplay(sender) + ".");
            }
            return;
        }
    }

    private static String verifiedPhoneSender(Person person) {
        if (person == null || person.getUri() == null) return null;
        Uri uri = Uri.parse(person.getUri());
        String scheme = uri.getScheme();
        if (scheme == null) return null;
        if (!"tel".equalsIgnoreCase(scheme)
                && !"sms".equalsIgnoreCase(scheme)
                && !"smsto".equalsIgnoreCase(scheme)) {
            return null;
        }

        String normalized = ShortCodeRelay.normalizeDestination(
                uri.getSchemeSpecificPart());
        return normalized.isEmpty() ? null : normalized;
    }
}
