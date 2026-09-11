package com.tahlor.smsforwarder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ForwardDeliveryReceiver extends BroadcastReceiver {
    static final String ACTION_FULL_PART_SENT =
            "com.tahlor.smsforwarder.action.FULL_PART_SENT";
    static final String EXTRA_TRANSACTION_ID = "transaction_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_FULL_PART_SENT.equals(intent.getAction())) return;

        String transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID);
        if (transactionId == null || transactionId.isEmpty()) return;

        boolean success = getResultCode() == Activity.RESULT_OK;
        ForwardDeliveryTracker.Completion completion =
                ForwardDeliveryTracker.recordPart(context, transactionId, success);

        if (completion == ForwardDeliveryTracker.Completion.PENDING
                || completion == ForwardDeliveryTracker.Completion.ALREADY_FINISHED) {
            return;
        }

        if (completion == ForwardDeliveryTracker.Completion.FAILED) {
            ForwardingPreferences.setStatus(context,
                    "The code-only copy may have arrived, but the full forwarded SMS did not send completely.");
        } else {
            ForwardingPreferences.setStatus(context,
                    "Full forwarded SMS sent successfully; code-only copy was queued immediately.");
        }
    }
}
