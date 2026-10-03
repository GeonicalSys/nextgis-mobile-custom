package com.nextgis.mobile.reliability;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.MotionEvent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.MaplibreMapInteraction;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.fragment.MapFragment;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.view.MapControlRail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** Real activity, menus, map projection and SQLite on an isolated test emulator. */
@RunWith(AndroidJUnit4.class)
public class MapEditingToolsTest {
    private ActivityScenario<MainActivity> scenario;
    private GISApplication app;
    private SharedPreferences preferences;
    private boolean hadIntro, oldIntro;
    private final List<VectorLayerUI> layers = new ArrayList<>();
    private final List<Long> ids = new ArrayList<>();

    @Before public void setUp() throws Exception {
        app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(app);
        hadIntro = preferences.contains(AppSettingsConstants.KEY_PREF_INTRO);
        oldIntro = preferences.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        assertTrue(preferences.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true).commit());
        scenario = ActivityScenario.launch(MainActivity.class);
        long deadline = android.os.SystemClock.uptimeMillis() + 30000;
        AtomicBoolean ready = new AtomicBoolean();
        while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap();
                ready.set(activity.getMapFragment() != null
                        && activity.getMapFragment().getEditLayerOverlay() != null
                        && map.getMaplibreMap() != null && map.getMaplibreMap().getStyle() != null);
            });
            if (!ready.get()) Thread.sleep(100);
        }
        assertTrue("MapLibre did not initialize", ready.get());
    }

    @After public void tearDown() {
        if (scenario != null) {
            scenario.onActivity(activity -> {
                if (activity.getMapFragment() != null)
                    activity.getMapFragment().setNewMode(MapFragment.MODE_NORMAL);
                for (VectorLayerUI layer : layers) assertTrue(layer.delete(false));
                app.getMap().save();
            });
            scenario.close();
        }
        if (preferences != null) {
            SharedPreferences.Editor editor = preferences.edit();
            if (hadIntro) editor.putBoolean(AppSettingsConstants.KEY_PREF_INTRO, oldIntro);
            else editor.remove(AppSettingsConstants.KEY_PREF_INTRO);
            assertTrue(editor.commit());
        }
    }

    private void addLayer(double x, boolean editable) {
        scenario.onActivity(activity -> {
            try {
                MapDrawable map = (MapDrawable) app.getMap();
                VectorLayerUI layer = new VectorLayerUI(app, new File(map.getPath(),
                        "reliability_ui_" + UUID.randomUUID().toString().replace("-", "")));
                layer.setName("Reliability test");
                map.addLayer(layer);
                layers.add(layer);
                layer.create(GeoConstants.GTPoint, Collections.emptyList());
                layer.setIsEditable(editable);
                layer.setVisible(true);
                ContentValues values = new ContentValues();
                GeoPoint point = new GeoPoint(x, 0);
                point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                values.put(Constants.FIELD_GEOM, point.toBlob());
                ids.add(layer.insertAddChanges(values));
            } catch (Exception error) { throw new AssertionError(error); }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void select(MainActivity activity, int index) {
        VectorLayerUI layer = layers.get(index);
        long id = ids.get(index);
        activity.getMapFragment().showViewModeForFeature(layer, layer.getFeature(id), layer,
                layer.getGeometryForId(id), id, id, false);
    }

    private int mode(MainActivity activity) {
        return ((MaplibreMapInteraction) activity.getMapFragment()).getMode();
    }

    @Test public void selectionImmediatelyEnablesToolsAndBackClearsIt() {
        addLayer(0, true);
        scenario.onActivity(activity -> {
            select(activity, 0);
            assertEquals(MapFragment.MODE_SELECT_ACTION, mode(activity));
            assertTrue(activity.getBottomToolbar().getMenu().findItem(R.id.menu_feature_edit).isEnabled());
            assertTrue(activity.getBottomToolbar().getMenu().findItem(R.id.menu_feature_edit_attributes).isEnabled());
            assertTrue(activity.getBottomToolbar().getMenu().findItem(R.id.menu_feature_delete).isEnabled());
            assertTrue(layers.get(0).isLocked());
            assertNull(((MapDrawable) app.getMap()).editingObject);
            activity.getOnBackPressedDispatcher().onBackPressed();
            assertEquals(MapFragment.MODE_NORMAL, mode(activity));
            assertFalse(layers.get(0).isLocked());
            assertEquals(Constants.NOT_FOUND,
                    activity.getMapFragment().getEditLayerOverlay().getSelectedFeatureId());
            assertFalse(activity.isFinishing());
        });
    }

    @Test public void anotherLayerCanBeSelectedAndReadOnlyLayerHasNoEditingActions() {
        addLayer(0, true);
        addLayer(100, true);
        addLayer(-100, false);
        scenario.onActivity(activity -> {
            MapDrawable map = (MapDrawable) app.getMap();
            map.getMaplibreMap().moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0, 0), 16));
            select(activity, 0);
            GeoPoint point = new GeoPoint(100, 0);
            point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            point.project(GeoConstants.CRS_WGS84);
            PointF screen = map.getMaplibreMap().getProjection()
                    .toScreenLocation(new LatLng(point.getY(), point.getX()));
            activity.getMapFragment().onSingleTapUpFromMaplibre(screen.x, screen.y);
            assertSame(layers.get(1), activity.getMapFragment().getSelectedLayer());
            assertFalse(layers.get(0).isLocked());
            assertTrue(layers.get(1).isLocked());
            select(activity, 2);
            assertEquals(MapFragment.MODE_SELECT_FOR_VIEW, mode(activity));
            assertNull(activity.getBottomToolbar().getMenu().findItem(R.id.menu_feature_edit));
            assertNull(activity.getBottomToolbar().getMenu().findItem(R.id.menu_feature_edit_attributes));
            assertNull(activity.getBottomToolbar().getMenu().findItem(R.id.menu_feature_delete));
            activity.getMapFragment().onSingleTapUpFromMaplibre(-10000, -10000);
            assertEquals(MapFragment.MODE_NORMAL, mode(activity));
            assertNull(activity.getMapFragment().getSelectedLayer());
        });
    }

    @Test public void narrowRailKeepsTargetsInsideBoundsAndOffersOverflow() {
        scenario.onActivity(activity -> {
            MapControlRail rail = new MapControlRail(activity, null);
            int cell = Math.round(48 * activity.getResources().getDisplayMetrics().density);
            for (int i = 0; i < 5; i++) {
                View control = new View(activity);
                control.setMinimumWidth(cell);
                control.setMinimumHeight(cell);
                control.setContentDescription("Tool " + i);
                rail.addView(control);
            }
            int width = cell, height = cell * 3;
            rail.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            rail.layout(0, 0, width, height);
            int displayed = 0;
            for (int i = 0; i < rail.getChildCount(); i++) {
                View control = rail.getChildAt(i);
                if (control.getWidth() == 0) continue;
                displayed++;
                assertTrue(control.getLeft() >= 0 && control.getRight() <= width);
                assertTrue(control.getTop() >= 0 && control.getBottom() <= height);
                assertTrue(control.getWidth() >= cell && control.getHeight() >= cell);
            }
            assertEquals(2, displayed);
            assertEquals(View.VISIBLE, rail.findViewWithTag("map_control_overflow").getVisibility());
        });
    }

    private void gesture(MapDrawable map, float x, float y, float dx, int duration, boolean cancel) {
        org.maplibre.android.maps.MapView view = map.getMaplibreMapView();
        long now = android.os.SystemClock.uptimeMillis();
        int[] actions = { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
                cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP };
        for (int i = 0; i < actions.length; i++) {
            MotionEvent event = MotionEvent.obtain(now, now + (i == 0 ? 0 : duration * i / 2),
                    actions[i], x + (i == 0 ? 0 : dx), y, 0);
            try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
        }
    }

    @Test public void rulerAcceptsFingerDriftAndKeepsPanCancelAndLongPressSeparate() {
        scenario.onActivity(activity -> {
            MapDrawable map = (MapDrawable) app.getMap();
            activity.findViewById(R.id.action_ruler).performClick();
            assertTrue(activity.getMapFragment().isRulerMeasuring());
            int before = map.getMeasurementGeometry().getPointCount();
            float density = activity.getResources().getDisplayMetrics().density;
            float x = map.getMaplibreMapView().getWidth() * .25f;
            float y = map.getMaplibreMapView().getHeight() * .3f;
            LatLng center = map.getMaplibreMap().getCameraPosition().target;
            gesture(map, x, y, 8 * density, 100, false);
            assertEquals(before + 1, map.getMeasurementGeometry().getPointCount());
            assertEquals(center, map.getMaplibreMap().getCameraPosition().target);
            gesture(map, x * 2.5f, y, 60 * density, 100, false);
            assertEquals(before + 1, map.getMeasurementGeometry().getPointCount());
            gesture(map, x * 2, y * 2, 8 * density, 100, true);
            assertEquals(before + 1, map.getMeasurementGeometry().getPointCount());
            gesture(map, x * 2, y * 2, 0, android.view.ViewConfiguration.getLongPressTimeout() + 20, false);
            assertEquals(before + 1, map.getMeasurementGeometry().getPointCount());
            activity.findViewById(R.id.add_point_by_tap).performClick();
            assertFalse(activity.getMapFragment().isRulerMeasuring());
        });
    }

    @Test public void azimuthAcceptsFingerDriftWithoutInterpretingADragAsAnotherPoint() {
        scenario.onActivity(activity -> {
            try {
                MapDrawable map = (MapDrawable) app.getMap();
                MapFragment fragment = activity.getMapFragment();
                fragment.setNewMode(MapFragment.MODE_AZIMUTH_POINTS);
                float density = activity.getResources().getDisplayMetrics().density;
                float x = map.getMaplibreMapView().getWidth() * .25f;
                float y = map.getMaplibreMapView().getHeight() * .3f;
                java.lang.reflect.Field start = MapFragment.class.getDeclaredField("azimuthStartPoint");
                java.lang.reflect.Field target = MapFragment.class.getDeclaredField("azimuthTargetPoint");
                start.setAccessible(true); target.setAccessible(true);
                gesture(map, x, y, 8 * density, 100, false);
                assertNotNull(start.get(fragment));
                assertNull(target.get(fragment));
                gesture(map, x * 2.5f, y, 8 * density, 100, false);
                assertNotNull(target.get(fragment));
                Object oldStart = start.get(fragment), oldTarget = target.get(fragment);
                gesture(map, x * 2, y * 2, 60 * density, 100, false);
                assertSame(oldStart, start.get(fragment));
                assertSame(oldTarget, target.get(fragment));
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }
}
