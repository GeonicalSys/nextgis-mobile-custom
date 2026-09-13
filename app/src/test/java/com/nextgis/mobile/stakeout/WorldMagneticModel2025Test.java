package com.nextgis.mobile.stakeout;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class WorldMagneticModel2025Test {
    private static final double DECLINATION_TOLERANCE_DEGREES = 0.06;

    @Test
    public void matchesOfficialSurfaceTestValuesAtEpoch() {
        long epoch = utc(2025, Calendar.JANUARY, 1, 0);

        assertDeclination(1.28, 80f, 0f, epoch);
        assertDeclination(-0.16, 0f, 120f, epoch);
        assertDeclination(68.78, -80f, 240f, epoch);
    }

    @Test
    public void matchesOfficialSurfaceTestValuesAt2027Point5() {
        long year2027Point5 = utc(2027, Calendar.JULY, 2, 12);

        assertDeclination(2.59, 80f, 0f, year2027Point5);
        assertDeclination(-0.24, 0f, 120f, year2027Point5);
        assertDeclination(68.49, -80f, 240f, year2027Point5);
    }

    private static void assertDeclination(
            double expected,
            float latitude,
            float longitude,
            long timeMillis) {
        WorldMagneticModel2025 model =
                new WorldMagneticModel2025(latitude, longitude, 0f, timeMillis);
        assertEquals(expected, model.getDeclination(), DECLINATION_TOLERANCE_DEGREES);
    }

    private static long utc(int year, int month, int day, int hour) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month, day, hour, 0, 0);
        return calendar.getTimeInMillis();
    }
}
