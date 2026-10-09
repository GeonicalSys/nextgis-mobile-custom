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
        assertTrue("Run only on the isolated emulator", android.os.Build.MODEL.contains("sdk")
                || android.os.Build.FINGERPRINT.startsWith("generic"));
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

    private android.widget.TextView buttonWithText(View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) view).getText()))
            return (android.widget.TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.TextView button = buttonWithText(group.getChildAt(i), text);
                if (button != null) return button;
            }
        }
        return null;
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
            assertTrue(buttonWithText(panel, activity.getString(com.nextgis.maplibui.R.string.walk_finish)).performClick());
        });
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
                    assertTrue(buttonWithText(panel, activity.getString(com.nextgis.maplibui.R.string.walk_finish)).performClick());
                });
                AtomicBoolean ready = new AtomicBoolean();
                long deadline = android.os.SystemClock.uptimeMillis() + 20000;
                while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync();
                    scenario.onActivity(activity -> ready.set(mode(activity) == MapFragment.MODE_EDIT));
                    if (!ready.get()) Thread.sleep(40);
                }
                assertTrue("Finished geometry did not reach the editor", ready.get());
                if (featureId == Constants.NOT_FOUND) {
                    android.graphics.Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
                    assertNotNull(screenshot);
                    try (java.io.FileOutputStream image = new java.io.FileOutputStream(new File(app.getFilesDir(), "walk-finished-panel.png"))) {
                        assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, image));
                    } finally { screenshot.recycle(); }
                }
                scenario.onActivity(activity -> {
                    View panel = activity.findViewById(R.id.walk_recording_panel);
                    android.widget.TextView save = buttonWithText(panel, activity.getString(com.nextgis.maplibui.R.string.walk_complete_object));
                    assertNotNull(save); assertTrue(save.isEnabled()); assertEquals(1f, save.getAlpha(), 0);
                    assertNull(buttonWithText(panel, "⋮"));
                    activity.showEditToolbar();
                    androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.main_toolbar);
                    android.view.MenuItem disk = toolbar.getMenu().findItem(com.nextgis.maplibui.R.id.menu_edit_save);
                    assertTrue(disk.isEnabled()); assertEquals(255, disk.getIcon().getAlpha());
                    assertTrue(save.performClick());
                });
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
                deadline = android.os.SystemClock.uptimeMillis() + 20000;
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

    @Test public void bottomWalkPanelMovesForCreationMenuAndCancelRequiresConfirmation() {
        final int[] bottom = new int[1];
        scenario.onActivity(activity -> beginWalk(activity, true, Constants.NOT_FOUND));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            View panel = activity.findViewById(R.id.walk_recording_panel);
            bottom[0] = panel.getBottom();
            assertTrue(panel.getBottom() > activity.findViewById(R.id.map_action_row).getTop());
            ((com.getbase.floatingactionbutton.FloatingActionsMenu) activity.findViewById(R.id.multiple_actions)).expand();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            assertTrue(activity.findViewById(R.id.walk_recording_panel).getBottom() < bottom[0]);
            ((com.getbase.floatingactionbutton.FloatingActionsMenu) activity.findViewById(R.id.multiple_actions)).collapse();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            View panel = activity.findViewById(R.id.walk_recording_panel);
            assertEquals(bottom[0], panel.getBottom());
            assertTrue(buttonWithText(panel, activity.getString(com.nextgis.maplibui.R.string.walk_cancel)).performClick());
            assertNotNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        });
        clickDialogButton("android:id/button2");
        assertNotNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        scenario.onActivity(activity -> {
            View panel = activity.findViewById(R.id.walk_recording_panel);
            buttonWithText(panel, activity.getString(com.nextgis.maplibui.R.string.walk_cancel)).performClick();
        });
        clickDialogButton("android:id/button1");
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (com.nextgis.maplibui.util.WalkSessionStore.load(app) != null
                && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50);
        assertNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        assertEquals(0, layers.get(0).getCount());
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
                activity.getMapFragment().onFinishChooseLayerDialog(MapFragment.EDIT_LAYER,layer,false,false);
                assertEquals(MapFragment.MODE_NORMAL,mode(activity));
            } catch(Exception error) { throw new AssertionError(error); }
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
                org.json.JSONArray fixtures = new org.json.JSONArray(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
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

    private void clickCreationAction(int id) {
        scenario.onActivity(activity -> ((com.getbase.floatingactionbutton.FloatingActionsMenu)
                activity.findViewById(R.id.multiple_actions)).expand());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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
        final android.os.Bundle[] selected = new android.os.Bundle[1];
        scenario.onActivity(activity -> {
            androidx.fragment.app.DialogFragment dialog = (androidx.fragment.app.DialogFragment) activity.getMapFragment()
                    .getChildFragmentManager().findFragmentByTag(com.nextgis.maplibui.dialog.ChooseFeatureTypeDialog.TAG);
            android.widget.ListView list = ((androidx.appcompat.app.AlertDialog) dialog.requireDialog()).getListView();
            selected[0] = ((com.nextgis.maplibui.util.FeatureTypeDefaults.Choice) list.getItemAtPosition(0)).state;
            assertEquals(0, layers.get(layers.size() - 1).getCount());
            if (accept) assertTrue(list.performItemClick(list.getChildAt(0), 0, 0));
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
        addStandardCategoryLayer(GeoConstants.GTPoint); supplyCurrentFix();
        clickCreationAction(R.id.add_current_location);
        chooseFirstCategory(false);
        scenario.onActivity(activity -> assertEquals(MapFragment.MODE_NORMAL, mode(activity)));
        clickCreationAction(R.id.add_current_location);
        scenario.recreate();
        supplyCurrentFix();
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity.class.getName(), null, false);
        try { saveCategoryForm(monitor, chooseFirstCategory(true), null); }
        finally { instrumentation.removeMonitor(monitor); }
    }

    @Test public void cancellingLocationCategoryReleasesPointLockDuringBackgroundWalk() throws Exception {
        scenario.onActivity(activity -> beginWalk(activity, true, Constants.NOT_FOUND));
        addStandardCategoryLayer(GeoConstants.GTPoint);
        String walkId = com.nextgis.maplibui.util.WalkSessionStore.load(app).id;
        clickCreationAction(R.id.add_current_location);
        assertTrue(com.nextgis.maplibui.util.WalkSessionStore.load(app).isPointActive());
        chooseFirstCategory(false);
        com.nextgis.maplibui.util.WalkSessionStore.Snapshot session = com.nextgis.maplibui.util.WalkSessionStore.load(app);
        assertEquals(walkId, session.id); assertFalse(session.isPointActive());
        assertEquals(0, layers.get(1).getCount());
        supplyCurrentFix();
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
        addStandardCategoryLayer(GeoConstants.GTLineString); supplyCurrentFix();
        clickCreationAction(R.id.add_geometry_by_walk);
        chooseFirstCategory(false); assertNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        clickCreationAction(R.id.add_geometry_by_walk);
        android.os.Bundle expected = chooseFirstCategory(true);
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
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        scenario.onActivity(activity -> {
            View panel = activity.findViewById(R.id.walk_recording_panel);
            ((com.nextgis.maplibui.view.WalkRecordingPanel) panel).refresh();
            assertTrue(buttonWithText(panel, activity.getString(com.nextgis.maplibui.R.string.walk_finish)).performClick());
        });
        AtomicBoolean editing = new AtomicBoolean(); deadline = android.os.SystemClock.uptimeMillis() + 20000;
        while (!editing.get() && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> editing.set(mode(activity) == MapFragment.MODE_EDIT));
            if (!editing.get()) Thread.sleep(40);
        }
        assertTrue("Finished walk did not recover", editing.get());
        assertCategory(expected, com.nextgis.maplibui.util.FeatureTypeDefaults.decode(
                com.nextgis.maplibui.util.GeometryEditDraftStore.load(app).initialValues));
        android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity.class.getName(), null, false);
        try {
            scenario.onActivity(activity -> assertTrue(activity.getMapFragment().saveEdits()));
            saveCategoryForm(monitor, expected, walk.id);
            assertNull(com.nextgis.maplibui.util.WalkSessionStore.load(app));
        } finally { instrumentation.removeMonitor(monitor); }
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
