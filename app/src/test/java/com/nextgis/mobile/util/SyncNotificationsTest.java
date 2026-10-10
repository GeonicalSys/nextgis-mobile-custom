package com.nextgis.mobile.util;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import com.nextgis.maplib.service.NGWSyncService;
import com.nextgis.maplib.util.NgwSyncProgress;
import com.nextgis.mobile.R;
import com.nextgis.mobile.datasource.SyncAdapter;
import com.nextgis.mobile.datasource.SyncService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {26, 36})
@LooperMode(LooperMode.Mode.PAUSED)
public class SyncNotificationsTest {
    private Context context;
    private NotificationManager manager;
    private ServiceController<?> controller;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        manager = context.getSystemService(NotificationManager.class);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(AppSettingsConstants.KEY_PREF_SHOW_SYNC).commit();
        NgwSyncProgress.beginSession(context, 1);
        NgwSyncProgress.finishSession();
        manager.cancelAll();
    }

    @After public void tearDown() {
        if (controller != null) controller.destroy();
        NgwSyncProgress.finishSession();
        NGWSyncService.markSyncFinished();
        manager.cancelAll();
    }

    @Test public void inactiveProgressCannotRecreateRemovedManualNotification() throws Exception {
        ServiceController<OfflineSyncIntentService> service =
                Robolectric.buildService(OfflineSyncIntentService.class).create();
        controller = service;
        BroadcastReceiver receiver = receiver(service.get(), "mProgressReceiver");
        // Reproduce the phone sequence: final progress is pending when foreground cleanup removes it.
        NgwSyncProgress.beginSession(context, 1);
        NgwSyncProgress.finishSession();
        manager.cancel(SyncNotifications.MANUAL_PROGRESS_ID);
        receiver.onReceive(context, new Intent(NgwSyncProgress.SYNC_PROGRESS));
        assertNull(shadowOf(manager).getNotification(SyncNotifications.MANUAL_PROGRESS_ID));
    }

    @Test public void destroyedManualReceiverCannotPostDuringNextSession() throws Exception {
        ServiceController<OfflineSyncIntentService> service =
                Robolectric.buildService(OfflineSyncIntentService.class).create();
        BroadcastReceiver receiver = receiver(service.get(), "mProgressReceiver");
        service.destroy();
        NgwSyncProgress.beginSession(context, 1);
        receiver.onReceive(context, new Intent(NgwSyncProgress.SYNC_PROGRESS));
        assertNull(shadowOf(manager).getNotification(SyncNotifications.MANUAL_PROGRESS_ID));
    }

    @Test public void destroyedAccountReceiverCannotStartForegroundForNextWorker() throws Exception {
        ServiceController<SyncService> service = Robolectric.buildService(SyncService.class).create();
        BroadcastReceiver receiver = receiver(service.get(), "mForegroundReceiver");
        service.destroy();
        NGWSyncService.markSyncStarted();
        NgwSyncProgress.beginSession(context, 1);
        receiver.onReceive(context, new Intent(SyncAdapter.SYNC_START));
        receiver.onReceive(context, new Intent(NgwSyncProgress.SYNC_PROGRESS));
        assertNull(shadowOf(manager).getNotification(SyncNotifications.ACCOUNT_PROGRESS_ID));
    }

    @Test public void startupRemovesOnlyStaleServiceProgress() {
        Notification placeholder = new Notification.Builder(context).setSmallIcon(R.drawable.ic_action_sync).build();
        manager.notify(SyncNotifications.MANUAL_PROGRESS_ID, placeholder);
        manager.notify(SyncNotifications.ACCOUNT_PROGRESS_ID, placeholder);
        manager.notify(518, placeholder);
        manager.notify(695, placeholder);
        SyncNotifications.clearStaleProgress(context);
        assertNull(shadowOf(manager).getNotification(SyncNotifications.MANUAL_PROGRESS_ID));
        assertNull(shadowOf(manager).getNotification(SyncNotifications.ACCOUNT_PROGRESS_ID));
        assertNotNull(shadowOf(manager).getNotification(518));
        assertNotNull(shadowOf(manager).getNotification(695));
    }

    @Test public void defaultShowsOnlyFailureAndSuccessfulRetryClearsIt() {
        SyncAdapter adapter = new SyncAdapter(context, true);
        for (String type : new String[]{SyncAdapter.SYNC_START, SyncAdapter.SYNC_FINISH, SyncAdapter.SYNC_CANCELED}) {
            adapter.sendNotification(context, type, null);
            assertNull(shadowOf(manager).getNotification(517));
        }
        adapter.sendNotification(context, SyncAdapter.SYNC_CHANGES, "HTTP 503 password=synthetic https://example.test");
        Notification failure = shadowOf(manager).getNotification(518);
        assertNotNull(failure);
        assertEquals(context.getString(R.string.sync_failed_notification_title),
                failure.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(context.getString(R.string.sync_failed_notification_message),
                failure.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(0, failure.flags & Notification.FLAG_ONGOING_EVENT);
        adapter.sendNotification(context, SyncAdapter.SYNC_START, null);
        adapter.sendNotification(context, SyncAdapter.SYNC_CANCELED, null);
        assertNotNull(shadowOf(manager).getNotification(518));
        adapter.sendNotification(context, SyncAdapter.SYNC_FINISH, null);
        assertNull(shadowOf(manager).getNotification(518));
    }

    @Test public void explicitPreferenceKeepsOptionalMessages() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(AppSettingsConstants.KEY_PREF_SHOW_SYNC, true).commit();
        SyncAdapter adapter = new SyncAdapter(context, true);
        adapter.sendNotification(context, SyncAdapter.SYNC_FINISH, null);
        Notification completed = shadowOf(manager).getNotification(517);
        assertNotNull(completed);
        assertEquals(context.getString(com.nextgis.maplib.R.string.sync_finished),
                completed.extras.getString(Notification.EXTRA_TEXT));
    }

    private static BroadcastReceiver receiver(Object service, String name) throws Exception {
        Field field = service.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (BroadcastReceiver) field.get(service);
    }
}
