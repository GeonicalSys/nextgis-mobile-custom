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
import io.sentry.Hint;
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
import io.sentry.protocol.Request;
import io.sentry.protocol.Response;
import io.sentry.hints.Cached;
import io.sentry.hints.Backfillable;
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
    private static final Set<String> DIAGNOSTIC_CONTEXTS = new HashSet<>(
            Arrays.asList("map_runtime", "gps_recovery", "sync_journal", "report_origin", "process_exit", "background_gps_start"));
    private static volatile com.nextgis.maplib.util.MapRuntimeDiagnostics.Trace startup;
    private static volatile Context diagnosticContext;

    public enum Operation { STARTUP, MAP, GEOMETRY_EDIT, ATTRIBUTE_FORM, SYNC, AZIMUTH, STAKEOUT, TRACK, WALK, LOCATION }
    public enum Phase { START, READY, SAVE, RESTORE, STOP, FAILED, FINISHED, CANCELLED }

    private AppDiagnostics() { }

    /** Rate limiting is owned by the acquisition policy; warnings diagnose reception, not crashes. */
    public static void reportGpsRecovery(java.util.Map<String, Object> snapshot) {
        try {
            java.util.Map<String,Object> observed = new java.util.LinkedHashMap<>(snapshot);
            try { observed.put("platform", DiagnosticsGpsPlatform.collect(diagnosticContext,
                    snapshot.get("raw_fix_elapsed_realtime_ms"))); }
            catch (RuntimeException unavailable) { observed.put("platform", java.util.Map.of("status", "unavailable")); }
            Sentry.captureEvent(gpsRecoveryEvent(observed));
        } catch (RuntimeException ignored) { }
    }

    static SentryEvent gpsRecoveryEvent(java.util.Map<String, Object> snapshot) {
        String reason = String.valueOf(snapshot.get("reason"));
        String phase = String.valueOf(snapshot.get("phase"));
        SentryEvent event = new SentryEvent();
        io.sentry.protocol.Message message = new io.sentry.protocol.Message();
        message.setFormatted("GPS recovery " + phase + ": " + reason);
        event.setMessage(message);
        event.setLevel("recovered".equals(phase) || "cancelled".equals(phase) ? SentryLevel.INFO : SentryLevel.WARNING);
        event.setTag("operation", Operation.LOCATION.name());
        event.setTag("failure_kind", "gps_recovery");
        event.setTag("gps_recovery_reason", reason);
        event.setTag("gps_recovery_phase", phase);
        event.setFingerprints(Arrays.asList("gps-recovery", reason));
        event.getContexts().put("gps_recovery", new java.util.LinkedHashMap<>(snapshot));
        return event;
    }

    public static void initialize(Context context) {
        initialize(context, BuildConfig.SENTRY_DSN);
    }

    /** Called by explicit log ZIP export, never by a crash callback or at startup. */
    public static String processExitSummary(Context context) {
        return DiagnosticsExitInfo.exportSummary(context);
    }

    // Package-private seam for the isolated instrumentation Application, never a runtime setting.
    static void initialize(Context context, String dsn) {
        diagnosticContext = context.getApplicationContext();
        com.nextgis.maplib.util.MapRuntimeDiagnostics.initialize(context,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.SOURCE_REVISION);
        PrivateRenderResourceProfiler.initialize(context);
        startup = com.nextgis.maplib.util.MapRuntimeDiagnostics.beginStartup();
        startup.put("brand", BuildConfig.FLAVOR);
        startup.put("environment", BuildConfig.DEBUG ? "debug" : "production");
        startup.phase("diagnostics_init");
        DiagnosticsProcessExitReporter.arm(context);
        if (dsn == null || dsn.trim().isEmpty()) return; // Explicit CI build without diagnostics.
        try {
            SentryAndroid.init(context, options -> {
                configure(context, options);
                options.setDsn(dsn);
            });
            Sentry.configureScope(AppDiagnostics::applyBuildIdentity);
            operation(Operation.STARTUP, Phase.START);
            scheduleRetry(context);
        } catch (RuntimeException error) {
            // Reporting must never prevent opening the user's map or recovery journals.
            Log.w(TAG, "Diagnostic reporting could not initialize", error);
        }
    }

    // Options tags are applied to Java events; native scope observers require actual scope tags.
    static void applyBuildIdentity(io.sentry.IScope scope) {
        scope.setTag("brand", BuildConfig.FLAVOR);
        scope.setTag("source_revision", BuildConfig.SOURCE_REVISION);
        scope.setTag("diagnostics_contract", "3");
    }

    public static void startupPhase(String phase) {
        com.nextgis.maplib.util.MapRuntimeDiagnostics.Trace trace = startup;
        if (trace != null) trace.phase(phase);
    }

    public static void startupFinished() {
        com.nextgis.maplib.util.MapRuntimeDiagnostics.Trace trace = startup;
        startup = null;
        if (trace != null) trace.finish(0, null);
        DiagnosticsProcessExitReporter.reportAsync();
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
        options.setTag("diagnostics_contract", "3");
        options.setTag("source_revision", BuildConfig.SOURCE_REVISION);
        options.setEnableScopeSync(true);
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
        options.setBeforeSend((event, hint) -> {
            if ("process_exit_fallback".equals(event.getTag("failure_kind"))) return prepareCachedForSend(event);
            if (HintUtils.hasType(hint, Cached.class) || HintUtils.hasType(hint, Backfillable.class)) {
                DiagnosticsExitInfo.attach(context, event);
                return prepareCachedForSend(event);
            }
            preserveHttpContext(event, hint);
            return prepareForSend(event, SystemClock.elapsedRealtime(), HTTP_POLICY);
        });
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
            Sentry.configureScope(scope -> applyOperation(scope, operation, phase));
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setCategory("lisa.operation");
            breadcrumb.setMessage(operation.name() + ": " + phase.name());
            breadcrumb.setData("operation", operation.name());
            breadcrumb.setData("phase", phase.name());
            Sentry.addBreadcrumb(breadcrumb);
        } catch (RuntimeException ignored) { }
    }

    // Native scope sync keeps the original operation/phase when a crash is replayed next launch.
    static void applyOperation(io.sentry.IScope scope, Operation operation, Phase phase) {
        scope.setTag("operation", operation.name());
        scope.setTag("operation_phase", phase.name());
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
            if (error instanceof SyncRecoveryJournal.PersistenceFailure) {
                event.getContexts().put("sync_journal", ((SyncRecoveryJournal.PersistenceFailure) error).diagnostics);
            }
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
        attachRecordedRuntime(event);
        return sanitize(event);
    }

    static SentryEvent prepareCachedForSend(SentryEvent event) {
        // Cached/native/OS ANR events belong to a previous process. Never substitute its release
        // or operation with the current reporter's build or LAST_OPERATION.
        java.util.Map<String, Object> origin = new java.util.LinkedHashMap<>();
        origin.put("delivery", "cached");
        origin.put("reporter_version", BuildConfig.VERSION_NAME);
        origin.put("reporter_build", BuildConfig.VERSION_CODE);
        origin.put("reporter_revision", BuildConfig.SOURCE_REVISION);
        event.getContexts().put("report_origin", origin);
        attachRecordedRuntime(event);
        return sanitize(event);
    }

    private static void attachRecordedRuntime(SentryEvent event) {
        if (event.getTimestamp() == null || event.getContexts().containsKey("map_runtime")) return;
        java.util.Map<String, Object> mapRuntime = com.nextgis.maplib.util.MapRuntimeDiagnostics
                .forEvent(event.getTimestamp().getTime());
        if (!mapRuntime.isEmpty()) event.getContexts().put("map_runtime", mapRuntime);
    }

    // The SDK's OkHttp hint retains the original URL/headers even when its default
    // event strips userinfo or headers. Do not read/consume streaming HTTP bodies.
    static void preserveHttpContext(SentryEvent event, Hint hint) {
        okhttp3.Request rawRequest = hint.getAs("okHttp:request", okhttp3.Request.class);
        if (rawRequest != null) {
            Request request = event.getRequest() == null ? new Request() : new Request(event.getRequest());
            request.setUrl(rawRequest.url().newBuilder().query(null).fragment(null).build().toString());
            request.setQueryString(rawRequest.url().encodedQuery());
            request.setFragment(rawRequest.url().encodedFragment());
            request.setMethod(rawRequest.method());
            request.setHeaders(headers(rawRequest.headers()));
            request.setCookies(rawRequest.header("Cookie"));
            event.setRequest(request);
        }
        okhttp3.Response rawResponse = hint.getAs("okHttp:response", okhttp3.Response.class);
        if (rawResponse != null) {
            Response response = event.getContexts().getResponse() == null ? new Response()
                    : new Response(event.getContexts().getResponse());
            response.setHeaders(headers(rawResponse.headers()));
            response.setCookies(rawResponse.header("Set-Cookie"));
            response.setStatusCode(rawResponse.code());
            event.getContexts().setResponse(response);
        }
    }

    private static java.util.Map<String, String> headers(okhttp3.Headers source) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < Math.min(source.size(), 64); i++) {
            String key = source.name(i);
            String value = DiagnosticsPayloadLimiter.text(source.value(i));
            String previous = result.get(key);
            result.put(key, DiagnosticsPayloadLimiter.text(previous == null ? value : previous + ", " + value));
        }
        return result;
    }

    static SentryEvent sanitize(SentryEvent event) {
        // Reserve independent parts of the same 32 KiB / 256-node additional-data budget.
        // Request/stack noise must not erase GPS, startup state or the last operation.
        DiagnosticsPayloadLimiter diagnosticLimit = new DiagnosticsPayloadLimiter(16_384, 128);
        DiagnosticsPayloadLimiter limit = new DiagnosticsPayloadLimiter(8_192, 64);
        DiagnosticsPayloadLimiter stackLimit = new DiagnosticsPayloadLimiter(4_096, 16);
        DiagnosticsPayloadLimiter breadcrumbLimit = new DiagnosticsPayloadLimiter(4_096, 48);
        if (event.getRequest() != null) {
            Request request = new Request(event.getRequest());
            request.setUrl(DiagnosticsPayloadLimiter.text(request.getUrl()));
            request.setQueryString(DiagnosticsPayloadLimiter.text(request.getQueryString()));
            request.setFragment(DiagnosticsPayloadLimiter.text(request.getFragment()));
            request.setCookies(DiagnosticsPayloadLimiter.text(request.getCookies()));
            request.setHeaders(limit.strings(request.getHeaders()));
            request.setEnvs(limit.strings(request.getEnvs()));
            request.setOthers(limit.strings(request.getOthers()));
            request.setData(limit.value(request.getData()));
            request.setUnknown(limit.map(request.getUnknown()));
            event.setRequest(request);
        }
        event.setServerName(DiagnosticsPayloadLimiter.text(event.getServerName()));
        event.setTransaction(DiagnosticsPayloadLimiter.text(event.getTransaction()));
        event.setExtras(limit.map(event.getExtras()));
        if (event.getUser() != null) {
            io.sentry.protocol.User user = new io.sentry.protocol.User(event.getUser());
            user.setEmail(DiagnosticsPayloadLimiter.text(user.getEmail()));
            user.setId(DiagnosticsPayloadLimiter.text(user.getId()));
            user.setUsername(DiagnosticsPayloadLimiter.text(user.getUsername()));
            user.setName(DiagnosticsPayloadLimiter.text(user.getName()));
            user.setData(limit.strings(user.getData()));
            user.setUnknown(limit.map(user.getUnknown()));
            event.setUser(user);
        }
        java.util.List<String> contextKeys = java.util.Collections.list(event.getContexts().keys());
        contextKeys.sort(java.util.Comparator.comparingInt(key -> "report_origin".equals(key) ? 0
                : "process_exit".equals(key) ? 1 : "sync_journal".equals(key) ? 2
                : "background_gps_start".equals(key) ? 3 : "gps_recovery".equals(key) ? 4
                : "map_runtime".equals(key) ? 5 : 6));
        for (String key : contextKeys) {
            Object context = event.getContexts().get(key);
            if (context instanceof Response) {
                Response response = new Response((Response) context);
                response.setCookies(DiagnosticsPayloadLimiter.text(response.getCookies()));
                response.setHeaders(limit.strings(response.getHeaders()));
                response.setData(limit.value(response.getData()));
                response.setUnknown(limit.map(response.getUnknown()));
                event.getContexts().put(key, response);
            } else if (!CONTEXTS.contains(key) && !(context instanceof io.sentry.protocol.OperatingSystem)) {
                Object bounded = (DIAGNOSTIC_CONTEXTS.contains(key) ? diagnosticLimit : limit).value(context);
                if (bounded == null) event.getContexts().remove(key);
                else event.getContexts().put(key, bounded);
            }
        }
        if (event.getMessage() != null) {
            event.getMessage().setMessage(DiagnosticsPolicy.limitMessage(event.getMessage().getMessage()));
            event.getMessage().setFormatted(DiagnosticsPolicy.limitMessage(event.getMessage().getFormatted()));
            event.getMessage().setParams(limit.strings(event.getMessage().getParams()));
        }
        if (event.getExceptions() != null) for (SentryException exception : event.getExceptions()) {
            exception.setValue(DiagnosticsPolicy.limitMessage(exception.getValue()));
            sanitize(exception.getStacktrace(), stackLimit);
        }
        if (event.getThreads() != null) event.getThreads().forEach(thread -> sanitize(thread.getStacktrace(), stackLimit));
        if (event.getBreadcrumbs() != null) {
            java.util.List<Breadcrumb> breadcrumbs = event.getBreadcrumbs();
            // Keep wire order while spending the reserved data budget from the newest records.
            for (int priority = 0; priority < 2; priority++) {
                for (int i = breadcrumbs.size() - 1; i >= 0; i--) {
                    Breadcrumb breadcrumb = breadcrumbs.get(i);
                    String category = breadcrumb.getCategory();
                    boolean operation = "lisa.operation".equals(category) || "ui.lifecycle".equals(category)
                            || "ui.fragment.lifecycle".equals(category) || "device.event".equals(category);
                    if (operation == (priority == 0)) sanitize(breadcrumb, breadcrumbLimit);
                }
            }
        }
        return event;
    }

    private static void sanitize(SentryStackTrace stack, DiagnosticsPayloadLimiter limit) {
        if (stack == null || stack.getFrames() == null) return;
        for (SentryStackFrame frame : stack.getFrames()) {
            frame.setVars(limit.map(frame.getVars()));
            frame.setAbsPath(DiagnosticsPayloadLimiter.text(frame.getAbsPath()));
            frame.setPreContext(limit.strings(frame.getPreContext()));
            frame.setPostContext(limit.strings(frame.getPostContext()));
            frame.setContextLine(DiagnosticsPayloadLimiter.text(frame.getContextLine()));
        }
    }

    static Breadcrumb sanitize(Breadcrumb breadcrumb) {
        return sanitize(breadcrumb, new DiagnosticsPayloadLimiter());
    }

    private static Breadcrumb sanitize(Breadcrumb breadcrumb, DiagnosticsPayloadLimiter limit) {
        breadcrumb.setMessage(DiagnosticsPolicy.limitMessage(breadcrumb.getMessage()));
        java.util.Map<String, Object> data = limit.map(breadcrumb.getData());
        breadcrumb.getData().clear();
        if (data != null) breadcrumb.getData().putAll(data);
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
