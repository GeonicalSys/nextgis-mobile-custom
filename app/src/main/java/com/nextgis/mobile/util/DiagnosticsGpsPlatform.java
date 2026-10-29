package com.nextgis.mobile.util;

import android.Manifest;
import android.app.ActivityManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import java.util.LinkedHashMap;
import java.util.Map;

/** Rare classified GPS reports only; permission/provider policy, never coordinates or app accounts. */
final class DiagnosticsGpsPlatform {
    static Map<String,Object> collect(Context context) {
        Map<String,Object> state = new LinkedHashMap<>(); state.put("schema_version",1);
        if (context == null) { state.put("status","unavailable"); return state; }
        state.put("status","available");
        state.put("fine_permission", context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);
        state.put("coarse_permission", context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED);
        state.put("background_permission", Build.VERSION.SDK_INT < 29 ? "not_required" :
                context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED ? "granted" : "denied");
        try {
            LocationManager manager = context.getSystemService(LocationManager.class);
            if (manager != null) {
                state.put("gps_provider_enabled",manager.isProviderEnabled(LocationManager.GPS_PROVIDER));
                state.put("location_enabled",Build.VERSION.SDK_INT >= 28 ? manager.isLocationEnabled() : !manager.getProviders(true).isEmpty());
            }
        } catch (RuntimeException ignored) { state.put("provider_status","unavailable"); }
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(info); state.put("process_importance",info.importance);
            if (manager != null && Build.VERSION.SDK_INT >= 28) state.put("background_restricted",manager.isBackgroundRestricted());
            if (Build.VERSION.SDK_INT >= 28) {
                UsageStatsManager usage = context.getSystemService(UsageStatsManager.class);
                if (usage != null) state.put("app_standby_bucket",usage.getAppStandbyBucket());
            }
        } catch (RuntimeException ignored) { state.put("background_policy_status","unavailable"); }
        state.put("observer_on_main",android.os.Looper.myLooper()==android.os.Looper.getMainLooper());
        return state;
    }
}
