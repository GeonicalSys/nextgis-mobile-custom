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

    private static class Tracks implements AutoCloseable {
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

    private static final class Ui {
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
        static void tap(java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> matches) throws Exception {
            AtomicReference<android.view.accessibility.AccessibilityNodeInfo> result = new AtomicReference<>();
            android.graphics.Rect[] previous = {null}; long[] stable = {0};
            try { await(() -> {
                if (result.get() != null) result.getAndSet(null).recycle();
                result.set(find(matches));
                if (result.get() == null) { previous[0] = null; return false; }
                android.graphics.Rect bounds = new android.graphics.Rect(); result.get().getBoundsInScreen(bounds);
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
            android.graphics.Rect bounds = new android.graphics.Rect(); node.getBoundsInScreen(bounds);
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
    }

    private static void menu(ActivityScenario<TracksActivity> list, int id, int title) throws Exception {
        String text = InstrumentationRegistry.getInstrumentation().getTargetContext().getString(title);
        java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> target = Ui.id(id).or(Ui.textMatch(text));
        android.view.accessibility.AccessibilityNodeInfo item = Ui.find(target);
        if (item != null) { item.recycle(); Ui.tap(target); }
        else {
            Ui.text(InstrumentationRegistry.getInstrumentation().getTargetContext().getString(androidx.appcompat.R.string.abc_action_menu_overflow_description));
            Ui.text(text);
        }
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
            PointF center = t.point(0); Bitmap image = t.f.snapshot();
            int core = image.getPixel(Math.round(center.x), Math.round(center.y));
            assertTrue("The native core uses the saved red", Color.red(core) > 220 && Color.green(core) < 70);
            int colored = 0;
            for (int dy = -30; dy <= 30; dy++) {
                int pixel = image.getPixel(Math.round(center.x), Math.round(center.y) + dy);
                if (Color.red(pixel) > Color.green(pixel) + 15) colored++;
            }
            assertTrue("Glow extends outside the crisp four-dp core", colored > 4 * t.f.app.getResources().getDisplayMetrics().density);
            save(t, image, "track-glow");
            image.recycle();
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
                    for (VectorLayerUI point : new VectorLayerUI[]{lower, upper}) {
                        int body = index(layers, "nglayer-layer-" + point.getId());
                        int label = index(layers, "symbol-nglayer-layer-" + point.getId());
                        assertTrue("Point bodies above tracks", body > track); assertTrue(label > track);
                        assertTrue("Point labels stay below tools", label < tool);
                    }
                    boolean lowerFirst = t.map().getLayers().indexOf(lower) < t.map().getLayers().indexOf(upper);
                    assertEquals("Native point order follows the layer list", lowerFirst,
                            index(layers, "nglayer-layer-" + lower.getId()) < index(layers, "nglayer-layer-" + upper.getId()));
                    PointF hit = t.map().getMaplibreMap().getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(0, 0));
                    assertTrue("A point intercepts a track hit", t.map().queryTrackIds(hit.x, hit.y, 8).isEmpty());
                });
                Bitmap points = t.f.snapshot(); PointF p = t.point(0);
                assertEquals("Native green point covers the red track", Color.GREEN, points.getPixel(Math.round(p.x), Math.round(p.y)));
                save(t, points, "track-points-" + pass);
                points.recycle();
            }
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
                                Ui.tap(android.R.id.button2);
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
