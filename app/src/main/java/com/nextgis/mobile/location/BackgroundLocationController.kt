package com.nextgis.mobile.location

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.preference.PreferenceManager
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.util.PermissionUtil
import com.nextgis.mobile.util.AppDiagnostics
import com.nextgis.mobile.util.DiagnosticsBackgroundLocationStart
import java.util.Collections
import java.util.IdentityHashMap

/** App visibility owns automatic GPS, independently of map fragments and recording sessions. */
class BackgroundLocationController(private val application: Application) :
    Application.ActivityLifecycleCallbacks, SharedPreferences.OnSharedPreferenceChangeListener {
    private val policy = BackgroundLocationPolicy()
    private val visible = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
    private val resumed = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
    private val roots = IdentityHashMap<Activity, Int>()
    private val userTasks = mutableSetOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val refresh = object : Runnable {
        override fun run() {
            reconcile()
            if (visible.isNotEmpty()) handler.postDelayed(this, 5_000)
        }
    }
    private val timeout = Runnable { reconcile() }

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
        observeLifecycle()
        preferences.registerOnSharedPreferenceChangeListener(this)
        (application as IGISApplication).gpsEventSource.setRecoveryReporter { snapshot ->
            AppDiagnostics.reportGpsRecovery(snapshot)
        }
    }

    private fun reconcile(now: Long = SystemClock.elapsedRealtime()) {
        val enabled = preferences.getBoolean(BackgroundLocationPolicy.PREFERENCE, true)
        val allowed = PermissionUtil.hasAnyLocationPermission(application)
        val source = (application as IGISApplication).gpsEventSource
        // Forms are still foreground app use even when the user disables screen-off GPS.
        if (allowed && policy.isOpened && visible.isNotEmpty()) source.acquireBackgroundUpdates(this)
        else source.releaseBackgroundUpdates(this)
        if (!allowed || !policy.wanted(enabled, now)) {
            BackgroundLocationService.stop(application)
        } else if (visible.isNotEmpty()) {
            // Launch while visible. A background worker or process restart must not start GPS.
            policy.sessionId?.let { BackgroundLocationService.start(application, it) }
        }
    }

    private fun observeLifecycle() {
        DiagnosticsBackgroundLocationStart.lifecycle(visible.size, resumed.size)
    }

    /** Release only automatic owners; Track/Walk services retain their recording listeners. */
    fun closeExplicitly() {
        policy.close()
        userTasks.clear()
        visible.clear()
        resumed.clear()
        observeLifecycle()
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(timeout)
        (application as IGISApplication).gpsEventSource.releaseBackgroundUpdates(this)
        BackgroundLocationService.stop(application)
    }

    fun disableBackground(sessionId: String?) {
        if (!policy.disableBackground(sessionId)) return
        handler.removeCallbacks(timeout)
        reconcile()
    }

    fun isBackgroundWanted(sessionId: String): Boolean =
        policy.sessionId == sessionId && policy.wanted(
            preferences.getBoolean(BackgroundLocationPolicy.PREFERENCE, true),
            SystemClock.elapsedRealtime())

    /** A delayed removal of an old task must not close a newly opened app session. */
    fun closeRemovedTask() {
        val remainingTasks = try {
            application.getSystemService(ActivityManager::class.java).appTasks
                .map { it.taskInfo.taskId }.toSet()
        } catch (_: RuntimeException) { emptySet<Int>() }
        if (userTasks.any { it in remainingTasks }) return
        if (visible.any { !it.isFinishing && !it.isDestroyed }) return
        closeExplicitly()
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.isFinishing) return
        visible.add(activity)
        userTasks.add(activity.taskId)
        if (activity.isTaskRoot) roots[activity] = activity.taskId
        observeLifecycle()
        policy.foreground(SystemClock.elapsedRealtime())
        handler.removeCallbacks(timeout)
        if (PermissionUtil.hasAnyLocationPermission(application))
            (application as IGISApplication).gpsEventSource.acquireBackgroundUpdates(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (!visible.contains(activity)) return
        if (activity.isTaskRoot) roots[activity] = activity.taskId
        resumed.add(activity)
        observeLifecycle()
        if (!policy.isOpened) return
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onActivityStopped(activity: Activity) {
        visible.remove(activity)
        resumed.remove(activity)
        observeLifecycle()
        if (visible.isNotEmpty()) return
        policy.background(SystemClock.elapsedRealtime())
        (application as IGISApplication).gpsEventSource.releaseBackgroundUpdates(this)
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(timeout)
        if (isBackgroundWanted(policy.sessionId ?: ""))
            handler.postDelayed(timeout, policy.remainingMs(SystemClock.elapsedRealtime()))
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key == BackgroundLocationPolicy.PREFERENCE) handler.post { reconcile() }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        if (activity.isTaskRoot) roots[activity] = activity.taskId
    }
    override fun onActivityPaused(activity: Activity) {
        resumed.remove(activity)
        observeLifecycle()
    }
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        onActivityStopped(activity)
        val task = roots.remove(activity) ?: return
        if (!activity.isFinishing || roots.any { it.value == task && !it.key.isFinishing }) return
        if (visible.any { it.taskId == task && !it.isFinishing && !it.isDestroyed }) return
        if (userTasks.remove(task) && userTasks.isEmpty()) closeExplicitly()
    }
}
