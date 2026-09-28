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
                    "Incoming SMS reached the app, but no downstream phone is configured.");
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, StringBuilder> entry : bodiesBySender.entrySet()) {
            String sender = entry.getKey();
            String body = entry.getValue().toString();
            String displaySender = ShortCodeRelay.formatSenderForDisplay(sender);
            ForwardingPreferences.setStatus(context,
                    "Incoming SMS received from " + displaySender + "; evaluating rules.");

            if (body.startsWith(FORWARD_PREFIX)) {
                ForwardingPreferences.setStatus(context,
                        "Ignored an SMS Forwarder acknowledgement to prevent a forwarding loop.");
                continue;
            }

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
                        "Incoming SMS matched the receiver, but Send SMS access is missing.");
                continue;
            }

            boolean queuedAnywhere = false;
            int matchedProfiles = 0;
            for (PhoneProfile profile : profiles) {
                if (!profile.permitsIncoming(sender, body)) continue;
                matchedProfiles++;

                String forwarded = FORWARD_PREFIX + "\nFrom: " + displaySender + "\n" + body;
                String extractedCode = MessageFilter.extractCode(body);
                try {
                    SmsManager smsManager = SmsManager.getDefault();
                    sendTrackedMessage(context, smsManager, profile.number, forwarded);
                    queuedAnywhere = true;
                    ForwardingPreferences.setStatus(context,
                            "Incoming SMS from " + displaySender
                                    + " matched forwarding rules; full message queued for "
                                    + profile.number + ".");

                    if (profile.codeCopyFollowup && extractedCode != null) {
                        try {
                            smsManager.sendTextMessage(
                                    profile.number, null, extractedCode, null, null);
                            ForwardingPreferences.setStatus(context,
                                    "Code-only copy also queued for " + profile.number + ".");
                        } catch (RuntimeException e) {
                            ForwardingPreferences.setStatus(context,
                                    "Full message was queued for " + profile.number
                                            + ", but the code-only copy could not be queued.");
                        }
                    }
                } catch (SecurityException e) {
                    ForwardingPreferences.setStatus(context,
                            "Android denied SMS forwarding permission for " + profile.number + ".");
                } catch (RuntimeException e) {
                    ForwardingPreferences.setStatus(context,
                            "Forwarding could not be queued for " + profile.number + ".");
                }
            }

            if (!queuedAnywhere) {
                if (matchedProfiles == 0) {
                    ForwardingPreferences.setStatus(context,
                            "Incoming SMS from " + displaySender
                                    + " was received, but no phone's authorization and forwarding preference matched it.");
                } else {
                    ForwardingPreferences.setStatus(context,
                            "Incoming SMS matched a profile, but no forwarding send was queued.");
                }
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
                    "Blocked relay command to " + displayDestination
                            + " by this phone's outgoing authorization.");
            if (hasSendPermission(context)) {
                RelayDeliveryReceiver.sendAcknowledgement(
                        context, controller.number,
                        "Blocked: " + displayDestination
                                + " is not allowed by this phone's relay permission.");
            }
            return true;
        }

        if (!hasSendPermission(context)) {
            ForwardingPreferences.setStatus(context,
                    "Relay command received for " + displayDestination
                            + ", but Send SMS access is missing.");
            return true;
        }

        if (command.payload.isEmpty()) {
            ForwardingPreferences.startReplyRelay(
                    context, controller.number, command.destination, System.currentTimeMillis());
            ForwardingPreferences.setStatus(context,
                    "Opened a 5-minute reply window for " + displayDestination + ".");
            RelayDeliveryReceiver.sendAcknowledgement(
                    context, controller.number,
                    "Reply window open for " + displayDestination + " for 5 minutes.");
            return true;
        }

        try {
            sendTrackedRelayMessage(
                    context,
                    SmsManager.getDefault(),
                    controller.number,
                    command.destination,
                    command.payload);
            ForwardingPreferences.setStatus(context,
                    "Relay command accepted for " + displayDestination
                            + "; waiting for carrier send result.");
        } catch (SecurityException e) {
            ForwardingPreferences.setStatus(context,
                    "Android denied the relay send to " + displayDestination + ".");
            RelayDeliveryReceiver.sendAcknowledgement(
                    context, controller.number,
                    "Failed: Android denied the send to " + displayDestination + ".");
        } catch (RuntimeException e) {
            ForwardingPreferences.setStatus(context,
                    "Relay send could not be queued for " + displayDestination + ".");
            RelayDeliveryReceiver.sendAcknowledgement(
                    context, controller.number,
                    "Failed to queue the send to " + displayDestination + ".");
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
                        "Reply reached the app, but Send SMS access is missing.");
                return true;
            }
            try {
                String relayedBody = "[" + ShortCodeRelay.formatDestination(activeDestination)
                        + "] " + body;
                sendMessage(SmsManager.getDefault(), profile.number, relayedBody);
                ForwardingPreferences.setStatus(context,
                        "Relayed a reply from "
                                + ShortCodeRelay.formatDestination(activeDestination)
                                + " to " + profile.number + ".");
                relayed = true;
            } catch (SecurityException e) {
                ForwardingPreferences.setStatus(context,
                        "Android denied forwarding an active-conversation reply.");
            } catch (RuntimeException e) {
                ForwardingPreferences.setStatus(context,
                        "Failed to forward an active-conversation reply.");
            }
        }
        return relayed;
    }

    private static boolean hasSendPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static void sendTrackedMessage(Context context, SmsManager smsManager,
                                           String destination, String message) {
        ArrayList<String> parts = smsManager.divideMessage(message);
        if (parts.isEmpty()) parts.add(message);

        String transactionId = UUID.randomUUID().toString();
        ForwardDeliveryTracker.start(context, transactionId, parts.size());
        ArrayList<PendingIntent> sentIntents = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            Intent callback = new Intent(context, ForwardDeliveryReceiver.class)
                    .setAction(ForwardDeliveryReceiver.ACTION_FULL_PART_SENT)
                    .putExtra(ForwardDeliveryReceiver.EXTRA_TRANSACTION_ID, transactionId);
            int requestCode = (transactionId + ":full:" + i).hashCode();
            sentIntents.add(PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    callback,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        }

        try {
            if (parts.size() == 1) {
                smsManager.sendTextMessage(destination, null, message, sentIntents.get(0), null);
            } else {
                smsManager.sendMultipartTextMessage(destination, null, parts, sentIntents, null);
            }
        } catch (RuntimeException e) {
            ForwardDeliveryTracker.cancel(context, transactionId);
            throw e;
        }
    }

    private static void sendTrackedRelayMessage(Context context, SmsManager smsManager,
                                                String controller, String destination,
                                                String message) {
        ArrayList<String> parts = smsManager.divideMessage(message);
        if (parts.isEmpty()) parts.add(message);

        String transactionId = UUID.randomUUID().toString();
        RelayDeliveryTracker.start(
                context, transactionId, parts.size(), controller, destination);
        ArrayList<PendingIntent> sentIntents = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            Intent callback = new Intent(context, RelayDeliveryReceiver.class)
                    .setAction(RelayDeliveryReceiver.ACTION_RELAY_PART_SENT)
                    .putExtra(RelayDeliveryReceiver.EXTRA_TRANSACTION_ID, transactionId);
            int requestCode = (transactionId + ":relay:" + i).hashCode();
            sentIntents.add(PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    callback,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
        }

        try {
            if (parts.size() == 1) {
                smsManager.sendTextMessage(destination, null, message, sentIntents.get(0), null);
            } else {
                smsManager.sendMultipartTextMessage(destination, null, parts, sentIntents, null);
            }
        } catch (RuntimeException e) {
            RelayDeliveryTracker.cancel(context, transactionId);
            throw e;
        }
    }

    private static void sendMessage(SmsManager smsManager, String destination, String message) {
        ArrayList<String> parts = smsManager.divideMessage(message);
        if (parts.size() <= 1) smsManager.sendTextMessage(destination, null, message, null, null);
        else smsManager.sendMultipartTextMessage(destination, null, parts, null, null);
    }
}
