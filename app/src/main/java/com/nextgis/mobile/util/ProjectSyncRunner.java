package com.nextgis.mobile.util;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.NgwSyncIo;
import com.nextgis.maplib.util.NgwSyncProgress;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplib.util.SyncWorkspaceSession;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.R;
import com.nextgis.mobile.datasource.SyncAdapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.FutureTask;

/** Serial account/project queue. Background maps never replace the map displayed by the UI. */
public final class ProjectSyncRunner {
    public static final String EXTRA_RECOVERY_TARGET = "ngw_recovery_target";

    public static final class Target {
        public final String uid, name, path, mapName;
        Target(String uid, String name, String path, String mapName) throws IOException {
            this.uid = uid == null ? "" : uid;
            this.name = name == null || name.trim().isEmpty() ? mapName : name;
            this.path = new File(path).getCanonicalPath();
            if (mapName == null || mapName.isEmpty() || !new File(mapName).getName().equals(mapName)
                    || mapName.contains("\\") || mapName.equals(".") || mapName.equals("..")) {
                throw new IOException("Invalid project map name");
            }
            this.mapName = mapName;
        }
        public String key() { return uid + "|" + path + "|" + mapName; }
        File mapFile() { return new File(path, mapName + Constants.MAP_EXT); }
    }

    private ProjectSyncRunner() { }

    public static boolean allProjects(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(AppSettingsConstants.KEY_PREF_SYNC_ALL_PROJECTS, true);
    }

    /** Recovery identifiers are matched against this trusted inventory; no path is accepted in extras. */
    public static List<Target> inventory(Context context) throws IOException {
        LinkedHashMap<String, Target> unique = new LinkedHashMap<>();
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        MapBase active = ((GISApplication) context.getApplicationContext()).getMap();
        if (active != null) {
            Target target = new Target(prefs.getString(SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, ""),
                    active.getName(), active.getPath().getAbsolutePath(),
                    prefs.getString(SettingsConstantsUI.KEY_PREF_MAP_NAME, "default"));
            unique.put(target.path, target);
        }
        for (CollectorProjectRegistry.ProjectInfo info : CollectorProjectRegistry.listProjects(context)) {
            Target target = new Target(info.getProjectUid(), info.getName(), info.getMapPath(), info.getMapName());
            unique.putIfAbsent(target.path, target);
        }
        return new ArrayList<>(unique.values());
    }

    static List<Target> plan(List<Target> inventory, boolean all, String layerPath, String recovery) {
        List<Target> targets = new ArrayList<>();
        for (Target target : inventory) {
            if (recovery != null ? recovery.equals(target.key())
                    : (all && layerPath == null || targets.isEmpty())) targets.add(target);
            if (recovery == null && (!all || layerPath != null)) break;
        }
        return targets;
    }

    interface Pass {
        void perform(Account account, Bundle extras, String authority,
                     ContentProviderClient provider, SyncResult result);
    }

    public static void run(Context context, List<Account> accounts, Bundle extras, String authority,
                           ContentProviderClient provider, SyncResult result) {
        run(context, accounts, extras, authority, provider, result,
                (account, bundle, auth, client, part) -> new SyncAdapter(context, true)
                        .onPerformSync(account, bundle, auth, client, part));
    }

