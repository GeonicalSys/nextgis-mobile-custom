package com.nextgis.mobile.util;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplibui.util.HyperLogCrashHandler;
import com.nextgis.maplib.util.LayerFormHashUtil;
import com.nextgis.mobile.BuildConfig;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

/** Separate invocations seed a synthetic backlog and then cold-start it; emulator only. */
@RunWith(AndroidJUnit4.class)
public class DiagnosticsNoiseTest {
    private Context context() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
        assertTrue(context.getPackageName().endsWith(".debug"));
        assertTrue(context.getApplicationContext() instanceof DiagnosticsTestApplication);
        return context;
    }

    @Test public void seedObsoleteEndpointAndLargeLocalBacklog() throws Exception {
        Context context = context();
        String message = "Synthetic startup backlog " + "x".repeat(2000);
        for (int i = 0; i < 6000; i++) HyperLog.i("StartupProbe", message);
        Field executor = HyperLog.class.getDeclaredField("executorService");
        executor.setAccessible(true);
        ((ExecutorService) executor.get(null)).submit(() -> {}).get(60, TimeUnit.SECONDS);
        assertTrue(HyperLog.getDeviceLogsCount() >= 6000);
        assertTrue(context.getSharedPreferences("HyperLog", 0).edit()
                .putString("URL", "https://127.0.0.1/nextgis-hyperlog-no-remote/").commit());
    }

    @Test public void coldStartRetainsBacklogAndDelegatingHandlerWithoutRemoteUpload() {
        Context context = context();
        assertNull(HyperLog.getURL());
        assertFalse(context.getSharedPreferences("HyperLog", 0).contains("URL"));
        assertTrue(HyperLog.getDeviceLogsCount() >= 6000);
        // The second argument is a one-based batch number, not a row limit.
        assertTrue(HyperLog.getDeviceLogsAsStringList(false, 1).stream()
                .anyMatch(line -> line.contains(BuildConfig.VERSION_NAME)));
        assertTrue(Thread.getDefaultUncaughtExceptionHandler() instanceof HyperLogCrashHandler);
    }

    @Test public void sdkBeforeSendPersistsOneHttpEventPerBurstWithSafeSource() throws Exception {
        context();
        AppDiagnostics.operation(AppDiagnostics.Operation.MAP, AppDiagnostics.Phase.START);
        // The explicit test DSN points to an unavailable loopback receiver: inspect SDK cache.
        for (int i = 0; i < 28; i++) Sentry.captureEvent(http("http://127.0.0.1:18901/tiles/12/43/" + i + ".mvt"));
        Sentry.captureEvent(http("https://private.example/tiles/12/43/1.mvt?token=probe-secret"));
        Sentry.flush(7_000L);
        File cache = new File(Sentry.getCurrentScopes().getOptions().getCacheDirPath());
        int count = 0;
        boolean local = false, remote = false;
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        do {
            count = 0;
            File[] files = cache.listFiles(file -> file.getName().endsWith(".envelope"));
            if (files != null) for (File file : files) {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (!text.contains("SentryHttpClientException")) continue;
                count++;
                local |= text.contains("\"http_source\":\"local\"");
                remote |= text.contains("\"http_source\":\"remote\"");
                assertFalse(text.contains("private.example"));
                assertFalse(text.contains("probe-secret"));
                assertFalse(text.contains("/tiles/"));
            }
            if (count >= 2) break;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertEquals(2, count);
        assertTrue(local && remote);
    }

    @Test public void androidJsonHashKeepsSnapshotDownloadAndStagedFormConsistent() throws Exception {
        Context context = context();
        String first = "[{\"type\":\"text_edit\",\"lisa_id\":\"name\","
                + "\"attributes\":{\"field\":\"name\",\"last\":false}}]";
        String reordered = "[ {\"attributes\":{\"last\":false,\"field\":\"name\"},"
                + "\"lisa_id\":\"name\",\"type\":\"text_edit\"} ]";
        String meta = "{\"name\":\"Synthetic\",\"lisa_form_rules\":{\"version\":2,\"rules\":[]}}";
        String remoteMeta = "{\"ngw_connection\":{\"url\":\"ignored\"},"
                + "\"lisa_form_rules\":{\"rules\":[],\"version\":2},\"name\":\"Synthetic\"}";
        String snapshot = hash(first, remoteMeta);
        assertEquals(snapshot, hash(reordered, meta));
        assertNotEquals(snapshot, hash(reordered.replace("false", "true"), meta));
        assertNotEquals(snapshot, hash(reordered, meta.replace("\"version\":2", "\"version\":1")));
        File stage = new File(context.getCacheDir(), "synthetic-form-hash");
        assertTrue(stage.exists() || stage.mkdirs());
        File formFile = new File(stage, "form.json");
        File metaFile = new File(stage, "ngfp_meta.json");
        Files.write(formFile.toPath(), reordered.getBytes(StandardCharsets.UTF_8));
        Files.write(metaFile.toPath(), meta.getBytes(StandardCharsets.UTF_8));
        assertEquals(snapshot, LayerFormHashUtil.md5NgfpFiles(formFile, metaFile));
    }

    private static String hash(String form, String meta) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("form.json"));
            zip.write(form.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("meta.json"));
            zip.write(meta.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return LayerFormHashUtil.md5NgfpZip(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static SentryEvent http(String url) {
        SentryEvent event = new SentryEvent();
        event.setLevel(SentryLevel.ERROR);
        Request request = new Request();
        request.setMethod("GET");
        request.setUrl(url);
        event.setRequest(request);
        SentryException exception = new SentryException();
        exception.setType("SentryHttpClientException");
        exception.setModule("io.sentry.exception");
        exception.setValue("HTTP Client Error with status code: 503");
        Mechanism mechanism = new Mechanism();
        mechanism.setType("SentryOkHttpInterceptor");
        mechanism.setHandled(true);
        exception.setMechanism(mechanism);
        event.setExceptions(new ArrayList<>(List.of(exception)));
        return event;
    }
}
