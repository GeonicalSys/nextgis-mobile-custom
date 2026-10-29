package com.nextgis.mobile.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Numeric cache observation only. It neither supplies a location nor diagnoses a delivery cause. */
final class DiagnosticsGpsFixWitness {
    interface Clock { long elapsedRealtimeMillis(); }
    interface Probe { Long cachedGpsElapsedRealtimeNanos(); }

    private DiagnosticsGpsFixWitness() { }

    static Map<String, Object> collect(Object appRawElapsedMillis, Clock clock, Probe probe) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_version", 1);
        result.put("read_status", "unavailable");
        result.put("probe_started_elapsed_realtime_ms", -1L);
        result.put("probe_finished_elapsed_realtime_ms", -1L);
        result.put("probe_duration_ms", -1L);
        result.put("cached_fix_elapsed_realtime_ms", -1L);
        result.put("cached_fix_age_ms", -1L);
        Long raw = integralMillis(appRawElapsedMillis);
        boolean missingRaw = appRawElapsedMillis == null || Long.valueOf(-1).equals(raw);
        result.put("app_raw_timestamp_status", missingRaw ? "missing" : raw == null || raw <= 0 ? "invalid" : "unvalidated");
        result.put("cache_vs_app_raw", "unknown");
        result.put("cache_minus_app_raw_ms", -1L);

        long started;
        try { started = clock.elapsedRealtimeMillis(); }
        catch (RuntimeException unavailable) { result.put("read_status", "clock_failed"); return result; }
        if (started < 0) { result.put("read_status", "clock_invalid"); return result; }
        result.put("probe_started_elapsed_realtime_ms", started);
        Long cachedNanos = null;
        String status = "unavailable";
        try {
            if (probe != null) {
                cachedNanos = probe.cachedGpsElapsedRealtimeNanos();
                status = cachedNanos == null ? "missing" : "available";
            }
        } catch (SecurityException denied) { status = "permission_denied"; }
        catch (RuntimeException failed) { status = "read_failed"; }
        result.put("read_status", status);
        long finished;
        try { finished = clock.elapsedRealtimeMillis(); }
        catch (RuntimeException unavailable) { result.put("read_status", "clock_failed"); return result; }
        if (finished < started) { result.put("read_status", "clock_invalid"); return result; }
        result.put("probe_finished_elapsed_realtime_ms", finished);
        result.put("probe_duration_ms", finished - started);
        boolean validRaw = raw != null && raw > 0 && raw <= started;
        if (!missingRaw && raw != null && raw > 0)
            result.put("app_raw_timestamp_status", validRaw ? "valid" : "future");
        if (!"available".equals(status)) return result;
        if (cachedNanos <= 0) { result.put("read_status", "invalid_fix_time"); return result; }
        long cachedMillis = cachedNanos / 1_000_000L;
        if (cachedMillis <= 0) { result.put("read_status", "invalid_fix_time"); return result; }
        if (cachedMillis > finished) { result.put("read_status", "future_fix_time"); return result; }
        result.put("cached_fix_elapsed_realtime_ms", cachedMillis);
        result.put("cached_fix_age_ms", finished - cachedMillis);
        if (missingRaw) result.put("cache_vs_app_raw", "app_raw_missing");
        else if (!validRaw) result.put("cache_vs_app_raw", "app_raw_invalid");
        else {
            // Both positive monotonic millisecond values are <= finished, so subtraction cannot overflow.
            long delta = cachedMillis - raw;
            result.put("cache_minus_app_raw_ms", delta);
            result.put("cache_vs_app_raw", delta == 0 ? "same" : delta > 0 ? "newer" : "older");
        }
        return result;
    }

    private static Long integralMillis(Object value) {
        return value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte
                ? ((Number) value).longValue() : null;
    }
}
