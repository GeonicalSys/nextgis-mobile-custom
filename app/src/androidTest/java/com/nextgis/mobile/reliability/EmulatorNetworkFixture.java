package com.nextgis.mobile.reliability;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.util.NetworkUtil;
import java.io.FileInputStream;
import static org.junit.Assert.*;

/** Changes connectivity only on an isolated emulator and restores its radio settings. */
public final class EmulatorNetworkFixture implements AutoCloseable {
    private final Context context;
    private final int wifi, data;
    public EmulatorNetworkFixture(Context context) {
        assertTrue("Isolated emulator only", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        this.context = context;
        wifi = Settings.Global.getInt(context.getContentResolver(), Settings.Global.WIFI_ON, 0);
        data = Settings.Global.getInt(context.getContentResolver(), "mobile_data", 0);
    }
    public void connected(boolean connected) throws Exception {
        shell("svc data disable");
        shell(connected ? "svc wifi enable" : "svc wifi disable");
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (new NetworkUtil(context).isNetworkAvailable() != connected && SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(50);
        assertEquals("Fixture connectivity", connected, new NetworkUtil(context).isNetworkAvailable());
    }
    @Override public void close() throws Exception {
        shell(wifi != 0 ? "svc wifi enable" : "svc wifi disable");
        shell(data != 0 ? "svc data enable" : "svc data disable");
    }
    private static void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            stream.readAllBytes();
        }
    }
}
