package com.tahlor.smsforwarder;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShortCodeRelay {
    static final long WINDOW_MS = 5L * 60L * 1000L;
    private static final Pattern COMMAND = Pattern.compile(
            "^\\s*\\[([+]?[-() 0-9.]{3,24})\\]\\s*(.*?)\\s*$",
            Pattern.DOTALL);

    private ShortCodeRelay() {}

    static Command parseCommand(String body) {
        if (body == null) return null;
        Matcher matcher = COMMAND.matcher(body);
        if (!matcher.matches()) return null;
        String destination = digitsOnly(matcher.group(1));
        if (destination.length() < 3 || destination.length() > 15) return null;
        if (matcher.group(1).trim().startsWith("+") && !destination.startsWith("1") && destination.length() == 10) {
            // Keep raw digits rather than guessing a country code.
        }
        return new Command(destination, matcher.group(2).trim());
    }

    static PhoneProfile findRegisteredProfile(List<PhoneProfile> profiles, String sender) {
        if (profiles == null) return null;
        for (PhoneProfile profile : profiles) {
            if (profile != null && sameAddress(sender, profile.number)) return profile;
        }
        return null;
    }

    static boolean isShortCode(String value) {
        String digits = digitsOnly(value);
        return digits.length() >= 3 && digits.length() <= 6;
    }

    static String formatDestination(String destination) {
        String digits = digitsOnly(destination);
        if (digits.length() == 6) return digits.substring(0, 3) + "-" + digits.substring(3);
        return destination == null ? "" : destination;
    }

    static String formatShortCode(String shortCode) {
        return formatDestination(shortCode);
    }

    static String formatSenderForDisplay(String sender) {
        String digits = digitsOnly(sender);
        if (digits.length() == 6) return formatDestination(digits);
        return sender == null ? "" : sender;
    }

    static boolean sameAddress(String first, String second) {
        String a = digitsOnly(first);
        String b = digitsOnly(second);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.length() == 11 && a.startsWith("1") && b.length() == 10)
            return a.substring(1).equals(b);
        if (b.length() == 11 && b.startsWith("1") && a.length() == 10)
            return b.substring(1).equals(a);
        return false;
    }

    static boolean senderMatchesDestination(String sender, String destination) {
        return sameAddress(sender, destination);
    }

    static boolean senderIsShortCode(String sender, String shortCode) {
        return isShortCode(shortCode) && digitsOnly(sender).equals(digitsOnly(shortCode));
    }

    private static String digitsOnly(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9]", "");
    }

    static final class Command {
        final String destination;
        final String payload;

        Command(String destination, String payload) {
            this.destination = destination;
            this.payload = payload;
        }
    }
}
