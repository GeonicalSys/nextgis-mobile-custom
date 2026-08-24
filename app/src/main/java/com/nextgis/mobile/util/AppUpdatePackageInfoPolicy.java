/*
 * Project: NextGIS Mobile
 * Purpose: Select PackageManager signature compatibility for downloaded APK archives.
 */

package com.nextgis.mobile.util;

final class AppUpdatePackageInfoPolicy
{
    private static final int MODERN_ARCHIVE_SIGNATURE_API = 30;

    private AppUpdatePackageInfoPolicy()
    {
    }


    static int signatureFlagsForSdk(int sdkInt, int legacyFlags, int modernFlags)
    {
        // Android 9 and 10 getPackageArchiveInfo() collect archive certificates only when
        // GET_SIGNATURES is requested. Android 11 fixed it to honor GET_SIGNING_CERTIFICATES.
        return sdkInt < MODERN_ARCHIVE_SIGNATURE_API ? legacyFlags : modernFlags;
    }
}
