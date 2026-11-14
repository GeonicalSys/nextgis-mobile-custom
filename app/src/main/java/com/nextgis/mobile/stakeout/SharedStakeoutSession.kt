package com.nextgis.mobile.stakeout

import android.content.Context
import android.content.SharedPreferences
import com.nextgis.maplib.datasource.GeoGeometry
import com.nextgis.maplib.location.GpsEventSource
import java.lang.ref.WeakReference

/** Process-owned guidance retains its immutable target, never an Activity or a layer/FID. */
class SharedStakeoutSession(context: Context, preferences: SharedPreferences, source: GpsEventSource) {
    private var attached: WeakReference<UiHandle>? = null
    private var retained = false
    private val controller = StakeoutController(context.applicationContext, preferences, source,
        object : StakeoutController.Listener {
            override fun onStakeoutStateChanged(state: StakeoutController.UiState) {
                attached?.get()?.listener?.onStakeoutStateChanged(state)
            }

            override fun onStakeoutUnavailable() {
                retained = false
                attached?.get()?.listener?.onStakeoutUnavailable()
            }
        })

    val isActive: Boolean get() = controller.isActive

    @JvmOverloads
    fun attach(listener: StakeoutController.Listener, onDetach: () -> Unit = {}): UiHandle {
        attached?.get()?.detach()
        controller.setForeground(false)
        return UiHandle(listener, onDetach).also { attached = WeakReference(it) }
    }

    fun stop() {
        retained = false
        controller.stop()
        controller.release()
    }

    private fun owns(handle: UiHandle): Boolean = attached?.get() === handle

    /** A replaced/destroyed screen cannot stop or detach the next screen's owner. */
    inner class UiHandle internal constructor(internal val listener: StakeoutController.Listener,
                                             private val onDetach: () -> Unit) {
        val isActive: Boolean get() = owns(this) && controller.isActive
        val isRetained: Boolean get() = isActive && retained

        @JvmOverloads
        fun start(geometry: GeoGeometry, enableSound: Boolean = true, retainOnDetach: Boolean = false) {
            if (!owns(this)) return
            retained = false
            controller.start(geometry, enableSound)
            retained = retainOnDetach
        }

        fun updateTarget(geometry: GeoGeometry) {
            if (owns(this)) controller.updateTarget(geometry)
        }

        fun stop() {
            if (owns(this)) this@SharedStakeoutSession.stop()
        }

        fun setForeground(value: Boolean) {
            if (owns(this)) controller.setForeground(value)
        }

        fun toggleMuted() {
            if (owns(this)) controller.toggleMuted()
        }

        fun refreshUi() {
            if (owns(this)) controller.refreshUi()
        }

        fun detach() {
            if (!owns(this)) return
            controller.setForeground(false)
            attached = null
            onDetach()
            // Free-point measurements retain their existing screen-local lifecycle.
            if (!retained) controller.release()
        }
    }
}
