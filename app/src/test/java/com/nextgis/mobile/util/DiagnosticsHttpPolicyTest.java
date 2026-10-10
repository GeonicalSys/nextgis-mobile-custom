package com.nextgis.mobile.util;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import static org.junit.Assert.*;

public class DiagnosticsHttpPolicyTest {
    @Test public void tileBurstKeepsFirstAndRenewsAfterTenMinutes() {
        DiagnosticsHttpPolicy policy = new DiagnosticsHttpPolicy();
        assertTrue(policy.shouldReport(http(503, "https://tiles.example/12/43/27.mvt?token=first"), 1));
        for (int i = 0; i < 28; i++) {
            assertFalse(policy.shouldReport(http(503, "https://tiles.example/12/44/" + i + ".mvt?token=other"), 2 + i));
        }
        assertTrue(policy.shouldReport(http(503, "https://tiles.example/12/43/27.mvt"),
                1 + DiagnosticsHttpPolicy.REPEAT_WINDOW_MS));
    }

    @Test public void separateStatusesOriginsEndpointsMethodsAndOperationsSurvive() {
        DiagnosticsHttpPolicy policy = new DiagnosticsHttpPolicy();
        assertTrue(policy.shouldReport(http(503, "http://127.0.0.1:1234/tiles/1/2/3"), 1));
        assertTrue(policy.shouldReport(http(503, "https://tiles.example/tiles/1/2/3"), 2));
        assertTrue(policy.shouldReport(http(502, "https://tiles.example/tiles/1/2/3"), 3));
        assertTrue(policy.shouldReport(http(503, "https://tiles.example/api/resource/1"), 4));
        SentryEvent post = http(503, "https://tiles.example/tiles/1/2/3");
        post.getRequest().setMethod("POST");
        assertTrue(policy.shouldReport(post, 5));
        SentryEvent sync = http(503, "https://tiles.example/tiles/1/2/3");
        sync.setTag("operation", "SYNC");
        assertTrue(policy.shouldReport(sync, 6));
    }

    @Test public void fatalUnhandledAndOtherExceptionsAlwaysSurvive() {
        DiagnosticsHttpPolicy policy = new DiagnosticsHttpPolicy();
        assertTrue(policy.shouldReport(http(503, "http://localhost/tiles/1"), 1));
        SentryEvent fatal = http(503, "http://localhost/tiles/1");
        fatal.setLevel(SentryLevel.FATAL);
        assertTrue(policy.shouldReport(fatal, 2));
        SentryEvent unhandled = http(503, "http://localhost/tiles/1");
        unhandled.getExceptions().get(0).getMechanism().setHandled(false);
        assertTrue(policy.shouldReport(unhandled, 3));
        SentryEvent different = http(503, "http://localhost/tiles/1");
        different.getExceptions().get(0).setType("IllegalStateException");
        assertTrue(policy.shouldReport(different, 4));
        SentryEvent nonSdk = http(503, "http://localhost/tiles/1");
        nonSdk.getExceptions().get(0).getMechanism().setType("AppDiagnostics.report");
        assertTrue(policy.shouldReport(nonSdk, 5));
    }

    @Test public void limitingKeepsAddressStatusAndSource() {
        for (String url : new String[]{"http://localhost/tiles/1", "http://[::1]/tiles/1",
                "http://127.0.0.1:1853/tiles/1", "https://private.example/resource/803?password=secret"}) {
            DiagnosticsHttpPolicy policy = new DiagnosticsHttpPolicy();
            SentryEvent event = http(503, url);
            assertSame(event, AppDiagnostics.prepareForSend(event, 1, policy));
            assertEquals(url, event.getRequest().getUrl());
            assertEquals("503", event.getTag("http_status"));
            assertEquals(url.contains("private") ? "remote" : "local", event.getTag("http_source"));
            assertEquals("GET", event.getTag("http_method"));
            assertEquals(4, event.getTags().size());
            assertNull(AppDiagnostics.prepareForSend(http(503, url), 2, policy));
        }
    }

    @Test public void absentRequestAndBoundedKeyEvictionAreSafe() {
        DiagnosticsHttpPolicy policy = new DiagnosticsHttpPolicy();
        SentryEvent missing = http(503, null);
        missing.setRequest(null);
        assertTrue(policy.shouldReport(missing, 1));
        assertEquals("unknown", missing.getTag("http_source"));
        for (int i = 0; i < 65; i++) {
            assertTrue(policy.shouldReport(http(503, "https://host" + i + ".example/tiles/1"), 2));
        }
        assertTrue(policy.shouldReport(http(503, "https://host0.example/tiles/1"), 3));
    }

    static SentryEvent http(int status, String url) {
        SentryEvent event = new SentryEvent();
        event.setLevel(SentryLevel.ERROR);
        event.setTag("operation", "MAP");
        Request request = new Request();
        request.setUrl(url);
        request.setMethod("GET");
        event.setRequest(request);
        SentryException exception = new SentryException();
        exception.setType("SentryHttpClientException");
        exception.setModule("io.sentry.exception");
        exception.setValue("HTTP Client Error with status code: " + status);
        Mechanism mechanism = new Mechanism();
        mechanism.setType("SentryOkHttpInterceptor");
        mechanism.setHandled(true);
        exception.setMechanism(mechanism);
        event.setExceptions(new ArrayList<>(List.of(exception)));
        return event;
    }
}
