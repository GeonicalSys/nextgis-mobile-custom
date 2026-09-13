package com.nextgis.mobile.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.UnderlayFiles;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.ProjectSettingsActivity;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.*;

/** User-confirmed Debug companion update with its own durable permission/install continuation. */
public final class DebugCompanionInstaller {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder().followRedirects(false)
            .followSslRedirects(false).connectTimeout(30, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).build();
    private DebugCompanionInstaller() { }
    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences("debug_companion_install", Context.MODE_PRIVATE);
    }
    public static boolean hasExporter(Context context) {
        return LegacyUnderlayMigrationContract.isTrustedDebugSourceInstalled(context)
                && LegacyUnderlayMigrationContract.createExportIntent().resolveActivity(context.getPackageManager()) != null;
    }
    public static boolean needsUpdate(Context context) {
        return LegacyUnderlayMigrationContract.isGeonicalTarget(context)
                && LegacyUnderlayMigrationContract.isTrustedDebugSourceInstalled(context) && !hasExporter(context);
    }
    public static boolean offer(Activity activity, boolean fromProject) {
        if (!needsUpdate(activity) || BUSY.get() || AppUpdateManager.isBusyOrPending(activity)) return false;
        new AlertDialog.Builder(activity).setTitle(R.string.companion_title).setMessage(R.string.companion_offer)
                .setNegativeButton(R.string.companion_later, null)
                .setPositiveButton(R.string.companion_update, (dialog, which) -> download(activity, fromProject)).show();
        return true;
    }
    private static void download(Activity activity, boolean fromProject) {
        if (!BUSY.compareAndSet(false, true)) return;
        ProgressDialog progress = progress(activity);
        Context context = activity.getApplicationContext();
        CollectorProjectRegistry.ProjectInfo project = CollectorProjectRegistry.getActiveProject(context);
        String uid = fromProject && project != null ? project.getProjectUid() : "";
        IO.execute(() -> {
            try {
                if (!needsUpdate(context)) throw new IOException("Companion is no longer eligible");
                DebugCompanionPolicy.Manifest manifest;
                try (Response response = HTTP.newCall(new Request.Builder().url(DebugCompanionPolicy.MANIFEST_URL)
                        .cacheControl(new CacheControl.Builder().noCache().noStore().build()).build()).execute()) {
                    if (!response.isSuccessful() || response.body() == null) throw new IOException("Companion manifest request failed");
                    manifest = new DebugCompanionPolicy.Manifest(new JSONObject(new String(
                            UnderlayFiles.readBounded(response.body().byteStream(), 512 * 1024), StandardCharsets.UTF_8)), Build.VERSION.SDK_INT);
                }
                if (manifest.code <= AppUpdateManager.getVersionCode(installed(context))) throw new IOException("No newer companion available");
                File apk = cached(context, manifest);
                if (!matches(apk, manifest)) downloadFile(manifest, apk);
                validate(context, manifest, apk);
                if (!state(context).edit().putString("manifest", manifest.json.toString()).putString("phase", "ready")
                        .putString("project", uid).commit()) throw new IOException("Cannot persist companion continuation");
                activity.runOnUiThread(() -> {
                    dismiss(progress);
                    if (usable(activity)) install(activity, manifest, apk);
                });
            } catch (Exception error) { fail(activity, progress, error); }
            finally { BUSY.set(false); }
        });
    }
    /** Call after self-update continuation, only from an unobscured resumed screen. */
    public static boolean resume(Activity activity, Runnable confirmTransfer) {
        if (!LegacyUnderlayMigrationContract.isGeonicalTarget(activity) || !usable(activity)) return false;
        SharedPreferences pending = state(activity);
        String json = pending.getString("manifest", null);
        if (json == null) return false;
        if (BUSY.get()) return true;
        try {
            DebugCompanionPolicy.Manifest manifest = new DebugCompanionPolicy.Manifest(new JSONObject(json), Build.VERSION.SDK_INT);
            PackageInfo installed = installed(activity);
            if (hasExporter(activity) && AppUpdateManager.getVersionCode(installed) >= manifest.code) {
                String uid = pending.getString("project", "");
                CollectorProjectRegistry.ProjectInfo project = CollectorProjectRegistry.getActiveProject(activity);
                boolean resumeTransfer = !uid.isEmpty() && project != null && uid.equals(project.getProjectUid());
                if (resumeTransfer && confirmTransfer == null) {
                    activity.startActivity(new Intent(activity, ProjectSettingsActivity.class));
                } else {
                    clear(activity);
                    if (resumeTransfer) confirmTransfer.run();
                    else Toast.makeText(activity, R.string.companion_ready, Toast.LENGTH_LONG).show();
                }
                return true;
            }
            String phase = pending.getString("phase", "ready");
            if ("installing".equals(phase)) {
                clear(activity);
                Toast.makeText(activity, R.string.companion_not_installed, Toast.LENGTH_LONG).show();
                return true;
            }
            if ("permission".equals(phase) && Build.VERSION.SDK_INT >= 26
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                clear(activity);
                Toast.makeText(activity, R.string.update_install_permission_not_granted, Toast.LENGTH_LONG).show();
                return true;
            }
            if (!BUSY.compareAndSet(false, true)) return true;
            ProgressDialog progress = progress(activity);
            IO.execute(() -> {
                try {
                    File apk = cached(activity, manifest);
                    validate(activity, manifest, apk);
                    activity.runOnUiThread(() -> { dismiss(progress); if (usable(activity)) install(activity, manifest, apk); });
                } catch (Exception error) { clear(activity); fail(activity, progress, error); }
                finally { BUSY.set(false); }
            });
        } catch (Exception error) { clear(activity); fail(activity, null, error); }
        return true;
    }
    private static void install(Activity activity, DebugCompanionPolicy.Manifest manifest, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle(R.string.update_install_permission_title)
                    .setMessage(R.string.update_install_permission_message)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> clear(activity))
                    .setOnCancelListener(d -> clear(activity))
                    .setPositiveButton(R.string.update_open_settings, (d, w) -> {
                        try {
                            phase(activity, "permission");
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                        } catch (Exception e) { clear(activity); fail(activity, null, e); }
                    }).show();
            return;
        }
        try {
            phase(activity, "installing");
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".easypicker.provider", apk);
            activity.startActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception e) { clear(activity); fail(activity, null, e); }
    }
    private static PackageInfo installed(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(DebugCompanionPolicy.PACKAGE, flags());
    }
    private static int flags() {
        return PackageManager.GET_META_DATA | PackageManager.GET_ACTIVITIES | AppUpdatePackageInfoPolicy.signatureFlagsForSdk(Build.VERSION.SDK_INT,
                PackageManager.GET_SIGNATURES, PackageManager.GET_SIGNING_CERTIFICATES);
    }
    private static void validate(Context context, DebugCompanionPolicy.Manifest manifest, File apk) throws Exception {
        if (!matches(apk, manifest)) throw new IOException("Companion file checksum mismatch");
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), flags());
        PackageInfo installed = installed(context);
        if (archive == null) throw new IOException("Companion APK cannot be read");
        boolean exporter = false;
        if (archive.activities != null) for (android.content.pm.ActivityInfo candidate : archive.activities)
            if (candidate.enabled && candidate.exported && "com.nextgis.mobile.activity.LegacyUnderlayExportActivity".equals(candidate.name)) exporter = true;
        if (!exporter) throw new IOException("Published Debug does not yet contain the underlay exporter");
        String flavor = archive.applicationInfo != null && archive.applicationInfo.metaData != null
                ? archive.applicationInfo.metaData.getString("com.nextgis.mobile.UPDATE_FLAVOR") : null;
        DebugCompanionPolicy.validateApk(manifest, archive.packageName, flavor, AppUpdateManager.getVersionCode(archive),
                archive.versionName, AppUpdateManager.getVersionCode(installed), AppUpdateManager.certificateDigests(archive),
                AppUpdateManager.certificateDigests(installed));
    }
    private static File cached(Context context, DebugCompanionPolicy.Manifest manifest) throws IOException {
        File dir = new File(context.getCacheDir(), "debug-companion");
        UnderlayFiles.directory(dir);
        return new File(dir, "debug-" + manifest.code + ".apk");
    }
    private static boolean matches(File file, DebugCompanionPolicy.Manifest manifest) throws IOException {
        if (!file.isFile() || file.length() != manifest.size) return false;
        try (InputStream in = new FileInputStream(file)) { return manifest.hash.equalsIgnoreCase(UnderlayFiles.sha256(in)); }
    }
    private static void downloadFile(DebugCompanionPolicy.Manifest manifest, File target) throws IOException {
        File partial = new File(target.getParentFile(), target.getName() + ".partial");
        try {
            try (Response response = HTTP.newCall(new Request.Builder().url(manifest.url).build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IOException("Companion APK request failed");
                long declared = response.body().contentLength();
                if (declared > 0 && declared != manifest.size) throw new IOException("Companion length mismatch");
                try (InputStream in = response.body().byteStream(); FileOutputStream out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[128 * 1024]; long total = 0; int count;
                    while ((count = in.read(buffer)) != -1) {
                        total += count;
                        if (total > manifest.size) throw new IOException("Companion exceeds expected size");
                        out.write(buffer, 0, count);
                    }
                    out.getFD().sync();
                }
            }
            if (!matches(partial, manifest)) throw new IOException("Companion download checksum mismatch");
            java.nio.file.Files.move(partial.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { if (partial.exists() && !partial.delete()) HyperLog.w(Constants.TAG, "Companion partial cleanup deferred"); }
    }
    private static void phase(Context context, String value) throws IOException {
        if (!state(context).edit().putString("phase", value).commit()) throw new IOException("Cannot persist companion phase");
    }
    private static void clear(Context context) { state(context).edit().clear().commit(); }
    private static ProgressDialog progress(Activity activity) {
        ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setTitle(R.string.companion_title); dialog.setMessage(activity.getString(R.string.companion_loading));
        dialog.setIndeterminate(true); dialog.setCancelable(false); dialog.show(); return dialog;
    }
    private static boolean usable(Activity activity) { return !activity.isFinishing() && !activity.isDestroyed(); }
    private static void dismiss(ProgressDialog progress) { if (progress != null && progress.isShowing()) progress.dismiss(); }
    private static void fail(Activity activity, ProgressDialog progress, Exception error) {
        HyperLog.w(Constants.TAG, "Debug companion update failed", error);
        activity.runOnUiThread(() -> {
            dismiss(progress);
            if (usable(activity)) new AlertDialog.Builder(activity).setTitle(R.string.companion_title)
                    .setMessage(R.string.companion_failed).setPositiveButton(android.R.string.ok, null).show();
        });
    }
}
