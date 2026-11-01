package com.nextgis.mobile.rendering;

import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.map.*;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.mobile.reliability.EmulatorNetworkFixture;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.geojson.Point;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import static org.junit.Assert.*;
import static com.nextgis.mobile.rendering.DirectVectorTilesTest.*;
import static com.nextgis.mobile.rendering.NativeSqliteVectorRenderingTest.*;

/** Radio changes are isolated to the owned emulator and restored in finally. */
@RunWith(AndroidJUnit4.class)
public class DirectVectorTilesOfflineTest {
    @Test public void offlineQuarterAcrossTileSeamsRetainsOneInteriorLabelAndFillOutlinePixels() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Radio changes belong only to the emulator", VerificationEnvironment.isEmulator());
        try (EmulatorNetworkFixture network = new EmulatorNetworkFixture(context)) {
            network.connected(false);
            try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
                f.screen.onActivity(activity -> f.app.getGpsEventSource().removeListener(activity.getMapFragment()));
                NGWVectorLayerUI layer = add(f, false, 6000);
                f.screen.onActivity(activity -> {
                    SimplePolygonStyle paint = new SimplePolygonStyle(Color.GREEN, Color.BLUE);
                    paint.setAlpha(255); paint.setOutAlpha(255); paint.setWidth(4);
                    paint.setText("51"); paint.setTextColor(Color.RED); paint.setTextSize(24f);
                    paint.getLabelAttributes().setTextFont("Open Sans Bold");
                    paint.getLabelAttributes().setTextAllowOverlap(true);
                    layer.setRenderer(new SimpleFeatureRenderer(layer, paint)); assertTrue(layer.save());
                });
                byte[] before = contents(layer, 1); reload(f, layer);
                String labels = "symbol-nglayer-layer-" + layer.getId();
                for (int zoom : new int[]{12, 13, 12}) {
                    f.zoom(zoom); assertStreaming(f, layer);
                    f.screen.onActivity(activity -> {
                        MapDrawable map = (MapDrawable) f.app.getMap();
                        String source = map.getVectorRenderSourceId(layer.getId());
                        org.maplibre.android.maps.Style style = map.getMaplibreMap().getStyle();
                        String fill = "nglayer-layer-" + layer.getId();
                        assertEquals(source, ((FillLayer) style.getLayer(fill)).getSourceId());
                        assertEquals(source, ((LineLayer) style.getLayer(fill+"_outline")).getSourceId());
                        assertEquals("Label shares the polygon source", source,
                                ((SymbolLayer) style.getLayer(labels)).getSourceId());
                    });
                    await("One SQL polygon has exactly one interior label across tile seams", () -> hits(f, layer, labels).size() == 1);
                    org.maplibre.geojson.Feature label = hits(f, layer, labels).get(0);
                    assertTrue(label.geometry() instanceof Point);
                    assertTrue(label.getBooleanProperty(NativeVectorTileEncoder.LABEL_ANCHOR));
                    assertEquals("51", label.getStringProperty(MplFeatureStyleProps.SIGNATURE));
                    Point anchor = (Point) label.geometry();
                    assertEquals(0, anchor.longitude(), .00001); assertEquals(0, anchor.latitude(), .00001);
                    Bitmap image = f.snapshot();
                    try {
                        int red = 0, green = 0, blue = 0;
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                            int pixel = image.getPixel(x, y);
                            if (Color.red(pixel) > 150 && Color.green(pixel) < 90 && Color.blue(pixel) < 90) red++;
                            if (Color.green(pixel) > 150 && Color.red(pixel) < 90 && Color.blue(pixel) < 90) green++;
                            if (Color.blue(pixel) > 150 && Color.red(pixel) < 90 && Color.green(pixel) < 90) blue++;
                        }
                        VectorStyleRenderingTest.saveFireflySnapshot(f, image, "direct-label-" + zoom);
                        assertTrue("Label glyphs must actually render, red=" + red, red > 10);
                        assertTrue("Polygon fill must remain, green=" + green, green > 100);
                        if (zoom == 12) assertTrue("Polygon outline must remain, blue=" + blue, blue > 10);
                    } finally { image.recycle(); }
                }
                assertArrayEquals(before, contents(layer, 1));
            }
            DirectVectorTilesTest.verifyLiveStyleEdits();
            assertFalse("Rendering must not enable networking", new com.nextgis.maplib.util.NetworkUtil(context).isNetworkAvailable());
        }
    }

}
