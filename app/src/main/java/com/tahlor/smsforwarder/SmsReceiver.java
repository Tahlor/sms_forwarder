package com.tahlor.smsforwarder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SmsReceiver extends BroadcastReceiver {
    static final String FORWARD_PREFIX = "[SMS Forwarder]";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        Map<String, StringBuilder> bodiesBySender = new LinkedHashMap<>();
        for (SmsMessage message : messages) {
            if (message == null) continue;
            String sender = message.getOriginatingAddress();
            if (sender == null || sender.isEmpty()) sender = "(unknown sender)";
            bodiesBySender.computeIfAbsent(sender, ignored -> new StringBuilder())
                    .append(message.getMessageBody() == null ? "" : message.getMessageBody());
        }

        List<PhoneProfile> profiles = ForwardingPreferences.profiles(context);
        for (Map.Entry<String, StringBuilder> entry : bodiesBySender.entrySet()) {
            String sender = entry.getKey();
            String body = entry.getValue().toString();
            if (body.startsWith(FORWARD_PREFIX)) continue;

            if (handleShortCodeRelay(context, profiles, sender, body)) continue;
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context, "Cannot forward: SEND_SMS permission is not granted.");
                continue;
            }

            for (PhoneProfile profile : profiles) {
                if (!profile.forwardEnabled) continue;
                if (!MessageFilter.shouldForward(body, profile.codeOnly)) continue;

                String forwarded = FORWARD_PREFIX + "\nFrom: "
                        + ShortCodeRelay.formatSenderForDisplay(sender) + "\n" + body;
                String extractedCode = MessageFilter.extractCode(body);
                try {
                    SmsManager smsManager = SmsManager.getDefault();
                    sendMessage(smsManager, profile.number, forwarded);
                    if (profile.codeCopyFollowup && extractedCode != null) {
                        smsManager.sendTextMessage(profile.number, null, extractedCode, null, null);
                    }
                    ForwardingPreferences.setStatus(context,
                            "Forwarded a matching SMS to configured phone profile(s).");
                } catch (SecurityException e) {
                    ForwardingPreferences.setStatus(context, "Android denied SMS send permission.");
                } catch (RuntimeException e) {
                    ForwardingPreferences.setStatus(context, "Forwarding failed for " + profile.number + ".");
                }
            }
        }
    }

    private static boolean handleShortCodeRelay(Context context, List<PhoneProfile> profiles,
                                                String sender, String body) {
        long now = System.currentTimeMillis();

        for (PhoneProfile profile : profiles) {
            if (!profile.relayEnabled || !ShortCodeRelay.sameAddress(sender, profile.number)) continue;
            ShortCodeRelay.Command command = ShortCodeRelay.parseCommand(body);
            if (command == null) return false;
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context, "Cannot relay shortcode command: SEND_SMS permission is missing.");
                return true;
            }
            String displayCode = ShortCodeRelay.formatShortCode(command.shortCode);
            try {
                ForwardingPreferences.startShortCodeRelay(context, profile.number, command.shortCode, now);
                if (!command.payload.isEmpty()) {
                    sendMessage(SmsManager.getDefault(), command.shortCode, command.payload);
                    ForwardingPreferences.setStatus(context,
                            "Sent to shortcode " + displayCode + "; forwarding replies for 5 minutes.");
                } else {
                    ForwardingPreferences.setStatus(context,
                            "Opened a 5-minute reply window for shortcode " + displayCode + ".");
                }
            } catch (SecurityException e) {
                ForwardingPreferences.setStatus(context, "Android denied the shortcode SMS send.");
            } catch (RuntimeException e) {
                ForwardingPreferences.setStatus(context, "Shortcode send failed; carrier/device may not allow it.");
            }
            return true;
        }

        boolean relayed = false;
        for (PhoneProfile profile : profiles) {
            if (!profile.relayEnabled) continue;
            String activeShortCode = ForwardingPreferences.activeShortCode(context, profile.number, now);
            if (activeShortCode.isEmpty() || !ShortCodeRelay.senderIsShortCode(sender, activeShortCode)) continue;
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context, "Cannot relay shortcode reply: SEND_SMS permission is missing.");
                return true;
            }
            try {
                String relayedBody = "[" + ShortCodeRelay.formatShortCode(activeShortCode) + "] " + body;
                sendMessage(SmsManager.getDefault(), profile.number, relayedBody);
                relayed = true;
            } catch (SecurityException e) {
                ForwardingPreferences.setStatus(context, "Android denied forwarding the shortcode reply.");
            } catch (RuntimeException e) {
                ForwardingPreferences.setStatus(context, "Failed to forward the shortcode reply.");
            }
        }
        if (relayed) {
            ForwardingPreferences.setStatus(context, "Relayed shortcode reply to active controller profile(s).");
        }
        return relayed;
    }

    private static boolean hasSendPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private static void sendMessage(SmsManager smsManager, String destination, String message) {
        ArrayList<String> parts = smsManager.divideMessage(message);
        if (parts.size() <= 1) smsManager.sendTextMessage(destination, null, message, null, null);
        else smsManager.sendMultipartTextMessage(destination, null, parts, null, null);
    }
}
