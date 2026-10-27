package com.nextgis.mobile.util;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import io.sentry.Hint;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.hints.DiskFlushNotification;
import io.sentry.protocol.SentryId;
import com.nextgis.mobile.BuildConfig;

/** Restart fallback when no Java/NDK envelope survived. SDK transport still owns durable delivery. */
final class DiagnosticsProcessExitReporter {
    private static final Object LOCK = new Object();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static Context application;
    private static long armedAt;

    static void arm(Context context) {
        if (Build.VERSION.SDK_INT < 30) return;
        synchronized (LOCK) {
            application = context.getApplicationContext(); armedAt = System.currentTimeMillis();
            try {
                JSONObject state = read(context);
                JSONArray pending = state.optJSONArray("pending"); if (pending == null) pending = new JSONArray();
                JSONObject old = state.optJSONObject("current");
                if (old != null && old.optInt("pid") != Process.myPid()) pending.put(old);
                JSONArray bounded = new JSONArray();
                for (int i = Math.max(0, pending.length() - 4); i < pending.length(); i++) bounded.put(pending.get(i));
                JSONObject current = new JSONObject().put("pid", Process.myPid()).put("started_at_ms", armedAt)
                        .put("version", BuildConfig.VERSION_NAME).put("build", BuildConfig.VERSION_CODE)
                        .put("revision", BuildConfig.SOURCE_REVISION).put("brand", BuildConfig.FLAVOR);
                state.put("current", current).put("pending", bounded);
                write(context, state);
            } catch (Exception error) { android.util.Log.w("LISA", "Process exit marker unavailable"); }
        }
    }

