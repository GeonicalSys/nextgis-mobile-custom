package com.nextgis.mobile.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Heading-cone half-angle from sensor accuracy, yaw jitter and magnetometer disturbance. */
internal object HeadingConeAccuracy {
    const val WINDOW_MILLIS = 500L
    const val OPEN_ALPHA = 0.45f
    const val CLOSE_ALPHA = 0.08f
    const val MAG_MIN_UT = 20f
    const val MAG_MAX_UT = 70f
    const val MAG_WEAK_UT = 10f
    const val MAG_STRONG_UT = 120f
    const val MAG_PENALTY_MAX_DEGREES = 45f
    const val MAX_HALF_ANGLE_DEGREES = 90f
    private const val PI = Math.PI.toFloat()
    private const val FULL_CIRCLE = 360f

    /**
     * `TYPE_ROTATION_VECTOR` values[4]: radians when in (0, π], otherwise OEM degrees.
     * `0` and `-1` mean "unavailable", not a perfect compass.
     */
    fun sensorAccuracyDegrees(valueCount: Int, valueAtIndex4: Float): Float? {
        if (valueCount < 5 || !valueAtIndex4.isFinite() || valueAtIndex4 <= 0f) return null
        val degrees = if (valueAtIndex4 > PI) {
            valueAtIndex4
        } else {
            Math.toDegrees(valueAtIndex4.toDouble()).toFloat()
        }
        return if (degrees.isFinite() && degrees > 0f) degrees else null
    }

    fun circularDeltaDegrees(a: Float, b: Float): Float {
        var delta = normalizeDegrees(a - b)
        if (delta > 180f) delta = FULL_CIRCLE - delta
        return delta
    }

    fun circularStdDevDegrees(headings: Iterable<Float>): Float {
        var count = 0
        var sumSin = 0.0
        var sumCos = 0.0
        for (heading in headings) {
            if (!heading.isFinite()) continue
            val radians = Math.toRadians(heading.toDouble())
            sumSin += sin(radians)
            sumCos += cos(radians)
            count++
        }
        if (count < 2) return 0f
        val meanDegrees = Math.toDegrees(atan2(sumSin, sumCos)).toFloat()
        var sumSq = 0.0
        var used = 0
        for (heading in headings) {
            if (!heading.isFinite()) continue
            val delta = circularDeltaDegrees(heading, meanDegrees).toDouble()
            sumSq += delta * delta
            used++
        }
        if (used < 2) return 0f
        return sqrt(sumSq / used).toFloat()
    }

    fun magneticPenaltyDegrees(fieldMicroTesla: Float?, unreliable: Boolean): Float {
        if (unreliable) return MAG_PENALTY_MAX_DEGREES
        val field = fieldMicroTesla ?: return 0f
        if (!field.isFinite() || field < 0f) return 0f
        return when {
            field < MAG_MIN_UT -> {
                val span = MAG_MIN_UT - MAG_WEAK_UT
                val t = ((MAG_MIN_UT - field) / span).coerceIn(0f, 1f)
                MAG_PENALTY_MAX_DEGREES * t
            }
            field > MAG_MAX_UT -> {
                val span = MAG_STRONG_UT - MAG_MAX_UT
                val t = ((field - MAG_MAX_UT) / span).coerceIn(0f, 1f)
                MAG_PENALTY_MAX_DEGREES * t
            }
            else -> 0f
        }
    }

    fun fieldMicroTesla(x: Float, y: Float, z: Float): Float {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return Float.NaN
        return sqrt(x * x + y * y + z * z)
    }

    fun rawHalfAngleDegrees(
        sensorAccuracyDegrees: Float?,
        headingStdDevDegrees: Float,
        rawVsFilteredDeltaDegrees: Float,
        magneticPenaltyDegrees: Float
    ): Float {
        var half = 0f
        if (sensorAccuracyDegrees != null && sensorAccuracyDegrees.isFinite()) {
            half = maxOf(half, sensorAccuracyDegrees)
        }
        if (headingStdDevDegrees.isFinite()) half = maxOf(half, headingStdDevDegrees)
        if (rawVsFilteredDeltaDegrees.isFinite()) half = maxOf(half, rawVsFilteredDeltaDegrees)
        if (magneticPenaltyDegrees.isFinite()) half = maxOf(half, magneticPenaltyDegrees)
        return half.coerceIn(0f, MAX_HALF_ANGLE_DEGREES)
    }

    fun smoothHalfAngle(previous: Float?, raw: Float): Float {
        if (previous == null || !previous.isFinite()) return raw
        if (!raw.isFinite()) return previous
        val alpha = if (raw > previous) OPEN_ALPHA else CLOSE_ALPHA
        return previous + alpha * (raw - previous)
    }

    fun normalizeDegrees(value: Float): Float {
        var normalized = value % FULL_CIRCLE
        if (normalized < 0f) normalized += FULL_CIRCLE
        return normalized
    }
}

/** Recent raw headings used for circular jitter of the GPS cone. */
internal class HeadingSampleWindow(
    private val windowMillis: Long = HeadingConeAccuracy.WINDOW_MILLIS
) {
    private val elapsedMillis = ArrayDeque<Long>()
    private val headings = ArrayDeque<Float>()

    fun add(nowElapsedMillis: Long, headingDegrees: Float) {
        elapsedMillis.addLast(nowElapsedMillis)
        headings.addLast(headingDegrees)
        while (elapsedMillis.isNotEmpty() && nowElapsedMillis - elapsedMillis.first() > windowMillis) {
            elapsedMillis.removeFirst()
            headings.removeFirst()
        }
    }

    fun stdDevDegrees(): Float = HeadingConeAccuracy.circularStdDevDegrees(headings)

    fun clear() {
        elapsedMillis.clear()
        headings.clear()
    }

    fun size(): Int = headings.size
}
