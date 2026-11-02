package com.nextgis.mobile.rendering;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.*;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.mobile.reliability.EmulatorNetworkFixture;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.geojson.Point;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static com.nextgis.mobile.rendering.NativeSqliteVectorRenderingTest.*;

/** Technical rows only; actual published config bytes are inputs, never output expectations. */
@RunWith(AndroidJUnit4.class)
public class DirectVectorTilesTest {
    @Test public void oldPublishedConfigsUseDirectTilesWithoutChangingSchemaRowsOrRights() throws Exception {
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            for (String configName : new String[]{"kvartal", "vydel", "vertexfeature_recent"}) {
                JSONObject config = config(configName);
                assertEquals("local_vector_tiles", config.getString("mobile_render_mode"));
                boolean point = configName.equals("vertexfeature_recent");
                NGWVectorLayerUI layer = add(f, point, 40);
                byte[] before = contents(layer, 1);
                List<Field> schema = new ArrayList<>(layer.getFields());
                int geometry = layer.getGeometryType();
                f.screen.onActivity(activity -> {try {
                    layer.applyMobileConfigPreservingAuthoritativeSchema(config);
                    // LayerFillService applies provenance separately after the presentation config.
                    layer.setLayerOriginMetadata(LayerOriginMetadata.manualNgwLayer(0,
                            com.nextgis.maplib.util.LayerConfigUtil.extractRenderMode(config)));
                    assertEquals("Legacy origin is retained, never selected as a backend",
                            "local_vector_tiles", layer.getLayerOriginMetadata().getRenderMode());
                    assertEquals(geometry, layer.getGeometryType());
                    assertEquals(schema, layer.getFields());
                    assertFalse("Config read-only policy must survive the renderer migration", layer.isEditable());
                    assertFalse("The published hidden state must be preserved", layer.isVisible());
                    assertFalse("A hidden config is not an identify candidate", LayerIdentifyPolicy.shouldInclude(layer));
                    layer.setVisible(true); assertTrue(layer.save());
                } catch (Exception error) { throw new AssertionError(error); }});
                reload(f, layer); assertStreaming(f, layer);
                assertNotNull(provider(f, layer).loadOriginalFeature(42));
                assertArrayEquals("Render mode does not migrate or rewrite SQL rows", before, contents(layer, 1));
                f.screen.onActivity(activity -> {
                    layer.setVisible(false);
                    ((MapDrawable) f.app.getMap()).checkLayerVisibility(layer.getId());
                    assertFalse(LayerIdentifyPolicy.shouldInclude(layer));
                });
            }
        }
    }

    @Test public void liveFireflyAndAdvancedPolygonEditsKeepDirectSourcesAndOriginalRows() throws Exception {
        verifyLiveStyleEdits();
    }

    static void verifyLiveStyleEdits() throws Exception {
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            for (boolean point : new boolean[]{true, false}) {
                NGWVectorLayerUI layer = add(f, point, 40);
                reload(f, layer); assertStreaming(f, layer);
                SqliteVectorTileProvider previous = provider(f, layer);
                byte[] before = contents(layer, 1);
                String child = "nglayer-layer-" + layer.getId() + (point
                        ? "-layout-firefly-1" : "_outline-layout-contour-1-primary");
                f.renderedFrame();
                await("Original tiled geometry must be rendered", () -> !hits(f, layer, "nglayer-layer-" + layer.getId()).isEmpty());
                f.screen.onActivity(activity -> {
                    com.nextgis.maplib.display.Style style;
                    if (point) {
                        SimpleMarkerStyle marker = new SimpleMarkerStyle(Color.WHITE, Color.rgb(255, 102, 133), 6,
                                SimpleMarkerStyle.MarkerStyleFirefly);
                        marker.setWidth(14); marker.setCircleBlur(2); style = marker;
                    } else {
                        SimplePolygonStyle polygon = new SimplePolygonStyle(Color.WHITE, Color.BLUE);
                        polygon.setWidth(14); polygon.setFill(false); polygon.setFillPattern(1);
                        PolygonPatternOptions pattern = new PolygonPatternOptions(); pattern.setColor(Color.RED);
                        polygon.setPatternOptions(pattern);
                        PolygonOutlineOptions outline = new PolygonOutlineOptions(); outline.setType(PolygonOutlineOptions.DASH);
                        polygon.setOutlineOptions(outline); style = polygon;
                    }
                    layer.setRenderer(new SimpleFeatureRenderer(layer, style)); assertTrue(layer.save());
                    ((MapDrawable) f.app.getMap()).reloadVectorLayerStyleToMaplibre(layer.getId());
                });
                await("Live edit must attach a new current provider", () -> {
                    SqliteVectorTileProvider next = provider(f, layer);
                    return next != null && next != previous && next.isCurrent();
                });
                assertFalse("An old style generation cannot serve late tiles", previous.isCurrent());
                assertStreaming(f, layer); f.renderedFrame();
                await("Advanced style must actually render through its direct source", () -> !hits(f, layer, child).isEmpty());
                Bitmap image = f.snapshot();
                try { VectorStyleRenderingTest.saveFireflySnapshot(f, image, point ? "direct-firefly" : "direct-polygon"); }
                finally { image.recycle(); }
                assertArrayEquals("Live style changes preserve full originals", before, contents(layer, 1));
                f.screen.onActivity(activity -> {
                    layer.setVisible(false); ((MapDrawable) f.app.getMap()).checkLayerVisibility(layer.getId());
                });
            }
        }
    }

    private static JSONObject config(String name) throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("direct-vector-configs/" + name + ".json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    static NGWVectorLayerUI add(VectorStyleRenderingTest.Fixture f, boolean point, double size) {
        AtomicReference<NGWVectorLayerUI> result = new AtomicReference<>();
        f.screen.onActivity(activity -> {try {
            MapDrawable map = (MapDrawable) f.app.getMap();
            NGWVectorLayerUI layer = new NGWVectorLayerUI(f.app, new File(map.getPath(), "direct_tiles_" + System.nanoTime()));
            layer.setName("Technical direct tile fixture");
            layer.create(point ? GeoConstants.GTPoint : GeoConstants.GTPolygon,
                    Arrays.asList(new Field(GeoConstants.FTString, "kvartal", "Quarter"),
                            new Field(GeoConstants.FTString, "vyd", "Stand"), new Field(GeoConstants.FTString, "num_line", "Point")));
            layer.setIsEditable(false); layer.setVisible(true);
            layer.setLayerOriginMetadata(LayerOriginMetadata.manualNgwLayer(0, LayerOriginMetadata.RENDER_MODE_LOCAL_VECTOR_TILES));
            GeoGeometry geometry;
            if (point) { GeoPoint p = new GeoPoint(0, 0); p.setCRS(GeoConstants.CRS_WEB_MERCATOR); geometry = p; }
            else {
                GeoLinearRing ring = new GeoLinearRing(); ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                for (double[] xy : new double[][]{{-size,-size},{size,-size},{size,size},{-size,size},{-size,-size}})
                    ring.add(new GeoPoint(xy[0], xy[1]));
                GeoPolygon polygon = new GeoPolygon(); polygon.setCRS(GeoConstants.CRS_WEB_MERCATOR); polygon.setOuterRing(ring); geometry = polygon;
            }
            Feature feature = new Feature(42, layer.getFields()); feature.setGeometry(geometry);
            for (Field field : layer.getFields()) feature.setFieldValue(field.getName(), "51");
            layer.createFeatureBatch(feature, DatabaseContext.getDatabaseForLayer(layer, false), false);
            layer.setRenderer(new SimpleFeatureRenderer(layer, point
                    ? new SimpleMarkerStyle(Color.GREEN, Color.GREEN, 20, SimpleMarkerStyle.MarkerStyleCircle)
                    : new SimplePolygonStyle(Color.GREEN, Color.BLUE)));
            assertTrue(layer.save()); map.addLayer(layer); f.layers.add(layer); assertTrue(map.save()); result.set(layer);
        } catch (Exception error) { throw new AssertionError(error); }});
        return result.get();
    }

    static List<org.maplibre.geojson.Feature> hits(VectorStyleRenderingTest.Fixture f, NGWVectorLayerUI layer, String child) {
        AtomicReference<List<org.maplibre.geojson.Feature>> result = new AtomicReference<>(Collections.emptyList());
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap();
            if (map.getMaplibreMap().getStyle().getLayer(child) != null)
                result.set(map.getMaplibreMap().queryRenderedFeatures(new RectF(0, 0,
                        map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()), child));
        });
        return result.get();
    }

    static void await(String message, BooleanSupplier condition) throws Exception {
        long end = SystemClock.uptimeMillis() + 30000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50);
        assertTrue(message, condition.getAsBoolean());
    }
}
