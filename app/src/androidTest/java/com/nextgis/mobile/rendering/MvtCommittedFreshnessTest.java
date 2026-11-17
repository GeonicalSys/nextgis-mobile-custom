package com.nextgis.mobile.rendering;

import static org.junit.Assert.*;
import android.content.ContentUris;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoLinearRing;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.datasource.GeoPolygon;
import com.nextgis.maplib.display.FieldStyleRule;
import com.nextgis.maplib.display.LabelAttributes;
import com.nextgis.maplib.display.RuleFeatureRenderer;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.SqliteVectorTileProvider;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.map.MPLFeaturesUtils;
import com.nextgis.maplib.map.MplFeatureStyleProps;
import com.nextgis.maplib.util.SyncWorkspaceSession;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.mobile.util.VerificationEnvironment;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real committed mutations and normal project activation; no test reload after mutations. */
@RunWith(AndroidJUnit4.class)
public class MvtCommittedFreshnessTest {
    private int layerId;
    private String evidenceRun;
    private static final String PREFIX="MVT night ";
    private final JSONArray evidence=new JSONArray();

    @Test public void committedRowsReachCurrentFrameAndEqualIdsStayInsideTheirProject() throws Exception {
        GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);
        CollectorProjectRegistry.ProjectInfo original=CollectorProjectRegistry.getActiveProject(app);
        assertNotNull("An original workspace must exist for exact restoration",original);
        List<CollectorProjectRegistry.ProjectInfo> owned=new ArrayList<>();
        VectorStyleRenderingTest.Fixture f=null;
        String token=InstrumentationRegistry.getArguments().getString("nightRun","");
        if(!token.matches("[a-f0-9]{32}"))token=UUID.randomUUID().toString();
        evidenceRun=token;
        try {
            CollectorProjectRegistry.ProjectInfo a=CollectorProjectRegistry.createLocalProject(app,PREFIX+token+" A");owned.add(a);
            CollectorProjectRegistry.ProjectInfo b=CollectorProjectRegistry.createLocalProject(app,PREFIX+token+" B");owned.add(b);
            assertNotNull(a);assertNotNull(b);assertNotEquals(a.getMapPath(),b.getMapPath());
            String[] names=collidingPaths(a.getMapPath(),b.getMapPath());
            layerId=new File(a.getMapPath(),names[0]).hashCode()&0x7fffffff;
            assertTrue(CollectorProjectRegistry.activateProject(app,a.getProjectUid()));
            f=new VectorStyleRenderingTest.Fixture();
            VectorLayerUI layer=create(f,names[0]);
            Uri base=uri(f,layer);
            assertNotNull(app.getContentResolver().insert(base,values(42,-120,0,"RED","Альфа")));
            NativeSqliteVectorRenderingTest.reload(f,layer); // Initial attachment only.
            NativeSqliteVectorRenderingTest.assertStreaming(f,layer);
            CameraPosition camera=camera(f);
            expect(f,layer,"initial",Collections.singletonMap(42L,new Expected(-120,0,Color.RED,"Альфа")),camera);
            SqliteVectorTileProvider initial=NativeSqliteVectorRenderingTest.provider(f,layer);

            assertEquals(1,app.getContentResolver().update(ContentUris.withAppendedId(base,42),values(null,120,0,"BLUE","Бета"),null,null));
            expect(f,layer,"update",Collections.singletonMap(42L,new Expected(120,0,Color.BLUE,"Бета")),camera);
            assertFalse("Committed data must retire the previous tile owner",initial.isCurrent());
            assertOriginal(layer,42,120,0,"Бета");
            assertNoPaint(f,-120,0);

            assertNotNull(app.getContentResolver().insert(base,values(43,0,150,"GREEN","Гамма")));
            Map<Long,Expected> two=new LinkedHashMap<>();two.put(42L,new Expected(120,0,Color.BLUE,"Бета"));two.put(43L,new Expected(0,150,Color.GREEN,"Гамма"));
            expect(f,layer,"insert",two,camera);
            assertOriginal(layer,43,0,150,"Гамма");
            assertEquals(1,app.getContentResolver().delete(ContentUris.withAppendedId(base,42),null,null));
            Map<Long,Expected> remaining=Collections.singletonMap(43L,new Expected(0,150,Color.GREEN,"Гамма"));
            expect(f,layer,"delete",remaining,camera);
            assertNull(layer.getFeature(42));assertNoPaint(f,120,0);
            SqliteVectorTileProvider aProvider=NativeSqliteVectorRenderingTest.provider(f,layer);

            activate(f,b.getProjectUid());
            VectorLayerUI other=create(f,names[1]);
            assertEquals(layer.getId(),other.getId());
            assertNotEquals(layer.getPath().getAbsolutePath(),other.getPath().getAbsolutePath());
            assertNotNull(app.getContentResolver().insert(uri(f,other),values(43,0,150,"BLUE","Дельта")));
            NativeSqliteVectorRenderingTest.reload(f,other);
            expect(f,other,"project-b",Collections.singletonMap(43L,new Expected(0,150,Color.BLUE,"Дельта")),camera(f));
            assertFalse("Previous project provider must be terminal",aProvider.isCurrent());
            SqliteVectorTileProvider bProvider=NativeSqliteVectorRenderingTest.provider(f,other);
            activate(f,a.getProjectUid());
            VectorLayerUI restored=current(f);
            expect(f,restored,"project-a-return",remaining,camera(f));
            assertFalse(bProvider.isCurrent());
            activate(f,b.getProjectUid());
            expect(f,current(f),"project-b-return",Collections.singletonMap(43L,new Expected(0,150,Color.BLUE,"Дельта")),camera(f));
            Bitmap picture=f.snapshot();
            File screenshot=new File(app.getFilesDir(),"verification-mvt-freshness-"+token+".png");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(screenshot)){assertTrue(picture.compress(Bitmap.CompressFormat.PNG,100,out));}
            JSONObject result=new JSONObject().put("schema",1).put("run",token).put("status","passed")
                    .put("stages",evidence).put("mutations","normal ContentResolver commit notifications")
                    .put("project_switch","normal registry activation and Activity recreation")
                    .put("screenshot",screenshot.getName());
            Files.write(new File(app.getFilesDir(),"verification-mvt-freshness.json").toPath(),result.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            if(f!=null)f.close(); // No foreign layers are in Fixture.layers.
            for(CollectorProjectRegistry.ProjectInfo project:owned) {
                if(project==null)continue;
                assertTrue(project.getName().startsWith(PREFIX+token));
                assertNotEquals(original.getProjectUid(),project.getProjectUid());
                assertTrue(CollectorProjectRegistry.activateProject(app,project.getProjectUid()));
                assertTrue("Delete only an owned synthetic workspace",CollectorProjectRegistry.deleteActiveProject(app,project.getProjectUid(),"Local",()->true).isSuccess());
            }
            assertTrue("Restore the original workspace",CollectorProjectRegistry.activateProject(app,original.getProjectUid()));
        }
    }
    private static String[] collidingPaths(String first,String second) {
        // Use real persisted File.hashCode IDs. A forced in-memory setId is lost on reload.
        Map<Integer,String> candidates=new HashMap<>();Random random=new Random(0x4d565431L);
        for(int i=0;i<16384;i++){String name="mvt_night_"+Long.toUnsignedString(random.nextLong(),36);candidates.put(new File(first,name).hashCode()&0x7fffffff,name);}
        for(int i=0;i<2000000;i++) {
            String name="mvt_night_"+Long.toUnsignedString(random.nextLong(),36);String match=candidates.get(new File(second,name).hashCode()&0x7fffffff);
            if(match!=null)return new String[]{match,name};
        }
        throw new AssertionError("Could not construct an independent natural local-ID collision");
    }
    private static VectorLayerUI create(VectorStyleRenderingTest.Fixture f,String name) {
        AtomicReference<VectorLayerUI> result=new AtomicReference<>();
        f.screen.onActivity(activity->{try {
            MapDrawable map=(MapDrawable)f.app.getMap();
            VectorLayerUI layer=new VectorLayerUI(f.app,new File(map.getPath(),name));
            layer.setName("Owned MVT freshness fixture");map.addLayer(layer);
            layer.create(GeoConstants.GTPolygon,Arrays.asList(new Field(GeoConstants.FTString,"kind","Kind"),new Field(GeoConstants.FTString,"name","Name")));
            layer.setIsEditable(true);layer.setVisible(true);
            FieldStyleRule rule=new FieldStyleRule(layer);rule.setKey("kind");rule.setOtherStyle(style(Color.GRAY));
            rule.setStyle("RED",style(Color.RED));rule.setStyle("BLUE",style(Color.BLUE));rule.setStyle("GREEN",style(Color.GREEN));
            layer.setRenderer(new RuleFeatureRenderer(layer,rule,style(Color.GRAY)));assertTrue(layer.save());result.set(layer);
        }catch(Exception error){throw new AssertionError(error);}});return result.get();
    }
    private static SimplePolygonStyle style(int color) {
        SimplePolygonStyle style=new SimplePolygonStyle();style.setFill(true);style.setColor(color);style.setOutAlpha(0);style.setAlpha(255);
        style.setField("name");style.setTextColor(Color.BLACK);style.setTextSize(4f);
        LabelAttributes labels=LabelAttributes.fromStyle(style);labels.setTextAllowOverlap(true);labels.setTextHaloWidth(0);return style;
    }
    private static Uri uri(VectorStyleRenderingTest.Fixture f,VectorLayerUI layer){return SyncWorkspaceSession.bindUri(layer,Uri.parse("content://"+f.app.getAuthority()+"/"+layer.getPath().getName()));}
    private static GeoPolygon polygon(double x,double y) {
        GeoLinearRing ring=new GeoLinearRing();ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        for(double[] point:new double[][]{{x-50,y-50},{x+50,y-50},{x+50,y+50},{x-50,y+50},{x-50,y-50}}){GeoPoint p=new GeoPoint(point[0],point[1]);p.setCRS(GeoConstants.CRS_WEB_MERCATOR);ring.add(p);}
        GeoPolygon result=new GeoPolygon();result.setCRS(GeoConstants.CRS_WEB_MERCATOR);result.setOuterRing(ring);return result;
    }
    private static ContentValues values(Integer id,double x,double y,String kind,String label) throws Exception {
        ContentValues values=new ContentValues();if(id!=null)values.put(Constants.FIELD_ID,id);
        values.put(Constants.FIELD_GEOM,polygon(x,y).toBlob());values.put("kind",kind);values.put("name",label);return values;
    }
    private static void assertOriginal(VectorLayerUI layer,long id,double x,double y,String label) throws Exception {
        com.nextgis.maplib.datasource.Feature feature=layer.getFeature(id);assertNotNull(feature);
        assertEquals(label,feature.getFieldValueAsString("name"));
        assertEquals("Original full coordinates remain exact",polygon(x,y).toJSON().toString(),feature.getGeometry().toJSON().toString());
    }
    private static void activate(VectorStyleRenderingTest.Fixture f,String uid) throws Exception {
        assertTrue(CollectorProjectRegistry.activateProject(f.app,uid));f.screen.recreate();
        await("Normal Activity must attach the activated map",()->{
            final boolean[] ready={false};f.screen.onActivity(activity->{MapDrawable map=(MapDrawable)f.app.getMap();ready[0]=map.getMaplibreMap()!=null&&map.getMaplibreMap().getStyle()!=null&&map.getMaplibreMapView()==activity.findViewById(com.nextgis.mobile.R.id.mapViewMaplibre);});return ready[0];
        });
    }
    private VectorLayerUI current(VectorStyleRenderingTest.Fixture f) {
        AtomicReference<VectorLayerUI> result=new AtomicReference<>();f.screen.onActivity(activity->result.set((VectorLayerUI)((MapDrawable)f.app.getMap()).getLayerById(layerId)));assertNotNull(result.get());return result.get();
    }
    private static CameraPosition camera(VectorStyleRenderingTest.Fixture f){AtomicReference<CameraPosition> value=new AtomicReference<>();f.screen.onActivity(activity->value.set(((MapDrawable)f.app.getMap()).getMaplibreMap().getCameraPosition()));return value.get();}
    private void expect(VectorStyleRenderingTest.Fixture f,VectorLayerUI layer,String stage,Map<Long,Expected> expected,CameraPosition camera) throws Exception {
        try {
        await(stage+" must reach rendered geometry, IDs and labels without reload or camera movement",()->{
            Map<Long,List<org.maplibre.geojson.Feature>> bodies=rendered(f,layer,false),labels=rendered(f,layer,true);
            if(!bodies.keySet().equals(expected.keySet())||!labels.keySet().equals(expected.keySet()))return false;
            for(Map.Entry<Long,Expected> item:expected.entrySet()) {
                Expected e=item.getValue();
                for(org.maplibre.geojson.Feature label:labels.get(item.getKey()))
                    if(!label.hasProperty(MplFeatureStyleProps.SIGNATURE)||!e.label.equals(label.getStringProperty(MplFeatureStyleProps.SIGNATURE)))return false;
                try {double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                    for(org.maplibre.geojson.Feature body:bodies.get(item.getKey()))bounds(new JSONObject(body.geometry().toJson()).getJSONArray("coordinates"),bounds);
                    double cx=(bounds[0]+bounds[2])/2,cy=(bounds[1]+bounds[3])/2;
                    if(Math.abs(cx-e.x)>4||Math.abs(cy-e.y)>4)return false;
                }catch(Exception error){throw new AssertionError(error);}
            }return true;
        });
        assertEquals("Content updates must not move the camera",camera,camera(f));
        NativeSqliteVectorRenderingTest.assertStreaming(f,layer);f.renderedFrame();Bitmap bitmap=f.snapshot();
        for(Expected e:expected.values())assertPaint(bitmap,screen(f,e.x+25,e.y+25),e.color);
        evidence.put(new JSONObject().put("stage",stage).put("fids",new JSONArray(expected.keySet())).put("geometry_labels_pixels","passed").put("camera_unchanged",true));
        } catch(Exception|AssertionError failure) {
            try {
                JSONObject witness=new JSONObject().put("schema",1).put("run",evidenceRun).put("status","failed").put("stage",stage).put("completed_stages",evidence)
                    .put("expected_ids",new JSONArray(expected.keySet())).put("camera",camera(f).toString());
                JSONArray bodies=new JSONArray(),labels=new JSONArray();
                for(List<org.maplibre.geojson.Feature> fragments:rendered(f,layer,false).values())for(org.maplibre.geojson.Feature feature:fragments)bodies.put(new JSONObject(feature.toJson()));
                for(List<org.maplibre.geojson.Feature> fragments:rendered(f,layer,true).values())for(org.maplibre.geojson.Feature feature:fragments)labels.put(new JSONObject(feature.toJson()));
                witness.put("rendered_bodies",bodies).put("rendered_labels",labels);
                Files.write(new File(f.app.getFilesDir(),"verification-mvt-failure.json").toPath(),witness.toString().getBytes(StandardCharsets.UTF_8));
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(f.app.getFilesDir(),"verification-mvt-failure.png"))){f.snapshot().compress(Bitmap.CompressFormat.PNG,100,out);}
            } catch(Exception|AssertionError diagnostic){failure.addSuppressed(diagnostic);}
            throw failure;
        }
    }
    private static Map<Long,List<org.maplibre.geojson.Feature>> rendered(VectorStyleRenderingTest.Fixture f,VectorLayerUI layer,boolean labels) {
        Map<Long,List<org.maplibre.geojson.Feature>> result=new LinkedHashMap<>();
        f.screen.onActivity(activity->{MapDrawable map=(MapDrawable)f.app.getMap();String source=map.getVectorRenderSourceId(layer.getId());List<String> ids=new ArrayList<>();
            if(source==null)return;
            for(org.maplibre.android.style.layers.Layer child:map.getMaplibreMap().getStyle().getLayers()) {
                if(labels&&child instanceof SymbolLayer&&source.equals(((SymbolLayer)child).getSourceId())||!labels&&child instanceof FillLayer&&source.equals(((FillLayer)child).getSourceId()))ids.add(child.getId());
            }
            if(ids.isEmpty())return;
            for(org.maplibre.geojson.Feature hit:map.getMaplibreMap().queryRenderedFeatures(new RectF(0,0,map.getMaplibreMapView().getWidth(),map.getMaplibreMapView().getHeight()),ids.toArray(new String[0]))) {
                assertTrue("Every raw hit must have SQL identity",hit.hasProperty(MPLFeaturesUtils.prop_featureid));
                assertNotNull(hit.geometry());
                if(labels)assertEquals("Point",hit.geometry().type());
                else assertTrue(hit.geometry() instanceof org.maplibre.geojson.Polygon||hit.geometry() instanceof org.maplibre.geojson.MultiPolygon);
                long id=hit.getProperty(MPLFeaturesUtils.prop_featureid).getAsLong();
                List<org.maplibre.geojson.Feature> fragments=result.computeIfAbsent(id,key->new ArrayList<>());
                if(!fragments.isEmpty()&&labels)assertEquals("Duplicate tile labels must agree",fragments.get(0).getStringProperty(MplFeatureStyleProps.SIGNATURE),hit.getStringProperty(MplFeatureStyleProps.SIGNATURE));
                fragments.add(hit);
            }
        });return result;
    }
    private static void bounds(JSONArray value,double[] bounds) throws Exception {
        if(value.length()>=2&&value.opt(0) instanceof Number){double x=Math.toRadians(value.getDouble(0))*6378137.0,y=Math.log(Math.tan(Math.PI/4+Math.toRadians(value.getDouble(1))/2))*6378137.0;bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);}
        else for(int i=0;i<value.length();i++)bounds(value.getJSONArray(i),bounds);
    }
    private static PointF screen(VectorStyleRenderingTest.Fixture f,double x,double y){AtomicReference<PointF> p=new AtomicReference<>();f.screen.onActivity(activity->p.set(((MapDrawable)f.app.getMap()).getMaplibreMap().getProjection().toScreenLocation(new LatLng(Math.toDegrees(2*Math.atan(Math.exp(y/6378137.0))-Math.PI/2),Math.toDegrees(x/6378137.0)))));return p.get();}
    private static void assertPaint(Bitmap bitmap,PointF p,int color){int x=Math.round(p.x),y=Math.round(p.y);assertTrue(x>=0&&y>=0&&x<bitmap.getWidth()&&y<bitmap.getHeight());int actual=bitmap.getPixel(x,y);assertEquals(Color.red(color),Color.red(actual),25);assertEquals(Color.green(color),Color.green(actual),25);assertEquals(Color.blue(color),Color.blue(actual),25);}
    private static void assertNoPaint(VectorStyleRenderingTest.Fixture f,double x,double y) throws Exception {Bitmap image=f.snapshot();PointF p=screen(f,x+25,y+25);int color=image.getPixel(Math.round(p.x),Math.round(p.y));assertFalse("Removed geometry must leave no colored ghost",(Color.red(color)>180&&Color.green(color)<80&&Color.blue(color)<80)||(Color.blue(color)>180&&Color.red(color)<80&&Color.green(color)<80));}
    private static void await(String reason,BooleanSupplier condition) throws Exception {long until=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<until){if(condition.getAsBoolean())return;Thread.sleep(60);}assertTrue(reason,condition.getAsBoolean());}
    private static final class Expected {final double x,y;final int color;final String label;Expected(double x,double y,int color,String label){this.x=x;this.y=y;this.color=color;this.label=label;}}
}
