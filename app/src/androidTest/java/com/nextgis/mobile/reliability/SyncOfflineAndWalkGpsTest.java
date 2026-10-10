package com.nextgis.mobile.reliability;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.inspector.WindowInspector;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.service.NGWSyncService;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.SyncAccountWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.nextgis.maplibui.service.WalkEditService;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.maplibui.util.WalkSessionPolicy;
import com.nextgis.maplibui.util.WalkSessionStore;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.datasource.SyncAdapter;
import com.nextgis.mobile.fragment.LayersFragment;
import com.nextgis.mobile.util.AppSettingsConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Real UI and recorder callbacks on an isolated Debug emulator, without NGW accounts. */
@RunWith(AndroidJUnit4.class)
public class SyncOfflineAndWalkGpsTest {
    private GISApplication app;
    private ActivityScenario<MainActivity> scenario;
    private SharedPreferences preferences;
    private Map<String, ?> savedPreferences;
    private String sessionId;
    private int wifi, mobileData;
    private final String[] keys = {AppSettingsConstants.KEY_PREF_INTRO, "battery_dont_show_pref",
            "show_geo_dialog", SettingsConstants.KEY_PREF_GNSS_INPUT,
            SettingsConstants.KEY_PREF_LOCATION_MIN_TIME, SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE};

