package dev.localvispr

import android.content.Context
import dev.localvispr.llm.LiteRtLlmEngine
import dev.localvispr.models.ModelDownloader
import dev.localvispr.models.ModelRepository
import dev.localvispr.settings.SettingsStore
import dev.localvispr.stt.SherpaWhisperTranscriber
import java.io.File

// Process-wide singletons shared between MainActivity and FloatingBubbleService.
class AppContainer private constructor(context: Context) {

    val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }
    val models = ModelRepository(modelsDir)
    val downloader = ModelDownloader(models)
    val settings = SettingsStore(context)
    val transcriber = SherpaWhisperTranscriber(models)
    val llmEngine = LiteRtLlmEngine(models, context.cacheDir)

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance
                ?: synchronized(this) {
                    instance ?: AppContainer(context.applicationContext).also { instance = it }
                }
    }
}
