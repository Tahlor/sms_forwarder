package com.tahlor.smslinkautomator;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleEngine {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENDER = Pattern.compile("[0-9]{3,15}");
    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?)+");
    public static final int MAX_URLS_PER_MESSAGE = 3;
    private RuleEngine() { }

    public static String normalizeSender(String sender) {
        if (sender == null) return "";
        return sender.replaceAll("[ ()+\\-]", "").trim();
    }

    public static boolean matchesSender(Rule rule, String sender) {
        return SENDER.matcher(normalizeSender(rule.sender)).matches()
                && normalizeSender(rule.sender).equals(normalizeSender(sender));
    }

    public static boolean headsUpMatches(Rule rule, String message) {
        return rule.autoArm && rule.headsUpPhrase != null
                && !rule.headsUpPhrase.trim().isEmpty() && message != null
                && message.toLowerCase(Locale.ROOT).contains(
                    rule.headsUpPhrase.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean timedArmActive(Rule rule, long nowMs) {
        long maxDuration = 1_440L * 60_000;
        long duration = Math.max(1, Math.min(1440, rule.durationMinutes)) * 60_000L;
        // Fail closed after device clock changes, corrupt state, or stale long-lived windows.
        return rule.armedAtMs > 0 && nowMs >= rule.armedAtMs
                && rule.armedUntilMs > nowMs
                && rule.armedUntilMs - rule.armedAtMs <= maxDuration
                && rule.armedUntilMs - rule.armedAtMs <= duration;
    }

    public static Decision evaluate(Rule rule, String message, long nowMs) {
        boolean headsUp = headsUpMatches(rule, message);
        boolean alreadyArmed = timedArmActive(rule, nowMs);
        // A heads-up SMS is only a trigger, not an auto-follow event in timed-only mode.
        boolean shouldFollow = rule.alwaysOn || (alreadyArmed && !headsUp);
        if (headsUp) {
            int minutes = Math.max(1, Math.min(1440, rule.durationMinutes));
            rule.armedAtMs = nowMs;
            rule.armedUntilMs = nowMs + minutes * 60_000L;
        }
        return new Decision(headsUp, shouldFollow);
    }

    public static final class Decision {
        public final boolean armed;
        public final boolean shouldFollow;
        Decision(boolean armed, boolean shouldFollow) {
            this.armed = armed;
            this.shouldFollow = shouldFollow;
        }
    }

    public static String canonicalHost(String host) {
        if (host == null) return null;
        try {
            String h = IDN.toASCII(host.trim().toLowerCase(Locale.ROOT), IDN.USE_STD3_ASCII_RULES);
            if (!DOMAIN.matcher(h).matches() || h.length() > 253 || h.endsWith(".local")
                    || h.endsWith(".internal") || h.endsWith(".localhost")
                    || h.endsWith(".test") || h.endsWith(".invalid")
                    || h.endsWith(".example") || h.endsWith(".onion")) return null;
            // No IP literals, loopback, private IPs, or DNS names without a public suffix.
            if (h.matches("[0-9.]+")) return null;
            return h;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Set<String> allowedHosts(Rule rule) {
        Set<String> result = new HashSet<>();
        if (rule.allowedHosts == null) return result;
        for (String item : rule.allowedHosts.split(",")) {
            String host = canonicalHost(item);
            if (host != null) result.add(host);
        }
        return result;
    }

    public static URI safeUri(String url, Rule rule) {
        if (url == null || url.length() > 2048) return null;
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getPort() != -1 && uri.getPort() != 443
                    || uri.getRawFragment() != null || uri.getHost() == null) return null;
            String host = canonicalHost(uri.getHost());
            if (host == null || !allowedHosts(rule).contains(host)) return null;
            return uri;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    public static List<URI> eligibleLinks(Rule rule, String message) {
        List<URI> result = new ArrayList<>();
        if (message == null) return result;
        Matcher matcher = URL.matcher(message);
        Set<String> seen = new HashSet<>();
        while (matcher.find() && result.size() < MAX_URLS_PER_MESSAGE) {
            String raw = matcher.group();
            // Strip sentence punctuation; preserve query strings and path components.
            while (!raw.isEmpty() && ".,;!?:)]}".indexOf(raw.charAt(raw.length() - 1)) >= 0) {
                raw = raw.substring(0, raw.length() - 1);
            }
            URI uri = safeUri(raw, rule);
            if (uri != null && seen.add(uri.toASCIIString())) result.add(uri);
        }
        return result;
    }

    public static String validate(Rule rule) {
        if (rule.name == null || rule.name.trim().isEmpty()) return "Enter a rule name.";
        if (!SENDER.matcher(normalizeSender(rule.sender)).matches()) return "Sender must be an exact 3-15 digit phone number or short code.";
        if (rule.allowedHosts == null || rule.allowedHosts.trim().isEmpty()) return "Enter at least one allowed HTTPS domain.";
        if (rule.allowedHosts.split(",").length > 10) return "At most 10 allowed domains per rule.";
        for (String item : rule.allowedHosts.split(",", -1)) {
            if (canonicalHost(item) == null) return "Invalid domain: " + item.trim() + ". Enter hostnames only, not URLs.";
        }
        if (rule.autoArm && (rule.headsUpPhrase == null || rule.headsUpPhrase.trim().isEmpty()))
            return "Auto-arming requires a nonempty heads-up phrase.";
        if (rule.durationMinutes < 1 || rule.durationMinutes > 1440) return "Duration must be 1-1440 minutes.";
        if (!Rule.MODE_HTTP.equals(rule.mode) && !Rule.MODE_BROWSER.equals(rule.mode))
            return "Choose an execution mode.";
        return null;
    }
}
