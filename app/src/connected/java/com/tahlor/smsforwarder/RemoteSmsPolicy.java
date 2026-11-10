package com.tahlor.smsforwarder;

import java.util.List;

/** Guard rails for server-originated SMS. Server settings never change these local rules. */
final class RemoteSmsPolicy {
    private RemoteSmsPolicy() {}

    static String denialReason(boolean optedIn, boolean hasSendPermission,
                               List<PhoneProfile> profiles, String destination) {
        if (!optedIn) return "Remote SMS sending is turned off on this phone.";
        if (!hasSendPermission) return "Android Send SMS permission is not granted.";
        if (!isValidNumber(destination)) return "The recipient is not a valid phone number.";
        if (profiles == null) return "No local outgoing authorization allows this recipient.";
        for (PhoneProfile profile : profiles) {
            if (profile != null && profile.permitsOutgoing(destination)) return null;
        }
        return "This recipient is not allowed by the phone's existing outgoing rules.";
    }

    static boolean isValidNumber(String value) {
        if (value == null) return false;
        String number = value.trim();
        return number.matches("\\+?[1-9][0-9]{6,14}");
    }
}
