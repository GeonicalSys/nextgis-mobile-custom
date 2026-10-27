package com.nextgis.mobile.util;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
}
