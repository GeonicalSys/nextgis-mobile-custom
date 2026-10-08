package com.nextgis.mobile.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import io.sentry.Sentry;
import io.sentry.SentryOptions;

/** Android may start the main application for this job even with no Activity open. */
public final class DiagnosticsRetryWorker extends Worker {
    public DiagnosticsRetryWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        if (!Sentry.isEnabled()) return Result.success();
        SentryOptions options = Sentry.getCurrentScopes().getOptions();
        if (!AppDiagnostics.hasPendingReports(options)) return Result.success();
        try {
            AppDiagnostics.retryPendingReports(options);
            return AppDiagnostics.hasPendingReports(options) ? Result.retry() : Result.success();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.retry();
        } catch (Exception error) {
            // Do not report a failure to send reports through that same reporting path.
            Log.w("AppDiagnostics", "Diagnostic delivery deferred", error);
            return Result.retry();
        }
    }
}
