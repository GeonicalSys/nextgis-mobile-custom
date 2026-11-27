package com.nextgis.mobile.util;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;

/** An explicit, separately installed app owns all data used by phone and emulator checks. */
public final class VerificationTestRunner extends AndroidJUnitRunner {
    private boolean observesColdBasemap;

    @Override public void onCreate(Bundle arguments) {
        VerificationTestApplication.phoneOptIn = "true".equals(arguments.getString("verificationPhone"));
        VerificationTestApplication.memoryNoticeUnderTest = arguments.getString("class", "").contains("RenderMemorySafetyTest");
        String selected = arguments.getString("class", "");
        String coldBasemap = "com.nextgis.mobile.reliability.DefaultBasemapStartupTest";
        observesColdBasemap = selected.equals(coldBasemap) || selected.startsWith(coldBasemap + "#");
        String coldRecovery = "com.nextgis.mobile.reliability.ProjectRecoveryColdSyncTest";
        if (selected.equals(coldRecovery) || selected.startsWith(coldRecovery + "#")) {
            com.nextgis.mobile.reliability.ProjectRecoveryColdSyncTest
                    .captureBeforeApplicationCreate(getTargetContext());
        }
        super.onCreate(arguments);
    }

    @Override public void onStart() {
        waitForIdleSync();
        long deadline = android.os.SystemClock.uptimeMillis() + 30_000L;
        try {
            if (!VerificationTestApplication.startupCallbacks.await(30,
                    java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Verification startup callbacks did not complete");
            }
            com.nextgis.mobile.MainApplication application =
                    (com.nextgis.mobile.MainApplication) getTargetContext().getApplicationContext();
            DefaultBasemapInitializer basemap = application.getDefaultBasemapInitializer();
            if (observesColdBasemap) {
                // This read-only case observes the real worker while it owns the initial map.
                // Waiting for its ZIP here would prevent the case from observing cold startup.
                if (!basemap.snapshot().scheduled) {
                    throw new IllegalStateException("Cold basemap observation requires the real bootstrap");
                }
                super.onStart();
                return;
            }
            long remaining = deadline - android.os.SystemClock.uptimeMillis();
            if (remaining <= 0 || !basemap.awaitIdle(remaining)) {
                throw new IllegalStateException("Default basemap startup did not finish");
            }
            if (basemap.snapshot().failureType != null) {
                throw new IllegalStateException("Default basemap startup failed: " + basemap.snapshot().failureType);
            }
            while (com.nextgis.maplibui.util.SharedUnderlayProjects.isMigrationPending()
                    || com.nextgis.maplibui.util.ProjectOperationCoordinator.isBusy()) {
                if (android.os.SystemClock.uptimeMillis() >= deadline) {
                    throw new IllegalStateException("Verification startup workspace operations did not finish");
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verification startup wait interrupted", error);
        }
        super.onStart();
    }

    @Override public Application newApplication(ClassLoader loader, String name, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(getClass().getClassLoader(), VerificationTestApplication.class.getName(), context);
    }
}
