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
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        if (!ForwardingPreferences.enabled(context)) {
            return;
        }

        String destination = ForwardingPreferences.destination(context);
        if (destination.isEmpty()) {
            ForwardingPreferences.setStatus(context, "Forwarding is enabled, but no destination number is configured.");
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ForwardingPreferences.setStatus(context, "Cannot forward: SEND_SMS permission is not granted.");
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        Map<String, StringBuilder> bodiesBySender = new LinkedHashMap<>();
        for (SmsMessage message : messages) {
            if (message == null) {
                continue;
            }
            String sender = message.getOriginatingAddress();
            if (sender == null || sender.isEmpty()) {
                sender = "(unknown sender)";
            }
            bodiesBySender.computeIfAbsent(sender, ignored -> new StringBuilder())
                    .append(message.getMessageBody() == null ? "" : message.getMessageBody());
        }

        for (Map.Entry<String, StringBuilder> entry : bodiesBySender.entrySet()) {
            String body = entry.getValue().toString();
            if (body.startsWith(FORWARD_PREFIX)) {
                continue;
            }
            if (!MessageFilter.shouldForward(body, ForwardingPreferences.codeOnly(context))) {
                continue;
            }

            String forwarded = FORWARD_PREFIX + "\nFrom: " + entry.getKey() + "\n" + body;
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

    private static void sendMessage(SmsManager smsManager, String destination, String message) {
        ArrayList<String> parts = smsManager.divideMessage(message);
        if (parts.size() <= 1) {
            smsManager.sendTextMessage(destination, null, message, null, null);
        } else {
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null);
        }
    }
}
