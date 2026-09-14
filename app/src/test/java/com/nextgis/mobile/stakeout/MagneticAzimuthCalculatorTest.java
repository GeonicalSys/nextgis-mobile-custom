package com.nextgis.mobile.stakeout;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MagneticAzimuthCalculatorTest {
    @Test
    public void subtractsEastDeclinationAndWrapsClockwise() {
        assertEquals(
                355f,
                MagneticAzimuthCalculator.fromTrueBearing(5.0, 10f, 100.0),
                0.0001f);
        assertEquals(
                15f,
                MagneticAzimuthCalculator.fromTrueBearing(5.0, -10f, 100.0),
                0.0001f);
    }

    @Test
    public void azimuthIsUndefinedForCoincidentPoints() {
        assertNull(MagneticAzimuthCalculator.fromTrueBearing(0.0, 10f, 0.0));
    }

    @Test
    public void appliesPersistedCorrectionToModelDeclination() {
        assertEquals(10f, MagneticAzimuthCalculator.effectiveDeclination(10f, 0f), 0.0001f);
        assertEquals(11f, MagneticAzimuthCalculator.effectiveDeclination(10f, 1f), 0.0001f);
        assertEquals(0f, MagneticAzimuthCalculator.effectiveDeclination(10f, -10f), 0.0001f);
        assertEquals(
                354f,
                MagneticAzimuthCalculator.fromTrueBearing(
                        5.0,
                        MagneticAzimuthCalculator.effectiveDeclination(10f, 1f),
                        100.0),
                0.0001f);
        assertEquals(
                359.5f,
                MagneticAzimuthCalculator.fromTrueBearing(
                        0.0,
                        MagneticAzimuthCalculator.effectiveDeclination(1f, -0.5f),
                        100.0),
                0.0001f);
    }
}
