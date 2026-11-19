package com.nextgis.mobile.rendering;

import static org.junit.Assert.*;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FeatureChanges;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplib.util.SyncWorkspaceSession;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.VerificationEnvironment;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Explicit staged synthetic fixture. Default catalog execution checks the isolation guard only. */
@RunWith(AndroidJUnit4.class)
public class Perf01NightSyncTest {
    static final String STATE="perf01-night-state.json",INPUT="perf01-night-input.json",OUTPUT="perf01-night-result.json";
    private final GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    private String arg(String key,String fallback){return InstrumentationRegistry.getArguments().getString(key,fallback);}
    private File file(String name){return new File(app.getFilesDir(),name);}
    private static JSONObject read(File file) throws Exception {
        assertTrue(file.isFile()&&file.length()>0&&file.length()<=8*1024*1024);
        return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
    }
    private static void write(File file,JSONObject value) throws Exception {Files.write(file.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));}
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
    @Test public void authorizedSyntheticNightStage() throws Exception {
        VerificationEnvironment.requireIsolated(app);
        String stage=arg("nightStage","guard"),run=arg("nightRun","");
        if(stage.equals("guard")){assertEquals(VerificationEnvironment.PACKAGE,app.getPackageName());return;}
        assertTrue("Require one explicit driver run",run.matches("[a-f0-9]{32}"));
        JSONObject input=read(file(INPUT));assertEquals(run,input.getString("run"));
        assertEquals("authored_synthetic",input.getString("data_authority"));
        Uri endpoint=Uri.parse(input.getString("endpoint"));
        assertEquals("http",endpoint.getScheme());assertEquals("127.0.0.1",endpoint.getHost());assertTrue(endpoint.getPort()>1024);
        JSONObject output=new JSONObject().put("schema",1).put("run",run).put("stage",stage).put("resource_id",input.getLong("resource_id"))
                .put("data_authority","authored_synthetic").put("status","running");
        write(file(OUTPUT),output);
        try {
            if(stage.equals("map-init")||stage.equals("seed")) {
                JSONObject state=scope(run,stage.equals("seed")?"sync":"map");write(file(STATE),state);
                if(stage.equals("seed"))seed(input,state,output);
            } else {
                JSONObject state=read(file(STATE));assertEquals(run,state.getString("run"));
                assertEquals(state.getString("project_uid"),CollectorProjectRegistry.getActiveProject(app).getProjectUid());
                if(stage.equals("map-cleanup")||stage.equals("cleanup"))cleanup(state,output);
                else operate(stage,input,state,output);
            }
            output.put("status","passed");write(file(OUTPUT),output);
        } catch(Throwable failure) {
            output.put("status","failed").put("failure_type",failure.getClass().getSimpleName());
            // Preserve the synthetic local outbox/content before the driver's owned cleanup,
            // including an unexpected partial upload failure. Never retry a mutation here.
            try {
                if(file(STATE).isFile()) {
                    JSONObject state=read(file(STATE));
                    if(run.equals(state.optString("run"))&&"sync".equals(state.optString("kind"))) {
                        NGWVectorLayerUI layer=loaded(state);
                        output.put("pending_after_failure",pending(layer)).put("failure_content",rows(layer));
                    }
                }
            }catch(Throwable diagnostic){output.put("failure_snapshot_type",diagnostic.getClass().getSimpleName());}
            write(file(OUTPUT),output);throw failure;
        }
    }
    private JSONObject scope(String run,String kind) throws Exception {
        assertFalse("An existing owned fixture requires explicit recovery",file(STATE).exists());
        CollectorProjectRegistry.ProjectInfo previous=CollectorProjectRegistry.getActiveProject(app);assertNotNull(previous);
        SharedPreferences prefs=android.preference.PreferenceManager.getDefaultSharedPreferences(app);
        JSONObject values=new JSONObject();
        for(String key:new String[]{AppSettingsConstants.KEY_PREF_INTRO,SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG,
                SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL,SettingsConstantsUI.KEY_PREF_SCROLL_X,SettingsConstantsUI.KEY_PREF_SCROLL_Y}) {
            Object value=prefs.getAll().get(key);values.put(key,value==null?JSONObject.NULL:value);
        }
        CollectorProjectRegistry.ProjectInfo own=CollectorProjectRegistry.createLocalProject(app,"PERF night "+run+" "+kind);assertNotNull(own);
        JSONObject state=new JSONObject().put("schema",1).put("run",run).put("kind",kind)
                .put("previous_uid",previous.getProjectUid()).put("project_uid",own.getProjectUid()).put("project_path",own.getMapPath())
                .put("preference_values",values);
        write(file(STATE),state); // Ownership survives activation or import failure.
        assertTrue(CollectorProjectRegistry.activateProject(app,own.getProjectUid()));return state;
    }
    private NGWVectorLayerUI newLayer(JSONObject state,JSONObject input,String name) throws Exception {
        assertTrue(name.matches("perf_night_[a-z0-9_]+"));MapDrawable map=(MapDrawable)app.getMap();
        assertEquals(state.getString("project_path"),map.getPath().getAbsolutePath());
        NGWVectorLayerUI layer=new NGWVectorLayerUI(app,new File(map.getPath(),name));
        layer.setName("PERF synthetic fixture");layer.setAccountName(state.getString("account"));layer.setRemoteId(input.getLong("resource_id"));
        map.addLayer(layer);layer.setSyncDirection(2);layer.beginBulkImport();
        try{layer.createFromNGW(null);}finally{layer.endBulkImport();}
        layer.setSyncType(Constants.SYNC_ALL);layer.setSyncDirection(2);layer.setIsEditable(true);assertTrue(layer.save());assertTrue(map.save());return layer;
    }
    private void seed(JSONObject input,JSONObject state,JSONObject output) throws Exception {
        Account account=new Account("perf-night-"+state.getString("run"),app.getAccountsType());
        Bundle data=new Bundle();data.putString("url",input.getString("endpoint"));data.putString("login","synthetic-gateway");
        state.put("account",account.name).put("resource_id",input.getLong("resource_id")).put("layer_name","perf_night_main");write(file(STATE),state);
        assertTrue(AccountManager.get(app).addAccountExplicitly(account,"synthetic-gateway",data));
        ContentResolver.setIsSyncable(account,app.getAuthority(),0);ContentResolver.setSyncAutomatically(account,app.getAuthority(),false);
        try(Perf01MapBenchmarkTest.RssSampler sampler=new Perf01MapBenchmarkTest.RssSampler()) {
            long began=Perf01MapBenchmarkTest.now();NGWVectorLayerUI layer=newLayer(state,input,"perf_night_main");
            output.put("initial_import_ms",Perf01MapBenchmarkTest.ms(Perf01MapBenchmarkTest.now()-began)).put("memory",Perf01MapBenchmarkTest.memory(sampler));
            JSONObject rows=rows(layer);assertEquals(input.getInt("initial_count"),rows.getInt("count"));
            output.put("content",rows).put("pending",pending(layer));assertEquals(0,pending(layer));
        }
    }
    private NGWVectorLayerUI loaded(JSONObject state) throws Exception {
        MapDrawable map=(MapDrawable)app.getMap();assertEquals(state.getString("project_path"),map.getPath().getAbsolutePath());
        VectorLayer attached=(VectorLayer)map.getLayerByPathName(state.getString("layer_name"));
        NGWVectorLayerUI layer;
        if(attached instanceof NGWVectorLayerUI)layer=(NGWVectorLayerUI)attached;
        else {layer=new NGWVectorLayerUI(app,new File(map.getPath(),state.getString("layer_name")));assertTrue(layer.load());layer.setParent(map);}
        assertEquals(state.getLong("resource_id"),layer.getRemoteId());assertEquals(state.getString("account"),layer.getAccountName());return layer;
    }
    private Uri uri(NGWVectorLayerUI layer){return SyncWorkspaceSession.bindUri(layer,Uri.parse("content://"+app.getAuthority()+"/"+layer.getPath().getName()));}
    private static long pending(NGWVectorLayerUI layer){return FeatureChanges.getChangeCount(DatabaseContext.getDatabaseForLayer(layer,false),layer.getChangeTableName());}
    private List<Long> ids(NGWVectorLayerUI layer,int count) {
        List<Long> ids=new ArrayList<>();
        try(android.database.Cursor c=DatabaseContext.getDatabaseForLayer(layer,false).query(layer.getPath().getName(),new String[]{Constants.FIELD_ID},null,null,null,null,Constants.FIELD_ID,Integer.toString(count))){while(c.moveToNext())ids.add(c.getLong(0));}
        assertEquals(count,ids.size());return ids;
    }
    private void operate(String stage,JSONObject input,JSONObject state,JSONObject output) throws Exception {
        assertEquals("sync",state.getString("kind"));NGWVectorLayerUI layer=loaded(state);int count=Integer.parseInt(arg("nightCount","1"));
        assertTrue(count==1||count==10||count==100);
        long pendingBefore=pending(layer);output.put("pending_before",pendingBefore);
        try(Perf01MapBenchmarkTest.RssSampler sampler=new Perf01MapBenchmarkTest.RssSampler()) {
            long begin=Perf01MapBenchmarkTest.now(),cpu=android.os.Process.getElapsedCpuTime();
            SyncResult sync=new SyncResult();
            if(stage.equals("patch")||stage.equals("patch-fail")||stage.equals("hold")) {
                assertEquals(0,pendingBefore);int revision=Integer.parseInt(arg("nightRevision","1"));assertTrue(revision>0);
                JSONArray changed=new JSONArray();
                for(long id:ids(layer,count)) {
                    ContentValues values=new ContentValues();values.put("revision",revision);values.put("test_label","Night "+revision+" / "+id);
                    if(id%2==0)values.putNull("nullable_text");else values.put("nullable_text","");
                    assertEquals(1,app.getContentResolver().update(ContentUris.withAppendedId(uri(layer),id),values,null,null));changed.put(id);
                }
                output.put("commit_ms",Perf01MapBenchmarkTest.ms(Perf01MapBenchmarkTest.now()-begin)).put("changed_ids",changed).put("revision",revision);
                if(stage.equals("patch")||stage.equals("patch-fail")) {
                    begin=Perf01MapBenchmarkTest.now();boolean sent=layer.sendLocalChanges(sync);output.put("send_result",sent);
                    if(stage.equals("patch")){assertTrue(sent);assertEquals(0,pending(layer));}
                    else{assertFalse(sent);assertEquals(count,pending(layer));}
                } else assertEquals(count,pending(layer));
            } else if(stage.equals("pull")) {
                assertTrue(layer.getChangesFromServer(app.getAuthority(),sync));assertEquals(pendingBefore,pending(layer));
            } else if(stage.equals("create")||stage.equals("ambiguous-create")) {
                assertEquals(0,pendingBefore);String key="night-client-"+state.getString("run")+"-"+stage;
                ContentValues values=new ContentValues();values.put("fixture_key",key);values.put("revision",1);values.put("test_label","Night client");values.putNull("nullable_text");
                values.put(Constants.FIELD_GEOM,GeoGeometryFactory.fromWKT("POLYGON ((3339000 8400000,3339100 8400000,3339100 8400100,3339000 8400100,3339000 8400000))",com.nextgis.maplib.util.GeoConstants.CRS_WEB_MERCATOR).toBlob());
                Uri local=app.getContentResolver().insert(uri(layer),values);assertNotNull(local);output.put("fixture_key",key).put("local_id",ContentUris.parseId(local));
                boolean sent=layer.sendLocalChanges(sync);output.put("send_result",sent);
                if(stage.equals("create")){assertTrue(sent);assertEquals(0,pending(layer));}
                else {assertFalse(sent);assertEquals(1,pending(layer));}
            } else if(stage.equals("retry-ambiguous")) {
                assertEquals(1,pendingBefore);assertFalse(layer.sendLocalChanges(sync));assertEquals(1,pending(layer));
            } else if(stage.equals("send")) {assertTrue(layer.sendLocalChanges(sync));assertEquals(0,pending(layer));}
            else if(stage.equals("delete")) {
                assertEquals(0,pendingBefore);long id=Long.parseLong(arg("nightFeatureId","-1"));assertTrue(id>0);
                Feature feature=layer.getFeature(id);assertNotNull(feature);
                assertTrue(feature.getFieldValueAsString("fixture_key").startsWith("night-client-"+state.getString("run")));
                assertEquals(1,app.getContentResolver().delete(ContentUris.withAppendedId(uri(layer),id),null,null));
                assertTrue(layer.sendLocalChanges(sync));assertEquals(0,pending(layer));assertNull(layer.getFeature(id));
            } else if(stage.equals("photo")) {
                assertEquals(0,pendingBefore);long id=ids(layer,1).get(0);Bitmap bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.MAGENTA);
                java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,encoded));bitmap.recycle();byte[] png=encoded.toByteArray();
                ContentValues meta=new ContentValues();meta.put(VectorLayer.ATTACH_DISPLAY_NAME,"night-"+state.getString("run")+".png");meta.put(VectorLayer.ATTACH_MIME_TYPE,"image/png");meta.put(VectorLayer.ATTACH_SIZE,png.length);
                Uri collection=uri(layer).buildUpon().appendPath(Long.toString(id)).appendPath(Constants.URI_ATTACH).build();Uri attachment=app.getContentResolver().insert(collection,meta);assertNotNull(attachment);
                try(OutputStream stream=app.getContentResolver().openOutputStream(attachment)){assertNotNull(stream);stream.write(png);}
                assertTrue(layer.sendLocalChanges(sync));assertEquals(0,pending(layer));output.put("photo_feature_id",id).put("photo_sha256",hash(png)).put("photo_bytes",png.length);
            } else if(stage.equals("full-compare")) {
                assertEquals(0,pendingBefore);NGWVectorLayerUI full=newLayer(state,input,"perf_night_full_"+UUID.randomUUID().toString().replace("-",""));
                try{assertEquals("Full reimport must match every saved field, FID and original geometry",rows(layer).getString("sha256"),rows(full).getString("sha256"));output.put("full_reimport_equal",true);}
                finally{assertTrue(full.delete(false));assertTrue(app.getMap().save());}
            } else if(!stage.equals("inspect"))throw new AssertionError("Unknown explicit stage");
            output.put("operation_upper_ms",Perf01MapBenchmarkTest.ms(Perf01MapBenchmarkTest.now()-begin))
                    .put("process_cpu_upper_ms",android.os.Process.getElapsedCpuTime()-cpu).put("memory",Perf01MapBenchmarkTest.memory(sampler))
                    .put("sync_error",sync.hasError()).put("pending",pending(layer));
            if(!stage.equals("ambiguous-create")&&!stage.equals("retry-ambiguous")&&!stage.equals("patch-fail"))assertFalse(sync.hasError());
            output.put("content",rows(layer)); // Outside the measured operation.
        }
    }
    private JSONObject rows(NGWVectorLayerUI layer) throws Exception {
        JSONArray rows=new JSONArray();java.io.ByteArrayOutputStream canonical=new java.io.ByteArrayOutputStream();
        String[] columns={Constants.FIELD_ID,Constants.FIELD_GEOM,"fixture_key","revision","test_label","nullable_text"};
        try(android.database.Cursor c=DatabaseContext.getDatabaseForLayer(layer,false).query(layer.getPath().getName(),columns,null,null,null,null,Constants.FIELD_ID)) {
            while(c.moveToNext()) {
                assertTrue(rows.length()<100000);JSONObject row=new JSONObject().put("id",c.getLong(0)).put("geom",GeoGeometryFactory.fromBlob(c.getBlob(1)).toJSON());
                for(int i=2;i<columns.length;i++)row.put(columns[i],c.isNull(i)?JSONObject.NULL:i==3?c.getInt(i):c.getString(i));
                String encoded=row.toString();byte[] bytes=encoded.getBytes(StandardCharsets.UTF_8);canonical.write(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());canonical.write(bytes);rows.put(row);
            }
        }
        return new JSONObject().put("count",rows.length()).put("sha256",hash(canonical.toByteArray())).put("rows",rows);
    }
    private void cleanup(JSONObject state,JSONObject output) throws Exception {
        assertTrue(CollectorProjectRegistry.getActiveProject(app).getName().startsWith("PERF night "+state.getString("run")));
        assertEquals(state.getString("project_path"),app.getMap().getPath().getAbsolutePath());
        if(state.has("account")) {
            String accountName=state.getString("account");assertEquals("perf-night-"+state.getString("run"),accountName);
            assertTrue(AccountManager.get(app).removeAccountExplicitly(new Account(accountName,app.getAccountsType())));
        }
        if(state.getString("kind").equals("map")&&file(Perf01MapBenchmarkTest.STATE).isFile()) {
            JSONObject fixture=read(file(Perf01MapBenchmarkTest.STATE));
            assertEquals(state.getString("run"),fixture.getString("run"));assertEquals(state.getString("project_path"),fixture.getString("root"));
            Files.delete(file(Perf01MapBenchmarkTest.STATE).toPath());
        }
        assertTrue(CollectorProjectRegistry.deleteActiveProject(app,state.getString("project_uid"),"Local",()->true).isSuccess());
        assertTrue(CollectorProjectRegistry.activateProject(app,state.getString("previous_uid")));
        SharedPreferences.Editor editor=android.preference.PreferenceManager.getDefaultSharedPreferences(app).edit();JSONObject prefs=state.getJSONObject("preference_values");
        for(Iterator<String> keys=prefs.keys();keys.hasNext();) {
            String key=keys.next();Object value=prefs.get(key);
            if(value==JSONObject.NULL)editor.remove(key);
            else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
            else if(key.equals(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL))editor.putFloat(key,((Number)value).floatValue());
            else editor.putLong(key,((Number)value).longValue());
        }
        assertTrue(editor.commit());Files.delete(file(STATE).toPath());output.put("owned_fixture_removed",true).put("original_project_restored",true);
    }
}
