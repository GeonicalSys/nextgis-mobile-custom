package com.nextgis.mobile.location;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackgroundLocationPolicyTest {
    @Test public void notificationMuteSurvivesForegroundNavigationAndRotation() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        policy.foreground(1_000);
        String session = policy.getSessionId();
        assertTrue(policy.disableBackground(session));
        policy.background(2_000);
        assertFalse(policy.wanted(true, 2_001));
        policy.foreground(3_000);
        assertEquals(session, policy.getSessionId());
        policy.background(4_000);
        policy.foreground(5_000);
        assertFalse(policy.wanted(true, 5_000));
        assertTrue(policy.isOpened());
    }

    @Test public void notificationMuteDoesNotEndTheUserSessionOrDependOnTheSetting() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        policy.foreground(1_000);
        String session = policy.getSessionId();
        assertTrue(policy.disableBackground(session));
        assertTrue(policy.disableBackground(session));
        assertFalse(policy.wanted(false, 1_001));
        assertFalse(policy.wanted(true, 1_002));
        assertEquals(session, policy.getSessionId());
    }

    @Test public void newOpeningResetsMuteAndRejectsTheOldNotification() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        policy.foreground(1_000);
        String old = policy.getSessionId();
        assertTrue(policy.disableBackground(old));
        policy.close();
        assertNull(policy.getSessionId());
        policy.foreground(2_000);
        assertNotEquals(old, policy.getSessionId());
        assertFalse(policy.disableBackground(old));
        assertTrue(policy.wanted(true, 2_001));
        assertFalse(policy.wanted(false, 2_001));
    }

    @Test public void foreignOrMissingSessionCannotMuteOrOpenGps() {
        BackgroundLocationPolicy first = new BackgroundLocationPolicy();
        BackgroundLocationPolicy second = new BackgroundLocationPolicy();
        first.foreground(1_000);
        assertFalse(second.disableBackground(first.getSessionId()));
        assertFalse(second.isOpened());
        second.foreground(1_000);
        assertFalse(second.disableBackground(first.getSessionId()));
        assertFalse(second.disableBackground(null));
        assertTrue(second.wanted(true, 1_001));
    }

    @Test public void returningAfterTheTimeoutDoesNotClearNotificationMute() {
        BackgroundLocationPolicy policy = new BackgroundLocationPolicy();
        policy.foreground(1_000);
        assertTrue(policy.disableBackground(policy.getSessionId()));
        policy.background(2_000);
        policy.foreground(2_000 + BackgroundLocationPolicy.INACTIVITY_TIMEOUT_MS);
        assertFalse(policy.wanted(true, 2_001 + BackgroundLocationPolicy.INACTIVITY_TIMEOUT_MS));
    }

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
