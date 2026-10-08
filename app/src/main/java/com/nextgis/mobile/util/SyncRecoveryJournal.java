package com.nextgis.mobile.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.util.Constants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

/** Durable pending project/account pairs, never credentials or feature data. */
public final class SyncRecoveryJournal {
    private static final String PREFS = "sync_recovery_journal";
    private static final String QUEUE = "pending_v2";
    private SyncRecoveryJournal() { }

    public static synchronized boolean enqueue(Context context, List<ProjectSyncRunner.Target> targets,
                                               List<Account> accounts, boolean manual) {
        try {
            JSONArray queue = load(context);
            boolean migrated = migrateLegacy(context, queue, ProjectSyncRunner.inventory(context));
            for (ProjectSyncRunner.Target target : targets) for (Account account : accounts) {
                JSONObject existing = find(queue, target.key(), account.name);
                if (existing != null) {
                    existing.put("manual", existing.optBoolean("manual") || manual);
                } else queue.put(new JSONObject().put("target", target.key()).put("account", account.name)
                        .put("manual", manual).put("started_at", System.currentTimeMillis()));
            }
            SharedPreferences.Editor editor = preferences(context).edit().putString(QUEUE, queue.toString());
            if (migrated) editor.remove("active").remove("account").remove("workspace").remove("manual")
                    .remove("started_at").remove("attempt");
            return editor.commit();
        } catch (Exception error) {
            HyperLog.e(Constants.TAG, "Cannot persist sync recovery queue", error); return false;
        }
    }

    public static synchronized boolean complete(Context context, ProjectSyncRunner.Target target, String account) {
        try {
            JSONArray before = load(context), after = new JSONArray();
            for (int i = 0; i < before.length(); i++) {
                JSONObject entry = before.getJSONObject(i);
                if (!target.key().equals(entry.getString("target")) || !account.equals(entry.getString("account"))) {
                    after.put(entry);
                }
            }
            return preferences(context).edit().putString(QUEUE, after.toString()).commit();
        } catch (Exception error) {
            HyperLog.e(Constants.TAG, "Cannot acknowledge completed sync pair", error); return false;
        }
    }

    /** Resolve recorded identities from the registry, even after opening another project. */
    public static synchronized void schedulePendingIfNeeded(Context context) {
        try {
            IGISApplication app = (IGISApplication) context.getApplicationContext();
            List<ProjectSyncRunner.Target> targets = ProjectSyncRunner.inventory(context);
            JSONArray queue = load(context);
            boolean migrated = migrateLegacy(context, queue, targets);
            JSONArray retained = new JSONArray();
            for (int i = 0; i < queue.length(); i++) {
                JSONObject entry = queue.getJSONObject(i);
                ProjectSyncRunner.Target target = null;
                for (ProjectSyncRunner.Target candidate : targets) {
                    if (candidate.key().equals(entry.getString("target"))) { target = candidate; break; }
                }
                Account account = null;
                for (Account candidate : AccountManager.get(context).getAccountsByType(app.getAccountsType())) {
                    if (candidate.name.equals(entry.getString("account"))) { account = candidate; break; }
                }
                if (target == null || account == null) {
                    HyperLog.w(Constants.TAG, "Sync recovery pair retired: project/account was removed");
                    continue;
                }
                retained.put(entry);
            }
            SharedPreferences.Editor editor = preferences(context).edit().putString(QUEUE, retained.toString());
            if (migrated) editor.remove("active");
            if (!editor.commit()) return;
            for (int i = 0; i < retained.length(); i++) {
                JSONObject entry = retained.getJSONObject(i);
                Account account = new Account(entry.getString("account"), app.getAccountsType());
                boolean manual = entry.optBoolean("manual");
                if (!manual && !ContentResolver.getSyncAutomatically(account, app.getAuthority())) continue;
                Bundle extras = new Bundle();
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, manual);
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, false);
                extras.putString(ProjectSyncRunner.EXTRA_RECOVERY_TARGET, entry.getString("target"));
                ContentResolver.requestSync(account, app.getAuthority(), extras);
            }
        } catch (Exception error) {
            HyperLog.e(Constants.TAG, "Cannot restore pending project syncs; journal retained", error);
        }
    }

    static JSONArray load(Context context) throws JSONException {
        return new JSONArray(preferences(context).getString(QUEUE, "[]"));
    }

    private static JSONObject find(JSONArray queue, String target, String account) throws JSONException {
        for (int i = 0; i < queue.length(); i++) {
            JSONObject entry = queue.getJSONObject(i);
            if (target.equals(entry.getString("target")) && account.equals(entry.getString("account"))) return entry;
        }
        return null;
    }

    private static boolean migrateLegacy(Context context, JSONArray queue, List<ProjectSyncRunner.Target> targets)
            throws JSONException {
        SharedPreferences prefs = preferences(context);
        if (!prefs.getBoolean("active", false)) return false;
        for (ProjectSyncRunner.Target target : targets) {
            if ((target.uid + "|" + target.path).equals(prefs.getString("workspace", ""))) {
                String account = prefs.getString("account", "");
                if (!account.isEmpty() && find(queue, target.key(), account) == null) {
                    queue.put(new JSONObject().put("target", target.key()).put("account", account)
                            .put("manual", prefs.getBoolean("manual", false))
                            .put("started_at", prefs.getLong("started_at", 0L)));
                }
                return true;
            }
        }
        return false;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
