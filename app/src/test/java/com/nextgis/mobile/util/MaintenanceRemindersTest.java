package com.nextgis.mobile.util;

import android.app.Application;
import android.content.Context;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.api.INGWLayer;
import com.nextgis.maplib.util.ProjectSyncState;
import com.nextgis.mobile.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {28, 36})
public class MaintenanceRemindersTest {
    private Context context;
    private String project;
    private static final long START = 1_790_000_000_000L;
    private static final long EIGHT_DAYS = 8L * 24 * 60 * 60 * 1000;
    private final long nextVersion = BuildConfig.VERSION_CODE + 1L;

    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(MaintenanceReminders.PREFERENCES, 0).edit().clear().commit();
        context.getSharedPreferences("full-project-sync-state", 0).edit().clear().commit();
        project = new File(context.getFilesDir(), "technical-project").getAbsolutePath();
    }

    @Test public void noHistoryHasAWeekOfObservationWithoutInventingSuccessfulSync() {
        assertFalse(MaintenanceReminders.read(context, project, 0, START).needed());
        assertFalse(MaintenanceReminders.read(context, project, 0, START + MaintenanceReminders.WEEK_MS).needed());
        MaintenanceReminders.Reminder late = MaintenanceReminders.read(context, project, 0, START + EIGHT_DAYS);
        assertTrue(late.syncOverdue);
        assertTrue(late.syncUnknown);
        assertTrue(late.checkOverdue);
        assertEquals(0, ProjectSyncState.lastComplete(context, project));
    }

    @Test public void localProjectNeverInheritsTheOldOnlineProjectSyncWarning() {
        assertTrue(ProjectSyncState.markComplete(context, project, START));
        MaintenanceReminders.read(context, project, 0, START);
        MaintenanceReminders.recordSuccessfulCheck(context, BuildConfig.VERSION_CODE, START + EIGHT_DAYS);
        assertTrue(MaintenanceReminders.read(context, project, 0, START + EIGHT_DAYS).syncOverdue);
        assertFalse(MaintenanceReminders.read(context, null, 0, START + EIGHT_DAYS).needed());
    }

    @Test public void fullSuccessClearsOnlyItsOwnProjectsWarning() {
        assertTrue(ProjectSyncState.markComplete(context, project, START));
        String other = project + "-other";
        assertTrue(ProjectSyncState.markComplete(context, other, START + EIGHT_DAYS));
        MaintenanceReminders.recordSuccessfulCheck(context, BuildConfig.VERSION_CODE, START + EIGHT_DAYS);
        assertTrue(MaintenanceReminders.read(context, project, 0, START + EIGHT_DAYS).syncOverdue);
        assertFalse(MaintenanceReminders.read(context, other, 0, START + EIGHT_DAYS).syncOverdue);
        assertTrue(ProjectSyncState.markComplete(context, project, START + EIGHT_DAYS));
        assertFalse(MaintenanceReminders.read(context, project, 0, START + EIGHT_DAYS).needed());
    }

    @Test public void successfulUpdateCheckDoesNotRefreshProjectSyncAndViceVersa() {
        MaintenanceReminders.read(context, project, 0, START);
        MaintenanceReminders.recordSuccessfulCheck(context, BuildConfig.VERSION_CODE, START);
        assertTrue(ProjectSyncState.markComplete(context, project, START + EIGHT_DAYS));
        MaintenanceReminders.Reminder late = MaintenanceReminders.read(context, project, 0, START + EIGHT_DAYS);
        assertFalse(late.syncOverdue);
        assertTrue(late.checkOverdue);
        MaintenanceReminders.recordSuccessfulCheck(context, BuildConfig.VERSION_CODE, START + EIGHT_DAYS);
        assertFalse(MaintenanceReminders.read(context, project, 0, START + EIGHT_DAYS).needed());
    }

    @Test public void availableUpdateDistinguishesPartialOrOlderFilesFromValidatedCurrentApk() {
        MaintenanceReminders.recordSuccessfulCheck(context, nextVersion, START);
        assertEquals(MaintenanceReminders.Update.NONE,
                MaintenanceReminders.read(context, null, nextVersion, START + MaintenanceReminders.WEEK_MS).update);
        assertEquals(MaintenanceReminders.Update.DOWNLOAD,
                MaintenanceReminders.read(context, null, 0, START + EIGHT_DAYS).update);
        assertEquals(MaintenanceReminders.Update.DOWNLOAD,
                MaintenanceReminders.read(context, null, BuildConfig.VERSION_CODE, START + EIGHT_DAYS).update);
        assertEquals(MaintenanceReminders.Update.INSTALL,
                MaintenanceReminders.read(context, null, nextVersion, START + EIGHT_DAYS).update);
    }

    @Test public void newerReleaseAndRepeatedChecksDoNotPostponeAnOutstandingUpdate() {
        MaintenanceReminders.recordSuccessfulCheck(context, nextVersion, START);
        MaintenanceReminders.recordSuccessfulCheck(context, nextVersion + 1, START + EIGHT_DAYS);
        MaintenanceReminders.Reminder late = MaintenanceReminders.read(context, null, nextVersion, START + EIGHT_DAYS);
        assertEquals(MaintenanceReminders.Update.DOWNLOAD, late.update);
        assertFalse(late.checkOverdue);
        assertEquals(MaintenanceReminders.Update.INSTALL,
                MaintenanceReminders.read(context, null, nextVersion + 1, START + EIGHT_DAYS).update);
    }

    @Test public void confirmedCurrentManifestClearsWithdrawnOrInstalledUpdate() {
        MaintenanceReminders.recordSuccessfulCheck(context, nextVersion, START);
        MaintenanceReminders.recordSuccessfulCheck(context, BuildConfig.VERSION_CODE, START + EIGHT_DAYS);
        assertFalse(MaintenanceReminders.read(context, null, 0, START + EIGHT_DAYS).needed());
    }

    @Test public void cachedApkDoesNotPretendThatInternetWasChecked() {
        MaintenanceReminders.recordSuccessfulCheck(context, nextVersion, START);
        MaintenanceReminders.recordReady(context, nextVersion, START + EIGHT_DAYS);
        MaintenanceReminders.Reminder late = MaintenanceReminders.read(context, null, nextVersion, START + EIGHT_DAYS);
        assertTrue(late.checkOverdue);
        assertEquals(MaintenanceReminders.Update.INSTALL, late.update);
        assertEquals(START, context.getSharedPreferences(MaintenanceReminders.PREFERENCES, 0).getLong("last_check", 0));
    }

    @Test public void anotherProcessContextKeepsClocksButANewSessionCanWarnAgain() {
        MaintenanceReminders.recordSuccessfulCheck(context, nextVersion, START);
        MaintenanceReminders.markShown(context, "launch-one");
        Context recreated = new android.content.ContextWrapper(context);
        assertTrue(MaintenanceReminders.alreadyShown(recreated, "launch-one"));
        assertFalse(MaintenanceReminders.alreadyShown(recreated, "launch-two"));
        assertEquals(MaintenanceReminders.Update.DOWNLOAD,
                MaintenanceReminders.read(recreated, null, 0, START + EIGHT_DAYS).update);
    }

    @Test public void backwardsClockCannotCreateAFalseOverdueWarning() {
        MaintenanceReminders.read(context, project, 0, START);
        MaintenanceReminders.recordSuccessfulCheck(context, nextVersion, START);
        assertFalse(MaintenanceReminders.read(context, project, nextVersion, START - EIGHT_DAYS).needed());
        assertFalse(MaintenanceReminders.overdue(START, 0));
    }

    @Test public void residentProcessGetsADailyReviewWithoutRetryOrRotationSpam() {
        assertFalse(MaintenanceReminders.reviewDue(START, 0));
        assertFalse(MaintenanceReminders.reviewDue(START + 10_000, START));
        assertFalse(MaintenanceReminders.reviewDue(START - EIGHT_DAYS, START));
        assertTrue(MaintenanceReminders.reviewDue(START + 24L * 60 * 60 * 1000, START));
        MaintenanceReminders.markShown(context, "same-resident-process");
        assertTrue(MaintenanceReminders.alreadyShown(context, "same-resident-process"));
        context.getSharedPreferences(MaintenanceReminders.PREFERENCES, 0).edit()
                .putLong("shown_at", System.currentTimeMillis() - EIGHT_DAYS).commit();
        assertFalse(MaintenanceReminders.alreadyShown(context, "same-resident-process"));
    }

    @Test public void nestedOnlineLayersAreRecognizedButLocalGeometryAndEmptyMapsAreNot() {
        java.util.List<ILayer> children = new java.util.ArrayList<>();
        LayerGroup child = new LayerGroup(context, new File(context.getFilesDir(), "nested"), null) {
            @Override public java.util.List<ILayer> getLayers() { return children; }
        };
        LayerGroup root = new LayerGroup(context, new File(context.getFilesDir(), "root"), null) {
            @Override public java.util.List<ILayer> getLayers() { return java.util.Collections.singletonList(child); }
        };
        java.lang.reflect.InvocationHandler noBusinessAccess = (proxy, method, args) -> {
            throw new AssertionError("Reminder must not access layer data: " + method.getName());
        };
        children.add((ILayer) java.lang.reflect.Proxy.newProxyInstance(ILayer.class.getClassLoader(),
                new Class<?>[]{ILayer.class}, noBusinessAccess));
        assertFalse(MaintenanceReminders.hasOnlineLayers(root));
        children.add((ILayer) java.lang.reflect.Proxy.newProxyInstance(ILayer.class.getClassLoader(),
                new Class<?>[]{ILayer.class, INGWLayer.class}, noBusinessAccess));
        assertTrue(MaintenanceReminders.hasOnlineLayers(root));
        assertFalse(MaintenanceReminders.hasOnlineLayers(null));
    }
}
