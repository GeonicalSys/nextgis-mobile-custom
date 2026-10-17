package com.nextgis.mobile.location;

/** Automatic GPS lasts until explicit exit or two continuous hours outside the app. */
public final class BackgroundLocationPolicy {
    public static final String PREFERENCE = "background_gps_enabled";
    public static final long INACTIVITY_TIMEOUT_MS = 2 * 60 * 60 * 1000L;
    private boolean opened, foreground;
    private long backgroundSince = -1L;

    public void foreground(long now) { opened = foreground = true; backgroundSince = -1L; }
    public void background(long now) {
        if (foreground) backgroundSince = now;
        foreground = false;
    }
    public void close() { opened = foreground = false; backgroundSince = -1L; }
    public boolean isOpened() { return opened; }
    public boolean wanted(boolean enabled, long now) {
        return enabled && opened && (foreground || remainingMs(now) > 0L);
    }
    public long remainingMs(long now) {
        return backgroundSince < 0L ? INACTIVITY_TIMEOUT_MS
                : Math.max(0L, INACTIVITY_TIMEOUT_MS - (now - backgroundSince));
    }
}
