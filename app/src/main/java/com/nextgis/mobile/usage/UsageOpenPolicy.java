package com.nextgis.mobile.usage;

/** Counts visible openings only, without clock reads or duration state. */
final class UsageOpenPolicy {
    private boolean foreground;
    private boolean opened;
    String open() {
        if (foreground) return null;
        foreground = true;
        String result = opened ? "app_resume" : "app_open";
        opened = true;
        return result;
    }
    void hide() { foreground = false; }
    boolean isForeground() { return foreground; }
}
