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
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.nextgis.maplib.util.ProdLogUtil;

/** Durable pending project/account pairs, never credentials or feature data. */
public final class SyncRecoveryJournal {
    private static final String PREFS = "sync_recovery_journal";
    private static final String QUEUE = "pending_v2";
    private static final String[] KEYS = {QUEUE, "active", "account", "workspace", "manual", "started_at", "attempt"};
    private static final Object STATE_LOCK = new Object();
    private static final ReferenceQueue<SharedPreferences> COLLECTED = new ReferenceQueue<>();
    private static final Map<PreferenceOwner, OwnerState> OWNERS = new HashMap<>();
    private SyncRecoveryJournal() { }

    private static final class PreferenceOwner extends WeakReference<SharedPreferences> {
        private final int hash;
        PreferenceOwner(SharedPreferences preferences, ReferenceQueue<SharedPreferences> queue) {
            super(preferences, queue);
            hash = System.identityHashCode(preferences);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PreferenceOwner)) return false;
            SharedPreferences preferences = get();
            return preferences != null && preferences == ((PreferenceOwner) other).get();
        }
    }

    private static final class OwnerState {
        Cohort view;
        boolean unconfirmedHistory;
    }

    /** Only owned keys; raw types and absence survive without eager legacy interpretation. */
    private static final class Cohort {
        final Map<String, Object> values;
        Cohort(Map<String, ?> source) {
            Map<String, Object> copy = new HashMap<>();
            for (String key : KEYS) if (source.containsKey(key)) copy.put(key, copyValue(source.get(key)));
            values = Collections.unmodifiableMap(copy);
        }
        private static Object copyValue(Object value) {
            if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer
                    || value instanceof Long || value instanceof Float) return value;
            if (value instanceof Set<?>) {
                Set<String> copy = new HashSet<>();
                for (Object item : (Set<?>) value) {
                    if (item != null && !(item instanceof String)) throw new ClassCastException("Invalid journal string set");
                    copy.add((String) item);
                }
                return Collections.unmodifiableSet(copy);
            }
            throw new ClassCastException("Unsupported journal preference value");
        }
        String string(String key, String fallback) {
            Object value = values.get(key); return value == null ? fallback : (String) value;
        }
        boolean bool(String key, boolean fallback) {
            Object value = values.get(key); return value == null ? fallback : (Boolean) value;
        }
        long number(String key, long fallback) {
            Object value = values.get(key); return value == null ? fallback : (Long) value;
        }
        JSONArray queue() throws JSONException { return new JSONArray(string(QUEUE, "[]")); }
        Cohort after(JSONArray queue, boolean removeActive, boolean removeLegacy) {
            Map<String, Object> next = new HashMap<>(values);
            next.put(QUEUE, queue.toString());
            if (removeActive || removeLegacy) next.remove("active");
            if (removeLegacy) for (String key : new String[]{"account", "workspace", "manual", "started_at", "attempt"}) next.remove(key);
            return new Cohort(next);
        }
        @SuppressWarnings("unchecked") void stage(SharedPreferences.Editor editor) {
            for (String key : KEYS) {
                if (!values.containsKey(key)) { editor.remove(key); continue; }
                Object value = values.get(key);
                if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                else if (value instanceof Set<?>) editor.putStringSet(key, (Set<String>) value);
                // Editor has no operation preserving a present null; do not silently remove it.
                else throw new ClassCastException("Unwritable journal preference value");
            }
        }
    }

    private static OwnerState owner(SharedPreferences preferences) {
        synchronized (STATE_LOCK) {
            PreferenceOwner collected;
            while ((collected = (PreferenceOwner) COLLECTED.poll()) != null) OWNERS.remove(collected);
            PreferenceOwner lookup = new PreferenceOwner(preferences, null);
            OwnerState state = OWNERS.get(lookup);
            if (state == null) {
                state = new OwnerState();
                OWNERS.put(new PreferenceOwner(preferences, COLLECTED), state);
            }
            return state;
        }
    }

    private static Cohort read(SharedPreferences preferences) {
        OwnerState state = owner(preferences);
        synchronized (STATE_LOCK) { if (state.view != null) return state.view; }
        Cohort raw;
        try { raw = new Cohort(preferences.getAll()); }
        catch (RuntimeException error) {
            synchronized (STATE_LOCK) { if (state.view != null) return state.view; }
            throw error;
        }
        // A writer may have installed a view while the raw cache was being read.
        synchronized (STATE_LOCK) { return state.view == null ? raw : state.view; }
    }

    /** Existing synchronized mutators serialize writes; readers never wait for that monitor. */
    private static boolean write(SharedPreferences preferences, Cohort before, Cohort after) throws IOException {
        OwnerState state = owner(preferences);
        synchronized (STATE_LOCK) { state.view = before; }
        boolean committed = false;
        try {
            SharedPreferences.Editor editor = preferences.edit();
            after.stage(editor);
            committed = SyncJournalCommit.commit(editor::commit);
            return committed;
        } finally {
            synchronized (STATE_LOCK) {
                if (!committed) state.unconfirmedHistory = true;
                // Never drop a sticky view after later success: old raw readers may still be in flight.
                state.view = committed ? (state.unconfirmedHistory ? after : null) : before;
            }
        }
    }

    static final class PersistenceFailure extends IOException {
        final java.util.Map<String, Object> diagnostics;
        PersistenceFailure(String message, Throwable cause, java.util.Map<String, Object> diagnostics) {
            super(message, cause);
            this.diagnostics = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(diagnostics));
        }
    }

    public static synchronized boolean enqueue(Context context, List<ProjectSyncRunner.Target> targets,
                                               List<Account> accounts, boolean manual) {
        try {
            enqueueOrThrow(context, targets, accounts, manual);
            return true;
        } catch (IOException error) {
            HyperLog.e(Constants.TAG, ProdLogUtil.withStack("Cannot persist sync recovery queue", error));
            return false;
        }
    }

    /** Preserve the real failure for the runner's diagnostic report, never replace it with a boolean. */
    public static synchronized void enqueueOrThrow(Context context, List<ProjectSyncRunner.Target> targets,
                                                  List<Account> accounts, boolean manual) throws IOException {
        String phase = "read_queue";
        int beforeCount = -1, afterCount = -1, bytes = -1;
        boolean interruptedAtEntry = Thread.currentThread().isInterrupted();
        try {
            SharedPreferences preferences = preferences(context);
            Cohort before = read(preferences);
            JSONArray queue = before.queue();
            beforeCount = queue.length();
            phase = "legacy_inventory";
            boolean migrated = before.bool("active", false)
                    && migrateLegacy(before, queue, ProjectSyncRunner.inventory(context));
            phase = "merge_pairs";
            for (ProjectSyncRunner.Target target : targets) for (Account account : accounts) {
                JSONObject existing = find(queue, target.key(), account.name);
                if (existing != null) {
                    existing.put("manual", existing.optBoolean("manual") || manual);
                } else queue.put(new JSONObject().put("target", target.key()).put("account", account.name)
                        .put("manual", manual).put("started_at", System.currentTimeMillis()));
            }
            String payload = queue.toString();
            afterCount = queue.length();
            bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            phase = "commit";
            if (!write(preferences, before, before.after(queue, migrated, migrated)))
                throw new IOException("SharedPreferences.commit returned false");
        } catch (Exception error) {
            long available = -1;
            try { available = context.getFilesDir().getUsableSpace(); } catch (RuntimeException ignored) { }
            java.util.Map<String, Object> diagnostics = new java.util.LinkedHashMap<>();
            diagnostics.put("phase", phase);
            diagnostics.put("projects", targets == null ? -1 : targets.size());
            diagnostics.put("accounts", accounts == null ? -1 : accounts.size());
            diagnostics.put("before", beforeCount); diagnostics.put("after", afterCount);
            diagnostics.put("bytes", bytes); diagnostics.put("storage_available_bytes", available);
            diagnostics.put("caller_interrupted_at_entry", interruptedAtEntry);
            diagnostics.put("caller_interrupted", Thread.currentThread().isInterrupted());
            diagnostics.put("write_mode", "owned_thread");
            throw new PersistenceFailure("Cannot persist sync queue phase=" + phase
                    + " projects=" + (targets == null ? -1 : targets.size())
                    + " accounts=" + (accounts == null ? -1 : accounts.size())
                    + " before=" + beforeCount + " after=" + afterCount + " bytes=" + bytes
                    + " storageAvailableBytes=" + available
                    + " callerInterruptedAtEntry=" + interruptedAtEntry
                    + " callerInterrupted=" + Thread.currentThread().isInterrupted()
                    + " writeMode=owned_thread", error, diagnostics);
        } finally {
            if (interruptedAtEntry) Thread.currentThread().interrupt();
        }
    }

    public static synchronized boolean complete(Context context, ProjectSyncRunner.Target target, String account) {
        boolean interruptedAtEntry = Thread.currentThread().isInterrupted();
        try {
            SharedPreferences preferences = preferences(context);
            Cohort before = read(preferences);
            JSONArray queue = before.queue(), after = new JSONArray();
            for (int i = 0; i < queue.length(); i++) {
                JSONObject entry = queue.getJSONObject(i);
                if (!target.key().equals(entry.getString("target")) || !account.equals(entry.getString("account"))) {
                    after.put(entry);
                }
            }
            return write(preferences, before, before.after(after, false, false));
        } catch (Exception error) {
            HyperLog.e(Constants.TAG, "Cannot acknowledge completed sync pair", error); return false;
        } finally { if (interruptedAtEntry) Thread.currentThread().interrupt(); }
    }

    /** Resolve recorded identities from the registry, even after opening another project. */
    public static synchronized void schedulePendingIfNeeded(Context context) {
        boolean interruptedAtEntry = Thread.currentThread().isInterrupted();
        try {
            IGISApplication app = (IGISApplication) context.getApplicationContext();
            List<ProjectSyncRunner.Target> targets = ProjectSyncRunner.inventory(context);
            SharedPreferences preferences = preferences(context);
            Cohort before = read(preferences);
            JSONArray queue = before.queue();
            boolean migrated = migrateLegacy(before, queue, targets);
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
            if (!write(preferences, before, before.after(retained, migrated, false))) return;
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
        } finally { if (interruptedAtEntry) Thread.currentThread().interrupt(); }
    }

    static JSONArray load(Context context) throws JSONException {
        return read(preferences(context)).queue();
    }

    private static JSONObject find(JSONArray queue, String target, String account) throws JSONException {
        for (int i = 0; i < queue.length(); i++) {
            JSONObject entry = queue.getJSONObject(i);
            if (target.equals(entry.getString("target")) && account.equals(entry.getString("account"))) return entry;
        }
        return null;
    }

    private static boolean migrateLegacy(Cohort prefs, JSONArray queue, List<ProjectSyncRunner.Target> targets)
            throws JSONException {
        if (!prefs.bool("active", false)) return false;
        for (ProjectSyncRunner.Target target : targets) {
            if ((target.uid + "|" + target.path).equals(prefs.string("workspace", ""))) {
                String account = prefs.string("account", "");
                if (!account.isEmpty() && find(queue, target.key(), account) == null) {
                    queue.put(new JSONObject().put("target", target.key()).put("account", account)
                            .put("manual", prefs.bool("manual", false))
                            .put("started_at", prefs.number("started_at", 0L)));
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
