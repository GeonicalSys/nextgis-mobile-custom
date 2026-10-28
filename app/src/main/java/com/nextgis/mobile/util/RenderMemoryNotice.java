package com.nextgis.mobile.util;

import java.util.concurrent.atomic.AtomicBoolean;

/** One automatic resource notice per application process, including every map and Activity. */
public final class RenderMemoryNotice {
    private static final Session SESSION = new Session();
    private RenderMemoryNotice() { }
    public static boolean claim() { return SESSION.claim(); }
    static final class Session {
        private final AtomicBoolean shown = new AtomicBoolean();
        boolean claim() { return shown.compareAndSet(false, true); }
    }
}
