package com.nextgis.mobile.rendering;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.GeoLinearRing;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.datasource.GeoPolygon;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.map.MPLFeaturesUtils;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.style.sources.GeoJsonSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Complete SQLite/native input and production notice, using small isolated technical geometry. */
@RunWith(AndroidJUnit4.class)
public class RenderMemorySafetyTest {
    private static final int ROWS = 129, VERTICES = 64;

    @Test public void cacheEconomyKeepsCompleteGeometryAndWarnsOnceAcrossRecreation() throws Exception {
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            VectorLayerUI first = corpus(f);
            byte[] before = contents(first);
            f.screen.onActivity(activity -> ((MapDrawable) f.app.getMap()).getRenderMemoryGuard().noteReducedCache());
            assertExactBinding(f, first, Color.GREEN);
            awaitCompleteNativeIds(f, first);
            assertOriginalGeometry(first);
            assertArrayEquals(before, contents(first));
            assertTrue(first.isVisible());
            assertTrue(((MapDrawable) f.app.getMap()).getRenderMemoryGuard().restrictions().isEmpty());

            AtomicReference<AlertDialog> automatic = new AtomicReference<>();
            await(() -> {
                f.screen.onActivity(activity -> {
                    automatic.set(notice(activity.getMapFragment()));
                });
                return automatic.get() != null && automatic.get().isShowing();
            });
            f.screen.onActivity(activity -> {
                assertTrue(automatic.get().getButton(AlertDialog.BUTTON_POSITIVE).isShown());
                automatic.get().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNoAutomaticNotice(f);
            f.screen.onActivity(activity -> assertTrue(((MapDrawable) f.app.getMap()).save()));
            f.screen.recreate();
            await(() -> {
                AtomicBoolean ready = new AtomicBoolean();
                f.screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) f.app.getMap();
                    ready.set(map.getMaplibreMap() != null && map.getMaplibreMap().getStyle() != null
                            && map.getMaplibreMapView() == activity.findViewById(com.nextgis.mobile.R.id.mapViewMaplibre));
                });
                return ready.get();
            });
            assertNoAutomaticNotice(f);
            // Rebind this technical source to the recreated native style through the same
            // production conversion/source creation route used by the style matrix.
            assertExactBinding(f, first, Color.GREEN);
            awaitCompleteNativeIds(f, first);
            assertArrayEquals(before, contents(first));

            VectorLayerUI second = corpus(f);
            byte[] secondBefore = contents(second);
            assertExactBinding(f, second, Color.CYAN);
            awaitCompleteNativeIds(f, second);
            f.screen.onActivity(activity -> ((MapDrawable) f.app.getMap()).getRenderMemoryGuard().noteSerializedTiles());
            assertNoAutomaticNotice(f);
            assertTrue(first.isVisible()); assertTrue(second.isVisible());

