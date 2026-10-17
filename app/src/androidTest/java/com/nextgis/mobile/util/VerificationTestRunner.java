package com.nextgis.mobile.util;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;

/** An explicit, separately installed app owns all data used by phone and emulator checks. */
public final class VerificationTestRunner extends AndroidJUnitRunner {
    @Override public void onCreate(Bundle arguments) {
        VerificationTestApplication.phoneOptIn = "true".equals(arguments.getString("verificationPhone"));
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
