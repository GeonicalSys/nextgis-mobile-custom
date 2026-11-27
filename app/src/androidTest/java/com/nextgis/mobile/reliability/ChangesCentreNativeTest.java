package com.nextgis.mobile.reliability;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.util.*;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.ChangesCentreActivity;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real screen, SQLite queue and loopback acknowledgements; no production account or phone. */
@RunWith(AndroidJUnit4.class)
public class ChangesCentreNativeTest {
    private GISApplication app;
    private EmulatorNetworkFixture network;
    @Before public void isolatedEmulator() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        com.nextgis.mobile.util.VerificationEnvironment.requireIsolated(context);
        assertTrue("Emulator only","ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE));
        app=(GISApplication)context.getApplicationContext();network=new EmulatorNetworkFixture(app);network.connected(true);
    }
    @After public void restoreNetwork() throws Exception {if(network!=null)network.close();}
    private interface Condition {boolean ready() throws Exception;}
    private static void await(Condition condition) throws Exception {
        long until=SystemClock.elapsedRealtime()+30000;
        do {if(condition.ready())return;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<until);
        fail("Expected changes state did not appear");
    }
    private static Button button(View root,String text) {
        if(root instanceof Button&&text.contentEquals(((Button)root).getText()))return (Button)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            Button found=button(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    private static boolean hasText(View root,String text) {
        if(root instanceof TextView&&((TextView)root).getText().toString().contains(text))return true;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)
            if(hasText(((ViewGroup)root).getChildAt(i),text))return true;
        return false;
    }
    private static EditText input(View root) {
        if(root instanceof EditText)return (EditText)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            EditText found=input(((ViewGroup)root).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private void ready(ActivityScenario<ChangesCentreActivity> scenario) throws Exception {
        await(()->{AtomicBoolean ready=new AtomicBoolean();scenario.onActivity(activity->ready.set(
                activity.findViewById(R.id.changes_progress).getVisibility()!=View.VISIBLE
                        &&activity.findViewById(R.id.changes_send).isEnabled()));return ready.get();});
    }
    private boolean clickDialog(String text) {
        AtomicBoolean clicked = new AtomicBoolean();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                Button action = button(root, text);
                if (action != null && action.isShown() && root.hasWindowFocus()) {
                    assertTrue(action.performClick()); clicked.set(true); break;
                }
            }
        });
        return clicked.get();
    }

