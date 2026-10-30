package com.nextgis.mobile.util;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SyncResultUtil;
import com.nextgis.mobile.R;
import com.nextgis.mobile.datasource.SyncAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetworkInfo;
import java.util.Arrays;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Actual dialog Intent factory and Notification builder; no Activity/map/SQLite/socket. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class SyncFailurePresentationTest {
    private Context context;
    private NotificationManager manager;
    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication(); manager = context.getSystemService(NotificationManager.class);
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(AppSettingsConstants.KEY_PREF_SHOW_SYNC).commit();
        connected(true); manager.cancelAll();
    }
    @After public void cleanup() { SyncResultUtil.endSync(); manager.cancelAll(); }
    private void connected(boolean on) {
        NetworkInfo info = on ? ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, true) : null;
        shadowOf(context.getSystemService(ConnectivityManager.class)).setActiveNetworkInfo(info);
    }
    private int captured(boolean serverParse) {
        SyncResult result = new SyncResult(); SyncResultUtil.beginSync(result);
        if (serverParse) { result.stats.numParseExceptions++; SyncResultUtil.recordServerParseFailureReason(result); }
        else SyncResultUtil.markServerTemporarilyUnavailable(result);
        Bundle bundle = new Bundle(); SyncResultUtil.exportAccountFailureSnapshot(bundle, result); SyncResultUtil.endSync();
        return SyncResultUtil.readAccountFailureReasons(bundle, result);
    }
    private Notification notifyReason(int flags) {
        SyncAdapter adapter = new SyncAdapter(context, true);
        adapter.sendFailureNotification(context, SyncAdapter.failurePresentation(context, flags));
        Notification notification = shadowOf(manager).getNotification(518); assertNotNull(notification); return notification;
    }
    private void generic(Notification notification) {
        assertEquals(context.getString(R.string.sync_failed_notification_title),notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(context.getString(R.string.sync_failed_notification_message),notification.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test public void typedServerIoSurvivesClosedScopeIntoActualNotification() {
        Notification n = notifyReason(captured(false));
        assertEquals(context.getString(R.string.sync_webgis_server_error_title), n.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(context.getString(com.nextgis.maplib.R.string.error_server_temporarily_unavailable), n.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(context.getString(com.nextgis.maplib.R.string.error_server_temporarily_unavailable), n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString());
        assertEquals(0, n.flags & Notification.FLAG_ONGOING_EVENT);
    }

    @Test public void typedLookupParseUsesTheSameHumanServerExplanation() {
        Notification n = notifyReason(captured(true));
        assertEquals(context.getString(com.nextgis.maplib.R.string.error_server_temporarily_unavailable),n.extras.getString(Notification.EXTRA_TEXT));
    }

    @Test public void manualServerAlertListsProjectsAndCarriesOnlySafeStrings() {
        SyncAdapter.FailurePresentation presentation = SyncAdapter.failurePresentation(context,captured(false));
        Intent intent = SyncAdapter.buildFailureAlert(context,presentation,Arrays.asList("First", "Second"));
        assertEquals(Constants.MESSAGE_ALERT_INTENT,intent.getAction()); assertEquals(context.getPackageName(),intent.getPackage());
        assertEquals(context.getString(R.string.sync_webgis_server_error_title),intent.getStringExtra(Constants.MESSAGE_TITLE_EXTRA));
        assertEquals(context.getString(com.nextgis.maplib.R.string.error_server_temporarily_unavailable)+"\n\n"+
                context.getString(R.string.sync_failed_projects_list,"First, Second"),intent.getStringExtra(Constants.MESSAGE_EXTRA));
        Bundle extras = intent.getExtras(); assertEquals(2,extras.size());
        for (String key : extras.keySet()) assertTrue(extras.get(key) instanceof String);
    }

    @Test public void mixedServerAndLocalFailuresUseExistingGenericCopy() {
        generic(notifyReason(SyncResultUtil.FAILURE_SERVER | SyncResultUtil.FAILURE_OTHER));
        generic(notifyReason(SyncResultUtil.FAILURE_SERVER | SyncResultUtil.FAILURE_CONNECT));
        generic(notifyReason(SyncResultUtil.FAILURE_SERVER | SyncResultUtil.FAILURE_OFFLINE));
    }

    @Test public void unknownEmptyAuthAndUnclassifiedErrorsDoNotClaimMaintenance() {
        for (int flags : new int[]{0,8,32,36}) generic(notifyReason(flags));
        SyncResult result = new SyncResult(); result.stats.numAuthExceptions = 1;
        generic(notifyReason(SyncResultUtil.readAccountFailureReasons(new Bundle(),result)));
    }

    @Test public void offlineFallbackWinsEvenOverAStoredServerPresentation() {
        SyncAdapter adapter = new SyncAdapter(context,true);
        SyncAdapter.FailurePresentation presentation = SyncAdapter.failurePresentation(context,SyncResultUtil.FAILURE_SERVER);
        connected(false); adapter.sendFailureNotification(context,presentation);
        assertEquals(context.getString(R.string.sync_no_internet),
                shadowOf(manager).getNotification(518).extras.getString(Notification.EXTRA_TEXT));
    }

    @Test public void oldRawMessageApiStillSuppressesStatusUrlAndPassword() {
        String raw = "HTTP 503 password=technical https://example.test/private";
        new SyncAdapter(context,true).sendNotification(context,SyncAdapter.SYNC_CHANGES,raw);
        Notification n = shadowOf(manager).getNotification(518); generic(n);
        assertFalse(n.extras.getString(Notification.EXTRA_TEXT).contains(raw));
    }

    @Test public void nullTypedPresentationKeepsGenericAndOfflineFallback() {
        SyncAdapter adapter = new SyncAdapter(context,true); adapter.sendFailureNotification(context,null);
        generic(shadowOf(manager).getNotification(518)); connected(false);adapter.sendFailureNotification(context,null);
        assertEquals(context.getString(R.string.sync_no_internet),shadowOf(manager).getNotification(518).extras.getString(Notification.EXTRA_TEXT));
    }

    @Test public void manualAlertRechecksConnectivityBeforeShowingAStoredServerPresentation() {
        SyncAdapter.FailurePresentation presentation = SyncAdapter.failurePresentation(context,SyncResultUtil.FAILURE_SERVER);
        connected(false);
        Intent intent = SyncAdapter.buildFailureAlert(context,presentation,Arrays.asList("First"));
        assertEquals(context.getString(R.string.sync_failed_notification_title),intent.getStringExtra(Constants.MESSAGE_TITLE_EXTRA));
        assertEquals(context.getString(R.string.sync_no_internet)+"\n\n"+
                context.getString(R.string.sync_failed_projects_list,"First"),intent.getStringExtra(Constants.MESSAGE_EXTRA));
        assertFalse(intent.getStringExtra(Constants.MESSAGE_EXTRA).contains(
                context.getString(com.nextgis.maplib.R.string.error_server_temporarily_unavailable)));
    }

    @Test public void genericManualAlertKeepsExistingProjectList() {
        Intent intent = SyncAdapter.buildFailureAlert(context,SyncAdapter.failurePresentation(context,SyncResultUtil.FAILURE_OTHER),Arrays.asList("First"));
        assertEquals(context.getString(R.string.project_sync_incomplete_title),intent.getStringExtra(Constants.MESSAGE_TITLE_EXTRA));
        assertEquals(context.getString(R.string.project_sync_incomplete,"First"),intent.getStringExtra(Constants.MESSAGE_EXTRA));
    }

    @Test public void cancelDoesNotReplaceErrorAndSuccessfulRetryClearsIt() {
        notifyReason(SyncResultUtil.FAILURE_SERVER); SyncAdapter adapter = new SyncAdapter(context,true);
        adapter.sendNotification(context,SyncAdapter.SYNC_CANCELED,null); assertNotNull(shadowOf(manager).getNotification(518));
        assertNull(shadowOf(manager).getNotification(517));
        adapter.sendNotification(context,SyncAdapter.SYNC_FINISH,null); assertNull(shadowOf(manager).getNotification(518));
    }

    @Test public void explicitOptionalProgressPreferenceRemainsEffective() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(AppSettingsConstants.KEY_PREF_SHOW_SYNC,true).commit();
        new SyncAdapter(context,true).sendNotification(context,SyncAdapter.SYNC_FINISH,null);
        assertNotNull(shadowOf(manager).getNotification(517));
    }

    @Test @Config(qualifiers = "ru") public void russianCopyExplainsPossibleMaintenanceAndNextAction() {
        Notification n = notifyReason(captured(false));
        assertEquals("Ошибка на стороне ВебГИС",n.extras.getString(Notification.EXTRA_TITLE));
        String message = n.extras.getString(Notification.EXTRA_TEXT);
        assertTrue(message.contains("Возможно")); assertTrue(message.contains("технические работы"));
        assertTrue(message.contains("Попробуйте позже"));
        assertFalse(message.contains("HTTP")); assertFalse(message.contains("http"));
    }
}
