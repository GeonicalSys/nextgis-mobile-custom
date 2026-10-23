package com.nextgis.mobile.reliability;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.location.GpsRecoveryPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Native source/registration integration with synthetic diagnostic timestamps; emulator only. */
@RunWith(AndroidJUnit4.class)
public class GpsRecoveryNativeTest {
    @Test public void noSatelliteSolutionKeepsRegistrationAndStaysLocalThroughRecovery() throws Exception {
        withSource(source -> {
            long now = SystemClock.elapsedRealtime();
            List<Map<String, Object>> reports = new ArrayList<>(); source.setRecoveryReporter(reports::add);
            seed(source, now, 0);
            GpsRecoveryPolicy policy = (GpsRecoveryPolicy) field(source, "recoveryPolicy");
            policy.update(now - 30_000, true, true, true, GpsRecoveryPolicy.Reason.NO_FIX);
            policy.update(now - 15_140, true, true, false, GpsRecoveryPolicy.Reason.NO_FIX);
            long requests = (Long) field(source, "gpsRequests");
            check(source);
            assertTrue(reports.isEmpty());
            assertEquals(requests, field(source, "gpsRequests"));
            set(policy, "waitingSince", now - 60_000);
            check(source);
            assertTrue(reports.isEmpty());
            assertEquals(requests, field(source, "gpsRequests"));
            Location recovered = new Location("gps"); recovered.setAccuracy(25f);
            recovered.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            set(source, "gps", recovered); set(source, "rawGps", recovered);
            check(source);
            assertTrue(reports.isEmpty());
        });
    }

    @Test public void sustainedUsedSatellitesWithoutCallbacksReregistersOnceAndPreservesRecording() throws Exception {
        withSource(source -> {
            GpsEventSource.RecordingListener recorder = location -> fail("Diagnostics must not invent track points");
            source.addRecordingListener(recorder);
            try {
                long now = SystemClock.elapsedRealtime(); seed(source, now, 8);
                GpsRecoveryPolicy policy = (GpsRecoveryPolicy) field(source, "recoveryPolicy");
                policy.update(now - 40_000, true, true, true, GpsRecoveryPolicy.Reason.CALLBACK_STALLED);
                policy.update(now - 31_000, true, true, false, GpsRecoveryPolicy.Reason.CALLBACK_STALLED);
                long requests = (Long) field(source, "gpsRequests");
                long stops = (Long) field(source, "gpsStops");
                List<Map<String, Object>> reports = new ArrayList<>(); source.setRecoveryReporter(reports::add);
                check(source); check(source);
                assertEquals(requests + 1, field(source, "gpsRequests"));
                assertEquals(stops, field(source, "gpsStops"));
                assertTrue(source.getRecordingDiagnostics().contains("wakeLock=true"));
                assertEquals(1, reports.size());
                assertEquals("registered", reports.get(0).get("subscription_retry"));
                assertEquals("callback_stalled", reports.get(0).get("reason"));
                assertEquals(3, reports.get(0).get("schema_version"));
                assertTrue((Long) reports.get(0).get("reason_duration_ms") >= 30_000);
                assertTrue(reports.get(0).containsKey("diagnostic_tick_gap_ms"));
            } finally { source.removeRecordingListener(recorder); }
        });
    }

    private interface Check { void run(GpsEventSource source) throws Exception; }
    private void withSource(Check action) throws Exception {
        assertTrue(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        assertTrue(app.getPackageName().endsWith(".debug"));
        // The source uses real monotonic time; allow a freshly booted emulator to reach fixture age.
        while (SystemClock.elapsedRealtime() < 65_000) Thread.sleep(500);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            GpsEventSource source = new GpsEventSource(app); Object owner = new Object();
            try {
                source.acquireBackgroundUpdates(owner);
                assertTrue((Long) field(source, "gpsRequests") > 0);
                action.run(source);
            } catch (Exception error) { throw new AssertionError(error); }
            finally { source.setRecoveryReporter(null); source.releaseBackgroundUpdates(owner); }
        });
    }
    private static void seed(GpsEventSource source, long now, int used) throws Exception {
        Location old = new Location("gps"); old.setAccuracy(25f);
        old.setElapsedRealtimeNanos((now - 23_591) * 1_000_000L);
        set(source, "gps", old); set(source, "rawGps", old);
        set(source, "lastRawCallbackAt", now - 23_076); set(source, "lastRawCallbackAge", 514L);
        set(source, "lastRawDecision", "accept"); set(source, "lastStatusAt", now - 95);
        set(source, "satellitesVisible", 29); set(source, "satellitesUsed", used);
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    private static void check(GpsEventSource source) throws Exception {
        Method m = GpsEventSource.class.getDeclaredMethod("checkRecovery"); m.setAccessible(true); m.invoke(source);
    }
}
