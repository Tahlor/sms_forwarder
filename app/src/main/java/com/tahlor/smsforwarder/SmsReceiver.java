package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.PendingIntent;
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
import java.util.UUID;

public final class SmsReceiver extends BroadcastReceiver {
    static final String FORWARD_PREFIX = "[SMS Forwarder]";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        Map<String, StringBuilder> bodiesBySender = new LinkedHashMap<>();
        Map<String, Long> timestampsBySender = new LinkedHashMap<>();
        for (SmsMessage message : messages) {
            if (message == null) continue;
            String sender = message.getOriginatingAddress();
            if (sender == null || sender.isEmpty()) sender = "(unknown sender)";
            bodiesBySender.computeIfAbsent(sender, ignored -> new StringBuilder())
                    .append(message.getMessageBody() == null ? "" : message.getMessageBody());
            long timestamp = message.getTimestampMillis();
            Long existing = timestampsBySender.get(sender);
            if (existing == null || timestamp < existing) timestampsBySender.put(sender, timestamp);
        }

        List<PhoneProfile> profiles = ForwardingPreferences.profiles(context);
        if (profiles.isEmpty()) {
            ForwardingPreferences.setStatus(context,
                    "Received SMS, but no downstream phone is configured yet.");
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, StringBuilder> entry : bodiesBySender.entrySet()) {
            String sender = entry.getKey();
            String body = entry.getValue().toString();
            if (body.startsWith(FORWARD_PREFIX)) continue;

            long smsTimestamp = timestampsBySender.containsKey(sender)
                    ? timestampsBySender.get(sender) : 0L;
            if (!IncomingMessageDeduplicator.shouldProcess(
                    context, sender, smsTimestamp, body, now)) {
                ForwardingPreferences.setStatus(context,
                        "Ignored a duplicate delivery of the same incoming SMS.");
                continue;
            }

            if (handleDownstreamCommand(context, profiles, sender, body)) continue;
            if (handleActiveReply(context, profiles, sender, body)) continue;
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context,
                        "Received SMS but cannot forward it because Send SMS access is missing.");
                continue;
            }

            boolean queuedAnywhere = false;
            boolean waitingForFollowup = false;
            for (PhoneProfile profile : profiles) {
                if (!profile.permitsIncoming(sender, body)) continue;

                String displaySender = ShortCodeRelay.formatSenderForDisplay(sender);
                String forwarded = FORWARD_PREFIX + "\nFrom: " + displaySender + "\n" + body;
                String extractedCode = MessageFilter.extractCode(body);
                try {
                    SmsManager smsManager = SmsManager.getDefault();
                    if (profile.codeCopyFollowup && extractedCode != null) {
                        sendFullThenCode(context, smsManager, profile.number, forwarded, extractedCode);
                        waitingForFollowup = true;
                    } else {
                        sendMessage(smsManager, profile.number, forwarded);
                    }
                    queuedAnywhere = true;
                } catch (SecurityException e) {
                    ForwardingPreferences.setStatus(context, "Android denied SMS forwarding permission.");
                } catch (RuntimeException e) {
                    ForwardingPreferences.setStatus(context, "Forwarding failed for " + profile.number + ".");
                }
            }
            if (queuedAnywhere) {
                if (waitingForFollowup) {
                    ForwardingPreferences.setStatus(context,
                            "Sending the full forwarded SMS first; code-only copy will follow only after it succeeds.");
                } else {
                    ForwardingPreferences.setStatus(context,
                            "Forwarded the incoming SMS to matching downstream phone(s).");
                }
            } else {
                ForwardingPreferences.setStatus(context,
                        "Received SMS; current forwarding rules did not match it.");
            }
        }
    }

    private static boolean handleDownstreamCommand(Context context, List<PhoneProfile> profiles,
                                                   String sender, String body) {
        PhoneProfile controller = ShortCodeRelay.findRegisteredProfile(profiles, sender);
        if (controller == null) return false;

        ShortCodeRelay.Command command = ShortCodeRelay.parseCommand(body);
        if (command == null) return false;

        String displayDestination = ShortCodeRelay.formatDestination(command.destination);
        if (!controller.permitsOutgoing(command.destination)) {
            ForwardingPreferences.setStatus(context,
                    "Blocked outgoing message to " + displayDestination + " by the downstream rules.");
            return true;
        }
        if (!hasSendPermission(context)) {
            ForwardingPreferences.setStatus(context,
                    "Received an outgoing command, but Send SMS access is missing.");
            return true;
        }

        try {
            ForwardingPreferences.startReplyRelay(
                    context, controller.number, command.destination, System.currentTimeMillis());
            if (!command.payload.isEmpty()) {
                sendMessage(SmsManager.getDefault(), command.destination, command.payload);
                ForwardingPreferences.setStatus(context,
                        "Sent downstream message to " + displayDestination
                                + "; replies will return for 5 minutes.");
            } else {
                ForwardingPreferences.setStatus(context,
                        "Opened a 5-minute reply window for " + displayDestination + ".");
            }
        } catch (SecurityException e) {
            ForwardingPreferences.setStatus(context, "Android denied the outgoing SMS send.");
        } catch (RuntimeException e) {
            ForwardingPreferences.setStatus(context,
                    "Outgoing SMS failed for " + displayDestination + ".");
        }
        return true;
    }

    private static boolean handleActiveReply(Context context, List<PhoneProfile> profiles,
                                             String sender, String body) {
        long now = System.currentTimeMillis();
        boolean relayed = false;
        for (PhoneProfile profile : profiles) {
            if (profile.outgoingMode == PhoneProfile.OutgoingMode.OFF) continue;
            String activeDestination = ForwardingPreferences.activeReplyDestination(
                    context, profile.number, now);
            if (activeDestination.isEmpty()
                    || !ShortCodeRelay.senderMatchesDestination(sender, activeDestination)) continue;
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context,
                        "Received a reply, but Send SMS access is missing.");
                return true;
            }
            try {
                String relayedBody = "[" + ShortCodeRelay.formatDestination(activeDestination) + "] " + body;
                sendMessage(SmsManager.getDefault(), profile.number, relayedBody);
                relayed = true;
            } catch (SecurityException e) {
                ForwardingPreferences.setStatus(context, "Android denied forwarding the reply.");
            } catch (RuntimeException e) {
                ForwardingPreferences.setStatus(context, "Failed to forward the reply.");
            }
        }
        if (relayed) {
            ForwardingPreferences.setStatus(context,
                    "Relayed a reply to the downstream phone with an active conversation.");
        }
        return relayed;
    }

    private static boolean hasSendPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private static void sendFullThenCode(Context context, SmsManager smsManager,
                                         String destination, String fullMessage, String code) {
        ArrayList<String> parts = smsManager.divideMessage(fullMessage);
        if (parts.isEmpty()) parts.add(fullMessage);

        String transactionId = UUID.randomUUID().toString();
        ForwardDeliveryTracker.start(context, transactionId, parts.size());
        ArrayList<PendingIntent> sentIntents = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            Intent callback = new Intent(context, ForwardDeliveryReceiver.class)
                    .setAction(ForwardDeliveryReceiver.ACTION_FULL_PART_SENT)
                    .putExtra(ForwardDeliveryReceiver.EXTRA_TRANSACTION_ID, transactionId)
                    .putExtra(ForwardDeliveryReceiver.EXTRA_DESTINATION, destination)
                    .putExtra(ForwardDeliveryReceiver.EXTRA_CODE, code);
            int requestCode = (transactionId + ":" + i).hashCode();
            sentIntents.add(PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    callback,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        }

        try {
            if (parts.size() == 1) {
                smsManager.sendTextMessage(destination, null, fullMessage, sentIntents.get(0), null);
            } else {
                smsManager.sendMultipartTextMessage(destination, null, parts, sentIntents, null);
            }
        } catch (RuntimeException e) {
            ForwardDeliveryTracker.cancel(context, transactionId);
            throw e;
        }
    }

    private static void sendMessage(SmsManager smsManager, String destination, String message) {
        ArrayList<String> parts = smsManager.divideMessage(message);
        if (parts.size() <= 1) smsManager.sendTextMessage(destination, null, message, null, null);
        else smsManager.sendMultipartTextMessage(destination, null, parts, null, null);
    }
}
