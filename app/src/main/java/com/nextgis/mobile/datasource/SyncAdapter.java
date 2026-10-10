/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ****************************************************************************
 * Copyright (c) 2016-2019 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.mobile.datasource;

import static android.content.Context.MODE_MULTI_PROCESS;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.service.NGWSyncService;
import com.nextgis.maplib.util.AccountUtil;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.util.NotificationHelper;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.SyncRecoveryJournal;

import static com.nextgis.maplib.util.Constants.MESSAGE_ALERT_INTENT;
import static com.nextgis.maplib.util.Constants.MESSAGE_EXTRA;
import static com.nextgis.maplib.util.Constants.MESSAGE_TITLE_EXTRA;
import static com.nextgis.maplibui.util.NotificationHelper.createBuilder;
import static com.nextgis.mobile.util.OfflineSyncIntentService.EXTRA_PROJECT_OPERATION_ALREADY_HELD;

public class SyncAdapter extends com.nextgis.maplib.datasource.ngw.SyncAdapter {
    private static final int NOTIFICATION_ID = 517;
    private static final int ERROR_NOTIFICATION_ID = 518;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String authority, ContentProviderClient contentProviderClient, SyncResult syncResult) {

        if (com.nextgis.maplib.util.SyncWorkspaceSession.current() == null) {
            com.nextgis.mobile.util.ProjectSyncRunner.run(getContext(),
                    java.util.Collections.singletonList(account), bundle, authority,
                    contentProviderClient, syncResult);
            return;
        }

//        Log.e("RRFRSH", "SyncAdapter datasource - onPerformSync for " + account.name);

        Log.d("SSYNC", "SyncAdapter/datasource  onPerformSync account - " + account.name);

        boolean operationAlreadyHeld = bundle != null
                && bundle.getBoolean(EXTRA_PROJECT_OPERATION_ALREADY_HELD, false);
        ProjectOperationCoordinator.Lease operationLease = operationAlreadyHeld
                ? null
                : ProjectOperationCoordinator.tryBegin(
                        getContext(), ProjectOperationCoordinator.Kind.DATA_SYNC);
        if (!operationAlreadyHeld && operationLease == null) {
            HyperLog.v(Constants.TAG,
                    "onPerformSync skipped (project operation in progress) for " + account.name);
            sendSyncFinishBroadcast();
            return;
        }

        try {
            if(!AccountUtil.isUserExists(getContext())) {
                syncResult.stats.numAuthExceptions++;
                HyperLog.v(Constants.TAG, "onPerformSync for" + account.name + " exit cos !AccountUtil.isUserExists");
                String alertMessage = getContext().getString(com.nextgis.maplibui.R.string.sync_need_login);
                String alertTitle = getContext().getString(com.nextgis.maplibui.R.string.sync_off_title);
                Intent msg = new Intent(MESSAGE_ALERT_INTENT);
                msg.putExtra(MESSAGE_EXTRA, alertMessage);
                msg.putExtra(MESSAGE_TITLE_EXTRA, alertTitle);
                msg.setPackage(getContext().getPackageName());
                getContext().sendBroadcast(msg);
                sendSyncFinishBroadcast();
                return;
            }

            IGISApplication gisApp = (IGISApplication) getContext().getApplicationContext();
            if (!gisApp.repairProjectIntegrityBeforeSync(account.name)) {
                syncResult.stats.numConflictDetectedExceptions++;
                Intent blocked = new Intent(MESSAGE_ALERT_INTENT);
                blocked.putExtra(MESSAGE_TITLE_EXTRA, getContext().getString(
                        com.nextgis.maplib.R.string.sync_project_repair_blocked_title));
                blocked.putExtra(MESSAGE_EXTRA, getContext().getString(
                        com.nextgis.maplib.R.string.sync_project_repair_blocked_message));
                blocked.setPackage(getContext().getPackageName());
                getContext().sendBroadcast(blocked);
                sendSyncFinishBroadcast();
                return;
            }

            if (!super.isSomeToSync(account)) {
                sendSyncFinishBroadcast();
                return;
            }

            if (gisApp.isLayerFillServiceBusy() && !operationAlreadyHeld) {
                HyperLog.v(Constants.TAG, "onPerformSync skipped (layer fill in progress) for " + account.name);
                sendSyncFinishBroadcast();
                return;
            }

            sendNotification(getContext(), SYNC_START, null);

            Log.d("SSYNC", "super.onPerformSync for " + account.name);

            gisApp.setLayerFillBatchDeferringHeavyMapReload(true);
            try {
                super.onPerformSync(account, bundle, authority, contentProviderClient, syncResult);
            } finally {
                // Pulling and MapLibre GeoJSON rebuilding the same layer at once caused the largest
                // observed native-memory peak. Flush one consolidated visible-layer reload only
                // after all SQLite work for the account has finished.
                gisApp.setLayerFillBatchDeferringHeavyMapReload(false);
                gisApp.requestMapReloadAfterLayerFillBatch();
            }

            if (isCanceled())
                sendNotification(getContext(), SYNC_CANCELED, null);
            else if (syncResult.hasError() && !TextUtils.isEmpty(mError))
                sendNotification(getContext(), SYNC_CHANGES, mError);
            else
                sendNotification(getContext(), SYNC_FINISH, null);
        } finally {
            if (operationLease != null) {
                operationLease.close();
            }
        }

//        Log.e("RRFRSH", "SyncAdapter datasource - onPerformSync end");
    }

    private void sendSyncFinishBroadcast() {
        if (com.nextgis.maplib.util.SyncWorkspaceSession.current() != null) return;
        NGWSyncService.markSyncFinished();
        Intent finish = new Intent(SYNC_FINISH);
        finish.setPackage(getContext().getPackageName());
        getContext().sendBroadcast(finish);
    }

    public void sendNotification(
            Context context,
            String notificationType,
            String message)
    {
        if (com.nextgis.maplib.util.SyncWorkspaceSession.current() != null) return;
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;
        boolean failed = SYNC_CHANGES.equals(notificationType);
        if (SYNC_FINISH.equals(notificationType)) notificationManager.cancel(ERROR_NOTIFICATION_ID);
        if (!failed && !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(AppSettingsConstants.KEY_PREF_SHOW_SYNC, false)) {
            notificationManager.cancel(NOTIFICATION_ID);
            return;
        }

        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = createBuilder(context, com.nextgis.maplibui.R.string.sync);
        builder.setSmallIcon(R.drawable.ic_action_sync)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOngoing(false)
        ;

        Bitmap largeIcon = NotificationHelper.getLargeIcon(R.drawable.ic_action_sync, context.getResources());
        switch (notificationType) {
            case SYNC_START:
                largeIcon = NotificationHelper.getLargeIcon(com.nextgis.maplibui.R.drawable.ic_next_dark, context.getResources());
                builder.setProgress(0, 0, true)
                        .setTicker(context.getString(com.nextgis.maplib.R.string.sync_started))
                        .setContentTitle(context.getString(com.nextgis.maplib.R.string.synchronization))
                        .setContentText(context.getString(com.nextgis.maplib.R.string.sync_progress));
                break;

            case SYNC_FINISH:
                largeIcon = NotificationHelper.getLargeIcon(com.nextgis.maplibui.R.drawable.ic_action_apply_dark, context.getResources());
                builder.setProgress(0, 0, false)
                        .setTicker(context.getString(com.nextgis.maplib.R.string.sync_finished))
                        .setContentTitle(context.getString(com.nextgis.maplib.R.string.synchronization))
                        .setContentText(context.getString(com.nextgis.maplib.R.string.sync_finished));
                break;

            case SYNC_CANCELED:
                largeIcon = NotificationHelper.getLargeIcon(com.nextgis.maplibui.R.drawable.ic_action_cancel_dark, context.getResources());
                builder.setProgress(0, 0, false)
                        .setTicker(context.getString(com.nextgis.maplib.R.string.sync_canceled))
                        .setContentTitle(context.getString(com.nextgis.maplib.R.string.synchronization))
                        .setContentText(context.getString(com.nextgis.maplib.R.string.sync_canceled));
                break;

            case SYNC_CHANGES:
                largeIcon = NotificationHelper.getLargeIcon(
                        com.nextgis.maplibui.R.drawable.ic_action_information_light, context.getResources());
                builder.setProgress(0, 0, false)
                        .setOnlyAlertOnce(true)
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .setContentTitle(context.getString(R.string.sync_failed_notification_title))
                        .setContentText(context.getString(R.string.sync_failed_notification_message))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(
                                context.getString(R.string.sync_failed_notification_message)));
                break;
            default:
                return;
        }

        builder.setLargeIcon(largeIcon);
        if (failed) notificationManager.cancel(NOTIFICATION_ID);
        notificationManager.notify(failed ? ERROR_NOTIFICATION_ID : NOTIFICATION_ID, builder.build());
    }
}
