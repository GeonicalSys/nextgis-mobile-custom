package com.nextgis.mobile.util;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import io.sentry.Sentry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional scalar evidence for an already handled FGS refusal, never an eligibility decision. */
public final class DiagnosticsBackgroundLocationStart {
    static final String CONTEXT = "background_gps_start";
    private static final String UNKNOWN = "unknown";
    // Published together by the existing main-thread lifecycle owner; no Activity is retained.
    private static volatile Counts lifecycle = new Counts(-1, -1);
    private DiagnosticsBackgroundLocationStart() { }

    private static final class Counts {
        final int visible, resumed;
        Counts(int visible, int resumed) { this.visible = visible; this.resumed = resumed; }
    }

    /** Observation only: these counts must not control GPS, promotion, cancellation or retry. */
    public static void lifecycle(int visible, int resumed) { lifecycle = new Counts(visible, resumed); }

    public static final class Request {
        private final long elapsed;
        private final Map<String, Object> state;
        private Request(long elapsed, Map<String, Object> state) {
            this.elapsed = elapsed; this.state = Collections.unmodifiableMap(state);
        }
    }

    public static Request request(Context context) { return request(context, SystemClock.elapsedRealtime()); }
    static Request request(Context context, long now) { return new Request(now, state(context)); }

    static Map<String, Object> failure(Context context, Request request, String stage, long now) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_version", 1);
        result.put("stage", stage(stage));
        result.put("request_age_ms", request == null || request.elapsed < 0 || now < request.elapsed
                ? UNKNOWN : now - request.elapsed);
        result.put("request", request == null ? unknownState() : request.state);
        result.put("failure", state(context));
        return result;
    }

    /** Keep the original exception, handled classification and existing report rate limit. */
    public static void report(Context context, Request request, String stage, RuntimeException error) {
        boolean[] invoked = {false};
        try {
            Map<String, Object> witness = failure(context, request, stage, SystemClock.elapsedRealtime());
            Sentry.withScope(scope -> {
                scope.setContexts(CONTEXT, witness);
                invoked[0] = true;
                AppDiagnostics.report(AppDiagnostics.Operation.LOCATION, error);
            });
        } catch (RuntimeException unavailable) {
            // A failed optional observation must not lose the existing failure report.
            if (!invoked[0]) AppDiagnostics.report(AppDiagnostics.Operation.LOCATION, error);
        }
    }

    private static String stage(String stage) {
        if ("launch".equals(stage) || "notification".equals(stage) || "start_foreground".equals(stage)
                || "acquire_background_updates".equals(stage) || "schedule_permission_check".equals(stage)) return stage;
        return UNKNOWN;
    }

    private static Map<String, Object> unknownState() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : new String[]{"visible_activities", "resumed_activities", "fine_permission",
                "coarse_permission", "background_permission", "process_importance"}) result.put(key, UNKNOWN);
        return result;
    }

    private static Map<String, Object> state(Context context) {
        Map<String, Object> result = unknownState();
        Counts observed = lifecycle;
        if (observed.visible >= 0) result.put("visible_activities", observed.visible);
        if (observed.resumed >= 0) result.put("resumed_activities", observed.resumed);
        if (context == null) return result;
        result.put("fine_permission", permission(context, Manifest.permission.ACCESS_FINE_LOCATION));
        result.put("coarse_permission", permission(context, Manifest.permission.ACCESS_COARSE_LOCATION));
        result.put("background_permission", Build.VERSION.SDK_INT < 29 ? "not_required"
                : permission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION));
        try {
            ActivityManager.RunningAppProcessInfo process = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(process);
            if (process.importance > 0) result.put("process_importance", process.importance);
        } catch (RuntimeException unavailable) { /* Explicit unknown is retained. */ }
        return result;
    }

    private static String permission(Context context, String permission) {
        try {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
        } catch (RuntimeException unavailable) { return UNKNOWN; }
    }
}
