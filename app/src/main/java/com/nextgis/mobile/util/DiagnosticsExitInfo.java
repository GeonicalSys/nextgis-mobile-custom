package com.nextgis.mobile.util;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Looper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.sentry.SentryEvent;

/** OS termination metadata for historical reports only; never queries business storage or traces. */
final class DiagnosticsExitInfo {
    static final long MATCH_WINDOW_MS = 10_000;

    static void attach(Context context, SentryEvent event) {
        if (Build.VERSION.SDK_INT < 30 || event.getTimestamp() == null
                || Looper.myLooper() == Looper.getMainLooper()
                || event.getContexts().containsKey("process_exit")) return;
        int reason = reasonFor(event);
        if (reason < 0) return;
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return;
            Map<String, Object> match = match(manager.getHistoricalProcessExitReasons(
                    context.getPackageName(), 0, 8), context.getPackageName(), event.getTimestamp().getTime(), reason);
            if (!match.isEmpty()) event.getContexts().put("process_exit", match);
        } catch (RuntimeException ignored) { /* Optional OS history may be unavailable. */ }
    }

    static int reasonFor(SentryEvent event) {
        if (event.getExceptions() == null) return -1;
        for (io.sentry.protocol.SentryException exception : event.getExceptions()) {
            if ("ApplicationNotResponding".equals(exception.getType())) return ApplicationExitInfo.REASON_ANR;
            if (exception.getMechanism() != null && "signalhandler".equals(exception.getMechanism().getType()))
                return ApplicationExitInfo.REASON_CRASH_NATIVE;
        }
        for (io.sentry.protocol.SentryException exception : event.getExceptions())
            if (exception.getMechanism() != null && Boolean.FALSE.equals(exception.getMechanism().isHandled()))
                return ApplicationExitInfo.REASON_CRASH;
        return -1;
    }

    @androidx.annotation.RequiresApi(30)
    static Map<String, Object> match(List<ApplicationExitInfo> history, String process, long eventTime, int expectedReason) {
        ApplicationExitInfo best = null;
        long distance = MATCH_WINDOW_MS + 1;
        for (ApplicationExitInfo info : history) {
            int reason = info.getReason();
            if (!process.equals(info.getProcessName()) || reason != expectedReason) continue;
            long delta = Math.abs(info.getTimestamp() - eventTime);
            if (delta <= MATCH_WINDOW_MS && delta < distance) { best = info; distance = delta; }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (best == null) return result;
        result.put("timestamp_ms", best.getTimestamp());
        result.put("event_distance_ms", distance);
        result.put("pid", best.getPid());
        result.put("reason", best.getReason());
        result.put("status", best.getStatus());
        result.put("importance", best.getImportance());
        result.put("description", DiagnosticsPayloadLimiter.text(best.getDescription()));
        result.put("pss_kib", best.getPss());
        result.put("rss_kib", best.getRss());
        return result;
    }
}
