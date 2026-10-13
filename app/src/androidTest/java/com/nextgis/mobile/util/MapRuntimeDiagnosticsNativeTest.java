package com.nextgis.mobile.util;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.LocalVectorTileServer;
import com.nextgis.maplib.util.MapRuntimeDiagnostics;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Real observer thread and loopback backpressure; isolated Debug emulator only. */
@RunWith(AndroidJUnit4.class)
public class MapRuntimeDiagnosticsNativeTest {
    private static void guard() {
        assertTrue(InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName().endsWith(".debug"));
        assertTrue(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
    }

    @Test public void observerPersistsAQueryWhileTheMainThreadIsStillBlocked() throws Exception {
        guard();
        File journal = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                "map-runtime-diagnostics.json");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
        String target = "synthetic_blocked_query_" + java.util.UUID.randomUUID();
        new Handler(Looper.getMainLooper()).post(() -> {
            MapRuntimeDiagnostics.Trace query = MapRuntimeDiagnostics.beginQuery(target, 0);
            entered.countDown();
            try { release.await(4, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { query.finish(0, null); exited.countDown(); }
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            JSONObject captured = null;
            while (System.nanoTime() < deadline) {
                if (journal.isFile()) {
                    JSONObject data = new JSONObject(new String(Files.readAllBytes(journal.toPath()), StandardCharsets.UTF_8));
                    JSONObject query = data.optJSONObject("render_query");
                    if (query != null && target.equals(query.optString("target"))) { captured = data; break; }
                }
                Thread.sleep(30);
            }
            assertNotNull("Independent observer writes before the UI returns", captured);
            assertEquals(1, exited.getCount());
            assertFalse(captured.getJSONObject("render_query").getBoolean("finished"));
            assertTrue(captured.getJSONObject("render_query").getLong("elapsed_ms") >= 1000);
            assertTrue(captured.getJSONObject("render_query").getJSONArray("thread_stack").length() > 0);
            assertTrue(MapRuntimeDiagnostics.forEvent(captured.getJSONObject("render_query").getLong("observed_at_ms"))
                    .containsKey("render_query"));
            io.sentry.SentryEvent event = new io.sentry.SentryEvent();
            event.setTimestamp(new java.util.Date(captured.getJSONObject("render_query").getLong("observed_at_ms")));
            event = AppDiagnostics.prepareForSend(event, android.os.SystemClock.elapsedRealtime(), new DiagnosticsHttpPolicy());
            assertTrue("The bounded snapshot reaches the SDK event", event.getContexts().get("map_runtime") instanceof java.util.Map);
        } finally {
            release.countDown();
            assertTrue(exited.await(5, TimeUnit.SECONDS));
        }
    }

    @Test public void anActuallyFullQueueReturnsTheReasonAndCorrelationId() throws Exception {
        guard();
        LocalVectorTileServer server = LocalVectorTileServer.getInstance();
        assertTrue(server.ensureStarted());
        int port = new URI(server.getTileUrl(0).replace("{z}", "0").replace("{x}", "0").replace("{y}", "0")).getPort();
        List<Socket> sockets = new ArrayList<>();
        try {
            // Two workers block on headers; sixteen more requests fill the bounded queue.
            for (int i = 0; i < 19; i++) sockets.add(new Socket("127.0.0.1", port));
            Socket rejected = sockets.get(18);
            rejected.setSoTimeout(4000);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    rejected.getInputStream(), StandardCharsets.US_ASCII));
            assertTrue(reader.readLine().contains("503"));
            String reason = null, id = null, line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith(MapRuntimeDiagnostics.REASON_HEADER + ":")) reason = line.substring(line.indexOf(':') + 1).trim();
                if (line.startsWith(MapRuntimeDiagnostics.REQUEST_HEADER + ":")) id = line.substring(line.indexOf(':') + 1).trim();
            }
            assertEquals("queue_full", reason);
            assertNotNull(id); assertTrue(id.matches("[0-9]+"));
        } finally {
            for (Socket socket : sockets) socket.close();
            server.clearLayers();
        }
    }
}
