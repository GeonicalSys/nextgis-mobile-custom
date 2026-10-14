package com.nextgis.mobile.reliability;

import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.util.SettingsConstants;
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
        scenario = ActivityScenario.launch(MainActivity.class);
        await(() -> running());
    }

    @After public void tearDown() {
        if (prefs == null) return;
        if (recorder != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                app.getGpsEventSource().removeRecordingListener(recorder));
        prefs.edit().putBoolean(BackgroundLocationPolicy.PREFERENCE, false).commit();
        if (scenario != null) scenario.close();
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
