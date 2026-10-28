package com.nextgis.mobile.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.PermissionUtil
import com.nextgis.mobile.R
import com.nextgis.mobile.MainApplication
import com.nextgis.mobile.activity.MainActivity
import com.nextgis.mobile.util.DiagnosticsBackgroundLocationStart
import java.util.UUID

/** One non-recording owner of the shared GPS stream, with a location foreground notification. */
class BackgroundLocationService : Service() {
    private var owner: Session? = null
    private val handler = Handler(Looper.getMainLooper())
    private val permissionCheck = object : Runnable {
        override fun run() {
            if (!PermissionUtil.hasAnyLocationPermission(this@BackgroundLocationService)) {
                stop(this@BackgroundLocationService)
                return
            }
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getStringExtra(SESSION_ID)
        val current = session?.takeIf { it.id == requested }
        if (current == null && session != null) return START_NOT_STICKY
        owner = current
        var diagnosticStage = "notification"
        try {
            val notification = notification()
            diagnosticStage = "start_foreground"
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(NOTIFICATION_ID, notification)
            current?.promoted = true
            if (current == null || !PermissionUtil.hasAnyLocationPermission(this)) {
                // Fulfil a pending FGS launch even if setting/timeout cancelled its owner.
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            diagnosticStage = "acquire_background_updates"
            (application as IGISApplication).gpsEventSource.acquireBackgroundUpdates(current)
            HyperLog.i(Constants.TAG, "Background GPS started owner=${current.id}")
            diagnosticStage = "schedule_permission_check"
            handler.removeCallbacks(permissionCheck)
            handler.post(permissionCheck)
        } catch (error: RuntimeException) {
            HyperLog.w(Constants.TAG, "Background GPS foreground start rejected", error)
            if (PermissionUtil.hasAnyLocationPermission(this))
                DiagnosticsBackgroundLocationStart.report(this, current?.request, diagnosticStage, error)
            if (session === current) session = null
            current?.let { (application as IGISApplication).gpsEventSource.releaseBackgroundUpdates(it) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as MainApplication).closeAutomaticLocation()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(permissionCheck)
        owner?.let {
            (application as IGISApplication).gpsEventSource.releaseBackgroundUpdates(it)
            if (session === it) session = null
        }
        HyperLog.i(Constants.TAG, "Background GPS stopped")
        super.onDestroy()
    }

    private fun notification(): android.app.Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = "$packageName.background_gps"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channel,
                getString(R.string.background_gps_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(this, NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(com.nextgis.maplibui.R.drawable.ic_location)
            .setContentTitle(getString(R.string.background_gps_title))
            .setContentText(getString(R.string.background_gps_notification))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.background_gps_notification)))
            .setContentIntent(open).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_LOCATION_SHARING)
            .build()
    }

    companion object {
        private const val SESSION_ID = "background_gps_session"
        private const val NOTIFICATION_ID = 18_406
        private class Session(val request: DiagnosticsBackgroundLocationStart.Request,
                              val id: String = UUID.randomUUID().toString()) { var promoted = false }
        private var session: Session? = null // Controller and service run on the main thread.

        @JvmStatic fun start(context: Context) {
            if (session != null) return
            val created = Session(DiagnosticsBackgroundLocationStart.request(context))
            session = created
            try {
                ContextCompat.startForegroundService(context, Intent(context, BackgroundLocationService::class.java)
                    .putExtra(SESSION_ID, created.id))
            } catch (error: RuntimeException) {
                session = null
                HyperLog.w(Constants.TAG, "Background GPS launch rejected", error)
                if (PermissionUtil.hasAnyLocationPermission(context))
                    DiagnosticsBackgroundLocationStart.report(context, created.request, "launch", error)
            }
        }

        @JvmStatic fun stop(context: Context) {
            val previous = session ?: return
            session = null
            (context.applicationContext as IGISApplication).gpsEventSource.releaseBackgroundUpdates(previous)
            // A pending foreground start must promote before stopping on Android 16.
            if (previous.promoted) context.stopService(Intent(context, BackgroundLocationService::class.java))
        }
    }
}
