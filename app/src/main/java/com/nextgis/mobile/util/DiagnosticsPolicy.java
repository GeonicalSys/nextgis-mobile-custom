package com.nextgis.mobile.util;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Size/frequency limits and expected-failure filtering; no access to storage or accounts. */
final class DiagnosticsPolicy {
    static final int CACHE_ITEMS = 256;
    static final int BREADCRUMBS = 80;
    private static final long REPEAT_INTERVAL_MS = 10 * 60_000L;
    private final LinkedHashMap<String, Long> lastReports = new LinkedHashMap<>();

    static String limitMessage(String text) {
        return DiagnosticsPayloadLimiter.text(text);
    }

    static boolean expectedConnectivityOrCancellation(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof CancellationException
                    || cause instanceof UnknownHostException || cause instanceof ConnectException
                    || cause instanceof NoRouteToHostException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    synchronized boolean shouldReport(String operation, Throwable error, long now) {
        if (error == null || expectedConnectivityOrCancellation(error)) return false;
        String site = "";
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().startsWith("com.nextgis.")) {
                site = frame.getClassName() + "." + frame.getMethodName();
                break;
            }
        }
        String key = operation + ":" + error.getClass().getName() + ":" + site;
        Long previous = lastReports.get(key);
        if (previous != null && now >= previous && now - previous < REPEAT_INTERVAL_MS) return false;
        lastReports.remove(key);
        lastReports.put(key, now);
        if (lastReports.size() > 32) lastReports.remove(lastReports.keySet().iterator().next());
        return true;
    }
}
