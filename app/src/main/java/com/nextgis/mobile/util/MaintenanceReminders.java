package com.nextgis.mobile.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;

import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.api.INGWLayer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.util.ProjectSyncState;
import com.nextgis.mobile.BuildConfig;
import com.nextgis.mobile.R;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Advisory only: successful network checks and full project sync have separate clocks. */
final class MaintenanceReminders {
    static final String PREFERENCES = "maintenance-reminders";
    static final long WEEK_MS = TimeUnit.DAYS.toMillis(7);
    private static final Object LOCK = new Object();

    enum Update { NONE, DOWNLOAD, INSTALL }

    static final class Reminder {
        final boolean syncOverdue, syncUnknown, checkOverdue;
        final Update update;

        Reminder(boolean syncOverdue, boolean syncUnknown, boolean checkOverdue, Update update) {
            this.syncOverdue = syncOverdue;
            this.syncUnknown = syncUnknown;
            this.checkOverdue = checkOverdue;
            this.update = update;
        }

        boolean needed() { return syncOverdue || checkOverdue || update != Update.NONE; }

        String message(Context context) {
            StringBuilder text = new StringBuilder();
            if (update != Update.NONE) append(text, context.getString(update == Update.INSTALL
                    ? R.string.maintenance_install_overdue : R.string.maintenance_download_overdue));
            if (syncOverdue) append(text, context.getString(syncUnknown
                    ? R.string.maintenance_sync_unknown : R.string.maintenance_sync_overdue));
            if (checkOverdue && update != Update.DOWNLOAD) {
                append(text, context.getString(R.string.maintenance_check_overdue));
            }
            return text.toString();
        }
    }

    private MaintenanceReminders() { }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /** Called only after the branch manifest has passed all identity/security validation. */
    static void recordSuccessfulCheck(Context context, long version, long now) {
        synchronized (LOCK) {
            SharedPreferences prefs = preferences(context);
            SharedPreferences.Editor edit = prefs.edit().putLong("last_check", now);
            rememberAvailable(prefs, edit, version, now);
            edit.commit();
        }
    }

    /** Reading a validated local APK proves availability, never successful internet access. */
    static void recordReady(Context context, long version, long now) {
        synchronized (LOCK) {
            SharedPreferences prefs = preferences(context);
            if (version <= BuildConfig.VERSION_CODE) return;
            SharedPreferences.Editor edit = prefs.edit();
            rememberAvailable(prefs, edit, version, now);
            edit.commit();
        }
    }

    private static void rememberAvailable(SharedPreferences prefs, SharedPreferences.Editor edit,
                                          long version, long now) {
        if (version <= BuildConfig.VERSION_CODE) {
            edit.remove("available_version").remove("available_since");
        } else {
            long known = prefs.getLong("available_version", 0);
            // A newer release must not postpone an already overdue uninstalled update.
            if (known <= BuildConfig.VERSION_CODE || prefs.getLong("available_since", 0) <= 0) {
                edit.putLong("available_since", now);
            }
            edit.putLong("available_version", Math.max(version, known));
        }
    }

    static boolean hasOnlineLayers(LayerGroup group) {
        if (group == null) return false;
        for (ILayer layer : group.getLayers()) {
            if (layer instanceof INGWLayer) return true;
            if (layer instanceof LayerGroup && hasOnlineLayers((LayerGroup) layer)) return true;
        }
        return false;
    }

    /** Run on updater IO executor; canonical project clock is read without changing any map. */
    static Reminder read(Context context, String onlineProjectPath, long readyVersion, long now) {
        synchronized (LOCK) {
            SharedPreferences prefs = preferences(context);
            long observed = prefs.getLong("first_observed", 0);
            SharedPreferences.Editor edit = prefs.edit();
            if (observed <= 0) { observed = now; edit.putLong("first_observed", now); }
            boolean syncOverdue = false, syncUnknown = false;
            if (onlineProjectPath != null) {
                try {
                    String canonical = new File(onlineProjectPath).getCanonicalPath();
                    String key = "project_observed_" + UUID.nameUUIDFromBytes(
                            canonical.getBytes(StandardCharsets.UTF_8));
                    long firstProject = prefs.getLong(key, 0);
                    if (firstProject <= 0) { firstProject = now; edit.putLong(key, now); }
                    long complete = ProjectSyncState.lastComplete(context, canonical);
                    syncUnknown = complete <= 0;
                    syncOverdue = overdue(now, syncUnknown ? firstProject : complete);
                } catch (IOException ignored) {
                    // Without a reliable owning path, never claim that a project is stale.
                }
            }
            long known = prefs.getLong("available_version", 0);
            Update update = Update.NONE;
            if (known > BuildConfig.VERSION_CODE && overdue(now, prefs.getLong("available_since", 0))) {
                update = readyVersion >= known ? Update.INSTALL : Update.DOWNLOAD;
            } else if (known > 0 && known <= BuildConfig.VERSION_CODE) {
                edit.remove("available_version").remove("available_since");
            }
            edit.commit();
            long checked = prefs.getLong("last_check", 0);
            return new Reminder(syncOverdue, syncUnknown, overdue(now, checked > 0 ? checked : observed), update);
        }
    }

    static boolean overdue(long now, long since) {
        return since > 0 && now > since && now - since > WEEK_MS;
    }

    static boolean alreadyShown(Context context, String session) {
        SharedPreferences prefs = preferences(context);
        return session.equals(prefs.getString("shown_session", null))
                && !reviewDue(System.currentTimeMillis(), prefs.getLong("shown_at", 0));
    }

    static void markShown(Context context, String session) {
        preferences(context).edit().putString("shown_session", session)
                .putLong("shown_at", System.currentTimeMillis()).commit();
    }

    static boolean reviewDue(long now, long reviewed) {
        return reviewed > 0 && now >= reviewed && now - reviewed >= TimeUnit.DAYS.toMillis(1);
    }

    static AlertDialog dialog(Context context, Reminder reminder) {
        return scrollable(new AlertDialog.Builder(context)
                .setTitle(R.string.maintenance_title)
                .setMessage(reminder.message(context))
                .setPositiveButton(android.R.string.ok, null)
                .create());
    }

    /** Retained package API for updater/reminder consumers. */
    static AlertDialog scrollable(AlertDialog dialog) {
        return ScrollableAlertDialogs.prepare(dialog);
    }

    private static void append(StringBuilder text, String paragraph) {
        if (text.length() > 0) text.append("\n\n");
        text.append(paragraph);
    }
}
