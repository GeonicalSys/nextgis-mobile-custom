package com.nextgis.mobile.rendering;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Independent test-only MVT wire witness. Never exports geometry, coordinates or arbitrary keys. */
public final class SqliteMvtEvidence {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 12 * 1024;
    private SqliteMvtEvidence() { }

    public static JSONObject summarize(byte[] bytes) {
        JSONObject out = new JSONObject();
        JSONArray layers = new JSONArray();
        Budget budget = new Budget();
        try {
            out.put("schema_version", 1).put("complete", false).put("bytes", bytes == null ? 0 : bytes.length)
                    .put("layers", layers).put("total_records", 0);
            if (bytes == null) return out.put("status", "missing");
            if (bytes.length > MAX_BYTES) return out.put("status", "oversized");
            Wire tile = new Wire(bytes, 0, bytes.length, budget);
            while (tile.next()) if (tile.field == 3) {
                tile.require(2);
                if (layers.length() >= 8) throw new Invalid("layer_limit");
                layers.put(layer(tile.child(), budget));
            }
            out.put("total_records", budget.records).put("status", "decoded").put("complete", true);
        } catch (Invalid invalid) {
            put(out, "status", "invalid"); put(out, "error_code", invalid.code);
        } catch (JSONException | RuntimeException unexpected) {
            put(out, "status", "invalid"); put(out, "error_code", "summary_failure");
        }
        // Counts remain complete; only optional focus details can be dropped to fit the evidence budget.
        int omitted = 0;
        while (out.toString().getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES - 128) {
            boolean removed = false;
            for (int i = layers.length() - 1; i >= 0; i--) {
                JSONArray details = layers.optJSONObject(i).optJSONArray("focus_records");
                if (details != null && details.length() > 0) {
                    details.remove(details.length() - 1); omitted++; removed = true; break;
                }
            }
            if (!removed) break;
        }
        if (omitted > 0) put(out, "focus_details_omitted", omitted);
        return out;
    }

    private static JSONObject layer(Wire source, Budget budget) throws Invalid, JSONException {
        List<String> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        String name = null; int version = 0, extent = 4096;
        Wire metadata = source.copy();
        while (metadata.next()) {
            if (metadata.field == 1) { metadata.require(2); name = metadata.text(64); }
            if (metadata.field == 3) {
                metadata.require(2); if (keys.size() >= 8192) throw new Invalid("key_limit");
                // Full property keys are checked before an allowlisted match, never prefix-matched.
                if (metadata.length > 1024) throw new Invalid("key_size");
                keys.add(metadata.text(1024));
            }
            if (metadata.field == 4) {
                metadata.require(2); if (values.size() >= 8192) throw new Invalid("value_limit");
                values.add(value(metadata.child()));
            }
            if (metadata.field == 5) { metadata.require(0); extent = metadata.positiveInt(); }
            if (metadata.field == 15) { metadata.require(0); version = metadata.positiveInt(); }
        }
        if (name == null || name.isEmpty() || version == 0 || extent == 0) throw new Invalid("layer_metadata");
        JSONObject out = new JSONObject().put("name", name).put("version", version).put("extent", extent);
        JSONArray details = new JSONArray(); int[] types = new int[4]; int count = 0;
        Wire features = source.copy();
        while (features.next()) if (features.field == 2) {
            features.require(2);
            if (++budget.records > 65536) throw new Invalid("record_limit");
            count++;
            Record record = record(features.child(), keys, values);
            types[record.type]++;
            String fid = record.properties.has("featureid") ? record.properties.optString("featureid") : record.wireId;
            if (("31".equals(fid) || "62".equals(fid) || "99".equals(fid)) && budget.details < 12) {
                budget.details++;
                details.put(new JSONObject().put("wire_id", record.wireId == null ? JSONObject.NULL : record.wireId)
                        .put("feature_id", fid).put("type", record.type).put("properties", record.properties));
            }
        }
        return out.put("total_records", count).put("type_counts", new JSONObject()
                        .put("point", types[1]).put("line", types[2]).put("polygon", types[3]).put("unknown", types[0]))
                .put("key_count", keys.size()).put("value_count", values.size()).put("focus_records", details);
    }

    private static Record record(Wire fields, List<String> keys, List<Object> values) throws Invalid, JSONException {
        Record record = new Record(); int key = -1;
        while (fields.next()) {
            if (fields.field == 1) { fields.require(0); record.wireId = Long.toUnsignedString(fields.number); }
            else if (fields.field == 3) {
                fields.require(0);
                if (fields.number < 0 || fields.number > 3) throw new Invalid("geometry_type");
                record.type = (int) fields.number;
            } else if (fields.field == 2) {
                // Protobuf permits packed or unpacked repeated uint32 fields, including multiple chunks.
                if (fields.wire != 0 && fields.wire != 2) throw new Invalid("tags_wire");
                Wire tags = fields.wire == 2 ? fields.child() : null;
                if (tags != null && tags.pos == tags.end) continue;
                do {
                    long number = tags == null ? fields.number : tags.varint();
                    if (number < 0 || number > Integer.MAX_VALUE) throw new Invalid("tag_index");
                    int index = (int) number;
                    if (key < 0) {
                        if (index >= keys.size()) throw new Invalid("key_index");
                        key = index;
                    } else {
                        if (index >= values.size()) throw new Invalid("value_index");
                        String name = keys.get(key);
                        if (allowed(name)) record.properties.put(name, values.get(index));
                        key = -1;
                    }
                } while (tags != null && tags.pos < tags.end);
            } else if (fields.field == 4) {
                if (fields.wire == 2) { Wire geometry = fields.child(); while (geometry.pos < geometry.end) geometry.varint(); }
                else fields.require(0);
            }
        }
        if (key >= 0) throw new Invalid("odd_tags");
        return record;
    }

