package com.nextgis.maplib.datasource.ngw;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplibui.dialog.NGWResourcesListAdapter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

/** Actual child parser and async background-to-owned-copy path with a technical transport double. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE, application=Application.class, qualifiers="ru",
        shadows=ResourceChildrenMessagesTest.HttpTransport.class)
public class ResourceChildrenMessagesTest {
    @Implements(value=NetworkUtil.class, isInAndroidSdk=false)
    public static class HttpTransport {
        static HttpResponse next;
        static IOException failure;
        static int calls;
        @Implementation protected static HttpResponse get(String url, String login,
                String password, boolean followRedirects) throws IOException {
            calls++;
            if (failure != null) throw failure;
            if (next == null) throw new AssertionError("Unexpected technical HTTP call");
            return next;
        }
    }
    private Context context;
    private Connection connection;
    @Before public void setUp() {
        context=RuntimeEnvironment.getApplication();
        connection=new Connection("technical connection", "technical-login", "technical-password",
                "https://example.test");
        HttpTransport.calls=0;HttpTransport.failure=null;HttpTransport.next=null;
    }
    private void response(int code,String body) {
        HttpTransport.next=new HttpResponse(code,"technical response",body);
        HttpTransport.next.setOk(code>=200&&code<300);
        HttpTransport.failure=null;HttpTransport.calls=0;
    }
    private ResourceGroup group() { return new ResourceGroup(7,connection); }
    private String serverCopy() {
        return "ВебГИС временно не может выполнить запрос. Возможно, ведутся технические работы или сервер перегружен. Попробуйте позже.";
    }
    private String background(INGWResource resource) throws Exception {
        Class<?> type=Class.forName("com.nextgis.maplibui.dialog.NGWResourcesListAdapter$NGWResourceAsyncTask");
        Constructor<?> ctor=type.getDeclaredConstructor(NGWResourcesListAdapter.class,
                Context.class,INGWResource.class,boolean.class,Activity.class);
        ctor.setAccessible(true);
        Object task=ctor.newInstance(null,context,resource,true,null);
        Method method=type.getDeclaredMethod("doInBackground",Void[].class);
        method.setAccessible(true);
        return (String)method.invoke(task,(Object)new Void[0]);
    }
    @Test public void genuineEmptySuccessIsLoadedCachedAndHasNoUserError() throws Exception {
        response(200,"[]");ResourceGroup group=group();
        assertEquals("",background(group));assertTrue(group.isChildrenLoaded());
        assertEquals(0,group.getChildrenCount());assertEquals(1,HttpTransport.calls);
        assertTrue(group.loadChildrenWithResult(true).isSuccessful());
        group.loadChildren(true);assertEquals(1,HttpTransport.calls);
    }
    @Test public void serverFailuresReachTheExistingAsyncPathAsSafeWebgisCopy() throws Exception {
        for(int code:new int[]{429,500,503,599}) {
            response(code,"<html>HTTP password=technical https://example.test/private</html>");
            ResourceGroup group=group();
            assertEquals(serverCopy(),background(group));assertFalse(group.isChildrenLoaded());
            assertEquals(1,HttpTransport.calls);
        }
    }
    @Test public void authAndRightsRemainDistinctEvenWithDatabaseWordsInTheBody() throws Exception {
        for(int code:new int[]{401,403}) {
            response(code,"ExternalDatabaseError secret=technical");
            String expected=context.getString(code==401?com.nextgis.maplib.R.string.error_401
                    :com.nextgis.maplib.R.string.error_403);
            ResourceGroup group=group();assertEquals(expected,background(group));
            assertFalse(group.isChildrenLoaded());
        }
    }
    @Test public void plainValidationMissingResourceAndTimeoutDoNotPretendToBeMaintenance() throws Exception {
        for(int code:new int[]{404,408,422}) {
            response(code,code==404?"ExternalDatabaseError":"validation");
            assertEquals(context.getString(com.nextgis.maplibui.R.string.resource_list_load_failed),
                    background(group()));
        }
    }
    @Test public void known422ExternalDatabaseFailureUsesTheSameServerCopy() throws Exception {
        response(422,"ExternalDatabaseError");
        assertEquals(serverCopy(),background(group()));
    }
    @Test public void malformedJsonAndIoFailureReturnHumanRetryCopyWithoutRawText() throws Exception {
        response(200,"{broken JSON https://example.test/private}");ResourceGroup group=group();
        assertEquals(context.getString(com.nextgis.maplibui.R.string.resource_list_load_failed),background(group));
        assertFalse(group.isChildrenLoaded());
        HttpTransport.failure=new IOException("Q:\\private\\technical.db HTTP 503 password=technical");
        assertEquals(context.getString(com.nextgis.maplibui.R.string.resource_list_load_failed),background(group()));
    }
    @Test public void legacyVoidFailureStillDoesNotThrowOrMarkLoaded() {
        response(503,"temporary failure");ResourceGroup group=group();
        group.loadChildren(true);assertFalse(group.isChildrenLoaded());assertEquals(1,HttpTransport.calls);
    }
    @Test public void structuredConnectionDelegatesItsRootAndMissingRootIsNotSuccess() {
        ResourceGroup.ChildrenLoadResult missing=connection.loadChildrenWithResult(true);
        assertFalse(missing.isSuccessful());assertFalse(missing.isTemporaryServerFailure());
        response(200,"[]");connection.mRootResource=group();
        assertTrue(connection.loadChildrenWithResult(true).isSuccessful());
        assertEquals(1,HttpTransport.calls);connection.loadChildren(true);assertEquals(1,HttpTransport.calls);
    }
    @Test public void technicalChildrenKeepExistingGroupFirstThenCaseInsensitiveOrder() {
        response(200,"["+item(11,"zeta","lookup_table")+","+item(12,"beta","resource_group")
                +","+item(13,"Alpha","lookup_table")+","+item(14,"aardvark","resource_group")+"]");
        ResourceGroup group=group();assertTrue(group.loadChildrenWithResult(true).isSuccessful());
        assertEquals(4,group.getChildrenCount());
        assertEquals("aardvark",group.getChild(0).getName());assertEquals("beta",group.getChild(1).getName());
        assertEquals("Alpha",group.getChild(2).getName());assertEquals("zeta",group.getChild(3).getName());
        assertEquals(1,HttpTransport.calls);
    }
    private static String item(int id,String name,String type) {
        return "{\"resource\":{\"id\":"+id+",\"display_name\":\""+name+"\",\"cls\":\""+type
                +"\",\"children\":false}}";
    }
}
