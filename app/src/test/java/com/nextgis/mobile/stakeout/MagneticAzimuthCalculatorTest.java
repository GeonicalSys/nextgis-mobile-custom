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
}
