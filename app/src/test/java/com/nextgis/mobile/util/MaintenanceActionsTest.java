package com.nextgis.mobile.util;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import androidx.preference.PreferenceManager;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplibui.util.LayerBackupManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MaintenanceActionsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Context context;
    private File external;

    @Before public void setUp() throws Exception {
        external = temporary.newFolder("external");
        context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public File getExternalFilesDir(String type) { return external; }
        };
        HyperLog.initialize(context);
    }

    @Test public void logCleanupRemovesOnlyLogChildrenAndPreservesBackupAndProjectFiles() throws Exception {
        File log = write("LogFiles/nested/old.log", "old log");
        File project = write("projects/working.sqlite", "unchanged project");
        File backup = write(LayerBackupManager.getBackupRoot(context), "saved.zip", "saved backup");
        assertTrue(MaintenanceActions.clearLog(context));
        assertFalse(log.exists());
        assertTrue(new File(external, "LogFiles").isDirectory());
        assertEquals("unchanged project", read(project));
        assertEquals("saved backup", read(backup));
    }

    @Test public void emptyLogStorageSucceedsWithoutCreatingALogFolder() {
        assertTrue(MaintenanceActions.clearLog(context));
        assertFalse(new File(external, "LogFiles").exists());
    }

    @Test public void invalidLogDirectoryIsReportedAsFailureAndIsKept() throws Exception {
        File invalid = write("LogFiles", "not a log directory");
        assertFalse(MaintenanceActions.clearLog(context));
        assertEquals("not a log directory", read(invalid));
    }

    @Test public void missingExternalStorageIsFailureWithoutRelativePathFallback() {
        Context missing = new ContextWrapper(context) {
            @Override public File getExternalFilesDir(String type) { return null; }
        };
        assertFalse(MaintenanceActions.clearLog(missing));
    }

    @Test public void backupCleanupKeepsProjectLogAndQuota() throws Exception {
        File root = LayerBackupManager.getBackupRoot(context);
        File backup = write(root, "nested/saved.zip", "old copy");
        File project = write("projects/working.sqlite", "unchanged data");
        File log = write("LogFiles/current.log", "current log");
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("layer_backup_max_gb", "10").putString("unrelated", "keep").commit();
        assertTrue(MaintenanceActions.clearBackups(context));
        assertFalse(backup.exists());
        assertTrue(root.isDirectory());
        assertEquals("unchanged data", read(project));
        assertEquals("current log", read(log));
        assertEquals("10", PreferenceManager.getDefaultSharedPreferences(context).getString("layer_backup_max_gb", ""));
        assertEquals("keep", PreferenceManager.getDefaultSharedPreferences(context).getString("unrelated", ""));
        assertFalse(PreferenceManager.getDefaultSharedPreferences(context).contains(MaintenanceActions.KEY_CLEAR_BACKUPS));
    }

    @Test public void emptyBackupStorageSucceedsAndKeepsItsDirectory() {
        assertTrue(MaintenanceActions.clearBackups(context));
        assertTrue(LayerBackupManager.getBackupRoot(context).isDirectory());
    }

    @Test public void invalidBackupDirectoryIsRefusedWithoutDeletingUnknownData() throws Exception {
        File invalid = write(LayerBackupManager.getBackupRoot(context).getParentFile(),
                LayerBackupManager.getBackupRoot(context).getName(), "not a backup directory");
        assertFalse(MaintenanceActions.clearBackups(context));
        assertEquals("not a backup directory", read(invalid));
    }

    private File write(String path, String value) throws Exception { return write(external, path, value); }
    private File write(File root, String path, String value) throws Exception {
        File file = new File(root, path);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
        return file;
    }
    private String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
