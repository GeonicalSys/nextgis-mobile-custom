package com.nextgis.mobile.location

import android.app.Activity
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
import java.util.Collections
import java.util.IdentityHashMap

/** App visibility owns automatic GPS, independently of map fragments and recording sessions. */
class BackgroundLocationController(private val application: Application) :
    Application.ActivityLifecycleCallbacks, SharedPreferences.OnSharedPreferenceChangeListener {
    private val policy = BackgroundLocationPolicy()
    private val visible = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
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
            BackgroundLocationService.start(application)
        }
    }

    /** Release only automatic owners; Track/Walk services retain their recording listeners. */
    fun closeExplicitly() {
        policy.close()
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(timeout)
        (application as IGISApplication).gpsEventSource.releaseBackgroundUpdates(this)
        BackgroundLocationService.stop(application)
    }

    override fun onActivityStarted(activity: Activity) {
        visible.add(activity)
        policy.foreground(SystemClock.elapsedRealtime())
        handler.removeCallbacks(timeout)
        if (PermissionUtil.hasAnyLocationPermission(application))
            (application as IGISApplication).gpsEventSource.acquireBackgroundUpdates(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (!policy.isOpened) return
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onActivityStopped(activity: Activity) {
        visible.remove(activity)
        if (visible.isNotEmpty()) return
        policy.background(SystemClock.elapsedRealtime())
        (application as IGISApplication).gpsEventSource.releaseBackgroundUpdates(this)
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(timeout)
        if (policy.isOpened) handler.postDelayed(timeout, policy.remainingMs(SystemClock.elapsedRealtime()))
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key == BackgroundLocationPolicy.PREFERENCE) handler.post { reconcile() }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
