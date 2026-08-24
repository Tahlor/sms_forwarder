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

        for (Map.Entry<String, StringBuilder> entry : bodiesBySender.entrySet()) {
            String sender = entry.getKey();
            String body = entry.getValue().toString();
            if (body.startsWith(FORWARD_PREFIX)) continue;

            if (handleShortCodeRelay(context, sender, body)) continue;
            if (!ForwardingPreferences.enabled(context)) continue;

            String destination = ForwardingPreferences.destination(context);
            if (destination.isEmpty()) {
                ForwardingPreferences.setStatus(context, "Normal forwarding is enabled, but no destination is configured.");
                continue;
            }
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context, "Cannot forward: SEND_SMS permission is not granted.");
                continue;
            }
            if (!MessageFilter.shouldForward(body, ForwardingPreferences.codeOnly(context))) continue;

            String forwarded = FORWARD_PREFIX + "\nFrom: " + sender + "\n" + body;
            String extractedCode = MessageFilter.extractCode(body);
            boolean sendCodeCopy = ForwardingPreferences.codeCopyFollowup(context) && extractedCode != null;

            try {
                SmsManager smsManager = SmsManager.getDefault();
                sendMessage(smsManager, destination, forwarded);
                if (sendCodeCopy) {
                    smsManager.sendTextMessage(destination, null, extractedCode, null, null);
                    ForwardingPreferences.setStatus(context, "Forwarded a matching SMS plus a code-only copy.");
                } else {
                    ForwardingPreferences.setStatus(context, "Forwarded a matching SMS successfully.");
                }
            } catch (SecurityException e) {
                ForwardingPreferences.setStatus(context, "Cannot forward: Android denied SMS send permission.");
            } catch (RuntimeException e) {
                ForwardingPreferences.setStatus(context, "Forwarding failed. Open the app to verify settings and SMS service.");
            }
        }
    }

    private static boolean handleShortCodeRelay(Context context, String sender, String body) {
        if (!ForwardingPreferences.shortCodeRelayEnabled(context)) return false;

        String controller = ForwardingPreferences.relayController(context);
        if (controller.isEmpty()) return false;

        long now = System.currentTimeMillis();
        if (ShortCodeRelay.sameAddress(sender, controller)) {
            ShortCodeRelay.Command command = ShortCodeRelay.parseCommand(body);
            if (command == null) return false;
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context, "Cannot relay shortcode command: SEND_SMS permission is missing.");
                return true;
            }
            try {
                ForwardingPreferences.startShortCodeRelay(context, command.shortCode, now);
                if (!command.payload.isEmpty()) {
                    sendMessage(SmsManager.getDefault(), command.shortCode, command.payload);
                    ForwardingPreferences.setStatus(context,
                            "Sent to shortcode " + command.shortCode + "; forwarding replies for 5 minutes.");
                } else {
                    ForwardingPreferences.setStatus(context,
                            "Opened a 5-minute reply window for shortcode " + command.shortCode + ".");
                }
            } catch (SecurityException e) {
                ForwardingPreferences.setStatus(context, "Android denied the shortcode SMS send.");
            } catch (RuntimeException e) {
                ForwardingPreferences.setStatus(context, "Shortcode send failed; carrier/device may not allow it.");
            }
            return true;
        }

        String activeShortCode = ForwardingPreferences.activeShortCode(context, now);
        if (!activeShortCode.isEmpty() && ShortCodeRelay.senderIsShortCode(sender, activeShortCode)) {
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context, "Cannot relay shortcode reply: SEND_SMS permission is missing.");
                return true;
            }
            try {
                String relayed = "[" + activeShortCode + "] " + body;
                sendMessage(SmsManager.getDefault(), controller, relayed);
                ForwardingPreferences.setStatus(context,
                        "Relayed a reply from shortcode " + activeShortCode + ".");
            } catch (SecurityException e) {
                ForwardingPreferences.setStatus(context, "Android denied forwarding the shortcode reply.");
            } catch (RuntimeException e) {
                ForwardingPreferences.setStatus(context, "Failed to forward the shortcode reply.");
            }
            return true;
        }

        return false;
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
