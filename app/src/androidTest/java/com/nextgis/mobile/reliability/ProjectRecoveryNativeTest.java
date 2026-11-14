package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.util.*;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.ProjectRecoveryActivity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native SQLite/ZIP, saved project isolation, exact checkpoint preservation and the actual screen. */
@RunWith(AndroidJUnit4.class)
public class ProjectRecoveryNativeTest {
    private GISApplication app;
    @Before public void isolatedEmulator() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        com.nextgis.mobile.util.VerificationEnvironment.requireIsolated(context);
        assertTrue("Emulator only","ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE));
        app=(GISApplication)context.getApplicationContext();
    }
    private static long number(SQLiteDatabase db,String sql) {
        try(Cursor c=db.rawQuery(sql,null)) {assertTrue(c.moveToFirst());return c.getLong(0);}
    }
    private static void remove(File directory) throws IOException {
        if(directory!=null&&directory.exists()) {
            UnderlayFiles.deleteChildTree(directory.getParentFile(),directory);assertFalse(directory.exists());
        }
    }
    private static void delete(File file) throws IOException {
        if(file!=null&&file.exists()&&!file.delete())throw new IOException("Cannot remove owned test file");
    }
    private void restoreRegistry(LocalChangesUploadTest.Fixture fixture) throws IOException {
        if(fixture.registryBefore==null)delete(fixture.registry);
        else Files.write(fixture.registry.toPath(),fixture.registryBefore);
    }
    @Test public void completeArchiveRestoresWalRtreePhotosFormsAndOutboxIntoAHeldSeparateCopyEvenIfSourceIsBroken() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            CollectorProjectRegistry.ProjectInfo original=CollectorProjectRegistry.createLocalProject(app,"Archive source fixture");
            assertNotNull(original);File originalDir=new File(original.getMapPath());
            Bitmap bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
            MapDrawable source=new MapDrawable(bitmap,app,new File(originalDir,original.getMapName()+Constants.MAP_EXT),app.getLayerFactory(),false);
            File archive=null;CollectorProjectRegistry.ProjectInfo copy=null;
            String active=CollectorProjectRegistry.getActiveProject(app).getProjectUid();
            try {
                source.setName(original.getName());
                try(SyncWorkspaceSession session=new SyncWorkspaceSession(source);SyncWorkspaceSession.Scope ignored=session.enter()) {
                    NGWVectorLayerUI layer=f.create(source);long id=f.savedLine(layer,false);
                    File photos=new File(layer.getPath(),Long.toString(id));assertTrue(photos.mkdirs());
                    byte[] photo={0,7,9,4,(byte)255};Files.write(new File(photos,"photo.bin").toPath(),photo);
                    byte[] form="{\"synthetic\":\"форма 🌲\"}".getBytes(StandardCharsets.UTF_8);
                    Files.write(new File(layer.getPath(),"form.json").toPath(),form);
                    SQLiteDatabase database=source.getDatabase(false);database.enableWriteAheadLogging();
                    database.execSQL("CREATE TABLE recovery_large(id INTEGER PRIMARY KEY AUTOINCREMENT, payload BLOB)");
                    database.execSQL("INSERT INTO recovery_large VALUES(9,zeroblob(3145741))");
                    database.execSQL("INSERT INTO recovery_large(id) VALUES(50)");database.delete("recovery_large","id=50",null);
                    database.execSQL("CREATE TABLE recovery_generated(name TEXT,_rowid_ INTEGER GENERATED ALWAYS AS(length(name)) VIRTUAL)");
                    database.execSQL("INSERT INTO recovery_generated(rowid,name) VALUES(79,'saved')");
                    assertTrue(source.save());
                    File spatialCache=new File(layer.getPath(),"rtree");assertTrue("Use the application's saved spatial index",spatialCache.isFile());
                    byte[] savedSpatialCache=Files.readAllBytes(spatialCache.toPath());
                    assertTrue(savedSpatialCache.length>0);
                    archive=ProjectArchiveManager.create(app,source,original);
                    JSONObject manifest=ProjectArchiveManager.verify(archive,original.getProjectUid(),MapContentProviderHelper.getCurrentDatabaseVersion(),Long.MAX_VALUE);
                    assertEquals(original.getProjectUid(),manifest.getJSONObject("source").getString("uid"));
                    source.closeSyncWorkspace();
                    // Source database is deliberately unusable. Restoration must read only the admitted archive.
                    Files.write(new File(originalDir,"layers.db").toPath(),new byte[]{1,2,3,4});
                    byte[] broken=Files.readAllBytes(new File(originalDir,"layers.db").toPath());
                    copy=ProjectArchiveManager.restore(app,archive,original,"Recovered archive fixture",MapContentProviderHelper.getCurrentDatabaseVersion());
                    assertNotEquals(original.getProjectUid(),copy.getProjectUid());assertNotEquals(original.getMapPath(),copy.getMapPath());
                    File restored=new File(copy.getMapPath());assertTrue(ProjectRecoveryGuard.isHeld(restored));assertTrue(ProjectRecoveryGuard.isRecoveryCopy(restored));
                    assertArrayEquals(broken,Files.readAllBytes(new File(originalDir,"layers.db").toPath()));
                    assertEquals(active,CollectorProjectRegistry.getActiveProject(app).getProjectUid());
                    assertSame("A scoped background call still resolves its own map",source,app.getMap());
                    assertSame("The displayed map is a separate, unchanged identity",f.map,MapBase.getActiveInstance());
                    try(SQLiteDatabase db=SQLiteDatabase.openDatabase(new File(restored,"layers.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)) {
                        WorkspaceSqliteSnapshot.check(db);
                        assertEquals(3145741,number(db,"SELECT length(payload) FROM recovery_large WHERE id=9"));
                        assertEquals(50,number(db,"SELECT seq FROM sqlite_sequence WHERE name='recovery_large'"));
                        assertEquals(79,number(db,"SELECT rowid FROM recovery_generated"));
                        assertEquals(5,number(db,"SELECT _rowid_ FROM recovery_generated"));
                        assertEquals(1,FeatureChanges.getChangeCount(db,layer.getChangeTableName()));
                        assertEquals(id,number(db,"SELECT _id FROM local_delivery_line"));
                    }
                    assertArrayEquals(photo,Files.readAllBytes(new File(restored,"local_delivery_line/"+id+"/photo.bin").toPath()));
                    assertArrayEquals(form,Files.readAllBytes(new File(restored,"local_delivery_line/form.json").toPath()));
                    File restoredSpatialCache=new File(restored,"local_delivery_line/rtree");
                    assertArrayEquals(savedSpatialCache,Files.readAllBytes(restoredSpatialCache.toPath()));
                    com.nextgis.maplib.datasource.GeometryRTree spatial=new com.nextgis.maplib.datasource.GeometryRTree();
                    spatial.load(restoredSpatialCache);assertFalse(spatial.getItem(id).isEmpty());
                }
                assertSame("Closing the background scope restores the caller's map",f.map,app.getMap());
            } finally {
                source.closeSyncWorkspace();bitmap.recycle();
                if(copy!=null)remove(new File(copy.getMapPath()));remove(originalDir);delete(archive);restoreRegistry(f);
            }
            assertTrue(endpoint.mutations.isEmpty());
        }
    }
    @Test public void corruptedArchiveWrongProjectAndInsufficientSpaceDoNotRegisterOrReplaceAnything() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            long id=f.savedLine(f.layer,false);CollectorProjectRegistry.ProjectInfo source=CollectorProjectRegistry.getActiveProject(app);
            File archive=ProjectArchiveManager.create(app,f.map,source),corrupt=new File(app.getCacheDir(),"corrupt-"+UUID.randomUUID()+".zip");
            byte[] registry=Files.readAllBytes(f.registry.toPath());int version=MapContentProviderHelper.getCurrentDatabaseVersion();
            try {
                try {ProjectArchiveManager.verify(archive,"another-project",version,Long.MAX_VALUE);fail("Wrong project accepted");}catch(IOException expected) { }
                try {ProjectArchiveManager.verify(archive,source.getProjectUid(),version+1,Long.MAX_VALUE);fail("Wrong schema accepted");}catch(IOException expected) { }
                try {ProjectArchiveManager.verify(archive,source.getProjectUid(),version,1);fail("No space accepted");}catch(IOException expected) { }
                try(ZipFile input=new ZipFile(archive);ZipOutputStream output=new ZipOutputStream(new FileOutputStream(corrupt))) {
                    Enumeration<? extends ZipEntry> entries=input.entries();
                    while(entries.hasMoreElements()) {
                        ZipEntry entry=entries.nextElement();output.putNextEntry(new ZipEntry(entry.getName()));
                        if(entry.getName().equals("workspace/layers.db"))output.write(new byte[]{3,2,1});
                        else try(InputStream in=input.getInputStream(entry)) {byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)output.write(buffer,0,count);}
                        output.closeEntry();
                    }
                }
                try {ProjectArchiveManager.restore(app,corrupt,source,"Must not appear",version);fail("Corrupt archive accepted");}catch(IOException expected) { }
                assertArrayEquals(registry,Files.readAllBytes(f.registry.toPath()));assertFalse(ProjectOperationCoordinator.isBusy());
                assertEquals(1,f.pending(f.layer));assertNotNull(f.layer.getFeature(id));assertTrue(endpoint.mutations.isEmpty());
            } finally {delete(corrupt);delete(archive);}
        }
    }
    @Test public void unreadableCheckpointIsPreservedExactlyAndAReplacedValidOperationCanBeRecovered() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            android.content.SharedPreferences prefs=app.getSharedPreferences("feature_form_draft",0);
            assertFalse(prefs.contains("state_json"));String malformed="{broken: \"точка 🌲\" "+UUID.randomUUID();
            Set<String> before=new HashSet<>();for(DraftQuarantineStore.Entry entry:DraftQuarantineStore.list(app))before.add(entry.id);
            assertTrue(prefs.edit().putString("state_json",malformed).commit());
            assertNull(FeatureFormDraftStore.load(app));assertFalse(prefs.contains("state_json"));
            DraftQuarantineStore.Entry preserved=null;
            for(DraftQuarantineStore.Entry entry:DraftQuarantineStore.list(app))if(!before.contains(entry.id))preserved=entry;
            assertNotNull(preserved);assertEquals(malformed,DraftQuarantineStore.original(app,preserved.id));
            ByteArrayOutputStream exported=new ByteArrayOutputStream();DraftQuarantineStore.export(app,preserved.id,exported);
            boolean originalFound=false;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(exported.toByteArray()))) {
                ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals("original.json")) {
                    assertArrayEquals(malformed.getBytes(StandardCharsets.UTF_8),UnderlayFiles.readBounded(zip,65536));originalFound=true;
                }
            }
            assertTrue(originalFound);assertFalse(FeatureFormDraftStore.retryQuarantined(app,preserved.id));
            FeatureFormDraftStore.Snapshot first=new FeatureFormDraftStore.Snapshot();first.layerId=f.layer.getId();first.mapPath=f.path;
            first.geometryWkt="LINESTRING (1 2,3 4)";first.controlState.put("name","Сохранённый черновик");
            assertTrue(FeatureFormDraftStore.save(app,first));
            FeatureFormDraftStore.Snapshot next=new FeatureFormDraftStore.Snapshot();next.layerId=f.layer.getId();next.mapPath=f.path;
            assertTrue(FeatureFormDraftStore.save(app,next));assertFalse(FeatureFormDraftStore.clearOwned(app,first.operationId));
            DraftQuarantineStore.Entry replaced=null;
            for(DraftQuarantineStore.Entry entry:DraftQuarantineStore.list(app))
                if(DraftQuarantineStore.original(app,entry.id).contains(first.operationId))replaced=entry;
            assertNotNull(replaced);assertFalse(FeatureFormDraftStore.retryQuarantined(app,replaced.id));
            assertTrue(FeatureFormDraftStore.clearOwned(app,next.operationId));assertTrue(FeatureFormDraftStore.retryQuarantined(app,replaced.id));
            assertEquals(first.operationId,FeatureFormDraftStore.load(app).operationId);
            assertEquals(first.controlState.toString(),FeatureFormDraftStore.load(app).controlState.toString());
            assertTrue(FeatureFormDraftStore.clearOwned(app,first.operationId));
        } finally {FeatureFormDraftStore.clear(app);}
    }
    @Test public void recoveredPartialFormRequiresANewFullSaveAndDoesNotReleaseTheOriginalProjectOrCurrentOwner() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            long id=f.savedLine(f.layer,false);String layer=f.layer.getPath().getName();
            SQLiteDatabase originalDb=f.map.getDatabase(false);
            FeatureSaveJournal.holdUpload(originalDb,layer,id,"archived-partial-form");
            LayerDatabaseTransaction.run(originalDb,()->{FeatureSaveJournal.completeForm(originalDb,layer,"completed-before-archive",id);return null;});
            CollectorProjectRegistry.ProjectInfo original=CollectorProjectRegistry.getActiveProject(app),copy=null;File archive=null;
            try {
                archive=ProjectArchiveManager.create(app,f.map,original);
                copy=ProjectArchiveManager.restore(app,archive,original,"Recovered form fixture",MapContentProviderHelper.getCurrentDatabaseVersion());
                final String uid=copy.getProjectUid();
                LocalChangesRepository.Row row=LocalChangesRepository.load(app,uid,50,0).rows.stream()
                        .filter(item->item.queued&&item.featureId==id).findFirst().get();
                assertEquals(LocalChangesRepository.Action.FORM,row.action);assertFalse(LocalChangesRepository.releaseReviewedCopy(app,uid));
                try {LocalChangesRepository.withLayer(app,uid,row.layerPath,vector->vector.prepareChangeReview(row.changeId,row.identity,id,-1,row.operation));
                    fail("An archived partial form was treated as ready");}catch(android.database.sqlite.SQLiteException expected) { }
                LocalChangesRepository.withLayer(app,uid,row.layerPath,vector->{
                    SQLiteDatabase db=DatabaseContext.getDatabaseForLayer(vector,false);
                    FeatureSaveJournal.record(db,layer,"new-copy-form",id);FeatureSaveJournal.holdUpload(db,layer,id,"new-copy-form");
                    LayerDatabaseTransaction.run(db,()->{
                        try {ProjectRecoveryGuard.releaseCompletedFormHolds(DatabaseContext.getMapForLayer(vector).getPath(),db,layer,id);}
                        catch(IOException error){throw new AssertionError(error);}return null;
                    });
                    assertTrue(FeatureSaveJournal.isUploadHeld(db,layer,id));
                    LayerDatabaseTransaction.run(db,()->{
                        FeatureSaveJournal.completeForm(db,layer,"new-copy-form",id);
                        try {ProjectRecoveryGuard.releaseCompletedFormHolds(DatabaseContext.getMapForLayer(vector).getPath(),db,layer,id);}
                        catch(IOException error){throw new AssertionError(error);}return null;
                    });
                    assertTrue("The new form owns its checkpoint until it clears",FeatureSaveJournal.isUploadHeld(db,layer,id));
                    FeatureSaveJournal.releaseUpload(db,layer,"new-copy-form");assertFalse(FeatureSaveJournal.isUploadHeld(db,layer,id));return null;
                });
                String operation=LocalChangesRepository.withLayer(app,uid,row.layerPath,
                        vector->vector.prepareChangeReview(row.changeId,row.identity,id,-1,row.operation));
                assertTrue(LocalChangesRepository.withLayer(app,uid,row.layerPath,vector->vector.authorizeUploadRetry(operation)));
                assertTrue(LocalChangesRepository.releaseReviewedCopy(app,uid));
                assertTrue(com.nextgis.maplibui.mapui.LocalChangesWorker.uploadProject(app,copy.getMapPath()));
                assertTrue(FeatureSaveJournal.isUploadHeld(originalDb,layer,id));assertNotNull(f.layer.getFeature(id));
                assertEquals(1,number(originalDb,"SELECT count(*) FROM \""+f.layer.getChangeTableName()+"\""));
                assertEquals(1,endpoint.mutations.size());assertSame(f.map,MapBase.getActiveInstance());
            }finally {
                FeatureSaveJournal.releaseUpload(originalDb,layer,"archived-partial-form");
                if(copy!=null)remove(new File(copy.getMapPath()));delete(archive);restoreRegistry(f);
            }
        }
    }
    private void awaitReady(ActivityScenario<ProjectRecoveryActivity> scenario) throws Exception {
        AtomicBoolean ready=new AtomicBoolean();long deadline=SystemClock.uptimeMillis()+20000;
        while(!ready.get()&&SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity(activity->{ready.set(activity.findViewById(R.id.recovery_create).isEnabled());assertFalse(activity.isFinishing());});
            if(!ready.get())Thread.sleep(50);
        }
        assertTrue("Recovery actions must become usable",ready.get());
    }
    private void confirmDialog() throws Exception {
        AtomicBoolean clicked=new AtomicBoolean();long deadline=SystemClock.uptimeMillis()+10000;
        while(!clicked.get()&&SystemClock.uptimeMillis()<deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.view.View button=root.findViewById(android.R.id.button1);
                    if(button!=null&&button.isShown()&&root.hasWindowFocus()) {assertTrue(button.performClick());clicked.set(true);break;}
                }
            });
            if(!clicked.get())Thread.sleep(50);
        }
        assertTrue("Restore confirmation must be reachable",clicked.get());
    }
    @Test public void actualRecoveryScreenCreatesAndRestoresWithoutSwitchingTheWorkingProject() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            f.savedLine(f.layer,false);String active=CollectorProjectRegistry.getActiveProject(app).getProjectUid();
            Set<String> projects=new HashSet<>();for(CollectorProjectRegistry.ProjectInfo info:CollectorProjectRegistry.listProjects(app))projects.add(info.getProjectUid());
            Set<File> archives=new HashSet<>(Arrays.asList(ProjectArchiveManager.archiveDirectory(app).listFiles()));
            try(ActivityScenario<ProjectRecoveryActivity> scenario=ActivityScenario.launch(new Intent(app,ProjectRecoveryActivity.class))) {
                awaitReady(scenario);assertFalse(ProjectOperationCoordinator.isBusy());
                scenario.onActivity(activity->assertTrue(activity.findViewById(R.id.recovery_create).performClick()));
                long deadline=SystemClock.uptimeMillis()+30000;boolean created=false;
                while(!created&&SystemClock.uptimeMillis()<deadline) {
                    for(File file:ProjectArchiveManager.archiveDirectory(app).listFiles())if(file.getName().endsWith(".zip")&&!archives.contains(file))created=true;
                    if(!created)Thread.sleep(50);
                }
                assertTrue("Create button must produce a complete archive",created);awaitReady(scenario);
                scenario.onActivity(activity->{
                    android.view.ViewGroup rows=activity.findViewById(R.id.recovery_archives);boolean clicked=false;
                    for(int i=0;i<rows.getChildCount();i++)if(rows.getChildAt(i) instanceof Button) {
                        Button button=(Button)rows.getChildAt(i);
                        if(button.getText().toString().equals(app.getString(R.string.recovery_restore))) {assertTrue(button.performClick());clicked=true;break;}
                    }
                    assertTrue("The created archive must offer restoration",clicked);
                });
                confirmDialog();deadline=SystemClock.uptimeMillis()+30000;CollectorProjectRegistry.ProjectInfo copy=null;
                while(copy==null&&SystemClock.uptimeMillis()<deadline) {
                    for(CollectorProjectRegistry.ProjectInfo info:CollectorProjectRegistry.listProjects(app))if(!projects.contains(info.getProjectUid()))copy=info;
                    if(copy==null)Thread.sleep(50);
                }
                assertNotNull("Restore confirmation must register a separate copy",copy);awaitReady(scenario);
                assertTrue(ProjectRecoveryGuard.isHeld(new File(copy.getMapPath())));assertEquals(active,CollectorProjectRegistry.getActiveProject(app).getProjectUid());
                assertEquals(1,f.pending(f.layer));assertTrue(endpoint.mutations.isEmpty());
            } finally {
                for(CollectorProjectRegistry.ProjectInfo info:CollectorProjectRegistry.listProjects(app))if(!projects.contains(info.getProjectUid()))remove(new File(info.getMapPath()));
                for(File file:ProjectArchiveManager.archiveDirectory(app).listFiles())if(!archives.contains(file))delete(file);
                restoreRegistry(f);
            }
        }
    }
}
