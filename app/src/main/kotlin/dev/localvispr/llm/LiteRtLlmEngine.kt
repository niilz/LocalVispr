package dev.localvispr.llm

import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dev.localvispr.models.ModelRepository
import dev.localvispr.models.ModelSpec
import dev.localvispr.text.LlmClient
import java.io.File

// ==============================================================================
// On-device LLM wrapper backed by Google LiteRT-LM
// ==============================================================================
//
// Each dictation opens a fresh single-turn `Conversation` so previous utterances
// never bleed into the next rewrite and the KV-cache stays small.

class LiteRtLlmEngine(private val repository: ModelRepository, private val cacheDir: File) {
    private var loadedModelId: String? = null
    private var engine: Engine? = null

    @Synchronized
    fun clientFor(spec: ModelSpec?): LlmClient? {
        // `liblitertlm_jni.so` emits ARMv8.2+ instructions (dotprod/i8mm) that
        // trigger SIGILL on the Android QEMU emulator's baseline Cortex-A57 CPU.
        if (isProbablyEmulator()) {
            android.util.Log.d("LocalVispr", "clientFor: skipping native LLM on QEMU emulator")
            return null
        }
        if (spec == null) {
            android.util.Log.d("LocalVispr", "clientFor: spec is null (rules-only mode)")
            return null
        }
        if (!repository.isInstalled(spec)) {
            android.util.Log.w("LocalVispr", "clientFor: model ${spec.id} not installed")
            return null
        }
        val activeEngine =
            try {
                ensureEngine(spec)
            } catch (t: Throwable) {
                android.util.Log.e("LocalVispr", "clientFor: ensureEngine failed for ${spec.id}", t)
                return null
            }
        return LlmClient { systemPrompt, userMessage ->
            val config =
                ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    // Low temperature keeps edits faithful to the spoken words.
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
                )
            activeEngine.createConversation(config).use { conversation ->
                val response = conversation.sendMessage(userMessage)
                val extracted =
                    response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                        .trim()
                if (extracted.isNotEmpty()) {
                    extracted
                } else {
                    response.toString()
                }
            }
        }
    }

    @Synchronized
    fun close() {
        runCatching { engine?.close() }
        engine = null
        loadedModelId = null
    }

    private fun ensureEngine(spec: ModelSpec): Engine {
        engine?.let { if (loadedModelId == spec.id) return it }
        close()

        val modelFile = repository.llmFile(spec)
        val config =
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = cacheDir.absolutePath,
                maxNumTokens = 512,
            )
        return Engine(config).also {
            it.initialize()
            engine = it
            loadedModelId = spec.id
        }
    }

    private fun isProbablyEmulator(): Boolean =
        Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("sdk_gphone")
}
