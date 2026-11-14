package com.nextgis.mobile.reliability;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplibui.util.LayerBackupManager;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.SettingsActivity;
import com.nextgis.mobile.fragment.SettingsFragment;
import com.nextgis.mobile.fragment.SettingsHeaderFragment;
import com.nextgis.mobile.util.MaintenanceActions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

/** Real settings launched without MainActivity; only isolated verification files are touched. */
@RunWith(AndroidJUnit4.class)
public class SettingsMaintenanceTest {
    private Context app;

    @Before public void requireIsolatedEmulator() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        assertEquals("com.nextgis.mobile.verification.debug", app.getPackageName());
        assertTrue(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
    }

    @Test public void openingGeneralSettingsKeepsDataAndCreatesNoActionPreferenceValues() throws Exception {
        File log = logMarker();
        File backup = backupMarker();
        try (ActivityScenario<SettingsActivity> screen = openGeneral()) {
            screen.onActivity(activity -> {
                SettingsFragment fragment = general(activity);
                Preference clearLog = fragment.findPreference(MaintenanceActions.KEY_CLEAR_LOG);
                Preference clearBackups = fragment.findPreference(MaintenanceActions.KEY_CLEAR_BACKUPS);
                assertNotNull(clearLog);
                assertNotNull(clearBackups);
                assertFalse(clearLog.isPersistent());
                assertFalse(clearBackups.isPersistent());
                assertEquals(activity.getString(R.string.maintenance_clear_backups), clearBackups.getTitle().toString());
                assertEquals(activity.getString(R.string.maintenance_actions_title), clearBackups.getParent().getTitle().toString());
            });
            assertEquals("keep log", read(log));
            assertEquals("keep backup", read(backup));
            assertFalse(androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
                    .contains(MaintenanceActions.KEY_CLEAR_LOG));
            assertFalse(androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
                    .contains(MaintenanceActions.KEY_CLEAR_BACKUPS));
        }
    }

    @Test public void logActionNeedsConfirmationAndKeepsBackupsAndProjectData() throws Exception {
        File log = logMarker();
        File backup = backupMarker();
        File project = write(new File(app.getExternalFilesDir(null), "maintenance-project-proof/feature.sqlite"), "keep project");
        try (ActivityScenario<SettingsActivity> screen = openGeneral()) {
            clickPreference(screen, MaintenanceActions.KEY_CLEAR_LOG);
            answerDialog(false);
            assertEquals("keep log", read(log));
            clickPreference(screen, MaintenanceActions.KEY_CLEAR_LOG);
            back();
            assertEquals("keep log", read(log));
            clickPreference(screen, MaintenanceActions.KEY_CLEAR_LOG);
            answerDialog(true);
            assertFalse(log.exists());
            assertEquals("keep backup", read(backup));
            assertEquals("keep project", read(project));
            clickPreference(screen, MaintenanceActions.KEY_CLEAR_LOG);
            answerDialog(true);
            assertEquals("keep project", read(project));
        }
    }

    @Test public void backupActionNeedsConfirmationAndKeepsLogsAndProjectData() throws Exception {
        File log = logMarker();
        File backup = backupMarker();
        File project = write(new File(app.getExternalFilesDir(null), "maintenance-project-proof/feature.sqlite"), "keep project");
        try (ActivityScenario<SettingsActivity> screen = openGeneral()) {
            clickPreference(screen, MaintenanceActions.KEY_CLEAR_BACKUPS);
            answerDialog(false);
            assertEquals("keep backup", read(backup));
            clickPreference(screen, MaintenanceActions.KEY_CLEAR_BACKUPS);
            back();
            assertEquals("keep backup", read(backup));
            clickPreference(screen, MaintenanceActions.KEY_CLEAR_BACKUPS);
            answerDialog(true);
            assertFalse(backup.exists());
            assertEquals("keep log", read(log));
            assertEquals("keep project", read(project));
            assertTrue(LayerBackupManager.getBackupRoot(app).isDirectory());
            clickPreference(screen, MaintenanceActions.KEY_CLEAR_BACKUPS);
            answerDialog(true);
            assertEquals("keep project", read(project));
        }
    }

    private ActivityScenario<SettingsActivity> openGeneral() throws Exception {
        ActivityScenario<SettingsActivity> screen = ActivityScenario.launch(SettingsActivity.class);
        screen.onActivity(activity -> {
            if (general(activity) != null) return;
            for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
                if (!(fragment instanceof SettingsHeaderFragment)) continue;
                PreferenceScreen preference = ((SettingsHeaderFragment) fragment).findPreference(SettingsConstantsUI.ACTION_PREFS_GENERAL);
                assertNotNull(preference);
                activity.onPreferenceStartScreen((SettingsHeaderFragment) fragment, preference);
                return;
            }
            fail("Settings headers are attached");
        });
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            boolean[] ready = {false};
            screen.onActivity(activity -> ready[0] = general(activity) != null);
            if (ready[0]) return screen;
            SystemClock.sleep(50);
        }
        screen.close();
        throw new AssertionError("General preferences did not attach");
    }

    private android.view.View awaitDialog() {
        java.util.concurrent.atomic.AtomicReference<android.view.View> found = new java.util.concurrent.atomic.AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (android.view.View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.view.View button = root.findViewById(android.R.id.button1);
                    if (button != null && button.isShown() && root.hasWindowFocus()) found.set(root);
                }
            });
            if (found.get() != null) return found.get();
            SystemClock.sleep(50);
        }
        throw new AssertionError("Maintenance confirmation has focus");
    }

    private void awaitClosed(android.view.View root) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        java.util.concurrent.atomic.AtomicBoolean attached = new java.util.concurrent.atomic.AtomicBoolean(true);
        while (attached.get() && SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> attached.set(root.isAttachedToWindow()));
            if (attached.get()) SystemClock.sleep(50);
        }
        assertFalse("Confirmation window must close before the next action", attached.get());
    }

    private void answerDialog(boolean confirm) {
        android.view.View root = awaitDialog();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                assertTrue(root.findViewById(confirm ? android.R.id.button1 : android.R.id.button2).performClick()));
        awaitClosed(root);
    }

    private void back() {
        android.view.View root = awaitDialog();
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        awaitClosed(root);
    }

    private SettingsFragment general(SettingsActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments())
            if (fragment instanceof SettingsFragment && fragment.isAdded()
                    && ((SettingsFragment) fragment).findPreference(MaintenanceActions.KEY_CLEAR_LOG) != null)
                return (SettingsFragment) fragment;
        return null;
    }

    private void clickPreference(ActivityScenario<SettingsActivity> screen, String key) {
        screen.onActivity(activity -> {
            Preference preference = general(activity).findPreference(key);
            assertNotNull(preference);
            preference.performClick();
        });
    }

    private File logMarker() throws Exception {
        return write(new File(app.getExternalFilesDir(null), "LogFiles/maintenance-test.log"), "keep log");
    }
    private File backupMarker() throws Exception {
        return write(new File(LayerBackupManager.getBackupRoot(app), "maintenance-test.zip"), "keep backup");
    }
    private File write(File file, String text) throws Exception {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }
    private String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
