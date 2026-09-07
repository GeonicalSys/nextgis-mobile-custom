/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (C) 2015, 2019-2024 Benoit Touchette
 * Copyright (C) 2026 GeonicalSystem
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nextgis.mobile.stakeout;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Offline World Magnetic Model 2025 calculation. The model is valid for 2025-2030.
 * Coefficients are from the official US/UK WMM2025 release.
 */
public final class WorldMagneticModel2025 {
    private static final float EARTH_SEMI_MAJOR_AXIS_KM = 6378.137f;
    private static final float EARTH_SEMI_MINOR_AXIS_KM = 6356.7523142f;
    private static final float EARTH_REFERENCE_RADIUS_KM = 6371.2f;
    private static final int BASE_YEAR = 2025;

    private static final float[][] G_COEFF = new float[][]{
            {0.0f},
            {-29351.8f, -1410.8f},
            {-2556.6f, 2951.1f, 1649.3f},
            {1361.0f, -2404.1f, 1243.8f, 453.6f},
            {895.0f, 799.5f, 55.7f, -281.1f, 12.1f},
            {-233.2f, 368.9f, 187.2f, -138.7f, -142.0f, 20.9f},
            {64.4f, 63.8f, 76.9f, -115.7f, -40.9f, 14.9f, -60.7f},
            {79.5f, -77.0f, -8.8f, 59.3f, 15.8f, 2.5f, -11.1f, 14.2f},
            {23.2f, 10.8f, -17.5f, 2.0f, -21.7f, 16.9f, 15.0f, -16.8f, 0.9f},
            {4.6f, 7.8f, 3.0f, -0.2f, -2.5f, -13.1f, 2.4f, 8.6f, -8.7f, -12.9f},
            {-1.3f, -6.4f, 0.2f, 2.0f, -1.0f, -0.6f, -0.9f, 1.5f, 0.9f, -2.7f, -3.9f},
            {2.9f, -1.5f, -2.5f, 2.4f, -0.6f, -0.1f, -0.6f, -0.1f, 1.1f, -1.0f, -0.2f, 2.6f},
            {-2.0f, -0.2f, 0.3f, 1.2f, -1.3f, 0.6f, 0.6f, 0.5f, -0.1f, -0.4f, -0.2f, -1.3f, -0.7f},
    };

    private static final float[][] H_COEFF = new float[][]{
            {0.0f},
            {0.0f, 4545.4f},
            {0.0f, -3133.6f, -815.1f},
            {0.0f, -56.6f, 237.5f, -549.5f},
            {0.0f, 278.6f, -133.9f, 212.0f, -375.6f},
            {0.0f, 45.4f, 220.2f, -122.9f, 43.0f, 106.1f},
            {0.0f, -18.4f, 16.8f, 48.8f, -59.8f, 10.9f, 72.7f},
            {0.0f, -48.9f, -14.4f, -1.0f, 23.4f, -7.4f, -25.1f, -2.3f},
            {0.0f, 7.1f, -12.6f, 11.4f, -9.7f, 12.7f, 0.7f, -5.2f, 3.9f},
            {0.0f, -24.8f, 12.2f, 8.3f, -3.3f, -5.2f, 7.2f, -0.6f, 0.8f, 10.0f},
            {0.0f, 3.3f, 0.0f, 2.4f, 5.3f, -9.1f, 0.4f, -4.2f, -3.8f, 0.9f, -9.1f},
            {0.0f, 0.0f, 2.9f, -0.6f, 0.2f, 0.5f, -0.3f, -1.2f, -1.7f, -2.9f, -1.8f, -2.3f},
            {0.0f, -1.3f, 0.7f, 1.0f, -1.4f, -0.0f, 0.6f, -0.1f, 0.8f, 0.1f, -1.0f, 0.1f, 0.2f},
    };

