package com.nextgis.mobile.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;

/**
 * Durable marker for an account sync that did not reach a clean finish.
 *
 * <p>Feature snapshot apply is transactional, so recovery does not resume in the middle of SQL.
 * Instead the next process reruns the account pass idempotently after the pre-sync integrity repair.
 * This marker closes the gap where the process died after Android considered the manual action
 * delivered and no new sync was pending.</p>
 */
public final class SyncRecoveryJournal {
    private static final String PREFS = "sync_recovery_journal";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_WORKSPACE = "workspace";
    private static final String KEY_MANUAL = "manual";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_ATTEMPT = "attempt";

    private SyncRecoveryJournal() {
    }

    public static boolean begin(Context context, String accountName, boolean manual) {
        if (context == null || TextUtils.isEmpty(accountName)) {
            return false;
        }
        SharedPreferences preferences = preferences(context);
        String workspace = ProjectOperationCoordinator.activeWorkspaceKey(context);
        int attempt = 1;
        if (preferences.getBoolean(KEY_ACTIVE, false)
                && accountName.equals(preferences.getString(KEY_ACCOUNT, ""))
                && workspace.equals(preferences.getString(KEY_WORKSPACE, ""))) {
            attempt = preferences.getInt(KEY_ATTEMPT, 0) + 1;
        }
        boolean saved = preferences.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_ACCOUNT, accountName)
                .putString(KEY_WORKSPACE, workspace)
                .putBoolean(KEY_MANUAL, manual)
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .putInt(KEY_ATTEMPT, attempt)
                .commit();
        HyperLog.i(Constants.TAG, "Sync recovery journal begin account=" + accountName
                + " workspace=" + workspace + " attempt=" + attempt + " saved=" + saved);
        return saved;
    }

    public static void complete(Context context, String accountName) {
        if (context == null || TextUtils.isEmpty(accountName)) {
            return;
        }
        SharedPreferences preferences = preferences(context);
        if (!preferences.getBoolean(KEY_ACTIVE, false)
                || !accountName.equals(preferences.getString(KEY_ACCOUNT, ""))) {
            return;
        }
        String currentWorkspace = ProjectOperationCoordinator.activeWorkspaceKey(context);
        if (!currentWorkspace.equals(preferences.getString(KEY_WORKSPACE, ""))) {
            return;
        }
        preferences.edit().clear().apply();
        HyperLog.i(Constants.TAG, "Sync recovery journal completed account=" + accountName);
    }

    /** Schedule one recovery pass after process restart when the same project is still active. */
    public static void schedulePendingIfNeeded(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences preferences = preferences(context);
        if (!preferences.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        String accountName = preferences.getString(KEY_ACCOUNT, "");
        String journalWorkspace = preferences.getString(KEY_WORKSPACE, "");
        String currentWorkspace = ProjectOperationCoordinator.activeWorkspaceKey(context);
        if (TextUtils.isEmpty(accountName) || !currentWorkspace.equals(journalWorkspace)) {
            HyperLog.w(Constants.TAG, "Sync recovery journal discarded after workspace change"
                    + " journal=" + journalWorkspace + " current=" + currentWorkspace);
            preferences.edit().clear().apply();
            return;
        }
        if (!(context.getApplicationContext() instanceof IGISApplication)) {
            return;
        }
        IGISApplication application = (IGISApplication) context.getApplicationContext();
        Account account = null;
        for (Account candidate : AccountManager.get(context)
                .getAccountsByType(application.getAccountsType())) {
            if (accountName.equals(candidate.name)) {
                account = candidate;
                break;
            }
        }
        if (account == null) {
            HyperLog.w(Constants.TAG, "Sync recovery journal account no longer exists: "
                    + accountName);
            preferences.edit().clear().apply();
            return;
        }

        Bundle extras = new Bundle();
        extras.putBoolean(
                ContentResolver.SYNC_EXTRAS_MANUAL,
                preferences.getBoolean(KEY_MANUAL, false));
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, false);
        ContentResolver.requestSync(account, application.getAuthority(), extras);
        HyperLog.w(Constants.TAG, "Sync recovery journal requested pending pass account="
                + accountName + " ageMs=" + Math.max(0L,
                System.currentTimeMillis() - preferences.getLong(KEY_STARTED_AT, 0L)));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
