package com.nextgis.mobile.reliability;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
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

import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.util.PendingTrackPoints;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** The actual foreground recorder must retain its tail and Stop intent until SQLite recovers. */
@RunWith(AndroidJUnit4.class)
public class TrackerStopRecoveryTest {
    private static void await(AtomicBoolean condition, long timeout, Runnable check) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (!condition.get() && SystemClock.uptimeMillis() < deadline) {
            check.run();
            if (!condition.get()) Thread.sleep(100);
        }
        assertTrue("Recorder did not reach the expected state", condition.get());
    }

    @Test public void failedStopDoesNotCloseOrDiscardTheUnwrittenTail() throws Exception {
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(app);
        SharedPreferences temporary = app.getSharedPreferences(TrackerService.TEMP_PREFERENCES, Context.MODE_PRIVATE);
        assertFalse("Run only on an isolated emulator without a user recording", TrackerService.isTrackRecordingEnabled(app));
        assertFalse(temporary.contains("track_uri"));
        assertFalse(temporary.contains("pending_stop"));
        boolean hadSend = preferences.contains(SettingsConstants.KEY_PREF_TRACK_SEND);
        boolean oldSend = preferences.getBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, true);
        assertTrue(preferences.edit().putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false).commit());
        boolean hadIntro = preferences.contains(AppSettingsConstants.KEY_PREF_INTRO);
        boolean oldIntro = preferences.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        boolean hadFlag = preferences.contains(SettingsConstants.KEY_PREF_TRACK_RECORDING_ENABLED);
        boolean hadFailure = preferences.contains("track_recording_failure");
        boolean oldFailure = preferences.getBoolean("track_recording_failure", false);
        assertTrue(preferences.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true).commit());
        SQLiteDatabase db = null;
        TrackLayer layer = null;
        Uri trackUri = null;
        String trigger = "reject_tail_" + UUID.randomUUID().toString().replace("-", "");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean ready = new AtomicBoolean();
            await(ready, 30000, () -> scenario.onActivity(activity -> ready.set(app.getMap() != null
                    && activity.getMapFragment() != null)));
            MapContentProviderHelper map = (MapContentProviderHelper) app.getMap();
            layer = (TrackLayer) MapContentProviderHelper.getVectorLayerByPath(map, TrackLayer.TABLE_TRACKS);
            assertNotNull(layer);
            db = map.getDatabase(false);
            assertEquals(0, DatabaseUtils.longForQuery(db, "SELECT count(*) FROM tracks WHERE end IS NULL OR end = ''", null));
            PendingTrackPoints queue = new PendingTrackPoints(map.getPath());
            assertEquals(0, queue.size());
            Uri tracks = Uri.parse("content://" + app.getAuthority() + "/tracks");
            ContentValues track = new ContentValues();
            track.put(TrackLayer.FIELD_NAME, "Stop recovery test");
            track.put(TrackLayer.FIELD_START, System.currentTimeMillis());
            track.put(TrackLayer.FIELD_VISIBLE, 0);
            trackUri = layer.insert(tracks, track);
            long id = ContentUris.parseId(trackUri);
            for (int index = 0; index < 2; index++) {
                ContentValues point = new ContentValues();
                point.put(TrackLayer.FIELD_SESSION, id);
                point.put(TrackLayer.FIELD_TIMESTAMP, 1000L + index);
                point.put(TrackLayer.FIELD_SEGMENT, index);
                point.put(TrackLayer.FIELD_LON, index + .125);
                point.put(TrackLayer.FIELD_LAT, -index - .25);
                point.put(TrackLayer.FIELD_SENT, 0);
                queue.append(point);
            }
            db.execSQL("CREATE TRIGGER " + trigger + " BEFORE INSERT ON trackpoints WHEN NEW.session = "
                    + id + " BEGIN SELECT RAISE(ABORT, 'injected full storage'); END");
            assertTrue(temporary.edit().putString("track_uri", trackUri.toString())
                    .putString("track_map", map.getPath().getAbsolutePath()).commit());
            TrackerService.setTrackRecordingEnabled(app, true);
            scenario.onActivity(TrackerService::ensureRecordingRunningIfEnabled);
            AtomicBoolean failed = new AtomicBoolean();
            await(failed, 15000, () -> failed.set(TrackerService.getRecordingState(app) == TrackerService.RecordingState.ERROR));
            scenario.onActivity(TrackerService::start_stop_tracking_GetIconWithTitle);
            AtomicBoolean stopping = new AtomicBoolean();
            await(stopping, 15000, () -> stopping.set(temporary.contains("pending_stop")));
            assertTrue(TrackerService.isTrackRecordingEnabled(app));
            assertEquals(2, new PendingTrackPoints(map.getPath()).size());
            assertEquals(0, DatabaseUtils.longForQuery(db, "SELECT count(*) FROM trackpoints WHERE session = ?", new String[] { Long.toString(id) }));
            assertEquals(1, DatabaseUtils.longForQuery(db, "SELECT count(*) FROM tracks WHERE _id = ? AND end IS NULL", new String[] { Long.toString(id) }));
            db.execSQL("DROP TRIGGER " + trigger);
            AtomicBoolean stopped = new AtomicBoolean();
            await(stopped, 20000, () -> stopped.set(TrackerService.getRecordingState(app) == TrackerService.RecordingState.STOPPED));
            assertEquals(0, new PendingTrackPoints(map.getPath()).size());
            assertFalse(temporary.contains("pending_stop"));
            assertFalse(temporary.contains("track_uri"));
            try (Cursor points = db.rawQuery("SELECT time, segment FROM trackpoints WHERE session = ? ORDER BY rowid", new String[] { Long.toString(id) })) {
                assertEquals(2, points.getCount());
                for (int index = 0; index < 2; index++) {
                    assertTrue(points.moveToNext());
                    assertEquals(1000L + index, points.getLong(0));
                    assertEquals(index, points.getInt(1));
                }
            }
            assertEquals(1, DatabaseUtils.longForQuery(db, "SELECT count(*) FROM tracks WHERE _id = ? AND end > 0", new String[] { Long.toString(id) }));
        } finally {
            if (db != null) db.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            // Give a failed assertion the same safe Stop/retry cleanup; never delete a queued tail.
            if (TrackerService.isTrackRecordingEnabled(app)) {
                try (ActivityScenario<MainActivity> cleanup = ActivityScenario.launch(MainActivity.class)) {
                    cleanup.onActivity(TrackerService::start_stop_tracking_GetIconWithTitle);
                    AtomicBoolean stopped = new AtomicBoolean();
                    await(stopped, 20000, () -> stopped.set(TrackerService.getRecordingState(app) == TrackerService.RecordingState.STOPPED));
                }
            }
            if (layer != null && trackUri != null) layer.delete(Uri.parse("content://"
                    + app.getAuthority() + "/tracks"), TrackLayer.FIELD_ID + " IN (?)",
                    new String[] { trackUri.getLastPathSegment() });
            SharedPreferences.Editor restore = preferences.edit();
            if (hadSend) restore.putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, oldSend); else restore.remove(SettingsConstants.KEY_PREF_TRACK_SEND);
            if (hadIntro) restore.putBoolean(AppSettingsConstants.KEY_PREF_INTRO, oldIntro); else restore.remove(AppSettingsConstants.KEY_PREF_INTRO);
            if (hadFlag) restore.putBoolean(SettingsConstants.KEY_PREF_TRACK_RECORDING_ENABLED, false); else restore.remove(SettingsConstants.KEY_PREF_TRACK_RECORDING_ENABLED);
            if (hadFailure) restore.putBoolean("track_recording_failure", oldFailure); else restore.remove("track_recording_failure");
            assertTrue(restore.commit());
        }
    }
}
