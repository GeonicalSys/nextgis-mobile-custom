package com.nextgis.mobile.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import io.sentry.DataCategory;
import io.sentry.Hint;
import io.sentry.RequestDetails;
import io.sentry.SentryEnvelope;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.cache.IEnvelopeCache;
import io.sentry.hints.Cached;
import io.sentry.hints.DiskFlushNotification;
import io.sentry.hints.Enqueable;
import io.sentry.hints.Retryable;
import io.sentry.hints.SubmissionResult;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import io.sentry.util.HintUtils;

/**
 * Sentry transport extension using its serializer, envelope cache, hints and rate limiter.
 * SDK 8.37.1's default HTTP transport discards every response >=400, including temporary 5xx.
 * Persist before enqueue, retain retryable failures, and let the SDK's disk senders replay them.
 */
final class DiagnosticsTransport implements ITransport {
    private final SentryOptions options;
    private final RequestDetails request;
    private final IEnvelopeCache cache;
    private final RateLimiter rateLimiter;
    private final ThreadPoolExecutor executor;

    DiagnosticsTransport(SentryOptions options, RequestDetails request) {
        this.options = options;
        this.request = request;
        this.cache = options.getEnvelopeDiskCache();
        this.rateLimiter = new RateLimiter(options);
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(options.getMaxQueueSize()), runnable -> {
                    Thread thread = new Thread(runnable, "diagnostic-http");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override public void send(SentryEnvelope envelope, Hint hint) {
        boolean cached = HintUtils.hasType(hint, Cached.class);
        if (!cached) {
            // This is an error path, not a per-frame/GPS path. SDK envelope persistence is bounded
            // and completes before capture returns, even if the process dies before HTTP starts.
            boolean stored = cache.storeEnvelope(envelope, hint);
            if (stored) HintUtils.runIfHasType(hint, DiskFlushNotification.class,
                    notification -> {
                        if (notification.isFlushable(envelope.getHeader().getEventId())) notification.markFlushed();
                    });
        }
        try {
            executor.execute(() -> deliver(envelope, hint, cached));
            HintUtils.runIfHasType(hint, Enqueable.class, Enqueable::markEnqueued);
        } catch (RejectedExecutionException fullOrClosed) {
            complete(hint, false, true); // The disk envelope remains available to the next pass.
        }
    }

    private void deliver(SentryEnvelope envelope, Hint hint, boolean cached) {
        if (!options.getTransportGate().isConnected()
                || rateLimiter.isActiveForCategory(DataCategory.All)
                || rateLimiter.isActiveForCategory(DataCategory.Error)) {
            complete(hint, false, true);
            return;
        }
        HttpURLConnection connection = null;
        boolean success = false;
        boolean retry = true;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream compressed = new GZIPOutputStream(bytes)) {
                options.getSerializer().serialize(envelope, compressed);
            }
            connection = (HttpURLConnection) request.getUrl().openConnection();
            connection.setConnectTimeout(options.getConnectionTimeoutMillis());
            connection.setReadTimeout(options.getReadTimeoutMillis());
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            connection.setRequestProperty("Content-Type", "application/x-sentry-envelope");
            connection.setRequestProperty("Content-Encoding", "gzip");
            byte[] body = bytes.toByteArray();
            connection.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            rateLimiter.updateRetryAfterLimits(connection.getHeaderField("X-Sentry-Rate-Limits"),
                    connection.getHeaderField("Retry-After"), status);
            success = status >= 200 && status < 300;
            retry = !success && isRetryableStatus(status);
            if (!success) options.getLogger().log(SentryLevel.WARNING,
                    "Diagnostic receiver returned HTTP %d; retry=%s", status, retry);
            if (!success) android.util.Log.w("AppDiagnostics",
                    "Diagnostic receiver HTTP " + status + "; retry=" + retry);
        } catch (IOException temporaryNetworkFailure) {
            // Lost acknowledgments may result in the same event UUID being sent again. GlitchTip
            // deduplicates that UUID; never manufacture a new event during cache replay.
            options.getLogger().log(SentryLevel.INFO, "Diagnostic network delivery deferred");
        } catch (Exception serializationFailure) {
            retry = false; // Retrying a malformed envelope forever cannot repair its serialization.
            options.getLogger().log(SentryLevel.ERROR, "Diagnostic envelope serialization failed");
        } finally {
            if (connection != null) connection.disconnect();
            // Cached/NDK outbox files are acknowledged/deleted by their SDK directory processor.
            if (!cached && (success || !retry)) cache.discard(envelope);
            complete(hint, success, retry);
        }
    }

    static boolean isRetryableStatus(int status) {
        return status >= 500 || status < 200 || (status >= 300 && status < 400) || status == 401 || status == 403
                || status == 408 || status == 425 || status == 429;
    }

    private static void complete(Hint hint, boolean success, boolean retry) {
        HintUtils.runIfHasType(hint, Retryable.class, value -> value.setRetry(retry));
        HintUtils.runIfHasType(hint, SubmissionResult.class, value -> value.setResult(success));
    }

    @Override public void flush(long timeoutMillis) {
        if (executor.isShutdown()) return;
        try { executor.submit(() -> { }).get(timeoutMillis, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (Exception ignored) { }
    }

    @Override public RateLimiter getRateLimiter() { return rateLimiter; }
    @Override public boolean isHealthy() { return !executor.isShutdown() && executor.getQueue().remainingCapacity() > 0; }
    @Override public void close() throws IOException { close(false); }
    @Override public void close(boolean force) throws IOException {
        executor.shutdown();
        try {
            if (force || !executor.awaitTermination(options.getShutdownTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow(); // Every queued normal event was already persisted.
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        rateLimiter.close();
    }
}