    @Before public void setUp() throws Exception {
        assertTrue("Isolated emulator only", Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
        app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        assertTrue(app.getPackageName().endsWith(".debug"));
        assertNull("No existing walk owner", WalkSessionStore.load(app));
        preferences = PreferenceManager.getDefaultSharedPreferences(app);
        savedPreferences = preferences.getAll();
        wifi = Settings.Global.getInt(app.getContentResolver(), Settings.Global.WIFI_ON, 0);
        mobileData = Settings.Global.getInt(app.getContentResolver(), "mobile_data", 0);
        shell("svc wifi disable"); shell("svc data disable");
        await(() -> !new NetworkUtil(app).isNetworkAvailable());
        preferences.edit().putBoolean(keys[0], true).putBoolean(keys[1], true)
                .putBoolean(keys[2], false).putString(keys[3], "system")
                .putString(keys[4], "0").putString(keys[5], "0").commit();
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After public void tearDown() throws Exception {
        if (app == null) return;
        if (sessionId != null) {
            WalkSessionStore.Snapshot current = WalkSessionStore.load(app);
            if (current != null && current.id.equals(sessionId)) {
                if (current.isPointActive()) WalkSessionStore.endPoint(app, current.pointId);
                WalkEditService.requestCommand(app, sessionId, WalkSessionPolicy.Command.DISCARD);
                await(() -> !WalkEditService.isSessionRunning(sessionId));
                WalkSessionStore.clear(app, sessionId);
            }
        }
        if (scenario != null) scenario.close();
        app.getSystemService(NotificationManager.class).cancel(518);
        if (savedPreferences != null) {
            SharedPreferences.Editor edit = preferences.edit();
            for (String key : keys) {
                Object value = savedPreferences.get(key);
                if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof String) edit.putString(key, (String) value);
                else edit.remove(key);
            }
            edit.commit();
        }
        if (wifi != 0) shell("svc wifi enable");
        if (mobileData != 0) shell("svc data enable");
    }

    @Test public void offlineManualTapAndRetryExplainInternetAndDoNotStartQueue() throws Exception {
        long lastSync = preferences.getLong(SettingsConstants.KEY_PREF_LAST_SYNC_TIMESTAMP, 0);
        scenario.onActivity(activity -> {
            try {
                LayersFragment layers = (LayersFragment) activity.getSupportFragmentManager().findFragmentById(R.id.layers);
                Method start = LayersFragment.class.getDeclaredMethod("startManualSync", boolean.class);
                start.setAccessible(true);
                start.invoke(layers, false);
                View dialog = dialogWithInternetMessage();
                dialog.findViewById(android.R.id.button1).performClick();
                dialogWithInternetMessage().findViewById(android.R.id.button2).performClick();
                start.invoke(layers, true);
                dialogWithInternetMessage().findViewById(android.R.id.button2).performClick();
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        assertFalse(NGWSyncService.isSyncStarted());
        assertFalse(ProjectOperationCoordinator.isDataSyncActive());
        assertEquals(lastSync, preferences.getLong(SettingsConstants.KEY_PREF_LAST_SYNC_TIMESTAMP, 0));
        new SyncAdapter(app, true).sendNotification(app, SyncAdapter.SYNC_CHANGES, "synthetic socket failure");
        await(() -> errorNotification() != null);
        assertEquals(app.getString(R.string.sync_no_internet),
                errorNotification().extras.getString(Notification.EXTRA_TEXT));
    }

    @Test public void scheduledAccountWorkWaitsForNetworkWithoutStartingSync() throws Exception {
        String account = "isolated-network-" + java.util.UUID.randomUUID();
        String name = "ngw-account-sync-" + Integer.toHexString(account.hashCode());
        WorkManager work = WorkManager.getInstance(app);
        try {
            SyncAccountWorker.scheduleSoon(app, account, 60);
            SystemClock.sleep(2000);
            java.util.List<WorkInfo> pending = work.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS);
            assertEquals(1, pending.size());
            assertEquals(WorkInfo.State.ENQUEUED, pending.get(0).getState());
            assertEquals(0, pending.get(0).getRunAttemptCount());
            assertFalse(NGWSyncService.isSyncStarted());
            assertFalse(ProjectOperationCoordinator.isDataSyncActive());
            assertNull(errorNotification());
            shell("svc wifi enable");
            await(() -> new NetworkUtil(app).isNetworkAvailable());
            long deadline = SystemClock.elapsedRealtime() + 20000;
            while (work.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).get(0).getState() != WorkInfo.State.SUCCEEDED
                    && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
            // The deliberately absent account retires only after network permits the worker.
            assertEquals(WorkInfo.State.SUCCEEDED, work.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).get(0).getState());
        } finally { work.cancelUniqueWork(name).getResult().get(5, TimeUnit.SECONDS); }
    }

    @Test public void gpsLossKeepsOwnerAndPointLockAndAutomaticallyAcceptsNextFix() throws Exception {
        beginWalk();
        scenario.onActivity(activity -> {
            WalkEditService service = service();
            service.onRecordingLocation(fix(37.001, SystemClock.elapsedRealtimeNanos()));
            int count = count();
            String point = WalkSessionStore.beginPoint(app, R.id.add_current_location);
            assertNotNull(point);
            service.onRecordingUnavailable(); service.onRecordingUnavailable();
            assertFalse(WalkSessionStore.load(app).gpsPaused);
            assertEquals(point, WalkSessionStore.load(app).pointId);
            assertEquals(count, count());
            service.onRecordingLocation(fix(37.002, SystemClock.elapsedRealtimeNanos() + 1_000_000));
            assertEquals(count + 1, count());
            assertFalse(WalkSessionStore.load(app).gpsPaused);
            assertTrue(WalkSessionStore.endPoint(app, point));
        });
        app.stopService(new Intent(app, WalkEditService.class));
        await(() -> !WalkEditService.isSessionRunning(sessionId));
        assertFalse("Unexpected service end keeps the recording intent", WalkSessionStore.load(app).gpsPaused);
    }

    @Test public void timestampGapDoesNotDropFirstFreshFixOrReplayDuplicates() throws Exception {
        beginWalk();
        scenario.onActivity(activity -> {
            WalkEditService service = service();
            long now = SystemClock.elapsedRealtimeNanos();
            service.onRecordingLocation(fix(37.001, now - 10_000_000_000L));
            int count = count();
            Location next = fix(37.002, now);
            service.onRecordingLocation(next);
            assertEquals(count + 1, count());
            assertFalse(WalkSessionStore.load(app).gpsPaused);
            service.onRecordingLocation(next);
            assertEquals(count + 1, count());
        });
    }

    @Test public void stickyRecoveryPreservesRecordingAndExplicitPauseStillBlocksFixes() throws Exception {
        beginWalk();
        scenario.onActivity(activity -> {
            WalkEditService service = service();
            service.onRecordingLocation(fix(37.001, SystemClock.elapsedRealtimeNanos()));
            service.onStartCommand(null, 0, 2);
            assertFalse("Sticky recovery does not create a pause", WalkSessionStore.load(app).gpsPaused);
            Intent pause = new Intent(app, WalkEditService.class).setAction(WalkEditService.ACTION_PAUSE_GPS)
                    .putExtra(WalkSessionStore.KEY_SESSION, sessionId);
            service.onStartCommand(pause, 0, 3);
            int count = count();
            service.onRecordingUnavailable();
            service.onStartCommand(null, 0, 4);
            service.onRecordingLocation(fix(37.003, SystemClock.elapsedRealtimeNanos() + 1_000_000));
            assertTrue(WalkSessionStore.load(app).gpsPaused);
            assertEquals(count, count());
        });
    }

    private void beginWalk() throws Exception {
        scenario.onActivity(activity -> {
            GeoLineString line = new GeoLineString(); line.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            GeoPoint origin = new GeoPoint(37, 55); origin.setCRS(GeoConstants.CRS_WGS84);
            origin.project(GeoConstants.CRS_WEB_MERCATOR); line.add(origin);
            sessionId = WalkSessionStore.begin(app, 900001, Constants.NOT_FOUND, line, 0, 0, 1,
                    MainActivity.class.getName());
            assertNotNull(sessionId);
            Intent start = new Intent(app, WalkEditService.class).setAction(WalkEditService.ACTION_START)
                    .putExtra(WalkSessionStore.KEY_SESSION, sessionId)
                    .putExtra(ConstantsUI.KEY_LAYER_ID, 900001).putExtra(ConstantsUI.KEY_FEATURE_ID, (long) Constants.NOT_FOUND)
                    .putExtra(ConstantsUI.KEY_GEOMETRY, line).putExtra(WalkEditService.KEY_INSERT_INDEX, 1)
                    .putExtra(ConstantsUI.TARGET_CLASS, MainActivity.class.getName()).putExtra(ConstantsUI.KEY_MESSAGE, true);
            androidx.core.content.ContextCompat.startForegroundService(activity, start);
        });
        await(() -> WalkEditService.isSessionRunning(sessionId));
    }

    private WalkEditService service() {
        try { Field active = WalkEditService.class.getDeclaredField("sActive"); active.setAccessible(true);
            WalkEditService service = (WalkEditService) active.get(null); assertNotNull(service); return service;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private int count() { return ((GeoLineString) WalkSessionStore.load(app).geometry()).getPointCount(); }
    private static Location fix(double longitude, long nanos) {
        Location fix = new Location("gps"); fix.setLatitude(55); fix.setLongitude(longitude); fix.setAccuracy(1);
        fix.setTime(System.currentTimeMillis()); fix.setElapsedRealtimeNanos(nanos); return fix;
    }
    private View dialogWithInternetMessage() {
        View dialog = null;
        for (View root : WindowInspector.getGlobalWindowViews()) {
            TextView message = root.findViewById(android.R.id.message);
            if (message != null && message.isShown() && app.getString(R.string.sync_no_internet).contentEquals(message.getText()))
                dialog = root;
        }
        if (dialog != null) return dialog;
        throw new AssertionError("Missing internet message in manual sync dialog");
    }
    private Notification errorNotification() {
        for (StatusBarNotification item : app.getSystemService(NotificationManager.class).getActiveNotifications())
            if (item.getId() == 518) return item.getNotification();
        return null;
    }
    private interface Condition { boolean matches(); }
    private static void await(Condition condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (!condition.matches() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(30);
        assertTrue("Timed out waiting for fixture state", condition.matches());
    }
    private static String shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
