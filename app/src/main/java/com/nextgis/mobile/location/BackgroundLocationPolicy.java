package com.nextgis.mobile.location;

import java.util.UUID;

/** Automatic GPS lasts until explicit exit or two continuous hours outside the app. */
public final class BackgroundLocationPolicy {
    public static final String PREFERENCE = "background_gps_enabled";
    public static final long INACTIVITY_TIMEOUT_MS = 2 * 60 * 60 * 1000L;
    private boolean opened, foreground;
    private boolean backgroundDisabled;
    private String sessionId;
    private long backgroundSince = -1L;

    public void foreground(long now) {
        if (!opened) {
            sessionId = UUID.randomUUID().toString();
            backgroundDisabled = false;
        }
        opened = foreground = true;
        backgroundSince = -1L;
    }
    public void background(long now) {
        if (foreground) backgroundSince = now;
        foreground = false;
    }
    public void close() {
        opened = foreground = false;
        sessionId = null;
        backgroundSince = -1L;
    }
    public boolean isOpened() { return opened; }
    public String getSessionId() { return sessionId; }

    /** Notification commands belong to a user session, not a service instance. */
    public boolean disableBackground(String expectedSessionId) {
        if (!opened || sessionId == null || !sessionId.equals(expectedSessionId)) return false;
        backgroundDisabled = true;
        return true;
    }

    public boolean wanted(boolean enabled, long now) {
        return enabled && opened && !backgroundDisabled && (foreground || remainingMs(now) > 0L);
    }
    public long remainingMs(long now) {
        return backgroundSince < 0L ? INACTIVITY_TIMEOUT_MS
                : Math.max(0L, INACTIVITY_TIMEOUT_MS - (now - backgroundSince));
    }
}
