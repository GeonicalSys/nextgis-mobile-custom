package com.nextgis.mobile.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import com.nextgis.maplib.map.UserLocationGeometry
import com.nextgis.mobile.stakeout.WorldMagneticModel2025
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Phone heading for the map GPS cone: rotation-vector magnetic azimuth converted to true heading
 * with WMM declination only (no stakeout correction). Half-angle comes from `values[4]`.
 */
internal class DeviceHeadingProvider(
    context: Context,
    private val onHeadingChanged: () -> Unit
) : SensorEventListener {
    data class Sample(val trueDegrees: Float, val halfAngleDegrees: Float)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rotationMatrix = FloatArray(9)
    private val adjustedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var magneticHeading: Float? = null
    private var headingHalfAngle: Float? = null
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

    fun heading(): Sample? {
        if (rotationSensor == null) return null
        if (SystemClock.elapsedRealtime() - headingElapsedMillis > SENSOR_STALE_MILLIS) {
            return null
        }
        val magnetic = magneticHeading ?: return null
        val halfAngle = headingHalfAngle ?: return null
        return Sample(normalize(magnetic + declinationDegrees), halfAngle)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            clearHeading()
            onHeadingChanged()
            return
        }

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val (axisX, axisY) = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjustedMatrix)
        SensorManager.getOrientation(adjustedMatrix, orientation)

        val rawHeadingRadians = orientation[0].toDouble()
        val rawSin = sin(rawHeadingRadians)
        val rawCos = cos(rawHeadingRadians)
        if (!hasFilteredHeading) {
            filteredSin = rawSin
            filteredCos = rawCos
            hasFilteredHeading = true
        } else {
            filteredSin += FILTER_ALPHA * (rawSin - filteredSin)
            filteredCos += FILTER_ALPHA * (rawCos - filteredCos)
        }
        magneticHeading = normalize(Math.toDegrees(atan2(filteredSin, filteredCos)).toFloat())
        headingHalfAngle = halfAngleDegrees(event)
        if (headingHalfAngle == null) {
            magneticHeading = null
            hasFilteredHeading = false
            onHeadingChanged()
            return
        }
        headingElapsedMillis = SystemClock.elapsedRealtime()
        onHeadingChanged()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR
            && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
        ) {
            clearHeading()
            onHeadingChanged()
        }
    }

    private fun halfAngleDegrees(event: SensorEvent): Float? {
        val estimatedRadians = if (event.values.size >= 5) event.values[4] else -1f
        val rawDegrees = if (estimatedRadians >= 0f) {
            Math.toDegrees(estimatedRadians.toDouble()).toFloat()
        } else {
            when (event.accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 15f
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 35f
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 60f
                else -> return null
            }
        }
        return UserLocationGeometry.clampHalfAngleDegrees(rawDegrees)
    }

    private fun clearHeading() {
        magneticHeading = null
        headingHalfAngle = null
        hasFilteredHeading = false
    }

    private fun normalize(value: Float): Float {
        val normalized = value % FULL_CIRCLE
        return if (normalized < 0f) normalized + FULL_CIRCLE else normalized
    }

    private companion object {
        const val FULL_CIRCLE = 360f
        const val SENSOR_STALE_MILLIS = 500L
        const val FILTER_ALPHA = 0.2
        const val DECLINATION_CACHE_DISTANCE_METERS = 1_000f
        const val DECLINATION_CACHE_AGE_MILLIS = 6L * 60L * 60L * 1_000L
    }
}
