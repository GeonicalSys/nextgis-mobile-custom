package com.nextgis.mobile.util;

import static com.nextgis.maplib.datasource.ngw.SyncAdapter.ACTION_LPATH;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.PeriodicSync;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.INGWLayer;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.map.CollectorProjectMetadata;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.NgwSyncProgress;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.mobile.datasource.SyncAdapter;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class OfflineSyncIntentService extends IntentService {

    private static final String ACTION_OFFSYNC = "com.nextgis.mobile.util.action.OFFSYNC";
    private static final String SYNC_CHANNEL_ID = "manual_sync_fgs";
    private static final int SYNC_NOTIFICATION_ID = 519;
    private BroadcastReceiver mProgressReceiver;
    private PendingIntent mSyncContentIntent;



    private static final String ACTION_ACCOUNT_NAME = "com.nextgis.mobile.util.action.ACCOUNTNAME";
    private static final String EXTRA_OPERATION_RESERVATION =
            "com.nextgis.mobile.extra.OPERATION_RESERVATION";
    private static final class PendingOperation {
        final ProjectOperationCoordinator.Lease lease;
        final ProjectOperationCoordinator.CancelRegistration cancelRegistration;
        final long cancellationGeneration;

        PendingOperation(
                ProjectOperationCoordinator.Lease lease,
                ProjectOperationCoordinator.CancelRegistration cancelRegistration,
                long cancellationGeneration) {
            this.lease = lease;
            this.cancelRegistration = cancelRegistration;
            this.cancellationGeneration = cancellationGeneration;
        }

        void close() {
            lease.close();
            cancelRegistration.close();
        }
    }

    private static final ConcurrentHashMap<String, PendingOperation>
            PENDING_OPERATION_LEASES = new ConcurrentHashMap<>();
    private static final Object CANCEL_LOCK = new Object();
    private static Thread activeManualWorker;
    private static long cancellationGeneration;

    private static void requestCancellation() {
        synchronized (CANCEL_LOCK) {
            cancellationGeneration++;
            for (Map.Entry<String, PendingOperation> entry
                    : PENDING_OPERATION_LEASES.entrySet()) {
                PendingOperation pending = PENDING_OPERATION_LEASES.remove(entry.getKey());
                if (pending != null) {
                    pending.close();
                }
            }
            if (activeManualWorker != null) {
                activeManualWorker.interrupt();
            }
        }
    }


    public OfflineSyncIntentService() {
        super("OfflineSyncIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    SYNC_CHANNEL_ID,
                    getString(com.nextgis.maplibui.R.string.sync),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mSyncContentIntent = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = buildSyncNotification(NgwSyncProgress.snapshot());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    SYNC_NOTIFICATION_ID,
                    builder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SYNC_NOTIFICATION_ID, builder.build());
        }
        mProgressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !NgwSyncProgress.SYNC_PROGRESS.equals(intent.getAction())) {
                    return;
                }
                updateSyncNotification(NgwSyncProgress.snapshot());
            }
        };
        IntentFilter progressFilter = new IntentFilter(NgwSyncProgress.SYNC_PROGRESS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mProgressReceiver, progressFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mProgressReceiver, progressFilter);
        }
    }

    public static boolean startActionFoo(Context context) {
        return startActionFoo(context, null);
    }

    public static boolean startActionFoo(Context context, String lpath) {
        return startActionFoo(context, lpath, false);
    }

    public static boolean startActionFoo(Context context, String lpath, boolean forceRecheck) {
        long operationGeneration;
        synchronized (CANCEL_LOCK) {
            operationGeneration = cancellationGeneration;
        }
        ProjectOperationCoordinator.CancelRegistration cancelRegistration =
                ProjectOperationCoordinator.registerDataSyncCancelHandler(
                        OfflineSyncIntentService::requestCancellation);
        ProjectOperationCoordinator.Lease operationLease =
                ProjectOperationCoordinator.tryBegin(
                        context, ProjectOperationCoordinator.Kind.DATA_SYNC);
        if (operationLease == null) {
            cancelRegistration.close();
            return false;
        }
        String reservation = UUID.randomUUID().toString();
        synchronized (CANCEL_LOCK) {
            if (operationGeneration != cancellationGeneration) {
                operationLease.close();
                cancelRegistration.close();
                return false;
            }
            PENDING_OPERATION_LEASES.put(
                    reservation, new PendingOperation(
                            operationLease, cancelRegistration, operationGeneration));
        }
        Intent intent = new Intent(context, OfflineSyncIntentService.class);
        intent.setAction(ACTION_OFFSYNC);
        if (lpath != null) {
            intent.putExtra(ACTION_LPATH, lpath);
        }
        intent.putExtra(EXTRA_MANUAL_SYNC, true);
        intent.putExtra(com.nextgis.maplib.datasource.ngw.SyncAdapter.EXTRA_RECHECK_SKIPPED,
                forceRecheck);
        intent.putExtra(EXTRA_OPERATION_RESERVATION, reservation);
        try {
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (RuntimeException e) {
            PendingOperation pending = PENDING_OPERATION_LEASES.remove(reservation);
            if (pending != null) {
                pending.close();
            }
            throw e;
        }
    }

    @Override
    public void onDestroy() {
        if (mProgressReceiver != null) {
            unregisterReceiver(mProgressReceiver);
            mProgressReceiver = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    private NotificationCompat.Builder buildSyncNotification(NgwSyncProgress.Snapshot snapshot) {
        boolean determinate = snapshot != null && snapshot.determinate && snapshot.active;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SYNC_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_sync)
                .setContentTitle(getString(com.nextgis.maplib.R.string.synchronization))
                .setContentText(getString(com.nextgis.maplib.R.string.sync_progress))
                .setContentIntent(mSyncContentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (determinate) {
            builder.setProgress(snapshot.total, snapshot.done, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder;
    }

    private void updateSyncNotification(NgwSyncProgress.Snapshot snapshot) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(SYNC_NOTIFICATION_ID, buildSyncNotification(snapshot).build());
        }
    }

    /** When {@code true}, sync errors are shown to the user (button / toast). */
    public static final String EXTRA_MANUAL_SYNC = "com.nextgis.mobile.extra.MANUAL_SYNC";
    /** Internal: the serial manual-sync runner already owns the project operation lease. */
    public static final String EXTRA_PROJECT_OPERATION_ALREADY_HELD =
            "com.nextgis.mobile.extra.PROJECT_OPERATION_ALREADY_HELD";

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_OFFSYNC.equals(action)) {
                String lpath = null;
                if (intent.hasExtra(ACTION_LPATH)) {
                    lpath = intent.getStringExtra(ACTION_LPATH);
                }
                boolean manual = intent.getBooleanExtra(EXTRA_MANUAL_SYNC, true);
                String reservation = intent.getStringExtra(EXTRA_OPERATION_RESERVATION);
                handleActionFoo(lpath, manual, reservation, intent.getBooleanExtra(
                        com.nextgis.maplib.datasource.ngw.SyncAdapter.EXTRA_RECHECK_SKIPPED, false));
            }
        }
    }

    private void handleActionFoo(
            String lpath,
            boolean manualSync,
            String operationReservation, boolean forceRecheck) {
        ProjectOperationCoordinator.Lease operationLease;
        ProjectOperationCoordinator.CancelRegistration cancelRegistration;
        long operationGeneration;
        synchronized (CANCEL_LOCK) {
            PendingOperation pending = operationReservation != null
                    ? PENDING_OPERATION_LEASES.remove(operationReservation)
                    : null;
            operationLease = pending != null
                    ? pending.lease
                    : operationReservation == null
                            ? ProjectOperationCoordinator.tryBegin(
                                    this, ProjectOperationCoordinator.Kind.DATA_SYNC)
                            : null;
            cancelRegistration = pending != null
                    ? pending.cancelRegistration
                    : operationLease != null
                            ? ProjectOperationCoordinator.registerDataSyncCancelHandler(
                                    OfflineSyncIntentService::requestCancellation)
                            : null;
            operationGeneration = pending != null
                    ? pending.cancellationGeneration
                    : cancellationGeneration;
            // A missing reservation was canceled before the service began. Never reacquire it.
            if (operationLease == null) {
                HyperLog.v(Constants.TAG,
                        "OfflineSyncIntentService skipped: reservation canceled or project busy");
                if (operationReservation != null) {
                    sendBroadcast(new Intent(SyncAdapter.SYNC_CANCELED)
                            .setPackage(getPackageName()));
                }
                return;
            }
            if (operationGeneration != cancellationGeneration) {
                operationLease.close();
                if (cancelRegistration != null) {
                    cancelRegistration.close();
                }
                return;
            }
            activeManualWorker = Thread.currentThread();
        }
        try {
            Log.d("SSYNC", "OfflineSyncIntentService handleActionFoo lpath=" + lpath
                    + " manual=" + manualSync);
            List<Account> mAccounts = new ArrayList<>();
            final AccountManager accountManager = AccountManager.get(getApplicationContext());
            final IGISApplication application = (IGISApplication) getApplication();
            List<INGWLayer> layers = new ArrayList<>();

            for (Account account : accountManager.getAccountsByType(application.getAccountsType())) {
                List<PeriodicSync> periodicSyncsList = ContentResolver.getPeriodicSyncs(account, ((IGISApplication) getApplication()).getAuthority());
                Log.d("SSYNC", "Number of sync for: " + account.name);
                Log.d("SSYNC", "Number of sync: " + periodicSyncsList.size());
                for (PeriodicSync p : periodicSyncsList) {
                    Log.d("SSYNC", "period: " + p.period + " sec, Extras: " + p.extras);
                    for (String key : p.extras.keySet()) {
                        Object value = p.extras.get(key);
                        Log.d("SSYNC", "Key: " + key + ", Value: " + value + " (" + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                    }
                }

                layers.clear();
                MapContentProviderHelper.getLayersByAccount(application.getMap(), account.name, layers);
                Log.d("SSYNC", "OfflineSyncIntentService account=" + account.name
                        + " ngwLayerCount=" + layers.size());

                if (layers.size() > 0)
                    mAccounts.add(account);
            }
            Log.d("SSYNC", "OfflineSyncIntentService accounts queued=" + mAccounts.size()
                    + " manual=" + manualSync + " lpath=" + lpath);
            prioritizeActiveCollectorAccount(application, mAccounts);
            if (!mAccounts.isEmpty()) {
                NgwSyncProgress.beginSession(this, mAccounts.size());
            }

            Bundle bundle = new Bundle();
            if (lpath != null) {
                bundle.putString(ACTION_LPATH, lpath);
            }
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, manualSync);
            bundle.putBoolean(EXTRA_PROJECT_OPERATION_ALREADY_HELD, true);
            bundle.putBoolean(com.nextgis.maplib.datasource.ngw.SyncAdapter.EXTRA_RECHECK_SKIPPED,
                    forceRecheck);
            for (Account account : mAccounts) {
                if (Thread.currentThread().isInterrupted()
                        || isCancellationRequested(operationGeneration)) break;
                try {
                    // SyncResult and SyncAdapter carry per-run state. Reusing either
                    // leaked errors/cancellation from one account into the next one.
                    SyncResult syncResult = new SyncResult();
                    SyncAdapter syncAdapter = new SyncAdapter(getApplicationContext(), true);
                    Log.d("SSYNC", "onPerformSync call for: " + account.name);
                    syncAdapter.onPerformSync(account,
                            new Bundle(bundle),
                            com.nextgis.mobile.util.AppSettingsConstants.AUTHORITY,
                            null, syncResult);
                    Log.d("SSYNC", "onPerformSync finished for: " + account.name
                            + " hasError=" + syncResult.hasError()
                            + " stats=" + syncResult.stats);
                } catch (Exception accountError) {
                    // A broken secondary account must not suppress the remaining
                    // project accounts in this manual sync run.
                    Log.e("SSYNC", "Account sync failed: " + account.name, accountError);
                    HyperLog.e(Constants.TAG,
                            "OfflineSyncIntentService account failed: " + account.name,
                            accountError);
                }
            }
        } catch (Exception e) {
            Log.e("SSYNC", "handleActionFoo failed: " + e.getMessage(), e);
            HyperLog.e(Constants.TAG, "OfflineSyncIntentService.handleActionFoo crash: " + e.getMessage(), e);
        } finally {
            boolean canceled;
            synchronized (CANCEL_LOCK) {
                canceled = operationGeneration != cancellationGeneration;
                activeManualWorker = null;
            }
            operationLease.close();
            if (cancelRegistration != null) {
                cancelRegistration.close();
            }
            if (canceled) {
                NgwSyncProgress.cancel();
                sendBroadcast(new Intent(SyncAdapter.SYNC_CANCELED).setPackage(getPackageName()));
            } else {
                NgwSyncProgress.finishSession();
            }
            // IntentService reuses its handler thread for later intents.
            Thread.interrupted();
        }
    }

    private static boolean isCancellationRequested(long operationGeneration) {
        synchronized (CANCEL_LOCK) {
            return operationGeneration != cancellationGeneration;
        }
    }

    private void prioritizeActiveCollectorAccount(
            IGISApplication application,
            List<Account> accounts) {
        if (application == null || application.getMap() == null || accounts.size() < 2) {
            return;
        }

        CollectorProjectMetadata metadata = application.getMap().getCollectorProjectMetadata();
        if (metadata == null || !metadata.isValid()) {
            return;
        }

        String activeAccountName = metadata.getAccountName();
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            if (activeAccountName.equals(account.name)) {
                if (i > 0) {
                    accounts.remove(i);
                    accounts.add(0, account);
                }
                Log.d("SSYNC", "Active Collector account prioritized: " + account.name);
                return;
            }
        }
    }

}
