package com.nextgis.mobile.util;

import android.os.Build;
import com.nextgis.mobile.MainApplication;
import io.sentry.Sentry;

public final class DiagnosticsTestApplication extends MainApplication {
    static String dsn = "";
    static String run = "";

    @Override protected void initializeDiagnostics() {
        if ((!Build.HARDWARE.equals("ranchu") && !Build.HARDWARE.equals("goldfish"))
                || !getPackageName().endsWith(".debug") || dsn.isEmpty()) {
            throw new IllegalStateException("Diagnostic delivery tests require an isolated Debug emulator and an explicit test DSN");
        }
        AppDiagnostics.initialize(this, dsn);
        Sentry.setTag("verification_run", run);
    }
}
