package com.nextgis.mobile.reliability;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
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
}
