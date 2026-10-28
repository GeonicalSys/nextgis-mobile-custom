package com.nextgis.mobile.util;

import android.app.ApplicationExitInfo;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.Assert.*;

public class DiagnosticsTracePolicyTest {
    @Test public void signalTraceIsRequestedFromApi30AndNativeProtoKeepsItsApi31Gate() {
        assertNull(DiagnosticsTracePolicy.requestedFormat(29, ApplicationExitInfo.REASON_SIGNALED));
        assertEquals(DiagnosticsNativeTrace.Format.AUTO, DiagnosticsTracePolicy.requestedFormat(30, ApplicationExitInfo.REASON_SIGNALED));
        assertEquals(DiagnosticsNativeTrace.Format.AUTO, DiagnosticsTracePolicy.requestedFormat(36, ApplicationExitInfo.REASON_SIGNALED));
        assertEquals(DiagnosticsNativeTrace.Format.ANR_TEXT, DiagnosticsTracePolicy.requestedFormat(30, ApplicationExitInfo.REASON_ANR));
        assertNull(DiagnosticsTracePolicy.requestedFormat(30, ApplicationExitInfo.REASON_CRASH_NATIVE));
        assertEquals(DiagnosticsNativeTrace.Format.NATIVE_PROTOBUF, DiagnosticsTracePolicy.requestedFormat(31, ApplicationExitInfo.REASON_CRASH_NATIVE));
    }

    @Test public void skippedReasonsNeverOpenTheOsStreamAndAreNotClaimedUnavailable() {
        DiagnosticsTracePolicy.TraceInput forbidden = () -> { fail("Skipped trace opened"); return null; };
        assertEquals("unsupported_api", DiagnosticsTracePolicy.read(29, ApplicationExitInfo.REASON_SIGNALED, forbidden).get("status"));
        for (int reason : new int[]{ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_EXIT_SELF, ApplicationExitInfo.REASON_USER_REQUESTED})
            assertEquals("not_requested_for_reason", DiagnosticsTracePolicy.read(36, reason, forbidden).get("status"));
    }

    @Test public void signalCanCarryPriorRecoveredAnrWithoutClaimingItsCause() {
        Map<String,Object> result = DiagnosticsTracePolicy.read(30, ApplicationExitInfo.REASON_SIGNALED,
                () -> new ByteArrayInputStream("\"main\" prio=5 tid=1\n at android.view.View.draw(View.java:42)".getBytes(StandardCharsets.UTF_8)));
        assertEquals("captured", result.get("status"));
        assertEquals("auto", result.get("requested_format"));
        assertEquals("anr_text", result.get("format"));
        assertEquals("may_precede_exit", result.get("association"));
        assertFalse(result.containsKey("cause"));
        assertFalse(result.containsKey("native_frames"));
    }

    @Test public void missingAndOpenFailuresRemainDistinctAndNeverExposeExceptionMessages() {
        Map<String,Object> missing = DiagnosticsTracePolicy.read(30, ApplicationExitInfo.REASON_SIGNALED, () -> null);
        assertEquals("missing", missing.get("status"));
        assertEquals("unknown", missing.get("format"));
        assertEquals("may_precede_exit", missing.get("association"));
        for (Exception failure : new Exception[]{new IOException("SECRET/path"), new SecurityException("SECRET token")}) {
            Map<String,Object> result = DiagnosticsTracePolicy.read(36, ApplicationExitInfo.REASON_SIGNALED, () -> {
                if (failure instanceof IOException) throw (IOException) failure;
                throw (RuntimeException) failure;
            });
            assertEquals("unavailable", result.get("status"));
            assertEquals("unknown", result.get("format"));
            assertEquals(failure.getClass().getSimpleName(), result.get("error_type"));
            assertEquals("may_precede_exit", result.get("association"));
            assertFalse(result.toString().contains("SECRET"));
        }
    }
}
