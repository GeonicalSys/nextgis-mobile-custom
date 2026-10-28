package com.nextgis.mobile.util;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class DiagnosticsNativeTraceTest {
    static byte[] number(int field, long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); varint(out, (long) field << 3); varint(out, value); return out.toByteArray();
    }
    static byte[] message(int field, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); varint(out, ((long) field << 3) | 2); varint(out, data.length); out.write(data, 0, data.length); return out.toByteArray();
    }
    static byte[] text(int field, String value) { return message(field, value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    static byte[] join(byte[]... data) { ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] b : data) out.write(b, 0, b.length); return out.toByteArray(); }
    static void varint(ByteArrayOutputStream out, long n) { while ((n & ~127L) != 0) { out.write((int) n & 127 | 128); n >>>= 7; } out.write((int) n); }

    @Test public void extractsOnlyCrashingNativeFramesAndIgnoresMemoryLogsAndOtherThreads() throws Exception {
        byte[] frame = join(number(1, 0x1234), text(4, "mbgl::Renderer::render"), text(6, "/private/install/libmaplibre.so"), text(8, "build-id"));
        byte[] thread = join(number(1, 42), text(2, "private thread"), message(4, frame), text(5, "SECRET MEMORY"));
        byte[] tombstone = join(number(6, 42), text(14, "Renderer assertion failed"),
                message(16, join(number(1, 41), message(2, text(4, "OTHER THREAD")))),
                message(16, join(number(1, 42), message(2, thread))), text(18, "SECRET LOG"));
        Map<String, Object> result = DiagnosticsNativeTrace.read(new ByteArrayInputStream(tombstone), true);
        String serialized = new org.json.JSONObject(result).toString();
        assertEquals("captured", result.get("status"));
        assertTrue(serialized.contains("libmaplibre.so")); assertTrue(serialized.contains("0x1234"));
        assertTrue(serialized.contains("mbgl::Renderer::render")); assertTrue(serialized.contains("build-id"));
        assertFalse(serialized.contains("SECRET")); assertFalse(serialized.contains("/private"));
        assertFalse(serialized.contains("OTHER THREAD")); assertFalse(serialized.contains("private thread"));
    }

    @Test public void capsInputRejectsCorruptionAndClosesTheOsStream() {
        final boolean[] closed = {false};
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[DiagnosticsNativeTrace.MAX_BYTES + 2]) {
            @Override public void close() { closed[0] = true; }
        };
        assertEquals("oversized", DiagnosticsNativeTrace.read(input, true).get("status")); assertTrue(closed[0]);
        assertEquals("unavailable", DiagnosticsNativeTrace.read(new ByteArrayInputStream(new byte[]{(byte) 0xff}), true).get("status"));
        assertEquals("missing", DiagnosticsNativeTrace.read(null, true).get("status"));
    }

    @Test public void anrKeepsOnlyMainStackAndNeverOtherThreadContents() {
        String anr = "\"worker\"\n at private.worker(secret.java:1)\n\"main\" prio=5 tid=1\n at android.view.View.draw(View.java:42)\n\"pool\"\n at private.pool(secret.java:2)";
        assertEquals(java.util.List.of("at android.view.View.draw(View.java:42)"), DiagnosticsNativeTrace.mainStack(anr));
    }

    @Test public void oversizedTailDoesNotEraseAnAlreadyAvailableCrashingStack() {
        byte[] frame = join(number(1, 0x2345), text(4, "Renderer"), text(6, "/private/libmaplibre.so"));
        byte[] thread = message(16, join(number(1, 42), message(2, message(4, frame))));
        byte[] data = join(number(6, 42), thread, message(18, new byte[DiagnosticsNativeTrace.MAX_BYTES + 100]));
        Map<String,Object> result = DiagnosticsNativeTrace.read(new ByteArrayInputStream(data), true);
        assertEquals("captured_truncated", result.get("status"));
        assertTrue(new org.json.JSONObject(result).toString().contains("Renderer"));
    }

    @Test public void autoRecognizesNativeFramesButNeverExportsUnknownProtoFields() {
        byte[] frame = join(number(1, 0x3456), text(4, "Renderer::draw"), text(6, "/private/libmaplibre.so"));
        byte[] data = join(number(6, 42), text(14, "password SECRET"),
                message(16, join(number(1, 42), message(2, message(4, frame)))), text(18, "SECRET MEMORY"));
        Map<String,Object> result = DiagnosticsNativeTrace.read(new ByteArrayInputStream(data), DiagnosticsNativeTrace.Format.AUTO);
        assertEquals("captured", result.get("status"));
        assertEquals("native_protobuf", result.get("format"));
        assertEquals(42L, result.get("crashing_tid"));
        assertEquals(List.of(Map.of("rel_pc", "0x3456", "function", "Renderer::draw", "library", "libmaplibre.so")), result.get("native_frames"));
        assertFalse(result.toString().contains("SECRET"));
        assertFalse(result.toString().contains("/private"));
        assertFalse(result.containsKey("main_stack"));
    }

    @Test public void autoRecognizesRecoveredAnrAndRetainsOnlyBoundedMainFrames() {
        StringBuilder text = new StringBuilder("\"worker\"\n at SECRET.worker(file:1)\n\"main\" prio=5 tid=1\n");
        for (int i = 0; i < 30; i++) text.append(" at android.view.View.draw(View.java:").append(i).append(")\n");
        text.append("\"pool\"\n at SECRET.pool(file:2)");
        Map<String,Object> result = DiagnosticsNativeTrace.read(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)), DiagnosticsNativeTrace.Format.AUTO);
        assertEquals("captured", result.get("status"));
        assertEquals("anr_text", result.get("format"));
        List<?> frames = (List<?>) result.get("main_stack");
        assertEquals(24, frames.size());
        assertEquals("at android.view.View.draw(View.java:0)", frames.get(0));
        assertEquals("at android.view.View.draw(View.java:23)", frames.get(23));
        assertFalse(result.toString().contains("SECRET"));
        assertFalse(result.containsKey("crashing_tid"));
        assertFalse(result.containsKey("native_frames"));
    }

    @Test public void autoDoesNotInventAStackOrAFormatForMalformedOrUnknownData() {
        for (byte[] data : new byte[][]{{(byte) 0xff}, number(6, 42), "SECRET unknown text".getBytes(StandardCharsets.UTF_8)}) {
            Map<String,Object> result = DiagnosticsNativeTrace.read(new ByteArrayInputStream(data), DiagnosticsNativeTrace.Format.AUTO);
            assertEquals("unrecognized_format", result.get("status"));
            assertEquals("unknown", result.get("format"));
            assertFalse(result.containsKey("native_frames"));
            assertFalse(result.containsKey("main_stack"));
            assertFalse(result.toString().contains("SECRET"));
        }
    }

    @Test public void autoHonorsByteCapClosesStreamsAndKeepsIoFailureUnknown() {
        final boolean[] closed = {false};
        ByteArrayInputStream oversized = new ByteArrayInputStream(new byte[DiagnosticsNativeTrace.MAX_BYTES + 100]) {
            @Override public void close() { closed[0] = true; }
        };
        Map<String,Object> result = DiagnosticsNativeTrace.read(oversized, DiagnosticsNativeTrace.Format.AUTO);
        assertEquals("oversized", result.get("status"));
        assertEquals(DiagnosticsNativeTrace.MAX_BYTES + 1, result.get("bytes_read"));
        assertTrue(closed[0]);
        closed[0] = false;
        InputStream failing = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("SECRET/path"); }
            @Override public void close() { closed[0] = true; }
        };
        result = DiagnosticsNativeTrace.read(failing, DiagnosticsNativeTrace.Format.AUTO);
        assertEquals("unavailable", result.get("status"));
        assertEquals("unknown", result.get("format"));
        assertEquals("IOException", result.get("error_type"));
        assertTrue(closed[0]);
        assertFalse(result.toString().contains("SECRET"));
    }
}
