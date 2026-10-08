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
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.annotation.MainThread
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.mobile.util.AppDiagnostics
import com.nextgis.maplib.util.Constants
import com.nextgis.mobile.R
import com.nextgis.mobile.activity.MainActivity
import java.util.UUID

/** Keeps an explicitly started stakeout session audible while the app is backgrounded. */
class StakeoutForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var runningSessionId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        val session = activeSession?.takeIf { it.id == sessionId }
        if (session == null && activeSession != null) {
            // A newer owner's queued start will promote the service. An old request
            // must neither stop that owner nor acquire its GPS/audio resources.
            return START_NOT_STICKY
        }
        runningSessionId = session?.id
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            session?.foreground = true
            if (session == null) {
                // Even an obsolete framework redelivery must fulfil the foreground-start
                // contract before stopping (Android 16 otherwise crashes the process).
                // Never recreate the dead controller's GPS/audio or acquire its wake lock.
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            acquireWakeLock()
        } catch (exception: RuntimeException) {
            HyperLog.w(Constants.TAG, "Stakeout location foreground rejected", exception)
            AppDiagnostics.report(AppDiagnostics.Operation.STAKEOUT, exception)
            activeSession = null
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            // startForegroundService returns before Android invokes onStartCommand;
            // report this asynchronous rejection to the owner so it releases GPS/audio.
            session?.onFailure?.invoke()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        val session = activeSession?.takeIf { it.id == runningSessionId }
        if (session != null) {
            activeSession = null
            session.onFailure()
        }
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
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
        private const val EXTRA_SESSION_ID = "stakeout_session_id"
        private class Session(val id: String, val onFailure: () -> Unit) {
            var foreground = false
        }
        // Accessed only on the main thread by the controller and service lifecycle.
        private var activeSession: Session? = null

        @MainThread
        fun start(context: Context, onFailure: () -> Unit): String {
            check(activeSession == null) { "Stakeout already has an active foreground owner" }
            val session = Session(UUID.randomUUID().toString(), onFailure)
            activeSession = session
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, StakeoutForegroundService::class.java)
                        .putExtra(EXTRA_SESSION_ID, session.id))
            } catch (exception: RuntimeException) {
                activeSession = null
                throw exception
            }
            return session.id
        }

        @MainThread
        fun stop(context: Context, sessionId: String) {
            val session = activeSession?.takeIf { it.id == sessionId } ?: return
            activeSession = null
            // Stopping a pending FGS before its onStartCommand/promote crashes Android 16.
            // Let that queued start satisfy the contract and stop itself without resources.
            if (session.foreground) {
                context.stopService(Intent(context, StakeoutForegroundService::class.java))
            }
        }
    }
}
