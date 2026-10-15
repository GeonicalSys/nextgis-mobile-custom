package com.nextgis.mobile.location;

import android.location.Location;
import android.location.LocationManager;
import com.nextgis.maplib.util.LocationTrackFilter;

/** Accuracy for the map's status panel and circle only; never changes a provider fix. */
public final class LocationDisplayAccuracy {
    private static final float PHONE_GPS_MIN_METERS = 3f;

    private LocationDisplayAccuracy() { }

    public static float meters(Location location, boolean externalInput) {
        float accuracy = location.getAccuracy();
        if (!externalInput && LocationManager.GPS_PROVIDER.equals(location.getProvider())
                && !LocationTrackFilter.isReceiverStream(location)
                && location.hasAccuracy() && Float.isFinite(accuracy) && accuracy > 0f) {
            return Math.max(PHONE_GPS_MIN_METERS, accuracy);
        }
        return accuracy;
    }
}
