package com.nextgis.mobile.location

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 36])
@LooperMode(LooperMode.Mode.PAUSED)
class DeviceHeadingProviderTest {
    private lateinit var sensors: ShadowSensorManager
    private lateinit var rotation: Sensor
    private lateinit var magnetic: Sensor
    private lateinit var provider: DeviceHeadingProvider
    private val published = mutableListOf<DeviceHeadingProvider.Sample?>()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        sensors = shadowOf(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
        rotation = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        magnetic = ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD)
        sensors.addSensor(rotation)
        sensors.addSensor(magnetic)
        provider = DeviceHeadingProvider(context) { published.add(provider.heading()) }
        provider.start()
    }

    @Test
    fun staleCompassNotifiesTheMapWithoutAnotherLocationOrSensorEvent() {
        sendRotation()
        assertEquals(0f, provider.heading()!!.trueDegrees, 0.001f)
        idle(500)
        assertNotNull(provider.heading())
        assertEquals(1, published.size)
        idle(1)
        assertNull(provider.heading())
        assertEquals(2, published.size)
        assertNull(published.last())
    }

    @Test
    fun freshRotationExtendsTheDeadlineAndRecoversAfterExpiry() {
        sendRotation()
        idle(400)
        sendRotation()
        idle(101)
        assertNotNull(provider.heading())
        idle(400)
        assertNull(provider.heading())
        sendRotation()
        assertNotNull(provider.heading())
        assertNotNull(published.last())
    }

    @Test
    fun magneticFieldEventsCannotKeepAnOldRotationAlive() {
        sendRotation()
        idle(400)
        val event = event(magnetic, 3)
        event.values[0] = 30f
        event.values[2] = 30f
        provider.onSensorChanged(event)
        idle(101)
        assertNull(provider.heading())
        assertNull(published.last())
    }

    @Test
    fun unreliableRotationImmediatelyClearsTheHeadingAndCancelsItsDeadline() {
        sendRotation()
        provider.onAccuracyChanged(rotation, SensorManager.SENSOR_STATUS_UNRELIABLE)
        assertNull(provider.heading())
        assertNull(published.last())
        val count = published.size
        idle(501)
        assertEquals(count, published.size)
        sendRotation()
        assertNotNull(provider.heading())
    }

    @Test
    fun stopCancelsCallbacksAndRestartWaitsForAFreshRotation() {
        sendRotation()
        provider.stop()
        assertNull(provider.heading())
        assertFalse(sensors.hasListener(provider))
        val count = published.size
        idle(501)
        assertEquals(count, published.size)
        provider.start()
        assertNull(provider.heading())
        sendRotation()
        assertNotNull(provider.heading())
    }

    @Test
    fun absentOrUnavailableRotationSensorLeavesHeadingAbsent() {
        provider.stop()
        sensors.setForceListenersToFail(true)
        provider.start()
        assertNull(provider.heading())
        sensors.setForceListenersToFail(false)
        sensors.removeSensor(rotation)
        val withoutCompass = DeviceHeadingProvider(RuntimeEnvironment.getApplication()) {}
        withoutCompass.start()
        assertNull(withoutCompass.heading())
        assertFalse(sensors.hasListener(withoutCompass))
    }

    private fun sendRotation() {
        val event = event(rotation, 5)
        event.values[3] = 1f
        event.values[4] = Math.toRadians(10.0).toFloat()
        provider.onSensorChanged(event)
    }

    private fun event(sensor: Sensor, size: Int): SensorEvent =
        ShadowSensorManager.createSensorEvent(size).also {
            it.sensor = sensor
            it.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        }

    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
}
