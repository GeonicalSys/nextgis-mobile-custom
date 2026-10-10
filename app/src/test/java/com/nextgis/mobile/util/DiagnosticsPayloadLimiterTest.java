package com.nextgis.mobile.util;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.Response;
import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class DiagnosticsPayloadLimiterTest {
    @Test public void sdkHintPreservesOriginalUrlQueryHeadersAndResponse() {
        okhttp3.Request original = new okhttp3.Request.Builder()
                .url("https://user:demo-pass@example.org/resource/803?token=demo-token&project=42#section")
                .header("Authorization", "Bearer demo-header")
                .header("Cookie", "session=demo-cookie").build();
        okhttp3.Response response = new okhttp3.Response.Builder().request(original)
                .protocol(okhttp3.Protocol.HTTP_1_1).code(503).message("Unavailable")
                .header("Retry-After", "60").header("Set-Cookie", "session=demo-new").build();
        Hint hint = new Hint();
        hint.set("okHttp:request", original);
        hint.set("okHttp:response", response);
        SentryEvent event = DiagnosticsHttpPolicyTest.http(503, "https://example.org/resource/803");
        AppDiagnostics.preserveHttpContext(event, hint);
        AppDiagnostics.sanitize(event);
        Request saved = event.getRequest();
        assertEquals("https://user:demo-pass@example.org/resource/803", saved.getUrl());
        assertEquals("token=demo-token&project=42", saved.getQueryString());
        assertEquals("section", saved.getFragment());
        assertEquals("Bearer demo-header", saved.getHeaders().get("Authorization"));
        assertEquals("session=demo-cookie", saved.getCookies());
        Response savedResponse = event.getContexts().getResponse();
        assertEquals(Integer.valueOf(503), savedResponse.getStatusCode());
        assertEquals("60", savedResponse.getHeaders().get("Retry-After"));
        assertEquals("session=demo-new", savedResponse.getCookies());
    }

    @Test public void structuredDataRemainsStructuredAndCredentialsAreNotFiltered() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("attributes", Map.of("password", "demo-password", "name", "Field point"));
        input.put("geometry", new double[]{28.123456, 60.123456});
        input.put("empty", "");
        Map<String, Object> result = new DiagnosticsPayloadLimiter().map(input);
        assertEquals(input.get("attributes"), result.get("attributes"));
        assertEquals(java.util.List.of(28.123456, 60.123456), result.get("geometry"));
        assertEquals("", result.get("empty"));
    }

    @Test public void cyclesAndUnknownObjectsCannotBreakDiagnosticSerialization() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("self", input);
        input.put("opaque", new Object() { @Override public String toString() { throw new AssertionError(); } });
        Map<String, Object> result = new DiagnosticsPayloadLimiter().map(input);
        assertEquals("[cyclic diagnostic]", result.get("self"));
        assertTrue(result.get("opaque") instanceof String);
    }

    @Test public void largePayloadsAndExhaustedBudgetStillReturnSerializableMaps() {
        Map<String, Object> input = new LinkedHashMap<>();
        for (int i = 0; i < 1000; i++) input.put("field" + i, "x".repeat(100_000));
        DiagnosticsPayloadLimiter limit = new DiagnosticsPayloadLimiter();
        Map<String, Object> result = limit.map(input);
        int characters = result.entrySet().stream().mapToInt(entry -> entry.getKey().length() + entry.getValue().toString().length()).sum();
        assertTrue(result.size() < 64);
        assertTrue(characters <= 32_800);
        assertNotNull(limit.map(input));
    }
}
