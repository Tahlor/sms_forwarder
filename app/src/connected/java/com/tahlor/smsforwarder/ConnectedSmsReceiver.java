package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Preserves the classic SMS path and queues only opted-in new messages for background sync. */
public final class ConnectedSmsReceiver extends SmsReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        try {
            if (ConnectedStore.credentials(context) == null) return;
        } catch (RuntimeException e) {
            ConnectedOptions.setError(context,
                    "Saved pairing credentials could not be read. Disconnect and pair again.");
            return;
        }

        boolean bodySync = ConnectedOptions.messageBodySync(context);
        boolean codeSync = ConnectedOptions.securityCodes(context);
        if (!bodySync && !codeSync) return;

        SmsMessage[] messages;
        try {
            messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (RuntimeException e) {
            ConnectedOptions.setError(context, "An incoming SMS could not be prepared for sync.");
            return;
        }
        if (messages == null || messages.length == 0) return;

        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        Map<String, Long> timestamps = new LinkedHashMap<>();
        for (SmsMessage message : messages) {
            if (message == null) continue;
            String sender = message.getOriginatingAddress();
            if (sender == null || sender.trim().isEmpty()) sender = "(unknown sender)";
            StringBuilder body = bodies.get(sender);
            if (body == null) {
                body = new StringBuilder();
                bodies.put(sender, body);
            }
            body.append(message.getMessageBody() == null ? "" : message.getMessageBody());
            long timestamp = message.getTimestampMillis();
            Long previous = timestamps.get(sender);
            if (previous == null || timestamp < previous) timestamps.put(sender, timestamp);
        }

        for (Map.Entry<String, StringBuilder> entry : bodies.entrySet()) {
            String sender = entry.getKey();
            String originalBody = entry.getValue().toString();
            if (!ConnectedOptions.shouldQueueMessage(bodySync, codeSync, originalBody)) continue;
            String code = codeSync ? MessageFilter.extractCode(originalBody) : null;
            String syncBody = bodySync ? originalBody : code;
            try {
                long timestamp = timestamps.containsKey(sender) ? timestamps.get(sender) : 0L;
                String fingerprint = ConnectedEventFingerprint.create(
                        sender, timestamp, syncBody == null ? "" : syncBody, bodySync);
                JSONObject payload = new JSONObject();
                payload.put("sender", sender);
                payload.put("body", syncBody == null ? "" : syncBody);
                payload.put("timestamp", timestamp);
                payload.put("is_security_code", code != null);
                payload.put("extracted_code", code == null ? JSONObject.NULL : code);
                payload.put("client_fingerprint", fingerprint);

                JSONObject event = new JSONObject();
                event.put("event_id", fingerprint);
                event.put("event_type", "sms_received");
                event.put("timestamp", timestamp);
                event.put("client_fingerprint", fingerprint);
                event.put("payload", payload);
                if (ConnectedStore.enqueueEvent(context, event)) {
                    ConnectedSyncScheduler.scheduleImmediate(context);
                }
            } catch (Exception e) {
                ConnectedOptions.setError(context, "An incoming message could not be queued securely.");
            }
        }
    }

}
