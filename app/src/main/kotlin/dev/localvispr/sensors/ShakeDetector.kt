package dev.localvispr.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

// ==============================================================================
// Android Shake Detector
// ==============================================================================
//
// Wraps [ShakeDetectorEngine] and listens to accelerometer events from
// [SensorManager]. Delivers a callback on every detected shake gesture.

class ShakeDetector(
    context: Context,
    private val engine: ShakeDetectorEngine = ShakeDetectorEngine(),
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var running: Boolean = false

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val sm = sensorManager ?: return false
        val sensor = accelerometer ?: return false

        running = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        return running
    }

    @Synchronized
    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values.getOrNull(0) ?: 0f
        val ay = event.values.getOrNull(1) ?: 0f
        val az = event.values.getOrNull(2) ?: 0f
        val timestampMs = SystemClock.uptimeMillis()

        if (engine.onAcceleration(ax, ay, az, timestampMs)) {
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
