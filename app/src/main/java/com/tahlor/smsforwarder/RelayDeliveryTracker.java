package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

final class RelayDeliveryTracker {
    private static final String PREFS = "sms_forwarder_relay_delivery";
    private static final String REMAINING = "remaining_";
    private static final String FAILED = "failed_";
    private static final String CONTROLLER = "controller_";
    private static final String DESTINATION = "destination_";

    enum Completion {
        PENDING,
        SUCCESS,
        FAILED,
        ALREADY_FINISHED
    }

    static final class Result {
        final Completion completion;
        final String controller;
        final String destination;

        Result(Completion completion, String controller, String destination) {
            this.completion = completion;
            this.controller = controller;
            this.destination = destination;
        }
    }

    private RelayDeliveryTracker() {}

    static synchronized void start(Context context, String id, int partCount,
                                   String controller, String destination) {
        prefs(context).edit()
                .putInt(REMAINING + id, Math.max(partCount, 1))
                .putBoolean(FAILED + id, false)
                .putString(CONTROLLER + id, controller == null ? "" : controller)
                .putString(DESTINATION + id, destination == null ? "" : destination)
                .apply();
    }

    static synchronized Result recordPart(Context context, String id, boolean success) {
        SharedPreferences prefs = prefs(context);
        int remaining = prefs.getInt(REMAINING + id, 0);
        String controller = prefs.getString(CONTROLLER + id, "");
        String destination = prefs.getString(DESTINATION + id, "");
        if (remaining <= 0) {
            return new Result(Completion.ALREADY_FINISHED, controller, destination);
        }

        boolean failed = prefs.getBoolean(FAILED + id, false) || !success;
        remaining--;
        if (remaining > 0) {
            prefs.edit()
                    .putInt(REMAINING + id, remaining)
                    .putBoolean(FAILED + id, failed)
                    .apply();
            return new Result(Completion.PENDING, controller, destination);
        }

        clear(prefs, id);
        return new Result(failed ? Completion.FAILED : Completion.SUCCESS,
                controller, destination);
    }

    static synchronized void cancel(Context context, String id) {
        clear(prefs(context), id);
    }

    private static void clear(SharedPreferences prefs, String id) {
        prefs.edit()
                .remove(REMAINING + id)
                .remove(FAILED + id)
                .remove(CONTROLLER + id)
                .remove(DESTINATION + id)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
