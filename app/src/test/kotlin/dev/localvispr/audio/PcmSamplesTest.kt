package dev.localvispr.audio

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PcmSamplesTest {

    @Test
    fun normalises16BitPcmIntoMinusOneToOneRange() {
        val floats = PcmSamples.shortsToFloats(shortArrayOf(0, Short.MAX_VALUE, -32768))
        assertEquals(0f, floats[0], absoluteTolerance = 1e-6f)
        assertEquals(1f, floats[1], absoluteTolerance = 1e-4f)
        assertEquals(-1f, floats[2], absoluteTolerance = 1e-6f)
    }

    @Test
    fun detectsSilentRecordingsSoWhisperDoesNotHallucinateOnEmptyAudio() {
        // Whisper is notorious for emitting "Vielen Dank." or "Thank you for watching"
        // when fed near-zero energy.
        assertTrue(PcmSamples.isEffectivelySilent(FloatArray(8000) { 0.0005f }))
        assertFalse(PcmSamples.isEffectivelySilent(FloatArray(8000) { 0.1f }))
    }
}
