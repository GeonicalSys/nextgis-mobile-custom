package com.nextgis.mobile.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Allowlisted Android tombstone fields only; never export registers, memory, logs or open files. */
final class DiagnosticsNativeTrace {
    static final int MAX_BYTES = 1024 * 1024;
    enum Format {
        ANR_TEXT("anr_text"), NATIVE_PROTOBUF("native_protobuf"), AUTO("auto");
        final String label;
        Format(String label) { this.label = label; }
    }

    static Map<String, Object> read(InputStream input, boolean nativeCrash) {
        return read(input, nativeCrash ? Format.NATIVE_PROTOBUF : Format.ANR_TEXT);
    }

    static Map<String, Object> read(InputStream input, Format format) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "missing");
        result.put("format", "unknown");
        if (input == null) return result;
        try (InputStream owned = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            boolean truncated = false;
            while ((count = owned.read(buffer, 0, Math.min(buffer.length, MAX_BYTES + 1 - bytes.size()))) > 0) {
                bytes.write(buffer, 0, count);
                if (bytes.size() > MAX_BYTES) { truncated = true; break; }
            }
            result.put("bytes_read", bytes.size());
            byte[] bounded = bytes.toByteArray();
            try {
                if (format == Format.AUTO) {
                    Map<String, Object> nativeTrace = null;
                    try { nativeTrace = tombstone(bounded, truncated); }
                    catch (IOException malformedOrText) { /* An OS trace can instead be prior ANR text. */ }
                    if (nativeTrace != null && meaningfulNativeFrames(nativeTrace)) {
                        result.putAll(nativeTrace);
                        result.put("format", Format.NATIVE_PROTOBUF.label);
                    } else {
                        List<String> frames = mainStack(new String(bounded, StandardCharsets.UTF_8));
                        if (!frames.isEmpty()) {
                            result.put("main_stack", frames);
                            result.put("format", Format.ANR_TEXT.label);
                        } else {
                            result.put("status", truncated ? "oversized" : "unrecognized_format");
                            return result;
                        }
                    }
                } else if (format == Format.NATIVE_PROTOBUF) {
                    result.putAll(tombstone(bounded, truncated));
                    result.put("format", format.label);
                } else {
                    result.put("main_stack", mainStack(new String(bounded, StandardCharsets.UTF_8)));
                    result.put("format", format.label);
                }
            } catch (IOException malformed) {
                result.put("status", truncated ? "oversized" : "unavailable");
                result.put("error_type", errorType(malformed));
                return result;
            }
            Object stack = result.get(Format.NATIVE_PROTOBUF.label.equals(result.get("format")) ? "native_frames" : "main_stack");
            boolean captured = stack instanceof List && !((List<?>) stack).isEmpty();
            result.put("status", captured ? (truncated ? "captured_truncated" : "captured") : truncated ? "oversized" : "empty_stack");
        } catch (IOException | RuntimeException error) {
            result.put("status", "unavailable"); result.put("error_type", errorType(error));
        }
        return result;
    }

    private static boolean meaningfulNativeFrames(Map<String, Object> trace) {
        Object tid = trace.get("crashing_tid"), frames = trace.get("native_frames");
        if (!(tid instanceof Number) || ((Number) tid).longValue() <= 0 || !(frames instanceof List)) return false;
        for (Object frame : (List<?>) frames)
            if (frame instanceof Map && !((Map<?, ?>) frame).isEmpty()) return true;
        return false;
    }

    static String errorType(Exception error) {
        String name = error.getClass().getSimpleName();
        return name.isEmpty() ? "Exception" : shortText(name);
    }

    static List<String> mainStack(String text) {
        List<String> frames = new ArrayList<>(); boolean main = false;
        // Avoid a second array containing every line of the one-MiB OS trace.
        for (int start = 0; start < text.length() && frames.size() < 24;) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            String line = text.substring(start, end); start = end + 1;
            if (line.startsWith("\"")) main = line.startsWith("\"main\"");
            if (main && (line.trim().startsWith("at ") || line.trim().startsWith("native: #")) && frames.size() < 24)
                frames.add(shortText(line.trim()));
        }
        return frames;
    }

    // Android's public tombstone.proto wire contract. Unknown fields are skipped by length;
    // only the crashing thread's backtrace is decoded, with fixed nesting and field budgets.
    static Map<String, Object> tombstone(byte[] bytes) throws IOException {
        return tombstone(bytes, false);
    }
    private static Map<String, Object> tombstone(byte[] bytes, boolean truncated) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        Reader top = new Reader(bytes, 0, bytes.length); long tid = 0;
        List<Reader> threads = new ArrayList<>();
        while (true) {
            try { if (!top.next()) break; }
            catch (IOException tail) { if (truncated) break; throw tail; }
            if (top.field == 6 && top.wire == 0) tid = top.number;
            if (top.field == 14 && top.wire == 2) {
                String message = top.text();
                if (!message.matches("(?is).*(://|password|token|\\b(?:select|insert|update|delete)\\s).*"))
                    result.put("abort_message", message);
            }
            if (top.field == 16 && top.wire == 2 && threads.size() < 128) threads.add(top.child());
        }
        result.put("crashing_tid", tid);
        for (Reader entry : threads) {
            long key = 0; Reader thread = null;
            while (entry.next()) {
                if (entry.field == 1 && entry.wire == 0) key = entry.number;
                if (entry.field == 2 && entry.wire == 2) thread = entry.child();
            }
            if (key != tid || thread == null) continue;
            List<Map<String, Object>> frames = new ArrayList<>();
            while (thread.next()) if (thread.field == 4 && thread.wire == 2 && frames.size() < 24) {
                Reader frame = thread.child(); Map<String, Object> fields = new LinkedHashMap<>();
                while (frame.next()) {
                    if (frame.field == 1 && frame.wire == 0) fields.put("rel_pc", "0x" + Long.toUnsignedString(frame.number, 16));
                    if (frame.field == 4 && frame.wire == 2) fields.put("function", frame.text());
                    if (frame.field == 6 && frame.wire == 2) fields.put("library", new java.io.File(frame.text()).getName());
                    if (frame.field == 8 && frame.wire == 2) fields.put("build_id", frame.text());
                }
                frames.add(fields);
            }
            result.put("native_frames", frames); break;
        }
        return result;
    }

    private static String shortText(String value) { return value.length() > 240 ? value.substring(0, 240) : value; }
    private static final class Reader {
        final byte[] bytes; final int end; int pos, field, wire, start, length, fields; long number;
        Reader(byte[] bytes, int start, int end) { this.bytes = bytes; this.pos = start; this.end = end; }
        long varint() throws IOException {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) throw new IOException("Truncated protobuf");
                int b = bytes[pos++] & 255; value |= (long) (b & 127) << shift;
                if (shift == 63 && (b & 254) != 0) throw new IOException("Invalid protobuf varint");
                if ((b & 128) == 0) return value;
            }
            throw new IOException("Invalid protobuf varint");
        }
        boolean next() throws IOException {
            if (pos == end) return false;
            if (++fields > 100_000) throw new IOException("Field budget");
            long tag = varint(); field = (int) (tag >>> 3); wire = (int) (tag & 7);
            if (field == 0) throw new IOException("Invalid field");
            if (wire == 0) { number = varint(); return true; }
            long count = wire == 2 ? varint() : wire == 1 ? 8 : wire == 5 ? 4 : -1;
            if (count < 0 || count > end - pos) throw new IOException("Invalid protobuf length");
            start = pos; length = (int) count; pos += length; return true;
        }
        Reader child() { return new Reader(bytes, start, start + length); }
        String text() { return shortText(new String(bytes, start, Math.min(length, 960), StandardCharsets.UTF_8)); }
    }
}
