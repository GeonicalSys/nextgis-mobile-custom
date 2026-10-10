package com.nextgis.mobile.util;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;

/** Bounds automatically captured HTTP 5xx bursts while retaining the first request. */
final class DiagnosticsHttpPolicy {
    static final long REPEAT_WINDOW_MS = 10 * 60 * 1000L;
    private static final int MAX_KEYS = 64;
    private static final Pattern STATUS = Pattern.compile("^HTTP Client Error with status code: (5[0-9]{2})$");
    private final LinkedHashMap<String, Long> sent = new LinkedHashMap<>();

    synchronized boolean shouldReport(SentryEvent event, long now) {
        if (event.getLevel() == SentryLevel.FATAL || event.getExceptions() == null) return true;
        for (SentryException exception : event.getExceptions()) {
            if (exception.getMechanism() != null
                    && Boolean.FALSE.equals(exception.getMechanism().isHandled())) return true;
        }
        for (SentryException exception : event.getExceptions()) {
            if (!"SentryHttpClientException".equals(exception.getType())
                    || !"io.sentry.exception".equals(exception.getModule())
                    || exception.getMechanism() == null
                    || !"SentryOkHttpInterceptor".equals(exception.getMechanism().getType())) continue;
            Matcher status = STATUS.matcher(exception.getValue() == null ? "" : exception.getValue());
            if (!status.matches()) continue;
            Request request = event.getRequest();
            String method = request == null ? "UNKNOWN" : safeMethod(request.getMethod());
            String source = "unknown";
            String endpoint = "unknown";
            if (request != null && request.getUrl() != null) {
                try {
                    URI uri = new URI(request.getUrl());
                    String host = uri.getHost();
                    if (host != null) {
                        host = host.toLowerCase(Locale.ROOT);
                        source = isLoopback(host) ? "local" : "remote";
                        // The limiter key excludes query/userinfo; the report keeps the original request.
                        // Slippy tile/resource numbers share one key; distinct endpoint names do not.
                        String path = uri.getPath() == null ? "" : uri.getPath();
                        path = path.replaceAll("(?<=/)[0-9]+(?=/|\\.|$)", "{number}");
                        endpoint = hash(uri.getScheme() + "://" + host + ":" + uri.getPort() + path);
                    }
                } catch (Exception ignored) { }
            }
            event.setTag("http_status", status.group(1));
            event.setTag("http_source", source);
            event.setTag("http_method", method);
            String key = status.group(1) + ":" + method + ":" + endpoint + ":"
                    + event.getTag("operation") + ":" + callSite(exception);
            Long previous = sent.get(key);
            if (previous != null && now >= previous && now - previous < REPEAT_WINDOW_MS) return false;
            sent.remove(key);
            sent.put(key, now);
            while (sent.size() > MAX_KEYS) sent.remove(sent.keySet().iterator().next());
            return true;
        }
        return true;
    }

    private static String safeMethod(String method) {
        if (method == null) return "UNKNOWN";
        String upper = method.toUpperCase(Locale.ROOT);
        switch (upper) {
            case "GET": case "HEAD": case "POST": case "PUT": case "PATCH":
            case "DELETE": case "OPTIONS": case "CONNECT": case "TRACE": return upper;
            default: return "UNKNOWN";
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) || "::1".equals(host) || "[::1]".equals(host)
                || host.matches("127\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
    }

    private static String callSite(SentryException exception) {
        if (exception.getStacktrace() != null && exception.getStacktrace().getFrames() != null) {
            for (SentryStackFrame frame : exception.getStacktrace().getFrames()) {
                if (frame.getModule() != null && frame.getModule().startsWith("com.nextgis.")) {
                    return frame.getModule() + ":" + frame.getFunction() + ":" + frame.getLineno();
                }
            }
        }
        return "sdk";
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
