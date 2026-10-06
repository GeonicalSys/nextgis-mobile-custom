package com.nextgis.mobile.reliability;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.gnss.GnssDevice;
import com.nextgis.maplib.gnss.GnssInputPrefs;
import com.nextgis.mobile.R;
import com.nextgis.mobile.view.GnssDeviceAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GnssDeviceListTest {
    @Test public void sameNamesDisplayDifferentMeasuredSignals() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            GnssDeviceAdapter adapter = new GnssDeviceAdapter(context, Arrays.asList(
                    new GnssDevice(GnssInputPrefs.TRANSPORT_BLUETOOTH_LE, "rover", "PiGo Lite", -45),
                    new GnssDevice(GnssInputPrefs.TRANSPORT_BLUETOOTH_LE, "base", "PiGo Lite", -80)));
            FrameLayout parent = new FrameLayout(context);
            View row = adapter.getView(0, null, parent);
            assertEquals("PiGo Lite", ((TextView) row.findViewById(android.R.id.text1)).getText().toString());
            assertEquals(context.getString(R.string.gnss_signal_dbm, -45),
                    ((TextView) row.findViewById(android.R.id.text2)).getText().toString());
            row = adapter.getView(1, row, parent);
            assertEquals(context.getString(R.string.gnss_signal_dbm, -80),
                    ((TextView) row.findViewById(android.R.id.text2)).getText().toString());
            assertEquals("base", adapter.getItem(1).id);
        });
    }

    @Test public void recycledRowsDoNotRetainAnotherReceiversSignal() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            GnssDeviceAdapter adapter = new GnssDeviceAdapter(context, Arrays.asList(
                    new GnssDevice(GnssInputPrefs.TRANSPORT_BLUETOOTH_CLASSIC, "r", "Receiver", -42),
                    new GnssDevice(GnssInputPrefs.TRANSPORT_BLUETOOTH_CLASSIC, "b", "Receiver"),
                    new GnssDevice(GnssInputPrefs.TRANSPORT_USB, "usb", "USB receiver")));
            FrameLayout parent = new FrameLayout(context);
            View row = adapter.getView(0, null, parent);
            row = adapter.getView(1, row, parent);
            assertEquals(context.getString(R.string.gnss_signal_unknown),
                    ((TextView) row.findViewById(android.R.id.text2)).getText().toString());
            assertEquals(View.VISIBLE, row.findViewById(android.R.id.text2).getVisibility());
            row = adapter.getView(2, row, parent);
            assertEquals(View.GONE, row.findViewById(android.R.id.text2).getVisibility());
            row = adapter.getView(0, row, parent);
            assertEquals(View.VISIBLE, row.findViewById(android.R.id.text2).getVisibility());
        });
    }
}
