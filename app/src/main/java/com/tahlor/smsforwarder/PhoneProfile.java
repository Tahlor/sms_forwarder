package com.tahlor.smsforwarder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PhoneProfile {
    enum IncomingAuthorization {
        NONE,
        ANY,
        SELECTED;

        static IncomingAuthorization fromStored(String value) {
            if (value != null) {
                try {
                    return valueOf(value);
                } catch (IllegalArgumentException ignored) {}
            }
            return NONE;
        }
    }

    enum IncomingPreference {
        OFF,
        ALL_AUTHORIZED,
        SECURITY_CODES,
        SELECTED;

        static IncomingPreference fromStored(String value) {
            if (value != null) {
                try {
                    return valueOf(value);
                } catch (IllegalArgumentException ignored) {}
            }
            return OFF;
        }
    }

    enum OutgoingMode {
        OFF,
        SHORT_CODES,
        SELECTED,
        ANY;

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
    final IncomingAuthorization incomingAuthorization;
    final List<String> incomingAuthorizedSenders;
    final List<String> incomingBlockedSenders;
    final IncomingPreference incomingPreference;
    final List<String> incomingPreferredSenders;
    final boolean codeCopyFollowup;
    final OutgoingMode outgoingMode;
    final List<String> outgoingAllowList;
    final List<String> outgoingBlockList;
    final boolean allowRemoteCommands;

    PhoneProfile(String number,
                 IncomingAuthorization incomingAuthorization,
                 List<String> incomingAuthorizedSenders,
                 List<String> incomingBlockedSenders,
                 IncomingPreference incomingPreference,
                 List<String> incomingPreferredSenders,
                 boolean codeCopyFollowup,
                 OutgoingMode outgoingMode,
                 List<String> outgoingAllowList,
                 List<String> outgoingBlockList,
                 boolean allowRemoteCommands) {
        this.number = number == null ? "" : number.trim();
        this.incomingAuthorization = incomingAuthorization == null
                ? IncomingAuthorization.NONE : incomingAuthorization;
        this.incomingAuthorizedSenders = cleanList(incomingAuthorizedSenders);
        this.incomingBlockedSenders = cleanList(incomingBlockedSenders);
        this.incomingPreference = incomingPreference == null
                ? IncomingPreference.OFF : incomingPreference;
        this.incomingPreferredSenders = cleanList(incomingPreferredSenders);
        this.codeCopyFollowup = codeCopyFollowup;
        this.outgoingMode = outgoingMode == null ? OutgoingMode.OFF : outgoingMode;
        this.outgoingAllowList = cleanList(outgoingAllowList);
        this.outgoingBlockList = cleanList(outgoingBlockList);
        this.allowRemoteCommands = allowRemoteCommands;
    }

    PhoneProfile(String number,
                 IncomingAuthorization incomingAuthorization,
                 List<String> incomingAuthorizedSenders,
                 List<String> incomingBlockedSenders,
                 IncomingPreference incomingPreference,
                 List<String> incomingPreferredSenders,
                 boolean codeCopyFollowup,
                 OutgoingMode outgoingMode,
                 List<String> outgoingAllowList,
                 List<String> outgoingBlockList) {
        this(number, incomingAuthorization, incomingAuthorizedSenders, incomingBlockedSenders,
                incomingPreference, incomingPreferredSenders, codeCopyFollowup, outgoingMode,
                outgoingAllowList, outgoingBlockList, false);
    }

    // Compatibility constructor retained for legacy migration/tests.
    PhoneProfile(String number, boolean forwardEnabled, boolean codeOnly,
                 boolean codeCopyFollowup, boolean relayEnabled) {
        this(number,
                forwardEnabled ? IncomingAuthorization.ANY : IncomingAuthorization.NONE,
                Collections.emptyList(),
                Collections.emptyList(),
                forwardEnabled
                        ? (codeOnly ? IncomingPreference.SECURITY_CODES
                                : IncomingPreference.ALL_AUTHORIZED)
                        : IncomingPreference.OFF,
                Collections.emptyList(),
                codeCopyFollowup,
                relayEnabled ? OutgoingMode.SHORT_CODES : OutgoingMode.OFF,
                Collections.emptyList(),
                Collections.emptyList(),
                false);
    }

    PhoneProfile withIncomingAuthorization(IncomingAuthorization value) {
        return new PhoneProfile(number, value, incomingAuthorizedSenders, incomingBlockedSenders,
                incomingPreference, incomingPreferredSenders, codeCopyFollowup, outgoingMode,
                outgoingAllowList, outgoingBlockList, allowRemoteCommands);
    }

    PhoneProfile withIncomingAuthorizedSenders(List<String> value) {
        return new PhoneProfile(number, incomingAuthorization, value, incomingBlockedSenders,
                incomingPreference, incomingPreferredSenders, codeCopyFollowup, outgoingMode,
                outgoingAllowList, outgoingBlockList, allowRemoteCommands);
    }

    PhoneProfile withIncomingBlockedSenders(List<String> value) {
        return new PhoneProfile(number, incomingAuthorization, incomingAuthorizedSenders, value,
                incomingPreference, incomingPreferredSenders, codeCopyFollowup, outgoingMode,
                outgoingAllowList, outgoingBlockList, allowRemoteCommands);
    }

    PhoneProfile withIncomingPreference(IncomingPreference value) {
        return new PhoneProfile(number, incomingAuthorization, incomingAuthorizedSenders,
                incomingBlockedSenders, value, incomingPreferredSenders, codeCopyFollowup,
                outgoingMode, outgoingAllowList, outgoingBlockList, allowRemoteCommands);
    }

    PhoneProfile withIncomingPreferredSenders(List<String> value) {
        return new PhoneProfile(number, incomingAuthorization, incomingAuthorizedSenders,
                incomingBlockedSenders, incomingPreference, value, codeCopyFollowup, outgoingMode,
                outgoingAllowList, outgoingBlockList, allowRemoteCommands);
    }

    PhoneProfile withRemoteCommands(boolean value) {
        return new PhoneProfile(number, incomingAuthorization, incomingAuthorizedSenders,
                incomingBlockedSenders, incomingPreference, incomingPreferredSenders,
                codeCopyFollowup, outgoingMode, outgoingAllowList, outgoingBlockList, value);
    }

    boolean hasAnyCapabilityEnabled() {
        return incomingAuthorization != IncomingAuthorization.NONE
                || outgoingMode != OutgoingMode.OFF
                || allowRemoteCommands;
    }

    boolean permitsIncomingAuthorization(String sender) {
        if (incomingAuthorization == IncomingAuthorization.NONE) return false;
        if (matchesAny(sender, incomingBlockedSenders)) return false;
        return incomingAuthorization == IncomingAuthorization.ANY
                || matchesAny(sender, incomingAuthorizedSenders);
    }

    boolean permitsIncoming(String sender, String body) {
        if (!permitsIncomingAuthorization(sender) || body == null || body.isEmpty()) return false;
        switch (incomingPreference) {
            case ALL_AUTHORIZED:
                return true;
            case SECURITY_CODES:
                return MessageFilter.containsSecurityCode(body);
            case SELECTED:
                return matchesAny(sender, incomingPreferredSenders);
            default:
                return false;
        }
    }

    boolean permitsOutgoing(String destination) {
        if (outgoingMode == OutgoingMode.OFF) return false;
        if (matchesAny(destination, outgoingBlockList)) return false;
        if (outgoingMode == OutgoingMode.SELECTED) {
            return matchesAny(destination, outgoingAllowList);
        }
        if (outgoingMode == OutgoingMode.SHORT_CODES) {
            return ShortCodeRelay.isShortCode(destination);
        }
        return ShortCodeRelay.isRoutableDestination(destination);
    }

    String summary() {
        String summary = number
                + " — incoming authorization: " + authorizationLabel()
                + "; automatic forwarding: " + preferenceLabel()
                + "; outgoing authorization: " + outgoingLabel();
        if (allowRemoteCommands) summary += "; remote management enabled";
        return summary;
    }

    String authorizationLabel() {
        switch (incomingAuthorization) {
            case ANY: return "any sender";
            case SELECTED: return "selected senders";
            default: return "none";
        }
    }

    String preferenceLabel() {
        switch (incomingPreference) {
            case ALL_AUTHORIZED: return "all authorized messages";
            case SECURITY_CODES: return "security codes";
            case SELECTED: return "selected authorized senders";
            default: return "nothing";
        }
    }

    String outgoingLabel() {
        switch (outgoingMode) {
            case ANY: return "any destination";
            case SHORT_CODES: return "short codes";
            case SELECTED: return "selected destinations";
            default: return "none";
        }
    }

    static boolean addressesMatch(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isEmpty() || b.isEmpty()) return false;
        if (isNumericAddress(a) && isNumericAddress(b)) return ShortCodeRelay.sameAddress(a, b);
        return a.equalsIgnoreCase(b);
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

    private static boolean isNumericAddress(String value) {
        return value.matches("[+()0-9 .-]+");
    }
}
