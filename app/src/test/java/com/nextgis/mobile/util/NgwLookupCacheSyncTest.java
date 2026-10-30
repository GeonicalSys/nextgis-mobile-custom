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
        @Override public boolean save() { saves++;return saveResult; }
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
    @Test public void falseSaveKeepsExistingInMemoryAndResultPolicyWithoutDurabilityClaim() {
        ProbeTable table=table(values("one","old"),values("one","new"));
        table.saveResult=false;SyncResult result=sync(table);
        assertEquals(values("one","new"),table.getData());assertEquals(1,table.saves);
        assertEquals(0,result.stats.numParseExceptions);assertEquals(0,result.stats.numIoExceptions);
    }
}
