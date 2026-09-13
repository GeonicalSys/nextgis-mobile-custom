/*
 * Project: NextGIS Mobile
 * Purpose: Manual application updates from the Geonical APK repository.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.CacheControl;
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
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final int MAX_MANIFEST_SIZE = 512 * 1024;
    private static final long MAX_APK_SIZE = 1024L * 1024L * 1024L;
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean UPDATE_CHECK_IN_PROGRESS = new AtomicBoolean(false);
    private static final AtomicBoolean UPDATE_DOWNLOAD_IN_PROGRESS = new AtomicBoolean(false);
    private static final OkHttpClient MANIFEST_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final OkHttpClient DOWNLOAD_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
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
        if (!isActivityUsable(activity) || !hasValidatedInternet(activity)) {
            return;
        }
        checkForUpdate(activity, false);
    }


    public static boolean resumePendingInstallation(Activity activity)
    {
        if (!isActivityUsable(activity)) {
            return false;
        }

        UpdateManifest manifest;
        try {
            manifest = readPendingInstallation(activity);
        } catch (JSONException error) {
            clearPendingInstallation(activity);
            showError(activity, error);
            return true;
        }
        if (manifest == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            clearPendingInstallation(activity);
            Toast.makeText(
                    activity,
                    R.string.update_install_permission_not_granted,
                    Toast.LENGTH_LONG)
                    .show();
            return true;
        }

        try {
            validateManifest(activity, manifest);
            if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                clearPendingInstallation(activity);
                return true;
            }
        } catch (IOException error) {
            clearPendingInstallation(activity);
            showError(activity, error);
            return true;
        }

        // Consume the one-shot continuation before starting asynchronous work. If validation or
        // installation later fails, the normal updater UI remains the explicit retry path.
        clearPendingInstallation(activity);
        downloadAndInstall(activity, manifest);
        return true;
    }


    private static void checkForUpdate(Activity activity, boolean interactive)
    {
        if (!isActivityUsable(activity)
                || !UPDATE_CHECK_IN_PROGRESS.compareAndSet(false, true)) {
            return;
        }

        ProgressDialog progressDialog = null;
        if (interactive) {
            progressDialog = new ProgressDialog(activity);
            progressDialog.setMessage(activity.getString(R.string.update_checking));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
        ProgressDialog checkProgressDialog = progressDialog;

        IO_EXECUTOR.execute(() -> {
            try {
                UpdateManifest manifest = requestManifest(activity);
                runOnUiThread(activity, () -> {
                    dismiss(checkProgressDialog);
                    if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                        if (interactive) {
                            new AlertDialog.Builder(activity)
                                    .setTitle(R.string.update_check)
                                    .setMessage(R.string.update_no)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                        return;
                    }
                    showUpdateAvailable(activity, manifest);
                });
            } catch (Exception error) {
                runOnUiThread(activity, () -> {
                    dismiss(checkProgressDialog);
                    if (interactive) {
                        showError(activity, error);
                    }
                });
            } finally {
                UPDATE_CHECK_IN_PROGRESS.set(false);
            }
        });
    }


    private static UpdateManifest requestManifest(Activity activity)
            throws IOException, JSONException
    {
        String branch = updateRepositoryBranch();
        String manifestUrl = AppSettingsConstants.APK_VERSION_UPDATE + "/" + branch
                + "/manifest.json";
        Request request = new Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", "NextGIS-Mobile-Updater/" + BuildConfig.VERSION_NAME)
                .cacheControl(new CacheControl.Builder().noCache().noStore().build())
                .build();

        try (Response response = MANIFEST_HTTP_CLIENT.newCall(request).execute()) {
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
            return manifest;
        }
    }


    private static void validateManifest(Activity activity, UpdateManifest manifest)
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


    private static void validateApkUrl(String value, String branch, long versionCode)
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


    private static void showUpdateAvailable(Activity activity, UpdateManifest manifest)
    {
        StringBuilder message = new StringBuilder(
                activity.getString(R.string.update_new, manifest.versionName));
        if (!manifest.releaseNotes.isEmpty()) {
            message.append("\n\n")
                    .append(activity.getString(
                            R.string.update_release_notes,
                            manifest.releaseNotes));
        }
        if (manifest.apkSize > 0) {
            double sizeMb = manifest.apkSize / (1024.0 * 1024.0);
            message.append("\n\n")
                    .append(String.format(Locale.getDefault(), "%.1f MB", sizeMb));
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_title)
                .setMessage(message.toString())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        R.string.update_download,
                        (dialog, which) -> downloadAndInstall(activity, manifest))
                .show();
    }


    private static void downloadAndInstall(Activity activity, UpdateManifest manifest)
    {
        if (!UPDATE_DOWNLOAD_IN_PROGRESS.compareAndSet(false, true)) {
            return;
        }
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle(R.string.update_title);
        progressDialog.setMessage(activity.getString(R.string.update_downloading));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        IO_EXECUTOR.execute(() -> {
            File updateDirectory = new File(activity.getCacheDir(), "updates");
            File apkFile = new File(
                    updateDirectory,
                    "update-" + manifest.versionCode + ".apk");
            try {
                if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
                    throw new IOException("Cannot create update cache directory");
                }

                if (apkFile.isFile()
                        && apkFile.length() == manifest.apkSize
                        && manifest.apkSha256.equalsIgnoreCase(sha256File(apkFile))) {
                    validateDownloadedApk(activity, apkFile, manifest);
                } else {
                    if (apkFile.exists() && !apkFile.delete()) {
                        throw new IOException("Cannot replace cached update APK");
                    }
                    downloadApk(activity, manifest, apkFile, progressDialog);
                    validateDownloadedApk(activity, apkFile, manifest);
                }

                runOnUiThread(activity, () -> {
                    dismiss(progressDialog);
                    requestInstallation(activity, apkFile, manifest);
                });
            } catch (Exception error) {
                runOnUiThread(activity, () -> {
                    dismiss(progressDialog);
                    showError(activity, error);
                });
            } finally {
                UPDATE_DOWNLOAD_IN_PROGRESS.set(false);
            }
        });
    }


    private static void downloadApk(
            Activity activity,
            UpdateManifest manifest,
            File destination,
            ProgressDialog progressDialog)
            throws IOException, NoSuchAlgorithmException
    {
        File temporaryFile = new File(destination.getParentFile(), destination.getName() + ".part");
        if (temporaryFile.exists() && !temporaryFile.delete()) {
            throw new IOException("Cannot clear unfinished update download");
        }

        Request request = new Request.Builder()
                .url(manifest.apkUrl)
                .header("User-Agent", "NextGIS-Mobile-Updater/" + BuildConfig.VERSION_NAME)
                .build();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (Response response = DOWNLOAD_HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " while downloading APK");
            }
            validateApkUrl(
                    response.request().url().toString(),
                    updateRepositoryBranch(),
                    manifest.versionCode);
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty APK response");
            }
            long totalBytes = body.contentLength();
            if (totalBytes > 0 && totalBytes != manifest.apkSize) {
                throw new IOException("APK Content-Length does not match manifest");
            }
            if (totalBytes <= 0) {
                totalBytes = manifest.apkSize;
            }
            final long expectedBytes = totalBytes;

            try (InputStream input = body.byteStream();
                 FileOutputStream output = new FileOutputStream(temporaryFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long downloadedBytes = 0;
                int lastProgress = -1;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    downloadedBytes += read;
                    if (downloadedBytes > manifest.apkSize) {
                        throw new IOException("Downloaded APK exceeds manifest size");
                    }
                    if (expectedBytes > 0) {
                        int progress = (int) Math.min(
                                100,
                                downloadedBytes * 100 / expectedBytes);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            int progressValue = progress;
                            runOnUiThread(
                                    activity,
                                    () -> progressDialog.setProgress(progressValue));
                        }
                    }
                }
                output.getFD().sync();
            }
        } catch (IOException error) {
            temporaryFile.delete();
            throw error;
        }

        if (manifest.apkSize > 0 && temporaryFile.length() != manifest.apkSize) {
            temporaryFile.delete();
            throw new IOException("Downloaded APK size does not match manifest");
        }
        String downloadedSha256 = toHex(digest.digest());
        if (!manifest.apkSha256.equalsIgnoreCase(downloadedSha256)) {
            temporaryFile.delete();
            throw new IOException("Downloaded APK SHA-256 does not match manifest");
        }
        if (destination.exists() && !destination.delete()) {
            temporaryFile.delete();
            throw new IOException("Cannot replace cached update APK");
        }
        if (!temporaryFile.renameTo(destination)) {
            temporaryFile.delete();
            throw new IOException("Cannot finalize downloaded update APK");
        }
    }


    private static void validateDownloadedApk(
            Activity activity,
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


    private static IOException invalidUpdate(Activity activity, String diagnostic)
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
            new AlertDialog.Builder(activity)
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
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error.getClass().getSimpleName();
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_title)
                .setMessage(activity.getString(R.string.update_error, message))
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


    private static boolean hasValidatedInternet(Activity activity)
    {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) activity.getSystemService(Activity.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
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


    private static final class UpdateManifest
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
