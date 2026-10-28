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
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            JSONObject state = new JSONObject().put("schema_version", 2).put("seed_pid", android.os.Process.myPid())
                    .put("ids", ids).put("features", FEATURES + 80).put("vertices_per_feature", VERTICES + 1);
            Files.write(new File(app.getFilesDir(), STATE).toPath(), state.toString().getBytes(StandardCharsets.UTF_8));
            // Fixture owns no entries in its cleanup list here: the next process must read them.
        }
    }

    @Test public void coldStartPanPinchAndRecreationKeepHeavyMvtRenderingAlive() throws Exception {
        GISApplication app = app(); int pid = android.os.Process.myPid();
        assertTrue(android.preference.PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(com.nextgis.mobile.util.AppSettingsConstants.KEY_PREF_INTRO, true)
                .putBoolean(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, false)
                .putFloat(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL, 11)
                .putLong(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SCROLL_X, Double.doubleToRawLongBits(0))
                .putLong(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SCROLL_Y, Double.doubleToRawLongBits(0)).commit());
        JSONObject state = new JSONObject(new String(Files.readAllBytes(new File(app.getFilesDir(), STATE).toPath()), StandardCharsets.UTF_8));
        assertEquals("The cold workload requires this exact seeded fixture contract", 2, state.getInt("schema_version"));
        assertTrue("Seed process identity must be recorded", state.getInt("seed_pid") > 0);
        assertNotEquals("The workload must really start in a new process", state.getInt("seed_pid"), pid);
        assertEquals(FEATURES + 80, state.getInt("features")); assertEquals(VERTICES + 1, state.getInt("vertices_per_feature"));
        assertEquals(2, state.getJSONArray("ids").length());
        assertViewportOracle();
        JSONObject evidence = new JSONObject().put("schema_version", 2).put("pid", pid).put("seed_pid", state.getInt("seed_pid"))
                .put("render_backend", "sqlite_native").put("status", "running")
                .put("model", android.os.Build.MODEL).put("manufacturer", android.os.Build.MANUFACTURER)
                .put("android_api", android.os.Build.VERSION.SDK_INT).put("features", state.getInt("features"))
                .put("vertices_per_feature", state.getInt("vertices_per_feature")).put("mvt_layers", 2);
        File output = new File(app.getFilesDir(), "render-stress-evidence.json");
        AtomicInteger frames = new AtomicInteger(); AtomicReference<android.view.View> target = new AtomicReference<>();
        AtomicReference<android.view.View> observed = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong lastFrame = new java.util.concurrent.atomic.AtomicLong(SystemClock.elapsedRealtime());
        AtomicReference<String> gpu = new AtomicReference<>();
        AtomicInteger actualMvtLayers = new AtomicInteger();
        long peakPss = 0, peakJava = 0, peakNative = 0;
        long began = SystemClock.elapsedRealtime(); int gestures = 0, recreations = 0, panChanges = 0, pinchChanges = 0;
        ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class);
        android.os.Handler headingLoop = new android.os.Handler(android.os.Looper.getMainLooper());
        AtomicInteger headingUpdates = new AtomicInteger();
        Runnable headingPulse = new Runnable() {
            @Override public void run() {
                MapDrawable map = (MapDrawable) app.getMap();
                if (map.getMaplibreMap() != null && map.getMaplibreMapView() != null
                        && map.getMaplibreMapView().isAttachedToWindow()
                        && map.getMaplibreMap().getStyle() != null && map.getMaplibreMap().getStyle().isFullyLoaded()) {
                    int sample = headingUpdates.incrementAndGet();
                    map.updateLocation(org.maplibre.geojson.Point.fromLngLat(0, 0), true, 0, 5,
                            (float) (sample * 7 % 360), 15f);
                }
                headingLoop.postDelayed(this, 100);
            }
        };
        headingLoop.post(headingPulse);
        long peakRss = 0; int sourceChecks = 0;
        try {
            awaitSqliteSources(screen, app, state.getJSONArray("ids"));
            long workloadBegan = SystemClock.elapsedRealtime();
            evidence.put("startup_elapsed_ms", workloadBegan - began);
            for (int cycle = 0; cycle < CYCLES || SystemClock.elapsedRealtime() - workloadBegan < 90_000; cycle++) {
                actualMvtLayers.set(awaitSqliteSources(screen, app, state.getJSONArray("ids"))); sourceChecks++;
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
                float[] area = readyTouchArea(screen, app);
                float x = area[0], y = area[1], width = area[2];
                org.maplibre.android.camera.CameraPosition before = camera(screen, app);
                pan(x, y, width * (cycle % 2 == 0 ? .25f : -.25f)); gestures++;
                org.maplibre.android.camera.CameraPosition afterPan = camera(screen, app);
                if (before != null && afterPan != null && before.target != null && afterPan.target != null
                        && (Math.abs(before.target.getLatitude() - afterPan.target.getLatitude()) > .0000001
                        || Math.abs(before.target.getLongitude() - afterPan.target.getLongitude()) > .0000001)) panChanges++;
                if (cycle % 3 == 0) {
                    pinch(x, y, width, cycle % 2 == 0); gestures++;
                    org.maplibre.android.camera.CameraPosition afterPinch = camera(screen, app);
                    if (afterPan != null && afterPinch != null && Math.abs(afterPan.zoom - afterPinch.zoom) > .01) pinchChanges++;
                }
                CountDownLatch responsive = new CountDownLatch(1);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(responsive::countDown);
                assertTrue("UI event loop stalled after gesture " + cycle, responsive.await(2, TimeUnit.SECONDS));
                if (SystemClock.elapsedRealtime() - began > 10_000)
                    assertTrue("Native renderer stopped producing frames", SystemClock.elapsedRealtime() - lastFrame.get() < 5_000);
                assertEquals("The same process survives the workload", pid, android.os.Process.myPid());
                if (cycle == 20 || cycle == 40) { screen.recreate(); recreations++; }
                android.os.Debug.MemoryInfo memory = new android.os.Debug.MemoryInfo(); android.os.Debug.getMemoryInfo(memory);
                peakPss = Math.max(peakPss, memory.getTotalPss());
                peakJava = Math.max(peakJava, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                peakNative = Math.max(peakNative, android.os.Debug.getNativeHeapAllocatedSize());
                java.util.Map<String, Object> budget = ((MapDrawable) app.getMap()).getRenderMemoryGuard().snapshot();
                peakRss = Math.max(peakRss, ((Number) budget.get("process_rss_bytes")).longValue());
                evidence.put("elapsed_ms", SystemClock.elapsedRealtime() - workloadBegan).put("gestures", gestures)
                        .put("frames", frames.get()).put("recreations", recreations)
                        .put("pan_changes", panChanges).put("pinch_changes", pinchChanges)
                        .put("mvt_layers", actualMvtLayers.get()).put("peak_pss_kib", peakPss)
                        .put("sqlite_layers", actualMvtLayers.get()).put("source_checks", sourceChecks).put("global_feature_count", 0)
                        .put("peak_java_bytes", peakJava).put("peak_native_bytes", peakNative)
                        .put("heading_updates", headingUpdates.get()).put("peak_process_rss_bytes", peakRss)
                        .put("memory_budget", new JSONObject(budget))
                        .put("last_frame_age_ms", SystemClock.elapsedRealtime() - lastFrame.get())
                        .put("gpu", gpu.get());
                Files.write(output.toPath(), evidence.toString().getBytes(StandardCharsets.UTF_8));
                Thread.sleep(900);
            }
            long workloadElapsed = SystemClock.elapsedRealtime() - workloadBegan;
            evidence.put("elapsed_ms", workloadElapsed).put("workload_elapsed_ms", workloadElapsed);
            screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap(); map.setZoomAndCenter(11, new GeoPoint(0, 0), false, 0);
                assertEquals("Persisted objects survive all reloads", FEATURES,
                        ((VectorLayer) map.getLayerById(state.optJSONArray("ids").optInt(0)))
                                .query(new GeoEnvelope(-20000, 20000, -20000, 20000)).size());
                assertEquals(80, ((VectorLayer) map.getLayerById(state.optJSONArray("ids").optInt(1)))
                        .query(new GeoEnvelope(-20000, 20000, -20000, 20000)).size());
            });
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (!atFinalCamera(camera(screen, app)) && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
            assertTrue("The final camera must actually reach zoom11/center0", atFinalCamera(camera(screen, app)));
            awaitSqliteSources(screen, app, state.getJSONArray("ids"));
            // Freeze independent expectations before any native feature query; never derive them
            // from what the renderer happened to return or demand every offscreen object.
            double[][] viewport = viewport(screen, app);
            List<Set<Long>> required = new ArrayList<>();
            required.add(requiredIds(viewport, FEATURES)); required.add(requiredIds(viewport, 80));
            int requiredCount = required.get(0).size() + required.get(1).size();
            assertTrue("The known final camera must contain complete technical polygons", requiredCount > 0);
            deadline = SystemClock.elapsedRealtime() + 30000;
            int visible = 0, missing = requiredCount;
            while (SystemClock.elapsedRealtime() < deadline) {
                JSONArray coverage = visibleCoverage(screen, app, state.getJSONArray("ids"), required);
                evidence.put("visible_coverage", coverage); visible = 0; missing = 0;
                for (int i = 0; i < coverage.length(); i++) {
                    JSONObject row = coverage.getJSONObject(i);
                    assertEquals("Native geometry cannot contain label/invalid roles", 0, row.getInt("invalid_role_hits"));
                    assertEquals("Native IDs must belong to the independent fixture", 0, row.getInt("unexpected_id_hits"));
                    visible += row.getInt("rendered_ids_count"); missing += row.getInt("missing_ids_count");
                }
                if (missing == 0) break;
                Thread.sleep(100);
            }
            assertEquals("Every independently expected fully visible polygon must render after stress", 0, missing);
            assertTrue("Heavy SQLite polygons still render after gestures/recreation", visible >= requiredCount);
            actualMvtLayers.set(awaitSqliteSources(screen, app, state.getJSONArray("ids")));
            assertTrue("Native frames must continue", frames.get() >= 60);
            assertTrue("Injected pans must actually move the native camera: " + panChanges, panChanges >= 50);
            assertTrue("Injected pinches must actually change native zoom: " + pinchChanges, pinchChanges >= 15);
            assertTrue("GPS/heading updates must exercise the native overlay during the workload", headingUpdates.get() >= 300);
            assertEquals("Both sources must actually use native MVT", 2, actualMvtLayers.get());
            assertNotNull("Record the actual OpenGL device", gpu.get());
            evidence.put("gpu", gpu.get()).put("visible_features", visible).put("visible_required_count", requiredCount)
                    .put("visible_missing_count", missing).put("total_elapsed_ms", SystemClock.elapsedRealtime() - began).put("status", "passed")
                    .put("smallest_width_dp", app.getResources().getConfiguration().smallestScreenWidthDp);
            Files.write(output.toPath(), evidence.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception | AssertionError error) {
            evidence.put("status", "failed").put("failure", error.getMessage())
                    .put("frames", frames.get()).put("last_frame_age_ms", SystemClock.elapsedRealtime() - lastFrame.get());
            evidence.put("failure_state", renderState(screen, app));
            if (VerificationEnvironment.isEmulator()) {
                android.view.accessibility.AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                evidence.put("active_window_package", root == null ? "unknown" : String.valueOf(root.getPackageName()));
                android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                if (image != null) {
                    File directory = new File(app.getFilesDir(), "firefly-qa"); directory.mkdirs();
                    try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new File(directory, "render-stress-failure.png"))) {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
                    }
                    image.recycle();
                }
            }
            Files.write(output.toPath(), evidence.toString().getBytes(StandardCharsets.UTF_8));
            // A delayed recovery is evidence, never a retry that turns this failed run green.
            for (int sample = 1; sample <= 2; sample++) {
                Thread.sleep(5_000);
                evidence.put("after_" + (sample * 5) + "s", renderState(screen, app)
                        .put("frames", frames.get()).put("last_frame_age_ms", SystemClock.elapsedRealtime() - lastFrame.get()));
                Files.write(output.toPath(), evidence.toString().getBytes(StandardCharsets.UTF_8));
            }
            throw error;
        } finally {
            headingLoop.removeCallbacks(headingPulse);
            screen.close();
            for (int i = 0; i < state.getJSONArray("ids").length(); i++) {
                com.nextgis.maplib.api.ILayer layer = ((MapDrawable) app.getMap()).getLayerById(state.getJSONArray("ids").getInt(i));
                if (layer != null) assertTrue(layer.delete(false));
            }
            assertTrue(app.getMap().save());
        }
    }

    private static int awaitSqliteSources(ActivityScenario<MainActivity> screen, GISApplication app, JSONArray ids) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 30000; AtomicInteger ready = new AtomicInteger();
        do {
            screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap(); int count = 0;
                if (map.getMaplibreMap() != null && map.getMaplibreMap().getStyle() != null
                        && map.getMaplibreMap().getStyle().isFullyLoaded()) for (int i = 0; i < ids.length(); i++) {
                    com.nextgis.maplib.api.ILayer layer = map.getLayerById(ids.optInt(i));
                    String source = layer == null ? null : map.getVectorRenderSourceId(layer.getId());
                    SqliteVectorTileProvider provider = layer == null ? null : map.getSqliteVectorProvider(layer.getId());
                    List<org.maplibre.geojson.Feature> cached = layer == null ? null : map.getLayerFeatures(layer);
                    if (source != null && map.isSqliteVectorLayer(layer.getId()) && provider != null && provider.isCurrent()
                            && map.getMaplibreMap().getStyle().getSource(source) instanceof org.maplibre.android.style.sources.CustomVectorSource
                            && cached != null && cached.isEmpty()) count++;
                }
                ready.set(count);
            });
            if (ready.get() == 2) return ready.get();
            Thread.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertEquals("Both actual SQLite sources must be current and keep empty global feature lists", 2, ready.get());
        return ready.get();
    }

    private static boolean atFinalCamera(org.maplibre.android.camera.CameraPosition camera) {
        return camera != null && camera.target != null && Math.abs(camera.zoom - 11) < .001
                && Math.abs(camera.target.getLatitude()) < .0000001 && Math.abs(camera.target.getLongitude()) < .0000001;
    }

    private static double[][] viewport(ActivityScenario<MainActivity> screen, GISApplication app) {
        double[][] result = new double[4][2];
        screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) app.getMap(); float width = map.getMaplibreMapView().getWidth(), height = map.getMaplibreMapView().getHeight();
            android.graphics.PointF[] corners = {new android.graphics.PointF(0, 0), new android.graphics.PointF(width, 0),
                    new android.graphics.PointF(width, height), new android.graphics.PointF(0, height)};
            for (int i = 0; i < 4; i++) {
                org.maplibre.android.geometry.LatLng position = map.getMaplibreMap().getProjection().fromScreenLocation(corners[i]);
                result[i][0] = Math.toRadians(position.getLongitude()) * 6378137.0;
                result[i][1] = Math.log(Math.tan(Math.PI / 4 + Math.toRadians(position.getLatitude()) / 2)) * 6378137.0;
            }
        });
        return result;
    }

    /** Conservative independent oracle: the authored radius140 circle must fit inside every viewport edge. */
    private static Set<Long> requiredIds(double[][] corners, int count) {
        double area = 0; for (int i = 0; i < 4; i++) area += corners[i][0] * corners[(i + 1) % 4][1] - corners[(i + 1) % 4][0] * corners[i][1];
        assertTrue("Viewport must have nonzero finite area", Double.isFinite(area) && Math.abs(area) > 1);
        double sign = Math.signum(area); Set<Long> result = new HashSet<>();
        for (int index = 0; index < count; index++) {
            double x = (index % 80 - 40) * 320.0, y = (index / 80 - 40) * 320.0; boolean inside = true;
            for (int i = 0; i < 4; i++) {
                double[] a = corners[i], b = corners[(i + 1) % 4]; double dx = b[0] - a[0], dy = b[1] - a[1];
                if (sign * (dx * (y - a[1]) - dy * (x - a[0])) <= 142 * Math.hypot(dx, dy)) { inside = false; break; }
            }
            if (inside) result.add((long) index + 1);
        }
        return result;
    }

    private static void assertViewportOracle() {
        Set<Long> square = new HashSet<>();
        for (int row = 38; row <= 42; row++) for (int column = 38; column <= 42; column++) square.add((long) row * 80 + column + 1);
        assertEquals(square, requiredIds(new double[][]{{-1000, -1000}, {1000, -1000}, {1000, 1000}, {-1000, 1000}}, FEATURES));
        assertEquals(square, requiredIds(new double[][]{{-1000, -1000}, {-1000, 1000}, {1000, 1000}, {1000, -1000}}, FEATURES));
        Set<Long> diamond = new HashSet<>();
        for (int row = -3; row <= 3; row++) for (int column = -3; column <= 3; column++)
            if (Math.abs(row) + Math.abs(column) <= 3) diamond.add((long) (40 + row) * 80 + 40 + column + 1);
        double radius = Math.sqrt(2) * 1000;
        assertEquals(diamond, requiredIds(new double[][]{{0, -radius}, {radius, 0}, {0, radius}, {-radius, 0}}, FEATURES));
        assertTrue(requiredIds(new double[][]{{-100, -100}, {100, -100}, {100, 100}, {-100, 100}}, FEATURES).isEmpty());
    }

    private static JSONArray visibleCoverage(ActivityScenario<MainActivity> screen, GISApplication app, JSONArray ids,
                                             List<Set<Long>> required) {
        JSONArray result = new JSONArray();
        screen.onActivity(activity -> {
            try {
                MapDrawable map = (MapDrawable) app.getMap();
                for (int group = 0; group < 2; group++) {
                    int count = group == 0 ? FEATURES : 80, invalid = 0, unexpected = 0; Set<Long> seen = new HashSet<>();
                    for (org.maplibre.geojson.Feature feature : map.getMaplibreMap().queryRenderedFeatures(new android.graphics.RectF(0, 0,
                            map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()), "nglayer-layer-" + ids.getInt(group))) {
                        if (!(feature.geometry() instanceof org.maplibre.geojson.Polygon || feature.geometry() instanceof org.maplibre.geojson.MultiPolygon)
                                || feature.hasProperty(NativeVectorTileEncoder.LABEL_ANCHOR)) { invalid++; continue; }
                        com.google.gson.JsonElement fid = feature.getProperty(MPLFeaturesUtils.prop_featureid); long id;
                        try { if (fid == null || fid.isJsonNull()) { invalid++; continue; } id = fid.getAsLong(); }
                        catch (RuntimeException malformed) { invalid++; continue; }
                        if (id < 1 || id > count) unexpected++; else seen.add(id);
                    }
                    Set<Long> missing = new HashSet<>(required.get(group)); missing.removeAll(seen);
                    JSONArray examples = new JSONArray(); for (long id : missing) { if (examples.length() >= 16) break; examples.put(id); }
                    result.put(new JSONObject().put("fixture_features", count).put("required_ids_count", required.get(group).size())
                            .put("rendered_ids_count", seen.size()).put("missing_ids_count", missing.size()).put("missing_id_examples", examples)
                            .put("invalid_role_hits", invalid).put("unexpected_id_hits", unexpected));
                }
            } catch (org.json.JSONException error) { throw new AssertionError("Cannot record independent viewport coverage", error); }
        });
        return result;
    }

    private static float[] readyTouchArea(ActivityScenario<MainActivity> screen, GISApplication app) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        AtomicReference<float[]> area = new AtomicReference<>();
        while (SystemClock.elapsedRealtime() < deadline) {
            screen.onActivity(activity -> {
                android.view.View view = ((MapDrawable) app.getMap()).getMaplibreMapView();
                if (view == null || !view.isAttachedToWindow() || !view.isShown() || !view.hasWindowFocus()
                        || view.getWidth() == 0 || view.getHeight() == 0) return;
                android.graphics.Rect visible = new android.graphics.Rect();
                if (view.getGlobalVisibleRect(visible) && visible.width() > 0 && visible.height() > 0)
                    area.set(new float[]{visible.exactCenterX(), visible.exactCenterY(), visible.width()});
            });
            if (area.get() != null) return area.get();
            Thread.sleep(25);
        }
        throw new AssertionError("The verification map window did not regain a measured, focused touch area after recreation");
    }

    private static org.maplibre.android.camera.CameraPosition camera(ActivityScenario<MainActivity> screen, GISApplication app) {
        AtomicReference<org.maplibre.android.camera.CameraPosition> result = new AtomicReference<>();
        screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) app.getMap();
            if (map.getMaplibreMap() != null) result.set(map.getMaplibreMap().getCameraPosition());
        });
        return result.get();
    }

    private static JSONObject renderState(ActivityScenario<MainActivity> screen, GISApplication app) {
        JSONObject state = new JSONObject();
        screen.onActivity(activity -> {
            try {
                MapDrawable map = (MapDrawable) app.getMap();
                android.view.View view = map.getMaplibreMapView();
                state.put("view_attached", view != null && view.isAttachedToWindow())
                        .put("view_shown", view != null && view.isShown())
                        .put("window_focus", view != null && view.hasWindowFocus())
                        .put("view_identity", System.identityHashCode(view))
                        .put("model_x", map.getMapCenter().getX()).put("model_y", map.getMapCenter().getY());
                if (map.getMaplibreMap() != null) {
                    org.maplibre.android.camera.CameraPosition camera = map.getMaplibreMap().getCameraPosition();
                    state.put("zoom", camera.zoom).put("latitude", camera.target.getLatitude()).put("longitude", camera.target.getLongitude());
                    org.maplibre.android.maps.Style style = map.getMaplibreMap().getStyle();
                    state.put("style_loaded", style != null && style.isFullyLoaded());
                    if (style != null) state.put("sources", style.getSources().size()).put("layers", style.getLayers().size());
                }
                Object fragment = activity.getMapFragment();
                for (String field : new String[]{"mapLibreHostResumed", "mapLibreLayersAppliedForCurrentView",
                        "awaitingMapLibreFrameAfterResume", "mapLibreRenderRecoveryActive",
                        "mapLibreRenderRecoveryAttempt", "mapLibreLastFrameFullyRendered"}) {
                    java.lang.reflect.Field member = fragment.getClass().getDeclaredField(field);
                    member.setAccessible(true); state.put(field, member.get(fragment));
                }
            } catch (Exception error) {
                try { state.put("state_read_error", error.getClass().getSimpleName()); }
                catch (org.json.JSONException ignored) { }
            }
        });
        return state;
    }

    private static void captureGpu(android.view.View view, AtomicReference<String> gpu) {
        if (gpu.get() != null) return;
        Runnable read = () -> {
            String vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR);
            String renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
            String version = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION);
            if (vendor != null && renderer != null && version != null) gpu.set(vendor + " / " + renderer + " / " + version);
        };
        if (view instanceof android.opengl.GLSurfaceView) ((android.opengl.GLSurfaceView) view).queueEvent(read);
        else if (view instanceof android.view.SurfaceView && view.getClass().getName().startsWith("org.maplibre.android.maps.renderer.")) {
            // MapLibre 13.7 has its own SurfaceView, not android.opengl.GLSurfaceView.
            // queueEvent is the public SDK API; reflection also retains comparison with13.0.2.
            try { view.getClass().getMethod("queueEvent", Runnable.class).invoke(view, read); }
            catch (ReflectiveOperationException error) { throw new AssertionError("MapLibre render queue unavailable", error); }
        }
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
    private static void pinch(float x, float y, float width, boolean out) {
        long down = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] properties = {new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()};
        properties[0].id = 0; properties[1].id = 1; properties[0].toolType = properties[1].toolType = MotionEvent.TOOL_TYPE_FINGER;
        for (int step = 0; step < 12; step++) {
            int pointers = step == 0 || step == 11 ? 1 : 2;
            // SDK13.7 rejects scale speeds below0.6dp/ms. Small fixed-pixel motions
            // were ignored on high-density screens; use a reciprocal screen-relative span.
            float progress = Math.max(0, Math.min(8, step - 1)) / 8f;
            float radius = width * (out ? .40f - .28f * progress : .12f + .28f * progress);
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
