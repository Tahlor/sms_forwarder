package com.tahlor.smsforwarder;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShortCodeRelay {
    static final long WINDOW_MS = 5L * 60L * 1000L;
    private static final Pattern COMMAND = Pattern.compile(
            "^\\s*\\[([0-9]{3}-?[0-9]{3})\\]\\s*(.*?)\\s*$",
            Pattern.DOTALL);

    private ShortCodeRelay() {}

    static Command parseCommand(String body) {
        if (body == null) return null;
        Matcher matcher = COMMAND.matcher(body);
        if (!matcher.matches()) return null;
        return new Command(digitsOnly(matcher.group(1)), matcher.group(2).trim());
    }

    static PhoneProfile findRegisteredProfile(List<PhoneProfile> profiles, String sender) {
        if (profiles == null) return null;
        for (PhoneProfile profile : profiles) {
            if (profile != null && sameAddress(sender, profile.number)) return profile;
        }
        return null;
    }

    static String formatShortCode(String shortCode) {
        String digits = digitsOnly(shortCode);
        if (digits.length() == 6) return digits.substring(0, 3) + "-" + digits.substring(3);
        return shortCode == null ? "" : shortCode;
    }

    static String formatSenderForDisplay(String sender) {
        String digits = digitsOnly(sender);
        if (digits.length() == 6) return formatShortCode(digits);
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

    static boolean senderIsShortCode(String sender, String shortCode) {
        return shortCode != null
                && shortCode.matches("[0-9]{6}")
                && digitsOnly(sender).equals(shortCode);
    }

    private static String digitsOnly(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9]", "");
    }

    static final class Command {
        final String shortCode;
        final String payload;

        Command(String shortCode, String payload) {
            this.shortCode = shortCode;
            this.payload = payload;
        }
    }
}
