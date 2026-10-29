// Technical JVM regressions for actual Settings callbacks; no native storage acceptance.
package com.nextgis.mobile.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Looper;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.maplibui.util.WalkSessionStore;
import com.nextgis.mobile.MainApplication;
import com.nextgis.mobile.R;
import com.nextgis.mobile.util.SelectMapPathPreference;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPausedAsyncTask;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = SettingsWorkspaceMoveTest.ProbeApplication.class, sdk = 28,
        qualifiers = "ru", shadows = SettingsWorkspaceMoveTest.QuietLog.class,
        instrumentedPackages = "com.hypertrack.hyperlog")
@LooperMode(LooperMode.Mode.PAUSED)
public class SettingsWorkspaceMoveTest {
    // Only an unrelated logging dependency is quieted. No coordinator or handler is mocked.
    @Implements(value = HyperLog.class, isInAndroidSdk = false)
    public static class QuietLog {
        @Implementation protected static void v(String tag, String text) { }
        @Implementation protected static void w(String tag, String text) { }
        @Implementation protected static void v(String tag, String text, Throwable error) { }
        @Implementation protected static void w(String tag, String text, Throwable error) { }
    }

    public static class ProbeApplication extends MainApplication {
        MapBase probeMap;
        int resets, baseInitializations, retires;
        boolean failMapRead;
        @Override public void onCreate() { /* No production bootstrap, migrations or workers. */ }
        @Override public synchronized MapBase getMap() {
            if (failMapRead) throw new IllegalStateException("technical owner read failure");
            return probeMap;
        }
        @Override public synchronized void resetMap() { assertUiLease(this); resets++; }
        @Override public void initBaseLayers() { assertUiLease(this); baseInitializations++; }
        @Override public void onTerminate() { }
        void install(MapDrawable map) { probeMap = map; mMap = map; }
        void clearActualMapField() { mMap = null; }
    }

