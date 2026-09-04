package com.tahlor.smsforwarder;

final class PhoneProfile {
    final String number;
    final boolean forwardEnabled;
    final boolean codeOnly;
    final boolean codeCopyFollowup;
    final boolean relayEnabled;

    PhoneProfile(String number, boolean forwardEnabled, boolean codeOnly,
                 boolean codeCopyFollowup, boolean relayEnabled) {
        this.number = number == null ? "" : number.trim();
        this.forwardEnabled = forwardEnabled;
        this.codeOnly = codeOnly;
        this.codeCopyFollowup = codeCopyFollowup;
        this.relayEnabled = relayEnabled;
    }

    boolean hasAnyFeatureEnabled() {
        return forwardEnabled || relayEnabled;
    }

    String summary() {
        StringBuilder summary = new StringBuilder(number);
        if (forwardEnabled) {
            summary.append(" — forwards ");
            summary.append(codeOnly ? "code messages" : "all SMS");
            if (codeCopyFollowup) summary.append(" + code-only copy");
        }
        if (relayEnabled) {
            if (forwardEnabled) summary.append("; ");
            else summary.append(" — ");
            summary.append("shortcode relay controller");
        }
        if (!hasAnyFeatureEnabled()) summary.append(" — disabled");
        return summary.toString();
    }
}
