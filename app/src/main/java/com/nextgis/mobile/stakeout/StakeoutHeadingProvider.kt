/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Copyright (c) 2026 GeonicalSystem
 */
package com.nextgis.mobile.stakeout

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager

internal class StakeoutHeadingProvider(
    context: Context,
    private val onHeadingChanged: () -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rotationMatrix = FloatArray(9)
    private val adjustedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var magneticHeading: Float? = null
    private var declinationDegrees = 0f
    private var declinationLocation: Location? = null
    private var declinationTimeMillis = 0L
    private var headingElapsedMillis = 0L
    private var filteredSin = 0.0
    private var filteredCos = 0.0
    private var hasFilteredHeading = false
    private var started = false

    fun start() {
        if (started || rotationSensor == null) return
        started = sensorManager.registerListener(
            this,
            rotationSensor,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    fun stop() {
        if (started) sensorManager.unregisterListener(this)
        started = false
        magneticHeading = null
        hasFilteredHeading = false
    }

    fun updateLocation(location: Location) {
        val modelTime = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val cachedLocation = declinationLocation
        if (cachedLocation != null
            && cachedLocation.distanceTo(location) < DECLINATION_CACHE_DISTANCE_METERS
            && kotlin.math.abs(modelTime - declinationTimeMillis) < DECLINATION_CACHE_AGE_MILLIS
        ) return

        declinationDegrees = WorldMagneticModel2025(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            if (location.hasAltitude()) location.altitude.toFloat() else 0f,
            modelTime
        ).declination
        declinationLocation = Location(location)
        declinationTimeMillis = modelTime
    }

    /** Device orientation relative to magnetic north; GNSS movement bearing is never used. */
    fun magneticHeading(): Float? {
        if (SystemClock.elapsedRealtime() - headingElapsedMillis > SENSOR_STALE_MILLIS) {
            return null
        }
        return magneticHeading
    }

    fun declinationDegrees(): Float = declinationDegrees

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val (axisX, axisY) = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjustedMatrix)
        SensorManager.getOrientation(adjustedMatrix, orientation)

        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            magneticHeading = null
            hasFilteredHeading = false
            return
        }

        val rawHeadingRadians = orientation[0].toDouble()
        val rawSin = kotlin.math.sin(rawHeadingRadians)
        val rawCos = kotlin.math.cos(rawHeadingRadians)
        if (!hasFilteredHeading) {
            filteredSin = rawSin
            filteredCos = rawCos
            hasFilteredHeading = true
        } else {
            filteredSin += FILTER_ALPHA * (rawSin - filteredSin)
            filteredCos += FILTER_ALPHA * (rawCos - filteredCos)
        }
        magneticHeading = normalize(
            Math.toDegrees(kotlin.math.atan2(filteredSin, filteredCos)).toFloat()
        )
        headingElapsedMillis = SystemClock.elapsedRealtime()
        onHeadingChanged()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR
            && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
        ) {
            magneticHeading = null
            hasFilteredHeading = false
        }
    }

    private fun normalize(value: Float): Float {
        val normalized = value % FULL_CIRCLE
        return if (normalized < 0f) normalized + FULL_CIRCLE else normalized
    }

    private companion object {
        const val FULL_CIRCLE = 360f
        const val SENSOR_STALE_MILLIS = 2_000L
        const val FILTER_ALPHA = 0.2
        const val DECLINATION_CACHE_DISTANCE_METERS = 1_000f
        const val DECLINATION_CACHE_AGE_MILLIS = 6L * 60L * 60L * 1_000L
    }
}
