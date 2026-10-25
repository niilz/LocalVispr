package dev.localvispr.sensors

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ShakeDetectorEngineTest {

    @Test
    fun restingDeviceDoesNotTriggerShake() {
        val engine = ShakeDetectorEngine(thresholdG = 2.3f, requiredHits = 2, windowMs = 550L)

        // Standard gravity ~1g on Z axis
        val triggered1 = engine.onAcceleration(0f, 0f, 9.80665f, 1000L)
        val triggered2 = engine.onAcceleration(0.1f, -0.2f, 9.8f, 1100L)
        val triggered3 = engine.onAcceleration(0f, 0f, 9.80665f, 1200L)

        assertFalse(triggered1)
        assertFalse(triggered2)
        assertFalse(triggered3)
    }

    @Test
    fun doubleStrongShakeWithinWindowTriggers() {
        val engine = ShakeDetectorEngine(thresholdG = 2.3f, requiredHits = 2, windowMs = 550L)
        val strongAx = 2.5f * 9.80665f

        // First strong shake hit at 1000ms
        val hit1 = engine.onAcceleration(strongAx, 0f, 0f, 1000L)
        assertFalse(hit1)

        // Second strong shake hit within 550ms window at 1250ms
        val hit2 = engine.onAcceleration(-strongAx, 0f, 0f, 1250L)
        assertTrue(hit2)
    }

    @Test
    fun cooldownSuppressesTriggersDirectlyAfterwards() {
        val engine =
            ShakeDetectorEngine(
                thresholdG = 2.3f,
                requiredHits = 2,
                windowMs = 550L,
                cooldownMs = 1200L,
            )
        val strongAx = 2.5f * 9.80665f

        // Trigger first shake
        assertFalse(engine.onAcceleration(strongAx, 0f, 0f, 1000L))
        assertTrue(engine.onAcceleration(-strongAx, 0f, 0f, 1200L))

        // Directly afterwards within 1200ms cooldown (e.g., at 1500ms and 1700ms)
        assertFalse(engine.onAcceleration(strongAx, 0f, 0f, 1500L))
        assertFalse(engine.onAcceleration(-strongAx, 0f, 0f, 1700L))

        // After cooldown expires (1200 + 1200 = 2400ms)
        assertFalse(engine.onAcceleration(strongAx, 0f, 0f, 2500L))
        assertTrue(engine.onAcceleration(-strongAx, 0f, 0f, 2700L))
    }
}
