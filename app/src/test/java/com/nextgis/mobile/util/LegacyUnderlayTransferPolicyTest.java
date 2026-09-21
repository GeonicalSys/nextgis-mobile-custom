package com.nextgis.mobile.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyUnderlayTransferPolicyTest {
    @Test
    public void inactivityUsesLastByteProgressRatherThanTotalDuration() {
        LegacyUnderlayTransferPolicy policy = new LegacyUnderlayTransferPolicy(100L, 1);

        assertFalse(policy.isStalled(1_099L, 1_000L));
        assertTrue(policy.isStalled(1_100L, 1_000L));
        assertFalse(policy.isStalled(2_050L, 2_000L));
    }

    @Test
    public void stalledSourceGetsExactlyOneRetry() {
        LegacyUnderlayTransferPolicy policy = new LegacyUnderlayTransferPolicy(100L, 1);

        assertTrue(policy.canRetry(0));
        assertFalse(policy.canRetry(1));
    }

    @Test
    public void explicitCancelIsDurableBeforeDescriptorAttachment() {
        LegacyUnderlayImporter.TransferControl control =
                new LegacyUnderlayImporter.TransferControl();

        control.cancel();

        assertTrue(control.isCanceled());
    }

    @Test(expected = IllegalArgumentException.class)
    public void timeoutMustBePositive() {
        new LegacyUnderlayTransferPolicy(0L, 1);
    }

    @Test
    public void idleCatalogExitDoesNotPrompt() {
        assertTrue(LegacyUnderlayTransferPolicy.shouldFinishWithoutTransferExitPrompt(false));
        assertFalse(LegacyUnderlayTransferPolicy.shouldShowTransferExitDialog(false, false));
        assertFalse(LegacyUnderlayTransferPolicy.shouldShowTransferExitDialog(false, true));
    }

    @Test
    public void activeTransferRequiresExitConfirmation() {
        assertFalse(LegacyUnderlayTransferPolicy.shouldFinishWithoutTransferExitPrompt(true));
        assertTrue(LegacyUnderlayTransferPolicy.shouldShowTransferExitDialog(true, false));
    }

    @Test
    public void exitConfirmationIsNotShownTwice() {
        assertFalse(LegacyUnderlayTransferPolicy.shouldShowTransferExitDialog(true, true));
    }
}