    public static class HostActivity extends Activity {
        boolean forbidPreferenceEdits;
        boolean failCommitAfterCache;
        RuntimeException failBeforeCache;
        int forbiddenEditAttempts;
        int destinationResolutions;
        boolean failWindowLookup;
        @Override public File getExternalFilesDir(String type) {
            destinationResolutions++;
            return super.getExternalFilesDir(type);
        }
        @Override public File getFilesDir() {
            destinationResolutions++;
            return super.getFilesDir();
        }
        @Override public Object getSystemService(String name) {
            if (failWindowLookup && Context.WINDOW_SERVICE.equals(name))
                throw new IllegalStateException("technical pre-lock window read failure");
            return super.getSystemService(name);
        }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            SharedPreferences actual = super.getSharedPreferences(name, mode);
            return (SharedPreferences) Proxy.newProxyInstance(
                    SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                    (proxy, method, args) -> {
                        if (forbidPreferenceEdits && method.getName().equals("edit")) {
                            forbiddenEditAttempts++;
                            throw new AssertionError("Actual Settings callback attempted prefs.edit while owner held");
                        }
                        if (method.getName().equals("edit") && failBeforeCache != null)
                            throw failBeforeCache;
                        try {
                            Object value = method.invoke(actual, args);
                            if (!method.getName().equals("edit")) return value;
                            SharedPreferences.Editor editor = (SharedPreferences.Editor) value;
                            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                    new Class<?>[]{SharedPreferences.Editor.class}, (editorProxy, edit, values) -> {
                                        try {
                                            Object result = edit.invoke(editor, values);
                                            if (edit.getName().equals("commit") && failCommitAfterCache) return false;
                                            return result == editor ? editorProxy : result;
                                        } catch (InvocationTargetException error) { throw error.getCause(); }
                                    });
                        }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                    });
        }
    }

    static class ProbeMap extends MapDrawable {
        final AtomicInteger moves = new AtomicInteger();
        boolean keepOriginalPath;
        RuntimeException moveFailure;
        Error fatalFailure;
        CountDownLatch entered, release;
        ProbeMap(Context context, File mapFile) {
            super(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), context, mapFile, null);
        }
        @Override public void retireSqliteVectorSources() {
            ((ProbeApplication) getContext()).retires++;
        }
        @Override public SQLiteDatabase getDatabase(boolean readOnly) {
            throw new AssertionError("Technical model must never open a database engine");
        }
        @Override public void moveTo(File target) {
            moves.incrementAndGet();
            if (entered != null) {
                entered.countDown();
                boolean interrupted = false;
                while (release.getCount() != 0) {
                    try { release.await(); } catch (InterruptedException error) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            if (moveFailure != null) throw moveFailure;
            if (fatalFailure != null) throw fatalFailure;
            if (!keepOriginalPath) mPath = target;
        }
        @Override public boolean save() { return true; }
        static void restoreActive(MapBase previous) { mInstance = previous; }
    }

    static class ProbeDatabaseMap extends ProbeMap {
        int databaseAttempts;
        ProbeDatabaseMap(Context context, File file) { super(context, file); }
        @Override public SQLiteDatabase getDatabase(boolean readOnly) {
            assertUiLease(getContext());
            databaseAttempts++;
            throw new SQLiteException("Technical no-engine boundary before VACUUM");
        }
    }

    static class QueuedExecutor implements Executor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Runnable last;
        @Override public void execute(Runnable action) { last = action; queue.add(action); }
        void runOne() { assertFalse("Actual AsyncTask must reach the captured executor", queue.isEmpty()); queue.remove().run(); }
        void runAll() { while (!queue.isEmpty()) runOne(); }
        SettingsFragment.BackgroundMoveTask task() {
            for (Field field : last.getClass().getDeclaredFields()) {
                if (!AsyncTask.class.isAssignableFrom(field.getType())) continue;
                try { field.setAccessible(true); return (SettingsFragment.BackgroundMoveTask) field.get(last); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            }
            throw new AssertionError("Actual Android FutureTask must retain its AsyncTask owner");
        }
    }

    ActivityController<HostActivity> host;
    HostActivity activity;
    ProbeApplication app;
    ProbeMap map;
    MapBase previousMap;
    SharedPreferences preferences;
    PreferenceScreen screen;
    SelectMapPathPreference mapPreference;
    QueuedExecutor background;
    File original, destination;
    ProbeDatabaseMap databaseProbe;
    Thread worker;
    final List<ProjectOperationCoordinator.Lease> owned = new ArrayList<>();

    @Before public void setUp() {
        app = (ProbeApplication) RuntimeEnvironment.getApplication();
        previousMap = MapBase.getActiveInstance();
        host = Robolectric.buildActivity(HostActivity.class);
        activity = host.get(); activity.setTheme(R.style.AppTheme);
        host.create().start().resume().visible();
        original = new File(activity.getFilesDir(), "technical-original");
        destination = new File(activity.getFilesDir(), "technical-destination");
        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        preferences.edit().clear().putString(SettingsConstants.KEY_PREF_MAP_PATH, original.getAbsolutePath())
                .putString("probe_user_setting", "retain").commit();
        WalkSessionStore.preferences(activity).edit().clear().commit();
        map = new ProbeMap(app, new File(original, "default.ngm")); app.install(map);
        PreferenceManager manager = new PreferenceManager(activity);
        screen = manager.createPreferenceScreen(activity);
        mapPreference = new SelectMapPathPreference(activity, null);
        mapPreference.setKey(SettingsConstants.KEY_PREF_MAP_PATH);
        screen.addPreference(mapPreference); // Attached hierarchy: persistString has a real owner.
        SettingsFragment.initializeMapPath(activity, mapPreference);
        background = new QueuedExecutor();
        ShadowPausedAsyncTask.overrideExecutor(background);
    }

    @After public void tearDown() {
        if (activity != null) activity.forbidPreferenceEdits = false;
        if (activity != null) activity.failWindowLookup = false;
        if (map != null && map.release != null) map.release.countDown();
        if (worker != null) {
            try { worker.join(2000); } catch (InterruptedException error) { throw new AssertionError(error); }
            assertFalse("Owned technical worker must unwind", worker.isAlive());
        }
        for (ProjectOperationCoordinator.Lease lease : owned) lease.close();
        if (background != null) background.runAll();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        ShadowPausedAsyncTask.overrideExecutor(null);
        ProbeMap.restoreActive(previousMap);
        if (map != null) map.closeSyncWorkspace();
        if (databaseProbe != null) databaseProbe.closeSyncWorkspace();
        if (host != null) host.pause().stop().destroy();
    }

    private static void assertUiLease(Context context) {
        ProjectOperationCoordinator.Lease escaped = ProjectOperationCoordinator.tryBegin(
                context, ProjectOperationCoordinator.Kind.DATA_SYNC);
        try { assertNull("Lease must still exclude sync inside actual UI consumer callbacks", escaped); }
        finally { if (escaped != null) escaped.close(); }
    }

    private ProjectOperationCoordinator.Lease hold(ProjectOperationCoordinator.Kind kind) {
        ProjectOperationCoordinator.Lease lease = ProjectOperationCoordinator.tryBegin(activity, kind);
        assertNotNull("Technical owner must be acquired through the real public API", lease);
        owned.add(lease); return lease;
    }

    private void assertNewSyncBlocked() {
        ProjectOperationCoordinator.Lease attempted = ProjectOperationCoordinator.tryBegin(
                activity, ProjectOperationCoordinator.Kind.DATA_SYNC);
        try { assertNull("Move must own the workspace through its terminal UI callback", attempted); }
        finally { if (attempted != null) attempted.close(); }
    }

    private void assertPreferenceRefusal(ProjectOperationCoordinator.Kind kind) {
        hold(kind);
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        CharSequence summary = mapPreference.getSummary();
        int result = Shadows.shadowOf(activity).getResultCode();
        ProjectOperationCoordinator.Snapshot owner = ProjectOperationCoordinator.snapshot();
        boolean accepted = mapPreference.callChangeListener(destination.getAbsolutePath());
        assertFalse("Actual initialized preference listener must refuse while another owner is held", accepted);
        assertEquals(before, preferences.getAll()); assertEquals(summary, mapPreference.getSummary());
        assertEquals(0, app.resets); assertEquals(0, map.moves.get()); assertTrue(background.queue.isEmpty());
        assertEquals(result, Shadows.shadowOf(activity).getResultCode());
        assertOwnerUnchanged(owner);
    }

    private void assertOwnerUnchanged(ProjectOperationCoordinator.Snapshot owner) {
        ProjectOperationCoordinator.Snapshot after = ProjectOperationCoordinator.snapshot();
        assertTrue(after.isBusy()); assertEquals(owner.getOperationCount(), after.getOperationCount());
        assertEquals(owner.getOldestKind(), after.getOldestKind());
        assertEquals(owner.getWorkspaceKey(), after.getWorkspaceKey());
        assertNewSyncBlocked();
    }

    private void clickReset() {
        Preference reset = new Preference(activity); reset.setKey("technical-reset"); screen.addPreference(reset);
        SettingsFragment.initializeReset(activity, reset);
        reset.getOnPreferenceClickListener().onPreferenceClick(reset);
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void actualPreferenceRefusesHeldManualSync() {
        assertPreferenceRefusal(ProjectOperationCoordinator.Kind.DATA_SYNC);
    }
    @Test public void actualPreferenceRefusesHeldLayerFill() {
        assertPreferenceRefusal(ProjectOperationCoordinator.Kind.LAYER_FILL);
    }
    @Test public void actualPreferenceRefusesHeldSchemaRebuild() {
        assertPreferenceRefusal(ProjectOperationCoordinator.Kind.SCHEMA_REBUILD);
    }

    @Test public void actualResetPositiveCallbackRefusesBeforeFirstPreferenceEdit() {
        hold(ProjectOperationCoordinator.Kind.DATA_SYNC);
        ProjectOperationCoordinator.Snapshot owner = ProjectOperationCoordinator.snapshot();
        int result = Shadows.shadowOf(activity).getResultCode();
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        Preference reset = new Preference(activity); reset.setKey("technical-reset"); screen.addPreference(reset);
        SettingsFragment.initializeReset(activity, reset);
        reset.getOnPreferenceClickListener().onPreferenceClick(reset);
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        int resolutions = activity.destinationResolutions;
        activity.forbidPreferenceEdits = true;
        try {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            // AlertController posts its listener; do not drop the write oracle before delivery.
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        finally { activity.forbidPreferenceEdits = false; }
        assertEquals(0, activity.forbiddenEditAttempts); assertEquals(before, preferences.getAll());
        assertEquals(0, app.resets); assertEquals(0, app.baseInitializations);
        assertEquals(0, map.moves.get()); assertTrue(background.queue.isEmpty());
        assertEquals(result, Shadows.shadowOf(activity).getResultCode()); assertOwnerUnchanged(owner);
        assertEquals("Busy Reset must not resolve/create its default destination", resolutions, activity.destinationResolutions);
    }

    @Test public void actualQueuedMoveBlocksSyncBeforeWorkerAndBeforePost() {
        SettingsFragment.moveMap(activity, destination);
        assertEquals(1, background.queue.size());
        assertNewSyncBlocked(); // Independent converse oracle: before any fake move body runs.
        background.runOne();
        assertEquals(1, map.moves.get());
        assertNewSyncBlocked(); // Worker ended, actual AsyncTask UI post is still held.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(ProjectOperationCoordinator.isBusy());
        hold(ProjectOperationCoordinator.Kind.DATA_SYNC); // Recovery after terminal owner release.
    }

    @Test public void idleActualTaskReachesProbeWithoutDatabaseOrRenderer() {
        SettingsFragment.moveMap(activity, destination);
        assertEquals(1, background.queue.size());
        background.runOne(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, map.moves.get()); assertEquals(1, app.resets);
        assertEquals(destination.getAbsolutePath(), preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, ""));
    }

    @Test public void admittedPreferenceWaitsForActualPathAndRebindsUnderLease() {
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        assertFalse(mapPreference.callChangeListener(destination.getAbsolutePath()));
        assertEquals(before, preferences.getAll()); assertEquals(0, app.resets);
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).getResultCode());
        background.runOne();
        assertEquals(destination.getAbsolutePath(), preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, ""));
        assertEquals(original.getAbsolutePath(), mapPreference.getText()); assertEquals(0, app.resets);
        assertNewSyncBlocked();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(destination.getAbsolutePath(), mapPreference.getText());
        assertEquals(destination.getAbsolutePath(), mapPreference.getSummary());
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        assertEquals(1, app.retires); assertEquals(1, app.resets); assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void admittedResetDefersPreferencesAndDestructionUntilOwnedCompletion() {
        databaseProbe = new ProbeDatabaseMap(app, new File(original, "default.ngm"));
        app.install(databaseProbe);
        preferences.edit().putBoolean(SettingsConstants.KEY_PREF_VERBOSE_LOG, true).commit();
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        clickReset();
        assertEquals(before, preferences.getAll()); assertEquals(0, app.resets);
        assertEquals(0, databaseProbe.databaseAttempts); assertEquals(0, app.baseInitializations);
        background.runOne();
        assertTrue(preferences.getBoolean(SettingsConstants.KEY_PREF_VERBOSE_LOG, false));
        assertEquals(0, app.resets); assertNewSyncBlocked();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(preferences.getBoolean(SettingsConstants.KEY_PREF_VERBOSE_LOG, false));
        assertEquals("retain", preferences.getString("probe_user_setting", ""));
        assertEquals(1, databaseProbe.moves.get()); assertEquals(1, databaseProbe.databaseAttempts);
        assertEquals(2, app.baseInitializations); assertEquals(1, app.resets);
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void targetNotReachedRefreshesOldActualMapWithoutResettingSettings() {
        map.keepOriginalPath = true;
        preferences.edit().putBoolean(SettingsConstants.KEY_PREF_VERBOSE_LOG, true).commit();
        clickReset(); background.runOne(); assertNewSyncBlocked();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(original.getAbsolutePath(), preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, ""));
        assertTrue(preferences.getBoolean(SettingsConstants.KEY_PREF_VERBOSE_LOG, false));
        assertEquals(0, app.baseInitializations); assertEquals(1, app.resets);
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void failedCommitAfterCacheMutationStillRefreshesActualMapWithoutResetDefaults() {
        preferences.edit().putBoolean(SettingsConstants.KEY_PREF_VERBOSE_LOG, true).commit();
        clickReset(); activity.failCommitAfterCache = true;
        background.runOne(); assertNewSyncBlocked();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(map.getPath().getAbsolutePath(), preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, ""));
        assertTrue(preferences.getBoolean(SettingsConstants.KEY_PREF_VERBOSE_LOG, false));
        assertEquals(0, app.baseInitializations); assertEquals(1, app.resets);
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void executorRejectionReleasesOnlyItsOwnerWithoutPublication() {
        ShadowPausedAsyncTask.overrideExecutor(action -> { throw new java.util.concurrent.RejectedExecutionException("technical reject"); });
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        assertFalse(mapPreference.callChangeListener(destination.getAbsolutePath()));
        assertEquals(before, preferences.getAll()); assertEquals(0, app.resets);
        assertEquals(0, app.retires); assertEquals(0, map.moves.get());
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).getResultCode());
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void mapReadFailureLeavesPreferencesAndOtherStateUntouched() {
        app.failMapRead = true;
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        assertFalse(mapPreference.callChangeListener(destination.getAbsolutePath()));
        assertEquals(before, preferences.getAll()); assertEquals(0, app.resets);
        assertTrue(background.queue.isEmpty()); assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void cancelledBeforeWorkerNeverRetiresOrMoves() {
        SettingsFragment.moveMap(activity, destination);
        assertTrue(background.task().cancel(true));
        Shadows.shadowOf(Looper.getMainLooper()).idle(); background.runAll();
        assertEquals(0, app.retires); assertEquals(0, map.moves.get()); assertEquals(0, app.resets);
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).getResultCode());
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void cancelledRunningWorkerKeepsLeaseUntilUnwindThenRefreshes() throws Exception {
        map.entered = new CountDownLatch(1); map.release = new CountDownLatch(1);
        SettingsFragment.moveMap(activity, destination);
        SettingsFragment.BackgroundMoveTask task = background.task();
        Runnable actual = background.queue.remove();
        worker = new Thread(actual, "SettingsMoveTechnicalProbe"); worker.start();
        assertTrue(map.entered.await(2, TimeUnit.SECONDS)); assertTrue(task.cancel(true));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, app.resets); assertNewSyncBlocked();
        map.release.countDown(); worker.join(2000); assertFalse(worker.isAlive());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, app.resets); assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void duplicateOldTerminalCallbacksCannotCloseNewOwner() {
        SettingsFragment.moveMap(activity, destination);
        SettingsFragment.BackgroundMoveTask task = background.task();
        background.runOne(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        ProjectOperationCoordinator.Lease later = hold(ProjectOperationCoordinator.Kind.DATA_SYNC);
        ProjectOperationCoordinator.Snapshot owner = ProjectOperationCoordinator.snapshot();
        task.onCancelled((SettingsFragment.MoveOutcome) null); task.onPostExecute(null);
        assertEquals(1, app.resets); assertOwnerUnchanged(owner);
        later.close();
    }

    @Test public void unexpectedBeforeCacheExceptionPreservesWorkerFailureAndDoesNotInventRecovery() {
        SettingsFragment.moveMap(activity, destination);
        RuntimeException expected = new IllegalStateException("technical before-cache exception");
        activity.failBeforeCache = expected;
        RuntimeException propagated = assertThrows(RuntimeException.class, background::runOne);
        assertSame(expected, propagated.getCause());
        activity.failBeforeCache = null;
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, app.resets); assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).getResultCode());
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void fatalWorkerCauseIsNotReplacedByMainThreadRecovery() {
        Error expected = new AssertionError("technical fatal worker"); map.fatalFailure = expected;
        SettingsFragment.moveMap(activity, destination);
        RuntimeException propagated = assertThrows(RuntimeException.class, background::runOne);
        assertSame(expected, propagated.getCause());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, app.resets); assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void postWorkerForeignPathPreventsBlindMapReplacement() {
        SettingsFragment.moveMap(activity, destination); background.runOne();
        preferences.edit().putString(SettingsConstants.KEY_PREF_MAP_PATH, "technical-foreign-owner").commit();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("technical-foreign-owner", preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, ""));
        assertEquals(0, app.resets); assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).getResultCode());
        assertFalse(ProjectOperationCoordinator.isBusy());
    }

    @Test public void actualRebindHelpersNeverResolveAMissingMap() {
        app.clearActualMapField();
        app.failMapRead = true;
        assertFalse(app.canRebindMapAfterStorageMove(map, original));
        assertFalse(app.rebindMapAfterStorageMove(map, original));
        assertFalse(app.retireMapForStorageMove(map));
        assertEquals(0, app.resets); assertEquals(0, app.retires);
    }

    @Test public void actualRebindHelperChecksIdentityPathAndKeepsLeaseDuringReplacement() {
        hold(ProjectOperationCoordinator.Kind.PROJECT_SWITCH);
        assertTrue(app.canRebindMapAfterStorageMove(map, original));
        assertTrue(app.rebindMapAfterStorageMove(map, original));
        assertEquals(1, app.resets);
        assertFalse(app.rebindMapAfterStorageMove(map, destination));
        app.clearActualMapField();
        assertFalse(app.rebindMapAfterStorageMove(map, original));
        assertEquals(1, app.resets);
    }

    @Test public void preLockWindowFailureDoesNotUnlockAnOrientationItNeverAcquired() {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        activity.failWindowLookup = true;
        SettingsFragment.moveMap(activity, destination);
        activity.failWindowLookup = false;
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity.getRequestedOrientation());
        assertEquals(0, app.retires); assertEquals(0, app.resets); assertEquals(0, map.moves.get());
        assertTrue(background.queue.isEmpty()); assertFalse(ProjectOperationCoordinator.isBusy());
    }
}
