package com.nextgis.mobile.util;

import static com.nextgis.maplib.datasource.ngw.SyncAdapter.ACTION_LPATH;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.ServiceInfo;
import android.content.ContentResolver;
import android.content.Intent;
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
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.mobile.datasource.SyncAdapter;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;

import java.util.ArrayList;
import java.util.List;
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



    private static final String ACTION_ACCOUNT_NAME = "com.nextgis.mobile.util.action.ACCOUNTNAME";
    private static final String EXTRA_OPERATION_RESERVATION =
            "com.nextgis.mobile.extra.OPERATION_RESERVATION";
    private static final ConcurrentHashMap<String, ProjectOperationCoordinator.Lease>
            PENDING_OPERATION_LEASES = new ConcurrentHashMap<>();


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
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SYNC_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_sync)
                .setContentTitle(getString(com.nextgis.maplib.R.string.synchronization))
                .setContentText(getString(com.nextgis.maplib.R.string.sync_progress))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    SYNC_NOTIFICATION_ID,
                    builder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SYNC_NOTIFICATION_ID, builder.build());
        }
    }

    public static boolean startActionFoo(Context context) {
        return startActionFoo(context, null);
    }

    public static boolean startActionFoo(Context context, String lpath) {
        ProjectOperationCoordinator.Lease operationLease =
                ProjectOperationCoordinator.tryBegin(
                        context, ProjectOperationCoordinator.Kind.DATA_SYNC);
        if (operationLease == null) {
            return false;
        }
        String reservation = UUID.randomUUID().toString();
        PENDING_OPERATION_LEASES.put(reservation, operationLease);
        Intent intent = new Intent(context, OfflineSyncIntentService.class);
        intent.setAction(ACTION_OFFSYNC);
        if (lpath != null) {
            intent.putExtra(ACTION_LPATH, lpath);
        }
        intent.putExtra(EXTRA_MANUAL_SYNC, true);
        intent.putExtra(EXTRA_OPERATION_RESERVATION, reservation);
        try {
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (RuntimeException e) {
            ProjectOperationCoordinator.Lease pending =
                    PENDING_OPERATION_LEASES.remove(reservation);
            if (pending != null) {
                pending.close();
            }
            throw e;
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
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
                handleActionFoo(lpath, manual, reservation);
            }
        }
    }

    private void handleActionFoo(
            String lpath,
            boolean manualSync,
            String operationReservation) {
        ProjectOperationCoordinator.Lease operationLease = operationReservation != null
                ? PENDING_OPERATION_LEASES.remove(operationReservation) : null;
        if (operationLease == null) {
            operationLease = ProjectOperationCoordinator.tryBegin(
                    this, ProjectOperationCoordinator.Kind.DATA_SYNC);
        }
        if (operationLease == null) {
            HyperLog.v(Constants.TAG,
                    "OfflineSyncIntentService skipped: project operation in progress");
            return;
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

            Bundle bundle = new Bundle();
            if (lpath != null) {
                bundle.putString(ACTION_LPATH, lpath);
            }
            bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, manualSync);
            bundle.putBoolean(EXTRA_PROJECT_OPERATION_ALREADY_HELD, true);
            for (Account account : mAccounts) {
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
            operationLease.close();
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
