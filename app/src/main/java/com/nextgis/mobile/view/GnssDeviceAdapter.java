package com.nextgis.mobile.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.nextgis.maplib.gnss.GnssDevice;
import com.nextgis.maplib.gnss.GnssInputPrefs;
import com.nextgis.mobile.R;
import java.util.List;

/** Receiver identity stays on the first line; a scan measurement is transient secondary text. */
public final class GnssDeviceAdapter extends ArrayAdapter<GnssDevice> {
    public GnssDeviceAdapter(Context context, List<GnssDevice> devices) {
        super(context, android.R.layout.simple_list_item_2, android.R.id.text1, devices);
    }

    @NonNull @Override public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View row = super.getView(position, convertView, parent);
        GnssDevice device = getItem(position);
        TextView name = row.findViewById(android.R.id.text1);
        TextView signal = row.findViewById(android.R.id.text2);
        name.setText(device.displayName);
        boolean bluetooth = GnssInputPrefs.TRANSPORT_BLUETOOTH_CLASSIC.equals(device.transport)
                || GnssInputPrefs.TRANSPORT_BLUETOOTH_LE.equals(device.transport);
        signal.setVisibility(bluetooth ? View.VISIBLE : View.GONE);
        signal.setText(device.rssiDbm == null ? getContext().getString(R.string.gnss_signal_unknown)
                : getContext().getString(R.string.gnss_signal_dbm, device.rssiDbm));
        return row;
    }
}
