/*
 * Project: NextGIS Mobile
 * Purpose: Manual application updates from the Geonical APK repository.
 */

package com.nextgis.mobile.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.nextgis.mobile.BuildConfig;
import com.nextgis.mobile.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public final class AppUpdateManager
{
    private static final String UPDATE_CHANNEL = "stable";
    private static final String UPDATE_FLAVOR_METADATA =
            "com.nextgis.mobile.UPDATE_FLAVOR";
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean UPDATE_CHECK_IN_PROGRESS = new AtomicBoolean(false);
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
        String flavor = updateRepositoryFlavor();
        String manifestUrl = AppSettingsConstants.APK_VERSION_UPDATE + "/" + flavor + "/"
                + UPDATE_CHANNEL + "/manifest.json";
        Request request = new Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", "NextGIS-Mobile-Updater/" + BuildConfig.VERSION_NAME)
                .build();

        try (Response response = MANIFEST_HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + manifestUrl);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty update manifest response");
            }
            UpdateManifest manifest = UpdateManifest.fromJson(new JSONObject(body.string()));
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
        if (!updateRepositoryFlavor().equals(manifest.flavor)) {
            throw new IOException("Unexpected application flavor in update manifest");
        }
        String expectedUrlPrefix = AppSettingsConstants.APK_VERSION_UPDATE + "/";
        if (!manifest.apkUrl.startsWith(expectedUrlPrefix)) {
            throw new IOException("Update APK points outside the trusted repository");
        }
        if (!manifest.apkSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("Invalid APK SHA-256 in update manifest");
        }
        if (!manifest.signingCertificateSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("Invalid signing certificate in update manifest");
        }
    }


    private static String updateRepositoryFlavor()
    {
        if (BuildConfig.DEBUG) {
            return "debug";
        }
        String flavor = BuildConfig.FLAVOR;
        return flavor == null || flavor.trim().isEmpty() ? "lisa" : flavor;
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
                    requestInstallation(activity, apkFile);
                });
            } catch (Exception error) {
                runOnUiThread(activity, () -> {
                    dismiss(progressDialog);
                    showError(activity, error);
                });
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
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty APK response");
            }
            long totalBytes = body.contentLength();
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
        int flags = PackageManager.GET_META_DATA | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES);

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
            throw new IOException(activity.getString(R.string.update_invalid));
        }
        String archiveFlavor = archiveInfo.applicationInfo != null
                && archiveInfo.applicationInfo.metaData != null
                ? archiveInfo.applicationInfo.metaData.getString(UPDATE_FLAVOR_METADATA)
                : null;
        if (!updateRepositoryFlavor().equals(archiveFlavor)) {
            throw new IOException(activity.getString(R.string.update_invalid));
        }
        if (getVersionCode(archiveInfo) != manifest.versionCode
                || manifest.versionCode <= getVersionCode(installedInfo)) {
            throw new IOException(activity.getString(R.string.update_invalid));
        }

        Set<String> archiveCertificates = certificateDigests(archiveInfo);
        Set<String> installedCertificates = certificateDigests(installedInfo);
        if (!archiveCertificates.contains(manifest.signingCertificateSha256.toLowerCase(Locale.US))
                || !installedCertificates.contains(
                        manifest.signingCertificateSha256.toLowerCase(Locale.US))) {
            throw new IOException(activity.getString(R.string.update_invalid));
        }
    }


    private static Set<String> certificateDigests(PackageInfo packageInfo)
            throws NoSuchAlgorithmException
    {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageInfo.signingInfo != null) {
            signatures = packageInfo.signingInfo.getApkContentsSigners();
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


    private static long getVersionCode(PackageInfo packageInfo)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }


    private static void requestInstallation(Activity activity, File apkFile)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.update_install_permission_title)
                    .setMessage(R.string.update_install_permission_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.update_open_settings, (dialog, which) -> {
                        Intent settingsIntent = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settingsIntent);
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
        final long versionCode;
        final String versionName;
        final String apkUrl;
        final long apkSize;
        final String apkSha256;
        final String signingCertificateSha256;
        final String releaseNotes;

        private UpdateManifest(
                String applicationId,
                String flavor,
                long versionCode,
                String versionName,
                String apkUrl,
                long apkSize,
                String apkSha256,
                String signingCertificateSha256,
                String releaseNotes)
        {
            this.applicationId = applicationId;
            this.flavor = flavor;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.apkSha256 = apkSha256;
            this.signingCertificateSha256 = signingCertificateSha256;
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
                    json.getLong("versionCode"),
                    json.getString("versionName"),
                    json.getString("apkUrl"),
                    json.optLong("apkSize", -1),
                    json.getString("apkSha256"),
                    json.getString("signingCertificateSha256"),
                    json.optString("releaseNotes", "").trim());
        }
    }
}
