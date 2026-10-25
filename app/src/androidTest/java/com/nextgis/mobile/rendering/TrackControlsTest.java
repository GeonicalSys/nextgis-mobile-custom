package com.nextgis.mobile.rendering;

import android.content.ContentUris;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.display.SimpleMarkerStyle;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.TracksActivity;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.TrackActions;
import com.nextgis.maplibui.util.TrackView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.sources.GeoJsonSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static com.nextgis.maplib.map.TrackLayer.*;
import static org.junit.Assert.*;

/** Actual shared provider, native style/source and gestures in the opt-in verification application. */
@RunWith(AndroidJUnit4.class)
public class TrackControlsTest {
    private static void await(BooleanSupplier condition) throws Exception {
        long end = SystemClock.uptimeMillis() + 30000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(60);
        assertTrue("Track UI/native source did not settle", condition.getAsBoolean());
    }

    static class Tracks implements AutoCloseable {
        final VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture();
        final List<Long> added = new ArrayList<>();
        final TrackLayer layer;
        Tracks() throws Exception {
            layer = (TrackLayer) MapContentProviderHelper.getVectorLayerByPath(f.app.getMap(), TABLE_TRACKS);
            assertNotNull(layer);
            f.screen.onActivity(activity -> {
                ((MapDrawable) f.app.getMap()).setVisible(true);
                layer.setVisible(true);
                ((MapDrawable) f.app.getMap()).checkLayerVisibility(layer.getId());
            });
        }
        MapDrawable map() { return (MapDrawable) f.app.getMap(); }
        long add(String name, Integer color, double y) {
            ContentValues row = new ContentValues();
            row.put(FIELD_NAME, name); row.put(FIELD_START, 100); row.put(FIELD_END, 900); row.put(FIELD_VISIBLE, 1);
            if (color != null) row.put(FIELD_COLOR, color);
            long id = ContentUris.parseId(f.app.getContentResolver().insert(TrackActions.uri(f.app), row)); added.add(id);
            for (int segment = 0; segment < 2; segment++) for (int x : new int[]{-300, 300}) {
                ContentValues point = new ContentValues(); point.put(FIELD_SESSION, id);
                point.put(FIELD_LON, x); point.put(FIELD_LAT, y + segment * 500);
                point.put(FIELD_TIMESTAMP, 200 + segment * 100 + (x > 0 ? 1 : 0));
                point.put(FIELD_SENT, 1); point.put(FIELD_SEGMENT, segment);
                assertNotNull(f.app.getContentResolver().insert(android.net.Uri.parse(
                        "content://" + f.app.getAuthority() + "/trackpoints"), point));
            }
            return id;
        }
        void source(long id, boolean present) throws Exception {
            f.screen.onActivity(activity -> { map().reloadTrackListToMap(); map().checkLayerVisibility(layer.getId()); });
            AtomicBoolean state = new AtomicBoolean();
            await(() -> {
                f.screen.onActivity(activity -> {
                    if (map().getMaplibreMap() == null || map().getMaplibreMap().getStyle() == null) return;
                    GeoJsonSource source = map().getMaplibreMap().getStyle().getSourceAs("track-line-source");
                    if (source == null) return;
                    boolean found = false;
                    for (org.maplibre.geojson.Feature feature : source.querySourceFeatures(null))
                        if (feature.hasProperty(TrackMapStyle.TRACK_ID)
                                && feature.getNumberProperty(TrackMapStyle.TRACK_ID).longValue() == id) found = true;
                    state.set(found == present);
                }); return state.get();
            });
            f.renderedFrame();
        }
        PointF point(double y) {
            AtomicReference<PointF> point = new AtomicReference<>();
            f.screen.onActivity(activity -> point.set(map().getMaplibreMap().getProjection()
                    .toScreenLocation(new org.maplibre.android.geometry.LatLng(
                            Math.toDegrees(2 * Math.atan(Math.exp(y / 6378137.0)) - Math.PI / 2), 0))));
            return point.get();
        }
        void tap(double y) {
            PointF point = point(y);
            f.screen.onActivity(activity -> {
                View view = map().getMaplibreMapView(); long time = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, point.x, point.y, 0);
                MotionEvent up = MotionEvent.obtain(time, time + 70, MotionEvent.ACTION_UP, point.x, point.y, 0);
                try { view.dispatchTouchEvent(down); view.dispatchTouchEvent(up); }
                finally { down.recycle(); up.recycle(); }
            });
        }
        boolean panel() {
            AtomicBoolean shown = new AtomicBoolean();
            f.screen.onActivity(activity -> shown.set(activity.findViewById(
                    com.nextgis.mobile.R.id.track_selection_panel).getVisibility() == View.VISIBLE));
            return shown.get();
        }
        void assertNativeColor(long id, int color) throws Exception {
            AtomicBoolean matches = new AtomicBoolean();
            String expected = String.format(java.util.Locale.ROOT, "#%06X", color & 0xffffff);
            await(() -> {
                f.screen.onActivity(activity -> {
                    GeoJsonSource source = map().getMaplibreMap().getStyle().getSourceAs("track-line-source");
                    for (org.maplibre.geojson.Feature feature : source.querySourceFeatures(null))
                        if (feature.getNumberProperty(TrackMapStyle.TRACK_ID).longValue() == id)
                            matches.set(expected.equals(feature.getStringProperty(TrackMapStyle.COLOR)));
                }); return matches.get();
            });
        }
        @Override public void close() {
            for (long id : added) f.app.getContentResolver().delete(TrackActions.uri(f.app), FIELD_ID + "=?",
                    new String[]{Long.toString(id)});
            if (f.screen.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED)
                f.screen = ActivityScenario.launch(com.nextgis.mobile.activity.MainActivity.class);
            f.close();
        }
    }

    static final class Ui {
        static boolean configured;
        static android.app.UiAutomation automation() {
            android.app.UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            if (configured) return ui;
            android.accessibilityservice.AccessibilityServiceInfo info = ui.getServiceInfo();
            info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            ui.setServiceInfo(info); configured = true; return ui;
        }
        static android.view.accessibility.AccessibilityNodeInfo find(java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> matches) {
            for (android.view.accessibility.AccessibilityWindowInfo window : automation().getWindows()) {
                if (!window.isActive() && !window.isFocused()) continue;
                android.view.accessibility.AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                // Never interact with another application or the system keyboard.
                if (!String.valueOf(root.getPackageName()).equals(InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName())) { root.recycle(); continue; }
                android.view.accessibility.AccessibilityNodeInfo result = search(root, matches); root.recycle();
                if (result != null) return result;
            }
            return null;
        }
        static android.view.accessibility.AccessibilityNodeInfo search(android.view.accessibility.AccessibilityNodeInfo node,
                java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> matches) {
            if (node.isVisibleToUser() && matches.test(node)) return android.view.accessibility.AccessibilityNodeInfo.obtain(node);
            for (int i=0; i<node.getChildCount(); i++) {
                android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i); if (child == null) continue;
                android.view.accessibility.AccessibilityNodeInfo result = search(child, matches); child.recycle();
                if (result != null) return result;
            }
            return null;
        }
        static java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> id(int id) {
            String suffix = ":id/" + InstrumentationRegistry.getInstrumentation().getTargetContext().getResources().getResourceEntryName(id);
            return node -> node.getViewIdResourceName() != null && node.getViewIdResourceName().endsWith(suffix);
        }
        static void tap(int id) throws Exception { tap(id(id)); }
        static java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> textMatch(String text) {
            return node -> text.contentEquals(node.getText() == null ? "" : node.getText())
                    || text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription());
        }
        static void text(String text) throws Exception { tap(textMatch(text)); }
        static android.graphics.Rect touchBounds(android.view.accessibility.AccessibilityNodeInfo node) {
            android.graphics.Rect result = new android.graphics.Rect(); node.getBoundsInScreen(result);
            List<android.view.accessibility.AccessibilityWindowInfo> windows = automation().getWindows();
            android.view.accessibility.AccessibilityWindowInfo owner = null;
            for (android.view.accessibility.AccessibilityWindowInfo window : windows)
                if (window.getId() == node.getWindowId()) owner = window;
            if (owner == null || !owner.isActive() || !owner.isFocused()) return null;
            android.graphics.Rect area = new android.graphics.Rect(); owner.getBoundsInScreen(area);
            if (!result.intersect(area)) return null;
            for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                if (window.getId() == owner.getId() || window.getLayer() <= owner.getLayer()) continue;
                window.getBoundsInScreen(area);
                // Foreground notification heads-up windows can cover a still-focused toolbar.
                if (area.contains((int)result.exactCenterX(), (int)result.exactCenterY())) return null;
            }
            return result;
        }
        static void tap(java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> matches) throws Exception {
            AtomicReference<android.view.accessibility.AccessibilityNodeInfo> result = new AtomicReference<>();
            android.graphics.Rect[] previous = {null}; long[] stable = {0};
            try { await(() -> {
                if (result.get() != null) result.getAndSet(null).recycle();
                result.set(find(matches));
                if (result.get() == null) { previous[0] = null; return false; }
                android.graphics.Rect bounds = touchBounds(result.get());
                if (bounds == null) { previous[0] = null; return false; }
                long now = SystemClock.uptimeMillis();
                if (!bounds.equals(previous[0])) { previous[0] = bounds; stable[0] = now; }
                return !bounds.isEmpty() && now - stable[0] >= 150;
            }); }
            catch (AssertionError error) {
                StringBuilder detail = new StringBuilder();
                for (android.view.accessibility.AccessibilityWindowInfo window : automation().getWindows()) {
                    android.view.accessibility.AccessibilityNodeInfo root = window.getRoot();
                    if (root != null) { dump(root, detail); root.recycle(); }
                }
                throw new AssertionError(error.getMessage() + "\n" + detail, error);
            }
            android.view.accessibility.AccessibilityNodeInfo node = result.get();
            android.graphics.Rect bounds = touchBounds(node);
            assertNotNull("The touch must still target the focused, unobscured app window", bounds);
            assertTrue(node.isEnabled()); assertFalse(bounds.isEmpty()); node.recycle();
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0);
            MotionEvent up = MotionEvent.obtain(now,now+70,MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);
            try { InstrumentationRegistry.getInstrumentation().sendPointerSync(down); InstrumentationRegistry.getInstrumentation().sendPointerSync(up); }
            finally { down.recycle(); up.recycle(); }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        static void dump(android.view.accessibility.AccessibilityNodeInfo node, StringBuilder text) {
            if (!String.valueOf(node.getPackageName()).equals(InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName())) return;
            text.append("id=").append(node.getViewIdResourceName()).append(" text=").append(node.getText())
                    .append(" description=").append(node.getContentDescription()).append('\n');
            for (int i=0; i<node.getChildCount(); i++) {
                android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) { dump(child, text); child.recycle(); }
            }
        }
        static void name(String name) throws Exception {
            AtomicReference<android.view.accessibility.AccessibilityNodeInfo> target = new AtomicReference<>();
            await(() -> { target.set(find(id(R.id.track_name_input))); return target.get() != null; });
            android.view.accessibility.AccessibilityNodeInfo input = target.get();
            android.os.Bundle args = new android.os.Bundle();
            args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name);
            assertTrue(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)); input.recycle();
            closeKeyboard();
        }
        static void closeKeyboard() {
            for (android.view.accessibility.AccessibilityWindowInfo window : automation().getWindows())
                if (window.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    automation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); break;
                }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }

        static View overflow(View view) {
            if (!view.isShown()) return null;
            if (view.getClass().getSimpleName().equals("OverflowMenuButton")) return view;
            if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup)view;
                for(int i=0;i<group.getChildCount();i++) {
                    View found=overflow(group.getChildAt(i)); if(found!=null)return found;
                }
            }
            return null;
        }

        static void menuItem(ActivityScenario<? extends android.app.Activity> screen, int id, String title, boolean actionMode) throws Exception {
            AtomicBoolean ready=new AtomicBoolean(),direct=new AtomicBoolean();
            AtomicReference<String> overflowDescription=new AtomicReference<>();
            await(() -> {
                screen.onActivity(activity -> {
                    ready.set(false);
                    View root=actionMode ? activity.findViewById(androidx.appcompat.R.id.action_mode_bar)
                            : activity.getWindow().getDecorView();
                    if(root==null || !root.isShown() || !root.hasWindowFocus() || root.isLayoutRequested() || root.getWidth()==0)return;
                    View item=activity.findViewById(id);
                    direct.set(item!=null && item.isShown() && item.getWidth()>0);
                    if(direct.get()) { ready.set(true);return; }
                    View button=overflow(root);
                    if(button!=null && button.getWidth()>0 && button.getContentDescription()!=null) {
                        overflowDescription.set(button.getContentDescription().toString());ready.set(true);
                    }
                }); return ready.get();
            });
            if(direct.get())tap(id(id).or(textMatch(title)));
            else { text(overflowDescription.get());text(title); }
        }
    }

    private static void menu(ActivityScenario<TracksActivity> list, int id, int title) throws Exception {
        String text = InstrumentationRegistry.getInstrumentation().getTargetContext().getString(title);
        Ui.menuItem(list,id,text,true);
    }

    @Test public void listRenamePersistsWithoutChangingColorGeometryOrIdentity() throws Exception {
        try (Tracks t = new Tracks()) {
            long id = t.add("Исходный трек", null, 0), other = t.add("Другой трек", 0xff5f789c, 150);
            int color = TrackActions.find(t.f.app, id).color;
            t.source(id, true);
            try (ActivityScenario<TracksActivity> list = ActivityScenario.launch(
                    new android.content.Intent(t.f.app, TracksActivity.class))) {
                AtomicBoolean loaded = new AtomicBoolean();
                await(() -> { list.onActivity(activity -> loaded.set(((TrackView) activity.findViewById(R.id.lv_tracks)).getCount() == 2)); return loaded.get(); });
                Ui.text("Исходный трек");
                AtomicBoolean selected = new AtomicBoolean();
                await(() -> { list.onActivity(activity -> selected.set(((TrackView) activity.findViewById(R.id.lv_tracks)).getSelectedItemsCount() == 1)); return selected.get(); });
                menu(list, R.id.menu_track_rename, R.string.track_rename);
                Ui.name("   "); Ui.tap(android.R.id.button1);
                assertEquals("Исходный трек", TrackActions.find(t.f.app, id).name);
                Ui.name("  Путь к участку  "); Ui.tap(android.R.id.button1);
                assertEquals("Путь к участку", TrackActions.find(t.f.app, id).name);
                menu(list, R.id.menu_track_rename, R.string.track_rename);
                Ui.name("Не сохранять"); Ui.tap(android.R.id.button2);
            }
            assertEquals("Путь к участку", TrackActions.find(t.f.app, id).name);
            assertEquals(color, TrackActions.find(t.f.app, id).color);
            assertEquals(0xff5f789c, TrackActions.find(t.f.app, other).color);
            assertEquals(2, t.layer.getTracks().get((int) id).size());
            // A secondary ActivityScenario closes the previous launcher scenario. Reopen the
            // real screen instead of retaining a destroyed scenario's Activity reference.
            t.f.screen = ActivityScenario.launch(com.nextgis.mobile.activity.MainActivity.class);
            t.source(id, true); t.assertNativeColor(id, color);
        }
    }

    @Test public void mapTapSupportsColorRenameHideRestoreOverlapsAndConfirmedDelete() throws Exception {
        try (Tracks t = new Tracks()) {
            long id = t.add("Поход", 0xffff1744, 0); t.source(id, true);
            t.tap(0); await(t::panel);
            Ui.tap(R.id.track_panel_color);
            Ui.tap(node -> node.getViewIdResourceName() != null && node.getViewIdResourceName().endsWith("/ambilwarna_viewSatBri"));
            Ui.tap(android.R.id.button1);
            int changed = TrackActions.find(t.f.app, id).color;
            assertNotEquals(0xffff1744, changed); t.assertNativeColor(id, changed);
            Ui.tap(R.id.track_panel_rename); Ui.name("Участок у реки"); Ui.tap(android.R.id.button1);
            Ui.tap(R.id.track_panel_visibility);
            assertFalse(TrackActions.find(t.f.app, id).visible); t.source(id, false); assertTrue(t.panel());
            t.f.screen.recreate(); await(t::panel);
            Ui.tap(R.id.track_panel_visibility);
            assertTrue(TrackActions.find(t.f.app, id).visible); t.source(id, true);
            long overlap = t.add("Параллельный проход", 0xff00b8d4, 0); t.source(overlap, true);
            t.tap(0); Ui.text("Параллельный проход");
            Ui.tap(R.id.track_panel_delete); Ui.tap(android.R.id.button2); assertNotNull(TrackActions.find(t.f.app, overlap));
            Ui.tap(R.id.track_panel_delete); Ui.tap(android.R.id.button1);
            await(() -> TrackActions.find(t.f.app, overlap) == null); await(() -> !t.panel());
            assertNotNull(TrackActions.find(t.f.app, id)); assertEquals(changed, TrackActions.find(t.f.app, id).color);
            t.tap(0); await(t::panel);
            t.f.screen.onActivity(activity -> activity.getMapFragment().setNewMode(com.nextgis.mobile.fragment.MapFragment.MODE_AZIMUTH_POINTS));
            assertFalse(t.panel()); t.tap(0); assertFalse(t.panel());
        }
    }

    @Test public void nativeGlowAndPointGroupsStayAboveTracksAfterLiteFullAndReorder() throws Exception {
        try (Tracks t = new Tracks()) {
            long id = t.add("Красный", 0xffff1744, 0); t.source(id, true);
            t.f.screen.onActivity(activity -> {
                for (String prefix : new String[]{"track-line", "track-inprogress"}) {
                    LineLayer line = t.map().getMaplibreMap().getStyle().getLayerAs(prefix + "-layer");
                    LineLayer glow = t.map().getMaplibreMap().getStyle().getLayerAs(prefix + "-glow");
                    assertEquals(1.4f, line.getLineWidth().getValue(), .001f);
                    assertEquals(6.5f, glow.getLineWidth().getValue(), .001f);
                    assertEquals(3f, glow.getLineBlur().getValue(), .001f);
                    assertEquals(.83f, glow.getLineOpacity().getValue(), .001f);
                }
            });
            PointF center = t.point(0); Bitmap image = t.f.snapshot();
            int core = image.getPixel(Math.round(center.x), Math.round(center.y));
            assertTrue("The native core is a darker shade of the saved red",
                    Color.red(core) < 160 && Color.red(core) > Color.green(core) + 20 && Color.green(core) < 120);
            int colored = 0;
            for (int dy = -30; dy <= 30; dy++) {
                int pixel = image.getPixel(Math.round(center.x), Math.round(center.y) + dy);
                if (Color.red(pixel) > Color.green(pixel) + 15) colored++;
            }
            float density = t.f.app.getResources().getDisplayMetrics().density;
            assertTrue("Glow extends outside the selected 1.4-dp core", colored > 1.4f * density);
            assertTrue("Glow stays within the selected width and its soft edges", colored < 12.5f * density);
            save(t, image, "track-glow");
            image.recycle();
            VectorLayerUI fieldLines = t.f.add(GeoConstants.GTLineString), ordinaryLines = t.f.add(GeoConstants.GTLineString);
            for (VectorLayerUI line : new VectorLayerUI[]{fieldLines, ordinaryLines}) {
                line.setName(line == fieldLines ? "Полевые линейные" : "Обычная линия");
                t.f.insert(line, 0, null);
                com.nextgis.maplib.display.SimpleLineStyle paint = new com.nextgis.maplib.display.SimpleLineStyle();
                paint.setColor(Color.BLUE); paint.setWidth(2); paint.setField("name"); paint.setTextSize(5);
                t.f.bind(line, paint, null);
            }
            VectorLayerUI lower = t.f.add(GeoConstants.GTPoint), upper = t.f.add(GeoConstants.GTMultiPoint);
            for (VectorLayerUI point : new VectorLayerUI[]{lower, upper}) {
                t.f.insert(point, 0, null);
                SimpleMarkerStyle marker = new SimpleMarkerStyle(Color.GREEN, Color.GREEN, 12, SimpleMarkerStyle.MarkerStyleFirefly);
                marker.setField("name"); marker.setTextSize(5); marker.setWidth(14);
                t.f.bind(point, marker, null);
            }
            for (int pass = 0; pass < 3; pass++) {
                final int step = pass;
                t.f.screen.onActivity(activity -> {
                    if (step == 2) t.map().moveLayer(t.map().getLayerCount() - 1, lower);
                    if (step == 1) activity.getMapFragment().reloadMapStyleAndLayersAfterLayerFillBatch();
                    else activity.getMapFragment().loadLayersLite();
                });
                t.source(id, true);
                t.f.screen.onActivity(activity -> {
                    List<Layer> layers = t.map().getMaplibreMap().getStyle().getLayers();
                    int track = index(layers, "track-inprogress-layer"), tool = index(layers, "selected-dot-layer");
                    int fieldBody = index(layers, "nglayer-layer-" + fieldLines.getId());
                    int fieldLabel = index(layers, "symbol-nglayer-layer-" + fieldLines.getId());
                    assertTrue("Field line body above tracks", fieldBody > track);
                    assertTrue("Field line labels above tracks", fieldLabel > track);
                    assertTrue("Ordinary lines remain below tracks", index(layers, "nglayer-layer-" + ordinaryLines.getId()) < track);
                    for (VectorLayerUI point : new VectorLayerUI[]{lower, upper}) {
                        int body = index(layers, "nglayer-layer-" + point.getId());
                        int label = index(layers, "symbol-nglayer-layer-" + point.getId());
                        assertTrue("Point bodies above tracks", body > track); assertTrue(label > track);
                        assertTrue("Points stay above field line body and labels", body > fieldBody && body > fieldLabel);
                        assertTrue("Point labels stay below tools", label < tool);
                    }
                    boolean lowerFirst = t.map().getLayers().indexOf(lower) < t.map().getLayers().indexOf(upper);
                    assertEquals("Native point order follows the layer list", lowerFirst,
                            index(layers, "nglayer-layer-" + lower.getId()) < index(layers, "nglayer-layer-" + upper.getId()));
                    PointF hit = t.map().getMaplibreMap().getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(0, 0));
                    assertTrue("Point drawing priority preserves the track as a hit candidate",
                            t.map().queryTrackIds(hit.x, hit.y, 8).contains(id));
                });
                Bitmap points = t.f.snapshot(); PointF p = t.point(0);
                assertEquals("Native green point covers the red track", Color.GREEN, points.getPixel(Math.round(p.x), Math.round(p.y)));
                save(t, points, "track-points-" + pass);
                points.recycle();
            }
        }
    }

    @Test public void crossingTracksPointsLinesAndPolygonsShareOneChooserInBothSelectionDirections() throws Exception {
        try (Tracks t = new Tracks()) {
            long first = t.add("Первый маршрут", 0xffff1744, 0), second = t.add("Второй маршрут", 0xff00b8d4, 0);
            VectorLayerUI point = t.f.add(GeoConstants.GTPoint), line = t.f.add(GeoConstants.GTLineString),
                    polygon = t.f.add(GeoConstants.GTPolygon);
            VectorLayerUI[] objects = {point, line, polygon};
            String[] names = {"Полевые точки", "Полевые линейные", "Площадной объект"};
            for (int i = 0; i < objects.length; i++) {
                VectorLayerUI layer = objects[i]; layer.setName(names[i]); t.f.insert(layer, 0, null);
                com.nextgis.maplib.display.Style paint;
                if (layer == point) paint = new SimpleMarkerStyle(Color.GREEN, Color.GREEN, 12, SimpleMarkerStyle.MarkerStyleCircle);
                else if (layer == line) paint = new com.nextgis.maplib.display.SimpleLineStyle();
                else paint = new com.nextgis.maplib.display.SimplePolygonStyle();
                ((com.nextgis.maplib.api.ITextStyle) paint).setField("name"); t.f.bind(layer, paint, null);
            }
            t.f.screen.onActivity(activity -> activity.getMapFragment().loadLayersLite());
            t.source(first, true); t.source(second, true);
            for (int i = 0; i < 5; i++) {
                t.tap(0);
                AtomicBoolean complete = new AtomicBoolean();
                await(() -> { t.f.screen.onActivity(activity -> {
                    try {
                        java.lang.reflect.Field field = activity.getMapFragment().getClass().getDeclaredField("mapSelectionDialog");
                        field.setAccessible(true);
                        androidx.appcompat.app.AlertDialog chooser = (androidx.appcompat.app.AlertDialog) field.get(activity.getMapFragment());
                        complete.set(chooser != null && chooser.isShowing() && chooser.getListView().getAdapter().getCount() == 5);
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                }); return complete.get(); });
                for (String name : names) {
                    AtomicReference<android.view.accessibility.AccessibilityNodeInfo> found = new AtomicReference<>();
                    await(() -> { found.set(Ui.find(node -> node.getText() != null && node.getText().toString().startsWith(name + ":")));
                        if (found.get() == null) return false; found.get().recycle(); return true; });
                }
                for (String trackName : new String[]{"Трек: Первый маршрут", "Трек: Второй маршрут"}) {
                    android.view.accessibility.AccessibilityNodeInfo row = Ui.find(Ui.textMatch(trackName));
                    assertNotNull("Track is present once beside all vector types", row); row.recycle();
                }
                if (i < 3) {
                    final int which = i;
                    Ui.tap(node -> node.getText() != null && node.getText().toString().startsWith(names[which] + ":"));
                    AtomicBoolean chosen = new AtomicBoolean();
                    await(() -> { t.f.screen.onActivity(activity -> chosen.set(activity.getMapFragment().getSelectedLayer() == objects[which]));
                        return chosen.get(); });
                    assertFalse("Selecting an object closes the track panel", t.panel());
                } else { Ui.text(i == 3 ? "Трек: Первый маршрут" : "Трек: Второй маршрут"); await(t::panel); }
            }
            TrackActions.visibility(t.f.app, second, false, () -> {}); t.source(second, false);
            t.f.screen.onActivity(activity -> { for (VectorLayerUI layer : objects) {
                layer.setVisible(false); t.map().checkLayerVisibility(layer.getId());
            }});
            t.tap(0); await(t::panel); // One remaining track is selected directly.
            TrackActions.visibility(t.f.app, first, false, () -> {}); t.source(first, false);
            t.f.screen.onActivity(activity -> { point.setVisible(true); t.map().checkLayerVisibility(point.getId()); });
            t.tap(0);
            AtomicBoolean chosen = new AtomicBoolean();
            await(() -> { t.f.screen.onActivity(activity -> chosen.set(activity.getMapFragment().getSelectedLayer() == point));
                return chosen.get(); });
            assertFalse("A lone vector object is selected directly", t.panel());
        }
    }

    @Test public void mixedCandidateDialogKeepsLongRowsScrollableAndCancelVisibleAtLargeScales() throws Exception {
        try (Tracks t = new Tracks()) {
            t.f.screen.onActivity(activity -> {
                for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1f, 1.3f, 2f})
                    for (int[] screen : new int[][]{{360,640},{640,360},{320,568},{568,320},{280,500},{500,280}}) {
                        android.content.res.Configuration config = new android.content.res.Configuration(activity.getResources().getConfiguration());
                        config.setLocale(new java.util.Locale("ru")); config.fontScale = scale;
                        config.screenWidthDp = screen[0]; config.screenHeightDp = screen[1];
                        config.smallestScreenWidthDp = Math.min(screen[0], screen[1]);
                        config.orientation = screen[0] > screen[1] ? android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                : android.content.res.Configuration.ORIENTATION_PORTRAIT;
                        androidx.appcompat.view.ContextThemeWrapper context = new androidx.appcompat.view.ContextThemeWrapper(activity,
                                dark ? com.nextgis.mobile.R.style.AppTheme_Dark : com.nextgis.mobile.R.style.AppTheme);
                        context.applyOverrideConfiguration(config);
                        List<String> labels = new ArrayList<>();
                        for (int i = 0; i < 9; i++) labels.add((i % 2 == 0 ? "Трек: " : "Полевые линейные: ")
                                + "Длинное название объекта у дальней границы участка " + i);
                        java.util.concurrent.atomic.AtomicInteger selected = new java.util.concurrent.atomic.AtomicInteger(-1);
                        androidx.appcompat.app.AlertDialog dialog = com.nextgis.mobile.util.MapSelectionDialog.create(context,
                                com.nextgis.mobile.R.string.choose_object, labels, selected::set);
                        try {
                            dialog.show(); View root = dialog.getWindow().getDecorView();
                            float density = context.getResources().getDisplayMetrics().density;
                            int width = Math.round(screen[0] * density), height = Math.round(screen[1] * density);
                            for (int pass = 0; pass < 3; pass++) {
                                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
                                root.layout(0,0,width,root.getMeasuredHeight());
                            }
                            assertTrue(root.getHeight() <= height);
                            android.widget.ListView list = dialog.getListView();
                            assertEquals(labels.size(), list.getAdapter().getCount());
                            assertTrue(list.getHeight() > 0);
                            for (int i = 0; i < labels.size(); i++) {
                                android.widget.TextView row = (android.widget.TextView) list.getAdapter().getView(i, null, list);
                                row.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,list.getWidth()), View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                                row.layout(0,0,row.getMeasuredWidth(),row.getMeasuredHeight());
                                assertEquals(labels.get(i), row.getText().toString());
                                for (int lineIndex = 0; lineIndex < row.getLayout().getLineCount(); lineIndex++) {
                                    assertEquals(0, row.getLayout().getEllipsisCount(lineIndex));
                                    assertTrue(row.getLayout().getLineMax(lineIndex) <= row.getLayout().getWidth() + 1);
                                }
                                assertTrue(row.getLayout().getHeight() <= row.getHeight() - row.getCompoundPaddingTop() - row.getCompoundPaddingBottom());
                            }
                            View cancel = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
                            android.graphics.Rect box = new android.graphics.Rect(0,0,cancel.getWidth(),cancel.getHeight());
                            ((android.view.ViewGroup) root).offsetDescendantRectToMyCoords(cancel,box);
                            assertTrue("Cancel stays inside viewport", box.top >= 0 && box.bottom <= root.getHeight());
                            assertEquals(-1, selected.get());
                            cancel.performClick();
                            assertEquals("Cancel never chooses an object", -1, selected.get());
                        } finally { dialog.dismiss(); }
                    }
            });
        }
    }

    @Test public void trackDialogsFitRussianThemesFontsAndNativePortraitLandscapeWindows() throws Exception {
        try (Tracks t = new Tracks()) {
            long id = t.add("Длинное название маршрута к дальней точке и обратно", null, 0);
            AtomicReference<Integer> oldOrientation = new AtomicReference<>();
            t.f.screen.onActivity(activity -> oldOrientation.set(activity.getRequestedOrientation()));
            try {
                for (int[] screen : new int[][]{{320,568,540},{280,500,620},{568,320,540},{500,280,620}}) {
                    boolean landscape = screen[0] > screen[1];
                    t.f.screen.onActivity(activity -> activity.setRequestedOrientation(landscape
                            ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                    AtomicBoolean oriented = new AtomicBoolean();
                    await(() -> { t.f.screen.onActivity(activity -> oriented.set(
                            (activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) == landscape)); return oriented.get(); });
                    for (boolean dark : new boolean[]{false, true}) for (float font : new float[]{1f,1.3f,2f}) {
                        for (int action = 0; action < 3; action++) {
                            int kind = action;
                            AtomicReference<android.app.Dialog> dialog = new AtomicReference<>();
                            t.f.screen.onActivity(activity -> {
                                android.content.res.Configuration config = new android.content.res.Configuration(activity.getResources().getConfiguration());
                                config.setLocale(new java.util.Locale("ru")); config.fontScale = font;
                                config.densityDpi = screen[2]; config.screenWidthDp = screen[0]; config.screenHeightDp = screen[1];
                                androidx.appcompat.view.ContextThemeWrapper context = new androidx.appcompat.view.ContextThemeWrapper(activity,
                                        dark ? com.nextgis.mobile.R.style.AppTheme_Dark : com.nextgis.mobile.R.style.AppTheme);
                                context.applyOverrideConfiguration(config);
                                if (kind == 0) dialog.set(TrackActions.rename(context, id, () -> fail("Cancel must not save")));
                                else if (kind == 1) dialog.set(TrackActions.color(context, new String[]{Long.toString(id)}, () -> fail("Cancel must not recolor")));
                                else dialog.set(TrackActions.delete(context, new String[]{Long.toString(id)}, () -> fail("Cancel must not delete")));
                                android.graphics.Rect usable = new android.graphics.Rect();
                                activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(usable);
                                float density = context.getResources().getDisplayMetrics().density;
                                dialog.get().getWindow().setLayout(Math.min(usable.width(), Math.round(screen[0] * density)), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                            });
                            try {
                                // Wait on actual native window accessibility, rather than assuming show() completed layout.
                                android.view.accessibility.AccessibilityNodeInfo[] button = {null};
                                await(() -> { button[0] = Ui.find(Ui.id(android.R.id.button2));
                                    if (button[0] == null) return false; button[0].recycle(); return true; });
                                Ui.closeKeyboard();
                                AtomicBoolean fitted = new AtomicBoolean();
                                await(() -> { t.f.screen.onActivity(activity -> {
                                    View root = dialog.get().getWindow().getDecorView();
                                    fitted.set(root.hasWindowFocus() && root.getWidth() > 0 && !root.isLayoutRequested());
                                }); return fitted.get(); });
                                t.f.screen.onActivity(activity -> com.nextgis.mobile.reliability.LargeTextLayoutTest.assertTrackDialogFits(dialog.get()));
                                NativeStyleTouch.tap(t.f, () -> dialog.get().findViewById(android.R.id.button2));
                                assertFalse(dialog.get().isShowing());
                                assertNotNull(TrackActions.find(t.f.app, id));
                            } finally { t.f.screen.onActivity(activity -> { if (dialog.get().isShowing()) dialog.get().dismiss(); }); }
                        }
                    }
                }
            } finally { t.f.screen.onActivity(activity -> activity.setRequestedOrientation(oldOrientation.get())); }
        }
    }

    private static int index(List<Layer> layers, String id) {
        for (int i = 0; i < layers.size(); i++) if (id.equals(layers.get(i).getId())) return i;
        throw new AssertionError("Missing native layer " + id);
    }

    private static void save(Tracks tracks, Bitmap bitmap, String name) throws Exception {
        java.io.File directory = new java.io.File(tracks.f.app.getFilesDir(), "large-text-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }
}
