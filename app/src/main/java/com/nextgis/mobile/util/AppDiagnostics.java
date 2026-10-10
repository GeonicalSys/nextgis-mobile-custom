package com.nextgis.mobile.util;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.nextgis.mobile.BuildConfig;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.sentry.Breadcrumb;
import io.sentry.IConnectionStatusProvider;
import io.sentry.Integration;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.hints.Cached;
import io.sentry.util.HintUtils;

/** Error diagnostics only: SDK event/cache/handler contracts with a durable HTTP extension. */
public final class AppDiagnostics {
    static final String RETRY_WORK = "diagnostic-reports-retry";
    private static final String TAG = "AppDiagnostics";
    private static final DiagnosticsPolicy POLICY = new DiagnosticsPolicy();
    private static final DiagnosticsHttpPolicy HTTP_POLICY = new DiagnosticsHttpPolicy();
    private static final AtomicReference<Operation> LAST_OPERATION =
            new AtomicReference<>(Operation.STARTUP);
    private static final Set<String> CONTEXTS = new HashSet<>(
            Arrays.asList("app", "device", "os", "runtime", "gpu", "trace"));
    private static final Set<String> BREADCRUMB_CATEGORIES = new HashSet<>(
            Arrays.asList("lisa.operation", "ui.lifecycle", "app.lifecycle", "device.event", "network.event"));
    private static final Set<String> BREADCRUMB_KEYS = new HashSet<>(Arrays.asList(
            "operation", "phase", "state", "screen", "action", "level", "charging",
            "battery_level", "low_memory", "network_type", "signal_strength", "is_connected",
            "available", "metered", "validated", "download_bandwidth", "upload_bandwidth"));

    public enum Operation { STARTUP, MAP, GEOMETRY_EDIT, ATTRIBUTE_FORM, SYNC, AZIMUTH, STAKEOUT, TRACK, WALK }
    public enum Phase { START, READY, SAVE, RESTORE, STOP, FAILED, FINISHED, CANCELLED }

    private AppDiagnostics() { }

    public static void initialize(Context context) {
        initialize(context, BuildConfig.SENTRY_DSN);
    }

    // Package-private seam for the isolated instrumentation Application, never a runtime setting.
    static void initialize(Context context, String dsn) {
        if (dsn == null || dsn.trim().isEmpty()) return; // Explicit CI build without diagnostics.
        try {
            SentryAndroid.init(context, options -> {
                configure(context, options);
                options.setDsn(dsn);
            });
            operation(Operation.STARTUP, Phase.START);
            scheduleRetry(context);
        } catch (RuntimeException error) {
            // Reporting must never prevent opening the user's map or recovery journals.
            Log.w(TAG, "Diagnostic reporting could not initialize", error);
        }
    }

    static void configure(Context context, SentryAndroidOptions options) {
        options.setCacheDirPath(new File(context.getFilesDir(), "diagnostic-reports").getAbsolutePath());
        options.setMaxCacheItems(DiagnosticsPolicy.CACHE_ITEMS);
        options.setMaxQueueSize(128);
        options.setMaxBreadcrumbs(DiagnosticsPolicy.BREADCRUMBS);
        options.setFlushTimeoutMillis(5_000L);
        options.setShutdownTimeoutMillis(2_000L);
        options.setConnectionTimeoutMillis(5_000);
        options.setReadTimeoutMillis(5_000);
        options.setRelease(context.getPackageName() + "@" + BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE);
        options.setDist(BuildConfig.FLAVOR);
        options.setEnvironment(BuildConfig.DEBUG ? "debug" : "production");
        options.setTag("brand", BuildConfig.FLAVOR);
        options.setTag("diagnostics_contract", "1");
        options.setTag("source_revision", BuildConfig.SOURCE_REVISION);
        options.setSendDefaultPii(false);
        options.setAttachServerName(false);
        options.setAttachThreads(true);
        options.setAttachScreenshot(false);
        options.setAttachViewHierarchy(false);
        options.setEnableUserInteractionBreadcrumbs(false);
        options.setEnableAutoSessionTracking(false);
        options.setTracesSampleRate(0.0);
        options.setProfilesSampleRate(0.0);
        options.setProfileSessionSampleRate(0.0);
        options.setSendClientReports(false);
        options.setTransportFactory(DiagnosticsTransport::new);
        options.setBeforeSend((event, hint) -> HintUtils.hasType(hint, Cached.class)
                ? sanitize(event) : prepareForSend(event, SystemClock.elapsedRealtime(), HTTP_POLICY));
        options.setBeforeBreadcrumb((breadcrumb, hint) -> sanitize(breadcrumb));
    }

    private static void scheduleRetry(Context context) {
        try {
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    DiagnosticsRetryWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    RETRY_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
        } catch (RuntimeException error) {
            // The SDK still retries on startup/network changes if the system scheduler is unavailable.
            Log.w(TAG, "Diagnostic retry scheduling deferred", error);
        }
    }

    public static void operation(Operation operation, Phase phase) {
        LAST_OPERATION.set(operation);
        try {
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setCategory("lisa.operation");
            breadcrumb.setMessage(operation.name() + ": " + phase.name());
            breadcrumb.setData("operation", operation.name());
            breadcrumb.setData("phase", phase.name());
            Sentry.addBreadcrumb(breadcrumb);
        } catch (RuntimeException ignored) { }
    }

