package com.nextgis.mobile.rendering;

import android.graphics.Color;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Technical geometry only. Two separate process phases; never use the user's APK or maps. */
@RunWith(AndroidJUnit4.class)
public class MapRenderStressTest {
    static final int FEATURES = 6400, VERTICES = 64, CYCLES = 60;
    private static final String STATE = "render-stress-fixture.json";
    private GISApplication app() {
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app); return app;
    }

    @Test public void seedHeavyLocalMapForARealColdProcessStart() throws Exception {
        GISApplication app = app();
        try (VectorStyleRenderingTest.Fixture fixture = new VectorStyleRenderingTest.Fixture()) {
            MapDrawable map = (MapDrawable) app.getMap(); org.json.JSONArray ids = new org.json.JSONArray();
            for (int group = 0; group < 2; group++) {
                NGWVectorLayerUI layer = new NGWVectorLayerUI(app, new File(map.getPath(), "render_stress_" + group));
                final int role = group;
                fixture.screen.onActivity(activity -> { try {
                    layer.setName("Technical render stress " + role); layer.create(GeoConstants.GTPolygon, Collections.emptyList());
                    layer.setIsEditable(false); layer.setVisible(true);
                    layer.setLayerOriginMetadata(LayerOriginMetadata.manualNgwLayer(0, LayerOriginMetadata.RENDER_MODE_LOCAL_VECTOR_TILES));
                    SimplePolygonStyle style = new SimplePolygonStyle(role == 0 ? Color.GREEN : Color.CYAN, Color.BLUE);
                    style.setAlpha(170); style.setOutAlpha(255); style.setWidth(2); style.setText("123");
                    style.setTextSize(14f); style.setTextColor(Color.RED); style.getLabelAttributes().setTextFont("Open Sans Bold");
                    layer.setRenderer(new SimpleFeatureRenderer(layer, style)); map.addLayer(layer);
                } catch (Exception error) { throw new AssertionError(error); } });
                android.database.sqlite.SQLiteDatabase db = DatabaseContext.getDatabaseForLayer(layer, false);
                db.beginTransaction();
                try {
                    for (int i = 0; i < (group == 0 ? FEATURES : 80); i++) {
                        double x = (i % 80 - 40) * 320.0, y = (i / 80 - 40) * 320.0;
                        GeoLinearRing ring = new GeoLinearRing(); ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                        for (int v = 0; v <= VERTICES; v++) {
                            double angle = 2 * Math.PI * (v % VERTICES) / VERTICES;
                            ring.add(new GeoPoint(x + 140 * Math.cos(angle), y + 140 * Math.sin(angle)));
                        }
                        GeoPolygon polygon = new GeoPolygon(); polygon.setCRS(GeoConstants.CRS_WEB_MERCATOR); polygon.setOuterRing(ring);
                        com.nextgis.maplib.datasource.Feature feature = new com.nextgis.maplib.datasource.Feature(i + 1, Collections.emptyList());
                        feature.setGeometry(polygon); layer.createFeatureBatch(feature, db, false);
                    }
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                assertTrue(layer.save()); ids.put(layer.getId());
            }
            fixture.screen.onActivity(activity -> { map.setVisible(true); map.setZoomAndCenter(11, new GeoPoint(0, 0), false, 0); assertTrue(map.save()); });
            JSONObject state = new JSONObject().put("ids", ids).put("features", FEATURES + 80).put("vertices_per_feature", VERTICES + 1);
            Files.write(new File(app.getFilesDir(), STATE).toPath(), state.toString().getBytes(StandardCharsets.UTF_8));
            // Fixture owns no entries in its cleanup list here: the next process must read them.
        }
    }

    @Test public void coldStartPanPinchAndRecreationKeepHeavyMvtRenderingAlive() throws Exception {
        GISApplication app = app(); int pid = android.os.Process.myPid();
        assertTrue(android.preference.PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(com.nextgis.mobile.util.AppSettingsConstants.KEY_PREF_INTRO, true)
                .putBoolean(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, false).commit());
        JSONObject state = new JSONObject(new String(Files.readAllBytes(new File(app.getFilesDir(), STATE).toPath()), StandardCharsets.UTF_8));
        JSONObject evidence = new JSONObject().put("schema_version", 1).put("pid", pid).put("status", "running")
                .put("model", android.os.Build.MODEL).put("manufacturer", android.os.Build.MANUFACTURER)
                .put("android_api", android.os.Build.VERSION.SDK_INT).put("features", state.getInt("features"))
                .put("vertices_per_feature", state.getInt("vertices_per_feature")).put("mvt_layers", 2);
        File output = new File(app.getFilesDir(), "render-stress-evidence.json");
        AtomicInteger frames = new AtomicInteger(); AtomicReference<android.view.View> target = new AtomicReference<>();
        AtomicReference<android.view.View> observed = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong lastFrame = new java.util.concurrent.atomic.AtomicLong(SystemClock.elapsedRealtime());
        AtomicReference<String> gpu = new AtomicReference<>();
        long began = SystemClock.elapsedRealtime(); int gestures = 0, recreations = 0;
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            for (int cycle = 0; cycle < CYCLES || SystemClock.elapsedRealtime() - began < 90_000; cycle++) {
                screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) app.getMap();
                    assertNotNull(map.getMaplibreMapView());
                    assertSame(activity.findViewById(com.nextgis.mobile.R.id.mapViewMaplibre), map.getMaplibreMapView());
                    app.getGpsEventSource().removeListener(activity.getMapFragment());
                    target.set(map.getMaplibreMapView());
                    if (observed.get() != map.getMaplibreMapView()) {
                        observed.set(map.getMaplibreMapView());
                        map.getMaplibreMapView().addOnDidFinishRenderingFrameListener((fully, encoding, rendering) -> {
                            frames.incrementAndGet(); lastFrame.set(SystemClock.elapsedRealtime());
                        });
                    }
                    captureGpu(map.getMaplibreMapView(), gpu);
                });
                android.view.View view = target.get();
                int[] location = new int[2]; view.getLocationOnScreen(location);
                float x = location[0] + view.getWidth() * .5f, y = location[1] + view.getHeight() * .5f;
                pan(x, y, view.getWidth() * (cycle % 2 == 0 ? .25f : -.25f)); gestures++;
                if (cycle % 3 == 0) { pinch(x, y, cycle % 2 == 0); gestures++; }
                CountDownLatch responsive = new CountDownLatch(1);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(responsive::countDown);
                assertTrue("UI event loop stalled after gesture " + cycle, responsive.await(2, TimeUnit.SECONDS));
                if (SystemClock.elapsedRealtime() - began > 10_000)
                    assertTrue("Native renderer stopped producing frames", SystemClock.elapsedRealtime() - lastFrame.get() < 5_000);
                assertEquals("The same process survives the workload", pid, android.os.Process.myPid());
                if (cycle == 20 || cycle == 40) { screen.recreate(); recreations++; }
                evidence.put("elapsed_ms", SystemClock.elapsedRealtime() - began).put("gestures", gestures)
                        .put("frames", frames.get()).put("recreations", recreations);
                Files.write(output.toPath(), evidence.toString().getBytes(StandardCharsets.UTF_8));
                Thread.sleep(900);
            }
            AtomicInteger visible = new AtomicInteger();
            screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap(); map.setZoomAndCenter(11, new GeoPoint(0, 0), false, 0);
                assertEquals("Persisted objects survive all reloads", FEATURES,
                        ((VectorLayer) map.getLayerById(state.optJSONArray("ids").optInt(0)))
                                .query(new GeoEnvelope(-20000, 20000, -20000, 20000)).size());
            });
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (visible.get() == 0 && SystemClock.elapsedRealtime() < deadline) {
                screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) app.getMap();
                    visible.set(map.getMaplibreMap().queryRenderedFeatures(new android.graphics.RectF(0, 0,
                            map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()),
                            "nglayer-layer-" + state.optJSONArray("ids").optInt(0)).size());
                });
                if (visible.get() == 0) Thread.sleep(100);
            }
            assertTrue("Heavy MVT polygons still render after gestures/recreation", visible.get() > 0);
            assertTrue("Native frames must continue", frames.get() >= 60);
            assertNotNull("Record the actual OpenGL device", gpu.get());
            evidence.put("gpu", gpu.get()).put("visible_features", visible.get()).put("status", "passed")
                    .put("smallest_width_dp", app.getResources().getConfiguration().smallestScreenWidthDp);
            Files.write(output.toPath(), evidence.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            for (int i = 0; i < state.getJSONArray("ids").length(); i++) {
                com.nextgis.maplib.api.ILayer layer = ((MapDrawable) app.getMap()).getLayerById(state.getJSONArray("ids").getInt(i));
                if (layer != null) assertTrue(layer.delete(false));
            }
            assertTrue(app.getMap().save());
        }
    }

    private static void captureGpu(android.view.View view, AtomicReference<String> gpu) {
        if (gpu.get() != null) return;
        if (view instanceof android.opengl.GLSurfaceView) ((android.opengl.GLSurfaceView) view).queueEvent(() -> {
            String vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR);
            String renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
            String version = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION);
            if (vendor != null && renderer != null && version != null) gpu.set(vendor + " / " + renderer + " / " + version);
        });
        else if (view instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++)
            captureGpu(((android.view.ViewGroup) view).getChildAt(i), gpu);
    }
    private static void pan(float x, float y, float dx) {
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= 9; i++) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), i == 0 ? MotionEvent.ACTION_DOWN
                    : i == 9 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, x + dx * i / 9, y, 0);
            try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event); } finally { event.recycle(); }
            SystemClock.sleep(20);
        }
    }
    private static void pinch(float x, float y, boolean out) {
        long down = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] properties = {new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()};
        properties[0].id = 0; properties[1].id = 1; properties[0].toolType = properties[1].toolType = MotionEvent.TOOL_TYPE_FINGER;
        for (int step = 0; step < 12; step++) {
            int pointers = step == 0 || step == 11 ? 1 : 2;
            float radius = out ? 90 - 5 * step : 30 + 5 * step;
            MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords(), new MotionEvent.PointerCoords()};
            coords[0].x = x - radius; coords[1].x = x + radius; coords[0].y = coords[1].y = y;
            coords[0].pressure = coords[1].pressure = 1; coords[0].size = coords[1].size = 1;
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 1 ? MotionEvent.ACTION_POINTER_DOWN | (1 << 8)
                    : step == 10 ? MotionEvent.ACTION_POINTER_UP | (1 << 8) : step == 11 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, pointers, properties, coords,
                    0, 0, 1, 1, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
            try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event); } finally { event.recycle(); }
            SystemClock.sleep(20);
        }
    }
}