    private static final float[][] DELTA_G = new float[][]{
            {0.0f},
            {12.0f, 9.7f},
            {-11.6f, -5.2f, -8.0f},
            {-1.3f, -4.2f, 0.4f, -15.6f},
            {-1.6f, -2.4f, -6.0f, 5.6f, -7.0f},
            {0.6f, 1.4f, 0.0f, 0.6f, 2.2f, 0.9f},
            {-0.2f, -0.4f, 0.9f, 1.2f, -0.9f, 0.3f, 0.9f},
            {-0.0f, -0.1f, -0.1f, 0.5f, -0.1f, -0.8f, -0.8f, 0.8f},
            {-0.1f, 0.2f, 0.0f, 0.5f, -0.1f, 0.3f, 0.2f, -0.0f, 0.2f},
            {-0.0f, -0.1f, 0.1f, 0.3f, -0.3f, 0.0f, 0.3f, -0.1f, 0.1f, -0.1f},
            {0.1f, 0.0f, 0.1f, 0.1f, -0.0f, -0.3f, 0.0f, -0.1f, -0.1f, -0.0f, -0.0f},
            {0.0f, -0.0f, 0.0f, 0.0f, 0.0f, -0.1f, 0.0f, -0.0f, -0.1f, -0.1f, -0.1f, -0.1f},
            {0.0f, 0.0f, -0.0f, -0.0f, -0.0f, -0.0f, 0.1f, -0.0f, 0.0f, 0.0f, -0.1f, -0.0f, -0.1f},
    };

    private static final float[][] DELTA_H = new float[][]{
            {0.0f},
            {0.0f, -21.5f},
            {0.0f, -27.7f, -12.1f},
            {0.0f, 4.0f, -0.3f, -4.1f},
            {0.0f, -1.1f, 4.1f, 1.6f, -4.4f},
            {0.0f, -0.5f, 2.2f, 0.4f, 1.7f, 1.9f},
            {0.0f, 0.3f, -1.6f, -0.4f, 0.9f, 0.7f, 0.9f},
            {0.0f, 0.6f, 0.5f, -0.8f, 0.0f, -1.0f, 0.6f, -0.2f},
            {0.0f, -0.2f, 0.5f, -0.4f, 0.4f, -0.5f, -0.6f, 0.3f, 0.2f},
            {0.0f, -0.3f, 0.3f, -0.3f, 0.3f, 0.2f, -0.1f, -0.2f, 0.4f, 0.1f},
            {0.0f, 0.0f, -0.0f, -0.2f, 0.1f, -0.1f, 0.1f, 0.0f, -0.1f, 0.2f, -0.0f},
            {0.0f, -0.0f, 0.1f, -0.0f, 0.1f, -0.0f, -0.0f, 0.1f, -0.0f, 0.0f, 0.0f, 0.0f},
            {0.0f, -0.0f, 0.0f, -0.1f, 0.1f, -0.0f, -0.0f, -0.0f, 0.0f, -0.0f, -0.0f, 0.0f, -0.1f},
    };

    private static final float[][] SCHMIDT_QUASI_NORM_FACTORS =
            computeSchmidtQuasiNormFactors(G_COEFF.length);

    private float fieldNorth;
    private float fieldEast;
    private float fieldDown;
    private float geocentricLatitudeRadians;
    private float geocentricLongitudeRadians;
    private float geocentricRadiusKm;

