package com.nextgis.mobile.util;

import android.app.ApplicationExitInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, manifest = Config.NONE, application = android.app.Application.class)
public class DiagnosticsExitInfoTest {
    private ApplicationExitInfo exit(String process, long time, int reason) throws Exception {
        ApplicationExitInfo info = ApplicationExitInfo.class.getDeclaredConstructor().newInstance();
        set(info, "setProcessName", String.class, process);
        set(info, "setTimestamp", long.class, time);
        set(info, "setReason", int.class, reason);
        set(info, "setDescription", String.class, "native abort test detail");
        set(info, "setPss", long.class, 123L);
        return info;
    }
    private void set(Object object, String method, Class<?> type, Object value) throws Exception {
        java.lang.reflect.Method setter = object.getClass().getDeclaredMethod(method, type);
        setter.setAccessible(true); setter.invoke(object, value);
    }
    @Test public void metadataMatchesOnlyTheSameProcessNearTheOriginalEvent() throws Exception {
        ApplicationExitInfo old = exit("app", 1, ApplicationExitInfo.REASON_ANR);
        ApplicationExitInfo other = exit("app:script", 20_000, ApplicationExitInfo.REASON_ANR);
        ApplicationExitInfo own = exit("app", 20_500, ApplicationExitInfo.REASON_CRASH_NATIVE);
        Map<String, Object> result = DiagnosticsExitInfo.match(List.of(old, other, own), "app", 20_000, ApplicationExitInfo.REASON_CRASH_NATIVE);
        assertEquals(500L, result.get("event_distance_ms"));
        assertEquals("native abort test detail", result.get("description"));
        assertEquals(123L, result.get("pss_kib"));
        assertTrue(DiagnosticsExitInfo.match(List.of(old, other), "app", 20_000, ApplicationExitInfo.REASON_ANR).isEmpty());
        assertTrue(DiagnosticsExitInfo.match(List.of(own), "app", 20_000, ApplicationExitInfo.REASON_ANR).isEmpty());
    }
    @Test public void normalExitAndEmptyHistoryDoNotBecomeCrashEvidence() throws Exception {
        assertTrue(DiagnosticsExitInfo.match(List.of(exit("app", 1, ApplicationExitInfo.REASON_EXIT_SELF)), "app", 1, ApplicationExitInfo.REASON_ANR).isEmpty());
        assertTrue(DiagnosticsExitInfo.match(List.of(), "app", 1, ApplicationExitInfo.REASON_ANR).isEmpty());
        assertEquals(-1, DiagnosticsExitInfo.reasonFor(new io.sentry.SentryEvent()));
    }

    @Test public void explicitExportRetainsNativeSignalsAndNormalExitsWithoutInventingOldBuilds() throws Exception {
        java.util.ArrayList<ApplicationExitInfo> history = new java.util.ArrayList<>();
        history.add(exit("app:script", 100, ApplicationExitInfo.REASON_CRASH_NATIVE));
        for (int i = 0; i < 12; i++) history.add(exit("app", i,
                i == 11 ? ApplicationExitInfo.REASON_SIGNALED : ApplicationExitInfo.REASON_EXIT_SELF));
        set(history.get(12), "setStatus", int.class, 9);
        List<Map<String, Object>> result = DiagnosticsExitInfo.exportRecords(history, "app");
        assertEquals(8, result.size()); assertEquals(11L, result.get(0).get("timestamp_ms"));
        assertEquals(ApplicationExitInfo.REASON_SIGNALED, result.get(0).get("reason"));
        assertEquals(9, result.get(0).get("status"));
        assertEquals(ApplicationExitInfo.REASON_EXIT_SELF, result.get(1).get("reason"));
        assertEquals(4L, result.get(7).get("timestamp_ms"));
        assertEquals(java.util.Set.of("timestamp_ms", "pid", "reason", "status", "importance", "pss_kib", "rss_kib"),
                result.get(0).keySet());
        assertTrue(DiagnosticsExitInfo.exportRecords(null, "app").isEmpty());
    }

    @Test @Config(sdk = 28) public void olderAndroidExportsAnExplicitUnsupportedResult() throws Exception {
        org.json.JSONObject summary = new org.json.JSONObject(DiagnosticsExitInfo.exportSummary(
                org.robolectric.RuntimeEnvironment.getApplication()));
        assertEquals("unsupported", summary.getString("status"));
        assertEquals(0, summary.getJSONArray("exits").length());
        assertEquals(28, summary.getInt("android_api"));
        assertTrue(summary.has("reporter_build"));
    }
}
