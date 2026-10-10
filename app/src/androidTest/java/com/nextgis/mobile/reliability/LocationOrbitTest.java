package com.nextgis.mobile.reliability;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.BackgroundLayer;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;
import org.maplibre.geojson.Polygon;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Exercises actual MapLibre style, vector images, rendered features and map projection. */
@RunWith(AndroidJUnit4.class)
public class LocationOrbitTest {
    private static final Point FIX = Point.fromLngLat(21.0095, 52.23035);

    @Test public void orbitUsesCompassThenCourseWithACenteredAnchorAndVisibleSector() throws Exception {
        assertTrue("Use only an isolated emulator", android.os.Build.MODEL.contains("sdk")
                || android.os.Build.FINGERPRINT.startsWith("generic"));
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue(app.getPackageName().endsWith(".debug"));
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app);
        boolean hadIntro = preferences.contains(AppSettingsConstants.KEY_PREF_INTRO);
        boolean oldIntro = preferences.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        boolean hadGeoDialog = preferences.contains(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
        boolean oldGeoDialog = preferences.getBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, true);
        preferences.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                .putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, false).commit();
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            await(screen, () -> ((MapDrawable) app.getMap()).getMaplibreMap() != null
                    && ((MapDrawable) app.getMap()).getMaplibreMap().getStyle() != null);
            screen.onActivity(activity -> {
                try {
                    Method stop = activity.getMapFragment().getClass().getDeclaredMethod("stopDeviceHeading");
                    stop.setAccessible(true);
                    stop.invoke(activity.getMapFragment());
                } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                MapDrawable map = (MapDrawable) app.getMap();
                map.getMaplibreMap().moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(FIX.latitude(), FIX.longitude()), 18));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(500);
            screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap();
                Style style = map.getMaplibreMap().getStyle();
                Bitmap dot = style.getImage("user-marker-location-stand");
                Bitmap orbit = style.getImage("user-marker-location-go");
                assertNotNull(dot);
                assertNotNull(orbit);
                assertEquals(dot.getWidth(), orbit.getWidth());
                assertEquals(dot.getHeight(), orbit.getHeight());
                assertEquals(Color.rgb(49, 137, 214), orbit.getPixel(orbit.getWidth() / 2, orbit.getHeight() / 2));
                assertEquals(dot.getPixel(dot.getWidth() / 2, dot.getHeight() / 2),
                        orbit.getPixel(orbit.getWidth() / 2, orbit.getHeight() / 2));
                SymbolLayer symbol = (SymbolLayer) style.getLayer("user-location-layer");
                assertEquals("center", symbol.getIconAnchor().getValue());
                assertEquals("map", symbol.getIconRotationAlignment().getValue());
                style.addLayer(new BackgroundLayer("orbit-test-hot-layer"));
                map.updateLocation(FIX, true, 140f, 12f, 35f, 30f);
                assertStack(style);
                style.removeLayer("orbit-test-hot-layer");
            });
            awaitMarker(screen, app, true, 35f);
            screen.onActivity(activity -> assertSectorSize((MapDrawable) app.getMap(), app));
            screenshot(app, "orbit-compass.png");
            screen.onActivity(activity -> ((MapDrawable) app.getMap())
                    .loadLayersToMaplibreMapLite(Collections.emptyList(), true));
            awaitMarker(screen, app, true, 35f);
            screen.onActivity(activity -> assertStack(((MapDrawable) app.getMap()).getMaplibreMap().getStyle()));
            for (double zoom : new double[]{16, 19}) {
                screen.onActivity(activity -> ((MapDrawable) app.getMap()).getMaplibreMap()
                        .moveCamera(CameraUpdateFactory.zoomTo(zoom)));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                Thread.sleep(500);
                screen.onActivity(activity -> ((MapDrawable) app.getMap())
                        .updateLocation(FIX, false, 140f, 12f, 35f, 30f));
                awaitMarker(screen, app, true, 35f);
                screen.onActivity(activity -> assertSectorSize((MapDrawable) app.getMap(), app));
            }
            screen.onActivity(activity -> ((MapDrawable) app.getMap()).updateLocation(
                    FIX, true, 140f, 12f, 35f, 30f));
            screen.onActivity(activity -> ((MapDrawable) app.getMap()).updateLocationHeading(null, null));
            awaitMarker(screen, app, false, 0f);
            screen.onActivity(activity -> ((MapDrawable) app.getMap()).updateLocation(
                    FIX, false, 0f, 12f, null, null));
            awaitMarker(screen, app, true, 0f);
            screenshot(app, "orbit-course.png");
            screen.onActivity(activity -> ((MapDrawable) app.getMap()).updateLocationHeading(270f, 60f));
            awaitMarker(screen, app, true, 270f);
            screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap();
                assertStack(map.getMaplibreMap().getStyle());
                map.clearLocation();
            });
            await(screen, () -> ((MapDrawable) app.getMap()).getMaplibreMap().queryRenderedFeatures(
                    markerPoint((MapDrawable) app.getMap()), "user-location-layer").isEmpty());
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            if (hadIntro) restore.putBoolean(AppSettingsConstants.KEY_PREF_INTRO, oldIntro);
            else restore.remove(AppSettingsConstants.KEY_PREF_INTRO);
            if (hadGeoDialog) restore.putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, oldGeoDialog);
            else restore.remove(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
            restore.commit();
        }
    }

    private static void assertStack(Style style) {
        List<Layer> layers = style.getLayers();
        int n = layers.size();
        assertEquals("user-location-accuracy", layers.get(n - 3).getId());
        assertEquals("user-location-heading", layers.get(n - 2).getId());
        assertEquals("user-location-layer", layers.get(n - 1).getId());
    }

    private static void assertSectorSize(MapDrawable map, GISApplication app) {
        GeoJsonSource source = map.getMaplibreMap().getStyle().getSourceAs("user-location-source");
        PointF start = markerPoint(map);
        double longest = 0;
        // Native tile geometry simplifies and clips the original 32-step arc.
        for (Feature feature : source.querySourceFeatures(null)) {
            if (!feature.hasProperty("role") || !"heading".equals(feature.getStringProperty("role"))) continue;
            for (List<Point> ring : ((Polygon) feature.geometry()).coordinates()) {
                for (Point arc : ring) {
                    PointF end = map.getMaplibreMap().getProjection()
                            .toScreenLocation(new LatLng(arc.latitude(), arc.longitude()));
                    longest = Math.max(longest, Math.hypot(end.x - start.x, end.y - start.y));
                }
            }
        }
        float density = app.getResources().getDisplayMetrics().density;
        assertEquals(36f, longest / density, 1.5);
    }

    private static void screenshot(GISApplication app, String name) throws Exception {
        // QueryRenderedFeatures can see symbols while their first placement still fades in.
        Thread.sleep(1_000);
        int[] center = new int[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MapDrawable map = (MapDrawable) app.getMap();
            map.getMaplibreMapView().getLocationOnScreen(center);
            PointF point = markerPoint(map);
            center[0] += Math.round(point.x);
            center[1] += Math.round(point.y);
        });
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (FileOutputStream file = new FileOutputStream(new File(app.getExternalFilesDir(null), name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, file));
            int actual = image.getPixel(center[0], center[1]);
            assertEquals("Position dot must be visibly rendered at the fix", 49, Color.red(actual), 10);
            assertEquals("Position dot must be visibly rendered at the fix", 137, Color.green(actual), 10);
            assertEquals("Position dot must be visibly rendered at the fix", 214, Color.blue(actual), 10);
        } finally { image.recycle(); }
    }

    private static PointF markerPoint(MapDrawable map) {
        return map.getMaplibreMap().getProjection().toScreenLocation(new LatLng(FIX.latitude(), FIX.longitude()));
    }

    private static void awaitMarker(ActivityScenario<MainActivity> screen, GISApplication app,
                                    boolean direction, float bearing) throws Exception {
        await(screen, () -> {
            MapDrawable map = (MapDrawable) app.getMap();
            return map.getMaplibreMap().queryRenderedFeatures(markerPoint(map), "user-location-layer")
                    .stream().anyMatch(f -> f.getBooleanProperty("has_direction") == direction
                            && Math.abs(f.getNumberProperty("bearing").floatValue() - bearing) < 0.001f);
        });
    }

    private interface Check { boolean ready(); }
    private static void await(ActivityScenario<MainActivity> screen, Check check) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 30_000;
        while (!ready.get() && SystemClock.uptimeMillis() < deadline) {
            screen.onActivity(activity -> ready.set(check.ready()));
            if (!ready.get()) Thread.sleep(100);
        }
        assertTrue("Native MapLibre state did not settle", ready.get());
    }
}
