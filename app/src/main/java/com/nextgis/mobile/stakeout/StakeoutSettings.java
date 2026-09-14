/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Copyright (c) 2026 GeonicalSystem
 */
package com.nextgis.mobile.stakeout;

import android.content.SharedPreferences;

public final class StakeoutSettings {
    public static final String KEY_SOUND_ENABLED = "stakeout_sound_enabled";
    public static final String KEY_FAR_DISTANCE = "stakeout_far_distance";
    public static final String KEY_MEDIUM_DISTANCE = "stakeout_medium_distance";
    public static final String KEY_NEAR_DISTANCE = "stakeout_near_distance";
    public static final String KEY_REACHED_DISTANCE = "stakeout_reached_distance";
    public static final String KEY_DECLINATION_CORRECTION = "stakeout_declination_correction";

    public static final double DEFAULT_FAR_DISTANCE = 5.0;
    public static final double DEFAULT_MEDIUM_DISTANCE = 1.0;
    public static final double DEFAULT_NEAR_DISTANCE = 0.5;
    public static final double DEFAULT_REACHED_DISTANCE = 0.1;
    public static final float DEFAULT_DECLINATION_CORRECTION = 0f;
    public static final float MIN_DECLINATION_CORRECTION = -180f;
    public static final float MAX_DECLINATION_CORRECTION = 180f;
    public static final float DECLINATION_CORRECTION_STEP = 0.1f;

    private StakeoutSettings() {
    }

    public static Thresholds loadThresholds(SharedPreferences preferences) {
        double far = readDistance(preferences, KEY_FAR_DISTANCE, DEFAULT_FAR_DISTANCE);
        double medium = readDistance(
                preferences, KEY_MEDIUM_DISTANCE, DEFAULT_MEDIUM_DISTANCE);
        double near = readDistance(preferences, KEY_NEAR_DISTANCE, DEFAULT_NEAR_DISTANCE);
        double reached = readDistance(
                preferences, KEY_REACHED_DISTANCE, DEFAULT_REACHED_DISTANCE);
        if (!isValid(far, medium, near, reached)) {
            return defaults();
        }
        return new Thresholds(far, medium, near, reached);
    }

    public static Thresholds defaults() {
        return new Thresholds(
                DEFAULT_FAR_DISTANCE,
                DEFAULT_MEDIUM_DISTANCE,
                DEFAULT_NEAR_DISTANCE,
                DEFAULT_REACHED_DISTANCE);
    }

    public static boolean isValid(double far, double medium, double near, double reached) {
        return Double.isFinite(far)
                && Double.isFinite(medium)
                && Double.isFinite(near)
                && Double.isFinite(reached)
                && far > medium
                && medium > near
                && near > reached
                && reached > 0.0;
    }

    public static double parseDistance(String value) throws NumberFormatException {
        return Double.parseDouble(value.trim().replace(',', '.'));
    }

    public static float loadCorrection(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_DECLINATION_CORRECTION;
        }
        try {
            if (!preferences.contains(KEY_DECLINATION_CORRECTION)) {
                return DEFAULT_DECLINATION_CORRECTION;
            }
            try {
                return correctionFromStoredValue(
                        preferences.getString(KEY_DECLINATION_CORRECTION, "0"));
            } catch (ClassCastException ignored) {
                return clampCorrection(preferences.getFloat(
                        KEY_DECLINATION_CORRECTION, DEFAULT_DECLINATION_CORRECTION));
            }
        } catch (ClassCastException ignored) {
            return DEFAULT_DECLINATION_CORRECTION;
        }
    }

    public static void saveCorrection(SharedPreferences preferences, float value) {
        if (preferences == null) {
            return;
        }
        preferences.edit()
                .putString(KEY_DECLINATION_CORRECTION, Float.toString(clampCorrection(value)))
                .apply();
    }

    public static float parseCorrection(String value) throws NumberFormatException {
        return (float) parseDistance(value.replace('−', '-'));
    }

    public static float correctionFromStoredValue(String value) {
        try {
            return clampCorrection(parseCorrection(value == null ? "0" : value));
        } catch (NumberFormatException exception) {
            return DEFAULT_DECLINATION_CORRECTION;
        }
    }

    public static float clampCorrection(float value) {
        if (!Float.isFinite(value)) {
            return DEFAULT_DECLINATION_CORRECTION;
        }
        float rounded = (float) (Math.round(value * 10.0) / 10.0);
        if (rounded > MAX_DECLINATION_CORRECTION) {
            return MAX_DECLINATION_CORRECTION;
        }
        if (rounded < MIN_DECLINATION_CORRECTION) {
            return MIN_DECLINATION_CORRECTION;
        }
        return rounded;
    }

    public static boolean isValidCorrection(float value) {
        return Float.isFinite(value)
                && value >= MIN_DECLINATION_CORRECTION
                && value <= MAX_DECLINATION_CORRECTION;
    }

    public static boolean isCorrectionReset(float value) {
        return Math.abs(clampCorrection(value)) < 0.05f;
    }

    private static double readDistance(
            SharedPreferences preferences, String key, double defaultValue) {
        try {
            String value = preferences.getString(key, Double.toString(defaultValue));
            return parseDistance(value == null ? Double.toString(defaultValue) : value);
        } catch (ClassCastException | NumberFormatException exception) {
            return defaultValue;
        }
    }

    public static final class Thresholds {
        public final double far;
        public final double medium;
        public final double near;
        public final double reached;

        public Thresholds(double far, double medium, double near, double reached) {
            this.far = far;
            this.medium = medium;
            this.near = near;
            this.reached = reached;
        }
    }
}
