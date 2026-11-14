package com.nextgis.mobile.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nextgis.mobile.MainApplication

/** A notification action never opens UI, changes preferences or stops working-mode owners. */
class BackgroundLocationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISABLE_BACKGROUND) return
        (context.applicationContext as? MainApplication)?.disableAutomaticBackgroundLocation(
            intent.getStringExtra(EXTRA_USER_SESSION))
    }

    companion object {
        const val ACTION_DISABLE_BACKGROUND = "com.nextgis.mobile.DISABLE_AUTOMATIC_BACKGROUND_GPS"
        const val EXTRA_USER_SESSION = "background_gps_user_session"
    }
}
