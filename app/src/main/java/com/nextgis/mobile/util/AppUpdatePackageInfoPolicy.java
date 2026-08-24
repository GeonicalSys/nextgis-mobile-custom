/*
 * Project: NextGIS Mobile
 * Purpose: Select archive signature flags while retaining the Android 9/10 fallback.
 */

package com.nextgis.mobile.util;

final class AppUpdatePackageInfoPolicy
{
    private static final int SIGNING_INFO_API = 28;

    private AppUpdatePackageInfoPolicy()
    {
    }


    static int signatureFlagsForSdk(int sdkInt, int legacyFlags, int modernFlags)
    {
        // Android 9 and 10 can leave archive SigningInfo empty, so keep requesting the legacy
        // signatures array even when the modern field is available. certificateDigests() then
        // prefers SigningInfo and falls back to this populated legacy array.
        return sdkInt >= SIGNING_INFO_API ? legacyFlags | modernFlags : legacyFlags;
    }
}
