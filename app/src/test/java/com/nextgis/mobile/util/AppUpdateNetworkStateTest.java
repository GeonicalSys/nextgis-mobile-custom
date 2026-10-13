package com.nextgis.mobile.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class AppUpdateNetworkStateTest {
    @Test public void unvalidatedWifiCanRequestTheUpdateServer() {
        AppUpdateManager.NetworkState state = new AppUpdateManager.NetworkState(true, true, false, true, false, false);
        assertTrue(state.canRequest());
        assertTrue(state.toString().contains("validated=false"));
        assertTrue(state.toString().contains("wifi=true"));
    }

    @Test public void unvalidatedCellularAndVpnCanRequestTheUpdateServer() {
        assertTrue(new AppUpdateManager.NetworkState(true, true, false, false, true, false).canRequest());
        assertTrue(new AppUpdateManager.NetworkState(true, true, false, false, false, true).canRequest());
    }

    @Test public void absentNetworkOrMissingInternetCapabilityRemainsPending() {
        assertFalse(new AppUpdateManager.NetworkState(false, false, false, false, false, false).canRequest());
        assertFalse(new AppUpdateManager.NetworkState(true, false, false, true, false, false).canRequest());
    }

    @Test public void validatedNetworkStillRequestsTheServer() {
        assertTrue(new AppUpdateManager.NetworkState(true, true, true, true, false, false).canRequest());
    }
}
