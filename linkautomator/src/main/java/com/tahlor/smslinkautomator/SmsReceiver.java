package com.tahlor.smslinkautomator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SmsReceiver extends BroadcastReceiver {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;
        Map<String, StringBuilder> messages = new LinkedHashMap<>();
        Map<String, Long> timestamps = new LinkedHashMap<>();
        for (SmsMessage part : parts) {
            if (part == null || part.getOriginatingAddress() == null) continue;
            String sender = part.getOriginatingAddress();
            if (!messages.containsKey(sender)) messages.put(sender, new StringBuilder());
            messages.get(sender).append(part.getMessageBody() == null ? "" : part.getMessageBody());
            long t = part.getTimestampMillis();
            timestamps.put(sender, Math.min(timestamps.getOrDefault(sender, t), t));
        }
        PendingResult pending = goAsync();
        Context application = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                for (Map.Entry<String, StringBuilder> entry : messages.entrySet()) {
                    process(application, entry.getKey(), timestamps.getOrDefault(entry.getKey(), 0L),
                            entry.getValue().toString());
                }
            } finally {
                pending.finish();
            }
        });
    }

    private void process(Context context, String sender, long smsTimestamp, String body) {
        List<Rule> rules = RuleStore.get(context);
        boolean senderMatched = false;
        boolean firstSeen = false;
        Set<String> visited = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Rule rule : rules) {
            if (!RuleEngine.matchesSender(rule, sender)) continue;
            senderMatched = true;
            if (!firstSeen) {
                if (!RuleStore.firstSeen(context, sender, smsTimestamp, body)) {
                    RuleStore.log(context, "Duplicate SMS ignored from " + sender);
                    return;
                }
                firstSeen = true;
            }
            RuleEngine.Decision decision = RuleEngine.evaluate(rule, body, now);
            if (decision.armed) {
                RuleStore.update(context, rule);
                RuleStore.log(context, "Armed " + rule.name + " for " + rule.durationMinutes + " minutes");
            }
            if (!decision.shouldFollow) {
                if (!decision.armed) RuleStore.log(context, "Inactive: " + rule.name + " (sender " + sender + ")");
                continue;
            }
            List<URI> links = RuleEngine.eligibleLinks(rule, body);
            if (links.isEmpty()) {
                RuleStore.log(context, "No eligible HTTPS links: " + rule.name);
                continue;
            }
            for (URI link : links) {
                // Never request the same URL twice when multiple rules match a message.
                if (!visited.add(link.toASCIIString())) continue;
                // We deliberately do not log message contents, query tokens, or full URLs.
                RuleStore.log(context, "Opening permitted link on " + link.getHost() + " for " + rule.name);
                String result = LinkExecutor.execute(context, rule, link);
                RuleStore.log(context, rule.name + ": " + result);
            }
        }
        if (!senderMatched) {
            // Do not even log unknown SMS senders: app processes only configured contacts.
        }
    }
}
