package com.tahlor.smsforwarder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MessageFilter {
    private static final Pattern SIX_PLUS_DIGITS = Pattern.compile("[0-9]{6,}");

    private MessageFilter() {}

    static boolean containsSecurityCode(String body) {
        return body != null && !body.isEmpty() && SIX_PLUS_DIGITS.matcher(body).find();
    }

    static boolean shouldForward(String body, boolean codeOnly) {
        if (body == null || body.isEmpty()) return false;
        return !codeOnly || containsSecurityCode(body);
    }

    static String extractCode(String body) {
        if (body == null || body.isEmpty()) return null;
        Matcher matcher = SIX_PLUS_DIGITS.matcher(body);
        return matcher.find() ? matcher.group() : null;
    }
}
