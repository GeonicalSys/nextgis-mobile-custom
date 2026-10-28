package com.nextgis.mobile.util;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, manifest = Config.NONE, application = Application.class)
public class DiagnosticsBackgroundLocationStartTest {
    private final Application app = org.robolectric.RuntimeEnvironment.getApplication();
    private static final Set<String> STATE_KEYS = Set.of("visible_activities", "resumed_activities",
            "fine_permission", "coarse_permission", "background_permission", "process_importance");

    @After public void releaseOnlyTestObservation() {
        DiagnosticsBackgroundLocationStart.lifecycle(-1, -1);
        Sentry.close();
    }

    @Test public void delayedPromotionAfterActivityStopKeepsRequestAndFailureStatesSeparate() {
        org.robolectric.Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        org.robolectric.Shadows.shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        DiagnosticsBackgroundLocationStart.lifecycle(1, 1);
        DiagnosticsBackgroundLocationStart.Request pending = DiagnosticsBackgroundLocationStart.request(app, 1_000);
        // The existing controller reports onPause and onStop before the pending service is promoted.
        DiagnosticsBackgroundLocationStart.lifecycle(1, 0);
        DiagnosticsBackgroundLocationStart.lifecycle(0, 0);
        Map<String, Object> witness = DiagnosticsBackgroundLocationStart.failure(app, pending, "start_foreground", 5_699);
        assertEquals(Set.of("schema_version", "stage", "request_age_ms", "request", "failure"), witness.keySet());
        assertEquals(4_699L, witness.get("request_age_ms"));
        assertEquals("start_foreground", witness.get("stage"));
        assertEquals(1, state(witness, "request").get("visible_activities"));
        assertEquals(1, state(witness, "request").get("resumed_activities"));
        assertEquals(0, state(witness, "failure").get("visible_activities"));
        assertEquals(0, state(witness, "failure").get("resumed_activities"));
        assertEquals("granted", state(witness, "request").get("fine_permission"));
        assertEquals("denied", state(witness, "failure").get("background_permission"));
        assertEquals(STATE_KEYS, state(witness, "request").keySet());
        assertEquals(STATE_KEYS, state(witness, "failure").keySet());
    }

    @Test public void permissionChangeBetweenRequestAndPromotionDoesNotRewriteTheRequest() {
        org.robolectric.Shadows.shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        org.robolectric.Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        DiagnosticsBackgroundLocationStart.Request pending = DiagnosticsBackgroundLocationStart.request(app, 50);
        org.robolectric.Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        Map<String, Object> witness = DiagnosticsBackgroundLocationStart.failure(app, pending, "launch", 80);
        assertEquals("denied", state(witness, "request").get("fine_permission"));
        assertEquals("granted", state(witness, "request").get("coarse_permission"));
        assertEquals("denied", state(witness, "request").get("background_permission"));
        assertEquals("granted", state(witness, "failure").get("fine_permission"));
        assertEquals("granted", state(witness, "failure").get("background_permission"));
        org.robolectric.Shadows.shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        Map<String, Object> none = DiagnosticsBackgroundLocationStart.failure(app, pending, "launch", 90);
        assertEquals("denied", state(none, "failure").get("coarse_permission"));
        assertEquals("denied", state(none, "failure").get("fine_permission"));
        assertEquals("denied", state(none, "failure").get("background_permission"));
    }

    @Test @Config(sdk = 28) public void oldAndroidDoesNotInventADeniedBackgroundPermission() {
        Map<String, Object> witness = DiagnosticsBackgroundLocationStart.failure(app, null, "notification", 1);
        assertEquals("not_required", state(witness, "failure").get("background_permission"));
    }

