package com.nextgis.mobile.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyUnderlayMigrationContractPolicyTest {
    @Test
    public void preRegistryDebugKeepsLegacyMapAndSkipsSharedCatalogMigration() {
        assertTrue(LegacyUnderlayMigrationContract.shouldDeferLegacyDebugWorkspace(
                true, false, true));
        assertFalse(LegacyUnderlayMigrationContract.shouldScheduleSharedCatalogMigration(true));
    }

    @Test
    public void registeredDebugStillSchedulesSharedCatalogMigration() {
        assertFalse(LegacyUnderlayMigrationContract.shouldDeferLegacyDebugWorkspace(
                true, true, true));
        assertTrue(LegacyUnderlayMigrationContract.shouldScheduleSharedCatalogMigration(false));
    }

    @Test
    public void productionGeonicalIsNotDeferred() {
        assertFalse(LegacyUnderlayMigrationContract.shouldDeferLegacyDebugWorkspace(
                false, false, true));
        assertTrue(LegacyUnderlayMigrationContract.shouldScheduleSharedCatalogMigration(false));
    }

    @Test
    public void debugWithoutLegacyMapUsesNormalBootstrap() {
        assertFalse(LegacyUnderlayMigrationContract.shouldDeferLegacyDebugWorkspace(
                true, false, false));
    }
}
