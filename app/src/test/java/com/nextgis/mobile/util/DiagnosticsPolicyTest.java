package com.nextgis.mobile.util;

import org.junit.Test;
import java.io.IOException;
import java.net.UnknownHostException;
import static org.junit.Assert.*;

public class DiagnosticsPolicyTest {
    @Test public void usefulAndroidFailureSurvivesRedaction() {
        String explanation = "Starting FGS with type location requires permissions "
                + "android.permission.FOREGROUND_SERVICE_LOCATION and ACCESS_FINE_LOCATION";
        assertEquals(explanation, DiagnosticsPolicy.redact(explanation));
        assertEquals("Cannot update display while location is unavailable",
                DiagnosticsPolicy.redact("Cannot update display while location is unavailable"));
    }

    @Test public void credentialsAddressesAndSqlNeverEnterEventMessages() {
        String redacted = DiagnosticsPolicy.redact("Failed password='demo-password' "
                + "Authorization: Bearer demo-bearer https://name:demo-pass@example.org/resource/803?token=demo-token "
                + "name@example.org at 60.123456, 28.123456; while compiling: INSERT INTO staff VALUES('private person')");
        for (String secret : new String[]{"demo-password", "demo-bearer", "demo-pass", "demo-token",
                "example.org", "60.123456", "28.123456", "private person"}) assertFalse(secret, redacted.contains(secret));
        assertTrue(redacted.contains("[database statement omitted]"));
    }

    @Test public void pathsReceiverDataAndHugeMessagesAreBounded() {
        assertFalse(DiagnosticsPolicy.redact("Cannot read /storage/emulated/0/customer/project/photo.jpg").contains("customer"));
        assertFalse(DiagnosticsPolicy.redact("Cannot read C:\\Work\\customer\\file.db").contains("customer"));
        assertFalse(DiagnosticsPolicy.redact("GNSS $GPGGA,123,60.123456,N").contains("123,"));
        assertEquals("Invalid geometry: [geometry omitted]",
                DiagnosticsPolicy.redact("Invalid geometry: SRID=4326;POINT(28.12 60.22)"));
        assertEquals(1024, DiagnosticsPolicy.redact("x".repeat(100_000)).length());
    }

    @Test public void offlineAndCancellationAreExpectedButLocalStorageFailureIsReported() {
        assertTrue(DiagnosticsPolicy.expectedConnectivityOrCancellation(new IOException(new UnknownHostException("offline"))));
        assertTrue(DiagnosticsPolicy.expectedConnectivityOrCancellation(new InterruptedException()));
        assertFalse(DiagnosticsPolicy.expectedConnectivityOrCancellation(new IOException("No space left on device")));
    }

    @Test public void repeatedHandledErrorIsLimitedWithoutSuppressingDifferentOperationOrNextInterval() {
        DiagnosticsPolicy policy = new DiagnosticsPolicy();
        Throwable error = new IllegalStateException("one failure");
        assertTrue(policy.shouldReport("SYNC", error, 1));
        assertFalse(policy.shouldReport("SYNC", error, 2));
        assertTrue(policy.shouldReport("AZIMUTH", error, 2));
        assertTrue(policy.shouldReport("SYNC", error, 600_001));
    }

    @Test public void serverOutagesAndRateLimitsRetainEventsButInvalidPayloadsDoNotLoop() {
        for (int status : new int[]{401, 403, 408, 425, 429, 500, 502, 503, 504}) {
            assertTrue("HTTP " + status, DiagnosticsTransport.isRetryableStatus(status));
        }
        for (int status : new int[]{400, 404, 405, 413, 422}) {
            assertFalse("HTTP " + status, DiagnosticsTransport.isRetryableStatus(status));
        }
    }
}
