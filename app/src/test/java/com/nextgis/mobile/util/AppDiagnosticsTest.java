package com.nextgis.mobile.util;

import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.User;
import static org.junit.Assert.*;

public class AppDiagnosticsTest {
    @Test public void gpsRecoveryKeepsItsPhaseCorrelationAndBoundedHistoryAcrossReplay() {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("phase", "recovered"); snapshot.put("reason", "no_fix");
        snapshot.put("episode_id", "synthetic-episode"); snapshot.put("waiting_ms", 75_000L);
        java.util.List<String> history = new ArrayList<>();
        for (int i = 0; i < 24; i++) history.add("elapsedMs=" + i * 5000 + " used=0/29 callbackAgeMs=23076");
        snapshot.put("history", history);
        for (int i = 0; i < 40; i++) snapshot.put("synthetic_scalar_" + i, i);
        SentryEvent recovered = AppDiagnostics.gpsRecoveryEvent(snapshot);
        Request request = new Request();
        request.setData("x".repeat(100_000)); recovered.setRequest(request);
        assertEquals(io.sentry.SentryLevel.INFO, recovered.getLevel());
        assertEquals("recovered", recovered.getTag("gps_recovery_phase"));
        for (int replay = 0; replay < 2; replay++) {
            AppDiagnostics.sanitize(recovered);
            java.util.Map<?, ?> context = (java.util.Map<?, ?>) recovered.getContexts().get("gps_recovery");
            assertEquals("synthetic-episode", context.get("episode_id"));
            assertEquals(24, ((java.util.List<?>) context.get("history")).size());
            assertEquals(history.get(23), ((java.util.List<?>) context.get("history")).get(23));
        }
        snapshot.put("phase", "delayed");
        SentryEvent delayed = AppDiagnostics.gpsRecoveryEvent(snapshot);
        assertEquals(io.sentry.SentryLevel.WARNING, delayed.getLevel());
        assertEquals(delayed.getFingerprints(), recovered.getFingerprints());
    }
    @Test public void eventKeepsSuppliedContextPathsAndStack() {
        SentryEvent event = new SentryEvent();
        event.setUser(new User());
        event.setRequest(new Request());
        event.setExtra("feature_attributes", "private field value");
        event.getContexts().put("geometry", new HashMap<>());
        event.setTag("operation", "AZIMUTH");
        SentryStackFrame frame = new SentryStackFrame();
        frame.setModule("com.nextgis.mobile.stakeout.StakeoutForegroundService");
        frame.setFunction("onStartCommand");
        frame.setLineno(58);
        frame.setVars(new HashMap<>());
        frame.setAbsPath("/storage/emulated/0/customer");
        SentryException exception = new SentryException();
        exception.setType("SecurityException");
        exception.setValue("Starting FGS type location password=demo-secret");
        exception.setStacktrace(new SentryStackTrace(new ArrayList<>(List.of(frame))));
        event.setExceptions(new ArrayList<>(List.of(exception)));
        AppDiagnostics.sanitize(event);
        assertNotNull(event.getUser());
        assertNotNull(event.getRequest());
        assertEquals("private field value", event.getExtras().get("feature_attributes"));
        assertTrue(event.getContexts().containsKey("geometry"));
        assertEquals("AZIMUTH", event.getTag("operation"));
        assertEquals("SecurityException", event.getExceptions().get(0).getType());
        assertTrue(exception.getValue().contains("demo-secret"));
        assertEquals("onStartCommand", frame.getFunction());
        assertEquals(Integer.valueOf(58), frame.getLineno());
        assertNotNull(frame.getVars());
        assertEquals("/storage/emulated/0/customer", frame.getAbsPath());
    }

    @Test public void httpAndFormBreadcrumbContextSurvivesIncludingReplay() {
        Breadcrumb http = new Breadcrumb();
        http.setCategory("http");
        http.setData("url", "https://private.example/resource/803");
        Breadcrumb screen = new Breadcrumb();
        screen.setCategory("ui.lifecycle");
        screen.setData("screen", "ModifyAttributesActivity");
        screen.setData("field_value", "private person");
        SentryEvent event = new SentryEvent();
        event.setBreadcrumbs(new ArrayList<>(List.of(http, screen)));
        AppDiagnostics.sanitize(event);
        assertEquals(2, event.getBreadcrumbs().size());
        assertEquals("https://private.example/resource/803", http.getData("url"));
        assertEquals("ModifyAttributesActivity", screen.getData("screen"));
        assertEquals("private person", screen.getData("field_value"));
    }