    @Test public void real226QueueCanBeReviewedAndSentFromTheScreenWithoutDeletingItsPoint() throws Exception {
        try (LocalChangesUploadTest.Endpoint endpoint = new LocalChangesUploadTest.Endpoint();
             LocalChangesUploadTest.Fixture f = new LocalChangesUploadTest.Fixture(endpoint, true, true)) {
            endpoint.resourceMetadata = LocalChangesUploadTest.lisaResourceMetadata(false).toString();
            endpoint.serveFeatures = true;
            long local = f.raw226Line("saved in version 226"); f.reopenLayerFromSaved226Config();
            SQLiteDatabase db = DatabaseContext.getDatabaseForLayer(f.layer, false);
            assertFalse(FeatureChanges.hasOperationIdentity(db, f.layer.getChangeTableName()));
            byte[] before;
            try (Cursor row = db.query(f.layer.getPath().getName(), new String[]{Constants.FIELD_GEOM},
                    Constants.FIELD_ID + "=?", new String[]{Long.toString(local)}, null, null, null)) {
                assertTrue(row.moveToFirst()); before = row.getBlob(0);
            }
            String uid = CollectorProjectRegistry.getActiveProject(app).getProjectUid();
            try (ActivityScenario<ChangesCentreActivity> scenario = ActivityScenario.launch(new Intent(app, ChangesCentreActivity.class)
                    .putExtra(ChangesCentreActivity.EXTRA_PROJECT_UID, uid))) {
                ready(scenario);
                assertTrue("Viewing an old queue must not send or migrate it", endpoint.mutations.isEmpty());
                assertFalse(FeatureChanges.hasOperationIdentity(db, f.layer.getChangeTableName()));
                scenario.onActivity(activity -> {
                    assertTrue(hasText(activity.findViewById(R.id.changes_rows), activity.getString(R.string.changes_reason_legacy)));
                    Button review = button(activity.findViewById(R.id.changes_rows), activity.getString(R.string.changes_action_review));
                    assertNotNull(review); assertTrue(review.performClick());
                });
                await(() -> clickDialog(app.getString(R.string.changes_confirm_never_sent)));
                assertTrue("Candidate lookup and opening confirmation must never POST a point", endpoint.mutations.isEmpty());
                try (Cursor retained = db.rawQuery("SELECT " + Constants.FIELD_GEOM + " FROM "
                        + f.layer.getPath().getName() + " WHERE " + Constants.FIELD_ID + "=?",
                        new String[]{Long.toString(local)})) {
                    assertTrue("The original saved point must still exist", retained.moveToFirst());
                    assertArrayEquals(before, retained.getBlob(0));
                }
                await(() -> clickDialog(app.getString(R.string.changes_allow_repeat)));
                await(() -> f.pending(f.layer) == 0);
            }
            assertEquals(1, endpoint.mutations.size());
            try (Cursor row = db.query(f.layer.getPath().getName(), null, Constants.FIELD_ID + "=42", null, null, null, null)) {
                assertTrue(row.moveToFirst()); assertEquals("saved in version 226", row.getString(row.getColumnIndexOrThrow("name")));
                assertArrayEquals(before, row.getBlob(row.getColumnIndexOrThrow(Constants.FIELD_GEOM)));
                assertEquals(-1, row.getColumnIndex("deletion_operation_id"));
            }
        }
    }
    @Test public void readOnlyPagesThenActualSendShowServerAcknowledgementWithoutFullSyncTimeOrMapSwitch() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            long id=f.savedLine(f.layer,false);String uid=CollectorProjectRegistry.getActiveProject(app).getProjectUid();
            long fullBefore=ProjectSyncState.lastComplete(app,f.path);
            LocalChangesRepository.Page before=LocalChangesRepository.load(app,uid,50,0);
            assertEquals(1,before.queueCount);assertTrue(endpoint.mutations.isEmpty());assertSame(f.map,MapBase.getActiveInstance());
            assertTrue(UploadOperationJournal.entries(f.map.getDatabase(false),f.layer.getPath().getName()).isEmpty());
            assertEquals(LocalChangesRepository.State.QUEUED,before.rows.stream().filter(row->row.featureId==id).findFirst().get().state);
            try(ActivityScenario<ChangesCentreActivity> scenario=ActivityScenario.launch(new Intent(app,ChangesCentreActivity.class)
                    .putExtra(ChangesCentreActivity.EXTRA_PROJECT_UID,uid))) {
                ready(scenario);scenario.onActivity(activity->{
                    assertTrue(hasText(activity.findViewById(R.id.changes_rows),"field-line"));
                    assertTrue(activity.findViewById(R.id.changes_send).performClick());
                });
                f.awaitSent(f.layer);await(()->LocalChangesRepository.load(app,uid,50,0).confirmedCount>before.confirmedCount);
                scenario.onActivity(activity->activity.findViewById(R.id.changes_refresh).performClick());ready(scenario);
                scenario.onActivity(activity->assertTrue(hasText(activity.findViewById(R.id.changes_rows),activity.getString(R.string.changes_confirmed))));
            }
            assertNotNull(f.layer.getFeature(42));assertEquals(fullBefore,ProjectSyncState.lastComplete(app,f.path));assertSame(f.map,MapBase.getActiveInstance());
        }
    }
    @Test public void old226PointEditedAfterItsOldUploadCanBeLinkedFromTheScreenAndUpdatedWithoutPost() throws Exception {
        try (LocalChangesUploadTest.Endpoint endpoint = new LocalChangesUploadTest.Endpoint();
             LocalChangesUploadTest.Fixture f = new LocalChangesUploadTest.Fixture(endpoint, true, true)) {
            endpoint.resourceMetadata = LocalChangesUploadTest.lisaResourceMetadata(false).toString();
            endpoint.serveFeatures = true;
            endpoint.remoteFeatures.put(42, LocalChangesUploadTest.accepted226Line(42, "old server values"));
            long local = f.raw226Line("newest local values"); f.reopenLayerFromSaved226Config();
            String uid = CollectorProjectRegistry.getActiveProject(app).getProjectUid();
            try (ActivityScenario<ChangesCentreActivity> scenario = ActivityScenario.launch(new Intent(app, ChangesCentreActivity.class)
                    .putExtra(ChangesCentreActivity.EXTRA_PROJECT_UID, uid))) {
                ready(scenario);
                scenario.onActivity(activity -> {
                    Button review = button(activity.findViewById(R.id.changes_rows), activity.getString(R.string.changes_action_review));
                    assertNotNull(review); assertTrue(review.performClick());
                });
                await(() -> clickDialog(app.getString(R.string.changes_manual_id)));
                await(() -> {
                    AtomicBoolean submitted = new AtomicBoolean();
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                        for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                            EditText id = input(root); View verify = root.findViewById(android.R.id.button1);
                            if (id != null && verify != null && verify.isShown() && root.hasWindowFocus()) {
                                id.setText("42"); assertTrue(verify.performClick()); submitted.set(true); break;
                            }
                        }
                    });
                    return submitted.get();
                });
                await(() -> {
                    AtomicBoolean shown = new AtomicBoolean();
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                        for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                            Button link = button(root, app.getString(R.string.changes_link_keep_local));
                            if (link != null && link.isShown() && root.hasWindowFocus()) shown.set(true);
                        }
                    });
                    return shown.get();
                });
                assertEquals("Looking up and comparing an existing point must never mutate the server", 0, endpoint.mutations.size());
                try (Cursor retained = DatabaseContext.getDatabaseForLayer(f.layer, false).rawQuery(
                        "SELECT name FROM " + f.layer.getPath().getName() + " WHERE " + Constants.FIELD_ID + "=?",
                        new String[]{Long.toString(local)})) {
                    assertTrue(retained.moveToFirst()); assertEquals("newest local values", retained.getString(0));
                }
                await(() -> clickDialog(app.getString(R.string.changes_link_keep_local)));
                await(() -> f.pending(f.layer) == 0);
            }
            assertEquals(1, endpoint.mutations.size()); assertTrue(endpoint.mutations.get(0).startsWith("PUT "));
            assertEquals("newest local values", new org.json.JSONObject(endpoint.remoteFeatures.get(42)).getJSONObject("fields").getString("name"));
            try (Cursor retained = DatabaseContext.getDatabaseForLayer(f.layer, false).rawQuery(
                    "SELECT name FROM " + f.layer.getPath().getName() + " WHERE " + Constants.FIELD_ID + "=42", null)) {
                assertTrue(retained.moveToFirst()); assertEquals("newest local values", retained.getString(0));
            }
        }
    }
    @Test public void lostResponseIsVisibleAndServerReadFinishesWithoutASecondCreation() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            f.savedLine(f.layer,false);endpoint.dropCreationResponse=true;assertFalse(f.send());endpoint.serveFeatures=true;
            String uid=CollectorProjectRegistry.getActiveProject(app).getProjectUid();
            LocalChangesRepository.Page pending=LocalChangesRepository.load(app,uid,50,0);
            LocalChangesRepository.Row row=pending.rows.stream().filter(item->item.action==LocalChangesRepository.Action.REVIEW).findFirst().get();
            assertEquals(LocalChangesRepository.State.REVIEW,row.state);assertEquals(1,endpoint.mutations.size());
            try(ActivityScenario<ChangesCentreActivity> scenario=ActivityScenario.launch(new Intent(app,ChangesCentreActivity.class))) {
                ready(scenario);scenario.onActivity(activity->{
                    Button review=button(activity.findViewById(R.id.changes_rows),activity.getString(R.string.changes_action_review));
                    assertNotNull(review);assertTrue(review.performClick());
                });
                await(()->{
                    AtomicBoolean clicked=new AtomicBoolean();
                    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                        for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                            Button candidate=button(root,app.getString(R.string.changes_candidate_id,42L));
                            if(candidate!=null&&candidate.isShown()&&root.hasWindowFocus()) {
                                assertTrue(candidate.performClick());clicked.set(true);break;
                            }
                            View confirm=root.findViewById(android.R.id.button1);EditText id=input(root);
                            if(id!=null&&confirm!=null&&confirm.isShown()&&root.hasWindowFocus()) {
                                id.setText("42");assertTrue(confirm.performClick());clicked.set(true);break;
                            }
                            Button manual=button(root,app.getString(R.string.changes_manual_id));
                            if(manual!=null&&manual.isShown()&&root.hasWindowFocus()) {manual.performClick();break;}
                        }
                    });
                    return clicked.get();
                });
                await(()->f.pending(f.layer)==0);
            }
            assertEquals(1,endpoint.mutations.size());assertEquals(0,f.pending(f.layer));assertNotNull(f.layer.getFeature(42));assertSame(f.map,MapBase.getActiveInstance());
        }
    }
    @Test public void recoveredCopyRequiresEveryPendingOperationToBeReviewedAndSendsOnlyItsOwnDatabase() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            long originalId=f.savedLine(f.layer,false);CollectorProjectRegistry.ProjectInfo original=CollectorProjectRegistry.getActiveProject(app);
            File archive=null;CollectorProjectRegistry.ProjectInfo copy=null;
            try {
                archive=ProjectArchiveManager.create(app,f.map,original);
                copy=ProjectArchiveManager.restore(app,archive,original,"Reviewed copy fixture",MapContentProviderHelper.getCurrentDatabaseVersion());
                String uid=copy.getProjectUid();assertFalse(LocalChangesRepository.releaseReviewedCopy(app,uid));
                LocalChangesRepository.Page page=LocalChangesRepository.load(app,uid,50,0);assertTrue(page.recoveryHeld);
                LocalChangesRepository.Row row=page.rows.stream().filter(item->item.featureId==originalId&&item.queued).findFirst().get();
                assertFalse(upload(copy.getMapPath()));assertTrue(endpoint.mutations.isEmpty());
                String identity=LocalChangesRepository.withLayer(app,uid,row.layerPath,layer->layer.prepareChangeReview(
                        row.changeId,row.identity,row.featureId,row.attachmentId,row.operation));
                assertFalse(LocalChangesRepository.releaseReviewedCopy(app,uid));
                assertTrue(LocalChangesRepository.withLayer(app,uid,row.layerPath,layer->layer.authorizeUploadRetry(identity)));
                assertTrue(LocalChangesRepository.releaseReviewedCopy(app,uid));assertFalse(ProjectRecoveryGuard.isHeld(new File(copy.getMapPath())));
                assertTrue(ProjectRecoveryGuard.isRecoveryCopy(new File(copy.getMapPath())));
                assertTrue(upload(copy.getMapPath()));assertEquals(1,endpoint.mutations.size());
                assertEquals(1,f.pending(f.layer));assertNotNull(f.layer.getFeature(originalId));assertSame(f.map,MapBase.getActiveInstance());
                assertTrue(ProjectRecoveryGuard.isDeliveryBlocked(f.map.getPath()));
                long lastSync=ProjectSyncState.lastComplete(app,f.path);
                assertTrue(f.send());assertEquals("The original must not repeat the copy's POST",1,endpoint.mutations.size());
                android.content.SyncResult sync=new android.content.SyncResult();assertFalse(f.layer.sendLocalChanges(sync));
                assertTrue(sync.hasError());assertEquals(1,f.pending(f.layer));assertNotNull(f.layer.getFeature(originalId));
                assertEquals(lastSync,ProjectSyncState.lastComplete(app,f.path));
                LocalChangesRepository.Page paused=LocalChangesRepository.load(app,original.getProjectUid(),50,0);
                assertTrue(paused.deliveryBlocked);assertFalse(LocalChangesRepository.request(app,original.getProjectUid()));
                try(SQLiteDatabase db=SQLiteDatabase.openDatabase(new File(copy.getMapPath(),"layers.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);
                    Cursor c=db.rawQuery("SELECT name FROM \""+f.layer.getPath().getName()+"\" WHERE _id=42",null)) {assertTrue(c.moveToFirst());assertEquals("field-line",c.getString(0));}
            }finally {
                if(copy!=null)UnderlayFiles.deleteChildTree(new File(copy.getMapPath()).getParentFile(),new File(copy.getMapPath()));
                if(archive!=null)assertTrue(archive.delete());
                if(f.registryBefore!=null)Files.write(f.registry.toPath(),f.registryBefore);else if(f.registry.exists())assertTrue(f.registry.delete());
            }
        }
    }
    @Test public void aNewSourceAcknowledgementBlocksStaleCopyRetryUntilReadOnlyServerReconciliation() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            endpoint.serveFeatures=true;
            long id=f.savedLine(f.layer,false);CollectorProjectRegistry.ProjectInfo original=CollectorProjectRegistry.getActiveProject(app);
            File archive=null;CollectorProjectRegistry.ProjectInfo copy=null;
            try {
                archive=ProjectArchiveManager.create(app,f.map,original);
                copy=ProjectArchiveManager.restore(app,archive,original,"Acknowledged source fixture",MapContentProviderHelper.getCurrentDatabaseVersion());
                String uid=copy.getProjectUid();LocalChangesRepository.Row row=LocalChangesRepository.load(app,uid,50,0).rows.stream()
                        .filter(item->item.featureId==id&&item.queued).findFirst().get();
                String operation=LocalChangesRepository.withLayer(app,uid,row.layerPath,layer->layer.prepareChangeReview(
                        row.changeId,row.identity,row.featureId,row.attachmentId,row.operation));
                assertTrue(LocalChangesRepository.withLayer(app,uid,row.layerPath,layer->layer.authorizeUploadRetry(operation)));
                assertTrue(f.send());assertEquals(1,endpoint.mutations.size());assertEquals(0,f.pending(f.layer));
                try {LocalChangesRepository.releaseReviewedCopy(app,uid);fail("A stale retry must not ignore the sibling ACK");}
                catch(LocalChangesRepository.SiblingAcknowledgementException expected) { }
                assertTrue(ProjectRecoveryGuard.isHeld(new File(copy.getMapPath())));assertFalse(ProjectRecoveryGuard.isDeliveryBlocked(f.map.getPath()));
                assertTrue(LocalChangesRepository.withLayer(app,uid,row.layerPath,layer->layer.reconcileUpload(operation,42)));
                assertEquals("Server reconciliation must only read",1,endpoint.mutations.size());
                assertTrue(LocalChangesRepository.releaseReviewedCopy(app,uid));assertTrue(upload(copy.getMapPath()));
                assertTrue(f.send());assertEquals(1,endpoint.mutations.size());assertNotNull(f.layer.getFeature(42));
            }finally {
                if(copy!=null)UnderlayFiles.deleteChildTree(new File(copy.getMapPath()).getParentFile(),new File(copy.getMapPath()));
                if(archive!=null)assertTrue(archive.delete());
                if(f.registryBefore!=null)Files.write(f.registry.toPath(),f.registryBefore);else if(f.registry.exists())assertTrue(f.registry.delete());
            }
        }
    }
    private boolean upload(String path) {return com.nextgis.maplibui.mapui.LocalChangesWorker.uploadProject(app,path);}
}
