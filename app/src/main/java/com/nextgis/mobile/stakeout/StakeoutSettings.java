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

    public static final double DEFAULT_FAR_DISTANCE = 5.0;
    public static final double DEFAULT_MEDIUM_DISTANCE = 1.0;
    public static final double DEFAULT_NEAR_DISTANCE = 0.5;
    public static final double DEFAULT_REACHED_DISTANCE = 0.1;

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
