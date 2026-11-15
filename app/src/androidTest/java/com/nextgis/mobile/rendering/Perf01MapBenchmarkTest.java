package com.nextgis.mobile.rendering;

import android.graphics.Color;
import android.graphics.RectF;
import android.os.Debug;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Technical benchmark only: ready times are validated upper bounds including the observer. */
@RunWith(AndroidJUnit4.class)
public class Perf01MapBenchmarkTest {
    static final String STATE="perf01-fixture.json", OUTPUT="perf01-samples.json";
    static final int VERTICES=64;
    GISApplication app() {
        GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);
        assertEquals(VerificationEnvironment.PACKAGE,app.getPackageName());
        assertTrue("PERF-01 fixtures require an emulator",VerificationEnvironment.isEmulator());
        return app;
    }
    @Test public void ordinaryTechnicalMap() throws Exception { run("ordinary",20); }
    @Test public void heavyTechnicalMap() throws Exception { run("heavy",80); }
    String arg(String key,String fallback) { return InstrumentationRegistry.getArguments().getString(key,fallback); }
    static long now() { return SystemClock.elapsedRealtimeNanos(); }
    static double ms(long ns) { return ns/1_000_000.0; }
    static String text(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8); }
    static void write(File f,JSONObject value) throws Exception { Files.write(f.toPath(),value.toString().getBytes(StandardCharsets.UTF_8)); }
    interface Action { void run(MainActivity activity) throws Exception; }
    static void ui(ActivityScenario<MainActivity> screen,Action action) {
        screen.onActivity(activity->{try { action.run(activity); } catch(Exception e) { throw new AssertionError(e); }});
    }
    void preferences(GISApplication app) {
        assertTrue(android.preference.PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putBoolean(com.nextgis.mobile.util.AppSettingsConstants.KEY_PREF_INTRO,true)
            .putBoolean(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG,false)
            .putFloat(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL,11)
            .putLong(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SCROLL_X,Double.doubleToRawLongBits(0))
            .putLong(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SCROLL_Y,Double.doubleToRawLongBits(0)).commit());
    }
    static String processIdentity() throws Exception {
        String stat=text(new File("/proc/self/stat"));
        String[] fields=stat.substring(stat.lastIndexOf(')')+2).trim().split("\\s+");
        return android.os.Process.myPid()+":"+fields[19]; // field22: starttime after the two leading fields
    }
    JSONObject seed(GISApplication app,String cohort,int side) throws Exception {
        assertFalse("Seed must start in a freshly reset verification workspace",new File(app.getFilesDir(),STATE).exists());
        JSONObject state=new JSONObject().put("schema",2).put("run",arg("perfRun","smoke"))
            .put("cohort",cohort).put("side",side).put("features",side*side+80)
            .put("vertices",65).put("seed_process",processIdentity()).put("crs","EPSG:3857").put("radius_m",140).put("spacing_m",320)
            .put("style_contract","green/cyan alpha170; blue outline2; two fixed label123 red2.5 Open Sans Bold")
            .put("label_ids",new JSONArray().put((side/2-10)*side+side/2-10+1).put(80));
        JSONArray ids=new JSONArray(),hashes=new JSONArray();
        try(VectorStyleRenderingTest.Fixture fixture=new VectorStyleRenderingTest.Fixture()) {
            MapDrawable map=(MapDrawable)app.getMap();state.put("root",map.getPath().getCanonicalPath());
            for(int group=0;group<2;group++) {
                int width=group==0?side:10,height=group==0?side:8;
                NGWVectorLayerUI layer=new NGWVectorLayerUI(app,new File(map.getPath(),"perf01_"+cohort+"_"+group));
                List<Field> fields=Collections.singletonList(new Field(GeoConstants.FTString,"test_label","Synthetic label"));
                int role=group;
                ui(fixture.screen,activity->{
                    layer.setName("PERF-01 technical "+cohort+" "+role);
                    layer.create(GeoConstants.GTPolygon,fields);
                    layer.setIsEditable(false);layer.setVisible(true);
                    layer.setLayerOriginMetadata(LayerOriginMetadata.manualNgwLayer(0,LayerOriginMetadata.RENDER_MODE_LOCAL_VECTOR_TILES));
                    SimplePolygonStyle style=new SimplePolygonStyle(role==0?Color.GREEN:Color.CYAN,Color.BLUE);
                    style.setAlpha(170);style.setOutAlpha(255);style.setWidth(2);style.setField("test_label");
                    style.setTextSize(2.5f);style.setTextColor(Color.RED);style.getLabelAttributes().setTextFont("Open Sans Bold");
                    layer.setRenderer(new SimpleFeatureRenderer(layer,style));map.addLayer(layer);
                });
                android.database.sqlite.SQLiteDatabase db=DatabaseContext.getDatabaseForLayer(layer,false);
                db.beginTransaction();
                try {
                    for(int i=0;i<width*height;i++) {
                        double x=(i%width-(width-1)/2.0)*320,y=(i/width-(height-1)/2.0)*320;
                        GeoLinearRing ring=new GeoLinearRing();ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                        for(int v=0;v<=VERTICES;v++) {
                            double angle=2*Math.PI*(v%VERTICES)/VERTICES;
                            ring.add(new GeoPoint(x+140*Math.cos(angle),y+140*Math.sin(angle)));
                        }
                        GeoPolygon polygon=new GeoPolygon();polygon.setCRS(GeoConstants.CRS_WEB_MERCATOR);polygon.setOuterRing(ring);
                        com.nextgis.maplib.datasource.Feature f=new com.nextgis.maplib.datasource.Feature(i+1,fields);
                        assertTrue(f.setFieldValue("test_label",i+1==state.getJSONArray("label_ids").getInt(group)?"123":""));
                        f.setGeometry(polygon);layer.createFeatureBatch(f,db,false);
                    }
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                assertTrue(layer.save());ids.put(layer.getId());hashes.put(digest(layer,width*height));
            }
            ui(fixture.screen,a->{
                map.setVisible(true);
                for(com.nextgis.maplib.api.ILayer old:map.getLayers()) {
                    boolean owned=old.getId()==ids.getInt(0)||old.getId()==ids.getInt(1);
                    if(old instanceof com.nextgis.maplib.api.ILayerView) ((com.nextgis.maplib.api.ILayerView)old).setVisible(owned);
                    assertTrue(old.save());
                }
                map.setZoomAndCenter(11,new GeoPoint(0,0),false,0);assertTrue(map.save());
            });
        }
        state.put("ids",ids).put("input_sha256",hashes);write(new File(app.getFilesDir(),STATE),state);return state;
    }
    static String digest(VectorLayer layer,int expected) throws Exception {
        String table=layer.getPath().getName();assertTrue(table.matches("[A-Za-z0-9_]+"));
        MessageDigest sha=MessageDigest.getInstance("SHA-256");int rows=0;
        try(android.database.Cursor c=DatabaseContext.getDatabaseForLayer(layer,false).query(table,
                new String[]{Constants.FIELD_ID,Constants.FIELD_GEOM,"test_label"},null,null,null,null,Constants.FIELD_ID)) {
            while(c.moveToNext()) {
                assertEquals(++rows,c.getLong(0));byte[] blob=c.getBlob(1);assertNotNull(blob);assertTrue(blob.length<8192);
                sha.update(ByteBuffer.allocate(12).putLong(c.getLong(0)).putInt(blob.length).array());sha.update(blob);
                assertFalse(c.isNull(2));byte[] label=c.getString(2).getBytes(StandardCharsets.UTF_8);
                sha.update(ByteBuffer.allocate(4).putInt(label.length).array());sha.update(label);
            }
        }
        assertEquals(expected,rows);StringBuilder out=new StringBuilder();for(byte b:sha.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    void inputsUnchanged(GISApplication app,JSONObject state,int side) throws Exception {
        MapDrawable map=(MapDrawable)app.getMap();assertEquals(state.getString("root"),map.getPath().getCanonicalPath());
        for(int i=0;i<2;i++)assertEquals(state.getJSONArray("input_sha256").getString(i),digest((VectorLayer)map.getLayerById(state.getJSONArray("ids").getInt(i)),i==0?side*side:80));
    }
    long clearDerivedCache(GISApplication app) throws Exception {
        MapDrawable map=(MapDrawable)app.getMap();assertTrue(map.getMaplibreMapView()==null||!map.getMaplibreMapView().isAttachedToWindow());
        File parent=app.getCacheDir().getCanonicalFile(),cache=new File(parent,"local-vector-tiles-v1");
        assertEquals(cache.getAbsolutePath(),cache.getCanonicalPath());assertFalse(Files.isSymbolicLink(cache.toPath()));
        if(!cache.exists())return 0;long bytes=0;File[] files=cache.listFiles();assertNotNull(files);
        for(File f:files) {
            assertTrue(f.isFile());assertFalse(Files.isSymbolicLink(f.toPath()));assertEquals(cache,f.getCanonicalFile().getParentFile());
            assertTrue("Unexpected derived-cache entry",f.getName().matches("[0-9a-f]{64}\\.pbf|tile-[A-Za-z0-9._-]+\\.part"));
            bytes+=f.length();Files.delete(f.toPath());
        }
        assertEquals(0,Objects.requireNonNull(cache.list()).length);return bytes;
    }
    static class Frames implements AutoCloseable {
        final org.maplibre.android.maps.MapView view;
        final LinkedBlockingQueue<Long> frames=new LinkedBlockingQueue<>(4);
        final AtomicLong first=new AtomicLong();
        final org.maplibre.android.maps.MapView.OnDidFinishRenderingFrameListener listener=(fully,encoding,rendering)->{
            if(fully){long n=now();first.compareAndSet(0,n);if(!frames.offer(n)){frames.poll();frames.offer(n);}}
        };
        Frames(org.maplibre.android.maps.MapView view){this.view=view;view.addOnDidFinishRenderingFrameListener(listener);}
        public void close(){view.removeOnDidFinishRenderingFrameListener(listener);}
    }
    static boolean camera(MapDrawable map,double lon) {
        if(map.getMaplibreMap()==null)return false;org.maplibre.android.camera.CameraPosition c=map.getMaplibreMap().getCameraPosition();
        return c.target!=null&&Math.abs(c.zoom-11)<.001&&Math.abs(c.target.getLatitude())<1e-7&&Math.abs(c.target.getLongitude()-lon)<1e-7&&Math.abs(c.bearing)<.001&&Math.abs(c.tilt)<.001;
    }
    static void move(MapDrawable map,double lon) {
        map.setZoomAndCenter(11,new GeoPoint(Math.toRadians(lon)*6378137,0),false,0);
        map.getMaplibreMap().moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
            new org.maplibre.android.camera.CameraPosition.Builder().target(new org.maplibre.android.geometry.LatLng(0,lon)).zoom(11).bearing(0).tilt(0).build()));
    }
    /** Circle-fit oracle authored from the grid; no production tile encoder supplies expectations. */
    static Set<Long> required(double[][] p,int width,int height) {
        double area=0;for(int i=0;i<4;i++)area+=p[i][0]*p[(i+1)%4][1]-p[(i+1)%4][0]*p[i][1];
        assertTrue(Double.isFinite(area)&&Math.abs(area)>1);Set<Long> result=new HashSet<>();
        for(int i=0;i<width*height;i++) {
            double x=(i%width-(width-1)/2.0)*320,y=(i/width-(height-1)/2.0)*320;boolean inside=true;
            for(int j=0;j<4;j++){double[] a=p[j],b=p[(j+1)%4];double dx=b[0]-a[0],dy=b[1]-a[1];
                if(Math.signum(area)*(dx*(y-a[1])-dy*(x-a[0]))<=142*Math.hypot(dx,dy)){inside=false;break;}}
            if(inside)result.add((long)i+1);
        }
        return result;
    }
    static void oracle() {
        double[][] p={{-500,-500},{500,-500},{500,500},{-500,500}};
        assertEquals(new HashSet<>(Arrays.asList(6L,7L,10L,11L)),required(p,4,4));
        assertEquals(required(p,4,4),required(new double[][]{p[0],p[3],p[2],p[1]},4,4));
        Set<Long> diamond=new HashSet<>();for(long i=1;i<=16;i++)if(i!=1&&i!=4&&i!=13&&i!=16)diamond.add(i);
        assertEquals(diamond,required(new double[][]{{0,-900},{900,0},{0,900},{-900,0}},4,4));
        assertTrue(required(new double[][]{{-100,-100},{100,-100},{100,100},{-100,100}},4,4).isEmpty());
    }
    static JSONObject quality(MapDrawable map,JSONObject state,int side) throws Exception {
        org.maplibre.android.maps.MapView view=map.getMaplibreMapView();assertNotNull(view);
        assertTrue(view.isAttachedToWindow()&&view.isShown()&&view.hasWindowFocus()&&view.getWidth()>0&&view.getHeight()>0);
        assertTrue("Declared camera differs: "+map.getMaplibreMap().getCameraPosition(),camera(map,0));
        assertNotNull(map.getMaplibreMap().getStyle());assertTrue("Style is not fully loaded",map.getMaplibreMap().getStyle().isFullyLoaded());
        double[][] corners=new double[4][2];int w=view.getWidth(),h=view.getHeight();
        android.graphics.PointF[] xy={new android.graphics.PointF(0,0),new android.graphics.PointF(w,0),new android.graphics.PointF(w,h),new android.graphics.PointF(0,h)};
        for(int i=0;i<4;i++){org.maplibre.android.geometry.LatLng p=map.getMaplibreMap().getProjection().fromScreenLocation(xy[i]);
            corners[i][0]=Math.toRadians(p.getLongitude())*6378137;corners[i][1]=Math.log(Math.tan(Math.PI/4+Math.toRadians(p.getLatitude())/2))*6378137;}
        JSONArray coverage=new JSONArray();
        for(int group=0;group<2;group++) {
            int id=state.getJSONArray("ids").getInt(group),count=group==0?side*side:80;
            com.nextgis.maplib.api.ILayer layer=map.getLayerById(id);String source=map.getVectorRenderSourceId(id);
            assertTrue(map.isSqliteVectorLayer(id));assertNotNull(map.getSqliteVectorProvider(id));assertTrue(map.getSqliteVectorProvider(id).isCurrent());
            assertTrue(map.getMaplibreMap().getStyle().getSource(source) instanceof org.maplibre.android.style.sources.CustomVectorSource);
            assertNotNull(map.getLayerFeatures(layer));assertTrue(map.getLayerFeatures(layer).isEmpty());
            Set<Long> expected=required(corners,group==0?side:10,group==0?side:8),seen=new HashSet<>();assertFalse(expected.isEmpty());
            for(org.maplibre.geojson.Feature f:map.getMaplibreMap().queryRenderedFeatures(new RectF(0,0,w,h),"nglayer-layer-"+id)) {
                assertTrue(f.geometry() instanceof org.maplibre.geojson.Polygon||f.geometry() instanceof org.maplibre.geojson.MultiPolygon);
                assertFalse(f.hasProperty(NativeVectorTileEncoder.LABEL_ANCHOR));long fid=f.getNumberProperty(MPLFeaturesUtils.prop_featureid).longValue();
                assertTrue(fid>=1&&fid<=count);seen.add(fid);
            }
            assertTrue("Missing authored visible FIDs",seen.containsAll(expected));int labels=0;
            JSONArray symbols=new JSONArray();
            for(org.maplibre.android.style.layers.Layer nativeLayer:map.getMaplibreMap().getStyle().getLayers()) {
                if(nativeLayer instanceof org.maplibre.android.style.layers.SymbolLayer&&nativeLayer.getId().contains(Integer.toString(id))) {
                    org.maplibre.android.style.layers.SymbolLayer symbol=(org.maplibre.android.style.layers.SymbolLayer)nativeLayer;
                    List<org.maplibre.geojson.Feature> hits=map.getMaplibreMap().queryRenderedFeatures(new RectF(0,0,w,h),nativeLayer.getId());
                    JSONArray examples=new JSONArray();
                    for(org.maplibre.geojson.Feature f:hits) {
                        if(examples.length()<2)examples.put(String.valueOf(f.properties()));
                        if(source.equals(symbol.getSourceId())&&f.hasProperty(MPLFeaturesUtils.prop_signature_text)
                                &&"123".equals(f.getStringProperty(MPLFeaturesUtils.prop_signature_text))
                                &&f.getNumberProperty(MPLFeaturesUtils.prop_featureid).longValue()==state.getJSONArray("label_ids").getLong(group))labels++;
                    }
                    symbols.put(new JSONObject().put("id",symbol.getId()).put("source",symbol.getSourceId()).put("hits",hits.size())
                        .put("minzoom",symbol.getMinZoom()).put("maxzoom",symbol.getMaxZoom()).put("text",String.valueOf(symbol.getTextField()))
                        .put("filter",String.valueOf(symbol.getFilter())).put("visibility",String.valueOf(symbol.getVisibility())).put("examples",examples));
                }
            }
            if(labels==0) {
                JSONObject diagnostic=new JSONObject().put("group",group).put("source",source).put("symbols",symbols);
                try { diagnostic.put("style",((VectorLayer)layer).getDefaultStyleNoExcept().toJSON());
                    diagnostic.put("styled_original",map.getSqliteVectorProvider(id).readOriginalFeature(1).properties()); }
                catch(Exception unavailable){diagnostic.put("diagnostic_error",unavailable.toString());}
                fail("Expected placed123 labels: "+diagnostic);
            }
            coverage.put(new JSONObject().put("required",expected.size()).put("seen",seen.size()).put("placed_labels",labels).put("source",source));
        }
        return new JSONObject().put("width_px",w).put("height_px",h).put("density_dpi",view.getResources().getDisplayMetrics().densityDpi)
            .put("font_scale",view.getResources().getConfiguration().fontScale).put("zoom",11).put("longitude",0).put("latitude",0).put("bearing",0).put("tilt",0).put("coverage",coverage);
    }
    static long rss() throws Exception {
        for(String line:Files.readAllLines(new File("/proc/self/status").toPath(),StandardCharsets.UTF_8))if(line.startsWith("VmRSS:"))return Long.parseLong(line.trim().split("\\s+")[1])*1024;
        throw new AssertionError("VmRSS is unavailable");
    }
    static class RssSampler implements AutoCloseable {
        final AtomicLong peak=new AtomicLong(),samples=new AtomicLong();volatile boolean running=true;volatile Exception error;final Thread thread;
        RssSampler(){thread=new Thread(()->{while(running){try{peak.accumulateAndGet(rss(),Math::max);samples.incrementAndGet();Thread.sleep(100);}
            catch(InterruptedException stop){return;}catch(Exception unavailable){error=unavailable;return;}}},"Perf01RssObserver");thread.start();}
        void reset() throws Exception {peak.set(rss());samples.set(1);}
        public void close() throws Exception {
            // /proc reads use interruptible channels: interrupting a normal finalization
            // can manufacture ClosedByInterruptException. The next100ms tick observes
            // the stop flag; a bounded join still fails if this owned observer hangs.
            running=false;thread.join(2000);assertFalse("RSS observer did not stop",thread.isAlive());if(error!=null)throw error;
        }
    }
    static Object statValue(String value) { try { return value==null?JSONObject.NULL:Long.parseLong(value); } catch(NumberFormatException unavailable) { return JSONObject.NULL; } }
    static JSONObject memory(RssSampler sampler) throws Exception {
        Debug.MemoryInfo info=new Debug.MemoryInfo();Debug.getMemoryInfo(info);Runtime runtime=Runtime.getRuntime();
        String graphics=info.getMemoryStat("summary.graphics");
        long currentRss=rss();
        return new JSONObject().put("java_used_bytes",runtime.totalMemory()-runtime.freeMemory()).put("native_allocated_bytes",Debug.getNativeHeapAllocatedSize())
            .put("pss_bytes",info.getTotalPss()*1024L).put("rss_bytes",currentRss).put("observed_peak_rss_bytes",Math.max(sampler.peak.get(),currentRss))
            .put("rss_samples",sampler.samples.get()+1).put("rss_interval_ms",100).put("graphics_pss_kib",statValue(graphics));
    }
    JSONObject measure(ActivityScenario<MainActivity> screen,GISApplication app,JSONObject state,int side,Frames frames,RssSampler sampler,long began,long cpuBegan,long attached,int index,String mode) throws Exception {
        int timeout=Integer.parseInt(arg("perfReadyTimeoutSeconds","120"));assertTrue(timeout>=30&&timeout<=120);
        long deadline=now()+TimeUnit.SECONDS.toNanos(timeout),probeNs=0,frameAt=0;int probes=0;Throwable last=null;JSONObject ready=null;
        while(now()<deadline) {
            Long event=frames.frames.poll(2,TimeUnit.SECONDS);if(event==null){ui(screen,a->((MapDrawable)app.getMap()).getMaplibreMap().triggerRepaint());continue;}
            if(event<began)continue;long probe=now();AtomicReference<JSONObject> found=new AtomicReference<>();
            try{ui(screen,a->found.set(quality((MapDrawable)app.getMap(),state,side)));ready=found.get();frameAt=event;}
            catch(AssertionError incomplete){last=incomplete;}
            finally{probeNs+=now()-probe;probes++;}
            if(ready!=null)break;
            Thread.sleep(250);frames.frames.clear();ui(screen,a->((MapDrawable)app.getMap()).getMaplibreMap().triggerRepaint());
        }
        if(ready==null)try {saveScene(app);}catch(Exception|AssertionError diagnosticFailure){android.util.Log.w("Perf01Evidence","Failure screenshot unavailable",diagnosticFailure);}
        assertNotNull("No validated frame within"+timeout+"s; last="+last,ready);long ended=now(),cpu=android.os.Process.getElapsedCpuTime()-cpuBegan;
        JSONObject sample=new JSONObject().put("index",index).put("mode",mode).put("process",processIdentity()).put("quality",ready)
            .put("validated_ready_upper_ms",ms(ended-began)).put("validated_sdk_frame_candidate_ms",ms(frameAt-began))
            .put("observer_attach_delay_ms",ms(attached-began)).put("quality_probe_ms",ms(probeNs)).put("quality_probes",probes)
            .put("process_cpu_upper_ms",cpu).put("memory",memory(sampler)).put("bounded_diagnostic_context",new JSONObject(MapRuntimeDiagnostics.forEvent(System.currentTimeMillis())));
        return sample;
    }
    static void saveScene(GISApplication app) throws Exception {
        android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(java.io.FileOutputStream file=new java.io.FileOutputStream(new File(app.getFilesDir(),"perf01-scene.png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file));}
        finally{bitmap.recycle();}
    }
    void run(String cohort,int side) throws Exception {
        GISApplication app=app();oracle();String stage=arg("perfStage","smoke");
        if(stage.equals("seed")){seed(app,cohort,side);return;}
        boolean smoke=stage.equals("smoke");assertTrue(smoke||stage.equals("sample"));
        JSONObject state=smoke?seed(app,cohort,side):new JSONObject(text(new File(app.getFilesDir(),STATE)));
        assertEquals(2,state.getInt("schema"));assertEquals(cohort,state.getString("cohort"));assertEquals(side,state.getInt("side"));
        assertEquals(side*side+80,state.getInt("features"));assertEquals(65,state.getInt("vertices"));assertEquals(arg("perfRun","smoke"),state.getString("run"));
        String mode=smoke?"smoke":arg("perfMode","fresh");assertTrue(Arrays.asList("smoke","fresh","warm").contains(mode));
        if(mode.equals("fresh"))assertNotEquals(state.getString("seed_process"),processIdentity());
        int count=Integer.parseInt(arg("perfSamples","1"));assertTrue(count>=1&&count<=20);if(!mode.equals("warm"))assertEquals(1,count);
        preferences(app);long cleared=mode.equals("fresh")?clearDerivedCache(app):0;
        JSONObject output=new JSONObject().put("schema",1).put("fixture",state).put("cohort",cohort).put("mode",mode)
            .put("phase_index",Integer.parseInt(arg("perfSample","0")))
            .put("status","running").put("cleared_derived_cache_bytes",cleared).put("timing_scope","ActivityScenario launch or live camera return; instrumentation/validation included; OS/font cache uncontrolled");
        JSONArray samples=new JSONArray();output.put("samples",samples);File out=new File(app.getFilesDir(),OUTPUT);
        write(out,output);
        AtomicReference<Frames> observed=new AtomicReference<>();
        try(RssSampler sampler=new RssSampler()) {
            sampler.reset();long began=now(),cpuBegan=android.os.Process.getElapsedCpuTime();
            try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)) {
            try {
            long deadline=now()+TimeUnit.SECONDS.toNanos(30);
            while(observed.get()==null&&now()<deadline){ui(screen,a->{MapDrawable map=(MapDrawable)app.getMap();if(map.getMaplibreMap()!=null&&map.getMaplibreMapView()!=null&&map.getMaplibreMap().getStyle()!=null
                        &&map.getMaplibreMapView()==a.findViewById(com.nextgis.mobile.R.id.mapViewMaplibre)&&map.getMaplibreMapView().isAttachedToWindow()) {
                        app.getGpsEventSource().removeListener(a.getMapFragment());observed.set(new Frames(map.getMaplibreMapView()));
                        // Declare the same scene after Activity/MapView restoration. Preferences alone
                        // do not control delayed view state; this work stays inside the opening bound.
                        move(map,0);
                    }});if(observed.get()==null)Thread.sleep(25);}
            assertNotNull(observed.get());Frames frames=observed.get();long attached=now();ui(screen,a->((MapDrawable)app.getMap()).getMaplibreMap().triggerRepaint());
            JSONObject initial=measure(screen,app,state,side,frames,sampler,began,cpuBegan,attached,Integer.parseInt(arg("perfSample","0")),mode);
            if(!mode.equals("warm"))samples.put(initial);
            else {
                MapDrawable map=(MapDrawable)app.getMap();Object[] providers={map.getSqliteVectorProvider(state.getJSONArray("ids").getInt(0)),map.getSqliteVectorProvider(state.getJSONArray("ids").getInt(1))};
                for(int index=0;index<count;index++) {
                    frames.frames.clear();AtomicLong awayBegan=new AtomicLong();ui(screen,a->{awayBegan.set(now());move(map,1.2);});
                    long awayDeadline=now()+TimeUnit.SECONDS.toNanos(30);boolean away=false;
                    while(!away&&now()<awayDeadline){Long event=frames.frames.poll(2,TimeUnit.SECONDS);if(event==null||event<awayBegan.get())continue;AtomicReference<Boolean> matched=new AtomicReference<>(false);ui(screen,a->matched.set(camera(map,1.2)));away=matched.get();}
                    assertTrue("Away camera must render before return",away);sampler.reset();frames.frames.clear();AtomicLong start=new AtomicLong(),cpu=new AtomicLong();
                    ui(screen,a->{start.set(now());cpu.set(android.os.Process.getElapsedCpuTime());move(map,0);});
                    JSONObject sample=measure(screen,app,state,side,frames,sampler,start.get(),cpu.get(),start.get(),index,mode);
                    for(int i=0;i<2;i++)assertSame("Warm scene must keep the live provider",providers[i],map.getSqliteVectorProvider(state.getJSONArray("ids").getInt(i)));
                    sample.put("same_live_providers",true);samples.put(sample);write(out,output);
                }
                output.put("unmeasured_warmup",initial);
            }
            inputsUnchanged(app,state,side);output.put("input_hashes_verified_after_measurement",true);
            if("true".equals(arg("perfScreenshot","false")))saveScene(app);
            output.put("status","passed");write(out,output);
            } finally { if(observed.get()!=null)ui(screen,a->observed.get().close()); }
            }
            if(smoke||"true".equals(arg("perfCleanup","false"))) {
                inputsUnchanged(app,state,side);MapDrawable map=(MapDrawable)app.getMap();
                for(int i=0;i<2;i++) {
                    VectorLayer layer=(VectorLayer)map.getLayerById(state.getJSONArray("ids").getInt(i));
                    assertEquals("perf01_"+cohort+"_"+i,layer.getPath().getName());assertTrue(layer.delete(false));
                }
                assertTrue(map.save());Files.delete(new File(app.getFilesDir(),STATE).toPath());
                output.put("fixture_cleaned",true);write(out,output);
            }
        } catch(Throwable error){output.put("status","failed").put("error",error.toString());write(out,output);throw error;}
    }
}
