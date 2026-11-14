package com.nextgis.mobile.reliability;

import android.content.*;
import android.database.Cursor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.GISApplication;
import java.io.File;
import java.nio.file.Files;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native SQLite and loopback HTTP; the final two phases cross a real process restart. */
@RunWith(AndroidJUnit4.class)
public class ReliableChangesNativeTest {
    private EmulatorNetworkFixture network;
    private Context context;
    @Before public void isolatedNetwork() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        com.nextgis.mobile.util.VerificationEnvironment.requireIsolated(context);
        assertTrue("Emulator only", "ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE));
        network=new EmulatorNetworkFixture(context);network.connected(true);
    }
    @After public void restoreNetwork() throws Exception { if(network!=null)network.close(); }
    private UploadOperationJournal.Entry receipt(LocalChangesUploadTest.Fixture f) {
        return UploadOperationJournal.entries(f.map.getDatabase(false),f.layer.getPath().getName()).get(0);
    }
    @Test public void missingIdentifierKeepsTheObjectVisibleAndRequiresReconciliation() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            long id=f.savedLine(f.layer,false);endpoint.creationReply="{}";
            assertFalse(f.send());assertEquals(1,f.pending(f.layer));assertNotNull(f.layer.getFeature(id));
            String operation=receipt(f).id;assertEquals(UploadOperationJournal.UNCERTAIN,receipt(f).state);
            endpoint.creationReply=null;assertFalse(f.send());assertEquals(1,endpoint.mutations.size());
            endpoint.serveFeatures=true;assertTrue(f.layer.reconcileCreatedFeature(operation,42));
            assertEquals(0,f.pending(f.layer));assertNotNull(f.layer.getFeature(42));assertEquals(1,endpoint.mutations.size());
        }
    }
    @Test public void acceptedPostWithLostResponseIsNotRepeatedAndOtherObjectsStillUpload() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            f.savedLine(f.layer,false);endpoint.dropCreationResponse=true;
            assertFalse(f.send());assertEquals(1,f.pending(f.layer));assertEquals(1,endpoint.mutations.size());
            endpoint.dropCreationResponse=false;f.savedLine(f.layer,false);
            assertFalse(f.send());assertEquals(1,f.pending(f.layer));assertEquals(2,endpoint.mutations.size());
            assertNotNull(f.layer.getFeature(43));
        }
    }
    @Test public void serverFailureRequiresReviewRatherThanAutomaticDuplicateCreation() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            f.savedLine(f.layer,false);endpoint.status=503;assertFalse(f.send());
            endpoint.status=200;assertFalse(f.send());assertEquals(1,endpoint.mutations.size());assertEquals(1,f.pending(f.layer));
        }
    }
    @Test public void failedFormSaveHoldSurvivesRetryUntilItsOwnOperationCompletes() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint();
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint)) {
            long id=f.savedLine(f.layer,false);
            FeatureSaveJournal.holdUpload(f.map.getDatabase(false),f.layer.getPath().getName(),id,"own-form");
            FeatureSaveJournal.releaseUpload(f.map.getDatabase(false),f.layer.getPath().getName(),"stale-form");
            assertFalse(f.send());assertTrue(endpoint.mutations.isEmpty());
            FeatureSaveJournal.releaseUpload(f.map.getDatabase(false),f.layer.getPath().getName(),"own-form");
            assertTrue(f.send());assertEquals(0,f.pending(f.layer));assertEquals(1,endpoint.mutations.size());
        }
    }
    @Test public void seedAcceptedReceiptBeforeProcessDeath() throws Exception {
        try(LocalChangesUploadTest.Endpoint endpoint=new LocalChangesUploadTest.Endpoint()) {
            LocalChangesUploadTest.Fixture f=new LocalChangesUploadTest.Fixture(endpoint);
            long id=f.savedLine(f.layer,false);
            File folder=new File(f.layer.getPath(),Long.toString(id));assertTrue(folder.mkdirs());
            Files.write(new File(folder,"synthetic-photo.bin").toPath(),new byte[]{9,2,7,1});
            ContentValues collision=new ContentValues();collision.put("_id",42);
            collision.put(Constants.FIELD_GEOM,f.layer.getFeature(id).getGeometry().toBlob());
            collision.put("name","synthetic collision");
            assertTrue(f.map.getDatabase(false).insert(f.layer.getPath().getName(),null,collision)>=0);
            assertFalse(f.send());assertEquals(UploadOperationJournal.ACCEPTED,receipt(f).state);
            assertEquals(42,receipt(f).remoteId);
            assertEquals(1,f.map.getDatabase(false).delete(f.layer.getPath().getName(),"_id=42",null));
            f.layer.setSyncDirection(2);assertTrue(f.layer.save());assertTrue(f.map.save());f.cancelJobs();
            assertTrue(context.getSharedPreferences("reliable-creation-boundary",0).edit()
                    .putString("operation",receipt(f).id).putLong("local_id",id)
                    .putString("account",f.account.name).putInt("pid",android.os.Process.myPid()).commit());
            assertEquals(1,endpoint.mutations.size());
            // The restart phase deliberately owns cleanup of this isolated fixture.
        }
    }
    @Test public void acceptedReceiptCompletesAfterRealProcessRestartWithoutAnotherPost() throws Exception {
        SharedPreferences evidence=context.getSharedPreferences("reliable-creation-boundary",0);
        assertNotEquals("Runner must force-stop between phases",evidence.getInt("pid",-1),android.os.Process.myPid());
        GISApplication app=(GISApplication)context.getApplicationContext();
        MapContentProviderHelper map=(MapContentProviderHelper)app.getMap();
        NGWVectorLayer layer=(NGWVectorLayer)map.getLayerByPathName("local_delivery_line");assertNotNull(layer);
        String operation=evidence.getString("operation",null);assertNotNull(operation);
        UploadOperationJournal.Entry before=UploadOperationJournal.find(map.getDatabase(false),layer.getPath().getName(),operation);
        assertNotNull(before);assertEquals(UploadOperationJournal.ACCEPTED,before.state);
        layer.setSyncDirection(3);assertTrue(layer.sendLocalChanges(new SyncResult()));
        assertEquals(0,FeatureChanges.getChangeCount(map.getDatabase(false),layer.getChangeTableName()));
        assertEquals(UploadOperationJournal.CONFIRMED,UploadOperationJournal.find(map.getDatabase(false),layer.getPath().getName(),operation).state);
        assertArrayEquals(new byte[]{9,2,7,1},Files.readAllBytes(new File(layer.getPath(),"42/synthetic-photo.bin").toPath()));
        try(Cursor c=map.getDatabase(false).query(layer.getPath().getName(),new String[]{"_id"},"_id=?",
                new String[]{Long.toString(evidence.getLong("local_id",-1))},null,null,null)) { assertFalse(c.moveToFirst()); }
        assertNotNull(layer.getFeature(42));
        String account=evidence.getString("account",null);assertTrue(layer.delete(false));map.save();
        assertTrue(android.accounts.AccountManager.get(context).removeAccountExplicitly(app.getAccount(account)));
        assertTrue(evidence.edit().clear().commit());
    }
}
