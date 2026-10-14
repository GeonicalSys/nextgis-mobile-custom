package com.nextgis.mobile.util;

import android.content.Context;
import android.os.Build;
import com.nextgis.mobile.BuildConfig;

/** Keep production and the ordinary Debug companion out of physical destructive fixtures. */
public final class VerificationEnvironment {
    public static final String PACKAGE = "com.nextgis.mobile.verification.debug";
    private VerificationEnvironment() { }

    public static boolean isEmulator() {
        return "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE);
    }

    public static void requireIsolated(Context context) {
        if (BuildConfig.DEBUG && BuildConfig.FIELD_VERIFICATION && PACKAGE.equals(context.getPackageName())) return;
        if (BuildConfig.DEBUG && isEmulator() && "com.nextgis.mobile.debug".equals(context.getPackageName())) return;
        throw new IllegalStateException("Synthetic fixtures require the isolated verification APK or a Debug emulator");
    }
}
