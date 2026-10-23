package com.nextgis.mobile.rendering;

import android.graphics.Color;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.GeoPoint;
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
public class FireflyTileFallbackTest {
    @Test public void changingALiveTiledPointToFireflyRebuildsItThroughTheClassicFallback() throws Exception {
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            MapDrawable map = (MapDrawable) f.app.getMap();
            NGWVectorLayerUI layer = new NGWVectorLayerUI(f.app,
                    new File(map.getPath(), "firefly_fallback_" + System.nanoTime()));
            try {
                f.screen.onActivity(activity -> {try {
                    layer.setName("Synthetic firefly fallback"); layer.create(GeoConstants.GTPoint, Collections.emptyList());
                    layer.setIsEditable(false); layer.setVisible(true);
                    layer.setLayerOriginMetadata(LayerOriginMetadata.manualNgwLayer(0, LayerOriginMetadata.RENDER_MODE_LOCAL_VECTOR_TILES));
                    GeoPoint point = new GeoPoint(0, 0); point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                    Feature feature = new Feature(42, Collections.emptyList()); feature.setGeometry(point);
                    layer.createFeatureBatch(feature, DatabaseContext.getDatabaseForLayer(layer, false), false);
                    SimpleMarkerStyle circle = new SimpleMarkerStyle(Color.GREEN, Color.GREEN, 20, SimpleMarkerStyle.MarkerStyleCircle);
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
                    SimpleMarkerStyle firefly = new SimpleMarkerStyle(Color.WHITE, Color.rgb(255, 102, 133), 6, SimpleMarkerStyle.MarkerStyleFirefly);
                    firefly.setWidth(14); firefly.setCircleBlur(2);
                    layer.setRenderer(new SimpleFeatureRenderer(layer, firefly)); assertTrue(layer.save());
                    map.reloadVectorLayerStyleToMaplibre(layer.getId());
                });
                AtomicBoolean classic = new AtomicBoolean(); deadline = SystemClock.uptimeMillis() + 30000;
                String child = "nglayer-layer-" + layer.getId() + "-layout-firefly-1";
                while (!classic.get() && SystemClock.uptimeMillis() < deadline) {
                    f.screen.onActivity(activity -> classic.set(map.getMaplibreMap().getStyle() != null
                            && map.getMaplibreMap().getStyle().getSource(layer.getPath().toString()) instanceof GeoJsonSource
                            && map.getMaplibreMap().getStyle().getLayer(child) != null));
                    if (!classic.get()) Thread.sleep(50);
                }
                assertTrue("The unsupported tiled marker becomes a classic Firefly without restarting the app", classic.get());
                f.renderedFrame(); Thread.sleep(400);
                f.screen.onActivity(activity -> assertFalse("The fallback actually renders a Firefly",
                        map.getMaplibreMap().queryRenderedFeatures(new android.graphics.RectF(0, 0,
                                map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()), child).isEmpty()));
                VectorStyleRenderingTest.saveFireflySnapshot(f, f.snapshot(), "tile-fallback");
            } finally {
                f.screen.onActivity(activity -> {assertTrue(layer.delete(false)); assertTrue(map.save());});
            }
        }
    }
}
