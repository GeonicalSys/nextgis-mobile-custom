/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Copyright (c) 2026 GeonicalSystem
 */
package com.nextgis.mobile.stakeout

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nextgis.maplib.api.GpsEventListener
import com.nextgis.maplib.datasource.GeoGeometry
import com.nextgis.maplib.location.GpsEventSource
import com.nextgis.maplib.util.StakeoutGuidancePolicy
import com.nextgis.maplib.util.StakeoutGeometryTarget

class StakeoutController(
    context: Context,
    private val preferences: SharedPreferences,
    private val gpsEventSource: GpsEventSource,
    private val listener: Listener
) : GpsEventListener {
    interface Listener {
        fun onStakeoutStateChanged(state: UiState)
    }

    data class UiState(
        val waitingForFix: Boolean,
        val distanceMeters: Double? = null,
        val relativeBearingDegrees: Float = 0f,
        val absoluteBearingDegrees: Float = 0f,
        val magneticBearingDegrees: Float? = null,
        val declinationDegrees: Float = 0f,
        val accuracyMeters: Double? = null,
        val usesDeviceCompass: Boolean = false,
        val reached: Boolean = false,
        val muted: Boolean = false
    )

    private val handler = Handler(Looper.getMainLooper())
    private val applicationContext = context.applicationContext
    private var audioCue: StakeoutAudioCue? = null
    private val headingProvider = StakeoutHeadingProvider(context) { publishLatestState() }
    private val highFrequencyOwner = Any()
    private val cueRunnable = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            if (!hasFreshFix(now)) {
                if (!waitingForFix) {
                    waitingForFix = true
                    audioCue?.stop()
                    publishLatestState()
                }
                handler.postDelayed(this, SCHEDULER_TICK_MILLIS)
                return
            }

            val interval = latestBand.intervalMillis
            if (interval != null
                && !muted
                && now >= nextCueElapsedMillis
            ) {
                audioCue?.playPing()
                nextCueElapsedMillis = now + interval
            }
            handler.postDelayed(this, SCHEDULER_TICK_MILLIS)
        }
    }

    private var target: StakeoutGeometryTarget? = null
    private var policy: StakeoutGuidancePolicy? = null
    private var thresholds = StakeoutSettings.defaults()
    private var latestLocation: Location? = null
    private var latestResult: StakeoutGeometryTarget.Result? = null
    private var latestBand = StakeoutGuidancePolicy.Band.SILENT
    private var nextCueElapsedMillis = Long.MAX_VALUE
    private var reachedConfirmations = 0
    private var reachedAnnounced = false
    private var waitingForFix = true
    private var active = false
    private var uiForeground = false
    private var resourcesActive = false
    private var muted = false

    val isActive: Boolean
        get() = active

    fun start(geometry: GeoGeometry) {
        stopInternal(clearListener = false)
        target = StakeoutGeometryTarget(geometry)
        thresholds = StakeoutSettings.loadThresholds(preferences)
        policy = StakeoutGuidancePolicy(
            thresholds.far,
            thresholds.medium,
            thresholds.near,
            thresholds.reached
        )
        if (audioCue == null) audioCue = StakeoutAudioCue()
        active = true
        muted = !preferences.getBoolean(StakeoutSettings.KEY_SOUND_ENABLED, true)
        waitingForFix = true
        startActiveResources()
        publishLatestState()
    }

    /** Replaces the live target without releasing the foreground GPS/audio resources. */
    fun updateTarget(geometry: GeoGeometry) {
        if (!active) {
            start(geometry)
            return
        }
        target = StakeoutGeometryTarget(geometry)
        policy?.reset()
        latestResult = null
        latestBand = StakeoutGuidancePolicy.Band.SILENT
        nextCueElapsedMillis = Long.MAX_VALUE
        reachedConfirmations = 0
        reachedAnnounced = false
        waitingForFix = true
        latestLocation?.let { updateLocation(it) } ?: publishLatestState()
    }

    fun stop() {
        stopInternal(clearListener = true)
    }

    fun release() {
        stopInternal(clearListener = false)
        audioCue?.release()
        audioCue = null
    }

    fun setForeground(value: Boolean) {
        uiForeground = value
        if (!active) return
        if (value) {
            headingProvider.start()
            latestLocation?.let { headingProvider.updateLocation(it) }
            publishLatestState()
        } else {
            headingProvider.stop()
        }
    }

    fun toggleMuted() {
        muted = !muted
        if (muted) audioCue?.stop()
        publishLatestState()
    }

    fun updateLocation(location: Location) {
        if (!active) return
        latestLocation = Location(location)
        if (uiForeground) headingProvider.updateLocation(location)

        val now = SystemClock.elapsedRealtime()
        if (!hasFreshFix(now)) {
            waitingForFix = true
            latestBand = StakeoutGuidancePolicy.Band.SILENT
            audioCue?.stop()
            publishLatestState()
            return
        }

        val result = target?.calculate(location.longitude, location.latitude) ?: return
        latestResult = result
        waitingForFix = false
        val oldBand = latestBand
        latestBand = policy?.evaluate(
            result.distanceMeters,
            isPrecisionFix(location)
        ) ?: StakeoutGuidancePolicy.Band.SILENT

        if (latestBand == StakeoutGuidancePolicy.Band.REACHED) {
            reachedConfirmations++
        } else {
            reachedConfirmations = 0
        }

        val reached = reachedConfirmations >= REQUIRED_REACHED_FIXES
        if (reached
            && !reachedAnnounced
            && !muted
        ) {
            audioCue?.playReached()
            reachedAnnounced = true
        }
        if (result.distanceMeters > thresholds.reached * REACHED_RESET_MULTIPLIER) {
            reachedAnnounced = false
        }

        if (latestBand.intervalMillis == null) {
            nextCueElapsedMillis = Long.MAX_VALUE
        } else if (oldBand != latestBand || nextCueElapsedMillis == Long.MAX_VALUE) {
            nextCueElapsedMillis = now
        }
        publishLatestState()
    }

    private fun startActiveResources() {
        if (resourcesActive) return
        resourcesActive = true
        StakeoutForegroundService.start(applicationContext)
        gpsEventSource.addRawListener(this)
        gpsEventSource.acquireHighFrequencyUpdates(highFrequencyOwner)
        if (uiForeground) headingProvider.start()
        handler.removeCallbacks(cueRunnable)
        handler.post(cueRunnable)
    }

    private fun stopActiveResources() {
        if (!resourcesActive) return
        resourcesActive = false
        gpsEventSource.releaseHighFrequencyUpdates(highFrequencyOwner)
        gpsEventSource.removeRawListener(this)
        headingProvider.stop()
        handler.removeCallbacks(cueRunnable)
        audioCue?.stop()
        StakeoutForegroundService.stop(applicationContext)
    }

    private fun stopInternal(clearListener: Boolean) {
        stopActiveResources()
        active = false
        target = null
        policy?.reset()
        policy = null
        latestLocation = null
        latestResult = null
        latestBand = StakeoutGuidancePolicy.Band.SILENT
        nextCueElapsedMillis = Long.MAX_VALUE
        reachedConfirmations = 0
        reachedAnnounced = false
        waitingForFix = true
        if (clearListener) listener.onStakeoutStateChanged(UiState(waitingForFix = true))
    }

    private fun publishLatestState() {
        if (!active || !uiForeground) return
        val result = latestResult
        if (waitingForFix || result == null) {
            listener.onStakeoutStateChanged(
                UiState(waitingForFix = true, muted = muted)
            )
            return
        }
        val magneticHeading = headingProvider.magneticHeading()
        val declination = headingProvider.declinationDegrees()
        val absoluteBearing = normalize(result.bearingDegrees.toFloat())
        val magneticBearing = MagneticAzimuthCalculator.fromTrueBearing(
            result.bearingDegrees,
            declination,
            result.distanceMeters
        )
        val location = latestLocation
        listener.onStakeoutStateChanged(
            UiState(
                waitingForFix = false,
                distanceMeters = result.distanceMeters,
                relativeBearingDegrees = if (magneticHeading == null || magneticBearing == null) {
                    absoluteBearing
                } else {
                    normalize(magneticBearing - magneticHeading)
                },
                absoluteBearingDegrees = absoluteBearing,
                magneticBearingDegrees = magneticBearing,
                declinationDegrees = declination,
                accuracyMeters = location?.takeIf { it.hasAccuracy() }
                    ?.accuracy
                    ?.toDouble(),
                usesDeviceCompass = magneticHeading != null,
                reached = reachedConfirmations >= REQUIRED_REACHED_FIXES,
                muted = muted
            )
        )
    }

    private fun isPrecisionFix(location: Location): Boolean {
        return location.provider == LocationManager.GPS_PROVIDER || isMockLocation(location)
    }

    private fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) updateLocation(location)
    }

    override fun onBestLocationChanged(location: Location) {
        updateLocation(location)
    }

    override fun onGpsStatusChanged(event: Int) {
        // Freshness is evaluated from Location timestamps by the cue scheduler.
    }

    private fun hasFreshFix(nowElapsedMillis: Long): Boolean {
        val location = latestLocation ?: return false
        val fixElapsedMillis = location.elapsedRealtimeNanos / 1_000_000L
        val age = if (fixElapsedMillis > 0L) {
            nowElapsedMillis - fixElapsedMillis
        } else {
            System.currentTimeMillis() - location.time
        }
        return age in 0..MAX_FIX_AGE_MILLIS
    }

    private fun normalize(value: Float): Float {
        val normalized = value % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }

    private companion object {
        const val MAX_FIX_AGE_MILLIS = 3_000L
        const val SCHEDULER_TICK_MILLIS = 100L
        const val REQUIRED_REACHED_FIXES = 2
        const val REACHED_RESET_MULTIPLIER = 1.5
    }
}
