package dev.localvispr.sensors

import kotlin.math.sqrt

// ==============================================================================
// Pure JVM Shake Detector Engine
// ==============================================================================
//
// Detects repeated shake movements by tracking peak acceleration magnitudes
// exceeding a threshold above standard Earth gravity. Kept free of any android.*
// imports to allow fast JVM testing.

class ShakeDetectorEngine(
    private val thresholdG: Float = 2.3f,
    private val requiredHits: Int = 2,
    private val windowMs: Long = 550L,
    private val cooldownMs: Long = 1200L,
) {
    companion object {
        const val GRAVITY_EARTH: Float = 9.80665f
    }

    private var hitCount: Int = 0
    private var firstHitTimestampMs: Long = 0L
    private var lastTriggerTimestampMs: Long = 0L

    /**
     * Evaluates an acceleration event.
     *
     * @param ax Acceleration along X axis (m/s^2)
     * @param ay Acceleration along Y axis (m/s^2)
     * @param az Acceleration along Z axis (m/s^2)
     * @param timestampMs Timestamp of the event in milliseconds
     * @return `true` if a shake gesture is triggered, `false` otherwise
     */
    fun onAcceleration(ax: Float, ay: Float, az: Float, timestampMs: Long): Boolean {
        // Enforce cooldown after a successful shake detection
        if (lastTriggerTimestampMs > 0L && timestampMs - lastTriggerTimestampMs < cooldownMs) {
            return false
        }

        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val gForce = magnitude / GRAVITY_EARTH

        if (gForce >= thresholdG) {
            if (hitCount == 0 || timestampMs - firstHitTimestampMs > windowMs) {
                // First hit or previous window expired; reset window with current hit
                hitCount = 1
                firstHitTimestampMs = timestampMs
            } else {
                hitCount++
            }

            if (hitCount >= requiredHits) {
                hitCount = 0
                firstHitTimestampMs = 0L
                lastTriggerTimestampMs = timestampMs
                return true
            }
        }

        return false
    }
}
