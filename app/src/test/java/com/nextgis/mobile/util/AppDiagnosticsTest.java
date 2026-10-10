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
}
