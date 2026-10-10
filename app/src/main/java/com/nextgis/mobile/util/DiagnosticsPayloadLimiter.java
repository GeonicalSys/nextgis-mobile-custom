package com.nextgis.mobile.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keep supplied diagnostic content without expensive/unbounded object serialization. */
final class DiagnosticsPayloadLimiter {
    private static final String OMITTED = "[diagnostic limit]";
    private int nodes = 256;
    private int characters = 32_768;
    private final IdentityHashMap<Object, Boolean> parents = new IdentityHashMap<>();

    static String text(String text) {
        return text == null ? null : text.substring(0, Math.min(text.length(), 4096));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> map(Map<String, ?> source) {
        if (source == null) return null;
        Object result = value(source);
        return result instanceof Map ? (Map<String, Object>) result
                : Collections.singletonMap("diagnostic_limit", OMITTED);
    }

    Map<String, String> strings(Map<String, String> source) {
        if (source == null) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (result.size() == 64 || nodes-- <= 0 || characters <= 0) break;
            result.put(key(entry.getKey()), boundedText(entry.getValue()));
        }
        return result;
    }

    List<String> strings(List<String> source) {
        if (source == null) return null;
        List<String> result = new ArrayList<>();
        for (String entry : source) {
            if (result.size() == 64 || nodes-- <= 0 || characters <= 0) break;
            result.add(boundedText(entry));
        }
        return result;
    }

    Object value(Object value) { return value(value, 0); }

    private Object value(Object value, int depth) {
        if (value == null || value == JSONObject.NULL) return null;
        if (--nodes < 0 || depth > 6 || characters <= 0) return OMITTED;
        if (value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof CharSequence) return boundedText(value.toString());
        if (parents.put(value, true) != null) return "[cyclic diagnostic]";
        try {
            if (value instanceof JSONObject) {
                JSONObject object = (JSONObject) value;
                Map<String, Object> result = new LinkedHashMap<>();
                java.util.Iterator<String> keys = object.keys();
                while (keys.hasNext() && result.size() < 64 && nodes > 0 && characters > 0) {
                    String rawKey = keys.next();
                    result.put(key(rawKey), value(object.opt(rawKey), depth + 1));
                }
                return result;
            }
            if (value instanceof Map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (result.size() == 64 || nodes <= 0 || characters <= 0) break;
                    result.put(key(entry.getKey()), value(entry.getValue(), depth + 1));
                }
                return result;
            }
            if (value instanceof JSONArray || value.getClass().isArray()) {
                int length = value instanceof JSONArray ? ((JSONArray) value).length() : Array.getLength(value);
                List<Object> result = new ArrayList<>();
                for (int i = 0; i < Math.min(length, 64) && nodes > 0 && characters > 0; i++) {
                    result.add(value(value instanceof JSONArray ? ((JSONArray) value).opt(i) : Array.get(value, i), depth + 1));
                }
                return result;
            }
            if (value instanceof Iterable) {
                List<Object> result = new ArrayList<>();
                for (Object entry : (Iterable<?>) value) {
                    if (result.size() == 64 || nodes <= 0 || characters <= 0) break;
                    result.add(value(entry, depth + 1));
                }
                return result;
            }
            // Unknown objects may have expensive toString/getters or cyclic internal graphs.
            return "[" + value.getClass().getSimpleName() + "]";
        } finally { parents.remove(value); }
    }

    private String key(Object value) {
        String key = value instanceof String ? (String) value
                : value instanceof Number || value instanceof Boolean ? String.valueOf(value) : "[diagnostic key]";
        return boundedText(key.substring(0, Math.min(key.length(), 256)));
    }

    private String boundedText(String value) {
        String result = text(value);
        if (result == null) return null;
        if (result.isEmpty()) return result;
        int length = Math.min(result.length(), Math.max(characters, 0));
        characters -= length;
        return length == 0 ? OMITTED : result.substring(0, length);
    }
}
