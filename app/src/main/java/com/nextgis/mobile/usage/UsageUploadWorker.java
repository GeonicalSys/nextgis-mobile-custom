package com.nextgis.mobile.usage;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.nextgis.mobile.BuildConfig;
import java.util.concurrent.TimeUnit;

public final class UsageUploadWorker extends Worker {
    public UsageUploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    static void schedule(Context context) {
        if (!UsageAnalytics.enabled()) return;
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager manager = WorkManager.getInstance(context);
        manager.enqueueUniqueWork("mobile-usage-upload", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(UsageUploadWorker.class).setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
        manager.enqueueUniquePeriodicWork("mobile-usage-periodic", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(UsageUploadWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
    }

    @NonNull @Override public Result doWork() {
        if (!UsageAnalytics.enabled()) return Result.success();
        try (UsageStore store = new UsageStore(getApplicationContext())) {
            UsageAnalytics.snapshot(getApplicationContext(), store, "");
            return UsageDelivery.send(store, UsageAnalytics.device(getApplicationContext(), store),
                    BuildConfig.USAGE_ENDPOINT, BuildConfig.USAGE_UPLOAD_TOKEN, false) ? Result.success() : Result.retry();
        } catch (Exception error) { return Result.retry(); }
    }
}
