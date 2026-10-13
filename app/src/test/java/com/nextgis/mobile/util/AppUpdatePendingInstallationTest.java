package com.nextgis.mobile.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.nextgis.mobile.BuildConfig;
import com.nextgis.mobile.R;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, qualifiers = "ru")
public class AppUpdatePendingInstallationTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private SharedPreferences pending;
    private static final String KEY = "pending_install_manifest";

    @Before public void setUp() {
        controller = Robolectric.buildActivity(Activity.class);
        activity = controller.get();
        activity.setTheme(R.style.AppTheme);
        controller.create().start().resume().visible();
        pending = activity.getSharedPreferences("app_update_state", Context.MODE_PRIVATE);
        pending.edit().clear().commit();
        Shadows.shadowOf(activity.getPackageManager()).setCanRequestPackageInstalls(true);
    }

    @After public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test public void malformedPendingStateDoesNotSuppressTheStartupCheck() {
        pending.edit().putString(KEY, "{invalid").commit();
        assertFalse(AppUpdateManager.resumePendingInstallation(activity));
        assertFalse(pending.contains(KEY));
    }

    @Test public void alreadyInstalledArtifactDoesNotSuppressCheckingForANewerRelease() throws Exception {
        pending.edit().putString(KEY, manifest().toString()).commit();
        assertFalse(AppUpdateManager.resumePendingInstallation(activity));
        assertFalse(pending.contains(KEY));
    }

    @Test public void deniedInstallPermissionClearsContinuationWithoutMarkingACheckCompleted() throws Exception {
        Shadows.shadowOf(activity.getPackageManager()).setCanRequestPackageInstalls(false);
        pending.edit().putString(KEY, manifest().toString()).commit();
        assertFalse(AppUpdateManager.resumePendingInstallation(activity));
        assertFalse(pending.contains(KEY));
    }

    @Test public void invalidProductManifestCannotSuppressTheCheckOrReachTheInstaller() throws Exception {
        JSONObject manifest = manifest();
        manifest.put("applicationId", "invalid.product");
        pending.edit().putString(KEY, manifest.toString()).commit();
        assertFalse(AppUpdateManager.resumePendingInstallation(activity));
        assertFalse(pending.contains(KEY));
    }

    private JSONObject manifest() throws Exception {
        return new JSONObject().put("schemaVersion", 1)
                .put("applicationId", activity.getPackageName()).put("flavor", "debug").put("channel", "debug")
                .put("versionCode", BuildConfig.VERSION_CODE).put("versionName", BuildConfig.VERSION_NAME)
                .put("minSdk", 26).put("targetSdk", 36).put("apkSize", 100)
                .put("apkUrl", AppSettingsConstants.APK_VERSION_UPDATE + "/debug/releases/"
                        + BuildConfig.VERSION_CODE + "/test.apk")
                .put("apkSha256", "a".repeat(64)).put("signingCertificateSha256", "b".repeat(64))
                .put("publishedAt", "2026-10-13T00:00:00Z");
    }
}
