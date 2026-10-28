package com.nextgis.mobile.util;

import android.app.ApplicationExitInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Post-restart OS trace collection only; an attached trace does not establish the exit's cause. */
final class DiagnosticsTracePolicy {
    interface TraceInput { InputStream open() throws IOException; }

    static DiagnosticsNativeTrace.Format requestedFormat(int api, int reason) {
        if (api < 30) return null;
        if (reason == ApplicationExitInfo.REASON_SIGNALED) return DiagnosticsNativeTrace.Format.AUTO;
        if (reason == ApplicationExitInfo.REASON_ANR) return DiagnosticsNativeTrace.Format.ANR_TEXT;
        if (reason == ApplicationExitInfo.REASON_CRASH_NATIVE && api >= 31)
            return DiagnosticsNativeTrace.Format.NATIVE_PROTOBUF;
        return null;
    }

    static Map<String, Object> read(int api, int reason, TraceInput input) {
        DiagnosticsNativeTrace.Format requested = requestedFormat(api, reason);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", "unknown");
        if (requested == null) {
            result.put("status", api < 30 ? "unsupported_api" : "not_requested_for_reason");
            return result;
        }
        result.put("requested_format", requested.label);
        // ApplicationExitInfo can retain an earlier, recovered ANR when the process later dies
        // for another reason. In particular, SIGNALED is not proof of a native crash or OOM.
        result.put("association", reason == ApplicationExitInfo.REASON_SIGNALED
                ? "may_precede_exit" : "os_exit_record");
        try { result.putAll(DiagnosticsNativeTrace.read(input.open(), requested)); }
        catch (IOException | RuntimeException error) {
            result.put("status", "unavailable");
            result.put("error_type", DiagnosticsNativeTrace.errorType(error));
        }
        return result;
    }
}
