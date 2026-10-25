package dev.localvispr.stt

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import dev.localvispr.audio.PcmSamples
import dev.localvispr.models.ModelRepository
import dev.localvispr.models.ModelSpec
import dev.localvispr.text.Language

data class TranscriptionResult(val rawText: String, val detectedLanguage: Language)

// ==============================================================================
// Offline Whisper speech-to-text via Sherpa-ONNX
// ==============================================================================
//
// Lazily loads the selected Whisper model from `ModelRepository` and recreates
// the native recognizer only when the user switches the model or language hint.

class SherpaWhisperTranscriber(private val repository: ModelRepository) {

    private var activeKey: Pair<String, String>? = null
    private var recognizer: OfflineRecognizer? = null

    @Synchronized
    fun transcribe(
        samples: FloatArray,
        spec: ModelSpec,
        fixedLanguage: Language?,
    ): TranscriptionResult {
        if (PcmSamples.isEffectivelySilent(samples)) {
            return TranscriptionResult("", fixedLanguage ?: Language.GERMAN)
        }
        // Empty string tells Whisper to auto-detect the spoken language.
        val langHint = fixedLanguage?.code.orEmpty()
        val rec = ensureRecognizer(spec, langHint)
        val stream = rec.createStream()
        return try {
            stream.acceptWaveform(samples, PcmSamples.SAMPLE_RATE_HZ)
            rec.decode(stream)
            val result = rec.getResult(stream)
            val text = result.text.trim()
            val language =
                fixedLanguage
                    ?: Language.fromCode(result.lang)
                    ?: Language.guess(text, default = Language.GERMAN)
            TranscriptionResult(rawText = text, detectedLanguage = language)
        } finally {
            stream.release()
        }
    }

    @Synchronized
    fun close() {
        recognizer?.release()
        recognizer = null
        activeKey = null
    }

    private fun ensureRecognizer(spec: ModelSpec, languageHint: String): OfflineRecognizer {
        val key = spec.id to languageHint
        recognizer?.let { if (activeKey == key) return it }
        recognizer?.release()

        val files = repository.whisperFiles(spec)
        val config =
            OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = PcmSamples.SAMPLE_RATE_HZ, featureDim = 80),
                modelConfig =
                    OfflineModelConfig(
                        whisper =
                            OfflineWhisperModelConfig(
                                encoder = files.encoder,
                                decoder = files.decoder,
                                language = languageHint,
                                task = "transcribe",
                            ),
                        tokens = files.tokens,
                        numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                        modelType = "whisper",
                    ),
            )
        return OfflineRecognizer(assetManager = null, config = config).also {
            recognizer = it
            activeKey = key
        }
    }
}
