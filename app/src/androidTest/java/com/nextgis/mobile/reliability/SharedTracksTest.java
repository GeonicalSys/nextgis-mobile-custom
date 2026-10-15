package com.nextgis.mobile.reliability;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.datasource.DatabaseHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FeatureSaveJournal;
import com.nextgis.maplib.util.PendingTrackPoints;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.SharedTrackProjects;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static com.nextgis.maplib.map.TrackLayer.*;
import static org.junit.Assert.*;

/** Actual provider, map reload, project lifecycle and unchanged recorder switch gate. */
@RunWith(AndroidJUnit4.class)
public class SharedTracksTest {
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 30000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
        assertTrue("Shared track state did not settle", condition.getAsBoolean());
    }
    private static class Fixture implements AutoCloseable {
        final GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        final boolean hadSend = prefs.contains(SettingsConstants.KEY_PREF_TRACK_SEND);
        final boolean oldSend = prefs.getBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, true);
        final boolean hadIntro = prefs.contains(AppSettingsConstants.KEY_PREF_INTRO);
        final boolean oldIntro = prefs.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        final CollectorProjectRegistry.ProjectInfo original;
        final CollectorProjectRegistry.ProjectInfo other;
        final File registry;
        final byte[] registryBefore;
        final ActivityScenario<MainActivity> screen;
        final List<Long> added = new ArrayList<>();
        final boolean oldVisible;
        Fixture() throws Exception {
            VerificationEnvironment.requireIsolated(app);
            assertTrue("Emulator only", VerificationEnvironment.isEmulator());
            assertFalse(TrackerService.hasRecordingSession(app));
            prefs.edit().putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false)
                    .putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true).commit();
            screen = ActivityScenario.launch(MainActivity.class);
            await(() -> app.getMap() != null);
            await(() -> !com.nextgis.maplibui.util.SharedUnderlayProjects.isMigrationPending()
                    && !com.nextgis.maplibui.util.ProjectOperationCoordinator.isBusy());
            original = CollectorProjectRegistry.getActiveProject(app); assertNotNull(original);
            registry = new File(app.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP), "collector_projects/collector_projects_registry.json");
            registryBefore = Files.readAllBytes(registry.toPath());
            other = CollectorProjectRegistry.createLocalProject(app, "Shared tracks " + UUID.randomUUID()); assertNotNull(other);
            oldVisible = layer().isVisible();
        }
        TrackLayer layer() { return (TrackLayer) MapContentProviderHelper.getVectorLayerByPath(app.getMap(), TABLE_TRACKS); }
        Uri uri() { return Uri.parse("content://" + app.getAuthority() + "/tracks"); }
        void switchTo(CollectorProjectRegistry.ProjectInfo project) throws Exception {
            screen.onActivity(activity -> assertTrue(CollectorProjectRegistry.activateProject(activity, project.getProjectUid())));
            screen.recreate();
            await(() -> project.isActive(app));
            List<ILayer> layers = new ArrayList<>(); LayerGroup.getLayersByType(app.getMap(), Constants.LAYERTYPE_TRACKS, layers);
            assertEquals(1, layers.size());
        }
        long addTrack() {
            ContentValues track = new ContentValues(); track.put(FIELD_NAME, "Shared history fixture");
            track.put(FIELD_START, 100); track.put(FIELD_END, 900); track.put(FIELD_VISIBLE, 1); track.put(FIELD_COLOR, 123);
            long id = ContentUris.parseId(app.getContentResolver().insert(uri(), track)); added.add(id);
            for (int i=0; i<2; i++) {
                ContentValues point = new ContentValues(); point.put(FIELD_SESSION, id);
                point.put(FIELD_LON, 3339584.7+i*10); point.put(FIELD_LAT, 8399737.9+i*10);
                point.put(FIELD_TIMESTAMP, 500+i); point.put(FIELD_SENT, 1); point.put(FIELD_SEGMENT, 0);
                assertNotNull(app.getContentResolver().insert(Uri.parse("content://"+app.getAuthority()+"/trackpoints"),point));
            }
            return id;
        }
        void assertTrack(long id, int color) {
            try (Cursor rows = app.getContentResolver().query(uri(), new String[]{FIELD_COLOR}, FIELD_ID+"=?",
                    new String[]{Long.toString(id)}, null)) {
                assertNotNull(rows); assertEquals(1,rows.getCount()); assertTrue(rows.moveToFirst()); assertEquals(color,rows.getInt(0));
            }
            try (Cursor points = app.getContentResolver().query(ContentUris.withAppendedId(uri(),id),
                    new String[]{FIELD_TIMESTAMP}, null,null,POINT_ORDER)) {
                assertNotNull(points); assertEquals(2,points.getCount()); assertTrue(points.moveToFirst()); assertEquals(500,points.getLong(0));
            }
            assertTrue(layer().getTracks().containsKey((int)id));
        }
        void assertRendered() throws Exception {
            AtomicBoolean visible = new AtomicBoolean();
            await(() -> {
                screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) app.getMap();
                    if (map.getMaplibreMap() == null || map.getMaplibreMap().getStyle() == null) return;
                    org.maplibre.android.style.sources.GeoJsonSource source =
                            map.getMaplibreMap().getStyle().getSourceAs("track-line-source");
                    if (source == null) return;
                    map.getMaplibreMap().moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            new org.maplibre.android.geometry.LatLng(60, 30), 15));
                    map.reloadTrackListToMap();
                    for (org.maplibre.geojson.Feature feature : source.querySourceFeatures(null)) {
                        if (feature.geometry() instanceof org.maplibre.geojson.LineString) {
                            org.maplibre.geojson.LineString line = (org.maplibre.geojson.LineString) feature.geometry();
                            if (line.coordinates().size() == 2 && Math.abs(line.coordinates().get(0).longitude() - 30) < .001)
                                visible.set(true);
                        }
                    }
                });
                return visible.get();
            });
        }
        @Override public void close() throws Exception {
            if (TrackerService.hasRecordingSession(app)) {
                screen.onActivity(TrackerService::start_stop_tracking_GetIconWithTitle);
                await(() -> TrackerService.getRecordingState(app)==TrackerService.RecordingState.STOPPED);
            }
            screen.onActivity(activity -> assertTrue(CollectorProjectRegistry.activateProject(activity, original.getProjectUid())));
            for (long id:added) app.getContentResolver().delete(uri(),FIELD_ID+"=?",new String[]{Long.toString(id)});
            layer().setVisible(oldVisible); screen.close();
            Files.write(registry.toPath(),registryBefore);
            FileUtil.deleteRecursive(new File(other.getMapPath()));
            SharedPreferences.Editor restore=prefs.edit();
            if(hadSend)restore.putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND,oldSend);else restore.remove(SettingsConstants.KEY_PREF_TRACK_SEND);
            if(hadIntro)restore.putBoolean(AppSettingsConstants.KEY_PREF_INTRO,oldIntro);else restore.remove(AppSettingsConstants.KEY_PREF_INTRO);
            assertTrue(restore.commit());
        }
    }

    @Test public void commonHistoryColorAndVisibilitySurviveSwitchAndProjectDeletion() throws Exception {
        try(Fixture f=new Fixture()) {
            long id=f.addTrack(); f.assertTrack(id,123); f.assertRendered();
            ContentValues change=new ContentValues();change.put(FIELD_COLOR,456);
            assertEquals(1,f.app.getContentResolver().update(f.uri(),change,FIELD_ID+"=?",new String[]{Long.toString(id)}));
            f.layer().setVisible(false); f.switchTo(f.other); assertFalse(f.layer().isVisible()); f.assertTrack(id,456);
            f.layer().setVisible(true); f.switchTo(f.original); assertTrue(f.layer().isVisible()); f.assertTrack(id,456); f.assertRendered();
            f.switchTo(f.other);
            AtomicReference<CollectorProjectRegistry.DeleteResult> deleted=new AtomicReference<>();
            f.screen.onActivity(activity -> deleted.set(CollectorProjectRegistry.deleteActiveProject(activity,
                    f.other.getProjectUid(),"Fallback",()->true)));
            assertEquals(CollectorProjectRegistry.DeleteResult.Status.SUCCESS,deleted.get().getStatus());
            f.screen.recreate(); f.assertTrack(id,456); f.assertRendered();
            assertTrue(SharedTrackStore.get(f.app).getDirectory().isDirectory());
        }
    }

    @Test public void projectSwitchRemainsBlockedUntilRecordingIsStopped() throws Exception {
        try(Fixture f=new Fixture()) {
            SharedPreferences temp=f.app.getSharedPreferences(TrackerService.TEMP_PREFERENCES,0);
            f.screen.onActivity(TrackerService::start_stop_tracking_GetIconWithTitle);
            await(TrackerService::isTrackRecordingActive);
            String current=temp.getString("track_uri",null);assertNotNull(current);
            long id=ContentUris.parseId(Uri.parse(current)); f.added.add(id);
            f.screen.onActivity(activity -> assertFalse(CollectorProjectRegistry.activateProject(activity,f.other.getProjectUid())));
            assertTrue(f.original.isActive(f.app)); assertEquals(current,temp.getString("track_uri",null));
            assertTrue(TrackerService.isTrackRecordingActive());
            f.screen.onActivity(TrackerService::start_stop_tracking_GetIconWithTitle);
            await(() -> TrackerService.getRecordingState(f.app)==TrackerService.RecordingState.STOPPED);
            f.switchTo(f.other);
            try(Cursor rows=f.app.getContentResolver().query(f.uri(),new String[]{FIELD_END},FIELD_ID+"=?",new String[]{Long.toString(id)},null)) {
                assertNotNull(rows);assertTrue(rows.moveToFirst());assertFalse(rows.isNull(0));
            }
        }
    }

    private static File seedLegacyDatabase(Fixture f, boolean unfinished) throws Exception {
        return seedLegacyDatabase(f,f.other,unfinished,
                "true".equals(InstrumentationRegistry.getArguments().getString("legacyTrackFixture")));
    }

    private static File seedLegacyDatabase(Fixture f, CollectorProjectRegistry.ProjectInfo project,
                                           boolean unfinished, boolean externalFixture) throws Exception {
        File file = new File(project.getMapPath(),"layers.db");
        if (externalFixture) {
            assertFalse("External history fixture is for the completed-history case",unfinished);
            File fixture = new File(f.app.getExternalFilesDir(null),"legacy-tracks-fixture.db");
            assertTrue("Explicit local track-only fixture is missing",fixture.isFile());
            assertFalse("Never overwrite an existing project database",file.exists());
            Files.copy(fixture.toPath(),file.toPath());
            return file;
        }
        try (DatabaseHelper helper=new DatabaseHelper(f.app,new File(project.getMapPath(),"layers"),null,6)) {
            SQLiteDatabase db=helper.getWritableDatabase();
            assertEquals(file.getAbsolutePath(),db.getPath());
            db.execSQL("CREATE TABLE tracks(_id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER,color TEXT,visible INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE trackpoints(lon REAL NOT NULL,lat REAL NOT NULL,ele REAL,fix TEXT,sat INTEGER,speed REAL,accuracy REAL,bearing REAL,segment INTEGER NOT NULL DEFAULT 0,time INTEGER NOT NULL,sent INTEGER NOT NULL,session INTEGER NOT NULL)");
            db.execSQL("INSERT INTO tracks VALUES(1,'Legacy history',100,?,123,1)",new Object[]{unfinished?null:900});
            for(int index=0;index<2;index++) db.insertOrThrow(TABLE_TRACKPOINTS,null,legacyPoint(100+index,index,index));
        }
        return file;
    }

    private static ContentValues legacyPoint(long time,int sent,int segment) {
        ContentValues point=new ContentValues(); point.put(FIELD_SESSION,1);
        point.put(FIELD_LON,3339584.7);point.put(FIELD_LAT,8399737.9);
        point.put(FIELD_TIMESTAMP,time);point.put(FIELD_SENT,sent);point.put(FIELD_SEGMENT,segment);
        point.put(FIELD_ELE,12.5);point.put(FIELD_FIX,"3d");point.put(FIELD_SAT,9);
        point.put(FIELD_SPEED,1.25);point.put(FIELD_ACCURACY,4.5);point.put(FIELD_BEARING,90.5);
        return point;
    }

    private static void assertSameValues(Cursor expected,Cursor actual) {
        for(String name:expected.getColumnNames()) {
            if(FIELD_ID.equals(name)||FIELD_SESSION.equals(name))continue;
            int source=expected.getColumnIndexOrThrow(name),target=actual.getColumnIndexOrThrow(name);
            assertEquals(name,expected.getType(source),actual.getType(target));
            switch(expected.getType(source)) {
                case Cursor.FIELD_TYPE_NULL:assertTrue(name,actual.isNull(target));break;
                case Cursor.FIELD_TYPE_INTEGER:assertEquals(name,expected.getLong(source),actual.getLong(target));break;
                case Cursor.FIELD_TYPE_FLOAT:assertEquals(name,expected.getDouble(source),actual.getDouble(target),0);break;
                default:assertEquals(name,expected.getString(source),actual.getString(target));
            }
        }
    }

    @Test public void completedLegacyHistoryAppearsBesideTracksRecordedAfterUpdate() throws Exception {
        try(Fixture f=new Fixture()) {
            long fresh=f.addTrack();
            File file;byte[] sourceBefore;
            // Publish a complete legacy fixture before a queued worker can inspect it.
            synchronized(SharedTrackProjects.class) {
                file=seedLegacyDatabase(f,false);sourceBefore=Files.readAllBytes(file.toPath());
                SharedTrackProjects.prepare(f.app);
            }
            SharedTrackStore store=SharedTrackStore.get(f.app);
            try(SQLiteDatabase legacy=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
                long tracks=DatabaseUtils.queryNumEntries(legacy,TABLE_TRACKS),points=DatabaseUtils.queryNumEntries(legacy,TABLE_TRACKPOINTS);
                assertTrue(tracks>0);assertTrue(points>0);
                SharedTrackProjects.prepare(f.app);SharedTrackProjects.prepare(f.app);
                assertTrue(f.original.isActive(f.app));
                assertEquals(tracks+1,DatabaseUtils.queryNumEntries(store.getDatabase(true),TABLE_TRACKS));
                assertEquals(points+2,DatabaseUtils.queryNumEntries(store.getDatabase(true),TABLE_TRACKPOINTS));
                try(Cursor histories=legacy.query(TABLE_TRACKS,null,null,null,null,null,FIELD_ID)) {
                    while(histories.moveToNext()) {
                        long old=histories.getLong(histories.getColumnIndexOrThrow(FIELD_ID));
                        long id=store.migratedTrackId(new File(f.other.getMapPath()),old);
                        assertNotEquals(fresh,id);f.added.add(id);
                        try(Cursor migrated=store.getDatabase(true).query(TABLE_TRACKS,null,FIELD_ID+"=?",new String[]{Long.toString(id)},null,null,null)) {
                            assertTrue(migrated.moveToFirst());assertSameValues(histories,migrated);
                        }
                        try(Cursor original=legacy.query(TABLE_TRACKPOINTS,null,FIELD_SESSION+"=?",new String[]{Long.toString(old)},null,null,"rowid");
                            Cursor migrated=store.getDatabase(true).query(TABLE_TRACKPOINTS,null,FIELD_SESSION+"=?",new String[]{Long.toString(id)},null,null,FIELD_ID)) {
                            assertEquals(original.getCount(),migrated.getCount());
                            while(original.moveToNext()){assertTrue(migrated.moveToNext());assertSameValues(original,migrated);}
                        }
                    }
                }
                android.util.Log.i("LegacyTrackRecovery","Verified all fields: tracks="+tracks+" points="+points);
            }
            assertArrayEquals(sourceBefore,Files.readAllBytes(file.toPath()));
            f.assertTrack(fresh,123);f.assertRendered();f.switchTo(f.other);f.assertTrack(fresh,123);f.assertRendered();
        }
    }

    @Test public void interruptedRecordingMigratesItsCheckpointAndPendingStopWithoutLosingPoints() throws Exception {
        try(Fixture f=new Fixture()) {
            long fresh=f.addTrack();File workspace=new File(f.other.getMapPath());
            SharedPreferences temp=f.app.getSharedPreferences(TrackerService.TEMP_PREFERENCES,0);
            long recovered;
            synchronized(SharedTrackProjects.class) {
                File file=seedLegacyDatabase(f,true);PendingTrackPoints queue=new PendingTrackPoints(workspace);
                queue.append(legacyPoint(200,0,2));queue.append(legacyPoint(201,0,3));
                try(SQLiteDatabase legacy=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READWRITE)) {
                    queue.replay((operation,values)->{
                        if(values.getAsLong(FIELD_TIMESTAMP)==200) {
                            long row=legacy.insertOrThrow(TABLE_TRACKPOINTS,null,values);
                            FeatureSaveJournal.record(legacy,TABLE_TRACKPOINTS,operation,row);
                        }
                        return 1;
                    });
                }
                assertTrue(temp.edit().putString("track_map",workspace.getAbsolutePath())
                        .putString("track_uri",ContentUris.withAppendedId(f.uri(),1).toString())
                        .putString("pending_stop",TrackerService.ACTION_STOP).commit());
                TrackerService.setTrackRecordingEnabled(f.app,true);assertTrue(TrackerService.isTrackRecordingEnabled(f.app));
                SharedTrackProjects.prepare(f.app);SharedTrackProjects.prepare(f.app);
                recovered=SharedTrackStore.get(f.app).migratedTrackId(workspace,1);assertNotEquals(fresh,recovered);f.added.add(recovered);
            }
            assertEquals(recovered,ContentUris.parseId(Uri.parse(temp.getString("track_uri",null))));
            assertEquals(SharedTrackStore.get(f.app).getDirectory().getAbsolutePath(),temp.getString("track_map",null));
            assertEquals(TrackerService.ACTION_STOP,temp.getString("pending_stop",null));assertTrue(f.original.isActive(f.app));
            f.screen.onActivity(TrackerService::ensureRecordingRunningIfEnabled);
            await(()->TrackerService.getRecordingState(f.app)==TrackerService.RecordingState.STOPPED);
            try(Cursor points=SharedTrackStore.get(f.app).getDatabase(true).query(TABLE_TRACKPOINTS,null,FIELD_SESSION+"=?",new String[]{Long.toString(recovered)},null,null,FIELD_ID)) {
                assertEquals(4,points.getCount());
                for(int index=0;index<4;index++){assertTrue(points.moveToNext());assertEquals(index,points.getInt(points.getColumnIndexOrThrow(FIELD_SEGMENT)));}
            }
            try(Cursor track=SharedTrackStore.get(f.app).getDatabase(true).query(TABLE_TRACKS,null,FIELD_ID+"=?",new String[]{Long.toString(recovered)},null,null,null)) {
                assertTrue(track.moveToFirst());assertTrue(track.getLong(track.getColumnIndexOrThrow(FIELD_END))>900);
            }
            assertFalse(temp.contains("track_uri"));assertFalse(temp.contains("pending_stop"));
            assertEquals(2,new PendingTrackPoints(workspace).size());f.assertTrack(fresh,123);
        }
    }

    @Test public void allProjectsImportCollidingTrackIdsWithoutActivatingTheirMaps() throws Exception {
        try(Fixture f=new Fixture()) {
            CollectorProjectRegistry.ProjectInfo third=CollectorProjectRegistry.createLocalProject(f.app,"More legacy tracks");
            assertNotNull(third);
            try {
                long fresh=f.addTrack();
                File[] sources;byte[][] before;
                synchronized(SharedTrackProjects.class) {
                    sources=new File[]{seedLegacyDatabase(f,f.other,false,false),seedLegacyDatabase(f,third,false,false)};
                    try(SQLiteDatabase legacy=SQLiteDatabase.openDatabase(sources[1].getPath(),null,SQLiteDatabase.OPEN_READWRITE)) {
                        legacy.execSQL("UPDATE tracks SET name='Another project history',color=456");
                        legacy.execSQL("UPDATE trackpoints SET time=time+1000,segment=segment+2");
                    }
                    before=new byte[][]{Files.readAllBytes(sources[0].toPath()),Files.readAllBytes(sources[1].toPath())};
                    SharedTrackProjects.prepare(f.app);
                }
                SharedTrackStore store=SharedTrackStore.get(f.app);
                long first=store.migratedTrackId(sources[0].getParentFile(),1);
                long second=store.migratedTrackId(sources[1].getParentFile(),1);
                assertTrue(first>0);assertTrue(second>0);
                f.added.add(first);f.added.add(second);
                assertNotEquals(first,second);assertNotEquals(fresh,first);assertNotEquals(fresh,second);
                SharedTrackProjects.prepare(f.app);SharedTrackProjects.prepare(f.app);
                assertTrue("Migration must not activate an inactive project",f.original.isActive(f.app));
                assertEquals(3,DatabaseUtils.queryNumEntries(store.getDatabase(true),TABLE_TRACKS));
                assertEquals(6,DatabaseUtils.queryNumEntries(store.getDatabase(true),TABLE_TRACKPOINTS));
                for(int index=0;index<sources.length;index++) {
                    assertArrayEquals(before[index],Files.readAllBytes(sources[index].toPath()));
                    long id=index==0?first:second;
                    try(SQLiteDatabase legacy=SQLiteDatabase.openDatabase(sources[index].getPath(),null,SQLiteDatabase.OPEN_READONLY);
                        Cursor track=legacy.query(TABLE_TRACKS,null,null,null,null,null,null);
                        Cursor migrated=store.getDatabase(true).query(TABLE_TRACKS,null,FIELD_ID+"=?",new String[]{Long.toString(id)},null,null,null);
                        Cursor points=legacy.query(TABLE_TRACKPOINTS,null,null,null,null,null,"rowid");
                        Cursor imported=store.getDatabase(true).query(TABLE_TRACKPOINTS,null,FIELD_SESSION+"=?",new String[]{Long.toString(id)},null,null,FIELD_ID)) {
                        assertTrue(track.moveToFirst());assertTrue(migrated.moveToFirst());assertSameValues(track,migrated);
                        assertEquals(points.getCount(),imported.getCount());
                        while(points.moveToNext()){assertTrue(imported.moveToNext());assertSameValues(points,imported);}
                    }
                }
                f.assertTrack(fresh,123);f.switchTo(f.other);
                assertTrue(f.layer().getTracks().containsKey((int)first));assertTrue(f.layer().getTracks().containsKey((int)second));
                f.switchTo(third);f.assertTrack(fresh,123);
                assertTrue(f.layer().getTracks().containsKey((int)first));assertTrue(f.layer().getTracks().containsKey((int)second));
            } finally {
                f.switchTo(f.original);
                FileUtil.deleteRecursive(new File(third.getMapPath()));
            }
        }
    }
}
