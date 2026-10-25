package dev.localvispr.llm

import com.google.ai.edge.litertlm.Backend
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
        if (spec == null || !repository.isInstalled(spec)) return null
        val activeEngine = ensureEngine(spec)
        return LlmClient { systemPrompt, userMessage ->
            val config =
                ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    // Low temperature keeps edits faithful to the spoken words.
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2),
                )
            activeEngine.createConversation(config).use { conversation ->
                conversation.sendMessage(userMessage).toString()
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
}
