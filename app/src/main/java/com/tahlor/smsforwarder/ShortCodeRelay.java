package com.tahlor.smsforwarder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShortCodeRelay {
    static final long WINDOW_MS = 5L * 60L * 1000L;
    private static final Pattern COMMAND = Pattern.compile(
            "^\\s*\\[([0-9]{6})\\]\\s*(.*?)\\s*$",
            Pattern.DOTALL);

    private ShortCodeRelay() {}

    static Command parseCommand(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = COMMAND.matcher(body);
        if (!matcher.matches()) {
            return null;
        }
        return new Command(matcher.group(1), matcher.group(2).trim());
    }

    static boolean sameAddress(String first, String second) {
        String a = digitsOnly(first);
        String b = digitsOnly(second);
        return !a.isEmpty() && a.equals(b);
    }

    static boolean senderIsShortCode(String sender, String shortCode) {
        return shortCode != null
                && shortCode.matches("[0-9]{6}")
                && digitsOnly(sender).equals(shortCode);
    }

    private static String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
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
