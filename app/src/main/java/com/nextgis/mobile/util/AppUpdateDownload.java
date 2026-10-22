package com.nextgis.mobile.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Resumable, bounded APK transfer. A partial file is never an installable update. */
final class AppUpdateDownload {
    interface Guard {
        void check() throws IOException;
        void attach(Call call);
        void validateUrl(String url) throws IOException;
    }

    private AppUpdateDownload() {}

    static void download(OkHttpClient client, Request request, File destination,
                         long size, String sha256, Guard guard)
            throws IOException, NoSuchAlgorithmException {
        if (size <= 0) throw new IOException("Invalid download size");
        File partial = new File(destination.getPath() + ".part");
        if (partial.length() > size) discard(partial);
        long offset = partial.length();
        guard.check();
        if (offset < size) {
            Request.Builder builder = request.newBuilder();
            if (offset > 0) builder.header("Range", "bytes=" + offset + "-");
            // Ranges and the manifest size describe the APK bytes, not HTTP compression.
            builder.header("Accept-Encoding", "identity");
            Call call = client.newCall(builder.build());
            guard.attach(call);
            guard.check();
            try (Response response = call.execute()) {
                guard.check();
                guard.validateUrl(response.request().url().toString());
                if (response.code() == 206) {
                    String expected = "bytes " + offset + "-" + (size - 1) + "/" + size;
                    if (!expected.equals(response.header("Content-Range"))) {
                        discard(partial);
                        throw new IOException("Unexpected APK Content-Range");
                    }
                } else if (response.code() == 200) {
                    offset = 0; // The server may ignore Range; restart rather than append.
                } else {
                    throw new IOException("APK HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Empty APK response");
                long remaining = size - offset;
                if (body.contentLength() >= 0 && body.contentLength() != remaining) {
                    discard(partial);
                    throw new IOException("APK Content-Length differs from manifest");
                }
                try (InputStream input = body.byteStream();
                     FileOutputStream output = new FileOutputStream(partial, offset > 0)) {
                    byte[] buffer = new byte[128 * 1024];
                    long written = offset;
                    int read;
                    while (true) {
                        guard.check();
                        read = input.read(buffer);
                        if (read == -1) break;
                        guard.check();
                        if (read > size - written) {
                            // Mark the corrupt transfer for deletion after its stream is closed.
                            throw new InvalidDownload("APK exceeds manifest size");
                        }
                        output.write(buffer, 0, read);
                        written += read;
                    }
                    output.getFD().sync();
                } catch (InvalidDownload error) {
                    discard(partial);
                    throw error;
                }
            }
        }
        guard.check();
        if (partial.length() != size) throw new IOException("Incomplete APK download");
        if (!sha256.equalsIgnoreCase(sha256(partial, guard))) {
            discard(partial);
            throw new IOException("APK SHA-256 differs from manifest");
        }
        guard.check();
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Cannot replace prepared APK");
        }
        if (!partial.renameTo(destination)) throw new IOException("Cannot finalize APK download");
    }

    static String sha256(File file, Guard guard) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (guard != null) guard.check();
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    private static void discard(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("Cannot discard invalid partial APK");
    }

    private static final class InvalidDownload extends IOException {
        InvalidDownload(String message) { super(message); }
    }
}
