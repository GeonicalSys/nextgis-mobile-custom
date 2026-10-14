package com.nextgis.mobile.util;

import android.os.Build;
import android.preference.PreferenceManager;
import com.nextgis.mobile.BuildConfig;
import com.nextgis.mobile.MainApplication;

public final class VerificationTestApplication extends MainApplication {
    static boolean phoneOptIn;

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
        if (!VerificationEnvironment.isEmulator()) {
            // These preferences belong only to this disposable app, never to the user's app/device.
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean("background_gps_enabled", false)
                    .putBoolean("battery_dont_show_pref", true).apply();
        }
        super.onCreate();
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
        AppDiagnostics.initialize(this, "http://synthetic@127.0.0.1:18539/1");
    }
}
