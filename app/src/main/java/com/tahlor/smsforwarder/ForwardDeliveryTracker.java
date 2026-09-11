package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

final class ForwardDeliveryTracker {
    private static final String PREFS = "sms_forwarder_delivery";
    private static final String REMAINING_PREFIX = "remaining_";
    private static final String FAILED_PREFIX = "failed_";

    enum Completion {
        PENDING,
        SUCCESS,
        FAILED,
        ALREADY_FINISHED
    }

    private ForwardDeliveryTracker() {}

    static synchronized void start(Context context, String id, int partCount) {
        prefs(context).edit()
                .putInt(REMAINING_PREFIX + id, Math.max(partCount, 1))
                .putBoolean(FAILED_PREFIX + id, false)
                .apply();
    }

    static synchronized Completion recordPart(Context context, String id, boolean success) {
        SharedPreferences prefs = prefs(context);
        String remainingKey = REMAINING_PREFIX + id;
        String failedKey = FAILED_PREFIX + id;
        int remaining = prefs.getInt(remainingKey, 0);
        if (remaining <= 0) return Completion.ALREADY_FINISHED;

        boolean failed = prefs.getBoolean(failedKey, false) || !success;
        remaining--;
        if (remaining > 0) {
            prefs.edit()
                    .putInt(remainingKey, remaining)
                    .putBoolean(failedKey, failed)
                    .apply();
            return Completion.PENDING;
        }

        prefs.edit().remove(remainingKey).remove(failedKey).apply();
        return failed ? Completion.FAILED : Completion.SUCCESS;
    }

    static synchronized void cancel(Context context, String id) {
        prefs(context).edit()
                .remove(REMAINING_PREFIX + id)
                .remove(FAILED_PREFIX + id)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
