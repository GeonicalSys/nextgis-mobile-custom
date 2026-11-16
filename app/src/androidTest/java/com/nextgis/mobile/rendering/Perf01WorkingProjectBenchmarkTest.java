package com.nextgis.mobile.rendering;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.os.SystemClock;
import android.content.SyncResult;
import android.graphics.RectF;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.util.AccountUtil;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.NGWUtil;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.mobile.activity.MainActivity;
import static org.junit.Assert.*;

/** Explicit real-data stages are opt-in; the default Full case checks isolation only. */
@RunWith(AndroidJUnit4.class)
public class Perf01WorkingProjectBenchmarkTest {
    static final String SERVER="setnovo.nextgis.com", OUTPUT="perf01-working-result.json";
    static final int PROJECT=482, RESOURCE=400;
    static final String INPUT="perf01-working-input.json", STATE="perf01-working-state.json", ORACLE="perf01-working-oracle.json";
    String arg(String name,String fallback) {
        return InstrumentationRegistry.getArguments().getString(name,fallback);
    }
    GISApplication app() {
        GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);
        assertEquals(VerificationEnvironment.PACKAGE,app.getPackageName());
        assertFalse("Real-device stage requires a physical phone",VerificationEnvironment.isEmulator());
        assertEquals("Primary phone user only",0,android.os.Process.myUid()/100000);
        return app;
    }
    static String serverUrl(GISApplication app,Account account) throws Exception {
        URI uri=new URI(app.getAccountUrl(account));
        if(!"https".equalsIgnoreCase(uri.getScheme())||!SERVER.equalsIgnoreCase(uri.getHost())
                ||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
                ||uri.getPort()!=-1&&uri.getPort()!=443||!(uri.getPath().isEmpty()||"/".equals(uri.getPath())))return null;
        return "https://"+SERVER;
    }
    static Account account(GISApplication app) throws Exception {
        Account chosen=null;
        for(Account candidate:AccountManager.get(app).getAccountsByType(app.getAccountsType())) {
            if(serverUrl(app,candidate)==null)continue;
            assertNull("Select one verification account for the approved server",chosen);chosen=candidate;
        }
        return chosen;
    }
    static void write(GISApplication app,JSONObject result) throws Exception {
        Files.write(new File(app.getFilesDir(),OUTPUT).toPath(),result.toString().getBytes(StandardCharsets.UTF_8));
    }
    /** No password, token, account name, business fields or geometries are exported. */
    static JSONObject getMetadata(GISApplication app,Account account) throws Exception {
        String url=serverUrl(app,account);assertNotNull(url);
        long begin=SystemClock.elapsedRealtimeNanos();
        HttpResponse response=NetworkUtil.get(NGWUtil.getResourceUrl(url,RESOURCE),
                app.getAccountLogin(account),app.getAccountPassword(account),false);
        JSONObject result=new JSONObject().put("metadata_http_status",response.getResponseCode())
                .put("metadata_get_ms",(SystemClock.elapsedRealtimeNanos()-begin)/1_000_000.0);
        if(response.isOk()) {
            String body=response.getResponseBody();assertNotNull(body);assertTrue(body.length()<=1048576);
            JSONObject json=new JSONObject(body);assertEquals(RESOURCE,json.getJSONObject("resource").getInt("id"));
            result.put("resource_class",json.getJSONObject("resource").getString("cls"));
            result.put("field_count",json.getJSONObject("feature_layer").getJSONArray("fields").length());
            assertEquals("Approved layer must really be PostGIS","postgis_layer",result.getString("resource_class"));
            HttpResponse count=NetworkUtil.get(NGWUtil.getFeatureCountUrl(url,RESOURCE,null),
                    app.getAccountLogin(account),app.getAccountPassword(account),false);
            result.put("data_read_http_status",count.getResponseCode());
            if(count.isOk()) {
                assertTrue(count.getResponseBody().length()<=65536);
                long total=new JSONObject(count.getResponseBody()).getLong("total_count");assertTrue(total>=0);
                result.put("unfiltered_server_count",total);
            }
        }
        return result;
    }
    @Test public void readOnlyWorkingProject() throws Exception {
        GISApplication app=app();String stage=arg("perfStage","guard");
        JSONObject result=new JSONObject().put("schema",1).put("stage",stage).put("project_id",PROJECT)
                .put("resource_id",RESOURCE).put("server",SERVER).put("server_write_authority",false);
        if(stage.equals("guard")) {
            result.put("status","guard_only_no_real_data_acceptance");write(app,result);return;
        }
        assertEquals("482",arg("perfProject",""));
        String run=arg("perfRun","");assertTrue("An explicit run nonce is required",run.matches("[0-9a-f]{32}"));
        result.put("run",run).put("activation_record_present",AccountUtil.isUserExists(app));
        if(!stage.equals("probe")) { measureStage(app,stage,result);return; }
        Account chosen=account(app);result.put("verification_server_account_present",chosen!=null);
        if(chosen==null) {result.put("status","auth_required");write(app,result);return;}
        JSONObject metadata=getMetadata(app,chosen);
        for(java.util.Iterator<String> keys=metadata.keys();keys.hasNext();) {String key=keys.next();result.put(key,metadata.get(key));}
        result.put("status",metadata.getInt("metadata_http_status")==200&&metadata.optInt("data_read_http_status")==200
                ?"authenticated_data_read":"data_read_rejected");
        write(app,result);
    }

    /** The benchmark can call only the existing incoming path; accidental generic sync fails. */
    static class ReadOnlyLayer extends NGWVectorLayerUI {
        ReadOnlyLayer(GISApplication app,File path){super(app,path);}
        @Override public boolean sendLocalChanges(SyncResult ignored) {
            throw new AssertionError("PERF01 explicitly forbids sending local changes");
        }
        void postgis(){mNGWLayerType=com.nextgis.maplib.datasource.ngw.Connection.NGWResourceTypePostgisLayer;}
        int remoteType(){return mNGWLayerType;}
    }
    static JSONObject read(File file) throws Exception {
        assertTrue(file.isFile()&&file.length()>0&&file.length()<=8*1024*1024);
        return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
    }
    static void save(File file,JSONObject value) throws Exception {
        Files.write(file.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
    }
    static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}
    static JSONObject inspect(ReadOnlyLayer layer,boolean envelopes) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");int count=0;long blobBytes=0;JSONArray boxes=new JSONArray();
        try(android.database.Cursor c=DatabaseContext.getDatabaseForLayer(layer,false).query(layer.getPath().getName(),null,null,null,null,null,Constants.FIELD_ID)) {
            for(String column:c.getColumnNames())sha.update((column+"\u0000").getBytes(StandardCharsets.UTF_8));
            int idColumn=c.getColumnIndexOrThrow(Constants.FIELD_ID),geometryColumn=c.getColumnIndexOrThrow(Constants.FIELD_GEOM);
            while(c.moveToNext()) {
                count++;assertTrue("Bounded real benchmark input",count<=50000);
                for(int i=0;i<c.getColumnCount();i++) {
                    int type=c.getType(i);sha.update((byte)type);
                    byte[] value=type==android.database.Cursor.FIELD_TYPE_BLOB?c.getBlob(i):type==android.database.Cursor.FIELD_TYPE_NULL?new byte[0]:c.getString(i).getBytes(StandardCharsets.UTF_8);
                    sha.update(ByteBuffer.allocate(4).putInt(value.length).array());sha.update(value);
                }
                byte[] blob=c.getBlob(geometryColumn);
                if(blob!=null) {
                    blobBytes+=blob.length;
                    if(envelopes) {
                        GeoGeometry geometry=GeoGeometryFactory.fromBlob(blob);GeoEnvelope e=geometry.getEnvelope();
                        if(e.isInit())boxes.put(new JSONArray().put(c.getLong(idColumn)).put(e.getMinX()).put(e.getMinY()).put(e.getMaxX()).put(e.getMaxY()));
                    }
                }
            }
        }
        JSONObject result=new JSONObject().put("count",count).put("input_sha256",hex(sha.digest())).put("geometry_blob_bytes",blobBytes);
        if(envelopes)result.put("boxes",boxes);return result;
    }
    ReadOnlyLayer loaded(GISApplication app,JSONObject state) throws Exception {
        MapDrawable map=(MapDrawable)app.getMap();assertEquals(state.getString("root"),map.getPath().getCanonicalPath());
        String name=state.getString("layer_name");assertTrue(name.matches("perf01_ro_[0-9a-f]{32}"));
        File path=new File(map.getPath(),name);assertEquals(path.getAbsolutePath(),path.getCanonicalPath());
        ReadOnlyLayer layer=new ReadOnlyLayer(app,path);assertTrue(layer.load());layer.setParent(map);
        assertEquals(RESOURCE,layer.getRemoteId());assertEquals(4,layer.remoteType());
        assertEquals(2,layer.getSyncDirection());return layer;
    }
    JSONObject input(GISApplication app) throws Exception {
        JSONObject source=read(new File(app.getFilesDir(),INPUT));assertEquals(PROJECT,source.getInt("project_id"));
        assertEquals(RESOURCE,source.getJSONObject("config").getInt("id"));assertEquals(4,source.getJSONObject("config").getInt("ngw_layer_type"));
        assertEquals(2,source.getJSONObject("config").getInt("sync_direction"));
        assertEquals("collector:setnovo.nextgis.com:482",source.getString("project_uid"));return source;
    }
    void seed(GISApplication app,JSONObject result) throws Exception {
        assertFalse("Clean only this task's previous owned fixture before reseeding",new File(app.getFilesDir(),STATE).exists());
        JSONObject source=input(app),config=source.getJSONObject("config");Account account=account(app);assertNotNull("AUTH_REQUIRED",account);
        JSONObject state=new JSONObject().put("run",arg("perfRun","")).put("layer_name","perf01_ro_"+arg("perfRun",""))
                .put("project_id",PROJECT).put("resource_id",RESOURCE).put("district",source.optString("district",""));
        new Perf01MapBenchmarkTest().preferences(app);
        try(VectorStyleRenderingTest.Fixture fixture=new VectorStyleRenderingTest.Fixture();Perf01MapBenchmarkTest.RssSampler sampler=new Perf01MapBenchmarkTest.RssSampler()) {
            Perf01MapBenchmarkTest.ui(fixture.screen,a->a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            MapDrawable map=(MapDrawable)app.getMap();state.put("root",map.getPath().getCanonicalPath());
            ReadOnlyLayer layer=new ReadOnlyLayer(app,new File(map.getPath(),state.getString("layer_name")));layer.setParent(map);
            layer.postgis();layer.setName("PERF01 read-only project482 layer400");layer.setAccountName(account.name);
            layer.setRemoteId(RESOURCE);layer.setSyncType(Constants.SYNC_DATA);layer.setSyncDirection(2);
            layer.setServerWhere(config.optString("server_where",""));layer.setCollectorDistrictOverride(source.optString("district",""));
            long begin=Perf01MapBenchmarkTest.now(),cpu=android.os.Process.getElapsedCpuTime();
            layer.createFromNGW(null);
            result.put("initial_import_upper_ms",Perf01MapBenchmarkTest.ms(Perf01MapBenchmarkTest.now()-begin))
                    .put("process_cpu_upper_ms",android.os.Process.getElapsedCpuTime()-cpu).put("memory",Perf01MapBenchmarkTest.memory(sampler));
            assertEquals(4,layer.remoteType());
            Perf01MapBenchmarkTest.ui(fixture.screen,a->{
                JSONObject local=layer.toJSON();
                if(config.has("renderer_properties"))local.put("renderer_properties",config.get("renderer_properties"));
                if(config.has("feature_label_field"))local.put("feature_label_field",config.get("feature_label_field"));
                layer.fromJSON(local);layer.setSyncType(Constants.SYNC_DATA);layer.setSyncDirection(2);layer.setIsEditable(false);
                layer.setLayerOriginMetadata(LayerOriginMetadata.fromJSON(config.optJSONObject("layer_origin")));
                layer.setVisible(true);assertTrue(layer.save());map.addLayer(layer);map.setVisible(true);
                for(com.nextgis.maplib.api.ILayer item:map.getLayers()) {
                    if(item instanceof com.nextgis.maplib.api.ILayerView)((com.nextgis.maplib.api.ILayerView)item).setVisible(item.getId()==layer.getId());
                    assertTrue(item.save());
                }
                assertTrue(map.save());
            });
            JSONObject inspected=inspect(layer,true);assertTrue(inspected.getInt("count")>0);JSONArray boxes=inspected.getJSONArray("boxes");assertTrue(boxes.length()>0);
            JSONArray first=boxes.getJSONArray(0);double x=(first.getDouble(1)+first.getDouble(3))/2,y=(first.getDouble(2)+first.getDouble(4))/2;
            double span=Math.max(first.getDouble(3)-first.getDouble(1),first.getDouble(4)-first.getDouble(2));
            double zoom=Math.min(13,Math.max(4,Math.floor(Math.log(40075016.68557849/Math.max(100,span*4))/Math.log(2))));
            state.put("layer_id",layer.getId()).put("input_sha256",inspected.getString("input_sha256")).put("count",inspected.getInt("count"))
                    .put("geometry_blob_bytes",inspected.getLong("geometry_blob_bytes")).put("x",x).put("y",y).put("zoom",zoom)
                    .put("latitude",Math.toDegrees(2*Math.atan(Math.exp(y/6378137))-Math.PI/2)).put("longitude",Math.toDegrees(x/6378137))
                    .put("source_render_mode",config.optJSONObject("layer_origin")==null?"unspecified":config.getJSONObject("layer_origin").optString("render_mode"));
            save(new File(app.getFilesDir(),ORACLE),new JSONObject().put("boxes",boxes));
        }
        save(new File(app.getFilesDir(),STATE),state);result.put("fixture",state).put("status","seeded_read_only_copy");
    }
    static void move(MapDrawable map,JSONObject state,double delta) throws Exception {
        double lon=state.getDouble("longitude")+delta;
        map.setZoomAndCenter((float)state.getDouble("zoom"),new GeoPoint(state.getDouble("x")+Math.toRadians(delta)*6378137,state.getDouble("y")),false,0);
        map.getMaplibreMap().moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(new org.maplibre.android.camera.CameraPosition.Builder()
                .target(new org.maplibre.android.geometry.LatLng(state.getDouble("latitude"),lon)).zoom(state.getDouble("zoom")).bearing(0).tilt(0).build()));
    }
    static boolean camera(MapDrawable map,JSONObject state,double delta) throws Exception {
        org.maplibre.android.camera.CameraPosition c=map.getMaplibreMap().getCameraPosition();
        return c.target!=null&&Math.abs(c.zoom-state.getDouble("zoom"))<.001&&Math.abs(c.target.getLatitude()-state.getDouble("latitude"))<1e-6
                &&Math.abs(c.target.getLongitude()-state.getDouble("longitude")-delta)<1e-6&&Math.abs(c.bearing)<.001&&Math.abs(c.tilt)<.001;
    }
    static JSONObject quality(MapDrawable map,JSONObject state,JSONArray boxes) throws Exception {
        org.maplibre.android.maps.MapView view=map.getMaplibreMapView();assertTrue(view.isAttachedToWindow()&&view.isShown()&&view.hasWindowFocus());
        assertTrue("Camera differs from declared real-data scene",camera(map,state,0));assertTrue(map.getMaplibreMap().getStyle().isFullyLoaded());
        int id=state.getInt("layer_id"),w=view.getWidth(),h=view.getHeight();
        org.maplibre.android.geometry.LatLng tl=map.getMaplibreMap().getProjection().fromScreenLocation(new android.graphics.PointF(2,2));
        org.maplibre.android.geometry.LatLng br=map.getMaplibreMap().getProjection().fromScreenLocation(new android.graphics.PointF(w-2,h-2));
        double minX=Math.toRadians(tl.getLongitude())*6378137,maxX=Math.toRadians(br.getLongitude())*6378137;
        double maxY=Math.log(Math.tan(Math.PI/4+Math.toRadians(tl.getLatitude())/2))*6378137,minY=Math.log(Math.tan(Math.PI/4+Math.toRadians(br.getLatitude())/2))*6378137;
        Set<Long> expected=new HashSet<>(),seen=new HashSet<>();
        for(int i=0;i<boxes.length();i++){JSONArray b=boxes.getJSONArray(i);if(b.getDouble(1)>minX&&b.getDouble(3)<maxX&&b.getDouble(2)>minY&&b.getDouble(4)<maxY)expected.add(b.getLong(0));}
        assertFalse("No independently bounded visible original",expected.isEmpty());List<String> fills=new ArrayList<>();
        for(org.maplibre.android.style.layers.Layer layer:map.getMaplibreMap().getStyle().getLayers())
            if(layer instanceof org.maplibre.android.style.layers.FillLayer&&layer.getId().contains(Integer.toString(id)))fills.add(layer.getId());
        assertFalse(fills.isEmpty());
        for(org.maplibre.geojson.Feature feature:map.getMaplibreMap().queryRenderedFeatures(new RectF(0,0,w,h),fills.toArray(new String[0]))) {
            assertTrue(feature.geometry() instanceof org.maplibre.geojson.Polygon||feature.geometry() instanceof org.maplibre.geojson.MultiPolygon);
            if(feature.hasProperty(MPLFeaturesUtils.prop_featureid))seen.add(feature.getNumberProperty(MPLFeaturesUtils.prop_featureid).longValue());
        }
        assertTrue("Missing fully visible original FIDs",seen.containsAll(expected));boolean sqlite=map.isSqliteVectorLayer(id);
        if(sqlite){assertNotNull(map.getSqliteVectorProvider(id));assertTrue(map.getSqliteVectorProvider(id).isCurrent());assertTrue(map.getLayerFeatures(map.getLayerById(id)).isEmpty());}
        return new JSONObject().put("required",expected.size()).put("seen",seen.size()).put("width_px",w).put("height_px",h)
                .put("density_dpi",view.getResources().getDisplayMetrics().densityDpi).put("font_scale",view.getResources().getConfiguration().fontScale)
                .put("sqlite_direct",sqlite).put("label_correctness","not_independently_accepted_in_this_stage");
    }
    static JSONObject ready(androidx.test.core.app.ActivityScenario<MainActivity> screen,MapDrawable map,JSONObject state,JSONArray boxes,
            Perf01MapBenchmarkTest.Frames frames,Perf01MapBenchmarkTest.RssSampler sampler,long begin,long cpu) throws Exception {
        long deadline=Perf01MapBenchmarkTest.now()+TimeUnit.SECONDS.toNanos(120);Throwable last=null;int probes=0;long probeNs=0;
        while(Perf01MapBenchmarkTest.now()<deadline) {
            Long event=frames.frames.poll(2,TimeUnit.SECONDS);if(event==null||event<begin){Perf01MapBenchmarkTest.ui(screen,a->map.getMaplibreMap().triggerRepaint());continue;}
            AtomicReference<JSONObject> quality=new AtomicReference<>();long probe=Perf01MapBenchmarkTest.now();probes++;
            try {Perf01MapBenchmarkTest.ui(screen,a->quality.set(quality(map,state,boxes)));}
            catch(AssertionError failure){last=failure;}finally{probeNs+=Perf01MapBenchmarkTest.now()-probe;}
            if(quality.get()!=null)return new JSONObject().put("validated_ready_upper_ms",Perf01MapBenchmarkTest.ms(Perf01MapBenchmarkTest.now()-begin))
                    .put("process_cpu_upper_ms",android.os.Process.getElapsedCpuTime()-cpu).put("quality_probe_ms",Perf01MapBenchmarkTest.ms(probeNs))
                    .put("quality_probes",probes).put("quality",quality.get()).put("process",Perf01MapBenchmarkTest.processIdentity()).put("memory",Perf01MapBenchmarkTest.memory(sampler));
            frames.frames.clear();Thread.sleep(100);Perf01MapBenchmarkTest.ui(screen,a->map.getMaplibreMap().triggerRepaint());
        }
        fail("No independently validated real-data frame; last="+last);return null;
    }
    void measureStage(GISApplication app,String stage,JSONObject result) throws Exception {
        try {
            if(stage.equals("seed")){seed(app,result);write(app,result);return;}
            JSONObject state=read(new File(app.getFilesDir(),STATE));assertEquals(arg("perfRun",""),state.getString("run"));result.put("fixture",state);
            ReadOnlyLayer layer=(stage.equals("pull")||stage.equals("cleanup"))?loaded(app,state):null;
            if(layer!=null)assertEquals(state.getString("input_sha256"),inspect(layer,false).getString("input_sha256"));
            if(stage.equals("cleanup")) {
                MapDrawable map=(MapDrawable)app.getMap();
                assertTrue(layer.delete(false));map.removeLayer(layer);assertTrue(map.save());
                assertFalse(layer.getPath().exists());assertNull(map.getLayerById(state.getInt("layer_id")));
                Files.delete(new File(app.getFilesDir(),STATE).toPath());Files.delete(new File(app.getFilesDir(),ORACLE).toPath());
                result.put("status","owned_fixture_cleaned_account_preserved");write(app,result);return;
            }
            JSONArray samples=new JSONArray();result.put("samples",samples);int n=Integer.parseInt(arg("perfSamples","1"));assertTrue(n>=1&&n<=20);
            try(Perf01MapBenchmarkTest.RssSampler sampler=new Perf01MapBenchmarkTest.RssSampler()) {
                if(stage.equals("pull")) {
                    JSONObject source=input(app);Account account=account(app);assertNotNull(account);assertEquals(layer.getAccountName(),account.name);
                    new Perf01MapBenchmarkTest().preferences(app);
                    try(ActivityScenario<MainActivity> awake=ActivityScenario.launch(MainActivity.class)) {
                    Perf01MapBenchmarkTest.ui(awake,a->a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                    for(int i=0;i<n;i++) {
                        layer.setSyncType(Constants.SYNC_DATA);layer.setSyncDirection(2);layer.setCollectorDistrictOverride(source.optString("district",""));
                        SyncResult sync=new SyncResult();sampler.reset();long begin=Perf01MapBenchmarkTest.now(),cpu=android.os.Process.getElapsedCpuTime();
                        long rx=android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()),tx=android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());
                        assertTrue(layer.getChangesFromServer(app.getAuthority(),sync));long end=Perf01MapBenchmarkTest.now();
                        assertEquals("Pull may not silently downgrade to metadata-only",Constants.SYNC_DATA,layer.getSyncType()&Constants.SYNC_DATA);
                        assertEquals(0,sync.stats.numIoExceptions+sync.stats.numAuthExceptions+sync.stats.numParseExceptions+sync.stats.numConflictDetectedExceptions);
                        JSONObject sample=new JSONObject().put("index",i).put("pull_upper_ms",Perf01MapBenchmarkTest.ms(end-begin))
                                .put("process_cpu_upper_ms",android.os.Process.getElapsedCpuTime()-cpu).put("memory",Perf01MapBenchmarkTest.memory(sampler))
                                .put("uid_rx_bytes",rx<0?JSONObject.NULL:android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())-rx)
                                .put("uid_tx_bytes",tx<0?JSONObject.NULL:android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid())-tx)
                                .put("input_hash_after",inspect(layer,false).getString("input_sha256"));
                        assertEquals("Server inputs changed; do not pool revisions",state.getString("input_sha256"),sample.getString("input_hash_after"));
                        samples.put(sample);save(new File(app.getFilesDir(),OUTPUT),result);
                    }
                    }
                } else {
                    assertTrue(stage.equals("fresh")||stage.equals("warm"));if(stage.equals("fresh"))assertEquals(1,n);
                    JSONArray boxes=read(new File(app.getFilesDir(),ORACLE)).getJSONArray("boxes");new Perf01MapBenchmarkTest().preferences(app);
                    MapDrawable map=(MapDrawable)app.getMap();AtomicReference<Perf01MapBenchmarkTest.Frames> observer=new AtomicReference<>();
                    sampler.reset();long begin=Perf01MapBenchmarkTest.now(),cpu=android.os.Process.getElapsedCpuTime();
                    try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)) {
                        try {
                            long deadline=Perf01MapBenchmarkTest.now()+TimeUnit.SECONDS.toNanos(30);
                            while(observer.get()==null&&Perf01MapBenchmarkTest.now()<deadline) {
                                Perf01MapBenchmarkTest.ui(screen,a->{if(map.getMaplibreMap()!=null&&map.getMaplibreMap().getStyle()!=null&&map.getMaplibreMapView()==a.findViewById(com.nextgis.mobile.R.id.mapViewMaplibre)) {
                                    a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                    app.getGpsEventSource().removeListener(a.getMapFragment());observer.set(new Perf01MapBenchmarkTest.Frames(map.getMaplibreMapView()));move(map,state,0);
                                }});if(observer.get()==null)Thread.sleep(25);
                            }
                            assertNotNull(observer.get());Perf01MapBenchmarkTest.Frames frames=observer.get();
                            JSONObject initial=ready(screen,map,state,boxes,frames,sampler,begin,cpu);
                            if(stage.equals("fresh"))samples.put(initial.put("index",Integer.parseInt(arg("perfSample","0"))));
                            else {
                                Object provider=map.getSqliteVectorProvider(state.getInt("layer_id"));
                                for(int i=0;i<n;i++) {
                                    frames.frames.clear();long awayStart=Perf01MapBenchmarkTest.now();Perf01MapBenchmarkTest.ui(screen,a->move(map,state,2));
                                    boolean away=false;long awayDeadline=Perf01MapBenchmarkTest.now()+TimeUnit.SECONDS.toNanos(30);
                                    while(!away&&Perf01MapBenchmarkTest.now()<awayDeadline){Long f=frames.frames.poll(2,TimeUnit.SECONDS);if(f==null||f<awayStart)continue;AtomicReference<Boolean> matches=new AtomicReference<>(false);Perf01MapBenchmarkTest.ui(screen,a->matches.set(camera(map,state,2)));away=matches.get();}
                                    assertTrue("Away scene did not render",away);sampler.reset();frames.frames.clear();long returned=Perf01MapBenchmarkTest.now(),returnedCpu=android.os.Process.getElapsedCpuTime();
                                    Perf01MapBenchmarkTest.ui(screen,a->move(map,state,0));JSONObject sample=ready(screen,map,state,boxes,frames,sampler,returned,returnedCpu).put("index",i);
                                    if(provider!=null)assertSame(provider,map.getSqliteVectorProvider(state.getInt("layer_id")));
                                    sample.put("same_live_provider",provider!=null);samples.put(sample);save(new File(app.getFilesDir(),OUTPUT),result);
                                }
                            }
                            if("true".equals(arg("perfScreenshot","false")))Perf01MapBenchmarkTest.saveScene(app);
                        } finally {if(observer.get()!=null)Perf01MapBenchmarkTest.ui(screen,a->observer.get().close());}
                    }
                }
            }
            if(layer==null)layer=loaded(app,state);
            assertEquals(state.getString("input_sha256"),inspect(layer,false).getString("input_sha256"));result.put("status","passed");write(app,result);
        } catch(Throwable failure) {result.put("status","failed").put("error_type",failure.getClass().getSimpleName());write(app,result);throw failure;}
    }
}
