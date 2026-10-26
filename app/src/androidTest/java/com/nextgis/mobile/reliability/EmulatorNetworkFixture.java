package com.nextgis.mobile.reliability;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
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
        while (!matches(connected) && SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(50);
        assertTrue("Fixture connectivity did not reach " + (connected ? "connected Wi-Fi" : "offline"), matches(connected));
    }
    private boolean matches(boolean connected) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int expectedState = connected ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED;
        if (wifiManager.getWifiState() != expectedState) return false;
        if (!connected) return !new NetworkUtil(context).isNetworkAvailable();
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        // Mobile data disabling is asynchronous. An old cellular connection can still look
        // connected after svc wifi enable, then disappear during the upload under test.
        return info != null && info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected();
    }
    @Override public void close() throws Exception {
        shell(wifi != 0 ? "svc wifi enable" : "svc wifi disable");
        shell(data != 0 ? "svc data enable" : "svc data disable");
        // Finish the radio transition before the next test reverses it. Otherwise its
        // connectivity check can still see the previous Wi-Fi connection about to vanish.
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int expectedState = wifi != 0 ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED;
        long deadline = SystemClock.elapsedRealtime() + 15000;
        while (wifiManager.getWifiState() != expectedState && SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(50);
        assertEquals("Fixture Wi-Fi restoration", expectedState, wifiManager.getWifiState());
    }
    private static void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            com.nextgis.mobile.util.VerificationIo.readAll(stream);
        }
    }
}
