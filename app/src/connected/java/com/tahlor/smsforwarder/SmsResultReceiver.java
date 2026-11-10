package com.tahlor.smsforwarder;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives each real SmsManager sent callback before a job can be acknowledged as complete. */
public final class SmsResultReceiver extends BroadcastReceiver {
    static final String EXTRA_JOB_ID = "remote_job_id";
    static final String EXTRA_PART_INDEX = "remote_job_part_index";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String jobId = intent.getStringExtra(EXTRA_JOB_ID);
        int part = intent.getIntExtra(EXTRA_PART_INDEX, -1);
        if (jobId == null || part < 0) return;
        boolean success = getResultCode() == Activity.RESULT_OK;
        RemoteJobLedger.recordPartResult(context, jobId, part, success);
        if (!success) ConnectedOptions.setError(context,
                "The carrier reported a remote SMS send failure. Its job will be acknowledged as failed.");
        ConnectedSyncScheduler.scheduleImmediate(context);
    }
}
