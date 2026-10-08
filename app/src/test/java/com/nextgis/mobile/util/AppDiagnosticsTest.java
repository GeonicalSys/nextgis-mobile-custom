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
    @Test public void eventKeepsStackAndOperationButDropsBusinessPayloads() {
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
        assertNull(event.getUser());
        assertNull(event.getRequest());
        assertNull(event.getExtras());
        assertFalse(event.getContexts().containsKey("geometry"));
        assertEquals("AZIMUTH", event.getTag("operation"));
        assertEquals("SecurityException", event.getExceptions().get(0).getType());
        assertFalse(exception.getValue().contains("demo-secret"));
        assertEquals("onStartCommand", frame.getFunction());
        assertEquals(Integer.valueOf(58), frame.getLineno());
        assertNull(frame.getVars());
        assertNull(frame.getAbsPath());
    }

    @Test public void onlyStructuredBreadcrumbsSurviveIncludingReplayedEvents() {
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
        assertEquals(1, event.getBreadcrumbs().size());
        assertEquals("ModifyAttributesActivity", screen.getData("screen"));
        assertNull(screen.getData("field_value"));
    }
}
