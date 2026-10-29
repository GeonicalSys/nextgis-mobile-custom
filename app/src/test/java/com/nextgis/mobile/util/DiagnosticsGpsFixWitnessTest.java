package com.nextgis.mobile.util;

import org.junit.Test;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DiagnosticsGpsFixWitnessTest {
    private Map<String, Object> collect(Object rawMillis, Long cachedNanos) {
        AtomicInteger tick = new AtomicInteger();
        return DiagnosticsGpsFixWitness.collect(rawMillis, () -> tick.getAndIncrement() == 0 ? 10_000 : 10_025,
                () -> cachedNanos);
    }

    @Test public void missingCacheDoesNotInventAFixOrComparison() {
        Map<String, Object> result = collect(9_000L, null);
        assertEquals("missing", result.get("read_status"));
        assertEquals(-1L, result.get("cached_fix_elapsed_realtime_ms"));
        assertEquals(-1L, result.get("cached_fix_age_ms"));
        assertEquals("unknown", result.get("cache_vs_app_raw"));
        assertEquals(25L, result.get("probe_duration_ms"));
    }

    @Test public void olderSameAndNewerReferOnlyToTheTwoMeasurementTimestamps() {
        for (long cachedMillis : new long[]{8_999, 9_000, 9_001}) {
            Map<String, Object> result = collect(9_000L, cachedMillis * 1_000_000L);
            assertEquals("available", result.get("read_status"));
            assertEquals(cachedMillis, result.get("cached_fix_elapsed_realtime_ms"));
            assertEquals(10_025 - cachedMillis, result.get("cached_fix_age_ms"));
            assertEquals(cachedMillis - 9_000, result.get("cache_minus_app_raw_ms"));
            assertEquals(cachedMillis < 9_000 ? "older" : cachedMillis == 9_000 ? "same" : "newer",
                    result.get("cache_vs_app_raw"));
        }
    }

    @Test public void comparisonUsesTheSameMillisecondPrecisionAsTheAppSnapshot() {
        assertEquals("same", collect(9_000L, 9_000_999_999L).get("cache_vs_app_raw"));
    }

    @Test public void absentRawFromOldSchemaOrNewSentinelRemainsUnknown() {
        for (Object raw : new Object[]{null, -1L}) {
            Map<String, Object> result = collect(raw, 9_000_000_000L);
            assertEquals("missing", result.get("app_raw_timestamp_status"));
            assertEquals("app_raw_missing", result.get("cache_vs_app_raw"));
            assertEquals(-1L, result.get("cache_minus_app_raw_ms"));
        }
    }

    @Test public void invalidRawTypesAndNonpositiveValuesAreNotCoerced() {
        for (Object raw : new Object[]{0L, -2L, Long.MIN_VALUE, "9000", 9_000d, Double.NaN, new Object()}) {
            Map<String, Object> result = collect(raw, 9_000_000_000L);
            assertEquals("invalid", result.get("app_raw_timestamp_status"));
            assertEquals("app_raw_invalid", result.get("cache_vs_app_raw"));
            assertEquals(-1L, result.get("cache_minus_app_raw_ms"));
        }
    }

    @Test public void futureRawTimestampDoesNotProduceAnOverflowedDifference() {
        for (long raw : new long[]{10_001, Long.MAX_VALUE}) {
            Map<String, Object> result = collect(raw, 9_000_000_000L);
            assertEquals("future", result.get("app_raw_timestamp_status"));
            assertEquals("app_raw_invalid", result.get("cache_vs_app_raw"));
            assertEquals(-1L, result.get("cache_minus_app_raw_ms"));
        }
    }

    @Test public void invalidCachedNanosAreRejectedWithoutInventingZeroAge() {
        for (long cached : new long[]{0, -1, Long.MIN_VALUE, 999_999}) {
            Map<String, Object> result = collect(9_000L, cached);
            assertEquals("invalid_fix_time", result.get("read_status"));
            assertEquals(-1L, result.get("cached_fix_age_ms"));
            assertEquals("unknown", result.get("cache_vs_app_raw"));
        }
    }

    @Test public void futureAndMaximumCachedTimestampAreRejected() {
        for (long cached : new long[]{10_026_000_000L, Long.MAX_VALUE}) {
            Map<String, Object> result = collect(9_000L, cached);
            assertEquals("future_fix_time", result.get("read_status"));
            assertEquals(-1L, result.get("cached_fix_elapsed_realtime_ms"));
            assertEquals(-1L, result.get("cached_fix_age_ms"));
        }
    }

    @Test public void securityAndRuntimeFailuresKeepOnlyFixedFailureEnums() {
        for (boolean denied : new boolean[]{true, false}) {
            AtomicInteger reads = new AtomicInteger();
            Map<String, Object> result = DiagnosticsGpsFixWitness.collect(9_000L, () -> 10_000L, () -> {
                reads.incrementAndGet();
                if (denied) throw new SecurityException("secret coordinates or endpoint");
                throw new IllegalStateException("secret coordinates or endpoint");
            });
            assertEquals(1, reads.get());
            assertEquals(denied ? "permission_denied" : "read_failed", result.get("read_status"));
            assertFalse(result.toString().contains("secret"));
            assertEquals("unknown", result.get("cache_vs_app_raw"));
        }
    }

    @Test public void unavailableProbeIsDistinctFromAnEmptySystemCache() {
        Map<String, Object> result = DiagnosticsGpsFixWitness.collect(9_000L, () -> 10_000L, null);
        assertEquals("unavailable", result.get("read_status"));
        assertEquals("valid", result.get("app_raw_timestamp_status"));
    }

    @Test public void invalidStartClockNeverCallsTheProbe() {
        AtomicInteger reads = new AtomicInteger();
        Map<String, Object> result = DiagnosticsGpsFixWitness.collect(9_000L, () -> Long.MIN_VALUE, () -> {
            reads.incrementAndGet(); return 9_000_000_000L;
        });
        assertEquals(0, reads.get());
        assertEquals("clock_invalid", result.get("read_status"));
    }

    @Test public void reversedClockRejectsAComparisonAndNegativeDuration() {
        AtomicInteger ticks = new AtomicInteger();
        Map<String, Object> result = DiagnosticsGpsFixWitness.collect(9_000L,
                () -> ticks.getAndIncrement() == 0 ? Long.MAX_VALUE : Long.MIN_VALUE, () -> 9_000_000_000L);
        assertEquals("clock_invalid", result.get("read_status"));
        assertEquals(-1L, result.get("probe_duration_ms"));
        assertEquals(-1L, result.get("cached_fix_age_ms"));
    }

    @Test public void clockFailureBeforeOrAfterReadIsFailOpenAndDoesNotRetry() {
        for (int failAt : new int[]{0, 1}) {
            AtomicInteger ticks = new AtomicInteger(), reads = new AtomicInteger();
            Map<String, Object> result = DiagnosticsGpsFixWitness.collect(9_000L, () -> {
                if (ticks.getAndIncrement() == failAt) throw new IllegalStateException("private detail");
                return 10_000;
            }, () -> { reads.incrementAndGet(); return 9_000_000_000L; });
            assertEquals(failAt, reads.get());
            assertEquals("clock_failed", result.get("read_status"));
            assertEquals("unknown", result.get("cache_vs_app_raw"));
        }
    }

    @Test public void successfulProbeReadsExactlyOnceAndRecordsTheObservationWindow() {
        AtomicInteger reads = new AtomicInteger(), ticks = new AtomicInteger();
        Map<String, Object> result = DiagnosticsGpsFixWitness.collect(9_000L,
                () -> ticks.getAndIncrement() == 0 ? 10_000 : 10_050,
                () -> { reads.incrementAndGet(); return 9_500_000_000L; });
        assertEquals(1, reads.get()); assertEquals(2, ticks.get());
        assertEquals(10_000L, result.get("probe_started_elapsed_realtime_ms"));
        assertEquals(10_050L, result.get("probe_finished_elapsed_realtime_ms"));
        assertEquals(50L, result.get("probe_duration_ms"));
        assertEquals(550L, result.get("cached_fix_age_ms"));
    }

    @Test public void onlyFixedKeysAndNumericOrEnumValuesCanBeSerialized() {
        Map<String, Object> result = collect(9_000L, 9_500_000_000L);
        assertEquals(Set.of("schema_version", "read_status", "probe_started_elapsed_realtime_ms",
                "probe_finished_elapsed_realtime_ms", "probe_duration_ms", "cached_fix_elapsed_realtime_ms",
                "cached_fix_age_ms", "app_raw_timestamp_status", "cache_vs_app_raw", "cache_minus_app_raw_ms"), result.keySet());
        for (Object value : result.values()) assertTrue(value instanceof Number || value instanceof String);
        assertFalse(result.containsKey("latitude")); assertFalse(result.containsKey("longitude"));
        assertFalse(result.containsKey("extras")); assertFalse(result.containsKey("provider"));
    }
}
