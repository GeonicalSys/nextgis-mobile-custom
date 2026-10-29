package com.nextgis.mobile.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,manifest=Config.NONE,application=android.app.Application.class)
public class DiagnosticsGpsPlatformTest {
    @Test public void currentPermissionAndProviderStatesAreSeparateFromTheRecoveryCause() {
        android.app.Application app=org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION);
        Map<String,Object> result=DiagnosticsGpsPlatform.collect(app);
        assertEquals(true,result.get("fine_permission")); assertEquals("denied",result.get("background_permission"));
        assertTrue(result.containsKey("gps_provider_enabled")); assertTrue(result.containsKey("app_standby_bucket"));
        assertFalse(result.containsKey("latitude")); assertFalse(result.containsKey("longitude"));
        assertFalse(result.containsKey("accounts")); assertFalse(result.containsKey("reason"));
    }
    @Test @Config(sdk=26) public void olderAndroidDoesNotInventARevokedBackgroundPermission() {
        assertEquals("not_required",DiagnosticsGpsPlatform.collect(org.robolectric.RuntimeEnvironment.getApplication()).get("background_permission"));
    }
    @Test public void missingContextIsExplicitlyUnavailable() { assertEquals("unavailable",DiagnosticsGpsPlatform.collect(null).get("status")); }

    @Test public void cachedFixExportsOnlyMeasurementTimeAndCannotInjectItsPayload() {
        assertNumericCachedFix();
    }

    @Test @Config(sdk=30) public void apiThirtyCachedFixUsesTheSameNumericContract() {
        assertNumericCachedFix();
    }

    private void assertNumericCachedFix() {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10));
        long now = android.os.SystemClock.elapsedRealtime();
        android.location.Location cached = new android.location.Location(android.location.LocationManager.GPS_PROVIDER);
        cached.setElapsedRealtimeNanos((now - 500) * 1_000_000L);
        cached.setLatitude(61.234567); cached.setLongitude(42.345678);
        android.os.Bundle extras = new android.os.Bundle(); extras.putString("secret", "private-payload");
        cached.setExtras(extras);
        android.location.LocationManager manager = app.getSystemService(android.location.LocationManager.class);
        org.robolectric.Shadows.shadowOf(manager).setProviderEnabled(android.location.LocationManager.GPS_PROVIDER, true);
        org.robolectric.Shadows.shadowOf(manager).setLastKnownLocation(android.location.LocationManager.GPS_PROVIDER, cached);
        Map<String,Object> platform = DiagnosticsGpsPlatform.collect(app, now - 1_000);
        Map<?,?> witness = (Map<?,?>) platform.get("gps_fix_witness");
        assertEquals("available", witness.get("read_status"));
        assertEquals(now - 500, witness.get("cached_fix_elapsed_realtime_ms"));
        assertEquals("newer", witness.get("cache_vs_app_raw"));
        assertTrue(((Long) witness.get("cached_fix_age_ms")) >= 500);
        assertFalse(platform.toString().contains("61.234567"));
        assertFalse(platform.toString().contains("42.345678"));
        assertFalse(platform.toString().contains("private-payload"));
        assertEquals((now - 500) * 1_000_000L, cached.getElapsedRealtimeNanos());
    }

    @Test public void deniedPermissionProducesAFixedWitnessWithoutLosingPlatformContext() {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(app).denyPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION);
        Map<String,Object> platform = DiagnosticsGpsPlatform.collect(app, 1_000L);
        assertEquals("available", platform.get("status"));
        assertEquals(false, platform.get("fine_permission"));
        assertEquals("permission_denied", ((Map<?,?>) platform.get("gps_fix_witness")).get("read_status"));
    }
}