    static void reportAsync() {
        if (application == null || !Sentry.isEnabled() || !STARTED.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try { reportPending(application); }
            catch (Exception error) { android.util.Log.w("LISA", "Process exit reporting deferred"); }
        }, "diagnostic-previous-exit");
        worker.setDaemon(true); worker.start();
    }

    @androidx.annotation.RequiresApi(30)
    static ApplicationExitInfo match(List<ApplicationExitInfo> history, JSONObject owner, String process, long before) {
        if (history == null) return null;
        for (ApplicationExitInfo info : history)
            if (info.getPid() == owner.optInt("pid") && process.equals(info.getProcessName())
                    && info.getTimestamp() >= owner.optLong("started_at_ms") && info.getTimestamp() <= before) return info;
        return null;
    }

    @androidx.annotation.RequiresApi(30)
    static boolean abnormal(int reason) {
        return reason != ApplicationExitInfo.REASON_EXIT_SELF && reason != ApplicationExitInfo.REASON_USER_REQUESTED
                && reason != ApplicationExitInfo.REASON_USER_STOPPED && reason != ApplicationExitInfo.REASON_PERMISSION_CHANGE
                && reason != ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE && reason != ApplicationExitInfo.REASON_PACKAGE_UPDATED;
    }

    @androidx.annotation.RequiresApi(30)
    private static void reportPending(Context context) throws Exception {
        ActivityManager manager = context.getSystemService(ActivityManager.class); if (manager == null) return;
        JSONArray pending;
        synchronized (LOCK) {
            JSONObject state = read(context);
            if (!state.optBoolean("history_scanned")) {
                JSONArray owners = state.optJSONArray("pending"); if (owners == null) owners = new JSONArray();
                // First upgraded launch can recover a recent termination predating this marker.
                // OS history does not supply its APK version: explicitly retain unknown identity.
                List<ApplicationExitInfo> history = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 8);
                if (history != null) for (ApplicationExitInfo info : history) {
                    if (owners.length() >= 4) break;
                    if (!context.getPackageName().equals(info.getProcessName()) || !abnormal(info.getReason())
                            || info.getTimestamp() > armedAt || info.getTimestamp() < armedAt - 86_400_000L) continue;
                    boolean known = false;
                    for (int i = 0; i < owners.length(); i++) if (owners.getJSONObject(i).optInt("pid") == info.getPid()) known = true;
                    if (!known) owners.put(new JSONObject().put("pid", info.getPid()).put("started_at_ms", info.getTimestamp())
                            .put("version", "unknown").put("build", -1).put("revision", "unknown").put("brand", BuildConfig.FLAVOR));
                }
                state.put("pending", owners).put("history_scanned", true); write(context, state);
            }
            pending = state.optJSONArray("pending");
        }
        if (pending == null) return;
        for (int i = 0; i < pending.length(); i++) {
            JSONObject owner = pending.getJSONObject(i);
            ApplicationExitInfo info = match(manager.getHistoricalProcessExitReasons(
                    context.getPackageName(), owner.getInt("pid"), 4), owner, context.getPackageName(), armedAt);
            if (info == null) continue;
            if (!abnormal(info.getReason())) { acknowledge(context, owner); continue; }
            Map<String, Object> trace = new java.util.LinkedHashMap<>(); trace.put("status", "not_available_for_reason");
            if (info.getReason() == ApplicationExitInfo.REASON_ANR
                    || (info.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE && Build.VERSION.SDK_INT >= 31)) {
                try { trace = DiagnosticsNativeTrace.read(info.getTraceInputStream(), info.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE); }
                catch (Exception error) { trace.put("status", "unavailable"); }
            }
            SentryEvent event = event(context.getPackageName(), owner, info, trace);
            Persisted persisted = new Persisted(); Hint hint = io.sentry.util.HintUtils.createWithTypeCheckHint(persisted);
            Sentry.captureEvent(event, hint);
            // Acknowledgement means the existing transport actually persisted the envelope,
            // not merely that capture returned an ID or a network attempt was scheduled.
            if (persisted.flushed) acknowledge(context, owner);
        }
    }

    @androidx.annotation.RequiresApi(30)
    static SentryEvent event(String process, JSONObject owner, ApplicationExitInfo info, Map<String, Object> trace) {
        SentryEvent event = new SentryEvent();
        event.setEventId(new SentryId(UUID.nameUUIDFromBytes((process + ":" + info.getPid() + ":" + info.getTimestamp()).getBytes(StandardCharsets.UTF_8))));
        event.setTimestamp(new Date(info.getTimestamp()));
        event.setRelease(process + "@" + owner.optString("version") + "+" + owner.optInt("build"));
        event.setTag("source_revision", owner.optString("revision")); event.setTag("brand", owner.optString("brand"));
        event.setTag("failure_kind", "process_exit_fallback"); event.setLevel(SentryLevel.ERROR);
        io.sentry.protocol.App app = new io.sentry.protocol.App(); app.setAppIdentifier(process);
        app.setAppVersion(owner.optString("version")); app.setAppBuild(String.valueOf(owner.optInt("build")));
        event.getContexts().setApp(app);
        event.setFingerprints(java.util.Arrays.asList("os-process-exit", String.valueOf(info.getReason())));
        io.sentry.protocol.Message message = new io.sentry.protocol.Message();
        message.setFormatted("Android process terminated: reason=" + info.getReason() + " status=" + info.getStatus());
        event.setMessage(message);
        Map<String, Object> exit = DiagnosticsExitInfo.exportRecords(java.util.Collections.singletonList(info), process).get(0);
        exit.put("trace", trace); event.getContexts().put("process_exit", exit);
        return event;
    }

    private static void acknowledge(Context context, JSONObject owner) throws Exception {
        synchronized (LOCK) {
            JSONObject state = read(context); JSONArray pending = state.optJSONArray("pending"), retained = new JSONArray();
            if (pending != null) for (int i = 0; i < pending.length(); i++) {
                JSONObject candidate = pending.getJSONObject(i);
                if (candidate.optInt("pid") != owner.optInt("pid") || candidate.optLong("started_at_ms") != owner.optLong("started_at_ms")) retained.put(candidate);
            }
            state.put("pending", retained); write(context, state);
        }
    }

    private static AtomicFile file(Context context) { return new AtomicFile(new File(context.getFilesDir(), "diagnostic-process.json")); }
    private static JSONObject read(Context context) throws Exception {
        AtomicFile file = file(context);
        if (!file.getBaseFile().isFile() || file.getBaseFile().length() > 8192) return new JSONObject();
        try { return new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8)); }
        catch (org.json.JSONException malformed) { return new JSONObject(); }
    }
    private static void write(Context context, JSONObject state) throws Exception {
        AtomicFile file = file(context); java.io.FileOutputStream out = file.startWrite();
        try { out.write(state.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch (Exception error) { file.failWrite(out); throw error; }
    }
    private static final class Persisted implements DiskFlushNotification {
        volatile boolean flushed;
        public void markFlushed() { flushed = true; }
        public boolean isFlushable(SentryId id) { return true; }
        public void setFlushable(SentryId id) { }
    }
}
