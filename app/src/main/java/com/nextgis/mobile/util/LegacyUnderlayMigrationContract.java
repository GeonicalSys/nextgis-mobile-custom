package com.nextgis.mobile.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;

import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.LocalTMSLayer;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.activity.LegacyUnderlayExportActivity;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Cross-package protocol shared by the debug exporter and Geonical importer. */
public final class LegacyUnderlayMigrationContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String DEBUG_PACKAGE = "com.nextgis.mobile.debug";
    public static final String GEONICAL_PACKAGE = "com.nextgis.mobile.geonical";
    private static final String GEONICAL_CERT_SHA256 =
            "ecd04c936532000eea8cb06ca6a3c1a3ae482844cacce2b84926c635429c3e18";
    private static final String DEBUG_CERT_SHA256 =
            "6ce7749e935e80243623f3c5fa146a972d5dd34f1f146b52597dd7f0dff98c71";
    public static final String ACTION_EXPORT =
            "com.nextgis.mobile.action.EXPORT_LEGACY_UNDERLAYS";
    public static final String MIME_TYPE =
            "application/vnd.geonical.legacy-underlay";
    public static final int STREAM_MAGIC = 0x4e475531; // NGU1
    public static final int RECORD_TILE = 1;
    public static final int RECORD_MBTILES = 2;
    public static final int RECORD_COMPLETE = 127;

    private LegacyUnderlayMigrationContract() {
    }

    public static boolean isDebugSource(Context context) {
        return context != null && DEBUG_PACKAGE.equals(context.getPackageName());
    }

    public static boolean isGeonicalTarget(Context context) {
        return context != null && GEONICAL_PACKAGE.equals(context.getPackageName());
    }

    public static boolean isTrustedGeonicalCaller(Context context, String packageName) {
        return GEONICAL_PACKAGE.equals(packageName)
                && hasSigningCertificate(context, packageName, GEONICAL_CERT_SHA256);
    }

    public static boolean isTrustedDebugSourceInstalled(Context context) {
        return hasSigningCertificate(context, DEBUG_PACKAGE, DEBUG_CERT_SHA256);
    }

    public static Intent createExportIntent() {
        return new Intent(ACTION_EXPORT)
                .setComponent(new ComponentName(
                        DEBUG_PACKAGE, LegacyUnderlayExportActivity.class.getName()));
    }

    public static Uri buildLayerUri(Context context, LocalTMSLayer layer) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".legacy_underlays")
                .appendPath("layer")
                .appendPath(Integer.toString(layer.getId()))
                .appendQueryParameter("source_key", sourceKey(layer))
                .build();
    }

    public static String sourceKey(LocalTMSLayer layer) {
        return layer.getPath().getName() + ":" + layer.getId();
    }

    public static List<LocalTMSLayer> collectLocalUnderlays(Context context) {
        ArrayList<ILayer> found = new ArrayList<>();
        MapBase map = ((GISApplication) context.getApplicationContext()).getMap();
        LayerGroup.getLayersByType(map, Constants.LAYERTYPE_LOCAL_TMS, found);
        ArrayList<LocalTMSLayer> result = new ArrayList<>();
        for (ILayer layer : found) {
            if (layer instanceof LocalTMSLayer) {
                result.add((LocalTMSLayer) layer);
            }
        }
        return result;
    }

    /**
     * An old debug installation must keep opening its pre-registry map in place. Otherwise the
     * normal project bootstrap would recursively copy every extracted tile before the exporter can
     * stream it to Geonical.
     */
    public static boolean shouldDeferDebugProjectBootstrap(Context context) {
        if (!isDebugSource(context)
                || !CollectorProjectRegistry.listProjects(context).isEmpty()) {
            return false;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        File defaultRoot = context.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
        if (defaultRoot == null) {
            defaultRoot = new File(context.getFilesDir(), SettingsConstants.KEY_PREF_MAP);
        }
        String mapPath = preferences.getString(
                SettingsConstants.KEY_PREF_MAP_PATH, defaultRoot.getAbsolutePath());
        String mapName = preferences.getString(SettingsConstantsUI.KEY_PREF_MAP_NAME, "default");
        return new File(mapPath, mapName + Constants.MAP_EXT).isFile()
                || new File(defaultRoot, "default" + Constants.MAP_EXT).isFile();
    }

    private static boolean hasSigningCertificate(
            Context context, String packageName, String expectedSha256) {
        if (context == null || packageName == null) {
            return false;
        }
        try {
            PackageManager manager = context.getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = manager.getPackageInfo(
                        packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) {
                    return false;
                }
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo info = manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                //noinspection deprecation
                signatures = info.signatures;
            }
            if (signatures == null) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Signature signature : signatures) {
                if (expectedSha256.equals(toHex(digest.digest(signature.toByteArray())))) {
                    return true;
                }
                digest.reset();
            }
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            return false;
        }
        return false;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
