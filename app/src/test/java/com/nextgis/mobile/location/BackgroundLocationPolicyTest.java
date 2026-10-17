package com.nextgis.mobile.location;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackgroundLocationPolicyTest {
    @Test public void explicitExitDisarmsGpsUntilTheNextForegroundEntry() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        policy.foreground(1_000);
        policy.background(2_000);
        policy.close();
        assertFalse(policy.isOpened());
        assertFalse(policy.wanted(true, 2_001));
        policy.background(2_002);
        assertFalse(policy.wanted(true, 2_003));
        policy.foreground(3_000);
        assertTrue(policy.isOpened());
        assertTrue(policy.wanted(true, 3_000));
    }

    @Test public void exitWhileVisibleCannotBeRearmedByPreferenceOrTimeoutRefresh() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        policy.foreground(1_000);
        policy.close();
        assertFalse(policy.wanted(false, 1_001));
        assertFalse(policy.wanted(true, 1_002));
        assertFalse(policy.wanted(true, 20_000_000));
    }
    @Test public void backgroundProcessWithoutOpeningTheAppNeverStartsGps() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        assertFalse(policy.wanted(true, 1_000));
    }
    @Test public void openAppHasNoTimeLimitAndBackgroundExpiresAtTwoHours() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        policy.foreground(1_000);
        assertTrue(policy.wanted(true, 20_000_000));
        policy.background(20_000_000);
        assertTrue(policy.wanted(true, 27_199_999));
        assertFalse(policy.wanted(true, 27_200_000));
    }
    @Test public void returnToTheAppRestartsTheInactivityDeadlineAndSettingWins() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        policy.foreground(1_000); policy.background(2_000);
        policy.foreground(7_201_000); policy.background(7_202_000);
        assertTrue(policy.wanted(true, 14_401_999));
        assertFalse(policy.wanted(true, 14_402_000));
        policy.foreground(14_403_000);
        assertFalse(policy.wanted(false, 14_403_000));
        assertTrue(policy.wanted(true, 14_403_000));
    }
}
