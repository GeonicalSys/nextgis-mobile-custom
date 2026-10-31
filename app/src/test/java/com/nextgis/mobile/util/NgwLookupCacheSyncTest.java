package com.nextgis.mobile.util;

import android.app.Application;
import android.content.Context;
import android.content.SyncResult;
import android.util.Pair;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IProgressor;
import com.nextgis.maplib.map.NGWLookupTable;
import com.nextgis.maplib.util.Constants;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import static org.junit.Assert.*;

/** Actual public sync with technical read/save seams; no disk or network I/O. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE, application=Application.class,
        shadows=NgwLookupCacheSyncTest.QuietLog.class)
public class NgwLookupCacheSyncTest {
    @Implements(HyperLog.class)
    public static class QuietLog {
        @Implementation protected static void v(String tag,String message) { }
    }
    private static final class ProbeTable extends NGWLookupTable {
        Map<String,String> remote=new LinkedHashMap<>();
        int reads,saves;
        boolean failAfterPartialRead,saveResult=true;
        RuntimeException runtimeSaveFailure;
        Error fatalSaveFailure;
        ProbeTable(Context context) {
            super(context,new File(System.getProperty("java.io.tmpdir"),"lookup-probe-no-file"));
            setName("technical lookup");setSyncType(Constants.SYNC_ALL);
        }
        @Override protected void fillFromNGW(Map<String,String> target,IProgressor progressor)
                throws IOException {
            reads++;
            if(failAfterPartialRead) {
                target.put("partial","unconfirmed");
                throw new IOException("technical read failure");
            }
            target.putAll(remote);
        }
        @Override public boolean save() {
            saves++;
            if(runtimeSaveFailure!=null)throw runtimeSaveFailure;
            if(fatalSaveFailure!=null)throw fatalSaveFailure;
            return saveResult;
        }
    }
    private static Map<String,String> values(String...pairs) {
        Map<String,String> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)result.put(pairs[i],pairs[i+1]);
        return result;
    }
    private static ProbeTable table(Map<String,String> cached,Map<String,String> remote) {
        ProbeTable table=new ProbeTable(RuntimeEnvironment.getApplication());
        table.setData(cached);table.remote=remote;return table;
    }
    private static SyncResult sync(ProbeTable table) {
        SyncResult result=new SyncResult();
        table.sync("technical.authority",Pair.create(3,0),result);
        return result;
    }
    @Test public void equalCountNewKeyReplacesCompleteCacheWithoutException() {
        ProbeTable table=table(values("old","cached"),values("new","downloaded"));
        Map<String,String> exactCache=table.getData();
        SyncResult result;
        try { result=sync(table); }
        catch(NullPointerException failure) {
            fail("Complete equal-count lookup key replacement must refresh, not dereference a missing local key");return;
        }
        assertSame(exactCache,table.getData());
        assertEquals(values("new","downloaded"),table.getData());
        assertEquals(1,table.reads);assertEquals(1,table.saves);
        assertEquals(0,result.stats.numParseExceptions);assertEquals(0,result.stats.numIoExceptions);
    }
    @Test public void unchangedCacheDoesNotWrite() {
        ProbeTable table=table(values("one","value"),values("one","value"));
        Map<String,String> cache=table.getData();sync(table);
        assertSame(cache,table.getData());assertEquals(values("one","value"),table.getData());
        assertEquals(1,table.reads);assertEquals(0,table.saves);
    }
    @Test public void reorderedEqualContentKeepsExistingCacheOrderAndIdentity() {
        ProbeTable table=table(values("two","second","one","first"),values("one","first","two","second"));
        Map<String,String> cache=table.getData();sync(table);
        assertSame(cache,table.getData());assertEquals(Arrays.asList("two","one"),new ArrayList<>(cache.keySet()));
        assertEquals(0,table.saves);
    }
    @Test public void changedValueOfExistingKeyStillWritesOnce() {
        ProbeTable table=table(values("one","old"),values("one","new"));sync(table);
        assertEquals(values("one","new"),table.getData());assertEquals(1,table.saves);
    }
    @Test public void changedCountStillReplacesWholeCache() {
        ProbeTable table=table(values("one","old","two","extra"),values("new","downloaded"));
        Map<String,String> cache=table.getData();sync(table);
        assertSame(cache,table.getData());assertEquals(values("new","downloaded"),cache);assertEquals(1,table.saves);
    }
    @Test public void completeEmptyRemoteClearsCacheThroughExistingSizeBranch() {
        ProbeTable table=table(values("one","old"),values());sync(table);
        assertTrue(table.getData().isEmpty());assertEquals(1,table.saves);
    }
    @Test public void syncNoneDoesNotReadOrWrite() {
        ProbeTable table=table(values("one","old"),values("new","downloaded"));
        table.setSyncType(Constants.SYNC_NONE);sync(table);
        assertEquals(values("one","old"),table.getData());assertEquals(0,table.reads);assertEquals(0,table.saves);
    }
    @Test public void failedPartialReadKeepsPriorCacheAndExistingErrorSignal() {
        ProbeTable table=table(values("one","old"),values("new","downloaded"));
        Map<String,String> cache=table.getData();table.failAfterPartialRead=true;SyncResult result=sync(table);
        assertSame(cache,table.getData());assertEquals(values("one","old"),cache);
        assertEquals(1,table.reads);assertEquals(0,table.saves);assertEquals(1,result.stats.numParseExceptions);
    }
    @Test public void falseSaveRetainsMemoryAndReportsIoWithoutDurabilityClaim() {
        ProbeTable table=table(values("one","old"),values("one","new"));
        Map<String,String> cache=table.getData();
        table.saveResult=false;SyncResult result=sync(table);
        assertSame(cache,table.getData());
        assertEquals(values("one","new"),table.getData());assertEquals(1,table.saves);
        assertEquals(0,result.stats.numParseExceptions);assertEquals(1,result.stats.numIoExceptions);
    }

    @Test public void falseSaveInSizeBranchReportsIoWithoutRollbackOrSecondWrite() {
        ProbeTable table=table(values("one","old"),values("one","new","extra","value"));
        Map<String,String> cache=table.getData();table.saveResult=false;
        SyncResult result=sync(table);
        assertSame(cache,table.getData());
        assertEquals(values("one","new","extra","value"),cache);
        assertEquals(1,table.reads);assertEquals(1,table.saves);
        assertEquals(0,result.stats.numParseExceptions);assertEquals(1,result.stats.numIoExceptions);
    }
    @Test public void falseSaveAddsIoAndPreservesExistingResultCohort() {
        ProbeTable table=table(values("one","old"),values("one","new"));
        Map<String,String> cache=table.getData();table.saveResult=false;
        SyncResult result=new SyncResult();result.stats.numIoExceptions=4;
        result.stats.numParseExceptions=2;result.stats.numAuthExceptions=3;
        table.sync("technical.authority",Pair.create(3,0),result);
        assertSame(cache,table.getData());assertEquals(values("one","new"),cache);
        assertEquals(1,table.reads);assertEquals(1,table.saves);
        assertEquals(2,result.stats.numParseExceptions);assertEquals(3,result.stats.numAuthExceptions);
        assertEquals(5,result.stats.numIoExceptions);
    }
    @Test public void successfulSaveKeepsExistingErrorCohort() {
        ProbeTable table=table(values("one","old"),values("one","new"));
        SyncResult result=new SyncResult();result.stats.numIoExceptions=4;
        result.stats.numParseExceptions=2;result.stats.numAuthExceptions=3;
        table.sync("technical.authority",Pair.create(3,0),result);
        assertEquals(values("one","new"),table.getData());
        assertEquals(1,table.reads);assertEquals(1,table.saves);
        assertEquals(4,result.stats.numIoExceptions);assertEquals(2,result.stats.numParseExceptions);
        assertEquals(3,result.stats.numAuthExceptions);
    }
    @Test public void runtimeSaveFailurePropagatesWithoutRollbackOrExtraAttempt() {
        ProbeTable table=table(values("one","old"),values("one","new"));
        Map<String,String> cache=table.getData();RuntimeException expected=new IllegalStateException("technical save failure");
        table.runtimeSaveFailure=expected;SyncResult result=new SyncResult();RuntimeException actual=null;
        try { table.sync("technical.authority",Pair.create(3,0),result); }
        catch(RuntimeException failure) { actual=failure; }
        assertSame(expected,actual);assertSame(cache,table.getData());
        assertEquals(values("one","new"),cache);assertEquals(1,table.reads);assertEquals(1,table.saves);
        assertEquals(0,result.stats.numIoExceptions);assertEquals(0,result.stats.numParseExceptions);
    }
    @Test public void fatalSaveFailurePropagatesWithoutRollbackOrExtraAttempt() {
        ProbeTable table=table(values("one","old"),values("one","new"));
        Map<String,String> cache=table.getData();Error expected=new AssertionError("technical fatal save failure");
        table.fatalSaveFailure=expected;SyncResult result=new SyncResult();Error actual=null;
        try { table.sync("technical.authority",Pair.create(3,0),result); }
        catch(Error failure) { actual=failure; }
        assertSame(expected,actual);assertSame(cache,table.getData());
        assertEquals(values("one","new"),cache);assertEquals(1,table.reads);assertEquals(1,table.saves);
        assertEquals(0,result.stats.numIoExceptions);assertEquals(0,result.stats.numParseExceptions);
    }
    @Test public void failureAddsNoCompensatingEqualResponseOrForeignTableWrites() {
        ProbeTable table=table(values("one","old"),values("one","new"));
        Map<String,String> cache=table.getData();table.saveResult=false;sync(table);
        // Existing publication/retry scope is preserved; this does not certify disk or a clean ACK.
        table.saveResult=true;sync(table);
        assertSame(cache,table.getData());assertEquals(values("one","new"),cache);
        assertEquals(2,table.reads);assertEquals(1,table.saves);
        ProbeTable other=table(values("one","foreign"),values("one","foreign"));
        SyncResult otherResult=sync(other);
        assertNotSame(cache,other.getData());assertEquals(values("one","foreign"),other.getData());
        assertEquals(1,other.reads);assertEquals(0,other.saves);
        assertEquals(0,otherResult.stats.numIoExceptions);assertEquals(0,otherResult.stats.numParseExceptions);
    }
}
