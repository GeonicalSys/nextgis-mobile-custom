package com.nextgis.mobile.usage;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.mobile.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Event timestamps only. No foreground duration, heartbeat or background-end event is collected. */
public final class UsageAnalytics implements Application.ActivityLifecycleCallbacks {
    private static UsageAnalytics instance;
    private final Application application;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "mobile-usage"));
    private final Set<Activity> visible = Collections.newSetFromMap(new IdentityHashMap<>());
    private final String session = UUID.randomUUID().toString();
    private final UsageOpenPolicy policy = new UsageOpenPolicy();
    private UsageStore store;
    private FileObserver observer;
    private final Runnable hidden = () -> policy.hide();
    private final Runnable inventoryChanged = () -> record(null, false);
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences = (prefs, name) -> {
        if (SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID.equals(name)) {
            main.post(() -> { if (policy.isForeground()) record("project_open", false); else record(null, false); });
        }
    };

    private UsageAnalytics(Application application) { this.application = application; }

    public static boolean enabled() {
        return !BuildConfig.DEBUG && !BuildConfig.FIELD_VERIFICATION && !BuildConfig.USAGE_ENDPOINT.isEmpty()
                && !BuildConfig.USAGE_UPLOAD_TOKEN.isEmpty();
    }

    public static synchronized void start(Application application) {
        if (!enabled() || instance != null) return;
        instance = new UsageAnalytics(application);
        try {
        application.registerActivityLifecycleCallbacks(instance);
        PreferenceManager.getDefaultSharedPreferences(application).registerOnSharedPreferenceChangeListener(instance.preferences);
        AccountManager.get(application).addOnAccountsUpdatedListener(accounts -> instance.changed(), instance.main, false);
        instance.io.execute(() -> {
            try {
                instance.store = new UsageStore(application);
                snapshot(application, instance.store, instance.session);
                File root = application.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
                if (root == null) root = new File(application.getFilesDir(), SettingsConstants.KEY_PREF_MAP);
                File directory = new File(root, "collector_projects");
                if (directory.isDirectory()) {
                    instance.observer = new FileObserver(directory.getAbsolutePath(), FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.DELETE) {
                        @Override public void onEvent(int event, String path) {
                            if ("collector_projects_registry.json".equals(path)) instance.changed();
                        }
                    };
                    instance.observer.startWatching();
                }
                UsageUploadWorker.schedule(application);
            } catch (Exception error) { Log.w("MobileUsage", "Usage collection initialization deferred"); }
        });
        } catch (RuntimeException error) { Log.w("MobileUsage", "Usage reporting initialization deferred"); }
    }

    private void changed() {
        main.removeCallbacks(inventoryChanged);
        main.postDelayed(inventoryChanged, 250);
    }

    private void record(String kind, boolean openProject) {
        long at = System.currentTimeMillis();
        String owner = PreferenceManager.getDefaultSharedPreferences(application)
                .getString(SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, "");
        io.execute(() -> {
            try {
                if (store == null) store = new UsageStore(application);
                JSONObject active = snapshot(application, store, session, owner);
                if (kind != null) store.append(event(kind, session, active).put("at_ms", at), null);
                if (openProject && active != null) store.append(event("project_open", session, active).put("at_ms", at), null);
                UsageUploadWorker.schedule(application);
            } catch (Exception error) { Log.w("MobileUsage", "Usage event storage deferred"); }
        });
    }

    static JSONObject event(String kind, String session, JSONObject project) throws Exception {
        return new JSONObject().put("type", kind).put("at_ms", System.currentTimeMillis()).put("session_id", session)
                .put("version", BuildConfig.VERSION_NAME).put("brand", BuildConfig.FLAVOR)
                .put("project", project == null ? JSONObject.NULL : project);
    }

    static JSONObject device(Context context, UsageStore store) throws Exception {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.isEmpty()) id = "installation-" + store.installationId();
        return new JSONObject().put("id", id).put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("dropped_events", store.dropped());
    }

    static JSONObject snapshot(Context context, UsageStore store, String session) throws Exception {
        return snapshot(context, store, session, PreferenceManager.getDefaultSharedPreferences(context)
                .getString(SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, ""));
    }

    private static JSONObject snapshot(Context context, UsageStore store, String session, String ownerUid) throws Exception {
        IGISApplication app = (IGISApplication) context.getApplicationContext();
        Map<String, JSONObject> accounts = new TreeMap<>();
        Map<String, JSONObject> byName = new HashMap<>();
        for (Account account : AccountManager.get(context).getAccountsByType(app.getAccountsType())) {
            try {
                URI source = new URI(app.getAccountUrl(account));
                if (source.getHost() == null || !("https".equalsIgnoreCase(source.getScheme()) || "http".equalsIgnoreCase(source.getScheme()))) continue;
                String server = new URI(source.getScheme().toLowerCase(Locale.ROOT), null, source.getHost().toLowerCase(Locale.ROOT), source.getPort(), source.getPath(), null, null).toString().replaceAll("/+$", "");
                String login = app.getAccountLogin(account);
                JSONObject value = new JSONObject().put("server", server).put("login", login == null ? "" : login);
                accounts.put(server + "\n" + value.optString("login"), value);
                byName.put(account.name, value);
            } catch (Exception ignored) { /* An unavailable account must not suppress other projects. */ }
        }
        Map<String, JSONObject> projects = new TreeMap<>();
        for (CollectorProjectRegistry.ProjectInfo info : CollectorProjectRegistry.listProjects(context)) {
            JSONObject owner = byName.get(info.getAccountName());
            JSONObject p = new JSONObject().put("uid", info.getProjectUid()).put("name", info.getName() == null ? "" : info.getName())
                    .put("type", info.isLocal() ? "local" : "webgis").put("remote_id", Math.max(0, info.getProjectRemoteId()))
                    .put("account", owner == null ? JSONObject.NULL : owner);
            projects.put(info.getProjectUid(), p);
        }
        JSONObject active = projects.get(ownerUid);
        JSONObject inventory = new JSONObject().put("accounts", new JSONArray(accounts.values())).put("projects", new JSONArray(projects.values()));
        JSONObject snapshot = event("inventory", session, null).put("accounts", inventory.getJSONArray("accounts")).put("projects", inventory.getJSONArray("projects"));
        store.append(snapshot, inventory.toString());
        return active;
    }

    @Override public void onActivityStarted(Activity activity) {
        main.removeCallbacks(hidden);
        if (visible.isEmpty()) {
            String kind = policy.open();
            if (kind != null) record(kind, true);
        }
        visible.add(activity);
    }

    @Override public void onActivityStopped(Activity activity) {
        visible.remove(activity);
        if (visible.isEmpty() && !activity.isChangingConfigurations()) main.postDelayed(hidden, 700);
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
