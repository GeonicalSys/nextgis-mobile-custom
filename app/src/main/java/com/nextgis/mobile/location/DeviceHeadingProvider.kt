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
 * with WMM declination only (no stakeout correction). Half-angle follows heading uncertainty.
 */
internal class DeviceHeadingProvider(
    context: Context,
    private val onHeadingChanged: () -> Unit
) : SensorEventListener {
    data class Sample(val trueDegrees: Float, val halfAngleDegrees: Float)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rotationMatrix = FloatArray(9)
    private val adjustedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val headingWindow = HeadingSampleWindow()

    private var magneticHeading: Float? = null
    private var lastRawHeadingDegrees: Float? = null
    private var headingHalfAngle: Float? = null
    private var smoothedHalfAngle: Float? = null
    private var sensorAccuracyDegrees: Float? = null
    private var magneticFieldMicroTesla: Float? = null
    private var magneticUnreliable = false
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
        if (started && magneticSensor != null) {
            sensorManager.registerListener(
                this,
                magneticSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
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
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> onMagneticField(event)
            Sensor.TYPE_ROTATION_VECTOR -> onRotationVector(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        when (sensor?.type) {
            Sensor.TYPE_ROTATION_VECTOR -> if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                clearHeading()
                onHeadingChanged()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magneticUnreliable = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                if (hasFilteredHeading) {
                    publishHalfAngle(SystemClock.elapsedRealtime(), refreshStale = false)
                }
            }
        }
    }

    private fun onMagneticField(event: SensorEvent) {
        if (event.values.size < 3) return
        magneticUnreliable = event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
        magneticFieldMicroTesla = HeadingConeAccuracy.fieldMicroTesla(
            event.values[0],
            event.values[1],
            event.values[2]
        )
        if (hasFilteredHeading) {
            publishHalfAngle(SystemClock.elapsedRealtime(), refreshStale = false)
        }
    }

    private fun onRotationVector(event: SensorEvent) {
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
        val rawHeading = normalize(Math.toDegrees(rawHeadingRadians).toFloat())
        lastRawHeadingDegrees = rawHeading
        magneticHeading = normalize(Math.toDegrees(atan2(filteredSin, filteredCos)).toFloat())
        sensorAccuracyDegrees = HeadingConeAccuracy.sensorAccuracyDegrees(
            event.values.size,
            if (event.values.size >= 5) event.values[4] else -1f
        )
        val now = SystemClock.elapsedRealtime()
        headingWindow.add(now, rawHeading)
        publishHalfAngle(now, refreshStale = true)
    }

    private fun publishHalfAngle(nowElapsedMillis: Long, refreshStale: Boolean) {
        val raw = lastRawHeadingDegrees ?: return
        val filtered = magneticHeading ?: return
        val combined = HeadingConeAccuracy.rawHalfAngleDegrees(
            sensorAccuracyDegrees,
            headingWindow.stdDevDegrees(),
            HeadingConeAccuracy.circularDeltaDegrees(raw, filtered),
            HeadingConeAccuracy.magneticPenaltyDegrees(magneticFieldMicroTesla, magneticUnreliable)
        )
        val smoothed = HeadingConeAccuracy.smoothHalfAngle(smoothedHalfAngle, combined)
        smoothedHalfAngle = smoothed
        headingHalfAngle = UserLocationGeometry.clampHalfAngleDegrees(smoothed)
        if (refreshStale) headingElapsedMillis = nowElapsedMillis
        onHeadingChanged()
    }

    private fun clearHeading() {
        magneticHeading = null
        lastRawHeadingDegrees = null
        headingHalfAngle = null
        smoothedHalfAngle = null
        sensorAccuracyDegrees = null
        hasFilteredHeading = false
        headingWindow.clear()
    }

    private fun normalize(value: Float): Float = HeadingConeAccuracy.normalizeDegrees(value)

    private companion object {
        const val SENSOR_STALE_MILLIS = 500L
        const val FILTER_ALPHA = 0.2
        const val DECLINATION_CACHE_DISTANCE_METERS = 1_000f
        const val DECLINATION_CACHE_AGE_MILLIS = 6L * 60L * 60L * 1_000L
    }
}
