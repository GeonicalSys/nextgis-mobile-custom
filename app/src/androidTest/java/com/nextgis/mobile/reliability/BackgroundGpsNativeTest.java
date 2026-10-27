package com.nextgis.mobile.reliability;

import android.app.NotificationManager;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.preference.PreferenceManager;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.maplibui.service.WalkEditService;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.maplibui.util.SharedUnderlayProjects;
import com.nextgis.maplibui.util.WalkSessionPolicy;
import com.nextgis.maplibui.util.WalkSessionStore;
import com.nextgis.mobile.MainApplication;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.activity.SettingsActivity;
import com.nextgis.mobile.location.BackgroundLocationPolicy;
import com.nextgis.mobile.util.AppSettingsConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Real service/Activity lifecycle on an isolated emulator; no track, server or physical device. */
@RunWith(AndroidJUnit4.class)
public class BackgroundGpsNativeTest {
    private MainApplication app;
    private SharedPreferences prefs;
    private final Map<String, Object> saved = new HashMap<>();
    private ActivityScenario<MainActivity> scenario;
    private MainActivity monitoredActivity;
    private GpsEventSource.RecordingListener recorder;
    private final String[] keys = {AppSettingsConstants.KEY_PREF_INTRO, "battery_dont_show_pref",
            "show_geo_dialog", SettingsConstants.KEY_PREF_GNSS_INPUT, BackgroundLocationPolicy.PREFERENCE};

    @Before public void setUp() throws Exception {
        app = (MainApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        assertTrue(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
        assertTrue(app.getPackageName().endsWith(".debug"));
        prefs = PreferenceManager.getDefaultSharedPreferences(app);
        for (String key : keys) saved.put(key, prefs.getAll().get(key));
        prefs.edit().putBoolean(keys[0], true).putBoolean(keys[1], true).putBoolean(keys[2], false)
                .putString(keys[3], "system").putBoolean(keys[4], true).commit();
        launchMainActivity();
        await(() -> running());
    }

    @After public void tearDown() {
        if (prefs == null) return;
        if (recorder != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                app.getGpsEventSource().removeRecordingListener(recorder));
        prefs.edit().putBoolean(BackgroundLocationPolicy.PREFERENCE, false).commit();
        if (scenario != null) scenario.close();
        monitoredActivity = null;
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : keys) {
            Object value = saved.get(key);
            if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof String) edit.putString(key, (String) value);
            else edit.remove(key);
        }
        edit.commit();
    }

