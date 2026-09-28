package com.tahlor.smsforwarder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MessageFilter {
    private static final Pattern AUTH_KEYWORD = Pattern.compile(
            "(?i)\\b(code|verification|verify|passcode|one-time|otp|pin|auth|login|secret|security|password|expires|do not share|never share)\\b");
    private static final Pattern CODE_TOKEN = Pattern.compile(
            "(?i)\\b(?:[A-Z]{1,3}[- ]?)?([0-9]{3}[- ][0-9]{3}(?![-0-9])|[0-9]{4,8})\\b");
    private static final Pattern SIX_PLUS_DIGITS = Pattern.compile("[0-9]{6,}");

    private MessageFilter() {}

    static boolean containsSecurityCode(String body) {
        if (body == null || body.isEmpty()) return false;
        if (AUTH_KEYWORD.matcher(body).find() && CODE_TOKEN.matcher(body).find()) return true;
        return SIX_PLUS_DIGITS.matcher(body).find();
    }

    static boolean shouldForward(String body, boolean codeOnly) {
        if (body == null || body.isEmpty()) return false;
        return !codeOnly || containsSecurityCode(body);
    }

    static String extractCode(String body) {
        if (body == null || body.isEmpty()) return null;
        if (AUTH_KEYWORD.matcher(body).find()) {
            Matcher tokenMatcher = CODE_TOKEN.matcher(body);
            if (tokenMatcher.find()) {
                String raw = tokenMatcher.group(1) != null
                        ? tokenMatcher.group(1) : tokenMatcher.group();
                return raw.replaceAll("[^0-9A-Za-z]", "");
            }
        }
        Matcher sixPlusMatcher = SIX_PLUS_DIGITS.matcher(body);
        return sixPlusMatcher.find() ? sixPlusMatcher.group() : null;
    }
}
