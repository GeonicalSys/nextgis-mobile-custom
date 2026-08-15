/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Copyright (c) 2026 GeonicalSystem
 */
package com.nextgis.mobile.stakeout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nextgis.mobile.R
import com.nextgis.mobile.activity.MainActivity

/** Keeps an explicitly started stakeout session audible while the app is backgrounded. */
class StakeoutForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:stakeout"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun buildNotification(): android.app.Notification {
        createNotificationChannel()
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, notificationChannelId())
            .setSmallIcon(R.drawable.ic_stakeout)
            .setContentTitle(getString(R.string.stakeout_background_title))
            .setContentText(getString(R.string.stakeout_background_text))
            .setContentIntent(openAppIntent)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            notificationChannelId(),
            getString(R.string.stakeout_background_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.stakeout_background_channel_summary)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun notificationChannelId(): String = "$packageName.stakeout"

    companion object {
        private const val NOTIFICATION_ID = 18_405

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StakeoutForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StakeoutForegroundService::class.java))
        }
    }
}
