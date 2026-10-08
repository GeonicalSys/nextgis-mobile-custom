package com.nextgis.mobile.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import io.sentry.IConnectionStatusProvider;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Multi-process phases are intentionally launched separately; the fatal phase kills its own app. */
@RunWith(AndroidJUnit4.class)
public class DiagnosticsDeliveryTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final SentryOptions options = Sentry.getCurrentScopes().getOptions();

    @Test public void queueHandledWhileOffline() throws Exception {
        awaitDisconnected();
        AppDiagnostics.operation(AppDiagnostics.Operation.AZIMUTH, AppDiagnostics.Phase.START);
        AppDiagnostics.report(AppDiagnostics.Operation.AZIMUTH,
                new IllegalStateException("Synthetic handled mobile diagnostic password=probe-secret "
                        + "https://private.example/resource/803"));
        Sentry.flush(5_000L);
        awaitPending(1);
        String envelopes = cachedText();
        assertTrue(envelopes.contains("Synthetic handled mobile diagnostic"));
        assertTrue(envelopes.contains("AZIMUTH"));
        assertTrue(envelopes.contains("diagnostics_contract"));
        assertFalse(envelopes.contains("probe-secret"));
        assertFalse(envelopes.contains("private.example"));
        assertTrue(Thread.getDefaultUncaughtExceptionHandler()
                instanceof com.nextgis.maplibui.util.HyperLogCrashHandler);
    }

    /** Expected instrumentation process-crash result, not a successful JUnit completion. */
    @Test public void crashWhileOffline() throws Exception {
        awaitDisconnected();
        Sentry.setTag("verification_case", "offline_fatal");
        AppDiagnostics.operation(AppDiagnostics.Operation.AZIMUTH, AppDiagnostics.Phase.START);
        new Handler(Looper.getMainLooper()).post(() -> {
            throw new IllegalStateException("Synthetic offline fatal mobile diagnostic");
        });
        SystemClock.sleep(30_000L);
        fail("Fatal exceptions must still delegate to Android; the process should have terminated");
    }

    @Test public void reportsSurviveOfflineRestart() throws Exception {
        awaitDisconnected();
        awaitPending(2);
        String envelopes = cachedText();
        assertTrue(envelopes.contains("Synthetic handled mobile diagnostic"));
        assertTrue(envelopes.contains("Synthetic offline fatal mobile diagnostic"));
        assertTrue(envelopes.contains("uncaughtException"));
    }

    @Test public void deliverWhenNetworkReturns() throws Exception {
        // The host enables Wi-Fi during this phase, without restarting this process.
        long deadline = SystemClock.elapsedRealtime() + 90_000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (options.getConnectionStatusProvider().getConnectionStatus()
                    != IConnectionStatusProvider.ConnectionStatus.DISCONNECTED
                    && !AppDiagnostics.hasPendingReports(options)) return;
            SystemClock.sleep(250L);
        }
        fail("SDK connection observer did not deliver the persisted reports");
    }

    @Test public void workerRetriesTemporaryServerFailureWithoutNetworkChange() throws Exception {
        assertTrue("Use the local synthetic endpoint for this test", options.getDsn().contains("127.0.0.1:18539"));
        long connectedDeadline = SystemClock.elapsedRealtime() + 30_000L;
        while (options.getConnectionStatusProvider().getConnectionStatus()
                == IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) {
            if (SystemClock.elapsedRealtime() >= connectedDeadline) fail("Enable emulator networking for the HTTP failure probe");
            SystemClock.sleep(250L);
        }
        WorkManager manager = WorkManager.getInstance(context);
        manager.cancelUniqueWork(AppDiagnostics.RETRY_WORK).getResult().get(10, TimeUnit.SECONDS);
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        AtomicInteger status = new AtomicInteger(503);
        AtomicInteger requests = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(18539, 8, InetAddress.getByName("127.0.0.1"))) {
            Thread responder = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(5_000);
                        ByteArrayOutputStream header = new ByteArrayOutputStream();
                        int tail = 0;
                        while (header.size() < 16384) {
                            int value = socket.getInputStream().read();
                            if (value < 0) break;
                            header.write(value);
                            tail = (tail << 8) | value;
                            if (tail == 0x0d0a0d0a) break;
                        }
                        String headers = header.toString("US-ASCII");
                        int length = 0;
                        for (String line : headers.split("\r\n")) {
                            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                                length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                            }
                        }
                        for (int i = 0; i < length; i++) if (socket.getInputStream().read() < 0) break;
                        requests.incrementAndGet();
                        String response = "HTTP/1.1 " + status.get() + " Test\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}";
                        socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    } catch (Exception ignored) { }
                }
            }, "diagnostics-test-http");
            responder.setDaemon(true);
            responder.start();
            AppDiagnostics.report(AppDiagnostics.Operation.GEOMETRY_EDIT,
                    new IllegalStateException("Synthetic temporary receiver failure"));
            Sentry.flush(7_000L);
            awaitPending(1);
            assertTrue("Initial request must reach the temporarily unavailable receiver", requests.get() >= 1);
            status.set(200);
            OneTimeWorkRequest retry = new OneTimeWorkRequest.Builder(DiagnosticsRetryWorker.class).build();
            manager.enqueue(retry).getResult().get(10, TimeUnit.SECONDS);
            WorkInfo info = null;
            long deadline = SystemClock.elapsedRealtime() + 60_000L;
            while (SystemClock.elapsedRealtime() < deadline) {
                info = manager.getWorkInfoById(retry.getId()).get(5, TimeUnit.SECONDS);
                if (info != null && info.getState().isFinished()) break;
                SystemClock.sleep(250);
            }
            assertNotNull(info);
            assertEquals(WorkInfo.State.SUCCEEDED, info.getState());
            assertFalse(AppDiagnostics.hasPendingReports(options));
            assertTrue(requests.get() >= 2);
            assertSame("Retry must not reinstall or swallow crash handlers", handler,
                    Thread.getDefaultUncaughtExceptionHandler());
        }
    }

    private void awaitDisconnected() {
        long deadline = SystemClock.elapsedRealtime() + 30_000L;
        while (options.getConnectionStatusProvider().getConnectionStatus()
                != IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Disable both emulator Wi-Fi and mobile data first");
            SystemClock.sleep(250L);
        }
    }

    private void awaitPending(int minimum) {
        long deadline = SystemClock.elapsedRealtime() + 15_000L;
        while (envelopes().length < minimum) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Expected persisted diagnostic envelopes: " + minimum);
            SystemClock.sleep(100L);
        }
    }

    private File[] envelopes() {
        File[] files = new File(options.getCacheDirPath()).listFiles(file -> file.getName().endsWith(".envelope"));
        return files == null ? new File[0] : files;
    }

    private String cachedText() throws Exception {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (File file : envelopes()) try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) all.write(buffer, 0, count);
        }
        return all.toString("UTF-8");
    }
}
