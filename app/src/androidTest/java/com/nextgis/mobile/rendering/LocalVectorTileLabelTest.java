package com.nextgis.mobile.rendering;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.style.layers.*;
import org.maplibre.android.style.sources.VectorSource;
import org.maplibre.geojson.Point;
import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Synthetic SQLite -> loopback MVT -> native symbols and pixels; no object-label source. */
@RunWith(AndroidJUnit4.class)
public class LocalVectorTileLabelTest {
    @Test public void oneQuarterAcrossTileSeamsKeepsOneInteriorLabelWithOneSourceAndThreeStyleLayers() throws Exception {
        GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);
        assertTrue(VerificationEnvironment.isEmulator());
        SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(app);
        Map<String,?> saved=new HashMap<>(prefs.getAll());
        assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO,true)
                .putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG,false).commit());
        AtomicReference<NGWVectorLayerUI> layerRef=new AtomicReference<>();
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean ready=new AtomicBoolean();
            await(()-> {
                screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                    ready.set(map.getMaplibreMap()!=null && map.getMaplibreMap().getStyle()!=null);});
                return ready.get();
            });
            await(()->!com.nextgis.maplibui.util.SharedUnderlayProjects.isMigrationPending()
                    && !com.nextgis.maplibui.util.ProjectOperationCoordinator.isBusy());
            screen.onActivity(activity->{try {
                MapDrawable map=(MapDrawable)app.getMap();
                map.setZoomAndCenter(12,new GeoPoint(0,0),false,0);map.setVisible(false);
                for(org.maplibre.android.style.layers.Layer nativeLayer:map.getMaplibreMap().getStyle().getLayers())
                    nativeLayer.setProperties(PropertyFactory.visibility("none"));
                NGWVectorLayerUI layer=new NGWVectorLayerUI(app,new File(map.getPath(),"tile_labels_"+System.nanoTime()));
                layer.setName("Synthetic tiled quarter");
                layer.create(GeoConstants.GTPolygon,Collections.emptyList());
                layer.setIsEditable(false);layer.setVisible(true);
                layer.setLayerOriginMetadata(LayerOriginMetadata.manualNgwLayer(
                        0,LayerOriginMetadata.RENDER_MODE_LOCAL_VECTOR_TILES));
                GeoPolygon polygon=new GeoPolygon();polygon.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                for(double[] xy:new double[][]{{-6000,-6000},{6000,-6000},{6000,6000},{-6000,6000},{-6000,-6000}})
                    polygon.add(new GeoPoint(xy[0],xy[1]));
                com.nextgis.maplib.datasource.Feature original=new com.nextgis.maplib.datasource.Feature(
                        42,Collections.emptyList());original.setGeometry(polygon);
                layer.createFeatureBatch(original,DatabaseContext.getDatabaseForLayer(layer,false),false);
                SimplePolygonStyle paint=new SimplePolygonStyle(Color.GREEN,Color.BLUE);
                paint.setAlpha(255);paint.setOutAlpha(255);paint.setWidth(4);
                paint.setText("51");paint.setTextColor(Color.RED);paint.setTextSize(24f);
                paint.getLabelAttributes().setTextFont("Open Sans Bold");
                paint.getLabelAttributes().setTextAllowOverlap(true);
                layer.setRenderer(new SimpleFeatureRenderer(layer,paint));
                assertTrue(layer.save());
                assertEquals(1,layer.query(new GeoEnvelope(-6000,6000,-6000,6000)).size());
                map.addLayer(layer);
                assertTrue(map.save());
                assertTrue(LocalVectorTileRenderMode.shouldUseLocalVectorTiles(layer));
                layerRef.set(layer);
                bind(map,layer);
            }catch(Exception error){throw new AssertionError(error);}});
            NGWVectorLayerUI layer=layerRef.get();
            try {
                String probe=LocalVectorTileServer.getInstance().getTileUrl(layer.getId())
                        .replace("{z}","12").replace("{x}","2048").replace("{y}","2048");
                try(java.io.InputStream stream=new java.net.URL(probe).openStream()) {
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                    byte[] buffer=new byte[4096];int count;
                    while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);
                    assertTrue("Owner MVT must contain its label anchor; bytes="+bytes.size()
                            +" style="+((SimplePolygonStyle)layer.getDefaultStyleNoExcept()).getText()
                            +" ids="+layer.query(new GeoEnvelope(-6000,6000,-6000,6000)),new String(bytes.toByteArray(),
                            java.nio.charset.StandardCharsets.ISO_8859_1).contains("_label_anchor"));
                }
                for(int zoom:new int[]{12,13,12}) {
                    screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                        map.setZoomAndCenter(zoom,new GeoPoint(0,0),false,0);
                        // Reapplying style must reuse the same source and three native layers.
                        bind(map,layer);
                    });
                    AtomicReference<List<org.maplibre.geojson.Feature>> symbols=new AtomicReference<>();
                    AtomicReference<String> sourceState=new AtomicReference<>("No source query");
                    try {
                    await(()-> {
                        screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                            VectorSource source=map.getMaplibreMap().getStyle().getSourceAs(layer.getPath().toString());
                            sourceState.set("source="+source.querySourceFeatures(new String[]{"features"},null)
                                    +" camera="+map.getMaplibreMap().getCameraPosition());
                            symbols.set(map.getMaplibreMap().queryRenderedFeatures(new RectF(0,0,
                                    map.getMaplibreMapView().getWidth(),map.getMaplibreMapView().getHeight()),
                                    "symbol-nglayer-layer-"+layer.getId()));});
                        return symbols.get()!=null && symbols.get().size()==1;
                    });
                    } catch(AssertionError error) {throw new AssertionError("Rendered symbols="+symbols.get()+" "+sourceState.get(),error);}
                    assertEquals(1,symbols.get().size());
                    org.maplibre.geojson.Feature label=symbols.get().get(0);
                    assertTrue(label.geometry() instanceof Point);
                    assertTrue(label.getBooleanProperty("_label_anchor"));
                    assertEquals("51",label.getStringProperty(MplFeatureStyleProps.SIGNATURE));
                    Point point=(Point)label.geometry();
                    assertEquals(0,point.longitude(),.00001);assertEquals(0,point.latitude(),.00001);
                    renderedFrame(screen,app);
                    CountDownLatch done=new CountDownLatch(1);AtomicReference<Bitmap> image=new AtomicReference<>();
                    screen.onActivity(activity->((MapDrawable)app.getMap()).getMaplibreMap().snapshot(bitmap->{image.set(bitmap);done.countDown();}));
                    assertTrue(done.await(10,TimeUnit.SECONDS));assertNotNull(image.get());
                    int red=0,green=0,blue=0;
                    for(int y=0;y<image.get().getHeight();y++)for(int x=0;x<image.get().getWidth();x++) {
                        int pixel=image.get().getPixel(x,y);
                        if(Color.red(pixel)>150 && Color.green(pixel)<90 && Color.blue(pixel)<90)red++;
                        if(Color.green(pixel)>150 && Color.red(pixel)<90 && Color.blue(pixel)<90)green++;
                        if(Color.blue(pixel)>150 && Color.red(pixel)<90 && Color.green(pixel)<90)blue++;
                    }
                    assertTrue("Label glyphs must actually render",red>10);
                    assertTrue("Polygon fill must remain",green>100);
                    if(zoom==12)assertTrue("Polygon outline must remain",blue>10);
                }
            } finally {
                screen.onActivity(activity->{LocalVectorTileServer.getInstance().unregisterLayer(layer.getId());
                    assertTrue(layer.delete(false));app.getMap().save();});
            }
        } finally {
            SharedPreferences.Editor restore=prefs.edit();
            for(String key:Arrays.asList(AppSettingsConstants.KEY_PREF_INTRO,SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG)) {
                if(saved.containsKey(key))restore.putBoolean(key,(Boolean)saved.get(key));else restore.remove(key);
            }
            assertTrue(restore.commit());
        }
    }

    private static void renderedFrame(ActivityScenario<MainActivity> screen,GISApplication app) throws Exception {
        // Native queries can already describe the new placement while the GPU still shows an
        // earlier frame. Fence current source/camera idle, then a fresh fully rendered frame.
        CountDownLatch idle=new CountDownLatch(1);
        org.maplibre.android.maps.MapView.OnDidBecomeIdleListener idleListener=idle::countDown;
        screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
            map.getMaplibreMapView().addOnDidBecomeIdleListener(idleListener);
            map.getMaplibreMap().triggerRepaint();});
        try {assertTrue("Tile source and placement must become idle",idle.await(30,TimeUnit.SECONDS));}
        finally {screen.onActivity(activity->((MapDrawable)app.getMap()).getMaplibreMapView().removeOnDidBecomeIdleListener(idleListener));}
        CountDownLatch frame=new CountDownLatch(1);
        org.maplibre.android.maps.MapView.OnDidFinishRenderingFrameListener listener=
                (fully,encoding,rendering)->{if(fully)frame.countDown();};
        screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
            map.getMaplibreMapView().addOnDidFinishRenderingFrameListener(listener);
            map.getMaplibreMap().triggerRepaint();});
        try {assertTrue("Tile glyphs must finish rendering",frame.await(15,TimeUnit.SECONDS));}
        finally {screen.onActivity(activity->((MapDrawable)app.getMap()).getMaplibreMapView().removeOnDidFinishRenderingFrameListener(listener));}
    }

    private static void bind(MapDrawable map,NGWVectorLayerUI layer) {
        org.maplibre.android.maps.Style style=map.getMaplibreMap().getStyle();
        String source=layer.getPath().toString();
        int sources=style.getSources().size(),layers=style.getLayers().size();
        boolean present=style.getSource(source)!=null;
        String url=LocalVectorTileServer.getInstance().registerLayer(layer);assertNotNull(url);
        assertTrue(MPLFeaturesUtils.createLocalVectorTileSourceForLayer(layer.getId(),style,source,url,0,16));
        MPLFeaturesUtils.createFillLayerForLocalVectorTileLayer(layer.getId(),layer.getGeometryType(),style,
                new HashMap<>(),new HashMap<>(),new HashMap<>(),layer.getDefaultStyleNoExcept(),layer,source,null);
        assertEquals(sources+(present?0:1),style.getSources().size());
        assertEquals(layers+(present?0:3),style.getLayers().size());
        assertTrue(style.getSource(source) instanceof VectorSource);
        String fill="nglayer-layer-"+layer.getId();
        assertTrue(style.getLayer(fill) instanceof FillLayer);
        assertTrue(style.getLayer(fill+"_outline") instanceof LineLayer);
        assertTrue(style.getLayer("symbol-"+fill) instanceof SymbolLayer);
    }

    private static void await(BooleanSupplier ready) throws Exception {
        long end=SystemClock.uptimeMillis()+30000;
        while(!ready.getAsBoolean() && SystemClock.uptimeMillis()<end)Thread.sleep(50);
        assertTrue("Native tile renderer did not settle",ready.getAsBoolean());
    }
}
