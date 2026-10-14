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

    @Override public Application newApplication(ClassLoader loader, String name, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(getClass().getClassLoader(), VerificationTestApplication.class.getName(), context);
    }
}
