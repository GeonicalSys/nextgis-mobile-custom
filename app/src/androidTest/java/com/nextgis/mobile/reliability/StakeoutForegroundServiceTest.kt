package com.nextgis.mobile.reliability

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.mobile.MainApplication
import com.nextgis.mobile.activity.AboutActivity
import com.nextgis.mobile.stakeout.StakeoutController
import com.nextgis.mobile.stakeout.StakeoutForegroundService
import com.nextgis.mobile.stakeout.StakeoutSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real Android permission enforcement; run only on an isolated emulator with Bluetooth denied. */
@RunWith(AndroidJUnit4::class)
class StakeoutForegroundServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as MainApplication
    private val controllers = mutableListOf<StakeoutController>()
    private val serviceOwners = mutableListOf<String>()
    private val preferences = app.getSharedPreferences("stakeout_test_${UUID.randomUUID()}", 0)

    @Before fun isolatedEmulatorWithoutBluetooth() {
        assertTrue("Never run on a working phone", Build.HARDWARE == "ranchu"
            || Build.HARDWARE == "goldfish")
        assertTrue(app.packageName.endsWith(".debug"))
        assertTrue("Permission enforcement requires Android 14+", Build.VERSION.SDK_INT >= 34)
        assertEquals(PackageManager.PERMISSION_DENIED,
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
        assertEquals(PackageManager.PERMISSION_DENIED,
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(preferences.edit().putBoolean(StakeoutSettings.KEY_SOUND_ENABLED, false).commit())
    }

    @After fun releaseOnlyTestOwners() {
        onMain {
            controllers.forEach { it.release() }
            serviceOwners.forEach { StakeoutForegroundService.stop(app, it) }
        }
        assertTrue(preferences.edit().clear().commit())
    }

    @Test fun mergedManifestUsesOnlyLocation() {
        val service = app.packageManager.getServiceInfo(
            ComponentName(app, StakeoutForegroundService::class.java), 0)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, service.foregroundServiceType)
    }

    @Test fun liveGuidanceWithoutBluetoothSurvivesBackgroundAndReleasesGps() {
        assumeTrue(hasLocationPermission())
        val failures = AtomicInteger()
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            val controller = controller(app, failures)
            onMain { controller.setForeground(true); controller.start(target()) }
            await { serviceIsForeground() }
            assertTrue(onMain { controller.isActive })
            assertGpsOwned(controller, true)
            val owner = onMain { privateField(controller, "foregroundSessionId") }
            onMain { controller.updateTarget(target()) }
            assertEquals(owner, onMain { privateField(controller, "foregroundSessionId") })
            onMain { controller.setForeground(false) }
            scenario.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(300)
            assertTrue("An already started location service survives Home", serviceIsForeground())
            assertGpsOwned(controller, true)
            scenario.moveToState(Lifecycle.State.RESUMED)
            onMain { controller.stop(); controller.stop() }
            await { !serviceIsForeground() && !hasNotification() }
            assertGpsOwned(controller, false)
            assertEquals(0, failures.get())
        }
    }

    @Test fun deniedLocationStopsGuidanceWithoutCrashOrLeakedGps() {
        assumeFalse(hasLocationPermission())
        val failures = AtomicInteger()
        ActivityScenario.launch(AboutActivity::class.java).use {
            val controller = controller(app, failures)
            repeat(2) { attempt ->
                onMain { controller.start(target()) }
                await { failures.get() == attempt + 1 }
                assertFalse(onMain { controller.isActive })
                assertGpsOwned(controller, false)
                await { !serviceIsForeground() && !hasNotification() }
            }
        }
    }

    @Test fun immediateStartRejectionRollsBackControllerAndAllowsRetry() {
        val failures = AtomicInteger()
        val rejectedContext = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun startForegroundService(service: Intent): ComponentName? {
                throw SecurityException("Synthetic foreground-start rejection")
            }
        }
        val controller = controller(rejectedContext, failures)
        repeat(2) {
            try {
                onMain { controller.start(target()) }
                fail("The synchronous rejection must reach the UI start guard")
            } catch (_: SecurityException) { }
            assertFalse(onMain { controller.isActive })
            assertGpsOwned(controller, false)
        }
        assertEquals(0, failures.get())
    }

    @Test fun staleStartAndStopCannotCancelANewerOwner() {
        assumeTrue(hasLocationPermission())
        val failures = AtomicInteger()
        ActivityScenario.launch(AboutActivity::class.java).use {
            val owners = onMain {
                val old = StakeoutForegroundService.start(app) { failures.incrementAndGet() }
                serviceOwners.add(old)
                StakeoutForegroundService.stop(app, old)
                val current = StakeoutForegroundService.start(app) { failures.incrementAndGet() }
                serviceOwners.add(current)
                old to current
            }
            await { serviceIsForeground() }
            onMain {
                ContextCompat.startForegroundService(app,
                    Intent(app, StakeoutForegroundService::class.java)
                        .putExtra("stakeout_session_id", owners.first))
                StakeoutForegroundService.stop(app, owners.first)
            }
            SystemClock.sleep(300)
            assertTrue(serviceIsForeground())
            assertEquals(0, failures.get())
            onMain { StakeoutForegroundService.stop(app, owners.second) }
            await { !serviceIsForeground() && !hasNotification() }
        }
    }

    @Test fun unownedStartDoesNotRestoreAnOldSession() {
        ActivityScenario.launch(AboutActivity::class.java).use {
            onMain { ContextCompat.startForegroundService(app,
                Intent(app, StakeoutForegroundService::class.java)) }
            SystemClock.sleep(300)
            await { !serviceIsForeground() && !hasNotification() }
        }
    }

    private fun controller(context: Context, failures: AtomicInteger): StakeoutController = onMain {
        StakeoutController(context, preferences, app.gpsEventSource,
            object : StakeoutController.Listener {
                override fun onStakeoutStateChanged(state: StakeoutController.UiState) { }
                override fun onStakeoutUnavailable() { failures.incrementAndGet() }
            }).also { controllers.add(it) }
    }

    private fun assertGpsOwned(controller: StakeoutController, owned: Boolean) = onMain {
        val gps = app.gpsEventSource
        assertEquals(owned, (privateField(gps, "rawListeners") as Set<*>).contains(controller))
        assertEquals(owned, (privateField(gps, "highFrequencyClients") as Set<*>)
            .contains(privateField(controller, "highFrequencyOwner")))
        assertEquals(owned, privateField(controller, "resourcesActive"))
    }

    private fun hasLocationPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun serviceIsForeground(): Boolean = app.getSystemService(ActivityManager::class.java)
        .getRunningServices(100).any {
            it.service.className == StakeoutForegroundService::class.java.name && it.foreground
        }

    private fun hasNotification(): Boolean = app.getSystemService(NotificationManager::class.java)
        .activeNotifications.any { it.id == 18_405 }

    private fun target() = GeoPoint(37.0, 55.0).apply { crs = GeoConstants.CRS_WGS84 }
    private fun privateField(instance: Any, name: String): Any? =
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(40)
        assertTrue("Timed out waiting for the real service lifecycle", condition())
    }
}
