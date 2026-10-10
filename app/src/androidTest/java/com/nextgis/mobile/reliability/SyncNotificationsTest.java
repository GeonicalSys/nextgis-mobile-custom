package com.nextgis.mobile.reliability;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.service.NGWSyncService;
import com.nextgis.maplib.util.NgwSyncProgress;
import com.nextgis.mobile.R;
import com.nextgis.mobile.datasource.SyncAdapter;
import com.nextgis.mobile.datasource.SyncService;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.SyncNotifications;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Real Android notification/service lifecycle; isolated emulator, no accounts or network requests. */
@RunWith(AndroidJUnit4.class)
public class SyncNotificationsTest {
    private Context context;
    private NotificationManager manager;
    private SharedPreferences preferences;
    private boolean hadPreference, previousPreference, bound;
    private ServiceConnection connection;

    @Before public void setUp() {
        assertTrue("Use an isolated emulator", Build.HARDWARE.equals("ranchu")
                || Build.HARDWARE.equals("goldfish"));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getPackageName().endsWith(".debug"));
        manager = context.getSystemService(NotificationManager.class);
        assertTrue("Grant POST_NOTIFICATIONS to the synthetic test app", manager.areNotificationsEnabled());
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        hadPreference = preferences.contains(AppSettingsConstants.KEY_PREF_SHOW_SYNC);
        previousPreference = preferences.getBoolean(AppSettingsConstants.KEY_PREF_SHOW_SYNC, false);
        preferences.edit().remove(AppSettingsConstants.KEY_PREF_SHOW_SYNC).commit();
        manager.cancel(517);
        manager.cancel(518);
        SyncNotifications.clearStaleProgress(context);
    }

    @After public void tearDown() {
        if (context == null) return;
        NgwSyncProgress.finishSession();
        NGWSyncService.markSyncFinished();
        context.sendBroadcast(new Intent(SyncAdapter.SYNC_FINISH).setPackage(context.getPackageName()));
        if (bound) context.unbindService(connection);
        SyncNotifications.clearStaleProgress(context);
        manager.cancel(517);
        manager.cancel(518);
        if (hadPreference) preferences.edit().putBoolean(
                AppSettingsConstants.KEY_PREF_SHOW_SYNC, previousPreference).commit();
        else preferences.edit().remove(AppSettingsConstants.KEY_PREF_SHOW_SYNC).commit();
    }

    @Test public void defaultPostsOnlyReadableErrorAndSuccessRemovesIt() throws Exception {
        SyncAdapter adapter = new SyncAdapter(context, true);
        for (String type : new String[]{SyncAdapter.SYNC_START, SyncAdapter.SYNC_FINISH, SyncAdapter.SYNC_CANCELED}) {
            adapter.sendNotification(context, type, null);
        }
        Thread.sleep(250);
        assertNull(notification(517));
        adapter.sendNotification(context, SyncAdapter.SYNC_CHANGES, "HTTP 503 https://example.test password=synthetic");
        awaitNotification(518, true);
        Notification failure = notification(518);
        assertEquals(context.getString(R.string.sync_failed_notification_title),
                failure.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(context.getString(R.string.sync_failed_notification_message),
                failure.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(0, failure.flags & Notification.FLAG_ONGOING_EVENT);
        adapter.sendNotification(context, SyncAdapter.SYNC_FINISH, null);
        awaitNotification(518, false);
    }

    @Test public void startupClearsOrphanedProgressButKeepsError() throws Exception {
        String channel = "synthetic_sync_cleanup";
        manager.createNotificationChannel(new NotificationChannel(channel, "Test", NotificationManager.IMPORTANCE_LOW));
        Notification placeholder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_action_sync).setContentTitle("Synthetic progress").build();
        manager.notify(SyncNotifications.MANUAL_PROGRESS_ID, placeholder);
        manager.notify(SyncNotifications.ACCOUNT_PROGRESS_ID, placeholder);
        manager.notify(518, placeholder);
        awaitNotification(SyncNotifications.MANUAL_PROGRESS_ID, true);
        awaitNotification(SyncNotifications.ACCOUNT_PROGRESS_ID, true);
        awaitNotification(518, true);
        SyncNotifications.clearStaleProgress(context);
        awaitNotification(SyncNotifications.MANUAL_PROGRESS_ID, false);
        awaitNotification(SyncNotifications.ACCOUNT_PROGRESS_ID, false);
        assertNotNull(notification(518));
        manager.deleteNotificationChannel(channel);
    }

    @Test public void accountServiceFinishCancelAndDelayedProgressLeaveNoCard() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) { connected.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        bound = context.bindService(new Intent(context, SyncService.class), connection, Context.BIND_AUTO_CREATE);
        assertTrue(bound);
        assertTrue("Sync adapter binder", connected.await(5, TimeUnit.SECONDS));
        for (String finish : new String[]{SyncAdapter.SYNC_FINISH, SyncAdapter.SYNC_CANCELED}) {
            NgwSyncProgress.beginSession(context, 1);
            NGWSyncService.markSyncStarted();
            assertTrue("Synthetic worker is active", NGWSyncService.isSyncStarted());
            context.sendBroadcast(new Intent(SyncAdapter.SYNC_START).setPackage(context.getPackageName()));
            // Android 12+ may defer the visible card for a newly started foreground service.
            awaitNotification(SyncNotifications.ACCOUNT_PROGRESS_ID, true, 15000);
            assertNotEquals(0, notification(SyncNotifications.ACCOUNT_PROGRESS_ID).flags & Notification.FLAG_FOREGROUND_SERVICE);
            if (finish.equals(SyncAdapter.SYNC_CANCELED)) NgwSyncProgress.cancel();
            else NgwSyncProgress.finishSession();
            NGWSyncService.markSyncFinished();
            context.sendBroadcast(new Intent(finish).setPackage(context.getPackageName()));
            awaitNotification(SyncNotifications.ACCOUNT_PROGRESS_ID, false);
            context.sendBroadcast(new Intent(NgwSyncProgress.SYNC_PROGRESS).setPackage(context.getPackageName()));
            context.sendBroadcast(new Intent(SyncAdapter.SYNC_START).setPackage(context.getPackageName()));
            Thread.sleep(300);
            assertNull(notification(SyncNotifications.ACCOUNT_PROGRESS_ID));
        }
    }

    private Notification notification(int id) {
        for (StatusBarNotification item : manager.getActiveNotifications()) {
            if (item.getId() == id) return item.getNotification();
        }
        return null;
    }

    private void awaitNotification(int id, boolean present) throws Exception {
        awaitNotification(id, present, 5000);
    }

    private void awaitNotification(int id, boolean present, long timeoutMs) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs;
        while ((notification(id) != null) != present && android.os.SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(30);
        }
        assertEquals("notification " + id, present, notification(id) != null);
    }
}