    @Test public void mapBackgroundAndFormDoNotRestartGps() throws Exception {
        long[] before = counts();
        scenario.moveToState(Lifecycle.State.CREATED);
        assertTrue(running());
        assertEquals(before[1], counts()[1]);
        scenario.moveToState(Lifecycle.State.RESUMED);
        scenario.onActivity(activity -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(running());
        assertEquals(before[0], counts()[0]);
        assertEquals(before[1], counts()[1]);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("input keyevent 4").close();
    }

    @Test public void screenOffKeepsSameGpsSubscriptionAndNotification() throws Exception {
        long[] before = counts();
        PowerManager power = app.getSystemService(PowerManager.class);
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("input keyevent 223").close();
            await(() -> !power.isInteractive());
            assertTrue(running());
            assertEquals(before[0], counts()[0]);
            assertEquals(before[1], counts()[1]);
            assertTrue(app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=true"));
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("input keyevent 224").close();
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("wm dismiss-keyguard").close();
            await(() -> power.isInteractive());
        }
    }

    @Test public void twoHourExpiryStopsOnlyAutomaticOwnerAndResumeStartsItAgain() throws Exception {
        scenario.moveToState(Lifecycle.State.CREATED);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                Field field = MainApplication.class.getDeclaredField("backgroundLocationController");
                field.setAccessible(true);
                Object controller = field.get(app);
                Field policyField = controller.getClass().getDeclaredField("policy");
                policyField.setAccessible(true);
                BackgroundLocationPolicy policy = (BackgroundLocationPolicy) policyField.get(controller);
                long now = SystemClock.elapsedRealtime();
                policy.foreground(now); policy.background(now);
                Method reconcile = controller.getClass().getDeclaredMethod("reconcile", long.class);
                reconcile.setAccessible(true);
                reconcile.invoke(controller, now + BackgroundLocationPolicy.INACTIVITY_TIMEOUT_MS);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        await(() -> !running());
        assertTrue(app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=false"));
        scenario.moveToState(Lifecycle.State.RESUMED);
        await(() -> running());
    }

    @Test public void settingTakesEffectImmediatelyAndPreservesRecordingOwner() throws Exception {
        recorder = location -> { };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.getGpsEventSource().addRecordingListener(recorder));
        long stops = counts()[1];
        prefs.edit().putBoolean(BackgroundLocationPolicy.PREFERENCE, false).commit();
        await(() -> !running());
        scenario.moveToState(Lifecycle.State.CREATED);
        assertEquals(stops, counts()[1]);
        assertTrue(app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=true"));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.getGpsEventSource().removeRecordingListener(recorder));
        prefs.edit().putBoolean(BackgroundLocationPolicy.PREFERENCE, true).commit();
        scenario.moveToState(Lifecycle.State.RESUMED);
        await(() -> running());
    }

    @Test public void foregroundFormsKeepGpsEvenWithBackgroundSettingDisabled() throws Exception {
        prefs.edit().putBoolean(BackgroundLocationPolicy.PREFERENCE, false).commit();
        await(() -> !running());
        long[] before = counts();
        scenario.onActivity(activity -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse(running());
        assertEquals(before[0], counts()[0]);
        assertEquals(before[1], counts()[1]);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("input keyevent 4").close();
    }

    @Test public void homeKeepsGpsButDoubleBackClosesItUntilReopen() throws Exception {
        long[] before = counts();
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
        await(() -> activityStage() == Stage.STOPPED);
        assertTrue(running());
        assertEquals(before[1], counts()[1]);
        // After a real Home the task must regain focus, not merely request a lifecycle state.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (ActivityManager.AppTask task : app.getSystemService(ActivityManager.class).getAppTasks())
                task.moveToFront();
        });
        await(() -> activityStage() == Stage.RESUMED);
        doubleBack();
        await(() -> !running() && activityStage() == Stage.DESTROYED);
        await(() -> app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=false"));
        assertTrue(counts()[1] > before[1]);
        // A queued preference reconciliation must not restart GPS after explicit exit.
        prefs.edit().putBoolean(BackgroundLocationPolicy.PREFERENCE, false).commit();
        prefs.edit().putBoolean(BackgroundLocationPolicy.PREFERENCE, true).commit();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse(running());
        scenario.close();
        launchMainActivity();
        await(() -> running());
    }

    @Test public void removingRecentTaskStopsAutomaticGps() throws Exception {
        long stops = counts()[1];
        removeRecentTask();
        await(() -> !running());
        await(() -> app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=false"));
        assertTrue(counts()[1] > stops);
    }

    @Test public void doubleBackPreservesAnIndependentRecordingSubscription() throws Exception {
        addRecorder();
        long stops = counts()[1];
        doubleBack();
        await(() -> !running() && activityStage() == Stage.DESTROYED);
        assertEquals(stops, counts()[1]);
        assertTrue(app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=true"));
        removeRecorder();
        await(() -> app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=false"));
    }

    @Test public void removingRecentTaskPreservesAnIndependentRecordingSubscription() throws Exception {
        addRecorder();
        long stops = counts()[1];
        removeRecentTask();
        await(() -> !running());
        assertEquals(stops, counts()[1]);
        assertTrue(app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=true"));
        removeRecorder();
        await(() -> app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=false"));
    }

    @Test public void doubleBackKeepsTheActualTrackServiceRecording() throws Exception {
        assertFalse(TrackerService.hasRecordingSession(app));
        try {
            scenario.onActivity(activity -> TrackerService.start_stop_tracking_GetIconWithTitle(activity));
            await(TrackerService::isTrackRecordingActive);
            doubleBack();
            await(() -> !running() && activityStage() == Stage.DESTROYED);
            assertTrue(TrackerService.isTrackRecordingActive());
            assertTrue(TrackerService.hasRecordingSession(app));
            assertTrue(app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=true"));
        } finally {
            if (TrackerService.hasRecordingSession(app)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                        TrackerService.start_stop_tracking_GetIconWithTitle(app));
                await(() -> !TrackerService.hasRecordingSession(app));
            }
        }
    }

    @Test public void removingRecentTaskKeepsTheActualWalkServiceRecording() throws Exception {
        await(() -> !SharedUnderlayProjects.isMigrationPending() && !ProjectOperationCoordinator.isBusy());
        assertNull(WalkSessionStore.load(app));
        String[] session = {null};
        try {
            scenario.onActivity(activity -> {
                GeoLineString line = new GeoLineString();
                line.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                line.add(new GeoPoint(0, 0));
                session[0] = WalkSessionStore.begin(app, 900001, Constants.NOT_FOUND,
                        line, 0, 0, 1, MainActivity.class.getName());
                assertNotNull(session[0]);
                Intent start = new Intent(app, WalkEditService.class).setAction(WalkEditService.ACTION_START)
                        .putExtra(WalkSessionStore.KEY_SESSION, session[0])
                        .putExtra(ConstantsUI.KEY_LAYER_ID, 900001)
                        .putExtra(ConstantsUI.KEY_FEATURE_ID, (long) Constants.NOT_FOUND)
                        .putExtra(ConstantsUI.KEY_GEOMETRY, line)
                        .putExtra(WalkEditService.KEY_INSERT_INDEX, 1)
                        .putExtra(ConstantsUI.TARGET_CLASS, MainActivity.class.getName())
                        .putExtra(ConstantsUI.KEY_MESSAGE, true);
                androidx.core.content.ContextCompat.startForegroundService(activity, start);
            });
            await(() -> WalkEditService.isSessionRunning(session[0]));
            removeRecentTask();
            await(() -> !running());
            assertTrue(WalkEditService.isSessionRunning(session[0]));
            assertNotNull(WalkSessionStore.load(app));
            assertFalse(WalkSessionStore.load(app).gpsPaused);
            assertTrue(app.getGpsEventSource().getRecordingDiagnostics().contains("wakeLock=true"));
        } finally {
            if (session[0] != null && WalkSessionStore.load(app) != null) {
                WalkEditService.requestCommand(app, session[0], WalkSessionPolicy.Command.DISCARD);
                await(() -> !WalkEditService.isSessionRunning(session[0]));
                WalkSessionStore.clear(app, session[0]);
            }
        }
    }

    private void addRecorder() {
        recorder = location -> { };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                app.getGpsEventSource().addRecordingListener(recorder));
    }

    private void removeRecorder() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                app.getGpsEventSource().removeRecordingListener(recorder));
        recorder = null;
    }

    private void doubleBack() {
        scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
        assertEquals("First Back only asks for confirmation", Stage.RESUMED, activityStage());
        assertTrue(running());
        scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
    }

    private void removeRecentTask() throws Exception {
        int[] taskId = {-1};
        scenario.onActivity(activity -> taskId[0] = activity.getTaskId());
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
        await(() -> activityStage() == Stage.STOPPED);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            boolean removed = false;
            for (ActivityManager.AppTask task : app.getSystemService(ActivityManager.class).getAppTasks()) {
                if (task.getTaskInfo().taskId == taskId[0]) {
                    task.finishAndRemoveTask();
                    removed = true;
                }
            }
            assertTrue("Remove the app's actual recent task", removed);
        });
        await(() -> activityStage() == Stage.DESTROYED);
    }

    private void launchMainActivity() {
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> monitoredActivity = activity);
    }

    private Stage activityStage() {
        // ActivityScenario.getState cannot represent an in-flight RESTARTED stage.
        // Observe the real Activity on the main thread without forcing a transition.
        Stage[] current = new Stage[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> current[0] =
                ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(monitoredActivity));
        return current[0];
    }

    private boolean running() {
        for (android.service.notification.StatusBarNotification n : app.getSystemService(NotificationManager.class).getActiveNotifications())
            if (n.getId() == 18_406) return true;
        return false;
    }
    private long[] counts() {
        long[] result = new long[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                Field requests = GpsEventSource.class.getDeclaredField("gpsRequests");
                Field stops = GpsEventSource.class.getDeclaredField("gpsStops");
                requests.setAccessible(true); stops.setAccessible(true);
                result[0] = requests.getLong(app.getGpsEventSource()); result[1] = stops.getLong(app.getGpsEventSource());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        return result;
    }
    private interface Condition { boolean ready(); }
    private void await(Condition condition) throws Exception {
        long until = SystemClock.elapsedRealtime() + 20_000;
        while (SystemClock.elapsedRealtime() < until) {
            if (condition.ready()) return;
            Thread.sleep(100);
        }
        fail("Service state did not settle");
    }
}