    public WorldMagneticModel2025(
            float latitudeDegrees,
            float longitudeDegrees,
            float altitudeMeters,
            long timeMillis) {
        final int maxN = G_COEFF.length;
        latitudeDegrees = Math.min(90.0f - 1e-5f, Math.max(-90.0f + 1e-5f, latitudeDegrees));
        computeGeocentricCoordinates(latitudeDegrees, longitudeDegrees, altitudeMeters);

        LegendreTable legendre = new LegendreTable(
                maxN - 1,
                (float) (Math.PI / 2.0 - geocentricLatitudeRadians));

        float[] relativeRadiusPower = new float[maxN + 2];
        relativeRadiusPower[0] = 1.0f;
        relativeRadiusPower[1] = EARTH_REFERENCE_RADIUS_KM / geocentricRadiusKm;
        for (int i = 2; i < relativeRadiusPower.length; ++i) {
            relativeRadiusPower[i] = relativeRadiusPower[i - 1] * relativeRadiusPower[1];
        }

        float[] sinLongitudeMultiple = new float[maxN];
        float[] cosLongitudeMultiple = new float[maxN];
        sinLongitudeMultiple[0] = 0.0f;
        cosLongitudeMultiple[0] = 1.0f;
        sinLongitudeMultiple[1] = (float) Math.sin(geocentricLongitudeRadians);
        cosLongitudeMultiple[1] = (float) Math.cos(geocentricLongitudeRadians);
        for (int m = 2; m < maxN; ++m) {
            int x = m >> 1;
            sinLongitudeMultiple[m] = sinLongitudeMultiple[m - x] * cosLongitudeMultiple[x]
                    + cosLongitudeMultiple[m - x] * sinLongitudeMultiple[x];
            cosLongitudeMultiple[m] = cosLongitudeMultiple[m - x] * cosLongitudeMultiple[x]
                    - sinLongitudeMultiple[m - x] * sinLongitudeMultiple[x];
        }

        float inverseCosLatitude = 1.0f / (float) Math.cos(geocentricLatitudeRadians);
        float yearsSinceBase = decimalYear(timeMillis) - BASE_YEAR;
        float geocentricNorth = 0.0f;
        float geocentricEast = 0.0f;
        float geocentricDown = 0.0f;
        for (int n = 1; n < maxN; n++) {
            for (int m = 0; m <= n; m++) {
                float g = G_COEFF[n][m] + yearsSinceBase * DELTA_G[n][m];
                float h = H_COEFF[n][m] + yearsSinceBase * DELTA_H[n][m];
                float longitudeTerm = g * cosLongitudeMultiple[m] + h * sinLongitudeMultiple[m];
                geocentricNorth += relativeRadiusPower[n + 2]
                        * longitudeTerm
                        * legendre.valuesDerivative[n][m]
                        * SCHMIDT_QUASI_NORM_FACTORS[n][m];
                geocentricEast += relativeRadiusPower[n + 2]
                        * m
                        * (g * sinLongitudeMultiple[m] - h * cosLongitudeMultiple[m])
                        * legendre.values[n][m]
                        * SCHMIDT_QUASI_NORM_FACTORS[n][m]
                        * inverseCosLatitude;
                geocentricDown -= (n + 1)
                        * relativeRadiusPower[n + 2]
                        * longitudeTerm
                        * legendre.values[n][m]
                        * SCHMIDT_QUASI_NORM_FACTORS[n][m];
            }
        }

        double latitudeDifference = Math.toRadians(latitudeDegrees) - geocentricLatitudeRadians;
        fieldNorth = (float) (geocentricNorth * Math.cos(latitudeDifference)
                + geocentricDown * Math.sin(latitudeDifference));
        fieldEast = geocentricEast;
        fieldDown = (float) (-geocentricNorth * Math.sin(latitudeDifference)
                + geocentricDown * Math.cos(latitudeDifference));
    }

    /** Positive declination means magnetic north is east of true north. */
    public float getDeclination() {
        return (float) Math.toDegrees(Math.atan2(fieldEast, fieldNorth));
    }

    public float getInclination() {
        return (float) Math.toDegrees(Math.atan2(fieldDown, Math.hypot(fieldNorth, fieldEast)));
    }

    public float getFieldStrength() {
        return (float) Math.sqrt(
                fieldNorth * fieldNorth + fieldEast * fieldEast + fieldDown * fieldDown);
    }

