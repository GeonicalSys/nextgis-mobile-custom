package com.nextgis.mobile.provider;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.nextgis.maplibui.util.GpxSharePublisher;

/** Advertise a MimeTypeMap-known type for GPX so messengers do not stringify a null extension. */
public final class ExportFileProvider extends FileProvider {
    @Override
    public String getType(@NonNull Uri uri) {
        // Retain FileProvider's canonical path / configured root validation.
        String detected = super.getType(uri);
        return GpxSharePublisher.isGpxName(uri.getLastPathSegment())
                ? GpxSharePublisher.URI_MIME_TYPE
                : detected;
    }

    @Override
    public String getTypeAnonymous(@NonNull Uri uri) {
        // Android can resolve the type before the recipient receives its URI grant.
        // This reports only a format, without probing whether a particular file exists.
        return GpxSharePublisher.isGpxName(uri.getLastPathSegment())
                ? getType(uri)
                : "application/octet-stream";
    }
}
