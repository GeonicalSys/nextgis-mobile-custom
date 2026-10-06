package com.nextgis.mobile.reliability;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.location.LocationPowerPolicy;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Map;
import java.util.Scanner;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Exercises Android's provider delivery and real SQLite writes with the screen asleep. */
@RunWith(AndroidJUnit4.class)
public class TrackPowerWarningTest {
    private int fixIndex;

    private static String shell(String command) throws Exception {
        ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (Scanner reader = new Scanner(new ParcelFileDescriptor.AutoCloseInputStream(fd), "UTF-8")) {
            reader.useDelimiter("\\A");
            return reader.hasNext() ? reader.next().trim() : "";
        }
    }

    private static void await(String message, BooleanSupplier condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 20000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
        assertTrue(message, condition.getAsBoolean());
    }

    private void emit(LocationManager manager, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Location fix = new Location(LocationManager.GPS_PROVIDER);
            fix.setLatitude(55 + fixIndex++ * .00002);
            fix.setLongitude(37);
            fix.setAccuracy(2);
            fix.setSpeed(2);
            fix.setTime(System.currentTimeMillis());
            fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            android.os.Bundle extras = new android.os.Bundle();
            extras.putDouble("hdop", 1.0); // The recording contract requires receiver metadata for mock GPS.
            fix.setExtras(extras);
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, fix);
            Thread.sleep(1100);
        }
    }

    private static long points(SQLiteDatabase db, String id) {
        return DatabaseUtils.longForQuery(db, "SELECT count(*) FROM trackpoints WHERE session = ?",
                new String[]{id});
    }

    private static boolean notificationWarns(Context app) {
        String warning = app.getString(com.nextgis.maplibui.R.string.track_power_notification);
        for (StatusBarNotification item : app.getSystemService(NotificationManager.class).getActiveNotifications()) {
            if (item.getId() == 1) return warning.contentEquals(
                    item.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT, ""));
        }
        return false;
    }

    @Test public void warnsDuringScreenOffRestrictionAndResumesWithoutRestartingTrack() throws Exception {
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue("Run only on an isolated emulator", android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.HARDWARE.contains("goldfish"));
        assertFalse(TrackerService.hasRecordingSession(app));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        SharedPreferences temp = app.getSharedPreferences(TrackerService.TEMP_PREFERENCES, Context.MODE_PRIVATE);
        assertFalse(temp.contains("track_uri"));
        Map<String, ?> original = prefs.getAll();
        String[] keys = {AppSettingsConstants.KEY_PREF_INTRO, "battery_dont_show_pref",
                SettingsConstants.KEY_PREF_TRACKS_MIN_TIME, SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE,
                SettingsConstants.KEY_PREF_GNSS_INPUT, SettingsConstants.KEY_PREF_TRACK_RECORDING_ENABLED,
                "track_recording_failure"};
        String originalConstants = shell("settings get global battery_saver_constants");
        String originalMock = shell("appops get " + app.getPackageName() + " android:mock_location");
        assertTrue("Mock provider must not belong to another test", originalMock.contains("No operations")
                || originalMock.contains("default"));
        LocationManager manager = app.getSystemService(LocationManager.class);
        PowerManager power = app.getSystemService(PowerManager.class);
        Uri trackUri = null;
        TrackLayer layer = null;
        boolean providerAdded = false;
        try {
            shell("cmd battery unplug");
            shell("cmd power set-mode 0");
            shell("settings put global battery_saver_constants location_mode=1");
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
            shell("appops set " + app.getPackageName() + " android:mock_location allow");
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false,
                    true, true, true, android.location.Criteria.POWER_HIGH, android.location.Criteria.ACCURACY_FINE);
            providerAdded = true;
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                    .putBoolean("battery_dont_show_pref", true)
                    .putString(SettingsConstants.KEY_PREF_GNSS_INPUT, "system")
                    .putString(SettingsConstants.KEY_PREF_TRACKS_MIN_TIME, "0")
                    .putString(SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE, "0").commit());
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                await("Map ready", () -> app.getMap() != null);
                MapContentProviderHelper map = (MapContentProviderHelper) app.getMap();
                layer = (TrackLayer) MapContentProviderHelper.getVectorLayerByPath(map, TrackLayer.TABLE_TRACKS);
                SQLiteDatabase db = map.getDatabase(false);
                scenario.onActivity(TrackerService::start_stop_tracking_GetIconWithTitle);
                await("Recording started", TrackerService::isTrackRecordingActive);
                trackUri = Uri.parse(temp.getString("track_uri", ""));
                String id = trackUri.getLastPathSegment();
                emit(manager, 5);
                await("Foreground points persisted", () -> points(db, id) >= 2);
                long beforeSleep = points(db, id);
                shell("input keyevent KEYCODE_SLEEP");
                await("Screen asleep", () -> !power.isInteractive());
                emit(manager, 8);
                assertTrue("Screen-off points persist without Battery Saver", points(db, id) > beforeSleep + 2);
                long beforeSaver = points(db, id);
                shell("cmd power set-mode 1");
                await("Screen-off GPS policy enabled", () -> power.isPowerSaveMode()
                        && power.getLocationPowerSaveMode() == 1 && LocationPowerPolicy.shouldWarn(app));
                await("Background notification warns", () -> notificationWarns(app));
                emit(manager, 10);
                long restricted = points(db, id);
                // Only the already accepted filter tail may drain after provider delivery stops.
                assertTrue("Android suppresses new fixes while Battery Saver blocks GPS",
                        restricted <= beforeSaver + 2);
                assertTrue(TrackerService.isTrackRecordingActive());
                assertFalse(power.isInteractive());
                shell("cmd power set-mode 0");
                await("Warning clears automatically", () -> !notificationWarns(app)
                        && !LocationPowerPolicy.shouldWarn(app));
                emit(manager, 8);
                assertTrue("GPS resumes with the screen still off", points(db, id) > restricted + 2);
                assertEquals("Same recording survives", trackUri.toString(), temp.getString("track_uri", ""));
                Log.i("TrackPowerTest", "persisted foreground=" + beforeSleep + " screenOff=" + beforeSaver
                        + " saver=" + restricted + " resumed=" + points(db, id));
                shell("input keyevent KEYCODE_WAKEUP");
                shell("wm dismiss-keyguard");
            }
        } finally {
            shell("cmd power set-mode 0");
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
            if (TrackerService.hasRecordingSession(app)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(
                        () -> TrackerService.start_stop_tracking_GetIconWithTitle(app));
                await("Recording stopped", () -> !TrackerService.hasRecordingSession(app));
            }
            if (providerAdded) manager.removeTestProvider(LocationManager.GPS_PROVIDER);
            shell("appops set " + app.getPackageName() + " android:mock_location default");
            if ("null".equals(originalConstants)) shell("settings delete global battery_saver_constants");
            else shell("settings put global battery_saver_constants '" + originalConstants.replace("'", "'\\''") + "'");
            shell("cmd battery reset");
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
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertTrue(restore.commit()));
        }
    }

    @Test public void startWarningOffersSettingsOrExplicitContinuationEvenIfOldPromptWasHidden() throws Exception {
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertTrue(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        assertFalse(TrackerService.hasRecordingSession(app));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        Map<String, ?> original = prefs.getAll();
        String[] keys = {AppSettingsConstants.KEY_PREF_INTRO, "battery_dont_show_pref", SettingsConstants.KEY_PREF_GNSS_INPUT};
        String constants = shell("settings get global battery_saver_constants");
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                new android.content.IntentFilter(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS), null, true);
        try {
            shell("cmd battery unplug");
            shell("settings put global battery_saver_constants location_mode=1");
            shell("cmd power set-mode 1");
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
            assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                    .putBoolean("battery_dont_show_pref", true)
                    .putString(SettingsConstants.KEY_PREF_GNSS_INPUT, "system").commit());
            await("Battery Saver restriction detected", () -> LocationPowerPolicy.shouldWarn(app));
            java.util.concurrent.atomic.AtomicBoolean start = new java.util.concurrent.atomic.AtomicBoolean();
            java.lang.reflect.Field field = com.nextgis.mobile.location.TrackPowerWarning.class.getDeclaredField("dialog");
            field.setAccessible(true);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    com.nextgis.mobile.location.TrackPowerWarning warning = new com.nextgis.mobile.location.TrackPowerWarning(activity);
                    try {
                        warning.confirmStart(() -> { start.set(true); return kotlin.Unit.INSTANCE; });
                        assertFalse("A warning must precede Start", start.get());
                        android.app.AlertDialog dialog = (android.app.AlertDialog) field.get(warning);
                        assertNotNull(dialog);
                        assertTrue(dialog.isShowing());
                        assertEquals(app.getString(com.nextgis.maplibui.R.string.track_power_message),
                                ((android.widget.TextView) dialog.findViewById(android.R.id.message)).getText().toString());
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                        assertFalse("Settings must not start recording", start.get());
                    } catch (IllegalAccessException error) { throw new AssertionError(error); }
                    finally { warning.close(); }
                });
                instrumentation.waitForIdleSync();
                assertEquals(1, monitor.getHits());
                scenario.onActivity(activity -> {
                    com.nextgis.mobile.location.TrackPowerWarning warning = new com.nextgis.mobile.location.TrackPowerWarning(activity);
                    try {
                        warning.confirmStart(() -> { start.set(true); return kotlin.Unit.INSTANCE; });
                        android.app.AlertDialog dialog = (android.app.AlertDialog) field.get(warning);
                        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
                    } catch (IllegalAccessException error) { throw new AssertionError(error); }
                    finally { warning.close(); }
                });
                instrumentation.waitForIdleSync();
                assertTrue("Only explicit continuation invokes Start", start.get());
                assertFalse("UI fixture never starts the real service", TrackerService.hasRecordingSession(app));
            }
        } finally {
            instrumentation.removeMonitor(monitor);
            shell("cmd power set-mode 0");
            if ("null".equals(constants)) shell("settings delete global battery_saver_constants");
            else shell("settings put global battery_saver_constants '" + constants.replace("'", "'\\''") + "'");
            shell("cmd battery reset");
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : keys) {
                Object value = original.get(key);
                if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof String) restore.putString(key, (String) value);
                else restore.remove(key);
            }
            instrumentation.runOnMainSync(() -> assertTrue(restore.commit()));
        }
    }
}
