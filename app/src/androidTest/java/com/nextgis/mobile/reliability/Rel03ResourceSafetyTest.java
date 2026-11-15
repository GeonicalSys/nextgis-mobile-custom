package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.PermissionUtil;
import com.nextgis.maplibui.service.WalkEditService;
import com.nextgis.maplibui.service.WalkGeometrySnapshot;
import com.nextgis.maplibui.util.*;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import static org.junit.Assert.*;

/** Only synthetic verification data. Host restart phases reuse the ordinary runner's safety gates. */
@RunWith(AndroidJUnit4.class)
public class Rel03ResourceSafetyTest {
    private static final String MARKER="rel03-native-cold-handoff.json";
    private Context app;
    private final List<Fixture> fixtures=new ArrayList<>();
    private final List<WalkCheckpointWriter> writers=new ArrayList<>();
    private final List<CountDownLatch> releases=new ArrayList<>();
    private boolean heldForHostRestart;

    @Before public void setup() throws Exception {
        app=InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);
        assertEquals(VerificationEnvironment.PACKAGE,app.getPackageName());
        assertTrue("Host restart recipe is restricted to its owned emulator",VerificationEnvironment.isEmulator());
        long deadline=SystemClock.uptimeMillis()+30000;
        while((SharedUnderlayProjects.isMigrationPending()||ProjectOperationCoordinator.isBusy())
                &&SystemClock.uptimeMillis()<deadline)Thread.sleep(50);
        assertFalse(SharedUnderlayProjects.isMigrationPending()||ProjectOperationCoordinator.isBusy());
    }
    private final class Fixture {
        final File root;
        final String preferences;
        final SharedPreferences prefs;
        final Context context;
        String id;
        Fixture(String preferences,File root) {
            this.root=root;this.preferences=preferences;prefs=app.getSharedPreferences(preferences,Context.MODE_PRIVATE);
            context=new ContextWrapper(app) {
                @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                    return WalkEditService.TEMP_PREFERENCES.equals(name)?prefs:super.getSharedPreferences(name,mode);
                }
            };
            fixtures.add(this);
        }
    }
    private Fixture begin() throws Exception {
        File root=new File(new File(app.getFilesDir(),"rel03-native"),UUID.randomUUID().toString());
        assertTrue(root.mkdirs());Fixture fixture=new Fixture("rel03_walk_"+UUID.randomUUID(),root);
        GeoGeometry full=GeoGeometryFactory.fromWKT("MULTIPOLYGON (((0 0,10 0,10 10,0 0),(2 2,4 2,4 4,2 2)),((100 100,110 100,110 110,100 100)))",GeoConstants.CRS_WEB_MERCATOR);
        fixture.id=WalkSessionStore.begin(fixture.context,-123,-1,full,0,1,3,"");
        assertNotNull(fixture.id);
        // The fixture's private map identity never replaces the application's active map.
        assertTrue(fixture.prefs.edit().putString(WalkSessionStore.KEY_MAP,root.getAbsolutePath()).commit());
        return fixture;
    }
    private WalkCheckpointWriter writer(Fixture fixture,WalkCheckpointWriter.Sink sink) throws Exception {
        WalkSessionStore.Snapshot state=WalkSessionStore.load(fixture.context);assertNotNull(state);
        GeoLineString seed=WalkGeometrySnapshot.part(state.geometry(),state.member,state.ring);
        WalkCheckpointWriter writer=new WalkCheckpointWriter(fixture.root,state.checkpointOwner,seed,
                state.insertion,state.gpsPaused,fixture.prefs.getLong(WalkCheckpointWriter.KEY_SEQUENCE,0),sink,null);
        writers.add(writer);return writer;
    }
    private boolean save(Fixture fixture,GeoLineString part,int index,boolean paused,long sequence) {
        return WalkSessionStore.updateGeometry(fixture.context,fixture.id,part,index,paused,sequence,
                fixture.prefs.getString(WalkCheckpointWriter.KEY_OWNER,fixture.id));
    }
    @After public void cleanup() throws Exception {
        if(heldForHostRestart)return;
        for(CountDownLatch release:releases)release.countDown();
        for(WalkCheckpointWriter writer:writers){writer.close();assertTrue(writer.awaitClosed(5000));}
        for(Fixture fixture:fixtures){
            assertTrue(fixture.prefs.edit().clear().commit());
            File parent=new File(app.getFilesDir(),"rel03-native");
            assertTrue(fixture.root.getCanonicalPath().startsWith(parent.getCanonicalPath()+File.separator));
            if(fixture.root.exists())try(Stream<Path> paths=Files.walk(fixture.root.toPath())) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    assertFalse(Files.isSymbolicLink(path));Files.delete(path);
                }
            }
        }
    }
    @Test public void fullCheckpointRunsOffMainAndFinishPreservesOtherPartsAndHoles() throws Exception {
        Fixture fixture=begin();WalkSessionStore.Snapshot before=WalkSessionStore.load(fixture.context);
        String other=((GeoGeometryCollection)before.geometry()).get(1).toWKT(true);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        releases.add(release);AtomicBoolean timeout=new AtomicBoolean(),ok=new AtomicBoolean();
        WalkCheckpointWriter writer=writer(fixture,(part,index,paused,sequence)-> {
            assertNotEquals(android.os.Looper.getMainLooper().getThread(),Thread.currentThread());
            entered.countDown();try {if(!release.await(5,TimeUnit.SECONDS)){timeout.set(true);return false;}}
            catch(InterruptedException error){return false;}
            return save(fixture,part,index,paused,sequence);
        });
        assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.insert(3,new GeoPoint(3,4),4,false));
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()-> {
            assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.correct(3,new GeoPoint(3.5,4.5),4,false));
            assertTrue(writer.checkpoint(4,true));
            assertFalse(WalkSessionStore.setPhase(fixture.context,fixture.id,WalkSessionPolicy.Phase.FINISHED));
        });
        assertFalse(timeout.get());
        writer.finishAsync(false,saved->{ok.set(saved);finished.countDown();});
        release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));assertTrue(ok.get());
        assertTrue(WalkSessionStore.setPhase(fixture.context,fixture.id,WalkSessionPolicy.Phase.FINISHED));
        WalkSessionStore.Snapshot state=WalkSessionStore.load(fixture.context);
        GeoGeometry full=state.geometry();GeoLineString part=WalkGeometrySnapshot.part(full,0,1);
        assertEquals(4,part.getPointCount());assertEquals(3.5,part.getPoint(3).getX(),0);
        assertEquals(other,((GeoGeometryCollection)full).get(1).toWKT(true));assertTrue(state.gpsPaused);
        assertEquals(1,((GeoPolygon)((GeoGeometryCollection)full).get(0)).getInnerRingCount());
    }
    @Test public void coldPendingStateRemainsRecoverable() throws Exception {
        String stage=InstrumentationRegistry.getArguments().getString("rel03Stage","single");
        if("replay".equals(stage)) {
            File marker=new File(app.getFilesDir(),MARKER);
            JSONObject saved=new JSONObject(new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8));
            assertNotEquals(saved.getInt("pid"),Process.myPid());
            Fixture fixture=new Fixture(saved.getString("preferences"),new File(saved.getString("root")));
            fixture.id=saved.getString("id");
            assertEquals(0,fixture.prefs.getLong(WalkCheckpointWriter.KEY_SEQUENCE,0));
            if("permission-loss".equals(InstrumentationRegistry.getArguments().getString("rel03Transition")))
                assertFalse(PermissionUtil.hasLocationPermissions(app));
            recover(fixture);assertTrue(marker.delete());return;
        }
        Fixture fixture=begin();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);
        if("seed".equals(stage) && "permission-loss".equals(InstrumentationRegistry.getArguments().getString("rel03Transition")))
            assertTrue(PermissionUtil.hasLocationPermissions(app));
        WalkCheckpointWriter writer=writer(fixture,(part,index,paused,sequence)-> {
            entered.countDown();if("seed".equals(stage))try{release.await(120,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
            return false;
        });
        assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.insert(3,new GeoPoint(3,4),4,false));
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.correct(3,new GeoPoint(3.5,4.5),4,false));
        assertTrue(writer.checkpoint(4,true));
        if("seed".equals(stage)) {
            File marker=new File(app.getFilesDir(),MARKER);assertFalse(marker.exists());
            JSONObject value=new JSONObject().put("pid",Process.myPid()).put("root",fixture.root.getAbsolutePath())
                    .put("preferences",fixture.preferences).put("id",fixture.id);
            try(FileOutputStream output=new FileOutputStream(marker)) {
                output.write(value.toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();
            }
            heldForHostRestart=true;return;
        }
        assertFalse(writer.flush(5000));writer.close();assertTrue(writer.awaitClosed(5000));recover(fixture);
    }
    private void recover(Fixture fixture) {
        WalkSessionStore.Snapshot recovered=WalkSessionStore.load(fixture.context);assertNotNull(recovered);
        assertEquals(4,WalkGeometrySnapshot.part(recovered.geometry(),0,1).getPointCount());
        assertEquals(3.5,WalkGeometrySnapshot.part(recovered.geometry(),0,1).getPoint(3).getX(),0);
        assertTrue(recovered.gpsPaused);
        assertTrue(WalkSessionStore.setPhase(fixture.context,fixture.id,WalkSessionPolicy.Phase.FINISHED));
        assertEquals(3,fixture.prefs.getLong(WalkCheckpointWriter.KEY_SEQUENCE,0));
        assertFalse(WalkSessionStore.updateGeometry(fixture.context,fixture.id,new GeoLineString(),0,false));
    }
    @Test public void unreadableJournalKeepsTheOwnerVisibleAndRefusesFinish() throws Exception {
        Fixture fixture=begin();WalkCheckpointWriter writer=writer(fixture,(part,index,paused,sequence)->false);
        assertEquals(WalkCheckpointWriter.Admission.DURABLE,writer.insert(3,new GeoPoint(3,4),4,false));
        assertFalse(writer.flush(5000));writer.close();assertTrue(writer.awaitClosed(5000));
        Path journal;
        try(Stream<Path> paths=Files.walk(fixture.root.toPath())) {
            journal=paths.filter(path->path.toString().endsWith(".json")).findFirst().get();
        }
        Files.write(journal,"broken committed record".getBytes(StandardCharsets.UTF_8));
        Map<String,?> before=fixture.prefs.getAll();
        WalkSessionStore.Snapshot state=WalkSessionStore.load(fixture.context);assertNotNull(state);
        assertEquals(fixture.id,state.id);assertTrue(state.hasCheckpointFailure());assertNull(state.geometry());
        assertFalse(WalkSessionStore.setPhase(fixture.context,fixture.id,WalkSessionPolicy.Phase.FINISHED));
        assertEquals(before,fixture.prefs.getAll());assertTrue(Files.exists(journal));
    }
    @Test public void staleCallbacksCannotChangeAReplacementOwnerOrItsPointLock() throws Exception {
        Fixture fixture=begin();String original=fixture.id;
        assertTrue(fixture.prefs.edit().putString(WalkSessionStore.KEY_SESSION,"replacement")
                .putString(WalkCheckpointWriter.KEY_OWNER,"replacement").putString(WalkSessionStore.KEY_POINT,"protected-point").commit());
        Map<String,?> before=fixture.prefs.getAll();
        assertFalse(WalkSessionStore.updateGeometry(fixture.context,original,new GeoLineString(),0,false,99,original));
        assertFalse(WalkSessionStore.setPhase(fixture.context,original,WalkSessionPolicy.Phase.FINISHED));
        assertFalse(WalkSessionStore.clear(fixture.context,original));assertEquals(before,fixture.prefs.getAll());
    }
}
