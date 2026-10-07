package com.nextgis.mobile.location

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.location.LocationPowerPolicy
import com.nextgis.maplib.util.Constants
import com.nextgis.maplibui.R
import com.nextgis.maplibui.service.TrackerService

/** Activity-owned warning; it never turns off Battery Saver or starts recording from settings. */
class TrackPowerWarning(private val activity: Activity) {
    private var dialog: AlertDialog? = null
    private var activeWarningShown = false

    fun confirmStart(start: () -> Unit) {
        if (!LocationPowerPolicy.shouldWarn(activity)) {
            activeWarningShown = false
            start()
            return
        }
        show(start)
    }

    fun refresh() {
        if (!LocationPowerPolicy.shouldWarn(activity)) {
            dialog?.dismiss()
            activeWarningShown = false
            return
        }
        if (!TrackerService.hasRecordingSession(activity)) {
            activeWarningShown = false
            return
        }
        if (!activeWarningShown) show(null)
    }

    private fun show(start: (() -> Unit)?) {
        if (activity.isFinishing || activity.isDestroyed || dialog?.isShowing == true) return
        activeWarningShown = true
        HyperLog.i(Constants.TAG, "Track power dialog " + LocationPowerPolicy.diagnostics(activity))
        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.track_power_title)
            .setMessage(R.string.track_power_message)
            .setPositiveButton(R.string.track_power_settings) { _, _ -> openSettings() }
            .setNegativeButton(if (start == null) R.string.track_power_acknowledge else R.string.track_power_continue) { _, _ ->
                start?.invoke()
            }
            .setOnDismissListener { dialog = null }
            .show()
    }

    private fun openSettings() {
        for (action in arrayOf(Settings.ACTION_BATTERY_SAVER_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                activity.startActivity(Intent(action))
                return
            } catch (error: ActivityNotFoundException) {
                HyperLog.w(Constants.TAG, "Battery settings unavailable", error)
            }
        }
    }

    fun close() {
        dialog?.dismiss()
        dialog = null
    }
}
