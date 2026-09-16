package com.nextgis.mobile.activity;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.nextgis.maplib.gnss.GnssDevice;
import com.nextgis.maplib.gnss.GnssDeviceScanner;
import com.nextgis.maplib.gnss.GnssInputPrefs;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.mobile.R;

import java.util.ArrayList;
import java.util.List;

/** Pick Bluetooth Classic/LE, USB or TCP NMEA without Mock Location. */
public class ExternalGnssActivity extends AppCompatActivity {
    private static final int REQUEST_BLUETOOTH = 812;

    private SharedPreferences prefs;
    private GnssDeviceScanner scanner;
    private ArrayAdapter<GnssDevice> adapter;
    private final List<GnssDevice> devices = new ArrayList<>();
    private RadioGroup transportGroup;
    private View tcpFields;
    private Button findButton;
    private EditText host;
    private EditText port;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_external_gnss);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.gnss_external_title);
        }
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        scanner = new GnssDeviceScanner(this);
        transportGroup = findViewById(R.id.gnss_transport_group);
        tcpFields = findViewById(R.id.gnss_tcp_fields);
        findButton = findViewById(R.id.gnss_find_devices);
        host = findViewById(R.id.gnss_tcp_host);
        port = findViewById(R.id.gnss_tcp_port);
        ListView list = findViewById(R.id.gnss_device_list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devices);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> select(devices.get(position)));

        selectTransportRadio(GnssInputPrefs.transport(prefs));
        host.setText(GnssInputPrefs.tcpHost(prefs));
        port.setText(String.valueOf(GnssInputPrefs.tcpPort(prefs)));
        showTransportUi();
        transportGroup.setOnCheckedChangeListener((group, checkedId) -> {
            persistTransport();
            showTransportUi();
            refreshList();
        });
        findButton.setOnClickListener(v -> findDevices());
        findViewById(R.id.gnss_tcp_save).setOnClickListener(v -> saveTcp());
        refreshList();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        scanner.stopBleScan();
        super.onDestroy();
    }

    private void showTransportUi() {
        boolean tcp = GnssInputPrefs.TRANSPORT_TCP.equals(currentTransport());
        tcpFields.setVisibility(tcp ? View.VISIBLE : View.GONE);
        findButton.setVisibility(tcp ? View.GONE : View.VISIBLE);
    }

    private String currentTransport() {
        int id = transportGroup.getCheckedRadioButtonId();
        if (id == R.id.gnss_transport_le) {
            return GnssInputPrefs.TRANSPORT_BLUETOOTH_LE;
        }
        if (id == R.id.gnss_transport_usb) {
            return GnssInputPrefs.TRANSPORT_USB;
        }
        if (id == R.id.gnss_transport_tcp) {
            return GnssInputPrefs.TRANSPORT_TCP;
        }
        return GnssInputPrefs.TRANSPORT_BLUETOOTH_CLASSIC;
    }

    private void selectTransportRadio(String transport) {
        int id = R.id.gnss_transport_classic;
        if (GnssInputPrefs.TRANSPORT_BLUETOOTH_LE.equals(transport)) {
            id = R.id.gnss_transport_le;
        } else if (GnssInputPrefs.TRANSPORT_USB.equals(transport)) {
            id = R.id.gnss_transport_usb;
        } else if (GnssInputPrefs.TRANSPORT_TCP.equals(transport)) {
            id = R.id.gnss_transport_tcp;
        }
        transportGroup.check(id);
    }

    private void persistTransport() {
        prefs.edit().putString(SettingsConstants.KEY_PREF_GNSS_TRANSPORT, currentTransport()).apply();
    }

    private void findDevices() {
        String transport = currentTransport();
        if (GnssInputPrefs.TRANSPORT_BLUETOOTH_CLASSIC.equals(transport)
                || GnssInputPrefs.TRANSPORT_BLUETOOTH_LE.equals(transport)) {
            if (!ensureBluetoothPermission()) {
                return;
            }
        }
        refreshList();
        if (GnssInputPrefs.TRANSPORT_BLUETOOTH_LE.equals(transport)) {
            scanner.startBleScan(found -> runOnUiThread(() -> {
                devices.clear();
                devices.addAll(found);
                adapter.notifyDataSetChanged();
            }));
        }
    }

    private void refreshList() {
        scanner.stopBleScan();
        devices.clear();
        String transport = currentTransport();
        if (GnssInputPrefs.TRANSPORT_BLUETOOTH_CLASSIC.equals(transport)) {
            devices.addAll(scanner.bondedClassic());
        } else if (GnssInputPrefs.TRANSPORT_USB.equals(transport)) {
            devices.addAll(scanner.usbDevices());
        }
        adapter.notifyDataSetChanged();
        if (devices.isEmpty() && !GnssInputPrefs.TRANSPORT_TCP.equals(transport)
                && !GnssInputPrefs.TRANSPORT_BLUETOOTH_LE.equals(transport)) {
            Toast.makeText(this, R.string.gnss_no_devices, Toast.LENGTH_SHORT).show();
        }
    }

    private void select(GnssDevice device) {
        prefs.edit()
                .putString(SettingsConstants.KEY_PREF_GNSS_TRANSPORT, device.transport)
                .putString(SettingsConstants.KEY_PREF_GNSS_DEVICE_ID, device.id)
                .putString(SettingsConstants.KEY_PREF_GNSS_DEVICE_NAME, device.displayName)
                .apply();
        Toast.makeText(this, device.displayName, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void saveTcp() {
        String hostValue = host.getText() == null ? "" : host.getText().toString().trim();
        String portValue = port.getText() == null ? "9001" : port.getText().toString().trim();
        if (hostValue.isEmpty()) {
            Toast.makeText(this, R.string.gnss_tcp_host_required, Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
                .putString(SettingsConstants.KEY_PREF_GNSS_TRANSPORT, GnssInputPrefs.TRANSPORT_TCP)
                .putString(SettingsConstants.KEY_PREF_GNSS_TCP_HOST, hostValue)
                .putString(SettingsConstants.KEY_PREF_GNSS_TCP_PORT, portValue)
                .putString(SettingsConstants.KEY_PREF_GNSS_DEVICE_ID, hostValue + ":" + portValue)
                .putString(SettingsConstants.KEY_PREF_GNSS_DEVICE_NAME, hostValue + ":" + portValue)
                .apply();
        finish();
    }

    private boolean ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        List<String> missing = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (missing.isEmpty()) {
            return true;
        }
        ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQUEST_BLUETOOTH);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.gnss_bluetooth_permission, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            findDevices();
        }
    }
}
