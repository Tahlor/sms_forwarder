package com.tahlor.smsforwarder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PhoneProfile {
    enum IncomingMode {
        ALL,
        SECURITY_CODES,
        SELECTED,
        OFF;

        static IncomingMode fromStored(String value, boolean legacyEnabled, boolean legacyCodeOnly) {
            if (value != null) {
                try {
                    return valueOf(value);
                } catch (IllegalArgumentException ignored) {}
            }
            if (!legacyEnabled) return OFF;
            return legacyCodeOnly ? SECURITY_CODES : ALL;
        }
    }

    enum OutgoingMode {
        ANY,
        SHORT_CODES,
        SELECTED,
        OFF;

        static OutgoingMode fromStored(String value, boolean legacyRelayEnabled) {
            if (value != null) {
                try {
                    return valueOf(value);
                } catch (IllegalArgumentException ignored) {}
            }
            return legacyRelayEnabled ? SHORT_CODES : OFF;
        }
    }

    final String number;
    final IncomingMode incomingMode;
    final List<String> incomingAllowList;
    final List<String> incomingBlockList;
    final boolean codeCopyFollowup;
    final OutgoingMode outgoingMode;
    final List<String> outgoingAllowList;
    final List<String> outgoingBlockList;

    PhoneProfile(String number,
                 IncomingMode incomingMode,
                 List<String> incomingAllowList,
                 List<String> incomingBlockList,
                 boolean codeCopyFollowup,
                 OutgoingMode outgoingMode,
                 List<String> outgoingAllowList,
                 List<String> outgoingBlockList) {
        this.number = number == null ? "" : number.trim();
        this.incomingMode = incomingMode == null ? IncomingMode.OFF : incomingMode;
        this.incomingAllowList = cleanList(incomingAllowList);
        this.incomingBlockList = cleanList(incomingBlockList);
        this.codeCopyFollowup = codeCopyFollowup;
        this.outgoingMode = outgoingMode == null ? OutgoingMode.OFF : outgoingMode;
        this.outgoingAllowList = cleanList(outgoingAllowList);
        this.outgoingBlockList = cleanList(outgoingBlockList);
    }

    // Compatibility constructor for legacy settings/tests.
    PhoneProfile(String number, boolean forwardEnabled, boolean codeOnly,
                 boolean codeCopyFollowup, boolean relayEnabled) {
        this(number,
                forwardEnabled ? (codeOnly ? IncomingMode.SECURITY_CODES : IncomingMode.ALL)
                        : IncomingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList(),
                codeCopyFollowup,
                relayEnabled ? OutgoingMode.SHORT_CODES : OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList());
    }

    boolean hasAnyFeatureEnabled() {
        return incomingMode != IncomingMode.OFF || outgoingMode != OutgoingMode.OFF;
    }

    boolean permitsIncoming(String sender, String body) {
        if (incomingMode == IncomingMode.OFF || body == null || body.isEmpty()) return false;
        if (matchesAny(sender, incomingBlockList)) return false;
        if (incomingMode == IncomingMode.SELECTED && !matchesAny(sender, incomingAllowList)) return false;
        return incomingMode != IncomingMode.SECURITY_CODES || MessageFilter.containsSecurityCode(body);
    }

    boolean permitsOutgoing(String destination) {
        if (outgoingMode == OutgoingMode.OFF) return false;
        if (matchesAny(destination, outgoingBlockList)) return false;
        if (outgoingMode == OutgoingMode.SELECTED) return matchesAny(destination, outgoingAllowList);
        return outgoingMode != OutgoingMode.SHORT_CODES || ShortCodeRelay.isShortCode(destination);
    }

    String summary() {
        StringBuilder summary = new StringBuilder(number);
        summary.append(" — incoming: ").append(incomingLabel());
        summary.append("; outgoing: ").append(outgoingLabel());
        if (!incomingBlockList.isEmpty()) summary.append("; ").append(incomingBlockList.size()).append(" incoming blocked");
        if (!outgoingBlockList.isEmpty()) summary.append("; ").append(outgoingBlockList.size()).append(" outgoing blocked");
        return summary.toString();
    }

    private String incomingLabel() {
        switch (incomingMode) {
            case ALL: return "all";
            case SECURITY_CODES: return "security codes";
            case SELECTED: return "selected senders";
            default: return "off";
        }
    }

    private String outgoingLabel() {
        switch (outgoingMode) {
            case ANY: return "any number";
            case SHORT_CODES: return "short codes";
            case SELECTED: return "selected numbers";
            default: return "off";
        }
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        ArrayList<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && !containsEquivalent(result, trimmed)) result.add(trimmed);
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean containsEquivalent(List<String> values, String candidate) {
        for (String value : values) {
            if (addressesMatch(value, candidate)) return true;
        }
        return false;
    }

    private static boolean matchesAny(String address, List<String> rules) {
        for (String rule : rules) {
            if (addressesMatch(address, rule)) return true;
        }
        return false;
    }

    private static boolean addressesMatch(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isEmpty() || b.isEmpty()) return false;
        if (isNumericAddress(a) && isNumericAddress(b)) return ShortCodeRelay.sameAddress(a, b);
        return a.equalsIgnoreCase(b);
    }

    private static boolean isNumericAddress(String value) {
        return value.matches("[+()0-9 .-]+");
    }
}
