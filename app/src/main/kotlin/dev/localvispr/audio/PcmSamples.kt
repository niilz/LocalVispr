package dev.localvispr.audio

import kotlin.math.log10
import kotlin.math.sqrt

object PcmSamples {

    const val SAMPLE_RATE_HZ = 16_000
    private const val SILENCE_RMS_FLOOR = 0.003f
    // Matches the dB noise-floor / SNR mapping used by the reference app's VoiceRecorder:
    // ambient room noise below -48 dBFS maps to 0.0 so the equalizer bars stay still
    // until actual speech arrives.
    private const val NOISE_FLOOR_DB = -48.0
    private const val TARGET_SNR_DB = 32.0

    fun shortsToFloats(pcm: ShortArray, count: Int = pcm.size): FloatArray =
        FloatArray(count) { i -> pcm[i] / 32768.0f }

    fun speechLevel(pcm: ShortArray, count: Int = pcm.size): Float {
        if (count <= 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = pcm[i] / 32767.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count)
        val db = 20.0 * log10(rms + 1e-9)
        return ((db - NOISE_FLOOR_DB) / TARGET_SNR_DB).toFloat().coerceIn(0f, 1f)
    }

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
