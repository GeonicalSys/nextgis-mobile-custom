/*
 * Project: NextGIS Mobile
 * Purpose: Mobile GIS for Android.
 * Copyright (c) 2026 GeonicalSystem
 */
package com.nextgis.mobile.stakeout

/** Converts a true initial bearing to clockwise magnetic azimuth. */
object MagneticAzimuthCalculator {
    private const val UNDEFINED_DISTANCE_METERS = 0.01

    @JvmStatic
    fun fromTrueBearing(
        trueBearingDegrees: Double,
        declinationDegrees: Float,
        distanceMeters: Double
    ): Float? {
        if (distanceMeters <= UNDEFINED_DISTANCE_METERS) return null
        return normalize((trueBearingDegrees - declinationDegrees).toFloat())
    }

    @JvmStatic
    fun normalize(value: Float): Float {
        val normalized = value % 360f
        if (normalized == 0f) return 0f
        return if (normalized < 0f) normalized + 360f else normalized
    }
}
