package com.nextgis.mobile.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.util.Constants
import com.nextgis.maplibui.util.FittedDialogs
import com.nextgis.maplibui.util.LayerBackupManager
import com.nextgis.mobile.R
import java.io.File
import java.nio.file.Files

/** Confirmed app maintenance shared by settings, independent of MainActivity and map state. */
object MaintenanceActions {
    const val KEY_CLEAR_LOG = "maintenance_clear_log"
    const val KEY_CLEAR_BACKUPS = "maintenance_clear_backups"

    @JvmStatic
    fun confirmClearLog(activity: Activity) {
        confirm(activity, R.string.clear_log, R.string.clear_log_message,
            R.string.log_cleared, R.string.maintenance_log_clear_error) {
            clearLog(activity.applicationContext)
        }
    }

    @JvmStatic
    fun confirmClearBackups(activity: Activity) {
        confirm(activity, R.string.maintenance_clear_backups, R.string.clear_layer_backups_message,
            R.string.layer_backups_cleared, R.string.layer_backups_clear_error) {
            clearBackups(activity.applicationContext)
        }
    }

    private fun confirm(activity: Activity, title: Int, message: Int, success: Int, failure: Int,
                        action: () -> Boolean) {
        if (activity.isFinishing || activity.isDestroyed) return
        FittedDialogs.compatBuilder(activity)
            .setTitle(title).setMessage(message)
            .setPositiveButton(R.string.maintenance_clear) { _, _ ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    val result = if (action()) success else failure
                    Toast.makeText(activity.applicationContext, result, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @JvmStatic
    fun clearLog(context: Context): Boolean = safely {
        val parent = context.getExternalFilesDir(null) ?: return@safely false
        val root = File(parent, "LogFiles")
        if (Files.isSymbolicLink(root.toPath()) || (root.exists() && !root.isDirectory))
            return@safely false
        val children = if (root.exists()) root.listFiles() ?: return@safely false else emptyArray()
        HyperLog.deleteLogs()
        var complete = true
        for (file in children) if (!deleteLogEntry(file)) complete = false
        complete
    }

    /** Never traverse a link from a log folder into projects or other application data. */
    private fun deleteLogEntry(file: File): Boolean {
        if (Files.isSymbolicLink(file.toPath())) return file.delete()
        var complete = true
        if (file.isDirectory) {
            val children = file.listFiles() ?: return false
            for (child in children) if (!deleteLogEntry(child)) complete = false
        }
        return file.delete() && complete
    }

    @JvmStatic
    fun clearBackups(context: Context): Boolean = safely {
        val root = LayerBackupManager.getBackupRoot(context)
        if (root.exists() && !root.isDirectory) return@safely false
        if (!safeBackupTree(root)) return@safely false
        LayerBackupManager.clearBackups(context)
    }

    private fun safeBackupTree(file: File): Boolean {
        if (Files.isSymbolicLink(file.toPath())) return false
        if (!file.exists() || !file.isDirectory) return true
        return file.listFiles()?.all { safeBackupTree(it) } ?: false
    }

    private inline fun safely(action: () -> Boolean): Boolean = try {
        action()
    } catch (error: Exception) {
        Log.w(Constants.TAG, "Application maintenance did not complete", error)
        false
    }
}
