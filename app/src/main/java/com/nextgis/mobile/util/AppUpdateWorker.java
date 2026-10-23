package com.nextgis.mobile.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;
import com.nextgis.mobile.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;

/** OS-owned scheduling, including process death/reboot; never opens an installer or an Activity. */
public final class AppUpdateWorker extends Worker {
    static final String PERIODIC_WORK = "app-update-periodic";
    static final String IMMEDIATE_WORK = "app-update-on-launch";
    private volatile Call activeCall;

    public AppUpdateWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    static Constraints constraints() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_ROAMING)
                .setRequiresStorageNotLow(true).build();
    }

    public static void schedule(Context context) {
        // Verification has a separate product identity and must not contact production releases.
        if (BuildConfig.FIELD_VERIFICATION) return;
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(AppUpdateWorker.class, 6, TimeUnit.HOURS)
                        .setConstraints(constraints()).addTag(PERIODIC_WORK)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
    }

    static void enqueue(Context context) {
        if (BuildConfig.FIELD_VERIFICATION) return;
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(AppUpdateWorker.class)
                        .setConstraints(constraints()).addTag(IMMEDIATE_WORK)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
    }

    @NonNull @Override public Result doWork() {
        if (BuildConfig.FIELD_VERIFICATION) return Result.failure();
        try {
            return AppUpdateManager.prepareUpdate(getApplicationContext(), this)
                    ? Result.success() : Result.retry();
        } catch (Exception error) {
            HyperLog.w(Constants.TAG, "Updater background preparation deferred attempt="
                    + getRunAttemptCount() + " type=" + error.getClass().getSimpleName(), error);
            return Result.retry();
        } finally {
            activeCall = null;
        }
    }

    void attach(Call call) {
        activeCall = call;
        if (isStopped()) call.cancel();
    }

    @Override public void onStopped() {
        Call call = activeCall;
        if (call != null) call.cancel();
    }
}
