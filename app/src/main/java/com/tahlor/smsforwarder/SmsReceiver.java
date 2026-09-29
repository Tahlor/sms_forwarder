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
    static final String FORWARD_PREFIX = "[FWD]";
    static final String LEGACY_FORWARD_PREFIX = "[SMS Forwarder]";

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
            ForwardingPreferences.logActivity(context,
                    "Incoming SMS received from " + displaySender + "; evaluating rules.");

            if (body.startsWith(FORWARD_PREFIX)
                    || body.startsWith(LEGACY_FORWARD_PREFIX)) {
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
            List<String> deliveredDestinations = new ArrayList<>();
            for (PhoneProfile profile : profiles) {
                if (!profile.permitsIncoming(sender, body)) continue;
                matchedProfiles++;

                boolean duplicateDestination = false;
                for (String delivered : deliveredDestinations) {
                    if (ShortCodeRelay.sameAddress(delivered, profile.number)) {
                        duplicateDestination = true;
                        break;
                    }
                }
                if (duplicateDestination) {
                    ForwardingPreferences.logActivity(context,
                            "Skipped duplicate forwarding profile for " + profile.number + ".");
                    continue;
                }
                deliveredDestinations.add(profile.number);

                String forwarded = buildForwardedMessage(displaySender, body);
                String extractedCode = MessageFilter.extractCode(body);
                try {
                    SmsManager smsManager = SmsManager.getDefault();
                    int partCount = sendTrackedMessage(
                            context, smsManager, profile.number, forwarded);
                    queuedAnywhere = true;
                    ForwardingPreferences.setStatus(context,
                            "Incoming SMS from " + displaySender
                                    + " matched forwarding rules; full message queued for "
                                    + profile.number + " as " + partCount
                                    + (partCount == 1 ? " SMS part." : " SMS parts."));

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

    static boolean handleDownstreamCommand(Context context, List<PhoneProfile> profiles,
                                           String sender, String body) {
        PhoneProfile controller = ShortCodeRelay.findRegisteredProfile(profiles, sender);
        if (controller == null) return false;

        ShortCodeRelay.ManagementCommand management =
                ShortCodeRelay.parseManagementCommand(body);
        if (management != null) {
            return handleManagementCommand(context, controller, management);
        }

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

    private static boolean handleManagementCommand(
            Context context, PhoneProfile controller,
            ShortCodeRelay.ManagementCommand command) {
        if (command.action == ShortCodeRelay.ManagementAction.PING) {
            if (!hasSendPermission(context)) {
                ForwardingPreferences.setStatus(context,
                        "PING reached SMS Forwarder from " + controller.number
                                + ", but Send SMS access is missing.");
                return true;
            }
            sendManagementReply(context, controller.number,
                    "PING received. SMS path is working. Remote management: "
                            + (controller.allowRemoteCommands ? "enabled" : "disabled")
                            + ". Incoming auth: " + controller.authorizationLabel()
                            + ". Auto-forward: " + controller.preferenceLabel()
                            + ". Outgoing auth: " + controller.outgoingLabel() + ".");
            ForwardingPreferences.setStatus(context,
                    "PING received and answered for " + controller.number + ".");
            return true;
        }

        if (!controller.allowRemoteCommands) {
            ForwardingPreferences.setStatus(context,
                    "Blocked remote-management command from " + controller.number
                            + " because that capability is disabled.");
            if (hasSendPermission(context)) {
                sendManagementReply(context, controller.number,
                        "Remote management is disabled for this phone. Enable it on the host phone first.");
            }
            return true;
        }

        if (!hasSendPermission(context)) {
            ForwardingPreferences.setStatus(context,
                    "Remote-management command received from " + controller.number
                            + ", but Send SMS access is missing.");
            return true;
        }

        switch (command.action) {
            case HELP:
                sendManagementReply(context, controller.number,
                        "Commands:\n"
                                + "PING - test SMS receipt + response without changing settings\n"
                                + "AUTH <ANY|SELECTED|OFF> - hard incoming authorization\n"
                                + "ALLOW <sender> - authorize + always forward sender\n"
                                + "UNALLOW <sender> - remove authorization + always-forward entry\n"
                                + "BLOCK/UNBLOCK <sender> - hard deny/remove deny\n"
                                + "MODE <CODES|ALL|SELECTED|OFF> - automatic forwarding\n"
                                + "PREFER/UNPREFER <sender> - selected auto-forward list\n"
                                + "LIST - show rules\n"
                                + "STATUS - show last status\n"
                                + "[destination] message - relay an SMS");
                ForwardingPreferences.setStatus(context,
                        "Sent remote-management help to " + controller.number + ".");
                return true;

            case STATUS:
                sendManagementReply(context, controller.number,
                        "Last status: " + ForwardingPreferences.status(context));
                return true;

            case LIST: {
                PhoneProfile current = currentProfile(context, controller);
                String reply = "Rules:\n"
                        + "Incoming auth: " + current.authorizationLabel() + "\n"
                        + "Authorized list: " + formatList(current.incomingAuthorizedSenders) + "\n"
                        + "Blocked: " + formatList(current.incomingBlockedSenders) + "\n"
                        + "Auto-forward: " + current.preferenceLabel() + "\n"
                        + "Preferred list: " + formatList(current.incomingPreferredSenders) + "\n"
                        + "Outgoing auth: " + current.outgoingLabel() + "\n"
                        + "Remote management: enabled";
                sendManagementReply(context, controller.number, reply);
                ForwardingPreferences.setStatus(context,
                        "Sent active rules to " + controller.number + ".");
                return true;
            }

            case ALLOW: {
                if (!requireArgument(context, controller.number, command.argument,
                        "ALLOW <sender>")) return true;
                PhoneProfile updated = ForwardingPreferences.addIncomingAuthorizedSender(
                        context, controller.number, command.argument);
                if (updated == null) {
                    sendManagementReply(context, controller.number,
                            "Could not update the authorization list.");
                    return true;
                }
                updated = ForwardingPreferences.addIncomingPreferredSender(
                        context, controller.number, command.argument);
                if (updated != null
                        && updated.incomingPreference == PhoneProfile.IncomingPreference.OFF) {
                    updated = ForwardingPreferences.setIncomingPreference(
                            context, controller.number, PhoneProfile.IncomingPreference.SELECTED);
                }
                String display = ShortCodeRelay.formatSenderForDisplay(command.argument);
                sendManagementReply(context, controller.number,
                        "Allowed " + display + ": it is inside the security authorization "
                                + "and will always forward unless blocked.");
                ForwardingPreferences.setStatus(context,
                        "Remote management allowed and preferred " + display + " for "
                                + controller.number + ".");
                return true;
            }

            case UNALLOW: {
                if (!requireArgument(context, controller.number, command.argument,
                        "UNALLOW <sender>")) return true;
                ForwardingPreferences.removeIncomingAuthorizedSender(
                        context, controller.number, command.argument);
                String display = ShortCodeRelay.formatSenderForDisplay(command.argument);
                sendManagementReply(context, controller.number,
                        "Removed " + display + " from authorization and the always-forward list.");
                ForwardingPreferences.setStatus(context,
                        "Remote management removed ALLOW for " + display
                                + " on " + controller.number + ".");
                return true;
            }

            case BLOCK: {
                if (!requireArgument(context, controller.number, command.argument,
                        "BLOCK <sender>")) return true;
                ForwardingPreferences.addIncomingBlock(
                        context, controller.number, command.argument);
                String display = ShortCodeRelay.formatSenderForDisplay(command.argument);
                sendManagementReply(context, controller.number,
                        "Blocked " + display + ". Block always wins.");
                ForwardingPreferences.setStatus(context,
                        "Remote management blocked " + display + " for "
                                + controller.number + ".");
                return true;
            }

            case UNBLOCK: {
                if (!requireArgument(context, controller.number, command.argument,
                        "UNBLOCK <sender>")) return true;
                ForwardingPreferences.removeIncomingBlock(
                        context, controller.number, command.argument);
                String display = ShortCodeRelay.formatSenderForDisplay(command.argument);
                sendManagementReply(context, controller.number,
                        "Removed the hard block for " + display + ".");
                ForwardingPreferences.setStatus(context,
                        "Remote management unblocked " + display + " for "
                                + controller.number + ".");
                return true;
            }

            case AUTH: {
                PhoneProfile.IncomingAuthorization authorization =
                        parseAuthorization(command.argument);
                if (authorization == null) {
                    sendManagementReply(context, controller.number,
                            "Usage: AUTH <ANY|SELECTED|OFF>");
                    return true;
                }
                ForwardingPreferences.setIncomingAuthorization(
                        context, controller.number, authorization);
                sendManagementReply(context, controller.number,
                        "Incoming security authorization set to "
                                + authorizationLabel(authorization) + ".");
                ForwardingPreferences.setStatus(context,
                        "Remote management changed incoming authorization for "
                                + controller.number + " to "
                                + authorizationLabel(authorization) + ".");
                return true;
            }

            case MODE: {
                PhoneProfile.IncomingPreference preference =
                        parsePreference(command.argument);
                if (preference == null) {
                    sendManagementReply(context, controller.number,
                            "Usage: MODE <CODES|ALL|SELECTED|OFF>");
                    return true;
                }
                ForwardingPreferences.setIncomingPreference(
                        context, controller.number, preference);
                sendManagementReply(context, controller.number,
                        "Automatic forwarding set to " + preferenceLabel(preference)
                                + ". It cannot exceed the security authorization.");
                ForwardingPreferences.setStatus(context,
                        "Remote management changed automatic forwarding for "
                                + controller.number + " to " + preferenceLabel(preference) + ".");
                return true;
            }

            case PREFER: {
                if (!requireArgument(context, controller.number, command.argument,
                        "PREFER <sender>")) return true;
                PhoneProfile current = currentProfile(context, controller);
                if (!current.permitsIncomingAuthorization(command.argument)) {
                    sendManagementReply(context, controller.number,
                            "Not added: that sender is outside the current security authorization "
                                    + "or is blocked. Authorize it first.");
                    return true;
                }
                ForwardingPreferences.addIncomingPreferredSender(
                        context, controller.number, command.argument);
                String display = ShortCodeRelay.formatSenderForDisplay(command.argument);
                sendManagementReply(context, controller.number,
                        "Added " + display + " to the selected automatic-forward list.");
                ForwardingPreferences.setStatus(context,
                        "Remote management preferred " + display + " for "
                                + controller.number + ".");
                return true;
            }

            case UNPREFER: {
                if (!requireArgument(context, controller.number, command.argument,
                        "UNPREFER <sender>")) return true;
                ForwardingPreferences.removeIncomingPreferredSender(
                        context, controller.number, command.argument);
                String display = ShortCodeRelay.formatSenderForDisplay(command.argument);
                sendManagementReply(context, controller.number,
                        "Removed " + display + " from the selected automatic-forward list.");
                ForwardingPreferences.setStatus(context,
                        "Remote management removed preferred sender " + display + " for "
                                + controller.number + ".");
                return true;
            }
        }
        return true;
    }

    private static boolean requireArgument(
            Context context, String controller, String argument, String usage) {
        if (argument != null && !argument.trim().isEmpty()) return true;
        sendManagementReply(context, controller, "Usage: " + usage);
        return false;
    }

    private static PhoneProfile currentProfile(Context context, PhoneProfile fallback) {
        PhoneProfile current = ShortCodeRelay.findRegisteredProfile(
                ForwardingPreferences.profiles(context), fallback.number);
        return current == null ? fallback : current;
    }

    private static PhoneProfile.IncomingAuthorization parseAuthorization(String argument) {
        String value = argument == null ? "" : argument.trim().toUpperCase();
        if (value.equals("ANY") || value.equals("ALL")) {
            return PhoneProfile.IncomingAuthorization.ANY;
        }
        if (value.equals("SELECTED") || value.equals("LIST")) {
            return PhoneProfile.IncomingAuthorization.SELECTED;
        }
        if (value.equals("OFF") || value.equals("NONE")) {
            return PhoneProfile.IncomingAuthorization.NONE;
        }
        return null;
    }

    private static PhoneProfile.IncomingPreference parsePreference(String argument) {
        String value = argument == null ? "" : argument.trim().toUpperCase();
        if (value.startsWith("CODE")) return PhoneProfile.IncomingPreference.SECURITY_CODES;
        if (value.equals("ALL")) return PhoneProfile.IncomingPreference.ALL_AUTHORIZED;
        if (value.equals("SELECTED") || value.equals("LIST")) {
            return PhoneProfile.IncomingPreference.SELECTED;
        }
        if (value.equals("OFF") || value.equals("NONE")) {
            return PhoneProfile.IncomingPreference.OFF;
        }
        return null;
    }

    private static String authorizationLabel(PhoneProfile.IncomingAuthorization value) {
        switch (value) {
            case ANY: return "Any sender";
            case SELECTED: return "Selected senders";
            default: return "Off";
        }
    }

    private static String preferenceLabel(PhoneProfile.IncomingPreference value) {
        switch (value) {
            case ALL_AUTHORIZED: return "All authorized messages";
            case SECURITY_CODES: return "Security codes only";
            case SELECTED: return "Selected authorized senders";
            default: return "Off";
        }
    }

    private static String formatList(List<String> items) {
        if (items == null || items.isEmpty()) return "None";
        StringBuilder result = new StringBuilder();
        for (String item : items) {
            if (result.length() > 0) result.append(", ");
            result.append(item);
        }
        return result.toString();
    }

    private static void sendManagementReply(Context context, String controller, String message) {
        if (!hasSendPermission(context) || controller == null || controller.isEmpty()) return;
        try {
            sendMessage(SmsManager.getDefault(), controller,
                    FORWARD_PREFIX + "\n" + message);
        } catch (RuntimeException e) {
            ForwardingPreferences.setStatus(context,
                    "Remote-management result was recorded, but the response SMS could not be sent.");
        }
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

    static String buildForwardedMessage(String displaySender, String body) {
        return FORWARD_PREFIX + " " + displaySender + "\n" + (body == null ? "" : body);
    }

    private static int sendTrackedMessage(Context context, SmsManager smsManager,
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
        return parts.size();
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
        if (parts.size() <= 1) {
            smsManager.sendTextMessage(destination, null, message, null, null);
        } else {
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null);
        }
    }
}