    @Test public void largeRequestsAndStackVariablesCannotEraseGpsOrTheLatestOperation() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 100; i++) headers.put("synthetic-" + i, "v".repeat(1000));
        request.setHeaders(headers); event.setRequest(request);
        List<SentryStackFrame> frames = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            SentryStackFrame frame = new SentryStackFrame();
            frame.setVars(java.util.Map.of("large", "x".repeat(1000))); frames.add(frame);
        }
        SentryException exception = new SentryException();
        exception.setStacktrace(new SentryStackTrace(frames)); event.setExceptions(new ArrayList<>(List.of(exception)));
        event.getContexts().put("gps_recovery", java.util.Map.of("raw_fresh", false, "used_satellites", 0));
        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        for (int i = 0; i < 79; i++) {
            Breadcrumb old = new Breadcrumb(); old.setCategory("http");
            old.setData("url", "https://synthetic.example/" + "q".repeat(1000)); breadcrumbs.add(old);
        }
        Breadcrumb latest = new Breadcrumb(); latest.setCategory("lisa.operation");
        latest.setData("operation", "LOCATION"); latest.setData("phase", "READY"); breadcrumbs.add(latest);
        event.setBreadcrumbs(breadcrumbs);
        for (int replay = 0; replay < 2; replay++) {
            AppDiagnostics.sanitize(event);
            assertEquals(0, ((java.util.Map<?, ?>) event.getContexts().get("gps_recovery")).get("used_satellites"));
            assertEquals("LOCATION", latest.getData("operation"));
            assertEquals("READY", latest.getData("phase"));
            assertSame(latest, event.getBreadcrumbs().get(79));
        }
    }

    @Test public void replayPreservesOriginalIdentityAndDoesNotInventAnOldOperation() {
        AppDiagnostics.operation(AppDiagnostics.Operation.SYNC, AppDiagnostics.Phase.START);
        SentryEvent event = new SentryEvent(); event.setTimestamp(new java.util.Date(1));
        event.setRelease("original@1+2"); event.setTag("source_revision", "original-source");
        AppDiagnostics.prepareCachedForSend(event);
        assertEquals("original@1+2", event.getRelease());
        assertEquals("original-source", event.getTag("source_revision")); assertNull(event.getTag("operation"));
        assertEquals(com.nextgis.mobile.BuildConfig.SOURCE_REVISION,
                ((java.util.Map<?, ?>) event.getContexts().get("report_origin")).get("reporter_revision"));
    }

    @Test public void nativeOsAliasRemainsTypedForTheSdkSerializer() {
        SentryEvent event = new SentryEvent(); io.sentry.protocol.OperatingSystem os = new io.sentry.protocol.OperatingSystem();
        os.setName("Linux"); os.setKernelVersion("synthetic-kernel"); event.getContexts().put("os_linux", os);
        AppDiagnostics.sanitize(event);
        assertSame(os, event.getContexts().get("os_linux"));
    }

    @Test public void buildIdentityReachesScopeObserversUsedByNativeReporting() {
        io.sentry.android.core.SentryAndroidOptions options = new io.sentry.android.core.SentryAndroidOptions();
        options.setEnableScopeSync(true);
        java.util.Map<String, String> nativeTags = new HashMap<>();
        options.addScopeObserver((io.sentry.IScopeObserver) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{io.sentry.IScopeObserver.class}, (proxy, method, args) -> {
                    if ("setTag".equals(method.getName())) nativeTags.put((String) args[0], (String) args[1]);
                    return null;
                }));
        AppDiagnostics.applyBuildIdentity(new io.sentry.Scope(options));
        AppDiagnostics.applyOperation(new io.sentry.Scope(options), AppDiagnostics.Operation.MAP, AppDiagnostics.Phase.READY);
        assertEquals(com.nextgis.mobile.BuildConfig.FLAVOR, nativeTags.get("brand"));
        assertEquals(com.nextgis.mobile.BuildConfig.SOURCE_REVISION, nativeTags.get("source_revision"));
        assertEquals("3", nativeTags.get("diagnostics_contract"));
        assertEquals("MAP", nativeTags.get("operation"));
        assertEquals("READY", nativeTags.get("operation_phase"));
    }
}
