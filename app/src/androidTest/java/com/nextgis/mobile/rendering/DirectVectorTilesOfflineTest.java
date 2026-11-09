package com.nextgis.mobile.rendering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.Settings;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.mobile.reliability.EmulatorNetworkFixture;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.geojson.Point;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static com.nextgis.mobile.rendering.DirectVectorTilesTest.*;
import static com.nextgis.mobile.rendering.NativeSqliteVectorRenderingTest.*;

/** Uses the owned emulator radio fixture or a manually disconnected verification phone. */
@RunWith(AndroidJUnit4.class)
public class DirectVectorTilesOfflineTest {
    @Test public void offlineQuarterAcrossTileSeamsRetainsOneInteriorLabelAndFillOutlinePixels() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VerificationEnvironment.requireIsolated(context);
        boolean emulator = VerificationEnvironment.isEmulator();
        if (!emulator) requireManualOffline(context);
        try (EmulatorNetworkFixture network = emulator ? new EmulatorNetworkFixture(context) : null) {
            if (network != null) network.connected(false);
            NetworkUtil connection = new NetworkUtil(context);
            assertFalse("The offline fixture must start without an active connection", connection.isNetworkAvailable());
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
                    AtomicReference<List<org.maplibre.geojson.Feature>> placed =
                            new AtomicReference<>(Collections.emptyList());
                    try {
                        await("One SQL polygon has exactly one interior label across tile seams", () -> {
                            placed.set(hits(f, layer, labels));
                            return placed.get().size() == 1;
                        });
                    } catch (AssertionError failure) {
                        throw new AssertionError("Offline label placement at zoom " + zoom
                                + ": labels=" + placed.get().size() + ", fills="
                                + hits(f, layer, "nglayer-layer-" + layer.getId()).size(), failure);
                    }
                    org.maplibre.geojson.Feature label = placed.get().get(0);
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
            assertFalse("Rendering must not enable networking", connection.isNetworkAvailable());
            if (!emulator) requireManualOffline(context);
        }
    }

    private static void requireManualOffline(Context context) {
        assertEquals("Explicit manual offline mode is required on a physical device", "true",
                InstrumentationRegistry.getArguments().getString("manualOffline"));
        assertEquals("Turn on airplane mode manually and keep Wi-Fi off for the offline test", 1,
                Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
    }
}
