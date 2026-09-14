package com.nextgis.mobile.stakeout;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StakeoutSettingsTest {
    @Test
    public void parsesSignedCorrectionWithCommaAndUnicodeMinus() {
        assertEquals(1.5f, StakeoutSettings.parseCorrection("1.5"), 0.0001f);
        assertEquals(1.5f, StakeoutSettings.parseCorrection("1,5"), 0.0001f);
        assertEquals(-2.5f, StakeoutSettings.parseCorrection("−2,5"), 0.0001f);
        assertEquals(1.5f, StakeoutSettings.parseCorrection("+1.5"), 0.0001f);
    }

    @Test
    public void clampsAndRejectsInvalidStoredCorrection() {
        assertEquals(180f, StakeoutSettings.clampCorrection(200f), 0.0001f);
        assertEquals(-180f, StakeoutSettings.clampCorrection(-200f), 0.0001f);
        assertEquals(0f, StakeoutSettings.clampCorrection(Float.NaN), 0.0001f);
        assertEquals(0f, StakeoutSettings.clampCorrection(Float.POSITIVE_INFINITY), 0.0001f);
        assertEquals(1.2f, StakeoutSettings.clampCorrection(1.23f), 0.0001f);
        assertEquals(0f, StakeoutSettings.correctionFromStoredValue("not-a-number"), 0.0001f);
        assertEquals(0f, StakeoutSettings.correctionFromStoredValue(null), 0.0001f);
        assertEquals(180f, StakeoutSettings.correctionFromStoredValue("200"), 0.0001f);
        assertTrue(StakeoutSettings.isValidCorrection(0f));
        assertTrue(StakeoutSettings.isValidCorrection(-180f));
        assertFalse(StakeoutSettings.isValidCorrection(181f));
        assertTrue(StakeoutSettings.isCorrectionReset(0.04f));
        assertFalse(StakeoutSettings.isCorrectionReset(0.1f));
    }
}
