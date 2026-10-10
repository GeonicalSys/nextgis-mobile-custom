package com.nextgis.mobile.reliability;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.location.LocationPowerPolicy;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.location.BackgroundLocationWarning;
import com.nextgis.mobile.util.AppSettingsConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Actual Android power/app restrictions; changes only an isolated emulator fixture. */
@RunWith(AndroidJUnit4.class)
public class BackgroundLocationWarningTest {
    private static String shell(String command) throws Exception {
        ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try (Scanner reader = new Scanner(new ParcelFileDescriptor.AutoCloseInputStream(fd), "UTF-8")) {
            reader.useDelimiter("\\A");
            return reader.hasNext() ? reader.next().trim() : "";
        }
    }

    private static void await(String message, BooleanSupplier check) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 20_000;
        while (!check.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
        assertTrue(message, check.getAsBoolean());
    }

    private static void awaitMessage(ActivityScenario<MainActivity> scenario, int message) throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> last = new java.util.concurrent.atomic.AtomicReference<>();
        try { await("Warning displayed without recording", () -> {
            AtomicBoolean found = new AtomicBoolean();
            scenario.onActivity(activity -> {
                TextView view = activity.findViewById(com.google.android.material.R.id.snackbar_text);
                String barState;
                try {
                    Field delegate = MainActivity.class.getDeclaredField("backgroundLocationWarning$delegate");
                    delegate.setAccessible(true);
                    Object warning = ((kotlin.Lazy<?>) delegate.get(activity)).getValue();
                    Field field = BackgroundLocationWarning.class.getDeclaredField("snackbar"); field.setAccessible(true);
                    com.google.android.material.snackbar.Snackbar bar = (com.google.android.material.snackbar.Snackbar) field.get(warning);
                    barState = bar == null ? "null" : "queued=" + bar.isShownOrQueued() + ", parent=" + bar.getView().getParent();
                } catch (ReflectiveOperationException error) { barState = error.toString(); }
                last.set("focused=" + activity.hasWindowFocus() + ", text=" + (view == null ? "absent" : view.getText())
                        + ", visible=" + (view != null && view.isShown()) + ", bar=" + barState);
                found.set(view != null && view.isShown() && view.getText().toString().equals(activity.getString(message)));
            });
            return found.get();
        }); } catch (AssertionError error) { throw new AssertionError(error.getMessage() + ": " + last.get(), error); }
    }

    private static void chooseSettings(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            activity.findViewById(com.google.android.material.R.id.snackbar_action).performClick();
            try {
                Field delegate = MainActivity.class.getDeclaredField("backgroundLocationWarning$delegate");
                delegate.setAccessible(true);
                BackgroundLocationWarning warning = (BackgroundLocationWarning) ((kotlin.Lazy<?>) delegate.get(activity)).getValue();
                Field field = BackgroundLocationWarning.class.getDeclaredField("dialog");
                field.setAccessible(true);
                AlertDialog dialog = (AlertDialog) field.get(warning);
                assertNotNull(dialog);
                dialog.getListView().performItemClick(dialog.getListView().getChildAt(0), 0, 0);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }

    private static BackgroundLocationWarning warning(MainActivity activity) {
        try {
            Field delegate = MainActivity.class.getDeclaredField("backgroundLocationWarning$delegate");
            delegate.setAccessible(true);
            return (BackgroundLocationWarning) ((kotlin.Lazy<?>) delegate.get(activity)).getValue();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test public void launchAndResumeOfferRelevantSettingsWithoutStartingRecording() throws Exception {
        assertTrue(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.content.Context app = instrumentation.getTargetContext();
        assertTrue(app.getPackageName().endsWith(".debug"));
        assertFalse(TrackerService.hasRecordingSession(app));
        PowerManager power = app.getSystemService(PowerManager.class);
        boolean wasExempt = power.isIgnoringBatteryOptimizations(app.getPackageName());
        String constants = shell("settings get global battery_saver_constants");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        Map<String, ?> original = prefs.getAll();
        String[] keys = {AppSettingsConstants.KEY_PREF_INTRO, "battery_dont_show_pref", SettingsConstants.KEY_PREF_GNSS_INPUT,
                SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG};
        android.app.Instrumentation.ActivityMonitor saver = instrumentation.addMonitor(new IntentFilter(Settings.ACTION_BATTERY_SAVER_SETTINGS), null, true);
        IntentFilter restrictedFilter = new IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        restrictedFilter.addDataScheme("package");
        IntentFilter optimizedFilter = new IntentFilter(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        optimizedFilter.addDataScheme("package");
        android.app.Instrumentation.ActivityMonitor restricted = instrumentation.addMonitor(restrictedFilter, null, true);
        android.app.Instrumentation.ActivityMonitor optimized = instrumentation.addMonitor(optimizedFilter, null, true);
        try {
            assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                    .putBoolean("battery_dont_show_pref", true).putString(SettingsConstants.KEY_PREF_GNSS_INPUT, "system")
                    .putBoolean(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, false).commit());
            shell("cmd location set-location-enabled true");
            shell("appops set " + app.getPackageName() + " RUN_ANY_IN_BACKGROUND allow");
            shell("dumpsys deviceidle whitelist +" + app.getPackageName());
            shell("cmd battery set level 50");
            shell("cmd battery unplug");
            await("Battery unplug acknowledged", () -> { try { return shell("dumpsys power").contains("mIsPowered=false"); } catch (Exception e) { throw new AssertionError(e); } });
            shell("settings put global battery_saver_constants location_mode=1");
            await("Saver enabled", () -> { try { shell("cmd power set-mode 1"); return LocationPowerPolicy.shouldWarn(app); } catch (Exception e) { throw new AssertionError(e); } });
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                awaitMessage(scenario, com.nextgis.mobile.R.string.background_location_saver_warning);
                chooseSettings(scenario);
                assertEquals(1, saver.getHits());
                scenario.recreate();
                instrumentation.waitForIdleSync();
                scenario.onActivity(activity -> {
                    TextView view = activity.findViewById(com.google.android.material.R.id.snackbar_text);
                    assertTrue("Rotation does not repeat the acknowledged warning", view == null || !view.isShown());
                });
                scenario.moveToState(Lifecycle.State.CREATED);
                shell("cmd power set-mode 0");
                shell("appops set " + app.getPackageName() + " RUN_ANY_IN_BACKGROUND ignore");
                await("App restriction detected", () -> app.getSystemService(ActivityManager.class).isBackgroundRestricted());
                scenario.moveToState(Lifecycle.State.RESUMED);
                awaitMessage(scenario, com.nextgis.mobile.R.string.background_location_restricted_warning);
                chooseSettings(scenario);
                assertEquals(1, restricted.getHits());
                scenario.moveToState(Lifecycle.State.CREATED);
                shell("appops set " + app.getPackageName() + " RUN_ANY_IN_BACKGROUND allow");
                shell("dumpsys deviceidle whitelist -" + app.getPackageName());
                scenario.moveToState(Lifecycle.State.RESUMED);
                awaitMessage(scenario, com.nextgis.mobile.R.string.background_location_optimized_warning);
                chooseSettings(scenario);
                assertEquals(1, optimized.getHits());
                scenario.moveToState(Lifecycle.State.CREATED);
                shell("dumpsys deviceidle whitelist +" + app.getPackageName());
                scenario.moveToState(Lifecycle.State.RESUMED);
                instrumentation.waitForIdleSync();
                await("Clearing restrictions resets acknowledgement", () -> {
                    AtomicBoolean cleared = new AtomicBoolean();
                    scenario.onActivity(activity -> cleared.set(warning(activity).getShownState() == 0));
                    return cleared.get();
                });
                shell("input keyevent KEYCODE_SLEEP");
                shell("cmd power set-mode 1");
                await("Saver enabled while asleep", () -> LocationPowerPolicy.shouldWarn(app));
                shell("input keyevent KEYCODE_WAKEUP");
                shell("wm dismiss-keyguard");
                awaitMessage(scenario, com.nextgis.mobile.R.string.background_location_saver_warning);
                assertFalse("Settings and resume never start a track", TrackerService.hasRecordingSession(app));
            }
        } finally {
            instrumentation.removeMonitor(saver);
            instrumentation.removeMonitor(restricted);
            instrumentation.removeMonitor(optimized);
            shell("cmd power set-mode 0");
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
            shell("appops set " + app.getPackageName() + " RUN_ANY_IN_BACKGROUND allow");
            shell("dumpsys deviceidle whitelist " + (wasExempt ? "+" : "-") + app.getPackageName());
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
            assertTrue(restore.commit());
        }
    }
}
