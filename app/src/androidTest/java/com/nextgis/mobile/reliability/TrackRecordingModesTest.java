package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.gnss.GnssInputPrefs;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.maplibui.util.TrackRecordingMode;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Injects the validated stream into the real foreground service and checks its SQLite output. */
@RunWith(AndroidJUnit4.class)
public class TrackRecordingModesTest {
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadline) Thread.sleep(100);
        assertTrue("Recorder did not reach expected state", condition.getAsBoolean());
    }

    @Test public void pedestrianSkipsCarsAndResumesWithPersistedGap() throws Exception {
        verifyRecording(TrackRecordingMode.PEDESTRIAN, false, false, 4, 2);
    }

    @Test public void missingSpeedUsesSkippedFixesForResume() throws Exception {
        verifyRecording(TrackRecordingMode.PEDESTRIAN, true, false, 3, 2);
    }

    @Test public void stopDuringDriveCannotFlushRejectedPoints() throws Exception {
        verifyRecording(TrackRecordingMode.PEDESTRIAN, false, true, 2, 1);
    }

    @Test public void mixedModeKeepsDrivingPoints() throws Exception {
        verifyRecording(TrackRecordingMode.WALK_AND_DRIVE, false, false, 6, 1);
    }

    private void verifyRecording(TrackRecordingMode mode, boolean missingSpeed,
                                 boolean stopDriving, int expectedPoints, int expectedSegments)
            throws Exception {
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        SharedPreferences temp = app.getSharedPreferences(TrackerService.TEMP_PREFERENCES, Context.MODE_PRIVATE);
        assertFalse("Use an isolated emulator without user tracks", TrackerService.hasRecordingSession(app));
        assertFalse(temp.contains("track_uri"));
        String[] keys = {AppSettingsConstants.KEY_PREF_INTRO, SettingsConstants.KEY_PREF_TRACKS_MIN_TIME,
                SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE, TrackerService.KEY_RECORDING_MODE,
                SettingsConstants.KEY_PREF_TRACK_RECORDING_ENABLED, "track_recording_failure",
                SettingsConstants.KEY_PREF_GNSS_INPUT, SettingsConstants.KEY_PREF_GNSS_TRANSPORT,
                SettingsConstants.KEY_PREF_GNSS_DEVICE_ID};
        Map<String, ?> original = prefs.getAll();
        assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                .putString(SettingsConstants.KEY_PREF_TRACKS_MIN_TIME, "0")
                .putString(SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE, "0").commit());
        Uri trackUri = null;
        TrackLayer layer = null;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            await(() -> app.getMap() != null);
            MapContentProviderHelper map = (MapContentProviderHelper) app.getMap();
            layer = (TrackLayer) MapContentProviderHelper.getVectorLayerByPath(map, TrackLayer.TABLE_TRACKS);
            SQLiteDatabase db = map.getDatabase(false);
            assertNotNull(layer);
            scenario.onActivity(activity -> {
                // A receiver without an endpoint stops system/provider traffic before Start.
                // Otherwise a slow UI dispatch can let emulator fixes enter the sampler and
                // advance its clock before this test injects its supposedly first fix.
                assertTrue(prefs.edit()
                        .putString(SettingsConstants.KEY_PREF_GNSS_INPUT, GnssInputPrefs.VALUE_EXTERNAL)
                        .putString(SettingsConstants.KEY_PREF_GNSS_TRANSPORT, GnssInputPrefs.TRANSPORT_USB)
                        .putString(SettingsConstants.KEY_PREF_GNSS_DEVICE_ID, "").commit());
                assertTrue(TrackerService.selectRecordingMode(activity, mode));
                TrackerService.start_stop_tracking_GetIconWithTitle(activity);
            });
            await(TrackerService::isTrackRecordingActive);
            assertEquals(mode, TrackerService.getRecordingMode(app));
            assertFalse("Cannot change an active session", TrackerService.selectRecordingMode(app,
                    mode == TrackRecordingMode.PEDESTRIAN ? TrackRecordingMode.WALK_AND_DRIVE
                            : TrackRecordingMode.PEDESTRIAN));
            trackUri = Uri.parse(temp.getString("track_uri", ""));
            String trackId = trackUri.getLastPathSegment();
            assertNotNull(trackId);
            Field serviceField = TrackerService.class.getDeclaredField("sRecordingService");
            serviceField.setAccessible(true);
            TrackerService service = (TrackerService) serviceField.get(null);
            assertNotNull(service);
            double[] offsets = {0, .00001, .00021, .00041, .00042, .00043};
            float[] speeds = {1, 1, 22, 22, 1, 1};
            scenario.onActivity(activity -> {
                // Isolate service persistence from emulator/provider traffic. The upstream
                // GNSS validator is tested separately; these represent its ordered output.
                app.getGpsEventSource().removeRecordingListener(service);
                long firstNanos = SystemClock.elapsedRealtimeNanos();
                long firstTime = System.currentTimeMillis();
                for (int i = 0; i < (stopDriving ? 4 : offsets.length); i++) {
                    Location location = new Location("gps");
                    location.setLatitude(55 + offsets[i]);
                    location.setLongitude(37);
                    location.setAccuracy(1);
                    location.setTime(firstTime + i * 1000);
                    location.setElapsedRealtimeNanos(firstNanos + i * 1_000_000_000L);
                    if (!missingSpeed) location.setSpeed(speeds[i]);
                    service.onRecordingLocation(location);
                }
                TrackerService.start_stop_tracking_GetIconWithTitle(activity);
            });
            await(() -> !TrackerService.hasRecordingSession(app));
            try (Cursor points = db.rawQuery("SELECT speed, segment FROM trackpoints WHERE session = ? ORDER BY rowid",
                    new String[]{trackId})) {
                assertEquals(expectedPoints, points.getCount());
                int segments = 0, previousSegment = -1, fastPoints = 0;
                while (points.moveToNext()) {
                    if (points.getFloat(0) > 30f / 3.6f) fastPoints++;
                    if (points.getInt(1) != previousSegment) {
                        segments++;
                        previousSegment = points.getInt(1);
                    }
                }
                assertEquals(expectedSegments, segments);
                assertEquals(mode == TrackRecordingMode.PEDESTRIAN ? 0 : 2, fastPoints);
            }
            try (Cursor closed = db.rawQuery("SELECT end FROM tracks WHERE _id = ?", new String[]{trackId})) {
                assertTrue(closed.moveToFirst());
                assertTrue(closed.getLong(0) > 0);
            }
            assertEquals(mode, TrackerService.getRecordingMode(app));
        } finally {
            if (TrackerService.hasRecordingSession(app)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(
                        () -> TrackerService.start_stop_tracking_GetIconWithTitle(app));
                await(() -> !TrackerService.hasRecordingSession(app));
            }
            if (layer != null && trackUri != null) layer.delete(Uri.parse("content://"
                    + app.getAuthority() + "/tracks"), TrackLayer.FIELD_ID + " IN (?)",
                    new String[]{trackUri.getLastPathSegment()});
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : keys) {
                Object value = original.get(key);
                if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof String) restore.putString(key, (String) value);
                else restore.remove(key);
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> assertTrue(restore.commit()));
        }
    }
}
