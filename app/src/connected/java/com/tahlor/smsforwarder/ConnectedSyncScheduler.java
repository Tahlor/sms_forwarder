package com.tahlor.smsforwarder;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.PeriodicWorkRequest.Builder;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Best-effort network scheduling: immediate work plus a 15-minute periodic check. */
final class ConnectedSyncScheduler {
    private static final String PERIODIC = "smsbridge-periodic-sync";
    private static final String IMMEDIATE = "smsbridge-immediate-sync";

    private ConnectedSyncScheduler() {}

    static void schedulePeriodic(Context context) {
        if (!isPaired(context)) return;
        PeriodicWorkRequest request = new Builder(ConnectedSyncWorker.class,
                15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    static void scheduleImmediate(Context context) {
        if (!isPaired(context)) return;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ConnectedSyncWorker.class)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    static void cancel(Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        manager.cancelUniqueWork(PERIODIC);
        manager.cancelUniqueWork(IMMEDIATE);
    }

    private static Constraints networkConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    private static boolean isPaired(Context context) {
        try {
            return ConnectedStore.credentials(context) != null;
        } catch (RuntimeException e) {
            ConnectedOptions.setError(context,
                    "Saved pairing credentials could not be read. Disconnect and pair again.");
            return false;
        }
    }
}
