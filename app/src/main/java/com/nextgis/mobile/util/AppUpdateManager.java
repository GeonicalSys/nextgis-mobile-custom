/*
 * Project: NextGIS Mobile
 * Purpose: Branch-specific application updates from the Geonical APK repository.
 */

package com.nextgis.mobile.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;
import com.nextgis.mobile.BuildConfig;
import com.nextgis.mobile.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public final class AppUpdateManager
{
    private static final String UPDATE_FLAVOR_METADATA =
            "com.nextgis.mobile.UPDATE_FLAVOR";
    private static final String UPDATE_STATE_PREFERENCES = "app_update_state";
    private static final String KEY_PENDING_INSTALL_MANIFEST = "pending_install_manifest";
    private static final String KEY_READY_MANIFEST = "ready_manifest";
    private static final Object STATE_LOCK = new Object();
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final int MAX_MANIFEST_SIZE = 512 * 1024;
    private static final long MAX_APK_SIZE = 1024L * 1024L * 1024L;
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean UPDATE_CHECK_IN_PROGRESS = new AtomicBoolean(false);
    private static final AtomicBoolean UPDATE_DOWNLOAD_IN_PROGRESS = new AtomicBoolean(false);
    private static final AtomicBoolean BACKGROUND_PREPARE_IN_PROGRESS = new AtomicBoolean(false);
    private static final AtomicLong CHECK_SEQUENCE = new AtomicLong();
    private static final AtomicLong LAST_STARTUP_REVIEW = new AtomicLong();
    private static final OkHttpClient MANIFEST_HTTP_CLIENT = new OkHttpClient.Builder()
            .callTimeout(45, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final OkHttpClient DOWNLOAD_HTTP_CLIENT = new OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.MINUTES)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private AppUpdateManager()
    {
    }


    public static void checkForUpdate(Activity activity)
    {
        checkForUpdate(activity, true);
    }


    public static void checkForUpdateAutomatically(Activity activity)
    {
        checkForUpdateAutomatically(activity, null);
    }


    public static void checkForUpdateAutomatically(
            Activity activity, StartupUpdateCheckScheduler.Completion completion)
    {
        checkForUpdate(activity, false, completion);
    }

    /** A resident process must not suppress reminders indefinitely across daily app openings. */
    public static boolean shouldRefreshStartupCheck() {
        return MaintenanceReminders.reviewDue(SystemClock.elapsedRealtime(), LAST_STARTUP_REVIEW.get());
    }


    /** True only when an installation continuation actually starts, not for cleared stale state. */
    public static boolean resumePendingInstallation(Activity activity)
    {
        if (!isActivityUsable(activity)) {
            return false;
        }

        UpdateManifest manifest;
        try {
            manifest = readPendingInstallation(activity);
        } catch (JSONException error) {
            HyperLog.w(Constants.TAG, "Updater install_resume rejected reason=pending_manifest_parse", error);
            clearPendingInstallation(activity);
            showError(activity, error);
            return false;
        }
        if (manifest == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            HyperLog.v(Constants.TAG, "Updater install_resume stopped reason=install_permission_denied");
            clearPendingInstallation(activity);
            Toast.makeText(
                    activity,
                    R.string.update_install_permission_not_granted,
                    Toast.LENGTH_LONG)
                    .show();
            return false;
        }

        try {
            validateManifest(activity, manifest);
            if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                HyperLog.v(Constants.TAG, "Updater install_resume stopped reason=already_installed"
                        + " availableCode=" + manifest.versionCode + " currentCode=" + BuildConfig.VERSION_CODE);
                clearPendingInstallation(activity);
                return false;
            }
        } catch (IOException error) {
            HyperLog.w(Constants.TAG, "Updater install_resume rejected reason=manifest_validation", error);
            clearPendingInstallation(activity);
            showError(activity, error);
            return false;
        }

        // Consume the one-shot continuation before starting asynchronous work. If validation or
        // installation later fails, the normal updater UI remains the explicit retry path.
        clearPendingInstallation(activity);
        HyperLog.v(Constants.TAG, "Updater install_resume started versionCode=" + manifest.versionCode);
        installPreparedUpdate(activity, manifest);
        return true;
    }


    private static void checkForUpdate(Activity activity, boolean interactive)
    {
        checkForUpdate(activity, interactive, null);
    }


    private static void checkForUpdate(Activity activity, boolean interactive,
                                      StartupUpdateCheckScheduler.Completion completion)
    {
        String mode = interactive ? "manual" : "automatic";
        if (!isActivityUsable(activity)) {
            HyperLog.v(Constants.TAG, "Updater check skipped mode=" + mode + " reason=activity_unusable");
            finishCheck(completion, false);
            return;
        }
        NetworkState network = networkState(activity);
        if (!UPDATE_CHECK_IN_PROGRESS.compareAndSet(false, true)) {
            HyperLog.v(Constants.TAG, "Updater check skipped mode=" + mode + " reason=check_in_progress " + network);
            finishCheck(completion, false);
            return;
        }
        long checkId = CHECK_SEQUENCE.incrementAndGet();
        if (!interactive) LAST_STARTUP_REVIEW.set(SystemClock.elapsedRealtime());
        long started = SystemClock.elapsedRealtime();
        HyperLog.v(Constants.TAG, "Updater check started id=" + checkId + " mode=" + mode
                + " branch=" + updateRepositoryBranch() + " currentVersion=" + BuildConfig.VERSION_NAME
                + " currentCode=" + BuildConfig.VERSION_CODE + " " + network);

        ProgressDialog progressDialog = null;
        if (interactive) {
            progressDialog = new ProgressDialog(activity);
            progressDialog.setMessage(activity.getString(R.string.update_checking));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
        ProgressDialog checkProgressDialog = progressDialog;
        // Capture only the active online project's identity on the UI thread. Local maps
        // never get a synchronization reminder, even if another project used to be open.
        com.nextgis.maplib.map.MapBase activeMap = com.nextgis.maplib.map.MapBase.getActiveInstance();
        String onlineProjectPath = MaintenanceReminders.hasOnlineLayers(activeMap)
                ? activeMap.getPath().getAbsolutePath() : null;

        IO_EXECUTOR.execute(() -> {
            UpdateManifest result = null;
            boolean ready = false;
            Exception failure = null;
            try {
                // Read locally first: a previously prepared update must also be offered offline.
                result = readReadyUpdate(activity);
                ready = result != null;
                if (ready) {
                    // Keep the launch check even when an older valid update is already ready.
                    AppUpdateWorker.enqueue(activity.getApplicationContext());
                } else if (network.canRequest()) {
                    result = requestManifest(activity, checkId);
                    if (result.versionCode > BuildConfig.VERSION_CODE) {
                        AppUpdateWorker.enqueue(activity.getApplicationContext());
                    }
                } else {
                    throw new IOException("No network for update check");
                }
            } catch (Exception error) {
                failure = error;
                HyperLog.w(Constants.TAG, "Updater check failed id=" + checkId + " mode=" + mode
                        + " elapsedMs=" + (SystemClock.elapsedRealtime() - started)
                        + " type=" + error.getClass().getSimpleName() + " message=" + error.getMessage(), error);
            } finally {
                UPDATE_CHECK_IN_PROGRESS.set(false);
            }
            UpdateManifest manifest = result;
            boolean updateReady = ready;
            Exception checkError = failure;
            MaintenanceReminders.Reminder reminder = MaintenanceReminders.read(activity,
                    onlineProjectPath, ready && result != null ? result.versionCode : 0,
                    System.currentTimeMillis());
            activity.runOnUiThread(() -> {
                if (!isActivityUsable(activity)) {
                    HyperLog.v(Constants.TAG, "Updater check result_deferred id=" + checkId + " reason=activity_unusable");
                    finishCheck(completion, false);
                    return;
                }
                dismiss(checkProgressDialog);
                // Never stack a startup warning on an installer, another dialog or a paused map.
                com.nextgis.maplib.map.MapBase currentMap = com.nextgis.maplib.map.MapBase.getActiveInstance();
                String currentPath = MaintenanceReminders.hasOnlineLayers(currentMap)
                        ? currentMap.getPath().getAbsolutePath() : null;
                if (!interactive && activity.hasWindowFocus()
                        && java.util.Objects.equals(onlineProjectPath, currentPath)
                        && reminder.needed()
                        && !MaintenanceReminders.alreadyShown(activity, StartupUpdateCheckScheduler.PROCESS_SESSION)) {
                    boolean readyOffer = updateReady && reminder.update != MaintenanceReminders.Update.DOWNLOAD;
                    if (readyOffer) {
                        readyDialog(activity, manifest.versionName, manifest.releaseNotes,
                                reminder.message(activity), () -> installPreparedUpdate(activity, manifest)).show();
                    } else MaintenanceReminders.dialog(activity, reminder).show();
                    MaintenanceReminders.markShown(activity, StartupUpdateCheckScheduler.PROCESS_SESSION);
                    // An offline warning never completes the pending network check. A ready
                    // offer already contains Install/Later and must not be offered twice.
                    finishCheck(completion, checkError == null && (readyOffer
                            || manifest.versionCode <= BuildConfig.VERSION_CODE));
                    return;
                }
                if (checkError != null) {
                    if (interactive) showError(activity, checkError);
                    finishCheck(completion, false);
                    return;
                }
                if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                    HyperLog.v(Constants.TAG, "Updater check completed id=" + checkId + " result=current"
                            + " availableCode=" + manifest.versionCode + " currentCode=" + BuildConfig.VERSION_CODE
                            + " elapsedMs=" + (SystemClock.elapsedRealtime() - started));
                    if (interactive) {
                        com.nextgis.maplibui.util.FittedDialogs.compatBuilder(activity)
                                .setTitle(R.string.update_check)
                                .setMessage(R.string.update_no)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                    finishCheck(completion, true);
                    return;
                }
                if (!interactive && !activity.hasWindowFocus()) {
                    HyperLog.v(Constants.TAG, "Updater check result_deferred id=" + checkId
                            + " reason=window_not_focused availableCode=" + manifest.versionCode);
                    finishCheck(completion, false);
                    return;
                }
                if (!updateReady) {
                    if (interactive) {
                        Toast.makeText(activity, network.canDownload()
                                        ? R.string.update_background_downloading : R.string.update_waiting_network,
                                Toast.LENGTH_LONG).show();
                    }
                    // A foreground retry will show the ready offer when preparation finishes.
                    finishCheck(completion, false);
                    return;
                }
                showReadyUpdate(activity, manifest);
                HyperLog.v(Constants.TAG, "Updater check completed id=" + checkId + " result=offer_shown"
                        + " availableVersion=" + manifest.versionName + " availableCode=" + manifest.versionCode
                        + " elapsedMs=" + (SystemClock.elapsedRealtime() - started));
                finishCheck(completion, true);
            });
        });
    }


    private static void finishCheck(StartupUpdateCheckScheduler.Completion completion, boolean completed)
    {
        if (completion != null) completion.finish(completed);
    }


    private static UpdateManifest requestManifest(Context activity, long checkId)
            throws IOException, JSONException
    {
        return requestManifest(activity, checkId, null);
    }


    private static UpdateManifest requestManifest(Context activity, long checkId, NetworkGuard guard)
            throws IOException, JSONException
    {
        String branch = updateRepositoryBranch();
        String manifestUrl = AppSettingsConstants.APK_VERSION_UPDATE + "/" + branch
                + "/manifest.json";
        HyperLog.v(Constants.TAG, "Updater manifest request id=" + checkId + " url=" + manifestUrl);
        Request request = new Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", "NextGIS-Mobile-Updater/" + BuildConfig.VERSION_NAME)
                .cacheControl(new CacheControl.Builder().noCache().noStore().build())
                .build();

        OkHttpClient client = guard == null ? MANIFEST_HTTP_CLIENT
                : guard.client.newBuilder().callTimeout(45, TimeUnit.SECONDS).build();
        Call call = client.newCall(request);
        if (guard != null) {
            guard.attach(call);
            guard.check();
        }
        try (Response response = call.execute()) {
            HyperLog.v(Constants.TAG, "Updater manifest response id=" + checkId + " http=" + response.code());
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + manifestUrl);
            }
            if (!manifestUrl.equals(response.request().url().toString())) {
                throw new IOException("Update manifest redirect is not allowed");
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty update manifest response");
            }
            UpdateManifest manifest =
                    UpdateManifest.fromJson(new JSONObject(readBodyLimited(body, MAX_MANIFEST_SIZE)));
            validateManifest(activity, manifest);
            MaintenanceReminders.recordSuccessfulCheck(activity, manifest.versionCode, System.currentTimeMillis());
            HyperLog.v(Constants.TAG, "Updater manifest validated id=" + checkId
                    + " version=" + manifest.versionName + " versionCode=" + manifest.versionCode
                    + " publishedAt=" + manifest.publishedAt + " branch=" + manifest.flavor
                    + " channel=" + manifest.channel);
            return manifest;
        }
    }


    static void validateManifest(Context activity, UpdateManifest manifest)
            throws IOException
    {
        String expectedApplicationId = activity.getPackageName();
        if (!expectedApplicationId.equals(manifest.applicationId)) {
            throw new IOException("Unexpected application ID in update manifest");
        }
        String expectedBranch = updateRepositoryBranch();
        if (!expectedBranch.equals(manifest.flavor)) {
            throw new IOException("Unexpected application flavor in update manifest");
        }
        if (!expectedUpdateChannel(expectedBranch).equals(manifest.channel)) {
            throw new IOException("Unexpected update channel in update manifest");
        }
        if (manifest.versionCode <= 0 || manifest.versionName.isEmpty()) {
            throw new IOException("Invalid application version in update manifest");
        }
        if (manifest.apkSize <= 0 || manifest.apkSize > MAX_APK_SIZE) {
            throw new IOException("Invalid APK size in update manifest");
        }
        if (manifest.minSdk <= 0 || manifest.targetSdk < manifest.minSdk) {
            throw new IOException("Invalid SDK range in update manifest");
        }
        if (manifest.minSdk > Build.VERSION.SDK_INT) {
            throw new IOException("Update requires a newer Android version");
        }
        if (manifest.publishedAt.isEmpty()) {
            throw new IOException("Missing publication time in update manifest");
        }
        if (!manifest.apkSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("Invalid APK SHA-256 in update manifest");
        }
        if (!manifest.signingCertificateSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("Invalid signing certificate in update manifest");
        }
        validateApkUrl(manifest.apkUrl, expectedBranch, manifest.versionCode);
    }


    private static String updateRepositoryBranch()
    {
        if (BuildConfig.DEBUG) {
            return "debug";
        }
        String flavor = BuildConfig.FLAVOR;
        return flavor == null || flavor.trim().isEmpty() ? "lisa" : flavor;
    }


    private static String expectedUpdateChannel(String branch)
    {
        return "debug".equals(branch) ? "debug" : "stable";
    }


    static void validateApkUrl(String value, String branch, long versionCode)
            throws IOException
    {
        try {
            URI repositoryUri = new URI(AppSettingsConstants.APK_VERSION_UPDATE);
            URI apkUri = new URI(value);
            URI normalizedUri = apkUri.normalize();
            int repositoryPort = repositoryUri.getPort() == -1 ? 443 : repositoryUri.getPort();
            int apkPort = normalizedUri.getPort() == -1 ? 443 : normalizedUri.getPort();
            String rawPath = apkUri.getRawPath();
            String normalizedPath = normalizedUri.getRawPath();
            String repositoryPath = repositoryUri.getRawPath();
            if (repositoryPath.endsWith("/")) {
                repositoryPath = repositoryPath.substring(0, repositoryPath.length() - 1);
            }
            String expectedPrefix = repositoryPath + "/" + branch + "/releases/"
                    + versionCode + "/";
            if (!"https".equalsIgnoreCase(normalizedUri.getScheme())
                    || repositoryUri.getHost() == null
                    || !repositoryUri.getHost().equalsIgnoreCase(normalizedUri.getHost())
                    || repositoryPort != apkPort
                    || normalizedUri.getUserInfo() != null
                    || normalizedUri.getQuery() != null
                    || normalizedUri.getFragment() != null
                    || rawPath == null
                    || !rawPath.equals(normalizedPath)
                    || rawPath.toLowerCase(Locale.US).contains("%2e")
                    || !normalizedPath.startsWith(expectedPrefix)
                    || normalizedPath.length() <= expectedPrefix.length()
                    || !normalizedPath.toLowerCase(Locale.US).endsWith(".apk")) {
                throw new IOException("Update APK points outside the trusted branch");
            }
        } catch (URISyntaxException error) {
            throw new IOException("Invalid update APK URL", error);
        }
    }


    private static String readBodyLimited(ResponseBody body, int maximumBytes)
            throws IOException
    {
        long declaredLength = body.contentLength();
        if (declaredLength > maximumBytes) {
            throw new IOException("Update manifest is too large");
        }
        try (InputStream input = body.byteStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw new IOException("Update manifest is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }


    private static void showReadyUpdate(Activity activity, UpdateManifest manifest)
    {
        readyDialog(activity, manifest.versionName, manifest.releaseNotes,
                () -> installPreparedUpdate(activity, manifest)).show();
    }


    static AlertDialog readyDialog(Context context, String version, String notes, Runnable install)
    {
        return readyDialog(context, version, notes, "", install);
    }


    static AlertDialog readyDialog(Context context, String version, String notes, String reminder, Runnable install)
    {
        String message = context.getString(R.string.update_ready_message, version);
        if (!reminder.isEmpty()) message = reminder + "\n\n" + message;
        if (!notes.isEmpty()) message += "\n\n" + context.getString(R.string.update_release_notes, notes);
        AlertDialog.Builder builder = reminder.isEmpty()
                ? com.nextgis.maplibui.util.FittedDialogs.compatBuilder(context)
                : new AlertDialog.Builder(context);
        AlertDialog dialog = builder
                .setTitle(R.string.update_ready_title)
                .setMessage(message)
                .setNegativeButton(R.string.update_later, null)
                .setPositiveButton(R.string.update_install, (prompt, which) -> install.run())
                .create();
        return reminder.isEmpty() ? dialog : MaintenanceReminders.scrollable(dialog);
    }


    private static void installPreparedUpdate(Activity activity, UpdateManifest manifest)
    {
        if (!UPDATE_DOWNLOAD_IN_PROGRESS.compareAndSet(false, true)) return;
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage(activity.getString(R.string.update_preparing_installation));
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        IO_EXECUTOR.execute(() -> {
            try {
                validateManifest(activity, manifest);
                File apk = preparedApk(activity, manifest);
                // Preserve continuation from versions that stored their APK in cache/updates.
                if (!apk.isFile()) apk = new File(activity.getCacheDir(),
                        "updates/update-" + manifest.versionCode + ".apk");
                if (!apk.isFile() || apk.length() != manifest.apkSize
                        || !manifest.apkSha256.equalsIgnoreCase(sha256File(apk))) {
                    AppUpdateWorker.enqueue(activity.getApplicationContext());
                    throw new IOException("Prepared APK is missing or corrupt");
                }
                validateDownloadedApk(activity, apk, manifest);
                File validated = apk;
                runOnUiThread(activity, () -> {
                    dismiss(progress);
                    requestInstallation(activity, validated, manifest);
                });
            } catch (Exception error) {
                runOnUiThread(activity, () -> {
                    dismiss(progress);
                    showError(activity, error);
                });
            } finally {
                UPDATE_DOWNLOAD_IN_PROGRESS.set(false);
            }
        });
    }


    static boolean prepareUpdate(Context context, AppUpdateWorker worker) throws Exception
    {
        if (!BACKGROUND_PREPARE_IN_PROGRESS.compareAndSet(false, true)) return false;
        try (NetworkGuard guard = new NetworkGuard(context, worker::isStopped, worker::attach)) {
            UpdateManifest manifest = requestManifest(context, CHECK_SEQUENCE.incrementAndGet(), guard);
            guard.manifest = manifest;
            guard.check();
            pruneInstalledUpdates(context);
            if (manifest.versionCode <= BuildConfig.VERSION_CODE) return true;
            File apk = preparedApk(context, manifest);
            if (!apk.getParentFile().isDirectory() && !apk.getParentFile().mkdirs()) {
                throw new IOException("Cannot create update directory");
            }
            if (!apk.isFile() || apk.length() != manifest.apkSize
                    || !manifest.apkSha256.equalsIgnoreCase(AppUpdateDownload.sha256(apk, guard))) {
                AppUpdateDownload.download(guard.client, new Request.Builder().url(manifest.apkUrl)
                                .header("User-Agent", "NextGIS-Mobile-Updater/" + BuildConfig.VERSION_NAME).build(),
                        apk, manifest.apkSize, manifest.apkSha256, guard);
            }
            guard.check();
            validateDownloadedApk(context, apk, manifest);
            guard.check();
            synchronized (STATE_LOCK) {
                if (!pendingInstallationPreferences(context).edit()
                        .putString(KEY_READY_MANIFEST, manifest.toJson().toString()).commit()) {
                    throw new IOException("Cannot persist prepared update");
                }
            }
            HyperLog.v(Constants.TAG, "Updater background ready versionCode=" + manifest.versionCode);
            return true;
        } finally {
            BACKGROUND_PREPARE_IN_PROGRESS.set(false);
        }
    }


    private static File preparedApk(Context context, UpdateManifest manifest)
    {
        return new File(context.getFilesDir(), "updates/update-" + manifest.versionCode
                + "-" + manifest.apkSha256.toLowerCase(Locale.US) + ".apk");
    }


    static UpdateManifest readReadyUpdate(Context context)
    {
        String json = pendingInstallationPreferences(context).getString(KEY_READY_MANIFEST, null);
        if (json == null) return null;
        try {
            UpdateManifest manifest = UpdateManifest.fromJson(new JSONObject(json));
            validateManifest(context, manifest);
            if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                clearReadyUpdate(context, json);
                return null;
            }
            File apk = preparedApk(context, manifest);
            if (apk.length() != manifest.apkSize
                    || !manifest.apkSha256.equalsIgnoreCase(sha256File(apk))) {
                throw new IOException("Prepared APK is missing or corrupt");
            }
            validateDownloadedApk(context, apk, manifest);
            MaintenanceReminders.recordReady(context, manifest.versionCode, System.currentTimeMillis());
            return manifest;
        } catch (Exception error) {
            HyperLog.w(Constants.TAG, "Updater prepared state rejected", error);
            clearReadyUpdate(context, json);
            return null;
        }
    }


    private static void clearReadyUpdate(Context context, String expected)
    {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = pendingInstallationPreferences(context);
            if (expected.equals(prefs.getString(KEY_READY_MANIFEST, null))) {
                prefs.edit().remove(KEY_READY_MANIFEST).commit();
            }
        }
    }


    private static void pruneInstalledUpdates(Context context)
    {
        File[] files = new File(context.getFilesDir(), "updates").listFiles();
        if (files == null || UPDATE_DOWNLOAD_IN_PROGRESS.get()
                || pendingInstallationPreferences(context).contains(KEY_PENDING_INSTALL_MANIFEST)) return;
        for (File file : files) {
            if (!file.getName().matches("update-[0-9]+-[0-9a-f]{64}\\.apk(\\.part)?")) continue;
            long code;
            try { code = Long.parseLong(file.getName().split("-")[1]); }
            catch (NumberFormatException error) { continue; }
            if (code <= BuildConfig.VERSION_CODE && !file.delete()) {
                HyperLog.w(Constants.TAG, "Updater cannot remove installed update artifact");
            }
        }
        String json = pendingInstallationPreferences(context).getString(KEY_READY_MANIFEST, null);
        if (json != null) {
            try {
                if (UpdateManifest.fromJson(new JSONObject(json)).versionCode <= BuildConfig.VERSION_CODE)
                    clearReadyUpdate(context, json);
            } catch (JSONException error) { clearReadyUpdate(context, json); }
        }
    }


    /** Pin sockets and DNS to the eligible network; stop in-flight HTTP on roaming/loss/switch. */
    static final class NetworkGuard implements AppUpdateDownload.Guard, AutoCloseable {
        private final Context context;
        private final java.util.function.BooleanSupplier stopped;
        private final java.util.function.Consumer<Call> onCall;
        private final ConnectivityManager connectivity;
        private final Network network;
        private final ConnectivityManager.NetworkCallback callback;
        private volatile boolean invalid;
        private volatile Call activeCall;
        private UpdateManifest manifest;
        final OkHttpClient client;

        NetworkGuard(Context context, java.util.function.BooleanSupplier stopped,
                     java.util.function.Consumer<Call> onCall) throws IOException {
            this.context = context;
            this.stopped = stopped;
            this.onCall = onCall;
            connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            network = connectivity == null ? null : connectivity.getActiveNetwork();
            if (network == null || !networkState(context).canDownload()) {
                throw new IOException("No non-roaming update network");
            }
            client = DOWNLOAD_HTTP_CLIENT.newBuilder().socketFactory(network.getSocketFactory())
                    .dns(host -> Arrays.asList(network.getAllByName(host)))
                    .connectionPool(new ConnectionPool()).retryOnConnectionFailure(false).build();
            callback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network available) {
                    if (!network.equals(available)) invalidate();
                }
                @Override public void onLost(Network lost) {
                    if (network.equals(lost)) invalidate();
                }
                @Override public void onCapabilitiesChanged(Network changed, NetworkCapabilities caps) {
                    if (network.equals(changed) && !networkState(context).canDownload()) invalidate();
                }
            };
            connectivity.registerDefaultNetworkCallback(callback);
        }

        private void invalidate() {
            invalid = true;
            Call call = activeCall;
            if (call != null) call.cancel();
        }

        @Override public void check() throws IOException {
            if (invalid || stopped.getAsBoolean() || !network.equals(connectivity.getActiveNetwork())
                    || !networkState(context).canDownload()) {
                invalidate();
                throw new IOException("Update download paused for network/worker change");
            }
        }

        @Override public void attach(Call call) {
            activeCall = call;
            onCall.accept(call);
            if (invalid) call.cancel();
        }

        @Override public void validateUrl(String url) throws IOException {
            validateApkUrl(url, updateRepositoryBranch(), manifest.versionCode);
        }

        @Override public void close() {
            connectivity.unregisterNetworkCallback(callback);
            client.connectionPool().evictAll();
        }
    }


    private static void validateDownloadedApk(
            Context activity,
            File apkFile,
            UpdateManifest manifest)
            throws IOException, PackageManager.NameNotFoundException, NoSuchAlgorithmException
    {
        PackageManager packageManager = activity.getPackageManager();
        // PackageParser on Android 9 and 10 may leave SigningInfo empty for an archive even when
        // GET_SIGNING_CERTIFICATES was requested. Ask for the legacy signatures field as a
        // deliberate fallback; installed and downloaded APKs are still compared by SHA-256.
        int flags = PackageManager.GET_META_DATA
                | AppUpdatePackageInfoPolicy.signatureFlagsForSdk(
                        Build.VERSION.SDK_INT,
                        PackageManager.GET_SIGNATURES,
                        PackageManager.GET_SIGNING_CERTIFICATES);

        PackageInfo archiveInfo;
        PackageInfo installedInfo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            archiveInfo = packageManager.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(),
                    PackageManager.PackageInfoFlags.of(flags));
            installedInfo = packageManager.getPackageInfo(
                    activity.getPackageName(),
                    PackageManager.PackageInfoFlags.of(flags));
        } else {
            archiveInfo = packageManager.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
            installedInfo = packageManager.getPackageInfo(activity.getPackageName(), flags);
        }

        if (archiveInfo == null || !activity.getPackageName().equals(archiveInfo.packageName)) {
            throw invalidUpdate(activity, archiveInfo == null
                    ? "archive package info unavailable"
                    : "application id mismatch");
        }
        String archiveFlavor = archiveInfo.applicationInfo != null
                && archiveInfo.applicationInfo.metaData != null
                ? archiveInfo.applicationInfo.metaData.getString(UPDATE_FLAVOR_METADATA)
                : null;
        if (!updateRepositoryBranch().equals(archiveFlavor)) {
            throw invalidUpdate(activity, "update flavor mismatch");
        }
        if (getVersionCode(archiveInfo) != manifest.versionCode
                || manifest.versionCode <= getVersionCode(installedInfo)
                || archiveInfo.versionName == null
                || !manifest.versionName.equals(archiveInfo.versionName)) {
            throw invalidUpdate(activity, "version identity mismatch");
        }

        Set<String> archiveCertificates = certificateDigests(archiveInfo);
        Set<String> installedCertificates = certificateDigests(installedInfo);
        if (!archiveCertificates.contains(manifest.signingCertificateSha256.toLowerCase(Locale.US))
                || !installedCertificates.contains(
                        manifest.signingCertificateSha256.toLowerCase(Locale.US))) {
            throw invalidUpdate(
                    activity,
                    "signing certificate mismatch archiveCertificates=" +
                            archiveCertificates.size() + " installedCertificates=" +
                            installedCertificates.size());
        }
        HyperLog.v(
                Constants.TAG,
                "Updater APK validation passed sdk=" + Build.VERSION.SDK_INT +
                        " versionCode=" + manifest.versionCode);
    }


    static Set<String> certificateDigests(PackageInfo packageInfo)
            throws NoSuchAlgorithmException
    {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageInfo.signingInfo != null) {
            signatures = packageInfo.signingInfo.getApkContentsSigners();
            if (signatures == null || signatures.length == 0) {
                signatures = packageInfo.signatures;
            }
        } else {
            signatures = packageInfo.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            return new HashSet<>();
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Set<String> result = new HashSet<>();
        for (Signature signature : signatures) {
            result.add(toHex(digest.digest(signature.toByteArray())));
            digest.reset();
        }
        return result;
    }


    private static IOException invalidUpdate(Context activity, String diagnostic)
    {
        HyperLog.w(Constants.TAG, "Updater APK validation rejected: " + diagnostic);
        return new IOException(activity.getString(R.string.update_invalid));
    }


    static long getVersionCode(PackageInfo packageInfo)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }


    private static void requestInstallation(
            Activity activity,
            File apkFile,
            UpdateManifest manifest)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            com.nextgis.maplibui.util.FittedDialogs.compatBuilder(activity)
                    .setTitle(R.string.update_install_permission_title)
                    .setMessage(R.string.update_install_permission_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.update_open_settings, (dialog, which) -> {
                        if (!savePendingInstallation(activity, manifest)) {
                            showError(
                                    activity,
                                    new IOException("Cannot persist pending update state"));
                            return;
                        }
                        Intent settingsIntent = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        try {
                            activity.startActivity(settingsIntent);
                        } catch (ActivityNotFoundException error) {
                            clearPendingInstallation(activity);
                            showError(activity, error);
                        }
                    })
                    .show();
            return;
        }

        Uri apkUri = FileProvider.getUriForFile(
                activity,
                BuildConfig.APPLICATION_ID + ".easypicker.provider",
                apkFile);
        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(apkUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(installIntent);
        } catch (ActivityNotFoundException error) {
            Intent fallbackIntent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(fallbackIntent);
        }
    }


    private static boolean savePendingInstallation(Activity activity, UpdateManifest manifest)
    {
        return pendingInstallationPreferences(activity)
                .edit()
                .putString(KEY_PENDING_INSTALL_MANIFEST, manifest.toJson().toString())
                .commit();
    }


    private static UpdateManifest readPendingInstallation(Activity activity)
            throws JSONException
    {
        String json = pendingInstallationPreferences(activity)
                .getString(KEY_PENDING_INSTALL_MANIFEST, null);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return UpdateManifest.fromJson(new JSONObject(json));
    }


    private static void clearPendingInstallation(Activity activity)
    {
        pendingInstallationPreferences(activity)
                .edit()
                .remove(KEY_PENDING_INSTALL_MANIFEST)
                .commit();
    }


    private static SharedPreferences pendingInstallationPreferences(Context context)
    {
        return context.getSharedPreferences(
                UPDATE_STATE_PREFERENCES,
                Context.MODE_PRIVATE);
    }

    public static boolean isBusyOrPending(Context context) {
        return UPDATE_CHECK_IN_PROGRESS.get() || UPDATE_DOWNLOAD_IN_PROGRESS.get()
                || pendingInstallationPreferences(context).contains(KEY_PENDING_INSTALL_MANIFEST);
    }


    private static String sha256File(File file)
            throws IOException, NoSuchAlgorithmException
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }


    private static String toHex(byte[] bytes)
    {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }


    private static void showError(Activity activity, Exception error)
    {
        if (!isActivityUsable(activity)) {
            return;
        }
        HyperLog.w(Constants.TAG, "Updater UI operation failed", error);
        com.nextgis.maplibui.util.FittedDialogs.compatBuilder(activity)
                .setTitle(R.string.update_title)
                .setMessage(R.string.update_error)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }


    private static boolean isActivityUsable(Activity activity)
    {
        return activity != null
                && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                || !activity.isDestroyed());
    }


    static final class NetworkState {
        final boolean active, internet, validated, wifi, cellular, vpn, notRoaming;

        NetworkState(boolean active, boolean internet, boolean validated,
                     boolean wifi, boolean cellular, boolean vpn) {
            this(active, internet, validated, wifi, cellular, vpn, false);
        }

        NetworkState(boolean active, boolean internet, boolean validated,
                     boolean wifi, boolean cellular, boolean vpn, boolean notRoaming) {
            this.active = active;
            this.internet = internet;
            this.validated = validated;
            this.wifi = wifi;
            this.cellular = cellular;
            this.vpn = vpn;
            this.notRoaming = notRoaming;
        }

        boolean canRequest() {
            return active && internet; // Server reachability is established by the HTTP request.
        }

        boolean canDownload() {
            return canRequest() && notRoaming && (wifi || cellular || vpn);
        }

        @Override public String toString() {
            return "active=" + active + " internet=" + internet + " validated=" + validated
                    + " wifi=" + wifi + " cellular=" + cellular + " vpn=" + vpn
                    + " notRoaming=" + notRoaming;
        }
    }


    static NetworkState networkState(Context activity)
    {
        try {
            ConnectivityManager connectivity =
                    (ConnectivityManager) activity.getSystemService(Activity.CONNECTIVITY_SERVICE);
            Network active = connectivity == null ? null : connectivity.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : connectivity.getNetworkCapabilities(active);
            boolean notRoaming = false;
            if (caps != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                notRoaming = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
            } else if (active != null) {
                // NOT_ROAMING was added in API 28; preserve the supported Android 8 route.
                android.net.NetworkInfo info = connectivity.getNetworkInfo(active);
                notRoaming = info != null && !info.isRoaming();
            }
            return new NetworkState(active != null,
                    caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN), notRoaming);
        } catch (RuntimeException error) {
            HyperLog.w(Constants.TAG, "Updater network_state_failed type=" + error.getClass().getSimpleName(), error);
            return new NetworkState(false, false, false, false, false, false);
        }
    }


    private static void runOnUiThread(Activity activity, Runnable action)
    {
        activity.runOnUiThread(() -> {
            if (isActivityUsable(activity)) {
                action.run();
            }
        });
    }


    private static void dismiss(ProgressDialog progressDialog)
    {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }


    static final class UpdateManifest
    {
        final String applicationId;
        final String flavor;
        final String channel;
        final long versionCode;
        final String versionName;
        final long minSdk;
        final long targetSdk;
        final String apkUrl;
        final long apkSize;
        final String apkSha256;
        final String signingCertificateSha256;
        final String publishedAt;
        final String releaseNotes;

        private UpdateManifest(
                String applicationId,
                String flavor,
                String channel,
                long versionCode,
                String versionName,
                long minSdk,
                long targetSdk,
                String apkUrl,
                long apkSize,
                String apkSha256,
                String signingCertificateSha256,
                String publishedAt,
                String releaseNotes)
        {
            this.applicationId = applicationId;
            this.flavor = flavor;
            this.channel = channel;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.minSdk = minSdk;
            this.targetSdk = targetSdk;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.apkSha256 = apkSha256;
            this.signingCertificateSha256 = signingCertificateSha256;
            this.publishedAt = publishedAt;
            this.releaseNotes = releaseNotes;
        }


        static UpdateManifest fromJson(JSONObject json) throws JSONException
        {
            if (json.optInt("schemaVersion", 0) != 1) {
                throw new JSONException("Unsupported update manifest version");
            }
            return new UpdateManifest(
                    json.getString("applicationId"),
                    json.getString("flavor"),
                    json.getString("channel"),
                    json.getLong("versionCode"),
                    json.getString("versionName").trim(),
                    json.getLong("minSdk"),
                    json.getLong("targetSdk"),
                    json.getString("apkUrl"),
                    json.getLong("apkSize"),
                    json.getString("apkSha256"),
                    json.getString("signingCertificateSha256"),
                    json.getString("publishedAt").trim(),
                    json.optString("releaseNotes", "").trim());
        }


        JSONObject toJson()
        {
            JSONObject json = new JSONObject();
            try {
                json.put("schemaVersion", 1);
                json.put("applicationId", applicationId);
                json.put("flavor", flavor);
                json.put("channel", channel);
                json.put("versionCode", versionCode);
                json.put("versionName", versionName);
                json.put("minSdk", minSdk);
                json.put("targetSdk", targetSdk);
                json.put("apkUrl", apkUrl);
                json.put("apkSize", apkSize);
                json.put("apkSha256", apkSha256);
                json.put("signingCertificateSha256", signingCertificateSha256);
                json.put("publishedAt", publishedAt);
                json.put("releaseNotes", releaseNotes);
            } catch (JSONException error) {
                throw new IllegalStateException("Cannot persist pending update manifest", error);
            }
            return json;
        }
    }
}
