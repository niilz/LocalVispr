package dev.localvispr.audio

import kotlin.math.sqrt

object PcmSamples {

    const val SAMPLE_RATE_HZ = 16_000
    private const val SILENCE_RMS_FLOOR = 0.003f

    fun shortsToFloats(pcm: ShortArray, count: Int = pcm.size): FloatArray =
        FloatArray(count) { i -> pcm[i] / 32768.0f }

    // Whisper hallucinates stock phrases ("Untertitel von...", "Thank you.") when
    // given silence. Skipping decoding below a tiny RMS threshold prevents that.
    fun isEffectivelySilent(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return true
        var sumSquares = 0.0
        for (s in samples) sumSquares += s * s
        val rms = sqrt(sumSquares / samples.size).toFloat()
        return rms < SILENCE_RMS_FLOOR
    }
}