    private static boolean allowed(String key) {
        return key.equals("featureid") || key.equals("fillcolor") || key.equals("opacity")
                || key.equals("geometry_layout_key") || key.equals("_label_anchor") || key.equals("signature");
    }

    private static Object value(Wire fields) throws Invalid {
        Object value = null;
        while (fields.next()) {
            Object next;
            switch (fields.field) {
                case 1: fields.require(2); next = fields.text(160); break;
                case 2:
                    fields.require(5); float f = Float.intBitsToFloat((int) fields.number);
                    if (!Float.isFinite(f)) throw new Invalid("nonfinite_value"); next = f; break;
                case 3:
                    fields.require(1); double d = Double.longBitsToDouble(fields.number);
                    if (!Double.isFinite(d)) throw new Invalid("nonfinite_value"); next = d; break;
                case 4: fields.require(0); next = fields.number; break;
                case 5: fields.require(0); next = fields.number >= 0 ? fields.number : Long.toUnsignedString(fields.number); break;
                case 6: fields.require(0); next = (fields.number >>> 1) ^ -(fields.number & 1); break;
                case 7:
                    fields.require(0); if (fields.number != 0 && fields.number != 1) throw new Invalid("boolean_value");
                    next = fields.number != 0; break;
                default: continue;
            }
            if (value != null) throw new Invalid("duplicate_value");
            value = next;
        }
        if (value == null) throw new Invalid("empty_value");
        return value;
    }

    private static final class Record { String wireId; int type; final JSONObject properties = new JSONObject(); }
    private static final class Budget { int fields, varints, records, details; }
    private static final class Invalid extends Exception {
        final String code;
        Invalid(String code) { this.code = code; }
    }

    /** Views reference the input; no feature/geometry byte arrays are copied. */
    private static final class Wire {
        final byte[] bytes; final int start, end; final Budget budget;
        int pos, field, wire, valueStart, length; long number;
        Wire(byte[] bytes, int start, int end, Budget budget) {
            this.bytes = bytes; this.start = start; this.pos = start; this.end = end; this.budget = budget;
        }
        Wire copy() { return new Wire(bytes, start, end, budget); }
        Wire child() { return new Wire(bytes, valueStart, valueStart + length, budget); }
        void require(int expected) throws Invalid { if (wire != expected) throw new Invalid("field_wire"); }
        int positiveInt() throws Invalid {
            if (number < 0 || number > Integer.MAX_VALUE) throw new Invalid("integer_range");
            return (int) number;
        }
        String text(int limit) {
            String text = new String(bytes, valueStart, Math.min(length, limit * 4), StandardCharsets.UTF_8);
            return text.length() <= limit ? text : text.substring(0, limit);
        }
        boolean next() throws Invalid {
            if (pos == end) return false;
            if (++budget.fields > 262144) throw new Invalid("field_limit");
            long tag = varint(); long tagField = tag >>> 3;
            if (tagField == 0 || tagField > 0x1fffffffL) throw new Invalid("field_number");
            field = (int) tagField; wire = (int) (tag & 7); number = 0;
            if (wire == 0) { number = varint(); return true; }
            if (wire == 2) {
                long count = varint();
                if (count < 0 || count > end - pos) throw new Invalid("field_length");
                valueStart = pos; length = (int) count; pos += length; return true;
            }
            int count = wire == 1 ? 8 : wire == 5 ? 4 : -1;
            if (count < 0 || count > end - pos) throw new Invalid("fixed_wire");
            for (int i = 0; i < count; i++) number |= (long) (bytes[pos++] & 255) << (i * 8);
            return true;
        }
        long varint() throws Invalid {
            if (++budget.varints > 1048576) throw new Invalid("varint_limit");
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) throw new Invalid("truncated_varint");
                int b = bytes[pos++] & 255;
                if (shift == 63 && (b & 254) != 0) throw new Invalid("overflow_varint");
                value |= (long) (b & 127) << shift;
                if ((b & 128) == 0) return value;
            }
            throw new Invalid("overflow_varint");
        }
    }

    private static void put(JSONObject out, String key, Object value) {
        try { out.put(key, value); } catch (JSONException impossibleScalar) { /* Fixed scalar metadata. */ }
    }
}
