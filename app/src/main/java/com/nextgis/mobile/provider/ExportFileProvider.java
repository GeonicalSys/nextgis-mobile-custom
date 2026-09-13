package com.nextgis.mobile.provider;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import java.util.Locale;

/** Keep the shared URI's MIME type consistent with the GPX share intent and filename. */
public final class ExportFileProvider extends FileProvider {
    @Override
    public String getType(@NonNull Uri uri) {
        // Retain FileProvider's canonical path / configured root validation.
        String detected = super.getType(uri);
        return isGpx(uri) ? "application/gpx+xml" : detected;
    }

    @Override
    public String getTypeAnonymous(@NonNull Uri uri) {
        // Android can resolve the type before the recipient receives its URI grant.
        // This reports only a format, without probing whether a particular file exists.
        return isGpx(uri) ? getType(uri) : "application/octet-stream";
    }

    private static boolean isGpx(Uri uri) {
        String name = uri.getLastPathSegment();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".gpx");
    }
}
