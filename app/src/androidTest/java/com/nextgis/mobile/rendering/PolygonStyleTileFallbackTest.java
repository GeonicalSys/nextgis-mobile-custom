package com.nextgis.mobile.rendering;

import android.graphics.Color;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.*;
import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimpleMarkerStyle;
import com.nextgis.maplib.map.LayerOriginMetadata;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.sources.VectorSource;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** Separate from the classic style matrix: live MVT -> supported classic fallback on style edit. */
@RunWith(AndroidJUnit4.class)
public class PolygonStyleTileFallbackTest {
    @Test public void changingALiveTiledPolygonToAnAdvancedStyleRebuildsItsClassicSource() throws Exception {
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            MapDrawable map = (MapDrawable) f.app.getMap();
            NGWVectorLayerUI layer = new NGWVectorLayerUI(f.app,
                    new File(map.getPath(), "polygon_style_fallback_" + System.nanoTime()));
            try {
                f.screen.onActivity(activity -> {try {
                    layer.setName("Synthetic polygon style fallback"); layer.create(GeoConstants.GTPolygon, Collections.emptyList());
                    layer.setIsEditable(false); layer.setVisible(true);
                    layer.setLayerOriginMetadata(LayerOriginMetadata.manualNgwLayer(0, LayerOriginMetadata.RENDER_MODE_LOCAL_VECTOR_TILES));
                    GeoLinearRing ring = new GeoLinearRing(); ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                    for (double[] xy : new double[][]{{-40,-40},{40,-40},{40,40},{-40,40},{-40,-40}}) ring.add(new GeoPoint(xy[0],xy[1]));
                    GeoPolygon point = new GeoPolygon(); point.setCRS(GeoConstants.CRS_WEB_MERCATOR); point.setOuterRing(ring);
                    Feature feature = new Feature(42, Collections.emptyList()); feature.setGeometry(point);
                    layer.createFeatureBatch(feature, DatabaseContext.getDatabaseForLayer(layer, false), false);
                    SimplePolygonStyle circle = new SimplePolygonStyle(Color.GREEN,Color.GREEN);
                    layer.setRenderer(new SimpleFeatureRenderer(layer, circle)); assertTrue(layer.save());
                    map.addLayer(layer); assertTrue(map.save()); map.addLayerByID(layer.getId());
                } catch (Exception error) {throw new AssertionError(error);}});
                AtomicBoolean tiled = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 30000;
                while (!tiled.get() && SystemClock.uptimeMillis() < deadline) {
                    f.screen.onActivity(activity -> tiled.set(map.getMaplibreMap().getStyle().getSource(layer.getPath().toString()) instanceof VectorSource));
                    if (!tiled.get()) Thread.sleep(50);
                }
                assertTrue("Start from a real configured native vector source", tiled.get());
                f.renderedFrame();
                f.screen.onActivity(activity -> {
                    SimplePolygonStyle firefly = new SimplePolygonStyle(Color.WHITE,Color.BLUE);
                    firefly.setWidth(14); firefly.setFill(false); firefly.setFillPattern(1);
                    firefly.setPatternOptions(new PolygonPatternOptions()); firefly.getPatternOptions().setColor(Color.RED);
                    firefly.setOutlineOptions(new PolygonOutlineOptions()); firefly.getOutlineOptions().setType(PolygonOutlineOptions.DASH);
                    layer.setRenderer(new SimpleFeatureRenderer(layer, firefly)); assertTrue(layer.save());
                    map.reloadVectorLayerStyleToMaplibre(layer.getId());
                });
                AtomicBoolean classic = new AtomicBoolean(); deadline = SystemClock.uptimeMillis() + 30000;
                String child = "nglayer-layer-" + layer.getId() + "_outline-layout-contour-1-primary";
                while (!classic.get() && SystemClock.uptimeMillis() < deadline) {
                    f.screen.onActivity(activity -> classic.set(map.getMaplibreMap().getStyle() != null
                            && map.getMaplibreMap().getStyle().getSource(layer.getPath().toString()) instanceof GeoJsonSource
                            && map.getMaplibreMap().getStyle().getLayer(child) != null));
                    if (!classic.get()) Thread.sleep(50);
                }
                assertTrue("Advanced polygons use classic fallback without restarting the app", classic.get());
                f.renderedFrame(); Thread.sleep(400);
                f.screen.onActivity(activity -> assertFalse("The fallback actually renders the advanced contour",
                        map.getMaplibreMap().queryRenderedFeatures(new android.graphics.RectF(0, 0,
                                map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()), child).isEmpty()));
                VectorStyleRenderingTest.saveFireflySnapshot(f, f.snapshot(), "polygon-tile-fallback");
            } finally {
                f.screen.onActivity(activity -> {assertTrue(layer.delete(false)); assertTrue(map.save());});
            }
        }
    }
}
