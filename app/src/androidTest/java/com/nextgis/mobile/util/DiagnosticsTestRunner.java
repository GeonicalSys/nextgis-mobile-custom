package com.nextgis.mobile.util;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;

/** Explicit runner for synthetic diagnostic tests, never the normal application runner. */
public final class DiagnosticsTestRunner extends AndroidJUnitRunner {
    @Override public Application newApplication(ClassLoader loader, String name, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(getClass().getClassLoader(), DiagnosticsTestApplication.class.getName(), context);
    }

    @Override public void onCreate(Bundle arguments) {
        DiagnosticsTestApplication.dsn = arguments.getString("diagnosticsDsn", "");
        DiagnosticsTestApplication.run = arguments.getString("diagnosticsRun", "local-test");
        super.onCreate(arguments);
    }
}