            // Manual status remains available after the single automatic session notice.
            // This bounded dialog component matrix supplements, not replaces, Full real windows.
            assertRussianDialogMatrix(f);
            assertNoAutomaticNotice(f);
            assertArrayEquals(before, contents(first)); assertArrayEquals(secondBefore, contents(second));
            assertOriginalGeometry(first); assertOriginalGeometry(second);
        }
    }

    private static void assertNoAutomaticNotice(VectorStyleRenderingTest.Fixture f) {
        f.screen.onActivity(activity -> {
            activity.getMapFragment().onRenderMemoryWarningChanged();
            AlertDialog shown = notice(activity.getMapFragment());
            assertTrue("No automatic repeat after another callback/layer/recreation", shown == null || !shown.isShowing());
        });
    }

    private static void awaitCompleteNativeIds(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) throws Exception {
        Set<Long> expected = new HashSet<>(); for (long id = 1; id <= ROWS; id++) expected.add(id);
        AtomicReference<Set<Long>> actual = new AtomicReference<>(new HashSet<>());
        await(() -> {
            f.screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) f.app.getMap();
                GeoJsonSource source = map.getMaplibreMap().getStyle().getSourceAs(layer.getPath().toString());
                Set<Long> ids = new HashSet<>();
                if (source != null) for (org.maplibre.geojson.Feature feature : source.querySourceFeatures(null)) {
                    assertNotNull(feature.geometry());
                    assertTrue(feature.geometry() instanceof org.maplibre.geojson.Polygon);
                    ids.add(feature.getProperty(MPLFeaturesUtils.prop_featureid).getAsLong());
                }
                actual.set(ids);
            });
            return actual.get().equals(expected);
        });
        assertEquals("Every independently expected source identity reached native tiles", expected, actual.get());
    }

    private static void assertExactBinding(VectorStyleRenderingTest.Fixture fixture,
                                           VectorLayerUI layer, int color) throws Exception {
        List<org.maplibre.geojson.Feature> rich = fixture.bind(layer,
                new SimplePolygonStyle(color, Color.BLUE), null);
        assertExactInput(rich);
        // Check the actual compact projection independently of the retained rich editor data.
        assertExactInput(MPLFeaturesUtils.nativeFeaturesForLayer(layer, GeoConstants.GTPolygon, rich));
    }

    /** Full coordinate lists before native tile quantization, checked against independent input. */
    private static void assertExactInput(List<org.maplibre.geojson.Feature> features) {
        assertEquals(ROWS, features.size());
        Map<Long, org.maplibre.geojson.Feature> byId = new HashMap<>();
        for (org.maplibre.geojson.Feature feature : features)
            assertNull("Duplicate input identity", byId.put(feature.getProperty(MPLFeaturesUtils.prop_featureid).getAsLong(), feature));
        for (long id = 1; id <= ROWS; id++) {
            org.maplibre.geojson.Feature feature = byId.get(id); assertNotNull("Missing input identity " + id, feature);
            List<List<org.maplibre.geojson.Point>> rings = ((org.maplibre.geojson.Polygon) feature.geometry()).coordinates();
            assertEquals(1, rings.size()); assertEquals(VERTICES + 1, rings.get(0).size());
            for (int i = 0; i <= VERTICES; i++) {
                double[] point = projected(id, i);
                double longitude = Math.toDegrees(point[0] / 6378137.0);
                double latitude = Math.toDegrees(2 * Math.atan(Math.exp(point[1] / 6378137.0)) - Math.PI / 2);
                assertEquals("Longitude id=" + id + " vertex=" + i, longitude, rings.get(0).get(i).longitude(), 1e-10);
                assertEquals("Latitude id=" + id + " vertex=" + i, latitude, rings.get(0).get(i).latitude(), 1e-10);
            }
        }
    }

    private static void assertOriginalGeometry(VectorLayerUI layer) {
        for (long id = 1; id <= ROWS; id++) {
            Feature feature = layer.getFeature(id); assertNotNull(feature);
            GeoLinearRing ring = ((GeoPolygon) feature.getGeometry()).getOuterRing();
            assertEquals(VERTICES + 1, ring.getPointCount());
            for (int i = 0; i <= VERTICES; i++) {
                double[] wanted = projected(id, i);
                assertEquals(wanted[0], ring.getPoint(i).getX(), 1e-9);
                assertEquals(wanted[1], ring.getPoint(i).getY(), 1e-9);
            }
        }
    }

    private static VectorLayerUI corpus(VectorStyleRenderingTest.Fixture f) throws Exception {
        VectorLayerUI layer = f.add(GeoConstants.GTPolygon);
        android.database.sqlite.SQLiteDatabase database = DatabaseContext.getDatabaseForLayer(layer, false);
        database.beginTransaction();
        try {
            for (int id = 1; id <= ROWS; id++) {
                GeoLinearRing ring = new GeoLinearRing(); ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                for (int vertex = 0; vertex <= VERTICES; vertex++) {
                    double[] xy = projected(id, vertex);
                    GeoPoint point = new GeoPoint(xy[0], xy[1]); point.setCRS(GeoConstants.CRS_WEB_MERCATOR); ring.add(point);
                }
                GeoPolygon polygon = new GeoPolygon(); polygon.setCRS(GeoConstants.CRS_WEB_MERCATOR); polygon.setOuterRing(ring);
                Feature feature = new Feature(id, layer.getFields()); feature.setGeometry(polygon);
                feature.setFieldValue("name", "Техническая фигура " + id); feature.setFieldValue("kind", "complete");
                layer.createFeatureBatch(feature, database, false);
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
        layer.rebuildCache(null); assertTrue(layer.save()); return layer;
    }

    private static double[] projected(long id, int vertex) {
        int index = (int) id - 1;
        double angle = 2 * Math.PI * (vertex % VERTICES) / VERTICES;
        return new double[]{-72 + index % 13 * 12 + 5 * Math.cos(angle), -48 + index / 13 * 12 + 5 * Math.sin(angle)};
    }

    private static byte[] contents(VectorLayerUI layer) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (Cursor rows = layer.query(null, null, null, "_id", null)) {
            assertNotNull(rows); assertEquals(ROWS, rows.getCount());
            for (String column : rows.getColumnNames()) hashPart(hash, column.getBytes(StandardCharsets.UTF_8));
            while (rows.moveToNext()) for (int column = 0; column < rows.getColumnCount(); column++) {
                int type = rows.getType(column); hash.update((byte) type);
                switch (type) {
                    case Cursor.FIELD_TYPE_NULL: hashPart(hash, null); break;
                    case Cursor.FIELD_TYPE_INTEGER: hashPart(hash, ByteBuffer.allocate(8).putLong(rows.getLong(column)).array()); break;
                    case Cursor.FIELD_TYPE_FLOAT: hashPart(hash, ByteBuffer.allocate(8).putLong(Double.doubleToRawLongBits(rows.getDouble(column))).array()); break;
                    case Cursor.FIELD_TYPE_BLOB: hashPart(hash, rows.getBlob(column)); break;
                    default: hashPart(hash, rows.getString(column).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return hash.digest();
    }
    private static void hashPart(MessageDigest hash, byte[] value) {
        hash.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length).array());
        if (value != null) hash.update(value);
    }

    private static void assertRussianDialogMatrix(VectorStyleRenderingTest.Fixture f) {
        for (boolean dark : new boolean[]{false, true}) for (float font : new float[]{1f, 2f})
            for (int[] screen : new int[][]{{280, 500}, {500, 280}}) {
                AtomicReference<Configuration> original = new AtomicReference<>();
                AtomicReference<Resources.Theme> theme = new AtomicReference<>();
                try {
                    f.screen.onActivity(activity -> {
                        Resources resources = activity.getResources();
                        original.set(new Configuration(resources.getConfiguration()));
                        Resources.Theme previous = resources.newTheme(); previous.setTo(activity.getTheme()); theme.set(previous);
                        Configuration configured = new Configuration(original.get()); configured.setLocale(new Locale("ru"));
                        configured.fontScale = font; configured.screenWidthDp = screen[0]; configured.screenHeightDp = screen[1];
                        configured.smallestScreenWidthDp = Math.min(screen[0], screen[1]);
                        configured.orientation = screen[0] > screen[1] ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
                        configured.uiMode = (configured.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                                | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                        resources.updateConfiguration(configured, resources.getDisplayMetrics());
                        activity.setTheme(dark ? com.nextgis.mobile.R.style.AppTheme_Dark : com.nextgis.mobile.R.style.AppTheme);
                        activity.getMapFragment().showRenderMemoryStatus();
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    f.screen.onActivity(activity -> {
                        AlertDialog dialog = notice(activity.getMapFragment()); assertNotNull(dialog); assertTrue(dialog.isShowing());
                        assertEquals("ru", dialog.getContext().getResources().getConfiguration().getLocales().get(0).getLanguage());
                        View decor = dialog.getWindow().getDecorView(); assertTrue(decor.isAttachedToWindow());
                        float density = dialog.getContext().getResources().getDisplayMetrics().density;
                        int width = Math.round(screen[0] * density), height = Math.round(screen[1] * density);
                        for (int pass = 0; pass < 3; pass++) {
                            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
                            decor.layout(0, 0, width, decor.getMeasuredHeight());
                        }
                        TextView message = decor.findViewById(android.R.id.message); assertNotNull(message);
                        assertTrue(message.getText().toString().contains(activity.getString(com.nextgis.mobile.R.string.render_memory_message)));
                        assertTrue(decor.getHeight() <= height); assertTextFits(decor);
                        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL}) {
                            View button = dialog.getButton(which); assertNotNull(button); assertTrue(button.isShown());
                            Rect bounds = new Rect(0, 0, button.getWidth(), button.getHeight());
                            ((ViewGroup) decor).offsetDescendantRectToMyCoords(button, bounds);
                            assertTrue("Complete button remains inside the dialog", bounds.left >= 0 && bounds.top >= 0
                                    && bounds.right <= decor.getWidth() && bounds.bottom <= decor.getHeight());
                        }
                        androidx.core.widget.NestedScrollView scroll = decor.findViewById(androidx.appcompat.R.id.scrollView);
                        assertNotNull(scroll); assertTrue("Warning owns a nonempty scroll viewport", scroll.getHeight() > 0);
                        if (scroll.getChildCount() > 0 && scroll.getChildAt(0).getHeight() > scroll.getHeight()) {
                            scroll.scrollTo(0, scroll.getChildAt(0).getHeight()); assertTrue(scroll.getScrollY() > 0);
                        }
                    });
                } finally {
                    f.screen.onActivity(activity -> {
                        AlertDialog shown = notice(activity.getMapFragment()); if (shown != null) shown.dismiss();
                        if (original.get() != null) activity.getResources().updateConfiguration(original.get(), activity.getResources().getDisplayMetrics());
                        if (theme.get() != null) activity.getTheme().setTo(theme.get());
                    });
                }
            }
    }
    private static void assertTextFits(View view) {
        if (view instanceof TextView && view.isShown()) {
            TextView text = (TextView) view;
            if (text.getText().length() > 0) {
                assertNotNull(text.getLayout());
                for (int line = 0; line < text.getLayout().getLineCount(); line++) {
                    assertEquals("No ellipsis in resource warning", 0, text.getLayout().getEllipsisCount(line));
                    assertTrue("Complete warning/action line fits", text.getLayout().getLineWidth(line)
                            <= text.getWidth() - text.getPaddingLeft() - text.getPaddingRight() + 2);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static AlertDialog notice(Object fragment) {
        try {
            java.lang.reflect.Field field = fragment.getClass().getDeclaredField("renderMemoryDialog");
            field.setAccessible(true); return (AlertDialog) field.get(fragment);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 30000;
        while (!condition.getAsBoolean() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
        assertTrue("Complete native source or resource notice did not become ready", condition.getAsBoolean());
    }
}
