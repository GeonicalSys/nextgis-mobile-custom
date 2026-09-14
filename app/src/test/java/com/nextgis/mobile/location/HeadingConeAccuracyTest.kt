package com.nextgis.mobile.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingConeAccuracyTest {
    @Test
    fun sensorAccuracyIgnoresMissingZeroAndNegative() {
        assertNull(HeadingConeAccuracy.sensorAccuracyDegrees(4, 0.2f))
        assertNull(HeadingConeAccuracy.sensorAccuracyDegrees(5, -1f))
        assertNull(HeadingConeAccuracy.sensorAccuracyDegrees(5, 0f))
        assertNull(HeadingConeAccuracy.sensorAccuracyDegrees(5, Float.NaN))
    }

    @Test
    fun sensorAccuracyReadsRadiansBelowPi() {
        val degrees = HeadingConeAccuracy.sensorAccuracyDegrees(5, 0.2f)
        assertEquals(Math.toDegrees(0.2).toFloat(), requireNotNull(degrees), 0.001f)
    }

    @Test
    fun sensorAccuracyTreatsValuesAbovePiAsDegrees() {
        assertEquals(15f, requireNotNull(HeadingConeAccuracy.sensorAccuracyDegrees(5, 15f)), 0.001f)
    }

    @Test
    fun circularDeltaWrapsAroundNorth() {
        assertEquals(20f, HeadingConeAccuracy.circularDeltaDegrees(350f, 10f), 0.001f)
        assertEquals(20f, HeadingConeAccuracy.circularDeltaDegrees(10f, 350f), 0.001f)
        assertEquals(0f, HeadingConeAccuracy.circularDeltaDegrees(0f, 360f), 0.001f)
    }

    @Test
    fun identicalHeadingsHaveZeroJitter() {
        assertEquals(
            0f,
            HeadingConeAccuracy.circularStdDevDegrees(listOf(40f, 40f, 40f)),
            0.001f
        )
    }

    @Test
    fun jitterWrapsAroundZeroInsteadOfUsingAHalfCircle() {
        val wrapped = HeadingConeAccuracy.circularStdDevDegrees(listOf(350f, 0f, 10f))
        val split = HeadingConeAccuracy.circularStdDevDegrees(listOf(0f, 180f))
        assertTrue(wrapped < 15f)
        assertTrue(split > 80f)
    }

    @Test
    fun magneticPenaltyIsZeroInTheEarthFieldBand() {
        assertEquals(0f, HeadingConeAccuracy.magneticPenaltyDegrees(45f, false), 0.001f)
        assertEquals(0f, HeadingConeAccuracy.magneticPenaltyDegrees(null, false), 0.001f)
    }

    @Test
    fun magneticPenaltyGrowsOutsideTheEarthFieldBand() {
        assertEquals(
            HeadingConeAccuracy.MAG_PENALTY_MAX_DEGREES,
            HeadingConeAccuracy.magneticPenaltyDegrees(10f, false),
            0.001f
        )
        assertEquals(
            HeadingConeAccuracy.MAG_PENALTY_MAX_DEGREES,
            HeadingConeAccuracy.magneticPenaltyDegrees(120f, false),
            0.001f
        )
        assertTrue(HeadingConeAccuracy.magneticPenaltyDegrees(15f, false) > 0f)
        assertEquals(
            HeadingConeAccuracy.MAG_PENALTY_MAX_DEGREES,
            HeadingConeAccuracy.magneticPenaltyDegrees(45f, true),
            0.001f
        )
    }

    @Test
    fun fieldMagnitudeIsTheEuclideanNorm() {
        assertEquals(5f, HeadingConeAccuracy.fieldMicroTesla(3f, 4f, 0f), 0.001f)
    }

    @Test
    fun rawHalfAngleTakesTheMaximumSource() {
        assertEquals(
            40f,
            HeadingConeAccuracy.rawHalfAngleDegrees(12f, 8f, 40f, 5f),
            0.001f
        )
        assertEquals(
            0f,
            HeadingConeAccuracy.rawHalfAngleDegrees(null, 0f, 0f, 0f),
            0.001f
        )
    }

    @Test
    fun smoothingOpensFasterThanItCloses() {
        val opened = HeadingConeAccuracy.smoothHalfAngle(10f, 40f)
        val closed = HeadingConeAccuracy.smoothHalfAngle(40f, 10f)
        assertEquals(10f + HeadingConeAccuracy.OPEN_ALPHA * 30f, opened, 0.001f)
        assertEquals(40f + HeadingConeAccuracy.CLOSE_ALPHA * -30f, closed, 0.001f)
        assertTrue(opened - 10f > 40f - closed)
    }

    @Test
    fun headingWindowDropsSamplesOlderThanTheWindow() {
        val window = HeadingSampleWindow(500L)
        window.add(0L, 0f)
        window.add(100L, 20f)
        window.add(600L, 20f)
        assertEquals(2, window.size())
        assertTrue(window.stdDevDegrees() < 1f)
    }
}