    private void computeGeocentricCoordinates(
            float latitudeDegrees,
            float longitudeDegrees,
            float altitudeMeters) {
        float altitudeKm = altitudeMeters / 1000.0f;
        float majorSquared = EARTH_SEMI_MAJOR_AXIS_KM * EARTH_SEMI_MAJOR_AXIS_KM;
        float minorSquared = EARTH_SEMI_MINOR_AXIS_KM * EARTH_SEMI_MINOR_AXIS_KM;
        double latitudeRadians = Math.toRadians(latitudeDegrees);
        float cosLatitude = (float) Math.cos(latitudeRadians);
        float sinLatitude = (float) Math.sin(latitudeRadians);
        float tanLatitude = sinLatitude / cosLatitude;
        float latitudeRadius = (float) Math.sqrt(
                majorSquared * cosLatitude * cosLatitude
                        + minorSquared * sinLatitude * sinLatitude);
        geocentricLatitudeRadians = (float) Math.atan(
                tanLatitude * (latitudeRadius * altitudeKm + minorSquared)
                        / (latitudeRadius * altitudeKm + majorSquared));
        geocentricLongitudeRadians = (float) Math.toRadians(longitudeDegrees);
        float radiusSquared = altitudeKm * altitudeKm
                + 2 * altitudeKm * latitudeRadius
                + (majorSquared * majorSquared * cosLatitude * cosLatitude
                + minorSquared * minorSquared * sinLatitude * sinLatitude)
                / (majorSquared * cosLatitude * cosLatitude
                + minorSquared * sinLatitude * sinLatitude);
        geocentricRadiusKm = (float) Math.sqrt(radiusSquared);
    }

    private static float decimalYear(long timeMillis) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(timeMillis);
        int year = calendar.get(Calendar.YEAR);
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        int daysInYear = calendar.getActualMaximum(Calendar.DAY_OF_YEAR);
        long startOfDayMillis = timeMillis
                - calendar.get(Calendar.HOUR_OF_DAY) * 60L * 60L * 1000L
                - calendar.get(Calendar.MINUTE) * 60L * 1000L
                - calendar.get(Calendar.SECOND) * 1000L
                - calendar.get(Calendar.MILLISECOND);
        double fractionOfDay = (timeMillis - startOfDayMillis) / (24.0 * 60.0 * 60.0 * 1000.0);
        return (float) (year + (dayOfYear - 1 + fractionOfDay) / daysInYear);
    }

    private static final class LegendreTable {
        final float[][] values;
        final float[][] valuesDerivative;

        LegendreTable(int maxN, float thetaRadians) {
            float cos = (float) Math.cos(thetaRadians);
            float sin = (float) Math.sin(thetaRadians);
            values = new float[maxN + 1][];
            valuesDerivative = new float[maxN + 1][];
            values[0] = new float[]{1.0f};
            valuesDerivative[0] = new float[]{0.0f};
            for (int n = 1; n <= maxN; n++) {
                values[n] = new float[n + 1];
                valuesDerivative[n] = new float[n + 1];
                for (int m = 0; m <= n; m++) {
                    if (n == m) {
                        values[n][m] = sin * values[n - 1][m - 1];
                        valuesDerivative[n][m] = cos * values[n - 1][m - 1]
                                + sin * valuesDerivative[n - 1][m - 1];
                    } else if (n == 1 || m == n - 1) {
                        values[n][m] = cos * values[n - 1][m];
                        valuesDerivative[n][m] = -sin * values[n - 1][m]
                                + cos * valuesDerivative[n - 1][m];
                    } else {
                        float k = ((n - 1) * (n - 1) - m * m)
                                / (float) ((2 * n - 1) * (2 * n - 3));
                        values[n][m] = cos * values[n - 1][m] - k * values[n - 2][m];
                        valuesDerivative[n][m] = -sin * values[n - 1][m]
                                + cos * valuesDerivative[n - 1][m]
                                - k * valuesDerivative[n - 2][m];
                    }
                }
            }
        }
    }

    private static float[][] computeSchmidtQuasiNormFactors(int maxN) {
        float[][] factors = new float[maxN + 1][];
        factors[0] = new float[]{1.0f};
        for (int n = 1; n <= maxN; n++) {
            factors[n] = new float[n + 1];
            factors[n][0] = factors[n - 1][0] * (2 * n - 1) / (float) n;
            for (int m = 1; m <= n; m++) {
                factors[n][m] = factors[n][m - 1]
                        * (float) Math.sqrt((n - m + 1) * (m == 1 ? 2 : 1)
                        / (float) (n + m));
            }
        }
        return factors;
    }
}
