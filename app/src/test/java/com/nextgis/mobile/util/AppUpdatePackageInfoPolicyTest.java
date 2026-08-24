package com.nextgis.mobile.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppUpdatePackageInfoPolicyTest
{
    private static final int LEGACY_FLAGS = 1;
    private static final int MODERN_FLAGS = 2;

    @Test
    public void preAndroidNineUsesLegacyArchiveSignatures()
    {
        assertEquals(
                LEGACY_FLAGS,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(
                        27, LEGACY_FLAGS, MODERN_FLAGS));
    }


    @Test
    public void androidNineAndNewerKeepLegacyFallbackWithModernSignatures()
    {
        int expected = LEGACY_FLAGS | MODERN_FLAGS;
        assertEquals(
                expected,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(
                        28, LEGACY_FLAGS, MODERN_FLAGS));
        assertEquals(
                expected,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(
                        29, LEGACY_FLAGS, MODERN_FLAGS));
        assertEquals(
                expected,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(
                        30, LEGACY_FLAGS, MODERN_FLAGS));
        assertEquals(
                expected,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(
                        36, LEGACY_FLAGS, MODERN_FLAGS));
    }
}
