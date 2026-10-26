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

/** Real activity, menus, map projection and SQLite in the isolated verification application. */
@RunWith(AndroidJUnit4.class)
public class MapEditingToolsTest {
    @org.junit.Rule public final org.junit.rules.TestName methodName = new org.junit.rules.TestName();
    private ActivityScenario<MainActivity> scenario;
    private GISApplication app;
    private SharedPreferences preferences;
    private boolean hadIntro, oldIntro;
    private boolean hadBatteryWarning, oldBatteryWarning;
    private static final String BATTERY_WARNING = "battery_dont_show_pref";
    private boolean hadGeoWarning, oldGeoWarning;
    private static final String GEO_WARNING = com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG;
    private final List<VectorLayerUI> layers = new ArrayList<>();
    private final List<Long> ids = new ArrayList<>();

    @Before public void setUp() throws Exception {
        app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getApplicationContext();
        if (!com.nextgis.mobile.util.VerificationEnvironment.isEmulator()) {
            com.nextgis.mobile.util.VerificationEnvironment.requireIsolated(app);
            assertTrue("Only stationary object editing is allowed on the opt-in verification phone",
                    methodName.getMethodName().equals("copyReadOnlyFeatureChoosesCompatibleLayerAndCategoryAndSavesMatchingFields")
                    || methodName.getMethodName().equals("wholeObjectGesturesKeepCameraFixedAndUndoEachDragOrRotationOnce"));
        }
        // Remove only uniquely named fixtures from an interrupted earlier run of this suite.
        List<com.nextgis.maplib.api.ILayer> interrupted = new ArrayList<>();
        com.nextgis.maplib.map.LayerGroup.getVectorLayersByType((MapDrawable) app.getMap(), GeoConstants.GTAnyCheck, interrupted);
        for (com.nextgis.maplib.api.ILayer candidate : interrupted) {
            if (!(candidate instanceof VectorLayerUI)) continue;
            VectorLayerUI layer = (VectorLayerUI) candidate;
            if (!layer.getPath().getName().matches("(creation_type_|walk_ui_)[0-9a-f]{32}")) continue;
            com.nextgis.maplibui.util.WalkSessionStore.Snapshot walk = com.nextgis.maplibui.util.WalkSessionStore.load(app);
            if (walk != null && walk.layerId == layer.getId()) {
                if (walk.isPointActive()) assertTrue(com.nextgis.maplibui.util.WalkSessionStore.endPoint(app, walk.pointId));
                assertTrue(com.nextgis.maplibui.service.WalkEditService.requestCommand(app, walk.id,
                        com.nextgis.maplibui.util.WalkSessionPolicy.Command.DISCARD));
            }
            assertTrue(layer.delete(false));
        }
        app.getMap().save();
        preferences = PreferenceManager.getDefaultSharedPreferences(app);
        hadIntro = preferences.contains(AppSettingsConstants.KEY_PREF_INTRO);
        oldIntro = preferences.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        hadBatteryWarning = preferences.contains(BATTERY_WARNING);
        oldBatteryWarning = preferences.getBoolean(BATTERY_WARNING, false);
        hadGeoWarning = preferences.contains(GEO_WARNING);
        oldGeoWarning = preferences.getBoolean(GEO_WARNING, true);
        // The warning has its own UI suite. Exercise the map with no snackbar covering its actions.
        assertTrue(preferences.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                .putBoolean(BATTERY_WARNING, true).putBoolean(GEO_WARNING, false).commit());
        scenario = ActivityScenario.launch(MainActivity.class);
        java.util.concurrent.atomic.AtomicReference<org.maplibre.android.maps.MapLibreMap> currentViewMap =
                new java.util.concurrent.atomic.AtomicReference<>();
        scenario.onActivity(activity -> {
            org.maplibre.android.maps.MapView view = activity.findViewById(R.id.mapViewMaplibre);
            assertNotNull(view);
            view.getMapAsync(currentViewMap::set);
        });
        long deadline = android.os.SystemClock.uptimeMillis() + 30000;
        AtomicBoolean ready = new AtomicBoolean();
        while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                MapDrawable map = (MapDrawable) app.getMap();
                ready.set(activity.getMapFragment() != null
                        && activity.getMapFragment().getEditLayerOverlay() != null
                        // The shared map can still reference the preceding destroyed Activity.
                        && map.getMaplibreMapView() == activity.findViewById(R.id.mapViewMaplibre)
                        && map.getMaplibreMapView() != null && map.getMaplibreMapView().isAttachedToWindow()
                        && currentViewMap.get() != null && map.getMaplibreMap() == currentViewMap.get()
                        && map.getMaplibreMap().getStyle() != null);
            });
            if (!ready.get()) Thread.sleep(100);
        }
        assertTrue("MapLibre did not initialize", ready.get());
        deadline = android.os.SystemClock.uptimeMillis() + 30000;
        while ((com.nextgis.maplibui.util.SharedUnderlayProjects.isMigrationPending()
                || com.nextgis.maplibui.util.ProjectOperationCoordinator.isBusy())
                && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
        assertFalse("Startup migration still owns project operations",
                com.nextgis.maplibui.util.SharedUnderlayProjects.isMigrationPending()
                        || com.nextgis.maplibui.util.ProjectOperationCoordinator.isBusy());
        // Let the normal launch warning finish showing, then dismiss it through Activity lifecycle.
        // Its text/actions are covered separately by LargeTextLayoutTest; no device setting changes.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); Thread.sleep(500);
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); Thread.sleep(300);
    }

    @After public void tearDown() {
        if (scenario != null) {
            scenario.onActivity(activity -> {
                com.nextgis.maplibui.util.WalkSessionStore.Snapshot walk = com.nextgis.maplibui.util.WalkSessionStore.load(app);
                if (walk != null && layers.stream().anyMatch(layer -> layer.getId() == walk.layerId)) {
                    if (walk.isPointActive()) assertTrue(com.nextgis.maplibui.util.WalkSessionStore.endPoint(app, walk.pointId));
                    assertTrue(com.nextgis.maplibui.service.WalkEditService.requestCommand(app, walk.id,
                            com.nextgis.maplibui.util.WalkSessionPolicy.Command.DISCARD));
                    com.nextgis.maplibui.util.FeatureFormDraftStore.clear(app);
                    com.nextgis.maplibui.util.GeometryEditDraftStore.clear(app, "walk-test-complete");
                }
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
            if (hadBatteryWarning) editor.putBoolean(BATTERY_WARNING, oldBatteryWarning);
            else editor.remove(BATTERY_WARNING);
            if (hadGeoWarning) editor.putBoolean(GEO_WARNING, oldGeoWarning);
            else editor.remove(GEO_WARNING);
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

    private void clickWalkButton(int id) {
        scenario.onActivity(activity -> {
            com.nextgis.maplibui.view.WalkRecordingPanel panel = activity.findViewById(R.id.walk_recording_panel);
            panel.refresh();
            View button = panel.findViewById(id);
            assertNotNull(button); assertTrue(button.isEnabled()); assertEquals(1f, button.getAlpha(), 0);
            assertTrue(button.performClick());
        });
    }

    private void clickDialogButton(String id) {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        int buttonId = id.endsWith("button2") ? android.R.id.button2 : android.R.id.button1;
        AtomicBoolean clicked = new AtomicBoolean();
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (!clicked.get() && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                List<View> roots = android.view.inspector.WindowInspector.getGlobalWindowViews();
                for (int i = roots.size() - 1; i >= 0; i--) {
                    View root = roots.get(i);
                    View button = root.findViewById(buttonId);
                    if (button != null && button.isShown() && root.isAttachedToWindow()) {
                        clicked.set(button.performClick());
                        break;
                    }
                }
            });
            if (!clicked.get()) android.os.SystemClock.sleep(50);
        }
        assertTrue("Confirmation button unavailable: " + id, clicked.get());
        instrumentation.waitForIdleSync();
    }

    private void beginWalk(MainActivity activity, boolean enoughPoints, long featureId) {
        try {
            MapDrawable map = (MapDrawable) app.getMap();
            VectorLayerUI layer = new VectorLayerUI(app, new File(map.getPath(),
                    "walk_ui_" + UUID.randomUUID().toString().replace("-", "")));
            layer.setName("Walk test");
            map.addLayer(layer); layers.add(layer);
            layer.create(GeoConstants.GTLineString, Collections.emptyList());
            layer.setIsEditable(true); layer.setVisible(true); map.save();
            com.nextgis.maplib.datasource.GeoLineString line = new com.nextgis.maplib.datasource.GeoLineString();
            line.setCRS(GeoConstants.CRS_WEB_MERCATOR); line.add(new GeoPoint(0, 0));
            if (enoughPoints) line.add(new GeoPoint(10, 10));
            if (featureId != Constants.NOT_FOUND) {
                ContentValues values = new ContentValues(); values.put(Constants.FIELD_GEOM, line.toBlob());
                featureId = layer.insertAddChanges(values);
                assertTrue(featureId != Constants.NOT_FOUND);
                line.add(new GeoPoint(20, 10));
            }
            assertNotNull(com.nextgis.maplibui.util.WalkSessionStore.begin(app, layer.getId(), featureId,
                    line, 0, 0, line.getPointCount(), MainActivity.class.getName()));
            ((com.nextgis.maplibui.view.WalkRecordingPanel) activity.findViewById(R.id.walk_recording_panel)).refresh();
        } catch (Exception error) { throw new AssertionError(error); }
    }

    @Test public void finishingAnInsufficientWalkCreatesNoObjectOrForm() {
        scenario.onActivity(activity -> {
            beginWalk(activity, false, Constants.NOT_FOUND);
            View panel = activity.findViewById(R.id.walk_recording_panel);
            assertTrue(panel.findViewById(com.nextgis.maplibui.R.id.walk_panel_finish).performClick());
        });
        clickDialogButton("android:id/button1");
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            assertNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
            assertNull(com.nextgis.maplibui.util.FeatureFormDraftStore.load(app));
            assertNull(com.nextgis.maplibui.util.GeometryEditDraftStore.load(app));
            assertEquals(0, layers.get(0).getCount());
            assertEquals(MapFragment.MODE_NORMAL, mode(activity));
            assertEquals(View.GONE, activity.findViewById(R.id.walk_recording_panel).getVisibility());
        });
    }

    @Test public void finishedWalkSaveOpensAttributesForNewAndExistingObjects() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        for (long featureId : new long[]{Constants.NOT_FOUND, 1}) {
            android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                    com.nextgis.maplibui.activity.ModifyAttributesActivity.class.getName(), null, false);
            com.nextgis.maplibui.activity.ModifyAttributesActivity opened = null;
            try {
                scenario.onActivity(activity -> {
                    beginWalk(activity, true, featureId);
                    View panel = activity.findViewById(R.id.walk_recording_panel);
                    assertTrue(panel.findViewById(com.nextgis.maplibui.R.id.walk_panel_finish).performClick());
                });
                // No recording mutation or row before confirmation; cancelling keeps this owner.
                String walkId = com.nextgis.maplibui.util.WalkSessionStore.load(app).id;
                clickDialogButton("android:id/button2");
                assertEquals(walkId, com.nextgis.maplibui.util.WalkSessionStore.load(app).id);
                assertEquals(com.nextgis.maplibui.util.WalkSessionPolicy.Phase.RECORDING,
                        com.nextgis.maplibui.util.WalkSessionStore.load(app).phase);
                assertNull(com.nextgis.maplibui.util.FeatureFormDraftStore.load(app));
                clickWalkButton(com.nextgis.maplibui.R.id.walk_panel_finish);
                clickDialogButton("android:id/button1");
                opened = (com.nextgis.maplibui.activity.ModifyAttributesActivity) instrumentation.waitForMonitorWithTimeout(monitor, 20000);
                assertNotNull("Walk attributes did not open", opened);
                instrumentation.waitForIdleSync();
                com.nextgis.maplibui.util.FeatureFormDraftStore.Snapshot draft = com.nextgis.maplibui.util.FeatureFormDraftStore.load(app);
                assertNotNull(draft); assertNotNull(draft.walkSessionId); assertTrue(draft.geometryChanged);
                assertEquals(featureId == Constants.NOT_FOUND ? 0 : 1, layers.get(layers.size() - 1).getCount());
                com.nextgis.maplibui.activity.ModifyAttributesActivity form = opened;
                instrumentation.runOnMainSync(() -> {
                    android.widget.PopupMenu popup = new android.widget.PopupMenu(form, new View(form));
                    assertTrue(form.onOptionsItemSelected(popup.getMenu().add(0, com.nextgis.maplibui.R.id.menu_apply, 0, "Save")));
                });
                AtomicBoolean saved = new AtomicBoolean();
                long deadline = android.os.SystemClock.uptimeMillis() + 20000;
                while (!saved.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync();
                    scenario.onActivity(activity -> saved.set(mode(activity) == MapFragment.MODE_NORMAL
                            && com.nextgis.maplibui.util.WalkSessionStore.load(app) == null));
                    if (!saved.get()) Thread.sleep(40);
                }
                assertTrue("Attribute Save did not finish the walking session", saved.get());
                VectorLayerUI layer = layers.get(layers.size() - 1);
                assertEquals(1, layer.getCount());
                try (android.database.Cursor rows = layer.query(null, null, null, null, null)) {
                    assertTrue(rows.moveToFirst());
                    long savedId = rows.getLong(rows.getColumnIndexOrThrow(Constants.FIELD_ID));
                    assertEquals(featureId == Constants.NOT_FOUND ? 2 : 3,
                            ((com.nextgis.maplib.datasource.GeoLineString) layer.getGeometryForId(savedId)).getPointCount());
                }
            } finally {
                if (opened != null && !opened.isFinishing()) {
                    com.nextgis.maplibui.activity.ModifyAttributesActivity form = opened;
                    instrumentation.runOnMainSync(form::finish);
                }
                instrumentation.removeMonitor(monitor);
                com.nextgis.maplibui.util.FeatureFormDraftStore.clear(app);
            }
        }
    }

    @Test public void iconStateDoesNotLeakBetweenButtonsOrToolbarRecreation() {
        scenario.onActivity(activity -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(activity, new View(activity));
            android.view.MenuItem first = popup.getMenu().add("First").setIcon(com.nextgis.maplibui.R.drawable.ic_action_save);
            android.view.MenuItem second = popup.getMenu().add("Second").setIcon(com.nextgis.maplibui.R.drawable.ic_action_save);
            com.nextgis.maplibui.util.ControlHelper.setEnabled(first, false);
            com.nextgis.maplibui.util.ControlHelper.setEnabled(second, true);
            assertEquals(160, first.getIcon().getAlpha()); assertEquals(255, second.getIcon().getAlpha());
            com.nextgis.maplibui.util.ControlHelper.setEnabled(first, true);
            com.nextgis.maplibui.util.ControlHelper.setEnabled(second, false);
            assertEquals(255, first.getIcon().getAlpha()); assertEquals(160, second.getIcon().getAlpha());
            com.nextgis.maplibui.util.ControlHelper.setEnabled(popup.getMenu().add("No icon"), false);
        });
    }

    private int visibleBottom(View view) {
        android.graphics.Rect visible = new android.graphics.Rect();
        assertTrue("The action panel is visible", view.getGlobalVisibleRect(visible));
        return visible.bottom;
    }

    @Test public void bottomWalkPanelMovesForCreationMenuAndCancelRequiresConfirmation() {
        final int[] bottom = new int[1];
        scenario.onActivity(activity -> beginWalk(activity, true, Constants.NOT_FOUND));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            View panel = activity.findViewById(R.id.walk_recording_panel);
            bottom[0] = visibleBottom(panel);
            int[] rowPosition = new int[2];
            activity.findViewById(R.id.map_action_row).getLocationOnScreen(rowPosition);
            assertTrue(bottom[0] > rowPosition[1]);
            ((com.getbase.floatingactionbutton.FloatingActionsMenu) activity.findViewById(R.id.multiple_actions)).expand();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            assertTrue(visibleBottom(activity.findViewById(R.id.walk_recording_panel)) < bottom[0]);
            ((com.getbase.floatingactionbutton.FloatingActionsMenu) activity.findViewById(R.id.multiple_actions)).collapse();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            View panel = activity.findViewById(R.id.walk_recording_panel);
            assertEquals(bottom[0], visibleBottom(panel));
            assertTrue(panel.findViewById(com.nextgis.maplibui.R.id.walk_panel_cancel).performClick());
            assertNotNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        });
        clickDialogButton("android:id/button2");
        assertNotNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        scenario.onActivity(activity -> {
            View panel = activity.findViewById(R.id.walk_recording_panel);
            panel.findViewById(com.nextgis.maplibui.R.id.walk_panel_cancel).performClick();
        });
        clickDialogButton("android:id/button1");
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (com.nextgis.maplibui.util.WalkSessionStore.load(app) != null
                && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50);
        assertNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        assertEquals(0, layers.get(0).getCount());
    }

    private void awaitWalkPaused(String id, boolean paused) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            com.nextgis.maplibui.util.WalkSessionStore.Snapshot session = com.nextgis.maplibui.util.WalkSessionStore.load(app);
            if (session != null && id.equals(session.id) && session.gpsPaused == paused
                    && com.nextgis.maplibui.service.WalkEditService.isSessionRunning(id)) return;
            Thread.sleep(40);
        }
        fail("Recorder did not reach paused=" + paused);
    }

    @Test public void pauseAndResumeRequireConfirmationAndRetainGeometry() throws Exception {
        scenario.onActivity(activity -> beginWalk(activity, true, Constants.NOT_FOUND));
        supplyCurrentFix();
        String id = com.nextgis.maplibui.util.WalkSessionStore.load(app).id;
        scenario.onActivity(activity -> assertTrue(com.nextgis.maplibui.service.WalkEditService.requestCommand(activity, id,
                com.nextgis.maplibui.util.WalkSessionPolicy.Command.RESUME)));
        awaitWalkPaused(id, false);
        clickWalkButton(com.nextgis.maplibui.R.id.walk_panel_resume);
        clickDialogButton("android:id/button2");
        assertFalse(com.nextgis.maplibui.util.WalkSessionStore.load(app).gpsPaused);
        clickWalkButton(com.nextgis.maplibui.R.id.walk_panel_resume);
        clickDialogButton("android:id/button1");
        awaitWalkPaused(id, true);
        String pausedGeometry = com.nextgis.maplibui.util.WalkSessionStore.load(app).geometry().toWKT(true);
        scenario.onActivity(activity -> {
            try {
                java.lang.reflect.Field active = com.nextgis.maplibui.service.WalkEditService.class.getDeclaredField("sActive");
                active.setAccessible(true);
                com.nextgis.maplibui.service.WalkEditService service = (com.nextgis.maplibui.service.WalkEditService) active.get(null);
                assertNotNull(service);
                android.location.Location fix = new android.location.Location(android.location.LocationManager.GPS_PROVIDER);
                fix.setLatitude(55.01); fix.setLongitude(37.01); fix.setAccuracy(1);
                fix.setTime(System.currentTimeMillis()); fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
                service.onRecordingLocation(fix); service.onRecordingFlushComplete();
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        assertEquals(pausedGeometry, com.nextgis.maplibui.util.WalkSessionStore.load(app).geometry().toWKT(true));
        assertEquals(0, layers.get(0).getCount());
        clickWalkButton(com.nextgis.maplibui.R.id.walk_panel_resume);
        clickDialogButton("android:id/button2");
        assertTrue(com.nextgis.maplibui.util.WalkSessionStore.load(app).gpsPaused);
        // A foreground point acquired while confirmation is open still blocks the service command.
        clickWalkButton(com.nextgis.maplibui.R.id.walk_panel_resume);
        String pointId = com.nextgis.maplibui.util.WalkSessionStore.beginPoint(app, R.id.add_current_location);
        assertNotNull(pointId);
        clickDialogButton("android:id/button1");
        assertTrue(com.nextgis.maplibui.util.WalkSessionStore.load(app).gpsPaused);
        assertTrue(com.nextgis.maplibui.util.WalkSessionStore.endPoint(app, pointId));
        supplyCurrentFix();
        clickWalkButton(com.nextgis.maplibui.R.id.walk_panel_resume);
        clickDialogButton("android:id/button1");
        awaitWalkPaused(id, false);
        assertEquals(id, com.nextgis.maplibui.util.WalkSessionStore.load(app).id);
        clickWalkButton(com.nextgis.maplibui.R.id.walk_panel_cancel);
        clickDialogButton("android:id/button1");
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        while ((com.nextgis.maplibui.util.WalkSessionStore.load(app) != null
                || com.nextgis.maplibui.service.WalkEditService.isSessionRunning(id))
                && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(40);
        assertNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        assertFalse(com.nextgis.maplibui.service.WalkEditService.isSessionRunning(id));
    }

    @Test public void compactWalkCardKeepsThreeAccessibleTargetsInBothThemes() {
        scenario.onActivity(activity -> {
            beginWalk(activity, true, Constants.NOT_FOUND);
            layers.get(0).setName("Наблюдения вдоль маршрута обследования");
            for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1, 2}) {
                android.content.res.Configuration config = new android.content.res.Configuration(activity.getResources().getConfiguration());
                config.setLocale(new java.util.Locale("ru")); config.fontScale = scale;
                android.content.Context themed = new androidx.appcompat.view.ContextThemeWrapper(
                        activity.createConfigurationContext(config), dark ? R.style.AppTheme_Dark : R.style.AppTheme);
                com.nextgis.maplibui.view.WalkRecordingPanel panel = new com.nextgis.maplibui.view.WalkRecordingPanel(themed);
                panel.setListener(new com.nextgis.maplibui.view.WalkRecordingPanel.Listener() {
                    @Override public void onSessionChanged(com.nextgis.maplibui.util.WalkSessionStore.Snapshot session) { }
                    @Override public void onFinishWalk(com.nextgis.maplibui.util.WalkSessionStore.Snapshot session) { }
                    @Override public void onPauseOrResumeWalk(com.nextgis.maplibui.util.WalkSessionStore.Snapshot session, boolean resume) { }
                    @Override public void onDiscardWalk(com.nextgis.maplibui.util.WalkSessionStore.Snapshot session) { }
                });
                float density = themed.getResources().getDisplayMetrics().density;
                int width = Math.round((scale == 1 ? 360 : 280) * density), target = Math.round(48 * density);
                panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                panel.layout(0, 0, width, panel.getMeasuredHeight());
                assertTrue("Wrapped title retains all three action targets", panel.getHeight() >= target);
                for (int buttonId : new int[]{com.nextgis.maplibui.R.id.walk_panel_finish,
                        com.nextgis.maplibui.R.id.walk_panel_resume, com.nextgis.maplibui.R.id.walk_panel_cancel}) {
                    View button = panel.findViewById(buttonId);
                    assertEquals(target, button.getWidth()); assertEquals(target, button.getHeight());
                    assertTrue(button.getPaddingLeft() >= Math.round(14 * density));
                    assertTrue(button.isEnabled()); assertEquals(1f, button.getAlpha(), 0);
                    assertNotNull(button.getContentDescription());
                    android.graphics.Rect bounds = new android.graphics.Rect(0, 0, button.getWidth(), button.getHeight());
                    panel.offsetDescendantRectToMyCoords(button, bounds);
                    assertTrue(bounds.left >= 0 && bounds.right <= width && bounds.bottom <= panel.getHeight());
                }
                android.widget.TextView title = panel.findViewById(com.nextgis.maplibui.R.id.walk_panel_title);
                assertEquals(layers.get(0).getName(), title.getText().toString());
                assertTrue(title.getWidth() > 0);
                assertTrue("The complete layer name fits vertically", title.getLayout().getHeight()
                        <= title.getHeight() - title.getCompoundPaddingTop() - title.getCompoundPaddingBottom());
                for (int line = 0; line < title.getLayout().getLineCount(); line++)
                    assertEquals("The layer name is not truncated", 0, title.getLayout().getEllipsisCount(line));
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, panel.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                panel.draw(new android.graphics.Canvas(bitmap));
                try (java.io.FileOutputStream image = new java.io.FileOutputStream(new File(app.getFilesDir(),
                        "walk-card-" + (dark ? "dark" : "light") + "-" + (int) scale + ".png"))) {
                    assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, image));
                } catch (java.io.IOException error) { throw new AssertionError(error); }
                finally { bitmap.recycle(); }
            }
        });
    }

    @Test public void layerChoiceOffersCategoryBeforeSketchAndHandsDefaultsToRealForm() throws Exception {
        scenario.onActivity(activity -> {
            try {
                MapDrawable map=(MapDrawable)app.getMap();
                VectorLayerUI layer=new VectorLayerUI(app,new File(map.getPath(),"type_"+UUID.randomUUID().toString().replace("-","")));
                layer.setName("Object type test");map.addLayer(layer);layers.add(layer);
                layer.create(GeoConstants.GTPoint,java.util.Arrays.asList(
                        new com.nextgis.maplib.datasource.Field(GeoConstants.FTString,"classobj","Class"),
                        new com.nextgis.maplib.datasource.Field(GeoConstants.FTString,"typeobj","Type")));
                layer.setIsEditable(true);layer.setVisible(false);
                org.json.JSONObject parent=new org.json.JSONObject().put("name","field").put("alias","Field points")
                        .put("values",new org.json.JSONArray().put(new org.json.JSONObject().put("name","tree").put("alias","Tree")));
                org.json.JSONArray form=new org.json.JSONArray().put(new org.json.JSONObject().put("type","double_combobox")
                        .put("attributes",new org.json.JSONObject().put("field_level1","classobj").put("field_level2","typeobj")
                                .put("last",true).put("values",new org.json.JSONArray().put(parent))));
                java.nio.file.Files.write(new File(layer.getPath(),"form.json").toPath(),form.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                java.nio.file.Files.write(new File(layer.getPath(),"ngfp_meta.json").toPath(),"{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                com.nextgis.maplib.display.SimpleMarkerStyle symbol=new com.nextgis.maplib.display.SimpleMarkerStyle();
                symbol.setColor(android.graphics.Color.RED);
                com.nextgis.maplib.display.FieldStyleRule rule=new com.nextgis.maplib.display.FieldStyleRule(layer);
                rule.setKey("typeobj");rule.setStyle("tree",symbol);
                layer.setRenderer(new com.nextgis.maplib.display.RuleFeatureRenderer(layer,rule,symbol));map.save();
            } catch(Exception error) { throw new AssertionError(error); }
        });
        AtomicBoolean sourceReady=new AtomicBoolean();
        long sourceDeadline=android.os.SystemClock.uptimeMillis()+30000;
        while(!sourceReady.get() && android.os.SystemClock.uptimeMillis()<sourceDeadline){
            scenario.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                sourceReady.set(map.getMaplibreMap().getStyle().getSource(layers.get(0).getPath().toString())
                        instanceof org.maplibre.android.style.sources.GeoJsonSource);});
            if(!sourceReady.get())Thread.sleep(40);
        }
        assertTrue("A hot-added layer must acquire its current-style source",sourceReady.get());
        scenario.onActivity(activity->{
            activity.getMapFragment().onFinishChooseLayerDialog(MapFragment.EDIT_LAYER,layers.get(0),false,false);
            assertEquals(MapFragment.MODE_NORMAL,mode(activity));
        });
        AtomicBoolean ready=new AtomicBoolean();
        long deadline=android.os.SystemClock.uptimeMillis()+20000;
        while(!ready.get() && android.os.SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity(activity -> {
                androidx.fragment.app.DialogFragment dialog=(androidx.fragment.app.DialogFragment)activity.getMapFragment()
                        .getChildFragmentManager().findFragmentByTag(com.nextgis.maplibui.dialog.ChooseFeatureTypeDialog.TAG);
                if(dialog==null || dialog.getDialog()==null) return;
                android.widget.ListView list=((androidx.appcompat.app.AlertDialog)dialog.getDialog()).getListView();
                ready.set(list!=null && list.getCount()==1 && list.getChildCount()==1
                        && list.getItemAtPosition(0) instanceof com.nextgis.maplibui.util.FeatureTypeDefaults.Choice);
            });
            if(!ready.get())Thread.sleep(40);
        }
        assertTrue("Category did not load",ready.get());
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(
                com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity.class.getName(),null,false);
        com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity opened=null;
        try {
            scenario.onActivity(activity -> {
                androidx.fragment.app.DialogFragment dialog=(androidx.fragment.app.DialogFragment)activity.getMapFragment()
                        .getChildFragmentManager().findFragmentByTag(com.nextgis.maplibui.dialog.ChooseFeatureTypeDialog.TAG);
                android.widget.ListView list=((androidx.appcompat.app.AlertDialog)dialog.requireDialog()).getListView();
                android.widget.ImageView symbol=list.getChildAt(0).findViewById(com.nextgis.maplibui.R.id.ivIcon);
                assertNotNull(symbol.getDrawable());
                list.performItemClick(list.getChildAt(0),0,0);
                assertEquals(MapFragment.MODE_EDIT,mode(activity));
                assertNotNull(activity.getMapFragment().getEditLayerOverlay().getSelectedFeatureGeometry());
                com.nextgis.maplibui.util.GeometryEditDraftStore.Snapshot draft=
                        com.nextgis.maplibui.util.GeometryEditDraftStore.load(app);
                assertNotNull(draft);
                android.os.Bundle defaults=com.nextgis.maplibui.util.FeatureTypeDefaults.decode(draft.initialValues);
                assertEquals("field",defaults.getString(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("classobj")));
                assertEquals("tree",defaults.getString(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("typeobj")));
                assertTrue(layers.get(0).isVisible());
                assertTrue(activity.getMapFragment().saveEdits());
            });
            opened=(com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity)instrumentation.waitForMonitorWithTimeout(monitor,20000);
            assertNotNull("Attribute form did not open",opened);
            com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity form=opened;
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                com.nextgis.maplibui.util.FeatureFormDraftStore.Snapshot draft=com.nextgis.maplibui.util.FeatureFormDraftStore.load(app);
                assertNotNull(draft);
                android.os.Bundle state=com.nextgis.maplibui.util.FeatureFormDraftStore.controlStateToBundle(draft);
                assertEquals("tree",state.getString(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("typeobj")));
                android.widget.PopupMenu menu=new android.widget.PopupMenu(form,new View(form));
                form.onOptionsItemSelected(menu.getMenu().add(0,com.nextgis.maplibui.R.id.menu_apply,0,"Save"));
            });
            deadline=android.os.SystemClock.uptimeMillis()+20000;
            while(layers.get(0).getCount()!=1 && android.os.SystemClock.uptimeMillis()<deadline)Thread.sleep(40);
            assertEquals(1,layers.get(0).getCount());
            try(android.database.Cursor row=layers.get(0).query(null,null,null,null,null)) {
                assertTrue(row.moveToFirst());assertEquals("tree",row.getString(row.getColumnIndexOrThrow("typeobj")));
                assertEquals("field",row.getString(row.getColumnIndexOrThrow("classobj")));
            }
            // SQLite commits before the asynchronous Save result returns to the map.
            // Wait for that result and onPause before checking/cleaning the owning draft.
            AtomicBoolean finished = new AtomicBoolean();
            deadline = android.os.SystemClock.uptimeMillis() + 20000;
            while (!finished.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync();
                scenario.onActivity(activity -> finished.set(mode(activity) == MapFragment.MODE_NORMAL
                        && !app.isLayerReservedForWalk(layers.get(0).getId())
                        && com.nextgis.maplibui.util.FeatureFormDraftStore.load(app) == null
                        && com.nextgis.maplibui.util.GeometryEditDraftStore.load(app) == null));
                if (!finished.get()) Thread.sleep(40);
            }
            assertTrue("Save did not finish the map session and clear its drafts", finished.get());
        } finally {
            if (opened != null && !opened.isFinishing()) {
                com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity form = opened;
                instrumentation.runOnMainSync(form::finish);
            }
            instrumentation.waitForIdleSync();
            instrumentation.removeMonitor(monitor);
            com.nextgis.maplibui.util.FeatureFormDraftStore.clear(app);
            com.nextgis.maplibui.util.GeometryEditDraftStore.clear(app,"test-complete");
        }
    }

    private void addStandardCategoryLayer(int geometryType) {
        scenario.onActivity(activity -> {
            try (java.io.InputStream stream = InstrumentationRegistry.getInstrumentation().getContext()
                    .getAssets().open("feature-types/standard-field-types.json")) {
                org.json.JSONArray fixtures = new org.json.JSONArray(new String(com.nextgis.mobile.util.VerificationIo.readAll(stream), java.nio.charset.StandardCharsets.UTF_8));
                org.json.JSONObject fixture = null;
                for (int i = 0; i < fixtures.length(); i++)
                    if (fixtures.getJSONObject(i).getInt("geometry_type") == geometryType) fixture = fixtures.getJSONObject(i);
                assertNotNull(fixture);
                MapDrawable map = (MapDrawable) app.getMap();
                VectorLayerUI layer = new VectorLayerUI(app, new File(map.getPath(),
                        "creation_type_" + UUID.randomUUID().toString().replace("-", "")));
                map.addLayer(layer); layers.add(layer); layer.setName("Creation categories");
                ArrayList<com.nextgis.maplib.datasource.Field> fields = new ArrayList<>();
                org.json.JSONArray schema = fixture.getJSONArray("fields");
                for (int i = 0; i < schema.length(); i++) {
                    com.nextgis.maplib.datasource.Field field = new com.nextgis.maplib.datasource.Field();
                    field.fromJSON(schema.getJSONObject(i)); fields.add(field);
                }
                layer.create(geometryType, fields); layer.setIsEditable(true); layer.setVisible(false);
                layer.setRenderer(fixture.getJSONObject("renderer_properties"));
                assertTrue(com.nextgis.maplibui.util.FeatureTypeDefaults.hasCategories(layer));
                java.nio.file.Files.write(new File(layer.getPath(), "form.json").toPath(),
                        fixture.getJSONArray("form").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                java.nio.file.Files.write(new File(layer.getPath(), "ngfp_meta.json").toPath(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                map.save();
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    private void moveCameraAway() {
        scenario.onActivity(activity -> ((MapDrawable) app.getMap()).getMaplibreMap().moveCamera(
                CameraUpdateFactory.newLatLngZoom(new LatLng(-10, -15), 11.5)));
    }

    private void awaitRecreatedMap() throws Exception {
        java.util.concurrent.atomic.AtomicReference<org.maplibre.android.maps.MapLibreMap> current=new java.util.concurrent.atomic.AtomicReference<>();
        scenario.onActivity(activity->((org.maplibre.android.maps.MapView)activity.findViewById(R.id.mapViewMaplibre)).getMapAsync(current::set));
        AtomicBoolean ready=new AtomicBoolean();long deadline=android.os.SystemClock.uptimeMillis()+30000;
        do{
            scenario.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                ready.set(current.get()!=null && map.getMaplibreMap()==current.get()
                        && map.getMaplibreMapView()==activity.findViewById(R.id.mapViewMaplibre)
                        && activity.getMapFragment().getMMapRef().get().getMap()==map
                        && map.getMaplibreMapView().getForeground()==null
                        && current.get().getStyle()!=null && current.get().getStyle().getSource("track-line-source")!=null);});
            if(!ready.get())Thread.sleep(40);
        }while(!ready.get() && android.os.SystemClock.uptimeMillis()<deadline);
        assertTrue("The recreated map must apply its own sources before creation",ready.get());
        java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);
        org.maplibre.android.maps.MapView.OnDidFinishRenderingFrameListener rendered=(fully,encoding,rendering)->frame.countDown();
        scenario.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
            map.getMaplibreMapView().addOnDidFinishRenderingFrameListener(rendered);map.getMaplibreMap().triggerRepaint();});
        try{assertTrue("The recreated camera must render before creation",frame.await(15,java.util.concurrent.TimeUnit.SECONDS));}
        finally{scenario.onActivity(activity->((MapDrawable)app.getMap()).getMaplibreMapView().removeOnDidFinishRenderingFrameListener(rendered));}
    }

    private void assertCameraAtCreationFix() throws Exception {
        // Exercise the actual 750 ms legacy redraw which used to restore the
        // previous centre after a direct native-only camera move.
        scenario.onActivity(activity->activity.getMapFragment().getMMapRef().get().scheduleInvalidate());
        Thread.sleep(com.nextgis.maplibui.mapui.MapView.DISPLAY_REDRAW_TIMEOUT+250);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        java.util.concurrent.atomic.AtomicReference<org.maplibre.android.camera.CameraPosition> last=new java.util.concurrent.atomic.AtomicReference<>();
        long deadline=android.os.SystemClock.uptimeMillis()+10000;
        do{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->last.set(((MapDrawable)app.getMap()).getMaplibreMap().getCameraPosition()));
            org.maplibre.android.camera.CameraPosition camera=last.get();
            if(Math.abs(camera.target.getLatitude()-55)<.00001 && Math.abs(camera.target.getLongitude()-37)<.00001
                    && Math.abs(camera.zoom-11.5)<.00001)return;
            Thread.sleep(40);
        }while(android.os.SystemClock.uptimeMillis()<deadline);
        assertEquals(55,last.get().target.getLatitude(),.00001);
        assertEquals(37,last.get().target.getLongitude(),.00001);
        assertEquals(11.5,last.get().zoom,.00001);
    }

    private void supplyCurrentFix() {
        scenario.onActivity(activity -> {
            try {
                android.location.Location fix = new android.location.Location(android.location.LocationManager.GPS_PROVIDER);
                fix.setLatitude(55); fix.setLongitude(37); fix.setAccuracy(1);
                fix.setTime(System.currentTimeMillis()); fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
                com.nextgis.maplib.location.GpsEventSource source = app.getGpsEventSource();
                // Every scenario starts with one accepted fix, independent of the preceding recorder's filter.
                java.lang.reflect.Field filter = source.getClass().getDeclaredField("filter"); filter.setAccessible(true);
                ((com.nextgis.maplib.util.LocationTrackFilter) filter.get(source)).reset();
                java.lang.reflect.Field raw = source.getClass().getDeclaredField("rawGps"); raw.setAccessible(true); raw.set(source, null);
                java.lang.reflect.Field listener = source.getClass().getDeclaredField("gpsListener"); listener.setAccessible(true);
                ((android.location.LocationListener) listener.get(source)).onLocationChanged(fix);
                assertNotNull(source.getLastKnownLocation()); assertNotNull(source.getLastRecordingLocation());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }

    private void clickCreationAction(int id) throws Exception {
        scenario.onActivity(activity -> ((com.getbase.floatingactionbutton.FloatingActionsMenu)
                activity.findViewById(R.id.multiple_actions)).expand());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        AtomicBoolean enabled = new AtomicBoolean();
        long deadline = android.os.SystemClock.uptimeMillis() + 10_000;
        while (!enabled.get() && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                View action = activity.findViewById(id);
                enabled.set(action != null && action.isShown() && action.isEnabled());
            });
            if (!enabled.get()) Thread.sleep(40);
        }
        scenario.onActivity(activity -> {
            View action = activity.findViewById(id);
            assertTrue("Creation action disabled: " + id, action.isEnabled());
            assertSame(app.getMap(), activity.getMapFragment().getMMapRef().get().getMap());
            assertTrue(action.performClick());
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.fragment.app.Fragment chooser = activity.getSupportFragmentManager()
                    .findFragmentByTag(com.nextgis.maplibui.dialog.ChooseLayerDialog.TAG);
            if (chooser instanceof com.nextgis.maplibui.dialog.ChooseLayerDialog)
                ((com.nextgis.maplibui.dialog.ChooseLayerDialog) chooser).onLayerSelect(layers.get(layers.size() - 1));
        });
    }

    private android.os.Bundle chooseFirstCategory(boolean accept) throws Exception {
        return chooseFirstCategory(accept, false);
    }

    private android.os.Bundle chooseFirstCategory(boolean accept, boolean refreshGps) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<String> status = new java.util.concurrent.atomic.AtomicReference<>();
        long deadline = android.os.SystemClock.uptimeMillis() + 20000;
        while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                androidx.fragment.app.DialogFragment dialog = (androidx.fragment.app.DialogFragment) activity.getMapFragment()
                        .getChildFragmentManager().findFragmentByTag(com.nextgis.maplibui.dialog.ChooseFeatureTypeDialog.TAG);
                if (dialog == null || dialog.getDialog() == null) {
                    status.set("no category dialog; children=" + activity.getMapFragment().getChildFragmentManager().getFragments()
                            + "; activity=" + activity.getSupportFragmentManager().getFragments());
                    return;
                }
                android.widget.ListView list = ((androidx.appcompat.app.AlertDialog) dialog.getDialog()).getListView();
                status.set("category list=" + (list == null ? "null" : list.getCount() + "/" + list.getChildCount()));
                ready.set(list != null && list.getCount() > 0 && list.getChildCount() > 0
                        && list.getItemAtPosition(0) instanceof com.nextgis.maplibui.util.FeatureTypeDefaults.Choice);
            });
            if (!ready.get()) Thread.sleep(40);
        }
        assertTrue("Creation category did not load: " + status.get(), ready.get());
        // Waiting through cancellation/reopening can outlive the real eight-second GPS freshness window.
        if (refreshGps) supplyCurrentFix();
        final android.os.Bundle[] selected = new android.os.Bundle[1];
        scenario.onActivity(activity -> {
            androidx.fragment.app.DialogFragment dialog = (androidx.fragment.app.DialogFragment) activity.getMapFragment()
                    .getChildFragmentManager().findFragmentByTag(com.nextgis.maplibui.dialog.ChooseFeatureTypeDialog.TAG);
            android.widget.ListView list = ((androidx.appcompat.app.AlertDialog) dialog.requireDialog()).getListView();
            selected[0] = ((com.nextgis.maplibui.util.FeatureTypeDefaults.Choice) list.getItemAtPosition(0)).state;
            assertEquals(0, layers.get(layers.size() - 1).getCount());
            if (accept) {
                assertTrue(list.performItemClick(list.getChildAt(0), 0, 0));
                android.util.Log.i("CreationCameraTrace","after category camera="
                        +((MapDrawable)app.getMap()).getMaplibreMap().getCameraPosition());
            }
            else ((androidx.appcompat.app.AlertDialog) dialog.requireDialog()).getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return selected[0];
    }

    private void assertCategory(android.os.Bundle expected, android.os.Bundle actual) {
        assertNotNull(actual);
        for (String field : new String[]{"classobj", "typeobj"}) {
            String key = com.nextgis.maplibui.util.ControlHelper.getSavedStateKey(field);
            assertEquals(expected.getString(key), actual.getString(key));
        }
    }

    private void saveCategoryForm(android.app.Instrumentation.ActivityMonitor monitor, android.os.Bundle expected,
                                  String walkId) throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity form =
                (com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity) instrumentation.waitForMonitorWithTimeout(monitor, 20000);
        assertNotNull("Category attributes did not open", form);
        try {
            instrumentation.waitForIdleSync();
            com.nextgis.maplibui.util.FeatureFormDraftStore.Snapshot draft = com.nextgis.maplibui.util.FeatureFormDraftStore.load(app);
            assertNotNull(draft); assertEquals(walkId, draft.walkSessionId);
            assertCategory(expected, com.nextgis.maplibui.util.FeatureFormDraftStore.controlStateToBundle(draft));
            if (walkId == null) {
                GeoPoint point = (GeoPoint) com.nextgis.maplibui.util.FeatureFormDraftStore.geometryFromSnapshot(draft);
                GeoPoint expectedPoint = new GeoPoint(37, 55); expectedPoint.setCRS(GeoConstants.CRS_WGS84);
                assertTrue(expectedPoint.project(GeoConstants.CRS_WEB_MERCATOR));
                assertEquals(expectedPoint.getX(), point.getX(), .001);
                assertEquals(expectedPoint.getY(), point.getY(), .001);
            }
            assertEquals(0, layers.get(layers.size() - 1).getCount());
            instrumentation.runOnMainSync(() -> {
                android.widget.PopupMenu menu = new android.widget.PopupMenu(form, new View(form));
                assertTrue(form.onOptionsItemSelected(menu.getMenu().add(0, com.nextgis.maplibui.R.id.menu_apply, 0, "Save")));
            });
            AtomicBoolean saved = new AtomicBoolean();
            long deadline = android.os.SystemClock.uptimeMillis() + 20000;
            while (!saved.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync();
                scenario.onActivity(activity -> saved.set(mode(activity) == MapFragment.MODE_NORMAL
                        && com.nextgis.maplibui.util.FeatureFormDraftStore.load(app) == null
                        && com.nextgis.maplibui.util.GeometryEditDraftStore.load(app) == null));
                if (!saved.get()) Thread.sleep(40);
            }
            assertTrue("Category form did not finish saving", saved.get());
            // The form stops the map's SurfaceView. Validate its camera when the
            // actual map is visible again, after the form's successful handoff.
            if(walkId==null)assertCameraAtCreationFix();
            VectorLayerUI layer = layers.get(layers.size() - 1); assertEquals(1, layer.getCount());
            try (android.database.Cursor row = layer.query(null, null, null, null, null)) {
                assertTrue(row.moveToFirst());
                for (String field : new String[]{"classobj", "typeobj"})
                    assertEquals(expected.getString(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey(field)),
                            row.getString(row.getColumnIndexOrThrow(field)));
            }
        } finally {
            if (!form.isFinishing()) instrumentation.runOnMainSync(form::finish);
            instrumentation.waitForIdleSync();
        }
    }

    @Test public void currentLocationCategorySurvivesPickerRecreationAndSavesToSQLite() throws Exception {
        addStandardCategoryLayer(GeoConstants.GTPoint); supplyCurrentFix(); moveCameraAway();
        clickCreationAction(R.id.add_current_location);
        chooseFirstCategory(false);
        scenario.onActivity(activity -> assertEquals(MapFragment.MODE_NORMAL, mode(activity)));
        clickCreationAction(R.id.add_current_location);
        scenario.recreate();
        awaitRecreatedMap();
        supplyCurrentFix(); moveCameraAway();
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity.class.getName(), null, false);
        try { saveCategoryForm(monitor, chooseFirstCategory(true), null); }
        finally { instrumentation.removeMonitor(monitor); }
    }

    @Test public void cancellingLocationCategoryReleasesPointLockDuringBackgroundWalk() throws Exception {
        scenario.onActivity(activity -> beginWalk(activity, true, Constants.NOT_FOUND));
        addStandardCategoryLayer(GeoConstants.GTPoint);
        supplyCurrentFix();
        String walkId = com.nextgis.maplibui.util.WalkSessionStore.load(app).id;
        clickCreationAction(R.id.add_current_location);
        assertTrue(com.nextgis.maplibui.util.WalkSessionStore.load(app).isPointActive());
        chooseFirstCategory(false);
        com.nextgis.maplibui.util.WalkSessionStore.Snapshot session = com.nextgis.maplibui.util.WalkSessionStore.load(app);
        assertEquals(walkId, session.id); assertFalse(session.isPointActive());
        assertEquals(0, layers.get(1).getCount());
        supplyCurrentFix(); moveCameraAway();
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity.class.getName(), null, false);
        try {
            clickCreationAction(R.id.add_current_location);
            saveCategoryForm(monitor, chooseFirstCategory(true), null);
            assertEquals(walkId, com.nextgis.maplibui.util.WalkSessionStore.load(app).id);
            assertFalse(com.nextgis.maplibui.util.WalkSessionStore.load(app).isPointActive());
        } finally { instrumentation.removeMonitor(monitor); }
    }

    @Test public void walkCategorySurvivesRecorderInterruptionAndFinishedGeometryHandoff() throws Exception {
        addStandardCategoryLayer(GeoConstants.GTLineString); supplyCurrentFix(); moveCameraAway();
        clickCreationAction(R.id.add_geometry_by_walk);
        chooseFirstCategory(false); assertNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        clickCreationAction(R.id.add_geometry_by_walk);
        android.os.Bundle expected = chooseFirstCategory(true, true);
        assertCameraAtCreationFix();
        com.nextgis.maplibui.util.WalkSessionStore.Snapshot walk = com.nextgis.maplibui.util.WalkSessionStore.load(app);
        assertNotNull(walk); assertCategory(expected, com.nextgis.maplibui.util.FeatureTypeDefaults.decode(walk.initialValues));
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        while (!com.nextgis.maplibui.service.WalkEditService.isSessionRunning(walk.id)
                && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(40);
        assertTrue(com.nextgis.maplibui.service.WalkEditService.isSessionRunning(walk.id));
        app.stopService(new android.content.Intent(app, com.nextgis.maplibui.service.WalkEditService.class));
        deadline = android.os.SystemClock.uptimeMillis() + 10000;
        while (com.nextgis.maplibui.service.WalkEditService.isSessionRunning(walk.id)
                && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(40);
        assertFalse(com.nextgis.maplibui.service.WalkEditService.isSessionRunning(walk.id));
        // Simulate a durable recorder snapshot after interruption; keep its real owner and category.
        com.nextgis.maplib.datasource.GeoLineString line = (com.nextgis.maplib.datasource.GeoLineString) walk.geometry();
        line.add(new GeoPoint(line.getPoint(0).getX() + 10, line.getPoint(0).getY() + 10));
        assertTrue(com.nextgis.maplibui.util.WalkSessionStore.updateGeometry(app, walk.id, line, 2, true));
        scenario.recreate();
        awaitRecreatedMap();
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity.class.getName(), null, false);
        try {
            clickWalkButton(com.nextgis.maplibui.R.id.walk_panel_finish);
            clickDialogButton("android:id/button1");
            saveCategoryForm(monitor, expected, walk.id);
            assertNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        } finally { instrumentation.removeMonitor(monitor); }
    }

    private void addCopyFixture(int geometryType, boolean editable, boolean visible, String wkt,
                                List<com.nextgis.maplib.datasource.Field> fields, ContentValues attributes) {
        scenario.onActivity(activity -> {
            try {
                MapDrawable map = (MapDrawable) app.getMap();
                VectorLayerUI layer = new VectorLayerUI(app, new File(map.getPath(),
                        "creation_type_" + UUID.randomUUID().toString().replace("-", "")));
                map.addLayer(layer); layers.add(layer);
                layer.setName("Слой для проверки копирования объектов");
                layer.create(geometryType, fields); layer.setIsEditable(editable); layer.setVisible(visible);
                long id = Constants.NOT_FOUND;
                if (wkt != null) {
                    ContentValues values = new ContentValues(attributes);
                    values.put(Constants.FIELD_GEOM, com.nextgis.maplib.datasource.GeoGeometryFactory
                            .fromWKT(wkt, GeoConstants.CRS_WEB_MERCATOR).toBlob());
                    id = layer.insertAddChanges(values);
                }
                ids.add(id); map.save();
            } catch (Exception error) { throw new AssertionError(error); }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void tapCopyAction() throws Exception {
        AtomicBoolean focused = new AtomicBoolean();
        long deadline = android.os.SystemClock.uptimeMillis()+10000;
        while(!focused.get() && android.os.SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity(activity -> focused.set(activity.getWindow().getDecorView().hasWindowFocus()));
            if(!focused.get())Thread.sleep(50);
        }
        assertTrue("Map window must have input focus before the copy tap",focused.get());
        final float[] position = new float[2];
        List<Integer> touches = new ArrayList<>();
        scenario.onActivity(activity -> {
            View action = activity.getBottomToolbar().findViewById(R.id.menu_feature_copy);
            assertNotNull(action); assertTrue(action.isShown());
            assertTrue("Copy menu item must be enabled", activity.getBottomToolbar().getMenu()
                    .findItem(R.id.menu_feature_copy).isEnabled());
            assertTrue("Copy action view must follow its enabled menu item", action.isEnabled());
            android.graphics.Rect visible = new android.graphics.Rect();
            assertTrue(action.getGlobalVisibleRect(visible));
            assertEquals(action.getWidth(),visible.width()); assertEquals(action.getHeight(),visible.height());
            int[] location=new int[2]; action.getLocationOnScreen(location);
            position[0]=location[0]+action.getWidth()/2f; position[1]=location[1]+action.getHeight()/2f;
            action.setOnTouchListener((view, event) -> { touches.add(event.getActionMasked()); return false; });
        });
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        long down=android.os.SystemClock.uptimeMillis();
        MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,position[0],position[1],0);
        MotionEvent release=MotionEvent.obtain(down,down+40,MotionEvent.ACTION_UP,position[0],position[1],0);
        instrumentation.sendPointerSync(press); instrumentation.sendPointerSync(release);
        press.recycle(); release.recycle(); instrumentation.waitForIdleSync();
        assertEquals("Copy action touch stream at "+java.util.Arrays.toString(position),
                java.util.Arrays.asList(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP),touches);
        AtomicBoolean shown = new AtomicBoolean();
        deadline = android.os.SystemClock.uptimeMillis()+5000;
        while(!shown.get() && android.os.SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity(activity -> shown.set(activity.getMapFragment().getChildFragmentManager()
                    .findFragmentByTag(com.nextgis.maplibui.dialog.ChooseLayerDialog.TAG)!=null));
            if(!shown.get())Thread.sleep(50);
        }
        assertTrue("The visible copy action must receive the real screen tap",shown.get());
    }

    private void tapVisibleView(View action) {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final float[] position = new float[2];
        instrumentation.runOnMainSync(() -> {
            assertNotNull(action); assertTrue(action.isShown()); assertTrue(action.isEnabled());
            android.graphics.Rect visible = new android.graphics.Rect();
            assertTrue(action.getGlobalVisibleRect(visible));
            assertEquals(action.getWidth(), visible.width()); assertEquals(action.getHeight(), visible.height());
            int[] location = new int[2]; action.getLocationOnScreen(location);
            position[0] = location[0] + action.getWidth()/2f;
            position[1] = location[1] + action.getHeight()/2f;
        });
        long down = android.os.SystemClock.uptimeMillis();
        MotionEvent press = MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,position[0],position[1],0);
        MotionEvent release = MotionEvent.obtain(down,down+40,MotionEvent.ACTION_UP,position[0],position[1],0);
        instrumentation.sendPointerSync(press); instrumentation.sendPointerSync(release);
        press.recycle(); release.recycle(); instrumentation.waitForIdleSync();
    }

    private void chooseCopyTarget(int target, boolean cancel) {
        java.util.concurrent.atomic.AtomicReference<View> action = new java.util.concurrent.atomic.AtomicReference<>();
        scenario.onActivity(activity -> {
            com.nextgis.maplibui.dialog.ChooseLayerDialog dialog = (com.nextgis.maplibui.dialog.ChooseLayerDialog)
                    activity.getMapFragment().getChildFragmentManager().findFragmentByTag(com.nextgis.maplibui.dialog.ChooseLayerDialog.TAG);
            assertNotNull(dialog); assertNotNull(dialog.getDialog());
            assertFalse(dialog.getLayers().contains(layers.get(0)));
            for (com.nextgis.maplib.api.ILayer candidate : dialog.getLayers()) {
                VectorLayerUI layer = (VectorLayerUI) candidate;
                assertTrue(layer.isEditingAllowed());
                assertTrue(com.nextgis.maplib.util.FeatureCopyPolicy.accepts(layers.get(0).getGeometryForId(ids.get(0)), layer.getGeometryType()));
            }
            if (cancel) action.set(((androidx.appcompat.app.AlertDialog) dialog.requireDialog())
                    .getButton(android.content.DialogInterface.BUTTON_NEGATIVE));
            else {
                android.widget.ListView list = dialog.requireDialog().findViewById(com.nextgis.maplibui.R.id.listView);
                int index = dialog.getLayers().indexOf(layers.get(target)); assertTrue(index >= 0);
                action.set(list.getChildAt(index - list.getFirstVisiblePosition()));
            }
        });
        tapVisibleView(action.get());
    }

    @Test public void copyReadOnlyFeatureChoosesCompatibleLayerAndCategoryAndSavesMatchingFields() throws Exception {
        List<com.nextgis.maplib.datasource.Field> fields = java.util.Arrays.asList(
                new com.nextgis.maplib.datasource.Field(GeoConstants.FTString,"name","Название"),
                new com.nextgis.maplib.datasource.Field(GeoConstants.FTInteger,"quantity","Количество"),
                new com.nextgis.maplib.datasource.Field(GeoConstants.FTLong,"big","Номер"),
                new com.nextgis.maplib.datasource.Field(GeoConstants.FTString,"note","Примечание"),
                new com.nextgis.maplib.datasource.Field(GeoConstants.FTDateTime,"date","Дата"),
                new com.nextgis.maplib.datasource.Field(GeoConstants.FTString,"typeobj","Категория"));
        ContentValues source = new ContentValues(); source.put("name","Исходный объект");
        source.put("quantity",0); source.put("big",9007199254740993L); source.putNull("note");
        source.putNull("date"); source.put("typeobj","source-category");
        addCopyFixture(GeoConstants.GTPoint,false,true,"POINT (120 340)",fields,source);
        addCopyFixture(GeoConstants.GTPoint,true,false,null,fields,new ContentValues());
        addCopyFixture(GeoConstants.GTLineString,true,true,null,fields,new ContentValues());
        addCopyFixture(GeoConstants.GTPoint,false,true,null,fields,new ContentValues());
        // Custom numeric controls must also retain int64 precision and typed category overrides.
        File formFile = new File(layers.get(1).getPath(),"form.json");
        File metaFile = new File(layers.get(1).getPath(),"ngfp_meta.json");
        org.json.JSONArray controls = new org.json.JSONArray()
                .put(new org.json.JSONObject().put("type","counter").put("attributes",new org.json.JSONObject().put("field","big")))
                .put(new org.json.JSONObject().put("type","checkbox").put("attributes",new org.json.JSONObject().put("field","quantity")))
                .put(new org.json.JSONObject().put("type","text").put("attributes",new org.json.JSONObject().put("field","name")));
        java.nio.file.Files.write(formFile.toPath(),controls.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.write(metaFile.toPath(),"{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            android.os.Bundle category = new android.os.Bundle();
            category.putString(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("quantity"),"1");
            android.os.Bundle typed = com.nextgis.maplibui.util.FeatureCopyDefaults.create(
                    layers.get(0).getFeature(ids.get(0)),layers.get(1),category);
            assertEquals(9007199254740993L,typed.getLong(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("big")));
            assertTrue(typed.getBoolean(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("quantity")));
            assertEquals("Исходный объект",typed.getString(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("name")));
        } finally { assertTrue(formFile.delete()); assertTrue(metaFile.delete()); }
        scenario.onActivity(activity -> {
            com.nextgis.maplib.display.SimpleMarkerStyle symbol = new com.nextgis.maplib.display.SimpleMarkerStyle();
            symbol.setColor(android.graphics.Color.RED);
            com.nextgis.maplib.display.FieldStyleRule rule = new com.nextgis.maplib.display.FieldStyleRule(layers.get(1));
            rule.setKey("typeobj"); rule.setStyle("destination-category", symbol);
            layers.get(1).setRenderer(new com.nextgis.maplib.display.RuleFeatureRenderer(layers.get(1),rule,symbol));
            select(activity,0); assertEquals(MapFragment.MODE_SELECT_FOR_VIEW,mode(activity));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        tapCopyAction();
        scenario.recreate(); awaitRecreatedMap();
        chooseCopyTarget(1,true); assertEquals(0,layers.get(1).getCount());
        tapCopyAction();
        chooseCopyTarget(1,false);
        AtomicBoolean categoryShown = new AtomicBoolean();
        long categoryDeadline = android.os.SystemClock.uptimeMillis()+10000;
        while(!categoryShown.get() && android.os.SystemClock.uptimeMillis()<categoryDeadline) {
            scenario.onActivity(activity -> {
                androidx.fragment.app.DialogFragment category = (androidx.fragment.app.DialogFragment)
                        activity.getMapFragment().getChildFragmentManager()
                                .findFragmentByTag(com.nextgis.maplibui.dialog.ChooseFeatureTypeDialog.TAG);
                categoryShown.set(category != null && category.getDialog() != null && category.requireDialog().isShowing());
            });
            if(!categoryShown.get())Thread.sleep(50);
        }
        assertTrue("The destination row tap must open its category chooser",categoryShown.get());
        scenario.recreate(); awaitRecreatedMap();
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                com.nextgis.maplibui.activity.ModifyAttributesActivity.class.getName(),null,false);
        com.nextgis.maplibui.activity.ModifyAttributesActivity form = null;
        try {
            chooseFirstCategory(true,false);
            form = (com.nextgis.maplibui.activity.ModifyAttributesActivity) instrumentation.waitForMonitorWithTimeout(monitor,20000);
            assertNotNull("Copy must open the destination form",form); instrumentation.waitForIdleSync();
            assertTrue(layers.get(1).isVisible()); assertEquals(0,layers.get(1).getCount());
            android.os.Bundle initial = form.getIntent().getBundleExtra(com.nextgis.maplibui.util.FeatureTypeDefaults.INITIAL_VALUES);
            assertNotNull(initial);
            assertEquals("destination-category",initial.getString(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("typeobj")));
            assertEquals("Исходный объект",initial.getString(com.nextgis.maplibui.util.ControlHelper.getSavedStateKey("name")));
            assertNotNull(com.nextgis.maplibui.util.FeatureFormDraftStore.load(app));
            final com.nextgis.maplibui.activity.ModifyAttributesActivity opened = form;
            java.util.concurrent.atomic.AtomicReference<View> save = new java.util.concurrent.atomic.AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)
                        opened.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                keyboard.hideSoftInputFromWindow(opened.getWindow().getDecorView().getWindowToken(),0);
                save.set(opened.findViewById(com.nextgis.maplibui.R.id.form_save));
            });
            instrumentation.waitForIdleSync();
            tapVisibleView(save.get());
            long deadline = android.os.SystemClock.uptimeMillis()+20000;
            while (layers.get(1).getCount()==0 && android.os.SystemClock.uptimeMillis()<deadline) Thread.sleep(50);
            assertEquals(1,layers.get(1).getCount());
            try (android.database.Cursor row = layers.get(1).query(null,null,null,null,null)) {
                assertTrue(row.moveToFirst());
                assertEquals("Исходный объект",row.getString(row.getColumnIndexOrThrow("name")));
                assertEquals(0,row.getLong(row.getColumnIndexOrThrow("quantity")));
                assertEquals(9007199254740993L,row.getLong(row.getColumnIndexOrThrow("big")));
                assertTrue(row.isNull(row.getColumnIndexOrThrow("note")));
                assertTrue(row.isNull(row.getColumnIndexOrThrow("date")));
                assertEquals("destination-category",row.getString(row.getColumnIndexOrThrow("typeobj")));
                long copiedId = row.getLong(row.getColumnIndexOrThrow(Constants.FIELD_ID));
                assertEquals(layers.get(0).getGeometryForId(ids.get(0)).toWKT(true),layers.get(1).getGeometryForId(copiedId).toWKT(true));
            }
            assertEquals(1,layers.get(0).getCount());
            assertEquals("source-category",layers.get(0).getFeature(ids.get(0)).getFieldValueAsString("typeobj"));
            AtomicBoolean finished = new AtomicBoolean();
            deadline = android.os.SystemClock.uptimeMillis()+20000;
            while(!finished.get() && android.os.SystemClock.uptimeMillis()<deadline) {
                instrumentation.waitForIdleSync();
                scenario.onActivity(activity -> finished.set(mode(activity)==MapFragment.MODE_NORMAL
                        && com.nextgis.maplibui.util.FeatureFormDraftStore.load(app)==null));
                if(!finished.get())Thread.sleep(50);
            }
            assertTrue("Copy save must finish its form handoff",finished.get());
        } finally {
            if (form != null && !form.isFinishing()) { final android.app.Activity opened=form; instrumentation.runOnMainSync(opened::finish); }
            instrumentation.removeMonitor(monitor); instrumentation.waitForIdleSync();
        }
    }

    private void transformEvent(View view,long down,int action,float... xy) {
        int count = xy.length/2;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int i=0;i<count;i++) {
            properties[i]=new MotionEvent.PointerProperties(); properties[i].id=i; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i]=new MotionEvent.PointerCoords(); coordinates[i].x=xy[2*i]; coordinates[i].y=xy[2*i+1];
            coordinates[i].pressure=1; coordinates[i].size=1;
        }
        MotionEvent event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,count,properties,coordinates,
                0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        view.dispatchTouchEvent(event); event.recycle();
    }

    @Test public void wholeObjectGesturesKeepCameraFixedAndUndoEachDragOrRotationOnce() throws Exception {
        addCopyFixture(GeoConstants.GTMultiPolygon,true,true,
                "MULTIPOLYGON (((0 0, 100 0, 100 100, 0 100, 0 0), (20 20, 80 20, 80 80, 20 80, 20 20)),"
                        + " ((200 200, 300 200, 300 300, 200 300, 200 200)))",Collections.emptyList(),new ContentValues());
        // Wait for hot-add, then exercise the real MapView touch dispatch rather than invoking a geometry helper.
        long until=android.os.SystemClock.uptimeMillis()+20000; AtomicBoolean ready=new AtomicBoolean();
        while(!ready.get() && android.os.SystemClock.uptimeMillis()<until) {
            scenario.onActivity(activity -> ready.set(((MapDrawable)app.getMap()).getMaplibreMap().getStyle()
                    .getSource(layers.get(0).getPath().toString()) != null));
            if(!ready.get())Thread.sleep(50);
        }
        assertTrue(ready.get());
        scenario.onActivity(activity -> {
            MapDrawable map=(MapDrawable)app.getMap(); MapFragment fragment=activity.getMapFragment();
            map.getMaplibreMap().moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(0,0),16));
            select(activity,0);
            assertTrue(activity.getBottomToolbar().getMenu().performIdentifierAction(R.id.menu_feature_edit,0));
            assertNotNull(map.editingObject);
            com.nextgis.maplib.datasource.GeoGeometry original=fragment.getEditLayerOverlay().getSelectedFeatureGeometry();
            String sourceWkt=layers.get(0).getGeometryForId(ids.get(0)).toWKT(true);
            org.maplibre.android.camera.CameraPosition camera=map.getMaplibreMap().getCameraPosition();
            assertTrue(fragment.onOptionsItemSelected(com.nextgis.maplibui.R.id.menu_edit_transform));
            assertTrue(map.isGeometryTransformEnabled());
            View mapView=map.getMaplibreMapView(); float x=mapView.getWidth()/2f,y=mapView.getHeight()/2f;
            long down=android.os.SystemClock.uptimeMillis();
            transformEvent(mapView,down,MotionEvent.ACTION_DOWN,x,y);
            transformEvent(mapView,down,MotionEvent.ACTION_MOVE,x+60,y+35);
            transformEvent(mapView,down,MotionEvent.ACTION_UP,x+60,y+35);
            com.nextgis.maplib.datasource.GeoGeometry moved=fragment.getEditLayerOverlay().getSelectedFeatureGeometry();
            assertNotEquals(original.toWKT(true),moved.toWKT(true));
            assertEquals(camera,map.getMaplibreMap().getCameraPosition());
            assertTrue(fragment.onOptionsItemSelected(com.nextgis.maplibui.R.id.menu_edit_undo));
            assertEquals(original.getEnvelope().getMinX(),fragment.getEditLayerOverlay().getSelectedFeatureGeometry().getEnvelope().getMinX(),.001);
            assertTrue(fragment.onOptionsItemSelected(com.nextgis.maplibui.R.id.menu_edit_redo));
            down=android.os.SystemClock.uptimeMillis();
            transformEvent(mapView,down,MotionEvent.ACTION_DOWN,x-70,y);
            transformEvent(mapView,down,MotionEvent.ACTION_POINTER_DOWN | (1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x-70,y,x+70,y);
            transformEvent(mapView,down,MotionEvent.ACTION_MOVE,x,y-100,x,y+100);
            transformEvent(mapView,down,MotionEvent.ACTION_POINTER_UP | (1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y-100,x,y+100);
            transformEvent(mapView,down,MotionEvent.ACTION_UP,x,y-100);
            com.nextgis.maplib.datasource.GeoMultiPolygon rotated=(com.nextgis.maplib.datasource.GeoMultiPolygon)fragment.getEditLayerOverlay().getSelectedFeatureGeometry();
            assertEquals(2,rotated.size()); assertEquals(1,rotated.get(0).getInnerRingCount());
            assertEquals(100,rotated.get(0).getOuterRing().getPoint(0).distance(rotated.get(0).getOuterRing().getPoint(1)),.001);
            assertTrue(rotated.get(0).getInnerRing(0).isClosed());
            assertTrue(fragment.onOptionsItemSelected(com.nextgis.maplibui.R.id.menu_edit_undo));
            assertEquals(moved.getEnvelope().getMinX(),fragment.getEditLayerOverlay().getSelectedFeatureGeometry().getEnvelope().getMinX(),.001);
            assertTrue(fragment.onOptionsItemSelected(com.nextgis.maplibui.R.id.menu_edit_redo));
            String beforeCancel=fragment.getEditLayerOverlay().getSelectedFeatureGeometry().toWKT(true);
            down=android.os.SystemClock.uptimeMillis();
            transformEvent(mapView,down,MotionEvent.ACTION_DOWN,x,y);
            transformEvent(mapView,down,MotionEvent.ACTION_MOVE,x+30,y);
            transformEvent(mapView,down,MotionEvent.ACTION_CANCEL,x+30,y);
            assertEquals(beforeCancel,fragment.getEditLayerOverlay().getSelectedFeatureGeometry().toWKT(true));
            assertEquals(sourceWkt,layers.get(0).getGeometryForId(ids.get(0)).toWKT(true));
            assertEquals(camera,map.getMaplibreMap().getCameraPosition());
            assertNotNull(com.nextgis.maplibui.util.GeometryEditDraftStore.load(app));
            assertTrue(fragment.onOptionsItemSelected(com.nextgis.maplibui.R.id.menu_edit_transform));
            assertFalse(map.isGeometryTransformEnabled());
            assertTrue(fragment.saveEdits());
            assertNotEquals(sourceWkt,layers.get(0).getGeometryForId(ids.get(0)).toWKT(true));
        });
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
        openMeasurementMenu();
        chooseMeasurementTool(R.id.measurement_ruler);
        scenario.onActivity(activity -> {
            MapDrawable map = (MapDrawable) app.getMap();
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

    @Test public void pendingNativeTapIsCancelledBeforeViewDestruction() throws Exception {
        for (int iteration = 0; iteration < 3; iteration++) {
            java.util.concurrent.atomic.AtomicReference<org.maplibre.android.maps.MapView> previous =
                    new java.util.concurrent.atomic.AtomicReference<>();
            scenario.onActivity(activity -> {
                org.maplibre.android.maps.MapView view = activity.findViewById(R.id.mapViewMaplibre);
                previous.set(view);
                assertSame(view, ((MapDrawable) app.getMap()).getMaplibreMapView());
                long now = android.os.SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                        view.getWidth() * .25f, view.getHeight() * .3f, 0);
                MotionEvent up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP,
                        view.getWidth() * .25f, view.getHeight() * .3f, 0);
                try {
                    // Feed the real SDK detector so its single-tap confirmation is pending.
                    view.onTouchEvent(down);
                    view.onTouchEvent(up);
                } finally { down.recycle(); up.recycle(); }
            });
            scenario.recreate();
            long deadline = android.os.SystemClock.uptimeMillis() + 30000;
            AtomicBoolean rebound = new AtomicBoolean();
            while (!rebound.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> {
                    org.maplibre.android.maps.MapView current = activity.findViewById(R.id.mapViewMaplibre);
                    MapDrawable map = (MapDrawable) app.getMap();
                    rebound.set(previous.get().isDestroyed() && current != previous.get()
                            && current != null && !current.isDestroyed()
                            && map.getMaplibreMapView() == current && map.getMaplibreMap() != null
                            && map.getMaplibreMap().getStyle() != null);
                });
                if (!rebound.get()) Thread.sleep(50);
            }
            assertTrue("Recreation must replace the destroyed native map", rebound.get());
            // Let an uncancelled SDK confirmation run while strict mode remains enabled.
            Thread.sleep(android.view.ViewConfiguration.getDoubleTapTimeout() + 120L);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    private void openMeasurementMenu() {
        scenario.onActivity(activity -> {
            View button = activity.findViewById(R.id.action_measurements);
            assertEquals(View.VISIBLE, button.getVisibility());
            assertTrue(button.isEnabled());
            assertEquals(activity.getString(R.string.measurement_tools), button.getContentDescription());
            assertTrue(button.performClick());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private android.widget.ListView measurementMenu(View root) {
        if (root instanceof android.widget.ListView && root.isShown()) {
            android.widget.ListView list = (android.widget.ListView) root;
            for (int i = 0; i < list.getCount(); i++) {
                Object item = list.getItemAtPosition(i);
                if (item instanceof android.view.MenuItem && ((android.view.MenuItem) item).getItemId() == R.id.measurement_azimuth_points)
                    return list;
            }
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.ListView list = measurementMenu(group.getChildAt(i));
                if (list != null) return list;
            }
        }
        return null;
    }

    private void chooseMeasurementTool(int id) {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicBoolean clicked = new AtomicBoolean();
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (!clicked.get() && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.widget.ListView list = measurementMenu(root);
                    if (list == null) continue;
                    for (int i = 0; i < list.getCount(); i++) {
                        android.view.MenuItem item = (android.view.MenuItem) list.getItemAtPosition(i);
                        if (item.getItemId() == id) {
                            View row = list.getChildAt(i - list.getFirstVisiblePosition());
                            if (row != null) clicked.set(list.performItemClick(row, i, list.getItemIdAtPosition(i)));
                        }
                    }
                }
            });
            if (!clicked.get()) android.os.SystemClock.sleep(50);
        }
        assertTrue("Measurement choice unavailable: " + id, clicked.get());
        instrumentation.waitForIdleSync();
    }

    @Test public void oneMeasurementButtonStartsRulerAndBothAzimuthModes() {
        openMeasurementMenu();
        // Back dismisses the chooser without starting a tool.
        InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            assertEquals(MapFragment.MODE_NORMAL, mode(activity));
            assertFalse(activity.getMapFragment().isRulerMeasuring());
        });
        openMeasurementMenu(); chooseMeasurementTool(R.id.measurement_ruler);
        scenario.onActivity(activity -> {
            assertTrue(activity.getMapFragment().isRulerMeasuring());
            assertEquals(View.GONE, activity.findViewById(R.id.action_measurements).getVisibility());
            assertTrue(activity.findViewById(R.id.add_point_by_tap).performClick());
        });
        for (int choice : new int[]{R.id.measurement_azimuth_points, R.id.measurement_azimuth_current}) {
            openMeasurementMenu(); chooseMeasurementTool(choice);
            scenario.onActivity(activity -> {
                assertEquals(choice == R.id.measurement_azimuth_current ? MapFragment.MODE_AZIMUTH_CURRENT : MapFragment.MODE_AZIMUTH_POINTS,
                        mode(activity));
                assertEquals(View.GONE, activity.findViewById(R.id.action_measurements).getVisibility());
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(MapFragment.MODE_NORMAL, mode(activity));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.action_measurements).getVisibility());
            });
        }
    }

    @Test public void hidingRulerKeepsAzimuthChoicesAccessibleAfterRecreation() {
        String key = AppSettingsConstants.KEY_PREF_SHOW_MEASURING;
        boolean had = preferences.contains(key), old = preferences.getBoolean(key, true);
        try {
            assertTrue(preferences.edit().putBoolean(key, false).commit());
            scenario.recreate(); openMeasurementMenu();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                android.widget.ListView list = null;
                for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    list = measurementMenu(root); if (list != null) break;
                }
                assertNotNull(list); assertEquals(2, list.getCount());
                for (int i = 0; i < list.getCount(); i++)
                    assertNotEquals(R.id.measurement_ruler, ((android.view.MenuItem) list.getItemAtPosition(i)).getItemId());
            });
            chooseMeasurementTool(R.id.measurement_azimuth_points);
            scenario.onActivity(activity -> {
                assertEquals(MapFragment.MODE_AZIMUTH_POINTS, mode(activity));
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(View.VISIBLE, activity.findViewById(R.id.action_measurements).getVisibility());
            });
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            if (had) editor.putBoolean(key, old); else editor.remove(key);
            assertTrue(editor.commit());
        }
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
