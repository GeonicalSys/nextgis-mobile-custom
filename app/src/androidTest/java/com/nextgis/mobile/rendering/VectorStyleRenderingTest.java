package com.nextgis.mobile.rendering;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.api.ITextStyle;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.*;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.PropertyValue;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static com.nextgis.maplib.map.MplFeatureStyleProps.*;
import static org.junit.Assert.*;

/** Classic SQL -> rule resolver -> GeoJSON -> native MapLibre -> rendered pixels, offline. */
@RunWith(AndroidJUnit4.class)
public class VectorStyleRenderingTest {
    private static boolean sameValue(com.google.gson.JsonElement expected,com.google.gson.JsonElement actual){
        if(expected==null || expected.isJsonNull())return actual==null || actual.isJsonNull();
        if(actual==null)return false;
        if(expected.isJsonArray() && actual.isJsonArray()){
            if(expected.getAsJsonArray().size()!=actual.getAsJsonArray().size())return false;
            for(int i=0;i<expected.getAsJsonArray().size();i++)if(!sameValue(expected.getAsJsonArray().get(i),actual.getAsJsonArray().get(i)))return false;
            return true;
        }
        if(expected.isJsonPrimitive() && actual.isJsonPrimitive() && expected.getAsJsonPrimitive().isNumber() && actual.getAsJsonPrimitive().isNumber())
            return Math.abs(expected.getAsDouble()-actual.getAsDouble()) <= .000001*(1+Math.abs(expected.getAsDouble()));
        return expected.equals(actual);
    }
    private static void expression(PropertyValue<?> value,String key){
        assertTrue("Native property rejected feature expression for "+key,value.isExpression());
        assertTrue(value.getExpression().toString().contains(key));
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end=SystemClock.uptimeMillis()+30000;
        while(!condition.getAsBoolean() && SystemClock.uptimeMillis()<end) Thread.sleep(50);
        assertTrue("Renderer did not settle",condition.getAsBoolean());
    }
    static class Fixture implements AutoCloseable {
        final GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(app);
        final boolean intro=prefs.getBoolean(AppSettingsConstants.KEY_PREF_INTRO,false), hadIntro=prefs.contains(AppSettingsConstants.KEY_PREF_INTRO);
        final boolean geoDialog=prefs.getBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG,true),hadGeoDialog=prefs.contains(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
        ActivityScenario<MainActivity> screen;
        final List<VectorLayerUI> layers=new ArrayList<>();
        Fixture() throws Exception {
            VerificationEnvironment.requireIsolated(app);
            assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO,true)
                    .putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG,false).commit());
            screen=ActivityScenario.launch(MainActivity.class);
            AtomicBoolean ready=new AtomicBoolean();
            await(()->{
                screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                    ready.set(map.getMaplibreMap()!=null && map.getMaplibreMap().getStyle()!=null
                            && map.getMaplibreMapView()==activity.findViewById(com.nextgis.mobile.R.id.mapViewMaplibre));});
                return ready.get();
            });
            await(()->!com.nextgis.maplibui.util.SharedUnderlayProjects.isMigrationPending()
                    && !com.nextgis.maplibui.util.ProjectOperationCoordinator.isBusy());
            screen.onActivity(activity->{
                MapDrawable map=(MapDrawable)app.getMap();
                // Keep delayed model redraws at the same camera and keep the
                // built-in underlay hidden when native layers are rebuilt.
                map.setZoomAndCenter(16,new GeoPoint(0,0),false,0);
                map.setVisible(false);
                // No real Web GIS layer is examined or modified by this fixture.
                for(org.maplibre.android.style.layers.Layer layer:map.getMaplibreMap().getStyle().getLayers())
                    layer.setProperties(PropertyFactory.visibility("none"));
            });
        }
        VectorLayerUI add(int geometry) {
            AtomicReference<VectorLayerUI> result=new AtomicReference<>();
            screen.onActivity(activity->{try {
                MapDrawable map=(MapDrawable)app.getMap();
                VectorLayerUI layer=new VectorLayerUI(app,new File(map.getPath(),"style_matrix_"+UUID.randomUUID().toString().replace("-","")));
                layer.setName("Synthetic classic style");map.addLayer(layer);layers.add(layer);
                layer.create(geometry,Arrays.asList(new Field(GeoConstants.FTString,"kind","Category"),new Field(GeoConstants.FTString,"name","Name")));
                layer.setIsEditable(true);layer.setVisible(true);
                assertFalse("Configured vector tiles are outside this matrix",LocalVectorTileRenderMode.isRequested(layer));
                assertFalse(LocalVectorTileRenderMode.shouldUseLocalVectorTiles(layer));
                result.set(layer);
            }catch(Exception error){throw new AssertionError(error);}});
            return result.get();
        }
        void insert(VectorLayerUI layer,int index,String kind) throws Exception {
            GeoGeometry geometry=geometry(layer.getGeometryType(),index*100);
            ContentValues values=new ContentValues();values.put(Constants.FIELD_GEOM,geometry.toBlob());
            if(kind==null)values.putNull("kind");else values.put("kind",kind);
            values.put("name",index==0?"Альфа":index==1?"Бета":"Прочие");
            assertTrue(layer.insertAddChanges(values)!=Constants.NOT_FOUND);
        }
        List<org.maplibre.geojson.Feature> bind(VectorLayerUI layer,com.nextgis.maplib.display.Style fallback,FieldStyleRule rules) throws Exception {
            // Exercise the persisted style format, including optional presence and explicit defaults.
            AtomicReference<com.nextgis.maplib.display.Style> parsed=new AtomicReference<>();
            SimpleFeatureRenderer.fromJSON(new org.json.JSONObject().put("style",fallback.toJSON()),parsed);
            FieldStyleRule restored=null;
            if(rules!=null){restored=new FieldStyleRule(layer);restored.fromJSON(rules.toJSON());}
            layer.setRenderer(restored==null?new SimpleFeatureRenderer(layer,parsed.get()):new RuleFeatureRenderer(layer,restored,parsed.get()));
            assertTrue(layer.save());
            List<org.maplibre.geojson.Feature> features=MPLFeaturesUtils.createFeatureListFromLayer(layer);
            screen.onActivity(activity->{
                org.maplibre.android.maps.Style nativeStyle=((MapDrawable)app.getMap()).getMaplibreMap().getStyle();
                String source=layer.getPath().toString();
                MPLFeaturesUtils.createSourceForLayer(layer.getId(),layer.getGeometryType(),features,nativeStyle,
                        new HashMap<>(),new HashMap<>(),new HashMap<>(),source,false,null);
                assertTrue("Classic source required",nativeStyle.getSource(source) instanceof GeoJsonSource);
                MPLFeaturesUtils.createFillLayerForLayer(layer.getId(),layer.getGeometryType(),nativeStyle,
                        new HashMap<>(),new HashMap<>(),new HashMap<>(),new HashMap<>(),layer.getDefaultStyleNoExcept(),
                        true,layer,source,null,null);
            });
            if(layer.isVisible()){
                Map<String,com.google.gson.JsonObject> expected=new HashMap<>();
                for(org.maplibre.geojson.Feature feature:features){com.google.gson.JsonObject properties=feature.properties().deepCopy();
                    // Legacy selection color is omitted by the geometry cache; it is not style paint.
                    properties.remove(MPLFeaturesUtils.prop_color);properties.remove(MPLFeaturesUtils.prop_order);
                    expected.put(feature.getStringProperty(MPLFeaturesUtils.prop_featureid),properties);}
                screen.onActivity(activity->((MapDrawable)app.getMap()).getMaplibreMap().triggerRepaint());
                AtomicBoolean sourceReady=new AtomicBoolean();
                AtomicReference<String> sourceState=new AtomicReference<>("No source features");
                try{await(()->{
                    screen.onActivity(activity->{GeoJsonSource source=((MapDrawable)app.getMap()).getMaplibreMap().getStyle().getSourceAs(layer.getPath().toString());
                        Set<String> loaded=new HashSet<>();
                        for(org.maplibre.geojson.Feature feature:source.querySourceFeatures(null)){
                            String id=feature.getStringProperty(MPLFeaturesUtils.prop_featureid);
                            com.google.gson.JsonObject wanted=expected.get(id);if(wanted==null)continue;
                            boolean same=true;for(Map.Entry<String,com.google.gson.JsonElement> property:wanted.entrySet())
                                if(!sameValue(property.getValue(),feature.properties().get(property.getKey()))){sourceState.set(id+" / "+property.getKey()+" expected="+property.getValue()+" native="+feature.properties().get(property.getKey()));same=false;break;}
                            if(same)loaded.add(id);
                        }
                        sourceReady.set(loaded.containsAll(expected.keySet()));});
                    return sourceReady.get();
                });}catch(AssertionError error){throw new AssertionError("Current style did not reach its source: "+sourceState.get(),error);}
            }
            // Source uploads and glyph layouts are asynchronous; a wall-clock delay cannot fence them.
            renderedFrame();Thread.sleep(400);return features;
        }
        void renderedFrame() throws Exception {
            // A fully rendered frame may still belong to the previous camera or
            // placement pass. Wait for native idle before fencing a fresh frame.
            CountDownLatch idle=new CountDownLatch(1);
            org.maplibre.android.maps.MapView.OnDidBecomeIdleListener idleListener=idle::countDown;
            screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                map.getMaplibreMapView().addOnDidBecomeIdleListener(idleListener);map.getMaplibreMap().triggerRepaint();});
            try{assertTrue("The current camera, source and placement must become idle",idle.await(30,TimeUnit.SECONDS));}
            finally{screen.onActivity(activity->((MapDrawable)app.getMap()).getMaplibreMapView().removeOnDidBecomeIdleListener(idleListener));}
            CountDownLatch frame=new CountDownLatch(1);
            org.maplibre.android.maps.MapView.OnDidFinishRenderingFrameListener listener=(fully,encoding,rendering)->{if(fully)frame.countDown();};
            screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();map.getMaplibreMapView().addOnDidFinishRenderingFrameListener(listener);
                map.getMaplibreMap().triggerRepaint();});
            try{assertTrue("The current native frame and glyphs must finish",frame.await(15,TimeUnit.SECONDS));}
            finally{screen.onActivity(activity->((MapDrawable)app.getMap()).getMaplibreMapView().removeOnDidFinishRenderingFrameListener(listener));}
        }
        SymbolLayer labels(VectorLayerUI layer) {
            return (SymbolLayer)((MapDrawable)app.getMap()).getMaplibreMap().getStyle().getLayer("symbol-nglayer-layer-"+layer.getId());
        }
        void zoom(double zoom) throws Exception {
            screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                map.setZoomAndCenter((float)zoom,map.getMaplibreCenter(),false,0);});
            AtomicBoolean settled=new AtomicBoolean();
            await(()->{screen.onActivity(activity->settled.set(Math.abs(((MapDrawable)app.getMap()).getMaplibreMap().getCameraPosition().zoom-zoom)<.001));return settled.get();});
            renderedFrame();
        }
        Bitmap snapshot() throws Exception {
            CountDownLatch done=new CountDownLatch(1);AtomicReference<Bitmap> image=new AtomicReference<>();
            screen.onActivity(activity->((MapDrawable)app.getMap()).getMaplibreMap().snapshot(bitmap->{image.set(bitmap);done.countDown();}));
            assertTrue("Native snapshot timed out",done.await(10,TimeUnit.SECONDS));assertNotNull(image.get());return image.get();
        }
        int redPixels() throws Exception {
            Bitmap image=snapshot();int count=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                int color=image.getPixel(x,y);
                if(Color.red(color)>150 && Color.green(color)<90 && Color.blue(color)<90)count++;
            }return count;
        }
        int picked(VectorLayerUI layer) {
            java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();
            screen.onActivity(activity->{MapDrawable map=(MapDrawable)app.getMap();
                try{
                    java.lang.reflect.Method picking=Arrays.stream(MapDrawable.class.getDeclaredMethods())
                            .filter(method->method.getName().equals("queryTouchFeatures")).findFirst().get();picking.setAccessible(true);
                    List<?> hits=(List<?>)picking.invoke(map,map.getMaplibreMap(),new android.graphics.RectF(0,0,
                            map.getMaplibreMapView().getWidth(),map.getMaplibreMapView().getHeight()),"nglayer-layer-"+layer.getId(),null);
                    // Native queries can return multiple clipped tile fragments of one SQL object.
                    Set<String> ids=new HashSet<>();for(Object hit:hits)
                        ids.add(((org.maplibre.geojson.Feature)hit).getStringProperty(MPLFeaturesUtils.prop_featureid));
                    count.set(ids.size());
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
            return count.get();
        }
        @Override public void close(){
            screen.onActivity(activity->{for(VectorLayerUI layer:layers)assertTrue(layer.delete(false));app.getMap().save();});
            screen.close();SharedPreferences.Editor restore=prefs.edit();
            if(hadIntro)restore.putBoolean(AppSettingsConstants.KEY_PREF_INTRO,intro);else restore.remove(AppSettingsConstants.KEY_PREF_INTRO);
            if(hadGeoDialog)restore.putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG,geoDialog);else restore.remove(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
            assertTrue(restore.commit());
        }
    }
    private static GeoGeometry geometry(int type,double y){
        GeoPoint point=new GeoPoint(0,y);point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        if(type==GeoConstants.GTPoint)return point;
        if(type==GeoConstants.GTMultiPoint){GeoMultiPoint multi=new GeoMultiPoint();multi.setCRS(GeoConstants.CRS_WEB_MERCATOR);multi.add(point);return multi;}
        GeoLineString line=new GeoLineString();line.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        line.add(new GeoPoint(-100,y));line.add(new GeoPoint(0,y+20));line.add(new GeoPoint(100,y));
        if(type==GeoConstants.GTLineString)return line;
        if(type==GeoConstants.GTMultiLineString){GeoMultiLineString multi=new GeoMultiLineString();multi.setCRS(GeoConstants.CRS_WEB_MERCATOR);multi.add(line);return multi;}
        GeoLinearRing ring=new GeoLinearRing();ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        for(double[] xy:new double[][]{{-40,y-40},{40,y-40},{40,y+40},{-40,y+40},{-40,y-40}})ring.add(new GeoPoint(xy[0],xy[1]));
        GeoPolygon polygon=new GeoPolygon();polygon.setCRS(GeoConstants.CRS_WEB_MERCATOR);polygon.setOuterRing(ring);
        if(type==GeoConstants.GTPolygon)return polygon;
        GeoMultiPolygon multi=new GeoMultiPolygon();multi.setCRS(GeoConstants.CRS_WEB_MERCATOR);multi.add(polygon);return multi;
    }
    private static com.nextgis.maplib.display.Style style(int type,int color){
        com.nextgis.maplib.display.Style style;
        if(type==GeoConstants.GTPoint||type==GeoConstants.GTMultiPoint){SimpleMarkerStyle marker=new SimpleMarkerStyle();marker.setType(SimpleMarkerStyle.MarkerStyleCircle);marker.setSize(8);marker.setField("name");marker.setTextColor(Color.RED);marker.setTextSize(5);marker.setTextAlignment(SimpleMarkerStyle.ALIGN_TOP);style=marker;}
        else if(type==GeoConstants.GTLineString||type==GeoConstants.GTMultiLineString){SimpleLineStyle line=new SimpleLineStyle();line.setField("name");line.setTextColor(Color.RED);line.setTextSize(5);style=line;}
        else{SimplePolygonStyle polygon=new SimplePolygonStyle();polygon.setFill(true);polygon.setField("name");polygon.setTextColor(Color.RED);polygon.setTextSize(5f);style=polygon;}
        style.setColor(color);style.setOutColor(Color.BLUE);style.setWidth(2);return style;
    }
    private static FieldStyleRule rule(VectorLayerUI layer,com.nextgis.maplib.display.Style other,com.nextgis.maplib.display.Style category){
        FieldStyleRule rule=new FieldStyleRule(layer);rule.setKey("kind");rule.setKeyIgnoreCase(true);rule.setOtherStyle(other);rule.setStyle("A",category);return rule;
    }
    private static LabelAttributes richLabels(com.nextgis.maplib.display.Style style){
        LabelAttributes labels=LabelAttributes.fromStyle(style);labels.setLabelTemplate("${name}");
        labels.setTextHaloColor(Color.BLUE);labels.setTextHaloWidth(4);labels.setTextHaloBlur(1);
        labels.setTextOpacity(190);labels.setTextFont("Open Sans Bold");labels.setTextJustify("right");
        labels.setTextTransform("uppercase");labels.setTextLetterSpacing(.2f);labels.setTextLineHeight(1.8f);
        labels.setTextPadding(6);labels.setTextMaxWidth(8);labels.setTextAllowOverlap(true);labels.setTextOptional(false);
        labels.setTextKeepUpright(false);labels.setTextMaxAngle(30);labels.setSymbolSpacing(80);
        labels.setLabelMinZoom(12);labels.setLabelMaxZoom(20);labels.setLineLabelRepeat(false);labels.setLineLabelHorizontal(true);
        labels.setTextScaleWithZoom(true);labels.setTextZoomScaleStops("12:0.5,16:1,20:2");return labels;
    }

    private static android.graphics.PointF screenPoint(Fixture f, double y) {
        AtomicReference<android.graphics.PointF> point = new AtomicReference<>();
        f.screen.onActivity(activity -> {
            GeoPoint geo = new GeoPoint(0, y); geo.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            geo.project(GeoConstants.CRS_WGS84);
            point.set(((MapDrawable) f.app.getMap()).getMaplibreMap().getProjection()
                    .toScreenLocation(new org.maplibre.android.geometry.LatLng(geo.getY(), geo.getX())));
        });
        return point.get();
    }

    private static int pixel(Bitmap bitmap, android.graphics.PointF point, float offset) {
        return bitmap.getPixel(Math.round(point.x + offset), Math.round(point.y));
    }

    private static int bluePixels(Bitmap bitmap) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            int color = bitmap.getPixel(x, y);
            if (Color.blue(color) > 160 && Color.red(color) < 80 && Color.green(color) < 80) count++;
        }
        return count;
    }

    static void saveFireflySnapshot(Fixture f, Bitmap bitmap, String name) throws Exception {
        File folder = new File(f.app.getFilesDir(), "firefly-qa");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new File(folder, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }

    @Test public void fireflyKeepsTheCoreCrispAndTheGlowOutsideWithIndependentOpacityAndZoom() throws Exception {
        try (Fixture f = new Fixture()) {
            for (int geometry : new int[]{GeoConstants.GTPoint, GeoConstants.GTMultiPoint}) {
                VectorLayerUI layer = f.add(geometry); f.insert(layer, 0, null);
                SimpleMarkerStyle marker = new SimpleMarkerStyle(Color.BLUE, Color.rgb(255, 102, 133),
                        20, SimpleMarkerStyle.MarkerStyleFirefly);
                marker.setWidth(24); marker.setAlpha(0); marker.setOutAlpha(0);
                f.bind(layer, marker, null);
                android.graphics.PointF center = screenPoint(f, 0);
                Bitmap empty = f.snapshot(); int background = pixel(empty, center, 0);
                float density = f.app.getResources().getDisplayMetrics().density;
                float core = MPLFeaturesUtils.getMPLThinkness(20) * density;
                float glow = MPLFeaturesUtils.getMPLThinkness(24) * density;
                marker.setAlpha(255); marker.setOutAlpha(255);
                for (int softness = 0; softness <= 3; softness++) {
                    marker.setCircleBlur(softness); f.bind(layer, marker, null);
                    Bitmap image = f.snapshot();
                    assertEquals("The native core stays blue at every softness", Color.BLUE, pixel(image, center, core * .6f));
                    int outside = pixel(image, center, core + glow * .3f);
                    assertTrue("Pink glow is visible outside the core", Color.red(outside) > Color.green(outside) + 45);
                    saveFireflySnapshot(f, image, "geometry-" + geometry + "-softness-" + softness);
                }
                marker.setCircleBlur(2); marker.setAlpha(0); f.bind(layer, marker, null);
                Bitmap transparent = f.snapshot();
                assertEquals("The halo cannot leak through a transparent centre", background, pixel(transparent, center, 0));
                assertEquals(background, pixel(transparent, center, core * .6f));
                assertNotEquals(background, pixel(transparent, center, core + glow * .3f));
                saveFireflySnapshot(f, transparent, "transparent-core-" + geometry);
                marker.setAlpha(255); marker.setOutAlpha(0); f.bind(layer, marker, null);
                assertEquals(background, pixel(f.snapshot(), center, core + glow * .3f));
                marker.setOutAlpha(255); layer.setLayerOpacity(128); f.bind(layer, marker, null);
                int half = pixel(f.snapshot(), center, 0);
                assertEquals(Math.round(Color.red(background) * 127 / 255f), Color.red(half), 5);
                assertEquals(Math.round(Color.blue(background) * 127 / 255f + 128), Color.blue(half), 5);
                layer.setLayerOpacity(255);
                marker.setScaleSizeWithZoom(true); marker.setSizeZoomScaleStops("12:0.5,16:1,20:2");
                f.bind(layer, marker, null); f.zoom(12); int small = bluePixels(f.snapshot());
                f.zoom(16); int medium = bluePixels(f.snapshot());
                f.zoom(20); int large = bluePixels(f.snapshot());
                assertTrue("Core scales with its halo", small > 4 && medium > small * 2 && large > medium * 2);
                marker.setScaleSizeWithZoom(false); f.bind(layer, marker, null);
                int fixed = bluePixels(f.snapshot()); f.zoom(12);
                assertEquals("Explicit fixed size survives style reload", fixed, bluePixels(f.snapshot()), 12);
                f.screen.onActivity(activity -> {layer.setVisible(false); ((MapDrawable) f.app.getMap()).checkLayerVisibility(layer.getId());});
                f.zoom(16);
            }
        }
    }

    @Test public void fireflyCategoriesPreserveCircleBlurPickingVisibilityAndImageCleanup() throws Exception {
        try (Fixture f = new Fixture()) {
            VectorLayerUI layer = f.add(GeoConstants.GTPoint);
            f.insert(layer, 0, "A"); f.insert(layer, 1, "B"); f.insert(layer, 2, null);
            SimpleMarkerStyle circle = new SimpleMarkerStyle(Color.GREEN, Color.GREEN, 20, SimpleMarkerStyle.MarkerStyleCircle);
            circle.setCircleBlur(2); circle.setOutAlpha(0);
            FieldStyleRule rules = rule(layer, circle, circle.clone()); f.bind(layer, circle, rules);
            Bitmap before = f.snapshot(); android.graphics.PointF normal = screenPoint(f, 180);
            SimpleMarkerStyle firefly = new SimpleMarkerStyle(Color.WHITE, Color.rgb(255, 102, 133), 6, SimpleMarkerStyle.MarkerStyleFirefly);
            firefly.setWidth(14); firefly.setCircleBlur(2); rules.setStyle("A", firefly);
            f.bind(layer, circle, rules); Bitmap after = f.snapshot();
            for (int y = -12; y <= 12; y++) for (int x = -12; x <= 12; x++)
                assertEquals("Adding a firefly category leaves the old blurred circle unchanged", before.getPixel(Math.round(normal.x) + x, Math.round(normal.y) + y),
                        after.getPixel(Math.round(normal.x) + x, Math.round(normal.y) + y));
            assertEquals(3, f.picked(layer));
            saveFireflySnapshot(f, after, "mixed-categories");
            String base = "nglayer-layer-" + layer.getId();
            String image = FireflyMarkerRegistry.sprite(firefly).imageName;
            f.screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) f.app.getMap();
                org.maplibre.android.maps.Style nativeStyle = map.getMaplibreMap().getStyle();
                assertTrue(nativeStyle.getLayer(base) instanceof org.maplibre.android.style.layers.CircleLayer);
                assertNotNull(nativeStyle.getLayer(base + "-layout-firefly-1"));
                assertNotNull(nativeStyle.getImage(image));
                layer.setVisible(false); map.checkLayerVisibility(layer.getId());
                assertEquals("none", nativeStyle.getLayer(base + "-layout-firefly-1").getVisibility().getValue());
                layer.setVisible(true); map.checkLayerVisibility(layer.getId());
            });
            firefly.setIconImage("turn"); rules.setStyle("A", firefly); f.bind(layer, circle, rules);
            f.screen.onActivity(activity -> assertNotNull(((MapDrawable) f.app.getMap()).getMaplibreMap().getStyle().getLayer(base + "_icon")));
            f.bind(layer, circle, null);
            f.screen.onActivity(activity -> {
                org.maplibre.android.maps.Style nativeStyle = ((MapDrawable) f.app.getMap()).getMaplibreMap().getStyle();
                assertNull(nativeStyle.getLayer(base + "-layout-firefly-1")); assertNull(nativeStyle.getImage(image));
            });
            firefly.setIconImage(null); f.bind(layer, firefly, null);
            f.screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) f.app.getMap(); map.deleteLayerByID(layer.getId());
                assertNull(map.getMaplibreMap().getStyle().getLayer(base + "-layout-firefly-1"));
                assertNull(map.getMaplibreMap().getStyle().getImage(image));
            });
        }
    }

    @Test public void rulesOtherAndNullCategoriesPreserveStylesAndLabelsForAllGeometries() throws Exception {
        try(Fixture f=new Fixture()){
            for(int type:new int[]{GeoConstants.GTPoint,GeoConstants.GTMultiPoint,GeoConstants.GTLineString,GeoConstants.GTMultiLineString,GeoConstants.GTPolygon,GeoConstants.GTMultiPolygon}){
                VectorLayerUI layer=f.add(type);f.insert(layer,0," A ");f.insert(layer,1,"a");f.insert(layer,2,null);
                com.nextgis.maplib.display.Style other=style(type,Color.GREEN),category=style(type,Color.RED);richLabels(other);
                List<org.maplibre.geojson.Feature> features=f.bind(layer,style(type,Color.BLUE),rule(layer,other,category));
                assertEquals(3,features.size());
                for(org.maplibre.geojson.Feature feature:features){
                    String name=feature.getStringProperty(SIGNATURE);assertTrue(Arrays.asList("Альфа","Бета","Прочие").contains(name));
                    String key=type==GeoConstants.GTPolygon||type==GeoConstants.GTMultiPolygon?COLOR_FILL_RULE:COLOR_FILL;
                    assertEquals(name.equals("Прочие")?Color.GREEN:Color.RED,Color.parseColor(feature.getStringProperty(key)));
                    assertEquals(4,feature.getNumberProperty(TEXT_HALO_WIDTH).intValue());
                    assertEquals(6,feature.getNumberProperty(TEXT_PADDING).intValue());
                    assertEquals("uppercase",feature.getStringProperty(TEXT_TRANSFORM));
                    assertEquals(190/255f,feature.getNumberProperty(TEXT_OPACITY).floatValue(),.001);
                    assertFalse(feature.getBooleanProperty(TEXT_OPTIONAL));
                }
                f.screen.onActivity(activity->{assertNotNull(f.labels(layer));
                    boolean line=type==GeoConstants.GTLineString||type==GeoConstants.GTMultiLineString;
                    assertEquals("point",f.labels(layer).getSymbolPlacement().getValue());
                    assertEquals(line?"viewport":"auto",f.labels(layer).getTextRotationAlignment().getValue());});
            }
        }
    }

    @Test public void everyLabelSettingReachesTheNativeClassicSymbolLayerAfterReload() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");
            com.nextgis.maplib.display.Style other=style(GeoConstants.GTPoint,Color.GREEN);richLabels(other);
            other.setAlpha(0);other.setOutAlpha(0);
            com.nextgis.maplib.display.Style category=style(GeoConstants.GTPoint,Color.RED);category.setAlpha(0);category.setOutAlpha(0);
            f.bind(layer,other,rule(layer,other,category));
            assertTrue("The configured bold Russian label must actually render",f.redPixels()>10);
            Map<String,Object> constants=new HashMap<>();
            constants.put("TextLineHeight",1.8f);constants.put("TextPadding",6f);constants.put("TextKeepUpright",false);
            constants.put("TextMaxAngle",30f);constants.put("TextAllowOverlap",true);constants.put("TextOptional",false);
            constants.put("SymbolSpacing",80f);constants.put("SymbolPlacement","point");constants.put("TextRotationAlignment","auto");
            constants.put("TextFont",new String[]{"Open Sans Bold"});
            String[][] checks={{"TextSize",TEXT_SIZE},{"TextColor",TEXT_COLOR},{"TextHaloColor",TEXT_HALO_COLOR},
                    {"TextHaloWidth",TEXT_HALO_WIDTH},{"TextHaloBlur",TEXT_HALO_BLUR},{"TextOpacity",TEXT_OPACITY},
                    {"TextFont",TEXT_FONT},{"TextJustify",TEXT_JUSTIFY},{"TextTransform",TEXT_TRANSFORM},
                    {"TextLetterSpacing",TEXT_LETTER_SPACING},{"TextLineHeight",TEXT_LINE_HEIGHT},{"TextPadding",TEXT_PADDING},
                    {"TextKeepUpright",TEXT_KEEP_UPRIGHT},{"TextMaxAngle",TEXT_MAX_ANGLE},{"TextMaxWidth",TEXT_MAX_WIDTH},
                    {"TextAllowOverlap",TEXT_ALLOW_OVERLAP},{"TextOptional",TEXT_OPTIONAL},{"SymbolSpacing",SYMBOL_SPACING},
                    {"SymbolPlacement",SYMBOL_PLACEMENT},{"TextRotationAlignment",TEXT_ROTATION_ALIGNMENT},
                    {"TextAnchor",TEXT_ANCHOR},{"TextOffset",TEXT_OFFSETS}};
            f.screen.onActivity(activity->{for(String[] check:checks){try{
                PropertyValue<?> property=(PropertyValue<?>)SymbolLayer.class.getMethod("get"+check[0]).invoke(f.labels(layer));
                if(constants.containsKey(check[0])){
                    Object expected=constants.get(check[0]);assertNotNull(check[0],property.getValue());
                    if(expected instanceof Number)assertEquals(check[0],((Number)expected).doubleValue(),((Number)property.getValue()).doubleValue(),.001);
                    else if(expected instanceof String[])assertArrayEquals(check[0],(String[])expected,(String[])property.getValue());
                    else assertEquals(check[0],expected,property.getValue());
                    continue;
                }
                assertNotNull(check[0],property.getExpression());
                assertTrue("Native property lost "+check[1]+": "+property.getExpression(),property.getExpression().toString().contains(check[1]));
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}}});
            Set<Integer> fontPixelCounts=new HashSet<>();
            LabelAttributes categoryLabels=LabelAttributes.fromStyle(category);
            categoryLabels.setTextHaloWidth(0);categoryLabels.setTextOpacity(255);
            for(String font:new String[]{"Open Sans Regular","Open Sans Bold","Open Sans Italic","Open Sans Bold Italic"}){
                categoryLabels.setTextFont(font);f.bind(layer,other,rule(layer,other,category));
                f.screen.onActivity(activity->{org.maplibre.android.maps.Style nativeStyle=((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle();
                    SymbolLayer actual=f.labels(layer);
                    if(!Arrays.equals(new String[]{font},actual.getTextFont().getValue()))
                        actual=(SymbolLayer)nativeStyle.getLayer(actual.getId()+"-layout-1");
                    assertNotNull(actual);assertArrayEquals(new String[]{font},actual.getTextFont().getValue());});
                int pixels=f.redPixels();assertTrue("Bundled Cyrillic font must render: "+font,pixels>10);fontPixelCounts.add(pixels);
            }
            assertTrue("Changing the configured font must change the rendered glyphs",fontPixelCounts.size()>1);
        }
    }

    @Test public void explicitFalseDefaultAndEmptyLabelSettingsSurviveRuleRoundTrip() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");
            com.nextgis.maplib.display.Style other=style(GeoConstants.GTPoint,Color.GREEN),category=style(GeoConstants.GTPoint,Color.RED);richLabels(other);
            LabelAttributes labels=LabelAttributes.fromStyle(category);labels.setTextScaleWithZoom(false);labels.setTextOpacity(255);
            LabelAttributes.fromStyle(other).setTextZoomScaleStops("10:1,20:3");other.setSizeZoomScaleStops("10:1,20:3");
            labels.setTextZoomScaleStops(null);category.setSizeZoomScaleStops(null);
            labels.setTextKeepUpright(null);labels.setTextAllowOverlap(null);
            labels.setTextHaloWidth(LabelAttributes.DEFAULT_HALO_WIDTH);labels.setTextPadding(2);labels.setTextOptional(true);
            labels.setLabelTemplate("");category.setScaleSizeWithZoom(false);other.setScaleSizeWithZoom(true);
            org.maplibre.geojson.Feature feature=f.bind(layer,other,rule(layer,other,category)).get(0);
            assertFalse(feature.getBooleanProperty(TEXT_SCALE_WITH_ZOOM));assertFalse(feature.getBooleanProperty(SCALE_SIZE_WITH_ZOOM));
            assertEquals("",feature.getStringProperty(TEXT_ZOOM_STOPS));assertEquals("",feature.getStringProperty(SIZE_ZOOM_STOPS));
            assertEquals(1,feature.getNumberProperty(TEXT_OPACITY).floatValue(),0);
            assertEquals(1.5,feature.getNumberProperty(TEXT_HALO_WIDTH).doubleValue(),0);
            assertEquals(2,feature.getNumberProperty(TEXT_PADDING).intValue());assertTrue(feature.getBooleanProperty(TEXT_OPTIONAL));
            assertEquals("Альфа",feature.getStringProperty(SIGNATURE));
            assertTrue(feature.getBooleanProperty(TEXT_KEEP_UPRIGHT));assertFalse(feature.getBooleanProperty(TEXT_ALLOW_OVERLAP));
        }
    }

    @Test public void differentRuleLayoutSettingsCreateFilteredNativeGroupsAndCleanUpOnSimpleStyle() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");f.insert(layer,1,null);
            com.nextgis.maplib.display.Style other=style(GeoConstants.GTPoint,Color.GREEN),category=style(GeoConstants.GTPoint,Color.RED);
            other.setAlpha(0);other.setOutAlpha(0);category.setAlpha(0);category.setOutAlpha(0);
            ((SimpleMarkerStyle)other).setTextColor(Color.GREEN);
            LabelAttributes.fromStyle(other).setTextAllowOverlap(true);
            LabelAttributes.fromStyle(other).setTextLineHeight(2);
            LabelAttributes.fromStyle(category).setTextLineHeight(1.2f);
            List<org.maplibre.geojson.Feature> features=f.bind(layer,other,rule(layer,other,category));
            assertNotEquals(features.get(0).getStringProperty(RuleLabelLayouts.KEY),features.get(1).getStringProperty(RuleLabelLayouts.KEY));
            String childId="symbol-nglayer-layer-"+layer.getId()+"-layout-1";
            assertTrue("Category label in the child layer must render",f.redPixels()>10);
            f.screen.onActivity(activity->{SymbolLayer child=(SymbolLayer)((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle().getLayer(childId);
                assertNotNull(child);assertEquals(1.2,child.getTextLineHeight().getValue(),.001);assertNotNull(child.getFilter());
                expression(child.getTextColor(),TEXT_COLOR);expression(child.getTextOpacity(),TEXT_OPACITY);
                expression(child.getTextHaloWidth(),TEXT_HALO_WIDTH);
                assertEquals(2,f.labels(layer).getTextLineHeight().getValue(),.001);
                layer.setVisible(false);((MapDrawable)f.app.getMap()).checkLayerVisibility(layer.getId());
                assertEquals("none",child.getVisibility().getValue());});
            assertEquals(0,f.redPixels());
            f.bind(layer,other,null);
            f.screen.onActivity(activity->assertNull(((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle().getLayer(childId)));
        }
    }

    @Test public void labelZoomOpacityAndLayerOpacityActuallyChangeRenderedPixels() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");
            com.nextgis.maplib.display.Style other=style(GeoConstants.GTPoint,Color.GREEN);other.setAlpha(0);other.setOutAlpha(0);
            LabelAttributes labels=LabelAttributes.fromStyle(other);labels.setTextHaloWidth(0);labels.setTextAllowOverlap(true);
            labels.setLabelMinZoom(14);labels.setLabelMaxZoom(18);
            f.bind(layer,other,rule(layer,other,other));f.zoom(12);Thread.sleep(400);assertEquals(0,f.redPixels());
            f.zoom(16);Thread.sleep(400);assertTrue("Visible Russian label must have red pixels",f.redPixels()>10);
            f.zoom(19);Thread.sleep(400);assertEquals(0,f.redPixels());
            f.zoom(16);labels.setTextOpacity(0);f.bind(layer,other,rule(layer,other,other));assertEquals(0,f.redPixels());
            labels.setTextOpacity(255);layer.setLayerOpacity(0);f.bind(layer,other,rule(layer,other,other));assertEquals(0,f.redPixels());
            layer.setLayerOpacity(255);f.bind(layer,other,rule(layer,other,other));assertTrue(f.redPixels()>10);
        }
    }

    @Test public void categoryLabelZoomCanExtendBeyondTheOtherStyleZoomRange() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");
            com.nextgis.maplib.display.Style other=style(GeoConstants.GTPoint,Color.GREEN),category=style(GeoConstants.GTPoint,Color.RED);
            other.setAlpha(0);other.setOutAlpha(0);category.setAlpha(0);category.setOutAlpha(0);
            LabelAttributes defaults=LabelAttributes.fromStyle(other);defaults.setLabelMinZoom(14);defaults.setLabelMaxZoom(18);defaults.setTextAllowOverlap(true);
            LabelAttributes labels=LabelAttributes.fromStyle(category);labels.setLabelMinZoom(10);labels.setLabelMaxZoom(20);
            f.bind(layer,other,rule(layer,other,category));f.zoom(12);Thread.sleep(400);
            assertTrue("Category label must be visible below Other's minimum zoom",f.redPixels()>10);
            f.zoom(19);Thread.sleep(400);assertTrue("Category label must be visible above Other's maximum zoom",f.redPixels()>10);
            f.zoom(21);Thread.sleep(400);assertEquals("Category label must disappear beyond its own maximum zoom",0,f.redPixels());
        }
    }

    @Test public void markerShapesSizeAndOpacityActuallyChangeRenderedPixels() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");
            SimpleMarkerStyle marker=(SimpleMarkerStyle)style(GeoConstants.GTPoint,Color.RED);marker.setField(null);marker.setOutAlpha(0);marker.setSize(4);
            f.bind(layer,marker,rule(layer,marker,marker));int small=f.redPixels();assertTrue(small>10);
            marker.setSize(12);f.bind(layer,marker,rule(layer,marker,marker));assertTrue(f.redPixels()>small*2);
            for(int shape:new int[]{1,2,3,4,5,6,8}){marker.setType(shape);f.bind(layer,marker,rule(layer,marker,marker));assertTrue("Shape "+shape,f.redPixels()>5);}
            marker.setAlpha(0);f.bind(layer,marker,rule(layer,marker,marker));assertEquals(0,f.redPixels());
        }
    }

    @Test public void allMarkerLabelAnchorsAndAssetIconsReachTheNativeRenderer() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");
            SimpleMarkerStyle marker=(SimpleMarkerStyle)style(GeoConstants.GTPoint,Color.RED);
            String[] anchors={"top","top-right","right","bottom-right","bottom","bottom-left","left","top-left"};
            for(int index=0;index<anchors.length;index++){
                marker.setTextAlignment(index);
                org.maplibre.geojson.Feature feature=f.bind(layer,marker,rule(layer,marker,marker)).get(0);
                assertEquals(anchors[index],feature.getStringProperty(TEXT_ANCHOR));
                assertEquals(2,feature.getProperty(TEXT_OFFSETS).getAsJsonArray().size());
            }
            List<String> icons=MarkerIconRegistry.availableAssetIconNames(f.app.getAssets());assertFalse(icons.isEmpty());
            marker.setIconImage(icons.get(0));marker.setIconSize(10);marker.setIconRotate(45);
            marker.setIconOffsetX(2);marker.setIconOffsetY(3);marker.setIconAnchor(SimpleMarkerStyle.ICON_ANCHOR_BOTTOM);
            marker.setIconAllowOverlap(false);marker.setIconIgnorePlacement(true);
            org.maplibre.geojson.Feature feature=f.bind(layer,marker,rule(layer,marker,marker)).get(0);
            assertEquals(icons.get(0),feature.getStringProperty(ICON_IMAGE));assertEquals(45,feature.getNumberProperty(ICON_ROTATE).intValue());
            assertEquals("bottom",feature.getStringProperty(ICON_ANCHOR));assertFalse(feature.getBooleanProperty(ICON_ALLOW_OVERLAP));
            assertTrue(feature.getBooleanProperty(ICON_IGNORE_PLACEMENT));
            f.screen.onActivity(activity->{org.maplibre.android.maps.Style nativeStyle=((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle();
                assertNotNull(nativeStyle.getImage(icons.get(0)));
                SymbolLayer icon=(SymbolLayer)nativeStyle.getLayer("nglayer-layer-"+layer.getId()+"_icon");
                assertNotNull(icon);
                assertEquals(Boolean.FALSE,icon.getIconAllowOverlap().getValue());
                assertEquals(Boolean.TRUE,icon.getIconIgnorePlacement().getValue());});
        }
    }

    @Test public void polygonPatternsAndTranslatedFillUseRegisteredNativeImages() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPolygon);f.insert(layer,0,"A");
            SimplePolygonStyle fill=(SimplePolygonStyle)style(GeoConstants.GTPolygon,Color.RED);
            fill.setField(null);
            for(int pattern:PolygonPatternRegistry.ALL_PATTERN_IDS){
                fill.setFillPattern(pattern);
                org.maplibre.geojson.Feature feature=f.bind(layer,fill,rule(layer,fill,fill)).get(0);
                assertEquals(pattern,feature.getNumberProperty(FILL_PATTERN).intValue());
                if(pattern!=0)f.screen.onActivity(activity->assertNotNull(((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle()
                        .getImage(PolygonPatternRegistry.ICON_PREFIX+pattern)));
                assertTrue("Patterned polygon must retain its fill: "+pattern,f.redPixels()>10);
            }
            fill.setFill(false);f.bind(layer,fill,rule(layer,fill,fill));assertEquals(0,f.redPixels());
            fill.setFill(true);f.bind(layer,fill,rule(layer,fill,fill));assertTrue(f.redPixels()>10);
        }
    }

    @Test public void ruleGeometryConstantsPreserveCategoryPaintsPickingAndLifecycle() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI line=f.add(GeoConstants.GTLineString);f.insert(line,0,"A");f.insert(line,1,null);
            SimpleLineStyle other=(SimpleLineStyle)style(GeoConstants.GTLineString,Color.GREEN),category=(SimpleLineStyle)style(GeoConstants.GTLineString,Color.RED);
            other.setField(null);category.setField(null);category.setLineCap(2);category.setLineJoin(1);category.setLineMiterLimit(4);
            f.bind(line,other,rule(line,other,category));assertTrue(f.redPixels()>10);
            assertEquals("Both ordinary rule categories must remain pickable",2,f.picked(line));
            String lineId="nglayer-layer-"+line.getId(),lineChild=lineId+"-layout-1";
            f.screen.onActivity(activity->{MapDrawable map=(MapDrawable)f.app.getMap();
                org.maplibre.android.style.layers.LineLayer child=(org.maplibre.android.style.layers.LineLayer)map.getMaplibreMap().getStyle().getLayer(lineChild);
                assertNotNull(child);assertEquals("square",child.getLineCap().getValue());assertEquals(4,child.getLineMiterLimit().getValue(),.001);
                expression(child.getLineColor(),COLOR_FILL);
                line.setVisible(false);map.checkLayerVisibility(line.getId());assertEquals("none",child.getVisibility().getValue());});
            assertEquals(0,f.redPixels());
            f.bind(line,other,null);f.screen.onActivity(activity->assertNull(((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle().getLayer(lineChild)));

            VectorLayerUI polygon=f.add(GeoConstants.GTPolygon);f.insert(polygon,0,"A");
            SimplePolygonStyle fillOther=(SimplePolygonStyle)style(GeoConstants.GTPolygon,Color.GREEN),fillCategory=(SimplePolygonStyle)style(GeoConstants.GTPolygon,Color.RED);
            fillOther.setField(null);fillCategory.setField(null);fillCategory.setFillTranslateX(20);fillCategory.setFillTranslateY(30);
            f.bind(polygon,fillOther,rule(polygon,fillOther,fillCategory));assertTrue(f.redPixels()>10);
            String fillChild="nglayer-layer-"+polygon.getId()+"-layout-1";
            f.screen.onActivity(activity->{MapDrawable map=(MapDrawable)f.app.getMap();
                org.maplibre.android.style.layers.FillLayer child=(org.maplibre.android.style.layers.FillLayer)map.getMaplibreMap().getStyle().getLayer(fillChild);
                assertNotNull(child);assertArrayEquals(new Float[]{20f,30f},child.getFillTranslate().getValue());expression(child.getFillColor(),COLOR_FILL_RULE);
                polygon.setVisible(false);map.checkLayerVisibility(polygon.getId());assertEquals("none",child.getVisibility().getValue());});
            f.bind(polygon,fillOther,null);f.screen.onActivity(activity->assertNull(((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle().getLayer(fillChild)));

            VectorLayerUI point=f.add(GeoConstants.GTPoint);f.insert(point,0,"A");
            SimpleMarkerStyle iconOther=(SimpleMarkerStyle)style(GeoConstants.GTPoint,Color.GREEN),iconCategory=(SimpleMarkerStyle)style(GeoConstants.GTPoint,Color.RED);
            iconOther.setField(null);iconCategory.setField(null);iconOther.setIconImage(MarkerIconRegistry.availableAssetIconNames(f.app.getAssets()).get(0));
            iconCategory.setIconAllowOverlap(false);iconCategory.setIconIgnorePlacement(true);
            f.bind(point,iconOther,rule(point,iconOther,iconCategory));
            String iconChild="nglayer-layer-"+point.getId()+"_icon-layout-1";
            f.screen.onActivity(activity->{MapDrawable map=(MapDrawable)f.app.getMap();SymbolLayer child=(SymbolLayer)map.getMaplibreMap().getStyle().getLayer(iconChild);
                assertNotNull(child);assertEquals(Boolean.FALSE,child.getIconAllowOverlap().getValue());assertEquals(Boolean.TRUE,child.getIconIgnorePlacement().getValue());
                expression(child.getIconSize(),ICON_SIZE);assertNotNull(child.getIconOpacity().getValue());
                assertTrue(point.delete(false));assertNull(map.getMaplibreMap().getStyle().getLayer(iconChild));});
            f.layers.remove(point);
        }
    }

    @Test public void customAndPresetRuleDashesReachNativeAndResetOnSolidStyle() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTLineString);f.insert(layer,0,"A");f.insert(layer,1,null);
            SimpleLineStyle other=(SimpleLineStyle)style(GeoConstants.GTLineString,Color.GREEN),category=(SimpleLineStyle)style(GeoConstants.GTLineString,Color.RED);
            other.setField(null);category.setField(null);other.setType(SimpleLineStyle.LineStyleDash);category.setType(SimpleLineStyle.LineStyleDash);
            category.setDashArray("6,3");
            for(int preset=0;preset<4;preset++){
                other.setDashPreset(preset);f.bind(layer,other,rule(layer,other,category));
                assertEquals("Dashed categories must remain pickable",2,f.picked(layer));
                f.screen.onActivity(activity->{org.maplibre.android.maps.Style nativeStyle=((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle();
                    List<Float[]> arrays=new ArrayList<>();for(org.maplibre.android.style.layers.Layer nativeLayer:nativeStyle.getLayers())
                        if(nativeLayer.getId().startsWith("nglayer-layer-"+layer.getId()+"_dash_")) arrays.add(((org.maplibre.android.style.layers.LineLayer)nativeLayer).getLineDasharray().getValue());
                    assertEquals(2,arrays.size());assertTrue(arrays.stream().anyMatch(array->Arrays.equals(array,new Float[]{6f,3f})));
                    assertTrue(arrays.stream().anyMatch(array->Arrays.equals(array,MplStyleMapper.dashArray(other.getDashPreset()))));});
            }
            f.bind(layer,category,null);
            f.screen.onActivity(activity->assertArrayEquals(new Float[]{6f,3f},((org.maplibre.android.style.layers.LineLayer)
                    ((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle().getLayer("nglayer-layer-"+layer.getId())).getLineDasharray().getValue()));
            category.setType(SimpleLineStyle.LineStyleSolid);f.bind(layer,category,null);
            f.screen.onActivity(activity->{org.maplibre.android.maps.Style nativeStyle=((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle();
                assertTrue(((org.maplibre.android.style.layers.LineLayer)nativeStyle.getLayer("nglayer-layer-"+layer.getId())).getLineDasharray().isNull());
                for(org.maplibre.android.style.layers.Layer nativeLayer:nativeStyle.getLayers())assertFalse(nativeLayer.getId().startsWith("nglayer-layer-"+layer.getId()+"_dash_"));});
        }
    }

    @Test public void categoryGeometryZoomCurveAndExplicitScaleOffChangeNativeSize() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");
            SimpleMarkerStyle other=(SimpleMarkerStyle)style(GeoConstants.GTPoint,Color.GREEN),category=(SimpleMarkerStyle)style(GeoConstants.GTPoint,Color.RED);
            other.setField(null);category.setField(null);other.setOutAlpha(0);category.setOutAlpha(0);category.setSize(4);
            other.setScaleSizeWithZoom(true);other.setSizeZoomScaleStops("10:1,20:1");
            category.setScaleSizeWithZoom(true);category.setSizeZoomScaleStops("10:1,16:3,20:3");
            f.bind(layer,other,rule(layer,other,category));f.zoom(10);Thread.sleep(400);int small=f.redPixels();assertTrue(small>5);
            f.zoom(16);Thread.sleep(400);assertTrue("A category must use its own geometry curve",f.redPixels()>small*4);
            category.setScaleSizeWithZoom(false);
            assertFalse("Explicit false survives the stored rule",f.bind(layer,other,rule(layer,other,category)).get(0).getBooleanProperty(SCALE_SIZE_WITH_ZOOM));
            f.screen.onActivity(activity->{org.maplibre.android.maps.Style nativeStyle=((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle();
                GeoJsonSource source=nativeStyle.getSourceAs(layer.getPath().toString());
                for(org.maplibre.geojson.Feature feature:source.querySourceFeatures(null))
                    assertFalse("Native source receives scale=false",feature.getBooleanProperty(SCALE_SIZE_WITH_ZOOM));});
            f.zoom(10);Thread.sleep(400);int fixed=f.redPixels();f.zoom(16);Thread.sleep(400);
            assertEquals("Explicit scale=false must keep pixel size",fixed,f.redPixels());
        }
    }

    @Test public void categoryLabelZoomCurveAndExplicitScaleOffChangeNativeText() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI layer=f.add(GeoConstants.GTPoint);f.insert(layer,0,"A");
            com.nextgis.maplib.display.Style other=style(GeoConstants.GTPoint,Color.GREEN),category=style(GeoConstants.GTPoint,Color.RED);
            other.setAlpha(0);category.setAlpha(0);other.setOutAlpha(0);category.setOutAlpha(0);
            LabelAttributes defaults=LabelAttributes.fromStyle(other),labels=LabelAttributes.fromStyle(category);
            defaults.setTextAllowOverlap(true);defaults.setTextHaloWidth(0);defaults.setTextScaleWithZoom(true);defaults.setTextZoomScaleStops("10:1,20:1");
            labels.setTextScaleWithZoom(true);labels.setTextZoomScaleStops("10:0.5,16:2,20:2");
            f.bind(layer,other,rule(layer,other,category));f.zoom(10);Thread.sleep(400);int small=f.redPixels();assertTrue(small>5);
            f.zoom(16);Thread.sleep(400);int large=f.redPixels();
            assertTrue("A category must use its own label curve: zoom10="+small+", zoom16="+large,large>small*3);
            labels.setTextScaleWithZoom(false);f.bind(layer,other,rule(layer,other,category));
            f.zoom(10);Thread.sleep(400);int fixed=f.redPixels();f.zoom(16);Thread.sleep(400);assertEquals(fixed,f.redPixels());
        }
    }

    @Test public void lineAndPolygonPaintOptionsSurviveSqlStyleAndNativeBinding() throws Exception {
        try(Fixture f=new Fixture()){
            VectorLayerUI line=f.add(GeoConstants.GTLineString);f.insert(line,0,"A");
            SimpleLineStyle stroke=(SimpleLineStyle)style(GeoConstants.GTLineString,Color.RED);
            stroke.setLineCap(2);stroke.setLineJoin(2);stroke.setLineMiterLimit(4);stroke.setLineBlur(2);
            stroke.setLineOffset(3);stroke.setLineGapWidth(2);stroke.setLineOutlineMultiplier(4);
            stroke.setDashArray("6,3");stroke.setAlpha(128);stroke.setOutAlpha(64);
            for(int dash=0;dash<4;dash++){stroke.setDashPreset(dash);
                org.maplibre.geojson.Feature feature=f.bind(line,stroke,rule(line,stroke,stroke)).get(0);
                assertEquals(8,feature.getNumberProperty(LINE_BLUR).intValue());assertEquals(3,feature.getNumberProperty(LINE_OFFSET).intValue());
                assertEquals("square",feature.getStringProperty(LINE_CAP));assertEquals("bevel",feature.getStringProperty(LINE_JOIN));
                assertEquals(128/255f,feature.getNumberProperty(OPACITY).floatValue(),.001);
                assertEquals(64/255f,feature.getNumberProperty(STROKE_OPACITY).floatValue(),.001);
                f.screen.onActivity(activity->{org.maplibre.android.style.layers.LineLayer nativeLine=(org.maplibre.android.style.layers.LineLayer)
                        ((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle().getLayer("nglayer-layer-"+line.getId());
                    assertEquals("square",nativeLine.getLineCap().getValue());assertEquals("bevel",nativeLine.getLineJoin().getValue());
                    assertEquals(2,nativeLine.getLineMiterLimit().getValue(),.001);expression(nativeLine.getLineBlur(),LINE_BLUR);
                    expression(nativeLine.getLineGapWidth(),LINE_GAP_WIDTH);expression(nativeLine.getLineOffset(),LINE_OFFSET);});
            }
            VectorLayerUI polygon=f.add(GeoConstants.GTPolygon);f.insert(polygon,0,"A");
            SimplePolygonStyle fill=(SimplePolygonStyle)style(GeoConstants.GTPolygon,Color.RED);fill.setFillTranslateX(3);fill.setFillTranslateY(4);
            fill.setAlpha(128);fill.setOutAlpha(64);fill.setFill(false);
            org.maplibre.geojson.Feature feature=f.bind(polygon,fill,rule(polygon,fill,fill)).get(0);
            assertEquals(0,feature.getNumberProperty(OPACITY).floatValue(),0);
            assertEquals(3,feature.getProperty(FILL_TRANSLATE).getAsJsonArray().get(0).getAsInt());
            f.screen.onActivity(activity->{org.maplibre.android.style.layers.FillLayer nativeFill=(org.maplibre.android.style.layers.FillLayer)
                    ((MapDrawable)f.app.getMap()).getMaplibreMap().getStyle().getLayer("nglayer-layer-"+polygon.getId());
                assertArrayEquals(new Float[]{3f,4f},nativeFill.getFillTranslate().getValue());});
            fill.setFill(true);f.bind(polygon,fill,rule(polygon,fill,fill));assertTrue(f.redPixels()>10);
        }
    }
}
