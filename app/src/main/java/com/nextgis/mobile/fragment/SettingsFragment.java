/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015-2020 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.mobile.fragment;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.NOTIFICATION_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.gnss.GnssInputPrefs;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplib.util.Constants;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.fragment.NGPreferenceSettingsFragment;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.mobile.BuildConfig;
import com.nextgis.mobile.MainApplication;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.ExternalGnssActivity;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppConstants;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.AppUpdateManager;
import com.nextgis.mobile.util.CustomPreference;
import com.nextgis.mobile.util.IntEditTextPreference;
import com.nextgis.mobile.util.SelectMapPathPreference;
import com.nextgis.mobile.util.WalkEmergencyReset;
import com.nextgis.mobile.stakeout.StakeoutSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static com.nextgis.maplib.util.Constants.ATTACHMENTS_NAME_POSTFIX;
import static com.nextgis.maplib.util.Constants.CHANGES_NAME_POSTFIX;
import static com.nextgis.maplib.util.Constants.MAP_EXT;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_MAP;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_SD_CARD_NAME;
import static com.nextgis.maplib.util.SettingsConstants.KEY_PREF_UNITS;
import static com.nextgis.maplibui.service.TrackerService.getUid;
import static com.nextgis.maplibui.service.TrackerService.isTrackerServiceRunning;
import static com.nextgis.mobile.util.AppSettingsConstants.KEY_PREF_SHOW_COMPASS;
import static com.nextgis.mobile.util.AppSettingsConstants.KEY_PREF_SHOW_MEASURING;
import static com.nextgis.mobile.util.AppSettingsConstants.KEY_PREF_SHOW_SCALE_RULER;
import static com.nextgis.mobile.util.AppSettingsConstants.KEY_PREF_SHOW_ZOOM_CONTROLS;


