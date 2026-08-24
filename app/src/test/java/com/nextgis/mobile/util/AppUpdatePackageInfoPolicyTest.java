package com.nextgis.mobile.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppUpdatePackageInfoPolicyTest
{
    private static final int LEGACY_FLAGS = 100;
    private static final int MODERN_FLAGS = 200;

    @Test
    public void androidEightThroughTenUseLegacyArchiveSignatures()
    {
        assertEquals(
                LEGACY_FLAGS,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(26, LEGACY_FLAGS, MODERN_FLAGS));
        assertEquals(
                LEGACY_FLAGS,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(28, LEGACY_FLAGS, MODERN_FLAGS));
        assertEquals(
                LEGACY_FLAGS,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(29, LEGACY_FLAGS, MODERN_FLAGS));
    }

    @Test
    public void androidElevenAndNewerUseModernArchiveSignatures()
    {
        assertEquals(
                MODERN_FLAGS,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(30, LEGACY_FLAGS, MODERN_FLAGS));
        assertEquals(
                MODERN_FLAGS,
                AppUpdatePackageInfoPolicy.signatureFlagsForSdk(36, LEGACY_FLAGS, MODERN_FLAGS));
    }
}