    @Test public void unavailableContextStageAndClockAreExplicitAndBounded() {
        DiagnosticsBackgroundLocationStart.lifecycle(-1, -1);
        DiagnosticsBackgroundLocationStart.Request unknown = DiagnosticsBackgroundLocationStart.request(null, 100);
        Map<String, Object> witness = DiagnosticsBackgroundLocationStart.failure(null, unknown, "private-value".repeat(1000), 90);
        assertEquals("unknown", witness.get("stage")); assertEquals("unknown", witness.get("request_age_ms"));
        for (Object value : state(witness, "failure").values()) assertEquals("unknown", value);
        assertEquals("unknown", DiagnosticsBackgroundLocationStart.failure(null, null, null, 110).get("request_age_ms"));
    }

    @Test public void oneUnavailablePermissionDoesNotEraseOtherObservations() {
        org.robolectric.Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        Context unavailableFine = new ContextWrapper(app) {
            @Override public int checkSelfPermission(String permission) {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)) throw new SecurityException("Technical unavailable permission");
                return super.checkSelfPermission(permission);
            }
        };
        DiagnosticsBackgroundLocationStart.lifecycle(2, 1);
        Map<String, Object> failure = state(DiagnosticsBackgroundLocationStart.failure(unavailableFine, null, "launch", 0), "failure");
        assertEquals("unknown", failure.get("fine_permission"));
        assertEquals("granted", failure.get("coarse_permission"));
        assertEquals(2, failure.get("visible_activities")); assertEquals(1, failure.get("resumed_activities"));
        assertTrue(failure.get("process_importance") instanceof Integer);
    }

    @Test public void actualSanitizerRetainsTheSmallWitnessDespiteLargeOtherPayloadAndReplay() {
        DiagnosticsBackgroundLocationStart.lifecycle(1, 1);
        DiagnosticsBackgroundLocationStart.Request pending = DiagnosticsBackgroundLocationStart.request(app, 10);
        DiagnosticsBackgroundLocationStart.lifecycle(0, 0);
        Map<String, Object> witness = DiagnosticsBackgroundLocationStart.failure(app, pending, "start_foreground", 1_010);
        SentryEvent event = new SentryEvent(new SecurityException("Technical delayed promotion"));
        event.getContexts().put("background_gps_start", witness);
        Map<String, Object> noise = new LinkedHashMap<>();
        for (int i = 0; i < 200; i++) noise.put("field" + i, "x".repeat(2000));
        event.getContexts().put("map_runtime", noise);
        event.setExtra("technical_noise", noise);
        Request request = new Request(); request.setData(noise); event.setRequest(request);
        for (int replay = 0; replay < 2; replay++) {
            AppDiagnostics.sanitize(event);
            assertEquals(witness, event.getContexts().get("background_gps_start"));
        }
    }

    @Test public void scopedDeliveryKeepsTheOriginalHandledExceptionAndDoesNotLeakTheWitness() {
        AtomicReference<SentryEvent> captured = new AtomicReference<>();
        Sentry.init(options -> {
            options.setDsn("https://technical@example.invalid/1");
            options.setEnableExternalConfiguration(false);
            options.setEnableUncaughtExceptionHandler(false);
            options.setBeforeSend((event, hint) -> { captured.set(AppDiagnostics.sanitize(event)); return null; });
        });
        DiagnosticsBackgroundLocationStart.lifecycle(0, 0);
        SecurityException original = new SecurityException("Technical background GPS start rejection");
        DiagnosticsBackgroundLocationStart.report(app, null, "start_foreground", original);
        assertNotNull(captured.get()); assertSame(original, captured.get().getThrowable());
        assertEquals("handled", captured.get().getTag("failure_kind"));
        assertEquals("LOCATION", captured.get().getTag("operation"));
        assertEquals("start_foreground", ((Map<?, ?>) captured.get().getContexts().get("background_gps_start")).get("stage"));
        AtomicReference<Object> remaining = new AtomicReference<>();
        Sentry.configureScope(scope -> remaining.set(scope.getContexts().get("background_gps_start")));
        assertNull(remaining.get());
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> state(Map<String, Object> witness, String key) {
        return (Map<String, Object>) witness.get(key);
    }
}