public class SettingsFragment
        extends NGPreferenceSettingsFragment
        implements SelectMapPathPreference.OnAttachedListener
{

    public static final int REQUEST_NOTIFICATION_PERMISSION = 741;

    @Override
    protected void createPreferences(PreferenceScreen screen)
    {
        if ( !isAdded()) {
            return;
        }

        switch (mAction) {
            case SettingsConstantsUI.ACTION_PREFS_GENERAL:
                //addPreferencesFromResource(R.xml.preferences_general);
                setPreferencesFromResource(R.xml.preferences_general, null);

                final ListPreference theme =
                        (ListPreference) findPreference(SettingsConstantsUI.KEY_PREF_THEME);
                initializeTheme(getActivity(), theme);
                final Preference reset =
                        findPreference(SettingsConstantsUI.KEY_PREF_RESET_SETTINGS);
                initializeReset(getActivity(), reset);
                final Preference resetWalk = findPreference(WalkEmergencyReset.KEY_PREFERENCE);
                initializeWalkEmergencyReset(getActivity(), resetWalk);
                final Preference notify =
                        findPreference(SettingsConstantsUI.KEY_PREF_SHOW_SYNC);
                initializeNotification(getActivity(), notify);
                final Preference update =
                        findPreference(AppSettingsConstants.KEY_PREF_UPDATE_CHECK);
                if (update != null) {
                    update.setSummary(
                            getString(R.string.update_current_version, BuildConfig.VERSION_NAME));
                    update.setOnPreferenceClickListener(preference -> {
                        Activity activity = getActivity();
                        if (activity != null) {
                            AppUpdateManager.checkForUpdate(activity);
                        }
                        return true;
                    });
                }
                final ListPreference backupQuota = (ListPreference) findPreference(
                        AppSettingsConstants.KEY_PREF_LAYER_BACKUP_MAX_GB);
                initializeLayerBackupQuota(getActivity(), backupQuota);

                break;
            case SettingsConstantsUI.ACTION_PREFS_MAP:
                setPreferencesFromResource(R.xml.preferences_map, null);
                //addPreferencesFromResource(R.xml.preferences_map);

                final ListPreference showInfoPanel = (ListPreference) findPreference(
                        SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL);
                initializeShowStatusPanel(showInfoPanel);

                final ListPreference lpCoordinateFormat =
                        (ListPreference) findPreference(SettingsConstantsUI.KEY_PREF_COORD_FORMAT);
                final IntEditTextPreference etCoordinateFraction =
                        (IntEditTextPreference) findPreference(
                                SettingsConstantsUI.KEY_PREF_COORD_FRACTION);
                initializeCoordinates(lpCoordinateFormat, etCoordinateFraction);

                final ListPreference lpUnits = (ListPreference) findPreference(KEY_PREF_UNITS);
                initializeUnits(lpUnits);

                final SelectMapPathPreference mapPath = (SelectMapPathPreference) findPreference(
                        SettingsConstants.KEY_PREF_MAP_PATH);
                mapPath.setOnAttachedListener(this);
                initializeMapPath(getActivity(), mapPath);

                final ListPreference showCurrentLocation = (ListPreference) findPreference(
                        SettingsConstantsUI.KEY_PREF_SHOW_CURRENT_LOC);
                initializeShowCurrentLocation(showCurrentLocation);

                final ListPreference changeMapBG =
                        (ListPreference) findPreference(SettingsConstantsUI.KEY_PREF_MAP_BG);
                initializeMapBG(changeMapBG, getContext());

                final Preference restore =
                        findPreference(SettingsConstantsUI.KEY_PREF_RESTORE_LAYERS);
                initializeRestore(getActivity(), restore);
                break;
            case SettingsConstantsUI.ACTION_PREFS_LOCATION:
                //addPreferencesFromResource(R.xml.preferences_location);
                setPreferencesFromResource(R.xml.preferences_location, null);

                final ListPreference lpLocationAccuracy =
                        (ListPreference) findPreference(SettingsConstants.KEY_PREF_LOCATION_SOURCE);
                initializeLocationAccuracy(lpLocationAccuracy, false);
                initializeGnssInput(
                        (ListPreference) findPreference(SettingsConstants.KEY_PREF_GNSS_INPUT),
                        findPreference(SettingsConstants.KEY_PREF_GNSS_DEVICE));

                final ListPreference minTimeLoc = (ListPreference) findPreference(
                        SettingsConstants.KEY_PREF_LOCATION_MIN_TIME);
                final ListPreference minDistanceLoc = (ListPreference) findPreference(
                        SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE);
                initializeLocationMins(minTimeLoc, minDistanceLoc, false);

                final EditTextPreference accurateMaxCount = (EditTextPreference) findPreference(
                        SettingsConstants.KEY_PREF_LOCATION_ACCURATE_COUNT);
                initializeAccurateTaking(accurateMaxCount);

                initializeStakeoutThresholds(
                        (EditTextPreference) findPreference(StakeoutSettings.KEY_FAR_DISTANCE),
                        (EditTextPreference) findPreference(StakeoutSettings.KEY_MEDIUM_DISTANCE),
                        (EditTextPreference) findPreference(StakeoutSettings.KEY_NEAR_DISTANCE),
                        (EditTextPreference) findPreference(StakeoutSettings.KEY_REACHED_DISTANCE));
                initializeDeclinationCorrection(
                        (EditTextPreference) findPreference(
                                StakeoutSettings.KEY_DECLINATION_CORRECTION));
                break;
            case SettingsConstantsUI.ACTION_PREFS_TRACKING:
                //addPreferencesFromResource(R.xml.preferences_tracks);
                setPreferencesFromResource(R.xml.preferences_tracks, null);


                final ListPreference lpTracksAccuracy =
                        (ListPreference) findPreference(SettingsConstants.KEY_PREF_TRACKS_SOURCE);
                initializeLocationAccuracy(lpTracksAccuracy, true);

                final ListPreference minTime =
                        (ListPreference) findPreference(SettingsConstants.KEY_PREF_TRACKS_MIN_TIME);


                final ListPreference minDistance = (ListPreference) findPreference(
                        SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE);
                initializeLocationMins(minTime, minDistance, true);

                final CheckBoxPreference uid = (CheckBoxPreference) findPreference(SettingsConstants.KEY_PREF_TRACK_SEND);

                initializeUid(uid);
                final CustomPreference uidTextPref = (CustomPreference) findPreference(SettingsConstants.KEY_PREF_TRACK_UID_CUSTOM);

                String uidText = getUid(getContext());
                //uidTextPref.setUID(getContext().getString(R.string.track_uid, uid));
                uidTextPref.setUID(uidText);

                break;
        }
    }


    protected static void restoreLayer(Activity activity, CharSequence name) {
        SharedPreferences preferences = android.preference.PreferenceManager.getDefaultSharedPreferences(activity);
        File defaultPath = activity.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
        if (defaultPath == null) {
            defaultPath = new File(activity.getFilesDir(), SettingsConstants.KEY_PREF_MAP);
        }
        String mapPath = preferences.getString(SettingsConstants.KEY_PREF_MAP_PATH, defaultPath.getPath());
        String mapName = preferences.getString(SettingsConstantsUI.KEY_PREF_MAP_NAME, "default");
        File mapFullPath = new File(mapPath, mapName + MAP_EXT);
        try {
            String json = FileUtil.readFromFile(mapFullPath);
            JSONObject map = new JSONObject(json);
            JSONArray layers = map.optJSONArray("layers");
            if (layers != null) {
                JSONObject layer = new JSONObject();
                layer.put("path", name);
                layers.put(layer);
                map.put("layers", layers);
                FileUtil.writeToFile(mapFullPath, map.toString());
                Intent intent = new Intent(activity, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(intent);
                Runtime.getRuntime().exit(0);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }


    protected static CharSequence[] fetchLayers(Activity activity)
    {
        ArrayList<String> items = new ArrayList<>();
        ArrayList<String> existing = new ArrayList<>();
        MainApplication app = (MainApplication) activity.getApplication();
        for (int i = app.getMap().getLayerCount() - 1; i >= 0; i--) {
            ILayer layer = app.getMap().getLayer(i);
            existing.add(layer.getPath().getName());
        }
        try {
            SQLiteDatabase db = ((MapContentProviderHelper) MapBase.getInstance()).getDatabase(false);
            Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);

            if (c.moveToFirst()) {
                do {
                    String table = c.getString(0);
                    if (table.startsWith("track") || table.startsWith("sqlite") || table.startsWith("android") || existing.contains(table)
                    || table.endsWith(CHANGES_NAME_POSTFIX) || table.endsWith(ATTACHMENTS_NAME_POSTFIX))
                        continue;
                    items.add(table);
                } while (c.moveToNext());
            }
            c.close();
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
        return items.toArray(new CharSequence[items.size()]);
    }


    @Override
    public FragmentManager getFragmentManagerFromParentFragment()
    {
        return getFragmentManager();
    }


    public static void initializeTheme(
            final Activity activity,
            final ListPreference theme)
    {
        if (null != theme) {
            theme.setSummary(theme.getEntry());

            theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(
                        Preference preference,
                        Object newValue)
                {
                    activity.startActivity(activity.getIntent());
                    activity.finish();
                    return true;
                }
            });
        }
    }


    public static void initializeReset(
            final Activity activity,
            final Preference preference)
    {
        if (null != preference) {
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(final Preference preference)
                {
                    AlertDialog.Builder confirm = com.nextgis.maplibui.util.FittedDialogs.compatBuilder(activity);
                    confirm.setTitle(R.string.reset_settings_title)
                            .setMessage(R.string.reset_settings_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which)
                                        {

                                            resetSettings(activity);
                                        }
                                    })
                            .show();
                    return false;
                }
            });
        }
    }

    public static void initializeWalkEmergencyReset(
            final Activity activity, final Preference preference) {
        if (activity == null || preference == null) return;
        boolean active = WalkEmergencyReset.hasState(activity);
        preference.setEnabled(active);
        preference.setSummary(active
                ? com.nextgis.maplibui.R.string.walk_emergency_reset_summary
                : com.nextgis.maplibui.R.string.walk_emergency_reset_empty);
        preference.setOnPreferenceClickListener(clicked -> {
            if (!WalkEmergencyReset.hasState(activity)) {
                clicked.setEnabled(false);
                clicked.setSummary(com.nextgis.maplibui.R.string.walk_emergency_reset_empty);
                return true;
            }
            com.nextgis.maplibui.util.FittedDialogs.compatBuilder(activity)
                    .setTitle(com.nextgis.maplibui.R.string.walk_emergency_reset)
                    .setMessage(com.nextgis.maplibui.R.string.walk_emergency_reset_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(com.nextgis.maplibui.R.string.walk_emergency_reset,
                            (dialog, which) -> {
                                if (WalkEmergencyReset.reset(activity)) {
                                    Toast.makeText(activity,
                                            com.nextgis.maplibui.R.string.walk_emergency_reset_done,
                                            Toast.LENGTH_LONG).show();
                                }
                                clicked.setEnabled(false);
                                clicked.setSummary(
                                        com.nextgis.maplibui.R.string.walk_emergency_reset_empty);
                            })
                    .show();
            return true;
        });
    }


    protected static void resetCollectorDistrict(MainApplication application)
    {
        if (application == null) {
            return;
        }
        MapBase map = application.getMap();
        if (map.clearCollectorDistrictRecursive()) {
            map.save();
        }
    }


    public static void initializeNotification(
            final Activity activity,
            final Preference preference)    {
        if (null != preference) {
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(final Preference preference)
                {
                    if (((CheckBoxPreference) preference).isChecked()){
                        // check notify perm
                        processNotifyPerm(activity, preference);
                    }

                    return false;
                }
            });
        }
    }


    public static void initializeLayerBackupQuota(
            final Activity activity,
            final ListPreference preference) {
        if (preference == null || activity == null) {
            return;
        }
        updateLayerBackupQuotaSummary(activity, preference);
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            // Persist first via ListPreference default handling; refresh summary after change.
            new Handler().post(() -> updateLayerBackupQuotaSummary(activity, preference));
            return true;
        });
    }


    private static void updateLayerBackupQuotaSummary(
            Activity activity,
            ListPreference preference) {
        if (activity == null || preference == null) {
            return;
        }
        long bytes = com.nextgis.maplibui.util.LayerBackupManager.getBackupDirectorySize(activity);
        String sizeText = android.text.format.Formatter.formatFileSize(activity, bytes);
        CharSequence entry = preference.getEntry();
        String limit = entry != null ? entry.toString() : preference.getValue();
        preference.setSummary(activity.getString(
                R.string.layer_backup_max_gb_summary, sizeText)
                + " (" + limit + ")");
    }


    public static void initializeRestore(
            final Activity activity,
            final Preference preference)
    {
        if (null != preference) {
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    final int[] selectedItem = {-1};
                    final CharSequence[] items = fetchLayers(activity);
                    AlertDialog.Builder confirm = com.nextgis.maplibui.util.FittedDialogs.compatBuilder(activity);
                    confirm.setTitle(R.string.select_layer_to_restore)
                            .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    selectedItem[0] = which;
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which)
                                        {
                                            if (selectedItem[0] > -1)
                                                restoreLayer(activity, items[selectedItem[0]]);
                                        }
                                    })
                            .show();
                    return false;
                }
            });
        }
    }


    public static void initializeShowStatusPanel(final ListPreference listPreference)
    {
        listPreference.setSummary(listPreference.getEntry());

        listPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                int id = listPreference.findIndexOfValue((String) newValue);
                CharSequence[] entries = listPreference.getEntries();
                if (id >= 0 && id < entries.length) {
                    preference.setSummary(entries[id]);
                }

                return true;
            }
        });
    }


    public static void initializeUnits(final ListPreference lpUnits) {
        lpUnits.setSummary(lpUnits.getEntry());
        lpUnits.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean metric = newValue.equals("metric");
                        preference.setSummary(lpUnits.getEntries()[metric ? 0 : 1]);
                        return true;
                    }
                });
    }


    public static void initializeCoordinates(
            ListPreference lpCoordinateFormat,
            IntEditTextPreference etCoordinateFraction)
    {
        if (null != lpCoordinateFormat) {
            lpCoordinateFormat.setSummary(lpCoordinateFormat.getEntry());

            lpCoordinateFormat.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener()
                    {
                        @Override
                        public boolean onPreferenceChange(
                                Preference preference,
                                Object newValue)
                        {
                            int value = Integer.parseInt(newValue.toString());
                            CharSequence[] entries = ((ListPreference) preference).getEntries();
                            if (value >= 0 && value < entries.length) {
                                preference.setSummary(entries[value]);
                            }
                            return true;
                        }
                    });
        }

        if (etCoordinateFraction != null) {
            etCoordinateFraction.setSummary(etCoordinateFraction.getPersistedString(
                    "" + AppConstants.DEFAULT_COORDINATES_FRACTION_DIGITS));

            etCoordinateFraction.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener()
                    {
                        @Override
                        public boolean onPreferenceChange(
                                Preference preference,
                                Object newValue)
                        {
                            preference.setSummary(newValue.toString());
                            return true;
                        }
                    });
        }
    }


    public static void initializeMapPath(
            final Context context,
            final SelectMapPathPreference mapPath)
    {
        if (null != mapPath) {
            mapPath.setSummary(mapPath.getText());

            mapPath.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(
                        Preference preference,
                        Object o)
                {

                    final Activity parent = (Activity) context;
                    if (null == parent) {
                        return false;
                    }
                    File newPath = new File((String) o);
                    startMapMove(parent, newPath, false, mapPath);
                    // The chooser must not persist a proposed path before the worker's result.
                    return false;
                }
            });
        }
    }


    public static void initializeShowCurrentLocation(
            final ListPreference listPreference)
    {
        listPreference.setSummary(listPreference.getEntry());

        listPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                int id = listPreference.findIndexOfValue((String) newValue);
                CharSequence[] entries = listPreference.getEntries();
                if (id >= 0 && id < entries.length) {
                    preference.setSummary(entries[id]);
                }

                return true;
            }
        });
    }


    public static void initializeMapBG(final ListPreference mapBG, final Context context)
    {
        mapBG.setSummary(mapBG.getEntry());

        mapBG.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                int id = mapBG.findIndexOfValue((String) newValue);
                CharSequence[] entries = mapBG.getEntries();
                if (id >= 0 && id < entries.length) {
                    preference.setSummary(entries[id]);
                }
                GISApplication.needUpdateBackground = true;
                return true;
            }
        });
    }


    public static CharSequence[] getAccuracyEntries(Context context) {
        CharSequence[] entries = new CharSequence[3];
        entries[0] = context.getString(R.string.pref_location_accuracy_gps);
        entries[1] = context.getString(R.string.pref_location_accuracy_cell);
        entries[2] = entries[0] + " & " + entries[1];
        return entries;
    }

    public static void initializeLocationAccuracy(
            final ListPreference listPreference,
            final boolean isTracks)
    {
        if (listPreference == null) return;
        String label = listPreference.getContext().getString(isTracks
                ? R.string.location_sources_recording : R.string.location_sources_automatic);
        String value = isTracks ? "1" : "3";
        listPreference.setEntries(new CharSequence[]{label});
        listPreference.setEntryValues(new CharSequence[]{value});
        listPreference.setValue(value);
        listPreference.setSummary(label);
        listPreference.setEnabled(false);
    }

    private void initializeGnssInput(final ListPreference input, final Preference device) {
        if (input != null) {
            input.setOnPreferenceChangeListener((preference, newValue) -> {
                String value = String.valueOf(newValue);
                preference.setSummary(GnssInputPrefs.VALUE_EXTERNAL.equals(value)
                        ? getString(R.string.gnss_input_external)
                        : getString(R.string.gnss_input_system));
                if (device != null) {
                    device.setEnabled(GnssInputPrefs.VALUE_EXTERNAL.equals(value));
                }
                if (getContext() != null) {
                    sectionWork(getContext(), false);
                }
                return true;
            });
            String current = input.getValue();
            if (current == null) {
                current = GnssInputPrefs.VALUE_SYSTEM;
            }
            input.setSummary(GnssInputPrefs.VALUE_EXTERNAL.equals(current)
                    ? getString(R.string.gnss_input_external)
                    : getString(R.string.gnss_input_system));
            if (device != null) {
                device.setEnabled(GnssInputPrefs.VALUE_EXTERNAL.equals(current));
            }
        }
        if (device != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(device.getContext());
            String name = GnssInputPrefs.deviceName(prefs);
            device.setSummary(name == null || name.isEmpty()
                    ? getString(R.string.gnss_external_summary) : name);
            device.setOnPreferenceClickListener(preference -> {
                startActivity(new android.content.Intent(getActivity(), ExternalGnssActivity.class));
                return true;
            });
        }
    }

    public static void initializeUid(CheckBoxPreference preference) {
        com.nextgis.maplib.util.TrackSendSettings.migrateDefault(preference.getSharedPreferences());
        preference.setEnabled(true);
        preference.setChecked(com.nextgis.maplib.util.TrackSendSettings.isEnabled(preference.getSharedPreferences()));
        preference.setSummary(R.string.track_send_pending_registration);
        preference.setOnPreferenceChangeListener((changed, value) -> {
            // Persist before scheduling: WorkManager must see the new upload intent.
            preference.getSharedPreferences().edit().putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, (Boolean) value).commit();
            if ((Boolean) value) com.nextgis.maplibui.mapui.TrackWorker.scheduleAll(preference.getContext());
            else com.nextgis.maplibui.mapui.TrackWorker.cancel(preference.getContext());
            return true;
        });
    }

    public static void initializeLocationMins(
            ListPreference minTime,
            final ListPreference minDistance,
            final boolean isTracks)
    {
        final Context context = minDistance.getContext();
        minTime.setSummary(getMinSummary(context, minTime.getEntry(), minTime.getValue()));
        minDistance.setSummary(
                getMinSummary(context, minDistance.getEntry(), minDistance.getValue()));

        minTime.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                int id = ((ListPreference) preference).findIndexOfValue((String) newValue);
                CharSequence[] entries = ((ListPreference) preference).getEntries();
                if (id >= 0 && id < entries.length) {
                    preference.setSummary(
                            getMinSummary(context, entries[id], (String) newValue));
                }

                String preferenceKey = isTracks
                                       ? SettingsConstants.KEY_PREF_TRACKS_MIN_TIME
                                       : SettingsConstants.KEY_PREF_LOCATION_MIN_TIME;
                preference.getSharedPreferences()
                        .edit()
                        .putString(preferenceKey, (String) newValue)
                        .apply();

                sectionWork(preference.getContext(), isTracks);

                return true;
            }
        });

        minDistance.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                int id = ((ListPreference) preference).findIndexOfValue((String) newValue);
                CharSequence[] entries = ((ListPreference) preference).getEntries();
                if (id >= 0 && id < entries.length) {
                    preference.setSummary(
                            getMinSummary(context, entries[id], (String) newValue));
                }

                String preferenceKey = isTracks
                                       ? SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE
                                       : SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE;
                preference.getSharedPreferences()
                        .edit()
                        .putString(preferenceKey, (String) newValue)
                        .apply();

                sectionWork(preference.getContext(), isTracks);

                return true;
            }
        });
    }


    public static void initializeAccurateTaking(EditTextPreference accurateMaxCount)
    {
        accurateMaxCount.setSummary(accurateMaxCount.getText());

        accurateMaxCount.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(
                    Preference preference,
                    Object newValue)
            {
                preference.setSummary((CharSequence) newValue);
                return true;
            }
        });
    }


    private static void initializeStakeoutThresholds(
            final EditTextPreference far,
            final EditTextPreference medium,
            final EditTextPreference near,
            final EditTextPreference reached)
    {
        final EditTextPreference[] preferences = {far, medium, near, reached};
        for (EditTextPreference preference : preferences) {
            if (preference == null) {
                continue;
            }
            preference.setSummary(preference.getContext().getString(
                    R.string.stakeout_threshold_summary, preference.getText()));
            preference.setOnPreferenceChangeListener((changedPreference, newValue) -> {
                try {
                    double farValue = StakeoutSettings.parseDistance(
                            changedPreference == far ? newValue.toString() : far.getText());
                    double mediumValue = StakeoutSettings.parseDistance(
                            changedPreference == medium ? newValue.toString() : medium.getText());
                    double nearValue = StakeoutSettings.parseDistance(
                            changedPreference == near ? newValue.toString() : near.getText());
                    double reachedValue = StakeoutSettings.parseDistance(
                            changedPreference == reached ? newValue.toString() : reached.getText());
                    if (StakeoutSettings.isValid(
                            farValue, mediumValue, nearValue, reachedValue)) {
                        changedPreference.setSummary(changedPreference.getContext().getString(
                                R.string.stakeout_threshold_summary, newValue.toString()));
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // The same human-readable validation message covers empty/non-numeric input.
                }
                Toast.makeText(
                        changedPreference.getContext(),
                        R.string.stakeout_threshold_invalid,
                        Toast.LENGTH_LONG).show();
                return false;
            });
        }
    }


    private static void initializeDeclinationCorrection(EditTextPreference preference)
    {
        if (preference == null) {
            return;
        }
        preference.setSummary(preference.getContext().getString(
                R.string.azimuth_correction_preference_summary, preference.getText()));
        preference.setOnPreferenceChangeListener((changedPreference, newValue) -> {
            try {
                float parsed = StakeoutSettings.parseCorrection(newValue.toString());
                if (StakeoutSettings.isValidCorrection(parsed)) {
                    float stored = StakeoutSettings.clampCorrection(parsed);
                    changedPreference.setSummary(changedPreference.getContext().getString(
                            R.string.azimuth_correction_preference_summary,
                            Float.toString(stored)));
                    if (stored != parsed) {
                        ((EditTextPreference) changedPreference).setText(Float.toString(stored));
                        return false;
                    }
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // The same human-readable validation message covers empty/non-numeric input.
            }
            Toast.makeText(
                    changedPreference.getContext(),
                    R.string.azimuth_correction_invalid,
                    Toast.LENGTH_LONG).show();
            return false;
        });
    }


    private static String getMinSummary(
            Context context,
            CharSequence newEntry,
            String newValue)
    {
        int value = 0;

        try {
            value = Integer.parseInt(newValue);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        String addition = newEntry + "";
        addition += value == 0 ? context.getString(R.string.frequentest) : "";

        return addition;
    }


    protected static void sectionWork(
            Context context,
            boolean isTracks)
    {
        if (!isTracks) {
            MainApplication application = (MainApplication) context.getApplicationContext();
            application.getGpsEventSource().updateActiveListeners();
        } else {
            if (isTrackerServiceRunning(context)) {
                Toast.makeText(
                        context, context.getString(R.string.tracks_reload), Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }


    protected static void resetSettings(Activity activity)
    {
        startMapMove(activity, null, true, null);
    }

    private static void applyResetPreferences(Activity activity, File actualPath)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(SettingsConstantsUI.KEY_PREF_THEME);
        editor.remove(SettingsConstantsUI.KEY_PREF_COMPASS_TRUE_NORTH);
        editor.remove(SettingsConstantsUI.KEY_PREF_COMPASS_MAGNETIC);
        editor.remove(SettingsConstantsUI.KEY_PREF_COMPASS_KEEP_SCREEN);
        editor.remove(SettingsConstantsUI.KEY_PREF_COMPASS_VIBRATE);
        editor.remove(SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL);
        editor.remove(SettingsConstantsUI.KEY_PREF_SHOW_CURRENT_LOC);
        editor.remove(KEY_PREF_SHOW_COMPASS);
        editor.remove(SettingsConstantsUI.KEY_PREF_KEEPSCREENON);
        editor.remove(SettingsConstantsUI.KEY_PREF_COORD_FORMAT);
        editor.remove(KEY_PREF_SHOW_ZOOM_CONTROLS);
        editor.remove(SettingsConstants.KEY_PREF_LOCATION_SOURCE);
        editor.remove(com.nextgis.mobile.location.BackgroundLocationPolicy.PREFERENCE);
        editor.remove(SettingsConstants.KEY_PREF_GNSS_INPUT);
        editor.remove(SettingsConstants.KEY_PREF_GNSS_TRANSPORT);
        editor.remove(SettingsConstants.KEY_PREF_GNSS_DEVICE_ID);
        editor.remove(SettingsConstants.KEY_PREF_GNSS_DEVICE_NAME);
        editor.remove(SettingsConstants.KEY_PREF_GNSS_TCP_HOST);
        editor.remove(SettingsConstants.KEY_PREF_GNSS_TCP_PORT);
        editor.remove(SettingsConstants.KEY_PREF_LOCATION_MIN_TIME);
        editor.remove(SettingsConstants.KEY_PREF_LOCATION_MIN_DISTANCE);
        editor.remove(SettingsConstants.KEY_PREF_LOCATION_ACCURATE_COUNT);
        editor.remove(StakeoutSettings.KEY_SOUND_ENABLED);
        editor.remove(StakeoutSettings.KEY_FAR_DISTANCE);
        editor.remove(StakeoutSettings.KEY_MEDIUM_DISTANCE);
        editor.remove(StakeoutSettings.KEY_NEAR_DISTANCE);
        editor.remove(StakeoutSettings.KEY_REACHED_DISTANCE);
        editor.remove(StakeoutSettings.KEY_DECLINATION_CORRECTION);
        editor.remove(SettingsConstants.KEY_PREF_TRACKS_SOURCE);
        editor.remove(SettingsConstants.KEY_PREF_TRACKS_MIN_TIME);
        editor.remove(SettingsConstants.KEY_PREF_TRACKS_MIN_DISTANCE);
        editor.remove(SettingsConstants.KEY_PREF_TRACK_RESTORE);
        editor.remove(SettingsConstants.KEY_PREF_TRACK_SEND);
        editor.remove(KEY_PREF_SHOW_MEASURING);
        editor.remove(KEY_PREF_SHOW_SCALE_RULER);
        editor.remove(SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG);
        editor.remove(KEY_PREF_SD_CARD_NAME);
        editor.remove(SettingsConstants.KEY_PREF_VERBOSE_LOG);

        editor.putString(SettingsConstants.KEY_PREF_MAP_PATH, actualPath.getAbsolutePath());
        editor.apply();

        PreferenceManager.setDefaultValues(activity, R.xml.preferences_general, true);
        PreferenceManager.setDefaultValues(activity, R.xml.preferences_map, true);
        PreferenceManager.setDefaultValues(activity, R.xml.preferences_location, true);
        PreferenceManager.setDefaultValues(activity, R.xml.preferences_tracks, true);

    }


    protected static void deleteLayers(Activity activity)
    {
        MainApplication app = (MainApplication) activity.getApplication();
        for (int i = app.getMap().getLayerCount() - 1; i >= 0; i--) {
            ILayer layer = app.getMap().getLayer(i);
            try {

                if (!layer.getPath().getName().equals(MainApplication.LAYER_OSM) && !layer.getPath()
                        .getName()
                        .equals(MainApplication.LAYER_TRACKS)) {
                    layer.delete(false);
                }
            } catch (Exception ex) {
                //Log.e("d", "'");
            }
        }

        try {
            ((MapContentProviderHelper) MapBase.getInstance()).getDatabase(false).execSQL("VACUUM");
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
    }


    public static void moveMap(
            Activity activity,
            File path) {
        startMapMove(activity, path, false, null);
    }

    private static boolean startMapMove(Activity activity, File path, boolean reset,
                                        SelectMapPathPreference preference) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        ProjectOperationCoordinator.Lease lease = null;
        BackgroundMoveTask task = null;
        try {
            MainApplication application = (MainApplication) activity.getApplication();
            lease = ProjectOperationCoordinator.tryBegin(activity,
                    ProjectOperationCoordinator.Kind.PROJECT_SWITCH);
            if (lease == null) {
                Toast.makeText(activity, R.string.project_operation_wait, Toast.LENGTH_LONG).show();
                return false;
            }
            MapBase map = application.getMap();
            if (map == null) throw new IllegalStateException("Map move owner is absent");
            File destination = path;
            if (reset) {
                destination = activity.getExternalFilesDir(KEY_PREF_MAP);
                if (destination == null) destination = new File(activity.getFilesDir(), KEY_PREF_MAP);
            }
            if (destination == null) throw new IllegalStateException("Map move path is absent");
            task = new BackgroundMoveTask(activity, application, map, destination, lease, reset, preference);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return true;
        } catch (RuntimeException failure) {
            if (task != null) task.failToStart(failure);
            else {
                try { reportMoveFailure(activity, failure); }
                finally { if (lease != null) lease.close(); }
            }
            return false;
        } catch (Error fatal) {
            if (task != null) task.failToStart(fatal);
            else if (lease != null) {
                try { lease.close(); }
                catch (Throwable cleanup) { fatal.addSuppressed(cleanup); }
            }
            throw fatal;
        }
    }

    private static void reportMoveFailure(Activity activity, Throwable failure) {
        HyperLog.w(Constants.TAG, "Map storage move could not complete", failure);
        if (!activity.isFinishing() && !activity.isDestroyed())
            Toast.makeText(activity, R.string.map_storage_move_failed, Toast.LENGTH_LONG).show();
    }

    protected static final class MoveOutcome {
        final File actualPath;
        final RuntimeException failure;
        MoveOutcome(File actualPath, RuntimeException failure) {
            this.actualPath = actualPath;
            this.failure = failure;
        }
    }

    protected static class BackgroundMoveTask
            extends AsyncTask<Void, Void, MoveOutcome>
    {
        private ProgressDialog mProgressDialog;
        private final Activity mActivity;
        private final MainApplication mApplication;
        private final MapBase mMap;
        private final File mPath;
        private final ProjectOperationCoordinator.Lease mLease;
        private final boolean mReset;
        private final SelectMapPathPreference mPreference;
        private final java.util.concurrent.atomic.AtomicBoolean mTerminalClaimed =
                new java.util.concurrent.atomic.AtomicBoolean();
        private boolean mOrientationLocked;
        private volatile boolean mRetired;
        private volatile File mActualPath;


        BackgroundMoveTask(
                Activity activity,
                MainApplication application,
                MapBase map,
                File path,
                ProjectOperationCoordinator.Lease lease,
                boolean reset,
                SelectMapPathPreference preference){
            mActivity = activity;
            mApplication = application;
            mMap = map;
            mPath = path;
            mLease = lease;
            mReset = reset;
            mPreference = preference;
        }


        @Override
        protected MoveOutcome doInBackground(Void... voids)
        {
            Throwable escaped = null;
            try {
                if (isCancelled()) return null;
                if (!mApplication.retireMapForStorageMove(mMap))
                    return new MoveOutcome(null, new IllegalStateException("Map move owner was replaced"));
                mRetired = true;
                mMap.moveTo(mPath);
                File actualPath = mMap.getPath();
                if (actualPath == null) throw new IllegalStateException("Map move result has no path");
                mActualPath = actualPath;
                boolean committed = PreferenceManager.getDefaultSharedPreferences(mActivity).edit()
                        .putString(SettingsConstants.KEY_PREF_MAP_PATH, actualPath.getAbsolutePath()).commit();
                RuntimeException failure = !committed
                        ? new IllegalStateException("Cannot persist map move result")
                        : !actualPath.getAbsoluteFile().equals(mPath.getAbsoluteFile())
                        ? new IllegalStateException("Map did not reach the requested destination") : null;
                return new MoveOutcome(actualPath, failure);
            } catch (RuntimeException | Error failure) {
                // Unexpected before-cache failures have no transactional recovery contract.
                escaped = failure;
                throw failure;
            } finally {
                if (escaped != null) {
                    // AsyncTask will still post cancellation after the worker unwinds.
                    mTerminalClaimed.set(true);
                    try { mLease.close(); }
                    catch (Throwable cleanup) { escaped.addSuppressed(cleanup); }
                }
            }
        }


        @Override
        protected void onPreExecute()
        {
            ControlHelper.lockScreenOrientation(mActivity);
            mOrientationLocked = true;
            mProgressDialog = new ProgressDialog(mActivity);
            mProgressDialog.setTitle(R.string.moving);
            mProgressDialog.setMessage(mActivity.getString(R.string.warning_map_moving));
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setIcon(com.nextgis.maplibui.R.drawable.ic_action_warning_light);
            mProgressDialog.show();
        }


        @Override
        protected void onPostExecute(MoveOutcome outcome)
        {
            finish(outcome, false);
        }

        @Override protected void onCancelled(MoveOutcome outcome) { finish(outcome, true); }
        @Override protected void onCancelled() { finish(null, true); }

        private void finish(MoveOutcome outcome, boolean cancelled) {
            if (!mTerminalClaimed.compareAndSet(false, true)) return;
            boolean rebindAttempted = false;
            try {
                if (!mRetired) {
                    if (!cancelled) reportMoveFailure(mActivity, outcome == null ? null : outcome.failure);
                    return;
                }
                boolean successful = !cancelled && outcome != null && outcome.failure == null;
                if (!canRebindActualMap()) {
                    reportMoveFailure(mActivity, new IllegalStateException("Map move owner/path no longer matches"));
                    return;
                }
                if (successful && mReset) {
                    applyResetPreferences(mActivity, outcome.actualPath);
                }
                rebindAttempted = true;
                rebindActualMap();
                if (successful && mReset) {
                    resetCollectorDistrict(mApplication);
                    mApplication.initBaseLayers();
                    deleteLayers(mActivity);
                    resetCollectorDistrict(mApplication);
                    mApplication.initBaseLayers();
                }
                if (!successful && !cancelled)
                    reportMoveFailure(mActivity, outcome == null ? null : outcome.failure);
            } catch (RuntimeException failure) {
                reportMoveFailure(mActivity, failure);
                // A reset-default callback can fail before the retired renderer was replaced.
                if (!rebindAttempted && canRebindActualMap()) {
                    rebindAttempted = true;
                    rebindActualMap();
                }
            } finally {
                releaseUiAndLease();
            }
        }

        private boolean canRebindActualMap() {
            return mApplication.canRebindMapAfterStorageMove(mMap, mActualPath);
        }

        private void rebindActualMap() {
            if (!mApplication.rebindMapAfterStorageMove(mMap, mActualPath))
                throw new IllegalStateException("Map move owner/path changed before refresh");
            // Request777 means reload the Activity's drawable, not durable move success.
            mActivity.setResult(RESULT_OK);
            if (mPreference != null) mPreference.applyMoveResult(mActualPath.getAbsolutePath());
        }

        private void failToStart(Throwable failure) {
            if (!mTerminalClaimed.compareAndSet(false, true)) return;
            if (failure instanceof Error) {
                try { releaseUiAndLease(); }
                catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            } else {
                try { reportMoveFailure(mActivity, failure); }
                finally { releaseUiAndLease(); }
            }
        }

        private void releaseUiAndLease() {
            try {
                if (mProgressDialog != null) mProgressDialog.dismiss();
            } finally {
                try {
                    if (mOrientationLocked) ControlHelper.unlockScreenOrientation(mActivity);
                } finally {
                    mLease.close();
                }
            }
        }
    }

    public static boolean processNotifyPerm(Activity activity, final Preference preference){
        if (ContextCompat.checkSelfPermission(
                activity,"android.permission.POST_NOTIFICATIONS") ==
                PackageManager.PERMISSION_GRANTED) {
            return true;
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                activity, "android.permission.POST_NOTIFICATIONS")) {

            AlertDialog.Builder confirm = com.nextgis.maplibui.util.FittedDialogs.compatBuilder(activity);
            confirm.setTitle(R.string.push_perm_title)
                    .setMessage(R.string.push_perm_why_text)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        android.preference.PreferenceManager.getDefaultSharedPreferences(activity).
                                edit()
                                .putBoolean(AppSettingsConstants.KEY_PREF_SHOW_SYNC, false)
                                .commit();
                        ((CheckBoxPreference)preference).setChecked(false);

                    })
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> {
                                if (which == -1){
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        activity.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATION_PERMISSION);
                                    }  else {

                                    }
                                }
                            })
                    .show();


        } else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                activity.requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATION_PERMISSION);
        }
        return true;
    }

    public void processPermission(int requestCode,
                                  int resultCode,
                                  Intent data){
        if (mAction.equals(SettingsConstantsUI.ACTION_PREFS_GENERAL)){
            if (resultCode ==  RESULT_OK){
                // nothing
            } else {
                final Preference notify =
                        findPreference(SettingsConstantsUI.KEY_PREF_SHOW_SYNC);
                ((CheckBoxPreference)notify).setChecked(false);
                android.preference.PreferenceManager.getDefaultSharedPreferences(this.getContext()).
                        edit()
                        .putBoolean(AppSettingsConstants.KEY_PREF_SHOW_SYNC, false)
                        .commit();


                // alert perm off

                AlertDialog.Builder confirm = com.nextgis.maplibui.util.FittedDialogs.compatBuilder(this.getActivity());
                confirm.setTitle(R.string.push_perm_title)
                        .setMessage(R.string.push_perm_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();

            }
        }
    }


    @Override
    public void onDetach() {
        super.onDetach();
    }

}
