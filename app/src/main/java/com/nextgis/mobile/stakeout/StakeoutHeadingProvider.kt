/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Copyright (c) 2026 GeonicalSystem
 */
package com.nextgis.mobile.stakeout

import android.content.Context
import android.hardware.GeomagneticField
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

    private var latestLocation: Location? = null
    private var trueHeading: Float? = null
    private var headingElapsedMillis = 0L
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
        trueHeading = null
    }

    fun updateLocation(location: Location) {
        latestLocation = Location(location)
    }

    /** Device orientation relative to true north; GNSS movement bearing is never used. */
    fun heading(): Float? {
        if (SystemClock.elapsedRealtime() - headingElapsedMillis > SENSOR_STALE_MILLIS) {
            return null
        }
        return trueHeading
    }

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

        val magneticHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val location = latestLocation
        val declination = if (location == null) {
            0f
        } else {
            GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                if (location.hasAltitude()) location.altitude.toFloat() else 0f,
                location.time
            ).declination
        }
        trueHeading = normalize(magneticHeading + declination)
        headingElapsedMillis = SystemClock.elapsedRealtime()
        onHeadingChanged()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR
            && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
        ) {
            trueHeading = null
        }
    }

    private fun normalize(value: Float): Float {
        val normalized = value % FULL_CIRCLE
        return if (normalized < 0f) normalized + FULL_CIRCLE else normalized
    }

    private companion object {
        const val FULL_CIRCLE = 360f
        const val SENSOR_STALE_MILLIS = 2_000L
    }
}
