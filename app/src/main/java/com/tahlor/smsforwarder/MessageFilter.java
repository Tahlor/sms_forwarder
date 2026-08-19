package com.tahlor.smsforwarder;

import java.util.regex.Pattern;

final class MessageFilter {
    private static final Pattern SIX_PLUS_DIGITS = Pattern.compile("[0-9]{6,}");

    private MessageFilter() {}

    static boolean shouldForward(String body, boolean codeOnly) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return !codeOnly || SIX_PLUS_DIGITS.matcher(body).find();
    }
}
