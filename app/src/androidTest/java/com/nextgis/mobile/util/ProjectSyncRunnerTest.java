package com.nextgis.mobile.util;

import android.accounts.Account;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.NgwSyncIo;
import com.nextgis.maplib.util.SyncWorkspaceSession;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.service.LayerFillService;
import com.nextgis.maplibui.util.CollectorImportJournal;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import com.nextgis.mobile.reliability.EmulatorNetworkFixture;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real project inventory, databases, provider, handlers and service; synthetic offline data only. */
@RunWith(AndroidJUnit4.class)
public class ProjectSyncRunnerTest {
    private static final Account ACCOUNT = new Account("offline-test", "offline-test");
    private static final String LAYER = "sync_owner_test";
    private EmulatorNetworkFixture network;

    @Before public void connectedFixture() throws Exception {
        network = new EmulatorNetworkFixture(InstrumentationRegistry.getInstrumentation().getTargetContext());
        network.connected(true);
    }
    @After public void restoreConnectivity() throws Exception { if (network != null) network.close(); }

    private static class Fixture implements AutoCloseable {
        final GISApplication app = (GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final MapContentProviderHelper active = (MapContentProviderHelper)app.getMap();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        final boolean hadSetting = prefs.contains(AppSettingsConstants.KEY_PREF_SYNC_ALL_PROJECTS);
        final boolean previousSetting = ProjectSyncRunner.allProjects(app);
        final List<CollectorProjectRegistry.ProjectInfo> created = new ArrayList<>();
        final File registry = new File(app.getExternalFilesDir(SettingsConstantsKey()), "collector_projects/collector_projects_registry.json");
        final byte[] registryBefore = registry.isFile() ? Files.readAllBytes(registry.toPath()) : null;
        final NGWVectorLayer a;
        Fixture() throws Exception {
            assertTrue("Use an isolated emulator", android.os.Build.FINGERPRINT.contains("generic")
                    || android.os.Build.FINGERPRINT.contains("emulator") || "ranchu".equals(android.os.Build.HARDWARE)
                    || "goldfish".equals(android.os.Build.HARDWARE));
            assertNull("Never overwrite a user's draft", FeatureFormDraftStore.load(app));
            assertEquals(0, SyncRecoveryJournal.load(app).length());
            a = layer(active, "A");
            for (String name : new String[]{"B", "C"}) {
                CollectorProjectRegistry.ProjectInfo info = CollectorProjectRegistry.createLocalProject(app, "Sync test "+name);
                assertNotNull(info);created.add(info);
                MapDrawable map = new MapDrawable(android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888),
                        app,new File(info.getMapPath(),info.getMapName()+Constants.MAP_EXT),app.getLayerFactory(),false);
                map.setName(info.getName());
                try (SyncWorkspaceSession session = new SyncWorkspaceSession(map);
                     SyncWorkspaceSession.Scope ignored = session.enter()) {
                    layer(map,name);assertTrue(map.save());
                } finally { map.closeSyncWorkspace(); }
            }
            prefs.edit().remove(AppSettingsConstants.KEY_PREF_SYNC_ALL_PROJECTS).commit();
        }
        private static String SettingsConstantsKey() { return com.nextgis.maplib.util.SettingsConstants.KEY_PREF_MAP; }
        NGWVectorLayer layer(MapContentProviderHelper map,String name) throws Exception {
            NGWVectorLayer layer=new com.nextgis.maplibui.mapui.NGWVectorLayerUI(app,new File(map.getPath(),LAYER));
            layer.setName(name);map.addLayer(layer);layer.beginBulkImport();
            layer.create(GeoConstants.GTPoint,Collections.singletonList(new Field(GeoConstants.FTString,"name","Name")));
            ContentValues row=new ContentValues();row.put(Constants.FIELD_GEOM,new GeoPoint(1,2).toBlob());row.put("name",name);
            assertTrue(layer.insertAddChanges(row)>=0);layer.endBulkImport();assertTrue(layer.save());assertTrue(map.save());return layer;
        }
        void run(Bundle extras,ProjectSyncRunner.Pass pass,SyncResult result) {
            ProjectSyncRunner.run(app,Collections.singletonList(ACCOUNT),extras,app.getAuthority(),null,result,pass);
        }
        @Override public void close() throws Exception {
            FeatureFormDraftStore.clear(app);
            app.getSharedPreferences("sync_recovery_journal",0).edit().clear().commit();
            if (hadSetting) prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_SYNC_ALL_PROJECTS,previousSetting).commit();
            else prefs.edit().remove(AppSettingsConstants.KEY_PREF_SYNC_ALL_PROJECTS).commit();
            assertSame(active,app.getMap());assertSame(active,MapBase.getActiveInstance());
            assertTrue(a.delete(false));active.save();
            for (CollectorProjectRegistry.ProjectInfo info:created) FileUtil.deleteRecursive(new File(info.getMapPath()));
            if (registryBefore==null) registry.delete();else Files.write(registry.toPath(),registryBefore);
        }
    }
    private static NGWVectorLayer layer(GISApplication app) {
        return (NGWVectorLayer)MapContentProviderHelper.getVectorLayerByPath((MapContentProviderHelper)app.getMap(),LAYER);
    }
    @Test public void disconnectedBackgroundRetainsQueueWithoutStartingAndRunsAfterReconnect() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(SyncRecoveryJournal.enqueue(f.app, ProjectSyncRunner.inventory(f.app), Collections.singletonList(ACCOUNT), false));
            String pending = SyncRecoveryJournal.load(f.app).toString();
            long lastSync = f.prefs.getLong(com.nextgis.maplib.util.SettingsConstants.KEY_PREF_LAST_SYNC_TIMESTAMP, 0);
            network.connected(false);
            SyncResult deferred = new SyncResult();
            f.run(new Bundle(), (a,e,u,p,r) -> fail("No offline account pass or HTTP"), deferred);
            assertEquals(1, deferred.stats.numIoExceptions);
            assertTrue(deferred.delayUntil > System.currentTimeMillis() / 1000L);
            assertFalse(com.nextgis.maplib.service.NGWSyncService.isSyncStarted());
            assertFalse(ProjectOperationCoordinator.isBusy());
            assertEquals(pending, SyncRecoveryJournal.load(f.app).toString());
            assertEquals(lastSync, f.prefs.getLong(com.nextgis.maplib.util.SettingsConstants.KEY_PREF_LAST_SYNC_TIMESTAMP, 0));
            network.connected(true);
            List<String> visited = new ArrayList<>(); SyncResult resumed = new SyncResult();
            f.run(new Bundle(), (a,e,u,p,r) -> visited.add(layer(f.app).getName()), resumed);
            assertFalse(resumed.hasError()); assertEquals(3, visited.size());
            assertEquals(0, SyncRecoveryJournal.load(f.app).length());
        }
    }
    @Test public void durableQueueRetainsOtherAccountsAndMigratesOnlyTheLegacyOwner() throws Exception {
        try (Fixture f = new Fixture()) {
            List<ProjectSyncRunner.Target> targets = ProjectSyncRunner.inventory(f.app);
            Account second = new Account("second-offline-test", "offline-test");
            assertTrue(SyncRecoveryJournal.enqueue(f.app, targets, java.util.Arrays.asList(ACCOUNT, second), true));
            assertEquals(6, SyncRecoveryJournal.load(f.app).length());
            assertTrue(SyncRecoveryJournal.complete(f.app, targets.get(1), ACCOUNT.name));
            assertEquals(5, SyncRecoveryJournal.load(f.app).length());
            assertFalse(SyncRecoveryJournal.load(f.app).toString().contains("password"));
            SharedPreferences journal = f.app.getSharedPreferences("sync_recovery_journal", 0);
            journal.edit().clear().putBoolean("active", true).putString("account", ACCOUNT.name)
                    .putString("workspace", targets.get(0).uid + "|" + targets.get(0).path)
                    .putBoolean("manual", true).commit();
            assertTrue(SyncRecoveryJournal.enqueue(f.app, Collections.emptyList(), Collections.emptyList(), false));
            assertEquals(1, SyncRecoveryJournal.load(f.app).length());assertFalse(journal.contains("active"));
            journal.edit().clear().putBoolean("active", true).putString("workspace", "foreign|missing").commit();
            assertTrue(SyncRecoveryJournal.enqueue(f.app, Collections.emptyList(), Collections.emptyList(), false));
            assertTrue("Foreign legacy marker must survive", journal.getBoolean("active", false));
        }
    }

    @Test public void collectorJournalsWithEqualGroupIdsBelongToTheirOwnProjectPaths() throws Exception {
        try (Fixture f = new Fixture()) {
            for (CollectorProjectRegistry.ProjectInfo info : f.created) {
                MapDrawable map = new MapDrawable(android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888),
                        f.app, new File(info.getMapPath(),info.getMapName()+Constants.MAP_EXT),f.app.getLayerFactory(),false);
                try (SyncWorkspaceSession session = new SyncWorkspaceSession(map,info.getProjectUid());
                     SyncWorkspaceSession.Scope ignored = session.enter()) {
                    assertNull(CollectorImportJournal.load(f.app));
                    CollectorImportJournal.Snapshot snapshot = new CollectorImportJournal.Snapshot();
                    snapshot.groupId=42;snapshot.accountName=ACCOUNT.name;snapshot.projectUid=info.getProjectUid();
                    snapshot.remoteIds=new long[]{101};snapshot.fullProjectRemoteIds=new long[]{101};
                    snapshot.names=new String[]{info.getName()};snapshot.configJsons=new String[]{null};
                    snapshot.formIds=new long[]{0};snapshot.editables=new boolean[]{true};snapshot.repairPassesRemaining=2;
                    assertTrue(CollectorImportJournal.save(f.app,snapshot));
                } finally { map.closeSyncWorkspace(); }
            }
            assertNull(CollectorImportJournal.load(f.app));
            for (CollectorProjectRegistry.ProjectInfo info : f.created) {
                MapDrawable map = new MapDrawable(android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888),
                        f.app,new File(info.getMapPath(),info.getMapName()+Constants.MAP_EXT),f.app.getLayerFactory(),false);
                try (SyncWorkspaceSession session = new SyncWorkspaceSession(map,info.getProjectUid());
                     SyncWorkspaceSession.Scope ignored = session.enter()) {
                    assertEquals(info.getProjectUid(),CollectorImportJournal.load(f.app).projectUid);
                    assertEquals(info.getName(),CollectorImportJournal.load(f.app).names[0]);
                    CollectorImportJournal.clear(f.app);
                } finally { map.closeSyncWorkspace(); }
            }
        }
    }
    @Test public void defaultManualAndScheduledVisitEveryProjectAndOffLimitsToOpenProject() throws Exception {
        try(Fixture f=new Fixture()) {
            assertTrue(ProjectSyncRunner.allProjects(f.app));
            FeatureFormDraftStore.Snapshot draft=new FeatureFormDraftStore.Snapshot();
            draft.layerId=f.a.getId();draft.mapPath=f.active.getPath().getAbsolutePath();
            draft.controlState.put("comment","Unsaved text");assertTrue(FeatureFormDraftStore.save(f.app,draft));
            String draftBefore=FeatureFormDraftStore.load(f.app).controlState.toString();
            for(boolean manual:new boolean[]{true,false}) {
                List<String> visited=new ArrayList<>();SyncResult result=new SyncResult();
                Bundle request = new Bundle(); request.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL,manual);
                f.run(request,
                        (account,extras,authority,provider,part)-> {
                            assertSame(f.active,MapBase.getActiveInstance());
                            NGWVectorLayer own=layer(f.app);assertNotNull(own);visited.add(own.getName());
                            SyncWorkspaceSession.post(new Handler(Looper.getMainLooper()),()-> {
                                assertSame(own.getParent(),f.app.getMap());
                                try { Files.write(new File(own.getPath(),"test_form_alias.json").toPath(),own.getName().getBytes(StandardCharsets.UTF_8)); }
                                catch(Exception error) { throw new AssertionError(error); }
                            });
                        },result);
                assertFalse(result.hasError());assertEquals(3,visited.size());
                assertTrue(visited.containsAll(java.util.Arrays.asList("A","B","C")));
                assertEquals(0,SyncRecoveryJournal.load(f.app).length());
            }
            assertEquals(draftBefore,FeatureFormDraftStore.load(f.app).controlState.toString());
            f.prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_SYNC_ALL_PROJECTS,false).commit();
            List<String> visited=new ArrayList<>();
            f.run(new Bundle(),(a,e,u,p,r)->visited.add(layer(f.app).getName()),new SyncResult());
            assertEquals(Collections.singletonList("A"),visited);
            assertFalse(ProjectOperationCoordinator.isBusy());
        }
    }
    @Test public void failedProjectKeepsItsPairAndLaterProjectsRunRecoveryIgnoresCurrentScopeSetting() throws Exception {
        try(Fixture f=new Fixture()) {
            List<String> visited=new ArrayList<>();SyncResult result=new SyncResult();
            f.run(new Bundle(),(a,e,u,p,r)-> {
                String name=layer(f.app).getName();visited.add(name);
                if("B".equals(name)) r.stats.numIoExceptions++;
            },result);
            assertTrue(result.hasError());assertEquals(3,visited.size());
            JSONArray pending=SyncRecoveryJournal.load(f.app);assertEquals(1,pending.length());
            String key=pending.getJSONObject(0).getString("target");
            f.prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_SYNC_ALL_PROJECTS,false).commit();
            Bundle recovery=new Bundle();recovery.putString(ProjectSyncRunner.EXTRA_RECOVERY_TARGET,key);
            visited.clear();f.run(recovery,(a,e,u,p,r)->visited.add(layer(f.app).getName()),new SyncResult());
            assertEquals(Collections.singletonList("B"),visited);assertEquals(0,SyncRecoveryJournal.load(f.app).length());
            recovery.putString(ProjectSyncRunner.EXTRA_RECOVERY_TARGET,"foreign|/arbitrary/path|map");
            visited.clear();f.run(recovery,(a,e,u,p,r)->visited.add("wrong"),new SyncResult());
            assertTrue(visited.isEmpty());
        }
    }
    @Test public void cancellationRetainsWorkspaceUntilChildEndsAndBlocksAnotherSync() throws Exception {
        try(Fixture f=new Fixture()) {
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread worker=new Thread(()-> {
                try { f.run(new Bundle(),(a,e,u,p,r)-> {
                    if (!"B".equals(layer(f.app).getName())) return;
                    SyncWorkspaceSession.start("HeldProjectChild",()-> {
                    entered.countDown();
                    try { assertTrue(release.await(20,TimeUnit.SECONDS)); }
                    catch(InterruptedException error) { throw new AssertionError(error); }
                    assertNotNull(SyncWorkspaceSession.currentMap());
                }); },new SyncResult()); } catch(Throwable error) { failure.set(error); }
                finally { finished.countDown(); }
            });
            worker.start();assertTrue(entered.await(20,TimeUnit.SECONDS));
            assertNull(ProjectOperationCoordinator.tryBegin(f.app,ProjectOperationCoordinator.Kind.DATA_SYNC));
            assertNull(ProjectOperationCoordinator.tryBegin(f.app,ProjectOperationCoordinator.Kind.PROJECT_SWITCH));
            assertNull(ProjectOperationCoordinator.tryBegin(f.app,ProjectOperationCoordinator.Kind.LAYER_FILL));
            NgwSyncIo.requestCancellation();assertFalse(finished.await(200,TimeUnit.MILLISECONDS));
            release.countDown();assertTrue(finished.await(20,TimeUnit.SECONDS));assertNull(failure.get());
            assertEquals(2,SyncRecoveryJournal.load(f.app).length());assertFalse(ProjectOperationCoordinator.isBusy());
            assertSame(f.active,f.app.getMap());
        }
    }
    @Test public void actualFillServiceCreatesLayerInBackgroundWorkspaceOnly() throws Exception {
        try(Fixture f=new Fixture()) {
            File geojson=new File(f.app.getCacheDir(),"sync-fill-"+UUID.randomUUID()+".geojson");
            Files.write(geojson.toPath(),("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\","
                    +"\"properties\":{\"name\":\"Local test\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[1,2]}}]}").getBytes(StandardCharsets.UTF_8));
            File[] filledPath=new File[1];SyncResult result=new SyncResult();
            f.run(new Bundle(),(a,e,u,p,r)-> {
                if(!"B".equals(layer(f.app).getName())) return;
                MapBase owner=f.app.getMap();filledPath[0]=owner.createLayerStorage();
                Intent intent=new Intent(f.app,LayerFillService.class).setAction(LayerFillService.ACTION_ADD_TASK)
                        .putExtra(LayerFillService.KEY_LAYER_GROUP_ID,owner.getId())
                        .putExtra(LayerFillService.KEY_INPUT_TYPE,LayerFillService.VECTOR_LAYER)
                        .putExtra(LayerFillService.KEY_NAME,"Background fill")
                        .putExtra(LayerFillService.KEY_URI,Uri.fromFile(geojson))
                        .putExtra(LayerFillService.KEY_LAYER_PATH,filledPath[0]);
                assertTrue(LayerFillService.startFillIntent(f.app,intent));
            },result);
            assertFalse("Fill must finish before the project is acknowledged",result.hasError());
            assertNotNull(filledPath[0]);assertTrue(new File(filledPath[0],"config.json").isFile());
            assertFalse(new File(f.active.getPath(),filledPath[0].getName()).exists());
            assertEquals(0,SyncRecoveryJournal.load(f.app).length());assertFalse(ProjectOperationCoordinator.isBusy());
            geojson.delete();
        }
    }
}
