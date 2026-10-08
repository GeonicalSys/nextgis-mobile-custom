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
import java.util.regex.Pattern;

/** Android-independent limits and redaction; no access to accounts, GIS data or log files. */
final class DiagnosticsPolicy {
    static final int CACHE_ITEMS = 256;
    static final int BREADCRUMBS = 80;
    private static final long REPEAT_INTERVAL_MS = 10 * 60_000L;
    private static final Pattern ADDRESS = Pattern.compile(
            "(?i)\\b(?:https?|content|file|postgres(?:ql)?|ftp)://[^\\s<>\\\"']+");
    private static final Pattern EMAIL = Pattern.compile(
            "[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(authorization|password|passwd|token|secret|api[_-]?key|cookie)"
                    + "\\s*[:=]\\s*(?:Bearer\\s+|Basic\\s+)?(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]|/(?:data|storage|sdcard|mnt|home)/)[^\\r\\n<>\\\"]*");
    private static final Pattern SQL = Pattern.compile(
            "(?is)(?:while compiling:|\\b(?:sql|query|statement)\\s*[:=]|"
                    + "\\b(?:SELECT\\s+[^;\\r\\n]+?\\s+FROM|INSERT INTO|UPDATE\\s+[\\w.\"`]+\\s+SET|DELETE FROM)\\s).*");
    private static final Pattern WKT = Pattern.compile(
            "(?is)\\b(?:SRID=\\d+;\\s*)?(?:POINT|MULTIPOINT|LINESTRING|MULTILINESTRING|POLYGON|MULTIPOLYGON|GEOMETRYCOLLECTION)"
                    + "(?:\\s+(?:Z|M|ZM))?\\s*\\(.*");
    private static final Pattern NMEA = Pattern.compile("\\$[A-Z]{2}[A-Z0-9]{3},[^\\r\\n]*");
    private static final Pattern PRECISE_NUMBER = Pattern.compile("(?<![\\w.])-?\\d{1,3}\\.\\d{5,}(?!\\d)");
    private final LinkedHashMap<String, Long> lastReports = new LinkedHashMap<>();

    static String redact(String text) {
        if (text == null) return null;
        // Bound work as well as the serialized event. Exception messages may contain huge SQL/WKT.
        String value = text.substring(0, Math.min(text.length(), 4096));
        value = SQL.matcher(value).replaceAll("[database statement omitted]");
        value = WKT.matcher(value).replaceAll("[geometry omitted]");
        value = CREDENTIAL.matcher(value).replaceAll("$1=[redacted]");
        value = ADDRESS.matcher(value).replaceAll("[address]");
        value = EMAIL.matcher(value).replaceAll("[email]");
        value = PATH.matcher(value).replaceAll("[path]");
        value = NMEA.matcher(value).replaceAll("[receiver data]");
        value = PRECISE_NUMBER.matcher(value).replaceAll("[number]");
        return value.substring(0, Math.min(value.length(), 1024));
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
