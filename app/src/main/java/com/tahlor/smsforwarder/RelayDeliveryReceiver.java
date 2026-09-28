package com.tahlor.smsforwarder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

public final class RelayDeliveryReceiver extends BroadcastReceiver {
    static final String ACTION_RELAY_PART_SENT =
            "com.tahlor.smsforwarder.action.RELAY_PART_SENT";
    static final String EXTRA_TRANSACTION_ID = "transaction_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_RELAY_PART_SENT.equals(intent.getAction())) return;
        String transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID);
        if (transactionId == null || transactionId.isEmpty()) return;

        RelayDeliveryTracker.Result result = RelayDeliveryTracker.recordPart(
                context, transactionId, getResultCode() == Activity.RESULT_OK);
        if (result.completion == RelayDeliveryTracker.Completion.PENDING
                || result.completion == RelayDeliveryTracker.Completion.ALREADY_FINISHED) {
            return;
        }

        String displayDestination = ShortCodeRelay.formatDestination(result.destination);
        if (result.completion == RelayDeliveryTracker.Completion.SUCCESS) {
            ForwardingPreferences.startReplyRelay(
                    context, result.controller, result.destination, System.currentTimeMillis());
            ForwardingPreferences.setStatus(context,
                    "Relay sent successfully to " + displayDestination
                            + "; replies will return for 5 minutes.");
            sendAcknowledgement(context, result.controller,
                    "Sent to " + displayDestination + ". Replies will return for 5 minutes.");
        } else {
            ForwardingPreferences.setStatus(context,
                    "Relay send failed for " + displayDestination + ".");
            sendAcknowledgement(context, result.controller,
                    "Failed to send to " + displayDestination + ".");
        }
    }

    static void sendAcknowledgement(Context context, String controller, String message) {
        if (controller == null || controller.isEmpty()) return;
        try {
            SmsManager.getDefault().sendTextMessage(
                    controller, null, SmsReceiver.FORWARD_PREFIX + "\n" + message, null, null);
        } catch (RuntimeException e) {
            ForwardingPreferences.setStatus(context,
                    "Relay result was recorded, but the controller acknowledgement could not be sent.");
        }
    }
}
