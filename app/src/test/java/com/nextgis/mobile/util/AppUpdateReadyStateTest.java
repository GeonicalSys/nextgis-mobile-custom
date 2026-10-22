package com.nextgis.mobile.util;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Bundle;

import androidx.work.NetworkType;

import com.nextgis.mobile.BuildConfig;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class AppUpdateReadyStateTest {
    private Context context;
    private JSONObject manifest;
    private File apk;

    @Before public void prepare() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_update_state", 0).edit().clear().commit();
        byte[] contents = "technical package archive double".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Signature signature = new Signature(new byte[]{1, 2, 3, 4});
        PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        installed.signatures = new Signature[]{signature};
        Shadows.shadowOf(context.getPackageManager()).installPackage(installed);
        long version = BuildConfig.VERSION_CODE + 1L;
        String sha = hex(MessageDigest.getInstance("SHA-256").digest(contents));
        manifest = new JSONObject().put("schemaVersion", 1).put("applicationId", context.getPackageName())
                .put("flavor", "debug").put("channel", "debug").put("versionCode", version)
                .put("versionName", "99.1").put("minSdk", 26).put("targetSdk", 36)
                .put("apkUrl", AppSettingsConstants.APK_VERSION_UPDATE + "/debug/releases/" + version + "/test.apk")
                .put("apkSize", contents.length).put("apkSha256", sha)
                .put("signingCertificateSha256", hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())))
                .put("publishedAt", "2026-10-23T00:00:00Z");
        apk = new File(context.getFilesDir(), "updates/update-" + version + "-" + sha + ".apk");
        assertTrue(apk.getParentFile().isDirectory() || apk.getParentFile().mkdirs());
        Files.write(apk.toPath(), contents);
        PackageInfo archive = new PackageInfo();
        archive.packageName = context.getPackageName();
        archive.setLongVersionCode(version);
        archive.versionName = "99.1";
        archive.signatures = new Signature[]{signature};
        archive.applicationInfo = new ApplicationInfo(installed.applicationInfo);
        archive.applicationInfo.metaData = new Bundle();
        archive.applicationInfo.metaData.putString("com.nextgis.mobile.UPDATE_FLAVOR", "debug");
        Shadows.shadowOf(context.getPackageManager()).setPackageArchiveInfo(apk.getAbsolutePath(), archive);
        save(manifest.toString());
    }

    private void save(String json) {
        assertTrue(context.getSharedPreferences("app_update_state", 0).edit().putString("ready_manifest", json).commit());
    }
    private void assertRejected() {
        assertNull(AppUpdateManager.readReadyUpdate(context));
        assertFalse(context.getSharedPreferences("app_update_state", 0).contains("ready_manifest"));
    }

    @Test public void checkedReadyArtifactCanBeReadOfflineWithoutOpeningTheInstaller() {
        assertNotNull(AppUpdateManager.readReadyUpdate(context));
        // Re-read durable preferences through another Context, as a new screen/process would.
        assertNotNull(AppUpdateManager.readReadyUpdate(new android.content.ContextWrapper(context)));
        assertNull(Shadows.shadowOf((Application) context).getNextStartedActivity());
    }

    @Test public void missingOrCorruptApkCannotProduceAReadyOffer() throws Exception {
        Files.write(apk.toPath(), new byte[]{1, 2});
        assertRejected();
    }

    @Test public void installedVersionClearsReadyState() throws Exception {
        manifest.put("versionCode", BuildConfig.VERSION_CODE);
        manifest.put("apkUrl", AppSettingsConstants.APK_VERSION_UPDATE + "/debug/releases/"
                + BuildConfig.VERSION_CODE + "/test.apk");
        save(manifest.toString());
        assertRejected();
    }

    @Test public void mismatchedProductAndCertificateAreRejected() throws Exception {
        String valid = manifest.toString();
        save(manifest.put("applicationId", "another.product").toString());
        assertRejected();
        save(new JSONObject(valid).put("signingCertificateSha256", "a".repeat(64)).toString());
        assertRejected();
    }

    @Test public void malformedReadyStateDoesNotBlockTheStartupCheck() {
        save("{broken");
        assertRejected();
    }

    @Test public void backgroundRequiresNonRoamingNetworkAndStorageWithoutRequiringWifi() {
        assertEquals(NetworkType.NOT_ROAMING, AppUpdateWorker.constraints().getRequiredNetworkType());
        assertTrue(AppUpdateWorker.constraints().requiresStorageNotLow());
        assertFalse(AppUpdateWorker.constraints().requiresCharging());
    }

    @Test public void apkUrlsRemainBoundToTheProductAndVersion() throws Exception {
        String valid = manifest.getString("apkUrl");
        long code = manifest.getLong("versionCode");
        AppUpdateManager.validateApkUrl(valid, "debug", code);
        for (String invalid : new String[]{valid.replace("https:", "http:"),
                valid.replace("/debug/", "/lisa/"), valid + "?token=unexpected",
                valid.replace("/test.apk", "/../test.apk"), "https://other.test/test.apk"}) {
            assertThrows(java.io.IOException.class, () -> AppUpdateManager.validateApkUrl(invalid, "debug", code));
        }
    }

    @Test @Config(qualifiers = "ru") public void laterKeepsReadyStateAndOnlyUpdateInvokesInstallation() {
        org.robolectric.android.controller.ActivityController<android.app.Activity> controller =
                org.robolectric.Robolectric.buildActivity(android.app.Activity.class);
        android.app.Activity activity = controller.get();
        activity.setTheme(com.nextgis.mobile.R.style.AppTheme);
        controller.create().start().resume().visible();
        java.util.concurrent.atomic.AtomicInteger installs = new java.util.concurrent.atomic.AtomicInteger();
        androidx.appcompat.app.AlertDialog dialog = AppUpdateManager.readyDialog(activity, "99.1", "", installs::incrementAndGet);
        try {
            dialog.show();
            assertEquals("Обновление готово", activity.getString(com.nextgis.mobile.R.string.update_ready_title));
            assertEquals("Обновить", dialog.getButton(-1).getText().toString());
            assertEquals("Позже", dialog.getButton(-2).getText().toString());
            assertEquals(0, installs.get());
            dialog.getButton(-2).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(0, installs.get());
            assertNotNull(AppUpdateManager.readReadyUpdate(context));
            dialog = AppUpdateManager.readyDialog(activity, "99.1", "", installs::incrementAndGet);
            dialog.show();
            dialog.getButton(-1).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1, installs.get());
        } finally {
            dialog.dismiss();
            controller.pause().stop().destroy();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format(Locale.US, "%02x", value & 255));
        return text.toString();
    }
}
