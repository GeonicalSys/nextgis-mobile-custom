package com.nextgis.mobile.location;

import android.app.Application;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import com.nextgis.maplib.util.ExternalGnssFixPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {26, 36})
public class LocationDisplayAccuracyTest {
    private static Location fix(String provider, float accuracy) {
        Location location = new Location(provider);
        location.setLatitude(52.23);
        location.setLongitude(21.01);
        location.setAccuracy(accuracy);
        location.setSpeed(1.2f);
        location.setTime(123_000);
        location.setElapsedRealtimeNanos(100_000_000_000L);
        return location;
    }

    @Test public void phoneFloorDoesNotChangeTheFixUsedForProcessing() {
        Location location = fix(LocationManager.GPS_PROVIDER, .5f);
        assertEquals(3f, LocationDisplayAccuracy.meters(location, false), 0f);
        assertEquals(.5f, location.getAccuracy(), 0f);
        assertEquals(52.23, location.getLatitude(), 0d);
        assertEquals(21.01, location.getLongitude(), 0d);
        assertEquals(1.2f, location.getSpeed(), 0f);
        assertEquals(123_000, location.getTime());
        assertEquals(100_000_000_000L, location.getElapsedRealtimeNanos());
    }

    @Test public void phoneValuesAtOrAboveTheFloorArePreserved() {
        for (float accuracy : new float[]{3f, 8f, 50f})
            assertEquals(accuracy, LocationDisplayAccuracy.meters(
                    fix(LocationManager.GPS_PROVIDER, accuracy), false), 0f);
    }

    @Test public void networkAndExplicitExternalInputKeepSubmeterAccuracy() {
        assertEquals(.5f, LocationDisplayAccuracy.meters(
                fix(LocationManager.NETWORK_PROVIDER, .5f), false), 0f);
        assertEquals(.02f, LocationDisplayAccuracy.meters(
                fix(LocationManager.GPS_PROVIDER, .02f), true), 0f);
    }

    @Test public void nativeNmeaReceiverKeepsItsAccuracyEvenWithoutExternalPreference() {
        Location location = fix(LocationManager.GPS_PROVIDER, .02f);
        Bundle extras = new Bundle();
        extras.putBoolean(ExternalGnssFixPolicy.EXTRA_NATIVE_NMEA, true);
        location.setExtras(extras);
        assertEquals(.02f, LocationDisplayAccuracy.meters(location, false), 0f);
    }

    @Test @Config(sdk = 36) public void receiverPublishedAsMockGpsKeepsItsAccuracy() {
        Location location = fix(LocationManager.GPS_PROVIDER, .02f);
        location.setMock(true);
        assertEquals(.02f, LocationDisplayAccuracy.meters(location, false), 0f);
    }

    @Test public void missingOrInvalidAccuracyIsNotInvented() {
        assertEquals(0f, LocationDisplayAccuracy.meters(new Location(LocationManager.GPS_PROVIDER), false), 0f);
        assertTrue(Float.isNaN(LocationDisplayAccuracy.meters(
                fix(LocationManager.GPS_PROVIDER, Float.NaN), false)));
    }
}