    /** Selected recoverable failures; ordinary offline/cancellation outcomes are not bugs. */
    public static void report(Operation operation, Throwable error) {
        operation(operation, Phase.FAILED);
        if (!POLICY.shouldReport(operation.name(), error, SystemClock.elapsedRealtime())) return;
        try {
            SentryEvent event = new SentryEvent(error);
            event.setLevel(SentryLevel.ERROR);
            event.setTag("operation", operation.name());
            event.setTag("failure_kind", "handled");
            Sentry.captureEvent(event);
        } catch (RuntimeException ignored) { }
    }

    /** Structured summaries only: never forward raw HyperLog, NMEA, field values or photo paths. */
    static void observeLog(String message) {
        if (message == null) return;
        Operation operation;
        if (message.startsWith("FormDraft") || message.startsWith("FeatureSave")) operation = Operation.ATTRIBUTE_FORM;
        else if (message.startsWith("GeometryDraft")) operation = Operation.GEOMETRY_EDIT;
        else if (message.startsWith("CrashRecovery")) operation = Operation.STARTUP;
        else return;
        Phase phase = message.contains("fail") || message.contains("error") ? Phase.FAILED
                : message.contains("clear") ? Phase.FINISHED
                : message.contains("restor") || message.contains("recover") ? Phase.RESTORE : Phase.SAVE;
        operation(operation, phase);
    }

    static SentryEvent prepareForSend(SentryEvent event, long now, DiagnosticsHttpPolicy httpPolicy) {
        if (event.getTag("operation") == null) event.setTag("operation", LAST_OPERATION.get().name());
        if (!httpPolicy.shouldReport(event, now)) return null;
        return sanitize(event);
    }

    static SentryEvent sanitize(SentryEvent event) {
        event.setUser(null);
        event.setRequest(null);
        event.setServerName(null);
        event.setTransaction(null);
        event.setExtras(null);
        for (String key : java.util.Collections.list(event.getContexts().keys())) {
            if (!CONTEXTS.contains(key)) event.getContexts().remove(key);
        }
        if (event.getTag("operation") == null) event.setTag("operation", LAST_OPERATION.get().name());
        if (event.getMessage() != null) {
            event.getMessage().setMessage(DiagnosticsPolicy.redact(event.getMessage().getMessage()));
            event.getMessage().setFormatted(DiagnosticsPolicy.redact(event.getMessage().getFormatted()));
            event.getMessage().setParams(null);
        }
        if (event.getExceptions() != null) for (SentryException exception : event.getExceptions()) {
            exception.setValue(DiagnosticsPolicy.redact(exception.getValue()));
            sanitize(exception.getStacktrace());
        }
        if (event.getThreads() != null) event.getThreads().forEach(thread -> sanitize(thread.getStacktrace()));
        if (event.getBreadcrumbs() != null) {
            event.getBreadcrumbs().removeIf(breadcrumb -> sanitize(breadcrumb) == null);
        }
        return event;
    }

    private static void sanitize(SentryStackTrace stack) {
        if (stack == null || stack.getFrames() == null) return;
        for (SentryStackFrame frame : stack.getFrames()) {
            frame.setVars(null);
            frame.setAbsPath(null);
            frame.setPreContext(null);
            frame.setPostContext(null);
        }
    }

    static Breadcrumb sanitize(Breadcrumb breadcrumb) {
        if (!BREADCRUMB_CATEGORIES.contains(breadcrumb.getCategory())) return null;
        breadcrumb.setMessage(DiagnosticsPolicy.redact(breadcrumb.getMessage()));
        breadcrumb.getData().keySet().retainAll(BREADCRUMB_KEYS);
        breadcrumb.getData().replaceAll((key, value) -> value instanceof Number || value instanceof Boolean
                ? value : DiagnosticsPolicy.redact(String.valueOf(value)));
        return breadcrumb;
    }

    static boolean hasPendingReports(SentryOptions options) {
        return hasFiles(options.getCacheDirPath(), true) || hasFiles(options.getOutboxPath(), false);
    }

    private static boolean hasFiles(String path, boolean envelopesOnly) {
        if (path == null) return false;
        File[] files = new File(path).listFiles(file -> file.isFile()
                && (!envelopesOnly || file.getName().endsWith(".envelope")));
        return files != null && files.length > 0;
    }

    /** Reuse the SDK's own serial, rate-limit-aware disk senders without reinitializing its handlers. */
    static void retryPendingReports(SentryOptions options) throws Exception {
        IConnectionStatusProvider.ConnectionStatus status = options.getConnectionStatusProvider().getConnectionStatus();
        if (status == IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) return;
        for (Integration integration : options.getIntegrations()) {
            // Android has its own package-private SendCachedEnvelopeIntegration. Both variants
            // expose the public observer contract; do not depend on a core-only implementation.
            if (integration instanceof IConnectionStatusProvider.IConnectionStatusObserver) {
                ((IConnectionStatusProvider.IConnectionStatusObserver) integration).onConnectionStatusChanged(status);
            }
        }
        // A barrier on the SDK executor waits for the queued cache replay, not just live HTTP tasks.
        options.getExecutorService().submit(() -> { }).get(40, TimeUnit.SECONDS);
        Sentry.flush(5_000L);
    }
}
