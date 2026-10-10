package com.nextgis.mobile.location

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.location.LocationPowerPolicy
import com.nextgis.maplib.util.Constants
import com.nextgis.mobile.R

/** Check at launch/resume even without recording; acknowledge once per unchanged state. */
class BackgroundLocationWarning(private val activity: Activity) {
    private var snackbar: Snackbar? = null
    private var dialog: AlertDialog? = null
    private var pendingState = 0
    var shownState = 0

    fun refresh() {
        if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) return
        val state = restrictions()
        if (state == 0) {
            close()
            shownState = 0
            return
        }
        if (state == shownState) return
        if (state == pendingState && snackbar?.isShownOrQueued == true) return
        pendingState = state
        snackbar?.dismiss()
        HyperLog.i(Constants.TAG, "Background location restrictions=$state " + LocationPowerPolicy.diagnostics(activity))
        val message = when {
            state and GPS_SAVER != 0 -> R.string.background_location_saver_warning
            state and BACKGROUND_RESTRICTED != 0 -> R.string.background_location_restricted_warning
            else -> R.string.background_location_optimized_warning
        }
        snackbar = Snackbar.make(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.background_location_settings) {
                shownState = state // An explicit tap also acknowledges a visible, still-animating bar.
                showSettingsChoices()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onShown(bar: Snackbar) {
                    if (snackbar === bar) {
                        shownState = state
                        HyperLog.i(Constants.TAG, "Background location warning shown state=$state")
                    }
                }
                override fun onDismissed(bar: Snackbar, event: Int) {
                    if (snackbar === bar) { snackbar = null; pendingState = 0 }
                    HyperLog.i(Constants.TAG, "Background location warning dismissed state=$state reason=$event")
                }
            })
            .also {
                it.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 6
                it.show()
            }
    }

    private fun restrictions(): Int {
        val power = activity.getSystemService(PowerManager::class.java)
        val manager = activity.getSystemService(ActivityManager::class.java)
        var state = if (LocationPowerPolicy.shouldWarn(activity)) GPS_SAVER else 0
        if (Build.VERSION.SDK_INT >= 28 && manager?.isBackgroundRestricted == true) state = state or BACKGROUND_RESTRICTED
        if (power != null && !power.isIgnoringBatteryOptimizations(activity.packageName)) state = state or OPTIMIZED
        return state
    }

    private fun showSettingsChoices() {
        if (activity.isFinishing || activity.isDestroyed || dialog?.isShowing == true) return
        val state = restrictions()
        val choices = mutableListOf<Pair<Int, Intent>>()
        if (state and GPS_SAVER != 0) choices += R.string.background_location_disable_saver to Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        if (state and BACKGROUND_RESTRICTED != 0) choices += R.string.background_location_allow_background to appSettings()
        if (state and OPTIMIZED != 0) choices += R.string.background_location_disable_optimization to
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
        if (choices.isEmpty()) { close(); shownState = 0; return }
        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.background_location_title)
            .setItems(choices.map { activity.getString(it.first) }.toTypedArray()) { _, index ->
                openSettings(choices[index].second)
            }
            .setNegativeButton(com.nextgis.maplibui.R.string.cancel, null)
            .setOnDismissListener { dialog = null }
            .show()
    }

    private fun appSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${activity.packageName}"))

    private fun openSettings(preferred: Intent) {
        for (intent in listOf(preferred, appSettings(), Intent(Settings.ACTION_SETTINGS))) {
            try {
                activity.startActivity(intent)
                return
            } catch (error: ActivityNotFoundException) {
                HyperLog.w(Constants.TAG, "Background settings unavailable", error)
            } catch (error: SecurityException) {
                HyperLog.w(Constants.TAG, "Background settings denied", error)
            }
        }
        Toast.makeText(activity, R.string.background_location_settings_unavailable, Toast.LENGTH_LONG).show()
    }

    fun close() {
        snackbar?.dismiss()
        snackbar = null
        pendingState = 0
        dialog?.dismiss()
        dialog = null
    }

    private companion object {
        const val GPS_SAVER = 1
        const val BACKGROUND_RESTRICTED = 2
        const val OPTIMIZED = 4
    }
}
