package com.nextgis.mobile.util;

import static com.nextgis.maplib.datasource.ngw.SyncAdapter.ACTION_LPATH;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.Context;
import android.content.PeriodicSync;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.INGWLayer;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.map.CollectorProjectMetadata;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.mobile.datasource.SyncAdapter;

import java.util.ArrayList;
import java.util.List;

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



    private static final String ACTION_ACCOUNT_NAME = "com.nextgis.mobile.util.action.ACCOUNTNAME";


    public OfflineSyncIntentService() {
        super("OfflineSyncIntentService");
    }

    public static void startActionFoo(Context context) {
        startActionFoo(context, null);
    }

    public static void startActionFoo(Context context, String lpath) {
        Intent intent = new Intent(context, OfflineSyncIntentService.class);
        intent.setAction(ACTION_OFFSYNC);
        if (lpath != null) {
            intent.putExtra(ACTION_LPATH, lpath);
        }
        intent.putExtra(EXTRA_MANUAL_SYNC, true);
        context.startService(intent);
    }

    /** When {@code true}, sync errors are shown to the user (button / toast). */
    public static final String EXTRA_MANUAL_SYNC = "com.nextgis.mobile.extra.MANUAL_SYNC";

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
                handleActionFoo(lpath, manual);
            }
        }
    }

    private void handleActionFoo(String lpath, boolean manualSync) {
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
