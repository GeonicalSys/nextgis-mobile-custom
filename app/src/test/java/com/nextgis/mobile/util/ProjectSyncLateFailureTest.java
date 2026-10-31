package com.nextgis.mobile.util;

import android.app.Application;
import android.content.SyncResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** The actual queue's final-outcome helper, without constructing any map/database/session. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class ProjectSyncLateFailureTest {
    @Test public void previouslyCleanProjectGetsOneFailureAndItsName() {
        SyncResult result = new SyncResult(); List<String> failed = new ArrayList<>();
        boolean recorded = ProjectSyncRunner.recordLateProjectFailure(result, failed, "First", true, false, false);
        assertTrue(recorded); assertTrue(result.hasError()); assertEquals(1, result.stats.numIoExceptions);
        assertEquals(Arrays.asList("First"),failed);
        recorded = ProjectSyncRunner.recordLateProjectFailure(result, failed, "First", true, recorded, false);
        assertTrue(recorded); assertEquals(1,result.stats.numIoExceptions); assertEquals(1,failed.size());
    }
    @Test public void anotherProjectsFailureDoesNotHideThisLateFailure() {
        SyncResult result = new SyncResult(); result.stats.numIoExceptions = 7;
        List<String> failed = new ArrayList<>(Arrays.asList("Other"));
        assertTrue(ProjectSyncRunner.recordLateProjectFailure(result,failed,"First",true,false,false));
        assertEquals(8,result.stats.numIoExceptions); assertEquals(Arrays.asList("Other","First"),failed);
    }
    @Test public void equalDisplayNamesDoNotReplacePerProjectFailureState() {
        SyncResult result = new SyncResult(); result.stats.numIoExceptions = 1;
        List<String> failed = new ArrayList<>(Arrays.asList("Same name"));
        assertTrue(ProjectSyncRunner.recordLateProjectFailure(result,failed,"Same name",true,false,false));
        assertEquals(2,result.stats.numIoExceptions); assertEquals(1,failed.size());
    }
    @Test public void alreadyRecordedAccountErrorDoesNotGetAnotherIo() {
        SyncResult result = new SyncResult(); result.stats.numAuthExceptions = 1;
        List<String> failed = new ArrayList<>(Arrays.asList("First"));
        assertTrue(ProjectSyncRunner.recordLateProjectFailure(result,failed,"First",true,true,false));
        assertEquals(0,result.stats.numIoExceptions); assertEquals(1,result.stats.numAuthExceptions);
        assertEquals(1,failed.size());
    }
    @Test public void cleanupErrorAlreadyCountedDoesNotGetAnotherIo() {
        SyncResult result = new SyncResult(); result.stats.numIoExceptions = 1;
        List<String> failed = new ArrayList<>(Arrays.asList("First"));
        assertTrue(ProjectSyncRunner.recordLateProjectFailure(result,failed,"First",true,true,false));
        assertEquals(1,result.stats.numIoExceptions); assertEquals(1,failed.size());
    }
    @Test public void cancellationIsNotReportedAsALateIoFailure() {
        SyncResult result = new SyncResult(); List<String> failed = new ArrayList<>();
        assertFalse(ProjectSyncRunner.recordLateProjectFailure(result,failed,"First",true,false,true));
        assertFalse(result.hasError()); assertEquals(0,result.stats.numIoExceptions); assertTrue(failed.isEmpty());
    }
    @Test public void quietSuccessfulChildLeavesAllCountersAndNamesUnchanged() {
        SyncResult result = new SyncResult(); result.stats.numInserts = 2;
        List<String> failed = new ArrayList<>();
        assertFalse(ProjectSyncRunner.recordLateProjectFailure(result,failed,"First",false,false,false));
        assertFalse(result.hasError()); assertEquals(0,result.stats.numIoExceptions); assertEquals(2,result.stats.numInserts);assertTrue(failed.isEmpty());
    }
}
