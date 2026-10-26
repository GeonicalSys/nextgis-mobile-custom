package com.nextgis.mobile.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Test stream reader for every supported Android API; the caller owns the stream. */
public final class VerificationIo {
    private VerificationIo() {}

    public static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) result.write(buffer, 0, count);
        return result.toByteArray();
    }
}
