package com.nextgis.mobile.util;

import android.app.NotificationManager;
import android.content.Context;

/** Notification identities stay stable so an app restart can remove old progress cards. */
public final class SyncNotifications {
    public static final int MANUAL_PROGRESS_ID = 519;
    public static final int ACCOUNT_PROGRESS_ID = 520;

    private SyncNotifications() { }

    /** Called during Application startup, before either sync service exists in this process. */
    public static void clearStaleProgress(Context context) {
        cancelProgress(context, MANUAL_PROGRESS_ID);
        cancelProgress(context, ACCOUNT_PROGRESS_ID);
    }

    public static void cancelProgress(Context context, int notificationId) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId);
    }
}
