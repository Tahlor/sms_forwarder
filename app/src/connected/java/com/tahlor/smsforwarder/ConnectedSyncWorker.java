package com.tahlor.smsforwarder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Network work is isolated from SMS receivers and UI execution. */
public final class ConnectedSyncWorker extends Worker {
    public ConnectedSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            return ConnectedSyncEngine.sync(getApplicationContext())
                    ? Result.success() : Result.retry();
        } catch (RuntimeException e) {
            ConnectedOptions.setError(getApplicationContext(),
                    "Connected sync failed. Try again or disconnect and pair again.");
            return Result.retry();
        }
    }
}
