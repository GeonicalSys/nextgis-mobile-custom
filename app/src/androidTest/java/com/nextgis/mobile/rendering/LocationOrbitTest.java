package com.nextgis.mobile.rendering;

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
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Polygon;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/**
 * Explicit graphics smoke on an isolated emulator with a working EGL backend.
 * Kept outside reliability's headless CI package because its SwiftShader backend
 * cannot render the bitmap symbols needed by the screenshot assertions.
 */
@RunWith(AndroidJUnit4.class)
public class LocationOrbitTest {
    private static final Point FIX = Point.fromLngLat(21.0095, 52.23035);

    @Test public void phoneAccuracyFloorMatchesThePanelAndNativeCircleWithoutChangingFixes() throws Exception {
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        com.nextgis.mobile.util.VerificationEnvironment.requireIsolated(app);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app);
        boolean hadIntro = preferences.contains(AppSettingsConstants.KEY_PREF_INTRO);
        boolean oldIntro = preferences.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        boolean hadGeoDialog = preferences.contains(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
        boolean oldGeoDialog = preferences.getBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, true);
        boolean hadPanel = preferences.contains(SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL);
        String oldPanel = preferences.getString(SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL, "1");
        assertTrue(preferences.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                .putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, false)
                .putString(SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL, "3").commit());
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            awaitInitialScene(screen, app);
            screen.onActivity(activity -> {
                app.getGpsEventSource().removeListener(activity.getMapFragment());
                ((MapDrawable) app.getMap()).getMaplibreMap().moveCamera(
                        CameraUpdateFactory.newLatLngZoom(new LatLng(FIX.latitude(), FIX.longitude()), 22));
            });
            for (int kind = 0; kind < 5; kind++) {
                android.location.Location fix = new android.location.Location(kind == 3 ? "network" : "gps");
                fix.setLatitude(FIX.latitude()); fix.setLongitude(FIX.longitude());
                fix.setAccuracy(kind == 1 ? 8f : .5f);
                fix.setTime(System.currentTimeMillis());
                fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                if (kind == 2) {
                    android.os.Bundle extras = new android.os.Bundle();
                    extras.putBoolean(com.nextgis.maplib.util.ExternalGnssFixPolicy.EXTRA_NATIVE_NMEA, true);
                    fix.setExtras(extras);
                }
                if (kind == 4) fix.setMock(true);
                float rawAccuracy = fix.getAccuracy();
                float expected = kind == 0 ? 3f : rawAccuracy;
                screen.onActivity(activity -> {
                    activity.getMapFragment().onLocationChanged(fix);
                    android.widget.TextView accuracy = activity.findViewById(com.nextgis.mobile.R.id.tv_accuracy);
                    assertEquals(activity.getString(com.nextgis.mobile.R.string.location_reported_accuracy,
                            expected, activity.getString(com.nextgis.maplib.R.string.unit_meter)), accuracy.getText().toString());
                    assertEquals("Display must preserve provider accuracy", rawAccuracy, fix.getAccuracy(), 0f);
                    assertEquals(FIX.latitude(), fix.getLatitude(), 0d);
                    assertEquals(FIX.longitude(), fix.getLongitude(), 0d);
                });
                await(screen, () -> Math.abs(accuracyCircleRadius((MapDrawable) app.getMap()) - expected) < .08);
            }
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            if (hadIntro) restore.putBoolean(AppSettingsConstants.KEY_PREF_INTRO, oldIntro);
            else restore.remove(AppSettingsConstants.KEY_PREF_INTRO);
            if (hadGeoDialog) restore.putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, oldGeoDialog);
            else restore.remove(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
            if (hadPanel) restore.putString(SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL, oldPanel);
            else restore.remove(SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL);
            assertTrue(restore.commit());
        }
    }

    private static double accuracyCircleRadius(MapDrawable map) {
        GeoJsonSource source = map.getMaplibreMap().getStyle().getSourceAs("user-location-source");
        if (source == null) return Double.NaN;
        double radius = Double.NaN;
        for (Feature feature : source.querySourceFeatures(null)) {
            if (!feature.hasProperty("role") || !"accuracy".equals(feature.getStringProperty("role"))) continue;
            // Source queries can return multiple clipped native tiles; inspect their outer arcs.
            for (Point edge : ((Polygon) feature.geometry()).coordinates().get(0)) {
                float[] distance = new float[1];
                android.location.Location.distanceBetween(FIX.latitude(), FIX.longitude(),
                        edge.latitude(), edge.longitude(), distance);
                radius = Double.isNaN(radius) ? distance[0] : Math.max(radius, distance[0]);
            }
        }
        return radius;
    }

    @Test public void orbitUsesCompassThenCourseWithACenteredAnchorAndConfigurableSector() throws Exception {
        assertTrue("Use only an isolated emulator", android.os.Build.MODEL.contains("sdk")
                || android.os.Build.FINGERPRINT.startsWith("generic"));
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue(app.getPackageName().endsWith(".debug"));
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app);
        String sectorKey = AppSettingsConstants.KEY_PREF_SHOW_COMPASS_ACCURACY_SECTOR;
        boolean hadSector = preferences.contains(sectorKey);
        boolean oldSector = preferences.getBoolean(sectorKey, false);
        assertTrue(preferences.edit().remove(sectorKey).commit());
        boolean hadIntro = preferences.contains(AppSettingsConstants.KEY_PREF_INTRO);
        boolean oldIntro = preferences.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        boolean hadGeoDialog = preferences.contains(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
        boolean oldGeoDialog = preferences.getBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, true);
        preferences.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                .putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, false).commit();
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            awaitInitialScene(screen, app);
            screen.onActivity(activity -> {
                // This graphics fixture injects overlay fixes directly; a real GPS-expiry callback
                // must not clear them while screenshots and camera updates are in flight.
                app.getGpsEventSource().removeListener(activity.getMapFragment());
                try {
                    Method stop = activity.getMapFragment().getClass().getDeclaredMethod("stopDeviceHeading");
                    stop.setAccessible(true);
                    stop.invoke(activity.getMapFragment());
                    // The separately tested power warning can cover the marker at 200% font.
                    java.lang.reflect.Field delegate = MainActivity.class.getDeclaredField("backgroundLocationWarning$delegate");
                    delegate.setAccessible(true);
                    com.nextgis.mobile.location.BackgroundLocationWarning warning =
                            (com.nextgis.mobile.location.BackgroundLocationWarning) ((kotlin.Lazy<?>) delegate.get(activity)).getValue();
                    Method restrictions = warning.getClass().getDeclaredMethod("restrictions");
                    restrictions.setAccessible(true);
                    warning.setShownState((Integer) restrictions.invoke(warning));
                    warning.close();
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
                for (Bitmap marker : new Bitmap[]{dot, orbit}) {
                    int cx = marker.getWidth() / 2;
                    int cy = marker.getHeight() / 2;
                    float density = marker.getWidth() / 72f;
                    assertEquals(Color.rgb(8, 114, 232), marker.getPixel(cx, cy));
                    assertWhiteBorder(marker, cx, cy, density);
                    assertEquals("Blue centre and white border end before 5.5 dp", 0,
                            Color.alpha(marker.getPixel(cx + Math.round(5.5f * density), cy)));
                    assertEquals("Transparent ring opening", 0,
                            Color.alpha(marker.getPixel(cx + Math.round(9f * density), cy)));
                    assertEquals("Blue rim grows outward into the white rim", Color.rgb(8, 114, 232),
                            marker.getPixel(cx + Math.round(12f * density), cy));
                    assertEquals("White outer rim remains", Color.WHITE,
                            marker.getPixel(cx + Math.round(14f * density), cy));
                }
                assertEquals(dot.getPixel(dot.getWidth() / 2, dot.getHeight() / 2),
                        orbit.getPixel(orbit.getWidth() / 2, orbit.getHeight() / 2));
                SymbolLayer symbol = (SymbolLayer) style.getLayer("user-location-layer");
                assertEquals("center", symbol.getIconAnchor().getValue());
                assertEquals("map", symbol.getIconRotationAlignment().getValue());
                style.addLayer(new BackgroundLayer("orbit-test-hot-layer"));
                map.updateLocation(FIX, true, 140f, 12f, 35f, 30f);
                assertStack(style);
                style.removeLayer("orbit-test-hot-layer");
                ensurePointUnderCursor(style);
            });
            awaitMarker(screen, app, true, 35f);
            screen.onActivity(activity -> assertSectorRetainsGeometry((MapDrawable) app.getMap(), app, false));
            screenshot(app, "orbit-compass.png");
            screen.onActivity(activity -> ((MapDrawable) app.getMap())
                    .loadLayersToMaplibreMapLite(Collections.emptyList(), true));
            awaitMarker(screen, app, true, 35f);
            screen.onActivity(activity -> {
                Style style = ((MapDrawable) app.getMap()).getMaplibreMap().getStyle();
                assertStack(style);
                assertSectorRetainsGeometry((MapDrawable) app.getMap(), app, false);
                ensurePointUnderCursor(style);
            });
            for (double zoom : new double[]{16, 19}) {
                screen.onActivity(activity -> ((MapDrawable) app.getMap()).getMaplibreMap()
                        .moveCamera(CameraUpdateFactory.zoomTo(zoom)));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                Thread.sleep(500);
                screen.onActivity(activity -> ((MapDrawable) app.getMap())
                        .updateLocation(FIX, false, 140f, 12f, 35f, 30f));
                awaitMarker(screen, app, true, 35f);
                screen.onActivity(activity -> assertSectorRetainsGeometry((MapDrawable) app.getMap(), app, false));
            }
            screen.onActivity(activity -> ((MapDrawable) app.getMap()).updateLocation(
                    FIX, true, 140f, 12f, 35f, 30f));
            screen.onActivity(activity -> ((MapDrawable) app.getMap()).updateLocationHeading(null, null));
            awaitMarker(screen, app, false, 0f);
            screen.onActivity(activity -> ((MapDrawable) app.getMap()).updateLocation(
                    FIX, false, 0f, 12f, null, null));
            awaitMarker(screen, app, true, 0f);
            screenshot(app, "orbit-course.png");
            screen.onActivity(activity -> {
                Style style = ((MapDrawable) app.getMap()).getMaplibreMap().getStyle();
                style.removeLayer("orbit-test-feature");
                style.removeSource("orbit-test-feature-source");
                style.addSource(new GeoJsonSource("orbit-test-track-source", Feature.fromGeometry(
                        LineString.fromLngLats(java.util.Arrays.asList(
                                Point.fromLngLat(FIX.longitude() - 0.001, FIX.latitude()),
                                Point.fromLngLat(FIX.longitude() + 0.001, FIX.latitude()))))));
                style.addLayerBelow(new LineLayer("orbit-test-track", "orbit-test-track-source")
                        .withProperties(PropertyFactory.lineColor("#0872E8"), PropertyFactory.lineWidth(6f)),
                        "user-location-accuracy");
            });
            screenshot(app, "orbit-blue-track.png", true);
            screen.onActivity(activity -> ((MapDrawable) app.getMap()).updateLocationHeading(270f, 60f));
            awaitMarker(screen, app, true, 270f);
            screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap();
                assertStack(map.getMaplibreMap().getStyle());
                assertSectorRetainsGeometry(map, app, false);
            });
            resumeWithSectorPreference(screen, app, preferences, true);
            awaitSector(screen, app, true);
            screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap();
                map.getMaplibreMap().getStyle().addLayer(new BackgroundLayer("sector-test-hot-layer"));
                map.updateLocationHeading(35f, 30f);
                assertStack(map.getMaplibreMap().getStyle());
                map.getMaplibreMap().getStyle().removeLayer("sector-test-hot-layer");
                map.loadLayersToMaplibreMapLite(Collections.emptyList(), true);
            });
            awaitSector(screen, app, true);
            screen.onActivity(activity -> assertSectorRetainsGeometry((MapDrawable) app.getMap(), app, true));
            resumeWithSectorPreference(screen, app, preferences, false);
            awaitSector(screen, app, false);
            screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap();
                assertSectorRetainsGeometry(map, app, false);
                map.clearLocation();
            });
            await(screen, () -> ((MapDrawable) app.getMap()).getMaplibreMap().queryRenderedFeatures(
                    markerPoint((MapDrawable) app.getMap()), "user-location-layer").isEmpty());
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            if (hadSector) restore.putBoolean(sectorKey, oldSector);
            else restore.remove(sectorKey);
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
        assertEquals("user-location-accuracy", layers.get(n - 5).getId());
        assertEquals("user-location-heading", layers.get(n - 4).getId());
        assertEquals("user-location-heading-halo", layers.get(n - 3).getId());
        assertEquals("user-location-heading-outline", layers.get(n - 2).getId());
        assertEquals("user-location-layer", layers.get(n - 1).getId());
    }

    private static void ensurePointUnderCursor(Style style) {
        if (style.getSource("orbit-test-feature-source") == null) {
            style.addSource(new GeoJsonSource("orbit-test-feature-source",
                    FeatureCollection.fromFeatures(Collections.singletonList(Feature.fromGeometry(FIX)))));
        }
        if (style.getLayer("orbit-test-feature") == null) {
            style.addLayerBelow(new CircleLayer("orbit-test-feature", "orbit-test-feature-source")
                    .withProperties(PropertyFactory.circleRadius(7f),
                            PropertyFactory.circleColor("#D52339")), "user-location-accuracy");
        }
    }

    private static void resumeWithSectorPreference(ActivityScenario<MainActivity> screen,
            GISApplication app, SharedPreferences preferences, boolean visible) throws Exception {
        screen.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
        assertTrue(preferences.edit().putBoolean(
                AppSettingsConstants.KEY_PREF_SHOW_COMPASS_ACCURACY_SECTOR, visible).commit());
        screen.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        screen.onActivity(activity -> {
            app.getGpsEventSource().removeListener(activity.getMapFragment());
            try {
                Method stop = activity.getMapFragment().getClass().getDeclaredMethod("stopDeviceHeading");
                stop.setAccessible(true);
                stop.invoke(activity.getMapFragment());
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
            ((MapDrawable) app.getMap()).updateLocation(FIX, true, 140f, 12f, 35f, 30f);
        });
        awaitMarker(screen, app, true, 35f);
    }

    private static void awaitSector(ActivityScenario<MainActivity> screen, GISApplication app,
            boolean visible) throws Exception {
        await(screen, () -> {
            MapDrawable map = (MapDrawable) app.getMap();
            boolean rendered = !map.getMaplibreMap().queryRenderedFeatures(new android.graphics.RectF(0, 0,
                    map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()),
                    "user-location-heading", "user-location-heading-halo", "user-location-heading-outline").isEmpty();
            // Hiding the layer is synchronous, while setGeoJson is processed by native tiles.
            // An empty rendered query alone can succeed before the retained sector arrives.
            return rendered == visible && Math.abs(sectorRadiusDp(map, app) - 56d) <= 1.5;
        });
    }

    private static void assertSectorRetainsGeometry(MapDrawable map, GISApplication app, boolean visible) {
        Style style = map.getMaplibreMap().getStyle();
        String[] sectorLayers = {"user-location-heading", "user-location-heading-halo",
                "user-location-heading-outline"};
        for (String id : sectorLayers) {
            assertEquals("Compass sector follows saved preference: " + id,
                    visible ? org.maplibre.android.style.layers.Property.VISIBLE
                            : org.maplibre.android.style.layers.Property.NONE,
                    style.getLayer(id).getVisibility().getValue());
        }
        assertEquals("GPS accuracy remains visible", org.maplibre.android.style.layers.Property.VISIBLE,
                style.getLayer("user-location-accuracy").getVisibility().getValue());
        assertEquals("Rendered sector follows saved preference", !visible,
                map.getMaplibreMap().queryRenderedFeatures(new android.graphics.RectF(0, 0,
                        map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()),
                        sectorLayers).isEmpty());
        assertEquals(56f, sectorRadiusDp(map, app), 1.5);
    }

    private static double sectorRadiusDp(MapDrawable map, GISApplication app) {
        Style style = map.getMaplibreMap().getStyle();
        if (style == null) return Double.NaN;
        GeoJsonSource source = style.getSourceAs("user-location-source");
        if (source == null) return Double.NaN;
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
        return longest / density;
    }

    private static void screenshot(GISApplication app, String name) throws Exception {
        screenshot(app, name, false);
    }

    private static void screenshot(GISApplication app, String name, boolean blueTrack) throws Exception {
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
            int anchor = image.getPixel(center[0], center[1]);
            assertEquals("Blue centre stays at the fix", 8, Color.red(anchor), 10);
            assertEquals("Blue centre stays at the fix", 114, Color.green(anchor), 10);
            assertEquals("Blue centre stays at the fix", 232, Color.blue(anchor), 10);
            float density = app.getResources().getDisplayMetrics().density;
            assertWhiteBorder(image, center[0], center[1], density);
            if (blueTrack) {
                int track = image.getPixel(center[0] + Math.round(6f * density), center[1]);
                assertEquals("Blue track passes through the transparent opening", 8, Color.red(track), 10);
                assertEquals(114, Color.green(track), 10);
                assertEquals(232, Color.blue(track), 10);
            } else {
                int featureSampleX = center[0] + Math.round(3f * density);
                int featureSampleY = center[1] - Math.round(5f * density);
                int feature = image.getPixel(featureSampleX, featureSampleY);
                assertEquals("The smaller feature remains visible through the opening", 213, Color.red(feature), 10);
                assertEquals("Accuracy and compass fills must not tint the feature", 35, Color.green(feature), 10);
                assertEquals("Accuracy and compass fills must not tint the feature", 57, Color.blue(feature), 10);
            }
            // Sample inside the band, away from the white edge after texture rotation/filtering.
            int blueSampleX = center[0] + Math.round(11.5f * density);
            int actual = image.getPixel(blueSampleX, center[1]);
            assertEquals("Blue rim remains visible", 8, Color.red(actual), 10);
            assertEquals("Blue rim remains visible", 114, Color.green(actual), 10);
            assertEquals("Blue rim remains visible", 232, Color.blue(actual), 10);
        } finally { image.recycle(); }
    }

    private static void assertWhiteBorder(Bitmap image, int cx, int cy, float density) {
        for (int side : new int[]{-1, 1}) {
            boolean white = false;
            // Projection and the rotated icon atlas round the anchor to neighbouring screen pixels.
            for (int offset = (int) Math.floor(2f * density); offset <= Math.ceil(4f * density); offset++) {
                int color = image.getPixel(cx + side * offset, cy);
                white |= Color.alpha(color) > 220 && Color.red(color) > 220
                        && Color.green(color) > 220 && Color.blue(color) > 220;
            }
            assertTrue("White border separates the blue centre from the blue track on both sides", white);
        }
    }

    private static PointF markerPoint(MapDrawable map) {
        return map.getMaplibreMap().getProjection().toScreenLocation(new LatLng(FIX.latitude(), FIX.longitude()));
    }

    private static void awaitMarker(ActivityScenario<MainActivity> screen, GISApplication app,
                                    boolean direction, float bearing) throws Exception {
        awaitScene(screen, app, "marker direction=" + direction + " bearing=" + bearing, () -> {
            MapDrawable map = (MapDrawable) app.getMap();
            return map.getMaplibreMap().queryRenderedFeatures(markerPoint(map), "user-location-layer")
                    .stream().anyMatch(f -> f.getBooleanProperty("has_direction") == direction
                            && Math.abs(f.getNumberProperty("bearing").floatValue() - bearing) < 0.001f);
        });
    }

    private static void awaitInitialScene(ActivityScenario<MainActivity> screen, GISApplication app) throws Exception {
        java.util.concurrent.atomic.AtomicReference<com.nextgis.mobile.fragment.MapFragment> fragment =
                new java.util.concurrent.atomic.AtomicReference<>();
        screen.onActivity(activity -> fragment.set(activity.getMapFragment()));
        awaitScene(screen, app, "initial application scene", () -> {
            MapDrawable map = (MapDrawable) app.getMap();
            if (map.getMaplibreMap() == null || map.getMaplibreMap().getStyle() == null
                    || !map.getMaplibreMap().getStyle().isFullyLoaded()
                    || map.getMaplibreMapView() == null || map.getMaplibreMapView().getWidth() == 0
                    || map.getMaplibreMapView().getHeight() == 0) return false;
            // A Style object precedes completion of the app's asynchronous layer apply.
            // Its post-load hook reads actual GPS and can erase a directly injected test fix.
            return !flag(MapDrawable.class, map, "mLayerLoadInProgress")
                    && !flag(MapDrawable.class, map, "mPendingReload")
                    && flag(com.nextgis.mobile.fragment.MapFragment.class,
                            fragment.get(), "mapLibreLayersAppliedForCurrentView");
        });
    }

    private static boolean flag(Class<?> owner, Object target, String name) {
        return (Boolean) value(owner, target, name);
    }

    private static Object value(Class<?> owner, Object target, String name) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void awaitScene(ActivityScenario<MainActivity> screen, GISApplication app,
                                   String phase, Check check) throws Exception {
        try { await(screen, check); }
        catch (AssertionError timeout) {
            java.util.concurrent.atomic.AtomicReference<String> actual = new java.util.concurrent.atomic.AtomicReference<>();
            try {
                screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) app.getMap();
                    org.maplibre.android.maps.MapLibreMap nativeMap = map.getMaplibreMap();
                    Style style = nativeMap == null ? null : nativeMap.getStyle();
                    StringBuilder state = new StringBuilder("activity=").append(activity.getLifecycle().getCurrentState())
                            .append(" focus=").append(activity.hasWindowFocus())
                            .append(" loading=").append(flag(MapDrawable.class, map, "mLayerLoadInProgress"))
                            .append(" pendingReload=").append(flag(MapDrawable.class, map, "mPendingReload"))
                            .append(" cachedFix=").append(value(MapDrawable.class, map, "lastUserLocationPoint"))
                            .append(" applied=").append(flag(com.nextgis.mobile.fragment.MapFragment.class,
                                    activity.getMapFragment(), "mapLibreLayersAppliedForCurrentView"));
                    if (nativeMap != null) state.append(" camera=").append(nativeMap.getCameraPosition());
                    if (style != null) {
                        GeoJsonSource source = style.getSourceAs("user-location-source");
                        state.append(" styleLoaded=").append(style.isFullyLoaded())
                                .append(" markerLayer=").append(style.getLayer("user-location-layer") != null);
                        if (source != null) {
                            List<Feature> features = source.querySourceFeatures(null);
                            state.append(" sourceFeatures=").append(features.size());
                            for (Feature feature : features) if (feature.geometry() instanceof Point) {
                                state.append(" point=").append(feature.toJson()); break;
                            }
                        }
                        state.append(" renderedAtFix=").append(nativeMap.queryRenderedFeatures(
                                markerPoint(map), "user-location-layer").size());
                    }
                    actual.set(state.toString());
                });
            } catch (RuntimeException diagnosticFailure) { actual.set("unavailable: " + diagnosticFailure); }
            throw new AssertionError(phase + " did not settle: " + actual.get(), timeout);
        }
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
