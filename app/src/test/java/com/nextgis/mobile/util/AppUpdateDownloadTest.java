package com.nextgis.mobile.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

import static org.junit.Assert.*;

public class AppUpdateDownloadTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] CONTENT = "verified update contents".getBytes(StandardCharsets.UTF_8);
    private final Request request = new Request.Builder().url("https://updates.test/releases/update.apk").build();

    private static class Guard implements AppUpdateDownload.Guard {
        boolean allowed = true;
        @Override public void check() throws IOException { if (!allowed) throw new IOException("roaming/stopped"); }
        @Override public void attach(Call call) {}
        @Override public void validateUrl(String url) throws IOException {
            if (!url.startsWith("https://updates.test/releases/")) throw new IOException("untrusted URL");
        }
    }

    private File destination() { return new File(temporary.getRoot(), "update.apk"); }
    private File partial() { return new File(destination().getPath() + ".part"); }
    private String hash() throws Exception {
        File source = temporary.newFile();
        Files.write(source.toPath(), CONTENT);
        return AppUpdateDownload.sha256(source, null);
    }
    private OkHttpClient client(int code, byte[] bytes, String range, AtomicReference<Request> seen) {
        return new OkHttpClient.Builder().addInterceptor(chain -> {
            seen.set(chain.request());
            Response.Builder response = new Response.Builder().request(chain.request())
                    .protocol(Protocol.HTTP_1_1).code(code).message("test")
                    .body(ResponseBody.create(bytes, MediaType.get("application/octet-stream")));
            if (range != null) response.header("Content-Range", range);
            return response.build();
        }).build();
    }

    @Test public void resumesPersistedPartialAfterRecreatingTheDownloader() throws Exception {
        Files.write(partial().toPath(), java.util.Arrays.copyOf(CONTENT, 7));
        AtomicReference<Request> seen = new AtomicReference<>();
        AppUpdateDownload.download(client(206, java.util.Arrays.copyOfRange(CONTENT, 7, CONTENT.length),
                        "bytes 7-" + (CONTENT.length - 1) + "/" + CONTENT.length, seen),
                request, destination(), CONTENT.length, hash(), new Guard());
        assertEquals("bytes=7-", seen.get().header("Range"));
        assertEquals("identity", seen.get().header("Accept-Encoding"));
        assertArrayEquals(CONTENT, Files.readAllBytes(destination().toPath()));
        assertFalse(partial().exists());
    }

    @Test public void ignoredRangeRestartsRatherThanAppending() throws Exception {
        Files.write(partial().toPath(), new byte[]{1, 2, 3});
        AppUpdateDownload.download(client(200, CONTENT, null, new AtomicReference<>()), request,
                destination(), CONTENT.length, hash(), new Guard());
        assertArrayEquals(CONTENT, Files.readAllBytes(destination().toPath()));
    }

    @Test public void wrongRangeCannotPublishOrKeepACorruptPartial() throws Exception {
        Files.write(partial().toPath(), new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> AppUpdateDownload.download(client(206, CONTENT,
                "bytes 0-100/101", new AtomicReference<>()), request,
                destination(), CONTENT.length, hash(), new Guard()));
        assertFalse(destination().exists());
        assertFalse(partial().exists());
    }

    @Test public void digestMismatchIsDiscardedAndNeverPublished() throws Exception {
        assertThrows(IOException.class, () -> AppUpdateDownload.download(client(200, CONTENT, null,
                new AtomicReference<>()), request, destination(), CONTENT.length, "0".repeat(64), new Guard()));
        assertFalse(destination().exists());
        assertFalse(partial().exists());
    }

    @Test public void roamingBeforeStartMakesNoHttpRequest() throws Exception {
        Guard guard = new Guard();
        guard.allowed = false;
        AtomicReference<Request> seen = new AtomicReference<>();
        assertThrows(IOException.class, () -> AppUpdateDownload.download(client(200, CONTENT, null, seen),
                request, destination(), CONTENT.length, hash(), guard));
        assertNull(seen.get());
        assertFalse(destination().exists());
    }

    @Test public void roamingOrWorkerStopDuringReadCannotPublishTheResponse() throws Exception {
        Guard guard = new Guard();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            BufferedSource source = Okio.buffer(new ForwardingSource(new Buffer().write(CONTENT)) {
                @Override public long read(Buffer sink, long count) throws IOException {
                    long result = super.read(sink, count);
                    guard.allowed = false;
                    return result;
                }
            });
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("test").body(new ResponseBody() {
                        @Override public MediaType contentType() { return MediaType.get("application/octet-stream"); }
                        @Override public long contentLength() { return CONTENT.length; }
                        @Override public BufferedSource source() { return source; }
                    }).build();
        }).build();
        assertThrows(IOException.class, () -> AppUpdateDownload.download(client, request,
                destination(), CONTENT.length, hash(), guard));
        assertFalse(destination().exists());
        assertEquals(0, partial().length());
    }

    @Test public void shortUnknownLengthResponseRemainsResumableRatherThanReady() throws Exception {
        byte[] prefix = java.util.Arrays.copyOf(CONTENT, 7);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
                new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("test").body(new ResponseBody() {
                            private final BufferedSource source = new Buffer().write(prefix);
                            @Override public MediaType contentType() { return null; }
                            @Override public long contentLength() { return -1; }
                            @Override public BufferedSource source() { return source; }
                        }).build()).build();
        assertThrows(IOException.class, () -> AppUpdateDownload.download(client, request,
                destination(), CONTENT.length, hash(), new Guard()));
        assertArrayEquals(prefix, Files.readAllBytes(partial().toPath()));
        assertFalse(destination().exists());
    }

    @Test public void completePartialIsVerifiedWithoutRepeatingTheDownload() throws Exception {
        Files.write(partial().toPath(), CONTENT);
        AtomicReference<Request> seen = new AtomicReference<>();
        AppUpdateDownload.download(client(500, new byte[0], null, seen), request,
                destination(), CONTENT.length, hash(), new Guard());
        assertNull(seen.get());
        assertArrayEquals(CONTENT, Files.readAllBytes(destination().toPath()));
    }

    @Test public void oversizedDeclaredResponseIsRejectedBeforeWriting() throws Exception {
        assertThrows(IOException.class, () -> AppUpdateDownload.download(client(200, CONTENT, null,
                new AtomicReference<>()), request, destination(), CONTENT.length - 1, hash(), new Guard()));
        assertFalse(destination().exists());
        assertFalse(partial().exists());
    }

    @Test public void existingCompleteFileSurvivesAFailedReplacement() throws Exception {
        Files.write(destination().toPath(), CONTENT);
        assertThrows(IOException.class, () -> AppUpdateDownload.download(client(503, new byte[0], null,
                new AtomicReference<>()), request, destination(), CONTENT.length, hash(), new Guard()));
        assertArrayEquals(CONTENT, Files.readAllBytes(destination().toPath()));
    }
}
