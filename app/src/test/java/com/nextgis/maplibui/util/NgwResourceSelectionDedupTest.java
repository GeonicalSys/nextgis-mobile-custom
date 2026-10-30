package com.nextgis.maplibui.util;

import android.app.Application;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.Connections;
import com.nextgis.maplib.datasource.ngw.INGWResource;
import com.nextgis.maplib.datasource.ngw.ResourceGroup;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Actual package-private restore. Technical resource identities, no HTTP/map/DB/files. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE, application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class NgwResourceSelectionDedupTest {
    private static class TestGroup extends ResourceGroup {
        int childReads;
        int missingOnChildRead;
        TestGroup(long remoteId,Connection account) {
            super(remoteId,account);
            mChildrenLoaded=true;mType=Connection.NGWResourceTypeResourceGroup;
        }
        TestGroup add(long remoteId) {
            TestGroup child=new TestGroup(remoteId,mConnection);
            child.setParent(this);mChildren.add(child);return child;
        }
        @Override public void loadChildren(boolean skip) { }
        @Override public INGWResource getChild(int index) {
            childReads++;
            if(childReads==missingOnChildRead)return null;
            return super.getChild(index);
        }
    }
    private static class TestConnection extends Connection {
        TestConnection(String name,String url) {
            super(name,"technical login","technical password",url);
            mRootResource=new TestGroup(0,this);mRootResource.setParent(this);mIsConnected=true;
        }
        TestGroup root() {return (TestGroup)mRootResource;}
    }
    private static Connections accounts(Connection... values) {
        Connections result=new Connections("technical accounts");
        for(Connection value:values)result.add(value);return result;
    }
    private static int positiveEntries(List<CheckState> checks,int id,int mode) {
        int count=0;
        for(CheckState check:checks)
            if(check.getId()==id&&(mode==1?check.isCheckState1():check.isCheckState2()))count++;
        return count;
    }
    @Test public void duplicateLogicalResourceCoalescesSplitAndRepeatedModesToOneImportedIdentity() throws Exception {
        TestConnection old=new TestConnection("one","https://one.example.test");
        TestGroup a=old.root().add(20),b=old.root().add(20),c=old.root().add(20);
        List<CheckState> input=Arrays.asList(new CheckState(a.getId(),true,false),
                new CheckState(b.getId(),false,true),new CheckState(c.getId(),true,true));
        String saved=NgwResourceSelectionState.save(accounts(old),old,input);
        TestConnection fresh=new TestConnection("one","https://one.example.test");
        TestGroup target=fresh.root().add(20);
        NgwResourceSelectionState.Restored result=NgwResourceSelectionState.restore(accounts(fresh),saved,true);
        assertEquals(1,result.checks.size());assertEquals(target.getId(),result.checks.get(0).getId());
        assertEquals(1,positiveEntries(result.checks,target.getId(),1));
        assertEquals(1,positiveEntries(result.checks,target.getId(),2));
        assertSame(fresh,result.current);assertEquals(3,fresh.root().childReads);
        assertEquals(3,input.size());assertTrue(input.get(0).isCheckState1());assertFalse(input.get(0).isCheckState2());
        assertFalse(input.get(1).isCheckState1());assertTrue(input.get(1).isCheckState2());
    }
    @Test public void mergedEntryKeepsFirstSelectionOrderAndOtherResources() throws Exception {
        TestConnection old=new TestConnection("one","https://one.example.test");
        TestGroup a=old.root().add(30),b=old.root().add(20),aAgain=old.root().add(30);
        String saved=NgwResourceSelectionState.save(accounts(old),old,Arrays.asList(
                new CheckState(a.getId(),true,false),new CheckState(b.getId(),false,true),
                new CheckState(aAgain.getId(),false,true)));
        TestConnection fresh=new TestConnection("one","https://one.example.test");
        TestGroup newA=fresh.root().add(30),newB=fresh.root().add(20);
        NgwResourceSelectionState.Restored result=NgwResourceSelectionState.restore(accounts(fresh),saved,true);
        assertEquals(2,result.checks.size());
        assertEquals(newA.getId(),result.checks.get(0).getId());assertTrue(result.checks.get(0).isCheckState1());
        assertTrue(result.checks.get(0).isCheckState2());
        assertEquals(newB.getId(),result.checks.get(1).getId());assertFalse(result.checks.get(1).isCheckState1());
        assertTrue(result.checks.get(1).isCheckState2());
    }
    @Test public void sameRemoteIdInDifferentAccountsKeepsTwoResolvedLocalIds() throws Exception {
        TestConnection oldA=new TestConnection("one","https://one.example.test");
        TestConnection oldB=new TestConnection("two","https://two.example.test");
        TestGroup a=oldA.root().add(20),b=oldB.root().add(20);
        String saved=NgwResourceSelectionState.save(accounts(oldA,oldB),oldB,Arrays.asList(
                new CheckState(a.getId(),true,false),new CheckState(b.getId(),false,true)));
        TestConnection freshA=new TestConnection("one","https://one.example.test");
        TestConnection freshB=new TestConnection("two","https://two.example.test");
        TestGroup newA=freshA.root().add(20),newB=freshB.root().add(20);
        NgwResourceSelectionState.Restored result=NgwResourceSelectionState.restore(accounts(freshB,freshA),saved,true);
        assertEquals(2,result.checks.size());assertNotEquals(newA.getId(),newB.getId());
        assertEquals(newA.getId(),result.checks.get(0).getId());assertTrue(result.checks.get(0).isCheckState1());
        assertFalse(result.checks.get(0).isCheckState2());
        assertEquals(newB.getId(),result.checks.get(1).getId());assertFalse(result.checks.get(1).isCheckState1());
        assertTrue(result.checks.get(1).isCheckState2());assertSame(freshB,result.current);
    }
    @Test public void eachDuplicateStillResolvesAndMissingLaterReadRemainsFailure() throws Exception {
        TestConnection old=new TestConnection("one","https://one.example.test");
        TestGroup a=old.root().add(20),b=old.root().add(20);
        String saved=NgwResourceSelectionState.save(accounts(old),old,Arrays.asList(
                new CheckState(a.getId(),true,false),new CheckState(b.getId(),false,true)));
        TestConnection fresh=new TestConnection("one","https://one.example.test");
        fresh.root().add(20);fresh.root().missingOnChildRead=2;
        assertThrows(IOException.class,()->NgwResourceSelectionState.restore(accounts(fresh),saved,true));
        assertEquals(2,fresh.root().childReads);
    }
    @Test public void invalidLaterDuplicateFlagsRemainParseFailure() throws Exception {
        TestConnection old=new TestConnection("one","https://one.example.test");
        TestGroup a=old.root().add(20),b=old.root().add(20);
        JSONObject saved=new JSONObject(NgwResourceSelectionState.save(accounts(old),old,Arrays.asList(
                new CheckState(a.getId(),true,false),new CheckState(b.getId(),false,true))));
        saved.getJSONArray("selected").getJSONObject(1).put("vector","invalid boolean");
        TestConnection fresh=new TestConnection("one","https://one.example.test");fresh.root().add(20);
        assertThrows(JSONException.class,()->NgwResourceSelectionState.restore(accounts(fresh),saved.toString(),true));
        assertEquals(2,fresh.root().childReads);
    }
    @Test public void inactiveSavedRecordRetainsLegacyEntryBehavior() throws Exception {
        TestConnection old=new TestConnection("one","https://one.example.test");TestGroup a=old.root().add(20);
        JSONObject saved=new JSONObject(NgwResourceSelectionState.save(accounts(old),old,
                Collections.singletonList(new CheckState(a.getId(),true,true))));
        saved.getJSONArray("selected").getJSONObject(0).put("raster",false).put("vector",false);
        TestConnection fresh=new TestConnection("one","https://one.example.test");TestGroup target=fresh.root().add(20);
        NgwResourceSelectionState.Restored result=NgwResourceSelectionState.restore(accounts(fresh),saved.toString(),true);
        assertEquals(1,result.checks.size());assertEquals(target.getId(),result.checks.get(0).getId());
        assertFalse(result.checks.get(0).isCheckState1());assertFalse(result.checks.get(0).isCheckState2());
    }
}
