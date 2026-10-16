package com.nextgis.mobile.usage;

import org.junit.Test;
import static org.junit.Assert.*;

public class UsageOpenPolicyTest {
    @Test public void workerInitializationDoesNotCountAsOpening() {
        UsageOpenPolicy policy = new UsageOpenPolicy();
        assertFalse(policy.isForeground());
        policy.hide();
        assertEquals("app_open", policy.open());
    }
    @Test public void activityTransfersAndRotationDoNotCreateExtraOpens() {
        UsageOpenPolicy policy = new UsageOpenPolicy();
        assertEquals("app_open", policy.open());
        assertNull(policy.open());
        assertNull(policy.open());
        policy.hide();
        assertEquals("app_resume", policy.open());
        assertNull(policy.open());
        policy.hide();
        assertEquals("app_resume", policy.open());
    }
}
