package com.nextgis.mobile.util;

/** Pure policy for detecting an inactive legacy-underlay stream and bounding retries. */
final class LegacyUnderlayTransferPolicy {
    static final long DEFAULT_INACTIVITY_TIMEOUT_MS = 90_000L;
    static final int DEFAULT_MAX_STALL_RETRIES = 1;

    private final long inactivityTimeoutMs;
    private final int maxStallRetries;

    LegacyUnderlayTransferPolicy() {
        this(DEFAULT_INACTIVITY_TIMEOUT_MS, DEFAULT_MAX_STALL_RETRIES);
    }

    LegacyUnderlayTransferPolicy(long inactivityTimeoutMs, int maxStallRetries) {
        if (inactivityTimeoutMs <= 0L) {
            throw new IllegalArgumentException("inactivityTimeoutMs must be positive");
        }
        if (maxStallRetries < 0) {
            throw new IllegalArgumentException("maxStallRetries must not be negative");
        }
        this.inactivityTimeoutMs = inactivityTimeoutMs;
        this.maxStallRetries = maxStallRetries;
    }

    boolean isStalled(long nowMs, long lastProgressAtMs) {
        return lastProgressAtMs > 0L && nowMs - lastProgressAtMs >= inactivityTimeoutMs;
    }

    boolean canRetry(int completedStallRetries) {
        return completedStallRetries < maxStallRetries;
    }
}
