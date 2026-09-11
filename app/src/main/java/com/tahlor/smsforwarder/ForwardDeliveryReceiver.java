package com.tahlor.smsforwarder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

public final class ForwardDeliveryReceiver extends BroadcastReceiver {
    static final String ACTION_FULL_PART_SENT =
            "com.tahlor.smsforwarder.action.FULL_PART_SENT";
    static final String EXTRA_TRANSACTION_ID = "transaction_id";
    static final String EXTRA_DESTINATION = "destination";
    static final String EXTRA_CODE = "code";

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
                    "The full forwarded SMS did not send completely, so the code-only copy was not sent.");
            return;
        }

        String destination = intent.getStringExtra(EXTRA_DESTINATION);
        String code = intent.getStringExtra(EXTRA_CODE);
        if (destination == null || destination.isEmpty() || code == null || code.isEmpty()) {
            ForwardingPreferences.setStatus(context,
                    "Full forwarded SMS sent, but the code-only follow-up was unavailable.");
            return;
        }

        try {
            SmsManager.getDefault().sendTextMessage(destination, null, code, null, null);
            ForwardingPreferences.setStatus(context,
                    "Full forwarded SMS sent first; code-only copy queued afterward.");
        } catch (SecurityException e) {
            ForwardingPreferences.setStatus(context,
                    "Full forwarded SMS sent, but Android denied the code-only follow-up.");
        } catch (RuntimeException e) {
            ForwardingPreferences.setStatus(context,
                    "Full forwarded SMS sent, but the code-only follow-up failed.");
        }
    }
}