    static void run(Context context, List<Account> accounts, Bundle extras, String authority,
                    ContentProviderClient provider, SyncResult result, Pass pass) {
        // Android may dispatch a previously queued account sync as the network drops.
        // Ask its scheduler to retry without starting progress, diagnostics or a new journal.
        if (!new NetworkUtil(context).isNetworkAvailable()) {
            result.stats.numIoExceptions++;
            result.delayUntil = System.currentTimeMillis() / 1000L + 30;
            return;
        }
        GISApplication app = (GISApplication) context.getApplicationContext();
        Bundle requested = extras == null ? new Bundle() : new Bundle(extras);
        boolean held = requested.getBoolean(OfflineSyncIntentService.EXTRA_PROJECT_OPERATION_ALREADY_HELD, false)
                && ProjectOperationCoordinator.isDataSyncActive();
        ProjectOperationCoordinator.Lease lease = held ? null
                : ProjectOperationCoordinator.tryBegin(context, ProjectOperationCoordinator.Kind.DATA_SYNC);
        if (!held && lease == null) {
            // A scheduled tick must retry after a manual sync/import rather than disappear as success.
            result.stats.numIoExceptions++;
            result.delayUntil = System.currentTimeMillis() / 1000L + 30;
            return;
        }
        boolean ownsProgress = false;
        boolean interrupted = false;
        List<String> failedProjects = new ArrayList<>();
        SyncAdapter notifications = new SyncAdapter(context, true);
        long generation = NgwSyncIo.captureGeneration();
        AppDiagnostics.operation(AppDiagnostics.Operation.SYNC, AppDiagnostics.Phase.START);
        try {
            ProjectOperationCoordinator.requireScopedSyncDependents();
            com.nextgis.maplib.service.NGWSyncService.markSyncStarted();
            context.sendBroadcast(new Intent(SyncAdapter.SYNC_START).setPackage(context.getPackageName()));
            notifications.sendNotification(context, SyncAdapter.SYNC_START, null);
            List<Target> targets = plan(inventory(context), allProjects(context),
                    requested.getString(com.nextgis.maplib.datasource.ngw.SyncAdapter.ACTION_LPATH),
                    requested.getString(EXTRA_RECOVERY_TARGET));
            boolean manual = requested.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
            if (!SyncRecoveryJournal.enqueue(context, targets, accounts, manual)) {
                throw new IOException("Cannot persist sync queue");
            }
            ownsProgress = NgwSyncProgress.ensureSession(context, Math.max(1, targets.size() * accounts.size()));
            for (Target target : targets) {
                if (Thread.currentThread().isInterrupted() || generation != NgwSyncIo.captureGeneration()) break;
                MapContentProviderHelper map = null;
                SyncWorkspaceSession session = null;
                boolean background = false;
                try {
                    MapBase active = app.getMap();
                    background = !target.path.equals(active.getPath().getCanonicalPath());
                    if (!background) map = (MapContentProviderHelper) active;
                    else {
                        if (!target.mapFile().isFile()) throw new IOException("Project map file is missing");
                        map = new MapDrawable(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                                context, target.mapFile(), app.getLayerFactory(), false);
                    }
                    session = new SyncWorkspaceSession(map, target.uid, generation);
                    try (SyncWorkspaceSession.Scope scope = session.enter()) {
                        if (background && !map.load()) throw new IOException("Cannot load project map");
                        onMain(app::prepareProjectSyncState);
                        onMain(app::finalizeCollectorImportVerifyAndRepairIfNeeded);
                        interrupted |= awaitChildren(session, lease);
                        if (interrupted || session.isCancelled()) throw new InterruptedException("Project sync cancelled");
                        if (app.hasCollectorImportBatchRegistered() || session.hasFailed()) {
                            throw new IOException("Unfinished project import could not be repaired");
                        }
                        for (Account account : accounts) {
                            if (Thread.currentThread().isInterrupted() || session.isCancelled()) break;
                            SyncResult part = new SyncResult();
                            Bundle bundle = new Bundle(requested);
                            bundle.putBoolean(OfflineSyncIntentService.EXTRA_PROJECT_OPERATION_ALREADY_HELD, true);
                            bundle.putBoolean(com.nextgis.maplib.datasource.ngw.SyncAdapter.EXTRA_QUEUE_OWNS_LIFECYCLE, true);
                            try {
                                pass.perform(account, bundle, authority, provider, part);
                            } catch (RuntimeException error) {
                                AppDiagnostics.report(AppDiagnostics.Operation.SYNC, error);
                                SyncWorkspaceSession.markFailed(); part.stats.numIoExceptions++;
                                HyperLog.e(Constants.TAG, "Project account sync failed project=" + target.key(), error);
                            }
                            interrupted |= awaitChildren(session, lease);
                            boolean complete = !part.hasError() && !session.hasFailed()
                                    && !session.isCancelled() && !interrupted && !Thread.currentThread().isInterrupted()
                                    && !app.hasCollectorImportBatchRegistered();
                            if (complete && !SyncRecoveryJournal.complete(context, target, account.name)) {
                                part.stats.numIoExceptions++;
                                complete = false;
                            }
                            if (!complete && !failedProjects.contains(target.name)) failedProjects.add(target.name);
                            aggregate(result, part);
                            if (session.isCancelled() || interrupted) break;
                        }
                    }
                } catch (Exception error) {
                    if (error instanceof InterruptedException) interrupted = true;
                    else {
                        result.stats.numIoExceptions++;
                        if (!failedProjects.contains(target.name)) failedProjects.add(target.name);
                        HyperLog.e(Constants.TAG, "Project sync failed project=" + target.key(), error);
                        AppDiagnostics.report(AppDiagnostics.Operation.SYNC, error);
                    }
                } finally {
                    if (session != null) {
                        interrupted |= awaitChildren(session, lease);
                        session.close();
                    }
                    if (background && map != null) map.closeSyncWorkspace();
                    // Restore only runtime bookkeeping for the open project, retaining every journal.
                    try { onMain(app::prepareProjectSyncState); }
                    catch (Exception error) { result.stats.numIoExceptions++; }
                }
                if (interrupted || generation != NgwSyncIo.captureGeneration()) break;
            }
        } catch (Exception error) {
            result.stats.numIoExceptions++;
            HyperLog.e(Constants.TAG, "Project sync queue failed", error);
            AppDiagnostics.report(AppDiagnostics.Operation.SYNC, error);
        } finally {
            AppDiagnostics.operation(AppDiagnostics.Operation.SYNC,
                    interrupted ? AppDiagnostics.Phase.CANCELLED : AppDiagnostics.Phase.FINISHED);
            if (ownsProgress) {
                if (interrupted || generation != NgwSyncIo.captureGeneration()) NgwSyncProgress.cancel();
                else NgwSyncProgress.finishSession();
            }
            try {
                com.nextgis.maplib.service.NGWSyncService.markSyncFinished();
                String finish = interrupted || generation != NgwSyncIo.captureGeneration()
                        ? SyncAdapter.SYNC_CANCELED : SyncAdapter.SYNC_FINISH;
                context.sendBroadcast(new Intent(finish).setPackage(context.getPackageName()));
                notifications.sendNotification(context,
                        (result.hasError() || !failedProjects.isEmpty())
                                && !SyncAdapter.SYNC_CANCELED.equals(finish)
                                ? SyncAdapter.SYNC_CHANGES : finish, null);
            } finally {
                if (lease != null) lease.close();
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
        if (!failedProjects.isEmpty()) {
            result.stats.numIoExceptions = Math.max(1, result.stats.numIoExceptions);
            if (requested.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false)) {
                context.sendBroadcast(new Intent(Constants.MESSAGE_ALERT_INTENT)
                        .setPackage(context.getPackageName())
                        .putExtra(Constants.MESSAGE_TITLE_EXTRA, context.getString(R.string.project_sync_incomplete_title))
                        .putExtra(Constants.MESSAGE_EXTRA, context.getString(R.string.project_sync_incomplete,
                                android.text.TextUtils.join(", ", failedProjects))));
            }
        }
    }

    /** Keep the database lease until every queued callback, service and write has actually finished. */
    private static boolean awaitChildren(SyncWorkspaceSession session, ProjectOperationCoordinator.Lease lease) {
        boolean interrupted = Thread.interrupted();
        try (AutoCloseable ignored = ProjectOperationCoordinator.allowSyncDependents(session.getToken())) {
            while (!session.isQuiet()) {
                if (lease != null) lease.heartbeat();
                session.expireUndelivered();
                try { session.waitForProgress(); }
                catch (InterruptedException cancel) { interrupted = true; }
            }
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
        return interrupted;
    }

    private static void onMain(Runnable action) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) { action.run(); return; }
        FutureTask<Void> task = new FutureTask<>(action, null);
        if (!SyncWorkspaceSession.post(new Handler(Looper.getMainLooper()), task)) {
            throw new IOException("Cannot deliver project callback");
        }
        task.get();
    }

    private static void aggregate(SyncResult total, SyncResult part) {
        total.stats.numIoExceptions += part.stats.numIoExceptions;
        total.stats.numAuthExceptions += part.stats.numAuthExceptions;
        total.stats.numParseExceptions += part.stats.numParseExceptions;
        total.stats.numConflictDetectedExceptions += part.stats.numConflictDetectedExceptions;
        total.stats.numInserts += part.stats.numInserts;
        total.stats.numUpdates += part.stats.numUpdates;
        total.stats.numDeletes += part.stats.numDeletes;
        total.databaseError |= part.databaseError;
        total.tooManyDeletions |= part.tooManyDeletions;
        total.tooManyRetries |= part.tooManyRetries;
        total.delayUntil = Math.max(total.delayUntil, part.delayUntil);
    }
}
