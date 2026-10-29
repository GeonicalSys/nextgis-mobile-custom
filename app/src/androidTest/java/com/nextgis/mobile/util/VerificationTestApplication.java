package com.nextgis.mobile.util;

import android.os.Build;
import android.preference.PreferenceManager;
import com.nextgis.mobile.BuildConfig;
import com.nextgis.mobile.MainApplication;

public final class VerificationTestApplication extends MainApplication {
    static boolean phoneOptIn;
    static boolean memoryNoticeUnderTest;
    static final java.util.concurrent.CountDownLatch startupCallbacks =
            new java.util.concurrent.CountDownLatch(1);

    @Override public void onCreate() {
        // The instrumented isolated script process cannot access app preferences or device APIs.
        // Preserve MainApplication's early sandbox branch before all verification setup too.
        if (com.nextgis.maplib.scripts.ProjectScriptProcess.isSandbox(this)) {
            super.onCreate();
            return;
        }
        if (!BuildConfig.FIELD_VERIFICATION || !getPackageName().equals(VerificationEnvironment.PACKAGE))
            throw new IllegalStateException("Verification requires its separate test-only APK identity");
        if (!VerificationEnvironment.isEmulator() && !phoneOptIn)
            throw new IllegalStateException("A physical verification phone must be explicitly selected");
        // Unrelated gesture/layout cases acknowledge this one known onboarding notice.
        // The dedicated memory suite keeps production delivery and tests it across recreation.
        if (!memoryNoticeUnderTest) RenderMemoryNotice.claim();
        if (!VerificationEnvironment.isEmulator()) {
            // These preferences belong only to this disposable app, never to the user's app/device.
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean("background_gps_enabled", false)
                    .putBoolean("battery_dont_show_pref", true).apply();
        }
        super.onCreate();
        // MainApplication posts underlay migration and journal recovery at +3 seconds.
        // The instrumentation thread must not create synthetic queues/projects before those
        // real startup callbacks have observed the initially empty verification workspace.
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(startupCallbacks::countDown, 3_000L);
        registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(android.app.Activity activity,android.os.Bundle state) {
                activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            @Override public void onActivityStarted(android.app.Activity activity) {}
            @Override public void onActivityResumed(android.app.Activity activity) {}
            @Override public void onActivityPaused(android.app.Activity activity) {}
            @Override public void onActivityStopped(android.app.Activity activity) {}
            @Override public void onActivitySaveInstanceState(android.app.Activity activity,android.os.Bundle state) {}
            @Override public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }

    @Override protected void initializeDiagnostics() {
        // pm clear resets this test workspace, but Android retains OS exit history from
        // earlier disposable runs. Do not import that history into an unrelated new fixture.
        // Owned marker/replay phases survive reset=False and exercise the real fallback.
        java.io.File marker = new java.io.File(getFilesDir(), "diagnostic-process.json");
        if (!marker.exists()) try {
            java.nio.file.Files.write(marker.toPath(), "{\"history_scanned\":true,\"pending\":[]}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        AppDiagnostics.initialize(this, "http://synthetic@127.0.0.1:18539/1");
    }
}
