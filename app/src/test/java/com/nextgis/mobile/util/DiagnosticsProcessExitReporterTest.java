package com.nextgis.mobile.util;

import android.app.ApplicationExitInfo;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, manifest = Config.NONE, application = android.app.Application.class)
public class DiagnosticsProcessExitReporterTest {
    private ApplicationExitInfo exit(String name, int pid, long time) throws Exception {
        ApplicationExitInfo result = ApplicationExitInfo.class.getDeclaredConstructor().newInstance();
        set(result, "setProcessName", String.class, name); set(result, "setPid", int.class, pid);
        set(result, "setTimestamp", long.class, time); set(result, "setReason", int.class, ApplicationExitInfo.REASON_CRASH_NATIVE);
        return result;
    }
    private void set(Object target, String method, Class<?> type, Object value) throws Exception {
        java.lang.reflect.Method setter = target.getClass().getDeclaredMethod(method, type); setter.setAccessible(true); setter.invoke(target, value);
    }
    private JSONObject owner() throws Exception {
        return new JSONObject().put("pid", 42).put("started_at_ms", 1000).put("version", "old")
                .put("build", 223).put("revision", "old-revision").put("brand", "lisa");
    }
    @Test public void requiresTheOwningProcessPidAndLifetimeInsteadOfGuessingFromTheLatestExit() throws Exception {
        JSONObject owner = owner(); ApplicationExitInfo correct = exit("app", 42, 2000);
        assertSame(correct, DiagnosticsProcessExitReporter.match(List.of(exit("app:script", 42, 2000),
                exit("app", 43, 2000), exit("app", 42, 999), exit("app", 42, 3001), correct), owner, "app", 3000));
        assertNull(DiagnosticsProcessExitReporter.match(List.of(exit("app", 42, 999)), owner, "app", 3000));
    }
    @Test public void stableFallbackEventKeepsOriginalIdentityAndTraceThroughTheRealSendPolicy() throws Exception {
        JSONObject owner = owner(); ApplicationExitInfo info = exit("app", 42, 2000);
        io.sentry.SentryEvent event = DiagnosticsProcessExitReporter.event("app", owner, info, Map.of("status", "captured", "native_frames", List.of(Map.of("function", "Renderer"))));
        assertEquals(event.getEventId(), DiagnosticsProcessExitReporter.event("app", owner, info, Map.of()).getEventId());
        event = AppDiagnostics.prepareCachedForSend(event);
        assertEquals("app@old+223", event.getRelease()); assertEquals("old-revision", event.getTag("source_revision"));
        assertNull(event.getTag("operation"));
        assertTrue(event.getContexts().get("process_exit").toString().contains("Renderer"));
        assertNotNull(event.getContexts().get("report_origin"));
    }
    @Test public void systemUserAndUpdateStopsAreNotReportedAsCrashes() {
        for (int reason : new int[]{ApplicationExitInfo.REASON_EXIT_SELF, ApplicationExitInfo.REASON_USER_REQUESTED,
                ApplicationExitInfo.REASON_USER_STOPPED, ApplicationExitInfo.REASON_PACKAGE_UPDATED,
                ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE, ApplicationExitInfo.REASON_PERMISSION_CHANGE})
            assertFalse(DiagnosticsProcessExitReporter.abnormal(reason));
        for (int reason : new int[]{ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR,
                ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_SIGNALED}) assertTrue(DiagnosticsProcessExitReporter.abnormal(reason));
    }

    @Test public void signalFallbackKeepsSignalReasonAndPriorTraceAssociationThroughSendPolicy() throws Exception {
        ApplicationExitInfo info = exit("app", 42, 2000);
        set(info, "setReason", int.class, ApplicationExitInfo.REASON_SIGNALED);
        set(info, "setStatus", int.class, 6);
        Map<String,Object> trace = DiagnosticsTracePolicy.read(30, info.getReason(), () ->
                new java.io.ByteArrayInputStream("\"main\"\n at android.view.View.draw(View.java:42)".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        io.sentry.SentryEvent event = AppDiagnostics.prepareCachedForSend(DiagnosticsProcessExitReporter.event("app", owner(), info, trace));
        Map<?,?> context = (Map<?,?>) event.getContexts().get("process_exit");
        assertEquals(2, context.get("reason"));
        assertEquals(6, context.get("status"));
        assertEquals("may_precede_exit", ((Map<?,?>) context.get("trace")).get("association"));
        assertEquals("anr_text", ((Map<?,?>) context.get("trace")).get("format"));
        assertEquals("app@old+223", event.getRelease());
        assertEquals(List.of("os-process-exit", "2"), event.getFingerprints());
        assertFalse(event.getMessage().getFormatted().contains("OOM"));
    }
}
