package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.service.WalkEditService;
import com.nextgis.maplibui.util.WalkSessionPolicy;
import com.nextgis.maplibui.util.WalkSessionStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import static org.junit.Assert.*;

/** Measures the existing full-WKT checkpoint on real storage and verifies its terminal fence. */
@RunWith(AndroidJUnit4.class)
public class WalkCheckpointTest {
    @Test public void longWalkCheckpointAndTerminalFenceOnNativePreferences() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        SharedPreferences testPreferences = app.getSharedPreferences("walk_benchmark_" + UUID.randomUUID(), Context.MODE_PRIVATE);
        Context isolated = new ContextWrapper(app) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return WalkEditService.TEMP_PREFERENCES.equals(name) ? testPreferences : super.getSharedPreferences(name, mode);
            }
        };
        JSONArray results = new JSONArray();
        try {
            for (int size : new int[] { 1000, 10000, 50000 }) {
                GeoLineString line = new GeoLineString();
                line.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                for (int i = 0; i < size; i++) line.add(new GeoPoint(i, i % 17));
                String[] id = new String[1];
                long[] elapsed = new long[3];
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    id[0] = WalkSessionStore.begin(isolated, -123, -1, line, 0, 0, size, "");
                    assertNotNull(id[0]);
                    for (int sample = 0; sample < elapsed.length; sample++) {
                        long began = SystemClock.elapsedRealtimeNanos();
                        assertTrue(WalkSessionStore.updateGeometry(isolated, id[0], line, size, false));
                        elapsed[sample] = SystemClock.elapsedRealtimeNanos() - began;
                    }
                });
                assertEquals(size, ((GeoLineString) WalkSessionStore.load(isolated).geometry()).getPointCount());
                assertTrue(WalkSessionStore.setPhase(isolated, id[0], WalkSessionPolicy.Phase.FINISHING));
                assertTrue(WalkSessionStore.setPhase(isolated, id[0], WalkSessionPolicy.Phase.FINISHED));
                assertFalse(WalkSessionStore.updateGeometry(isolated, id[0], line, size, false));
                assertTrue(WalkSessionStore.clear(isolated, id[0]));
                assertFalse(WalkSessionStore.updateGeometry(isolated, id[0], line, size, false));
                JSONArray milliseconds = new JSONArray();
                for (long duration : elapsed) milliseconds.put(duration / 1000000.0);
                results.put(new JSONObject().put("nodes", size).put("checkpoint_ms", milliseconds));
            }
            Files.write(new File(app.getFilesDir(), "reliability-walk-benchmark.json").toPath(),
                    results.toString(2).getBytes(StandardCharsets.UTF_8));
        } finally { assertTrue(testPreferences.edit().clear().commit()); }
    }
}
