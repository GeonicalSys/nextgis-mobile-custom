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
}
