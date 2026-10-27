package dev.localvispr.settings

import android.content.Context
import dev.localvispr.models.ModelCatalog
import dev.localvispr.models.ModelSpec
import dev.localvispr.text.DictationStyle
import dev.localvispr.text.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Null means "detect automatically from Whisper's language token / word heuristic".
enum class LanguagePreference(val label: String, val fixedLanguage: Language?) {
    AUTO("Auto (DE/EN)", null),
    GERMAN("Deutsch", Language.GERMAN),
    ENGLISH("English", Language.ENGLISH),
}

data class UserSettings(
    val sttModelId: String = ModelCatalog.defaultStt.id,
    val llmModelId: String? = ModelCatalog.defaultLlm.id,
    val languagePreference: LanguagePreference = LanguagePreference.AUTO,
    val dictionaryWords: List<String> = emptyList(),
    val dictationStyle: DictationStyle = DictationStyle.CASUAL,
    val autoHideBubble: Boolean = true,
    val liveStreamingEnabled: Boolean = true,
    val shakeToDictateEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val snoozedUntilEpochMs: Long = 0L,
) {
    val sttModel: ModelSpec
        get() = ModelCatalog.byId(sttModelId) ?: ModelCatalog.defaultStt

    // Null when the user explicitly disabled the LLM cleanup pass (rules-only mode).
    val llmModel: ModelSpec?
        get() = llmModelId?.let(ModelCatalog::byId)
}

class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("localvispr_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserSettings> = _state.asStateFlow()

    fun setSttModel(spec: ModelSpec) = update {
        prefs.edit().putString(KEY_STT_MODEL, spec.id).apply()
        it.copy(sttModelId = spec.id)
    }

    fun setLlmModel(spec: ModelSpec?) = update {
        prefs.edit().putString(KEY_LLM_MODEL, spec?.id ?: NONE_SENTINEL).apply()
        it.copy(llmModelId = spec?.id)
    }

    fun setLanguagePreference(pref: LanguagePreference) = update {
        prefs.edit().putString(KEY_LANGUAGE, pref.name).apply()
        it.copy(languagePreference = pref)
    }

    fun setDictionaryWords(rawCsv: String) = update {
        val words = rawCsv.split(',', '\n').map(String::trim).filter(String::isNotEmpty)
        prefs.edit().putString(KEY_DICTIONARY, words.joinToString(",")).apply()
        it.copy(dictionaryWords = words)
    }

    fun setDictationStyle(style: DictationStyle) = update {
        prefs.edit().putString(KEY_DICTATION_STYLE, style.name).apply()
        it.copy(dictationStyle = style)
    }

    fun setAutoHideBubble(enabled: Boolean) = update {
        prefs.edit().putBoolean(KEY_AUTO_HIDE_BUBBLE, enabled).apply()
        it.copy(autoHideBubble = enabled)
    }

    fun setLiveStreamingEnabled(enabled: Boolean) = update {
        prefs.edit().putBoolean(KEY_LIVE_STREAMING, enabled).apply()
        it.copy(liveStreamingEnabled = enabled)
    }

    fun setShakeToDictateEnabled(enabled: Boolean) = update {
        prefs.edit().putBoolean(KEY_SHAKE_TO_DICTATE, enabled).apply()
        it.copy(shakeToDictateEnabled = enabled)
    }

    fun setHapticsEnabled(enabled: Boolean) = update {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        it.copy(hapticsEnabled = enabled)
    }

    fun setSnoozedUntil(epochMs: Long) = update {
        prefs.edit().putLong(KEY_SNOOZED_UNTIL, epochMs).apply()
        it.copy(snoozedUntilEpochMs = epochMs)
    }

    private inline fun update(block: (UserSettings) -> UserSettings) {
        _state.value = block(_state.value)
    }

    private fun load(): UserSettings {
        val stt = prefs.getString(KEY_STT_MODEL, null) ?: ModelCatalog.defaultStt.id
        val rawLlm = prefs.getString(KEY_LLM_MODEL, null)
        val llm =
            when (rawLlm) {
                null -> ModelCatalog.defaultLlm.id
                NONE_SENTINEL -> null
                else -> rawLlm
            }
        val lang =
            prefs.getString(KEY_LANGUAGE, null)?.let {
                runCatching { LanguagePreference.valueOf(it) }.getOrNull()
            } ?: LanguagePreference.AUTO
        val dict =
            prefs
                .getString(KEY_DICTIONARY, "")
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
        val style =
            prefs.getString(KEY_DICTATION_STYLE, null)?.let {
                runCatching { DictationStyle.valueOf(it) }.getOrNull()
            } ?: DictationStyle.CASUAL
        val autoHide = prefs.getBoolean(KEY_AUTO_HIDE_BUBBLE, true)
        val streaming = prefs.getBoolean(KEY_LIVE_STREAMING, true)
        val shake = prefs.getBoolean(KEY_SHAKE_TO_DICTATE, false)
        val haptics = prefs.getBoolean(KEY_HAPTICS, true)
        val snoozed = prefs.getLong(KEY_SNOOZED_UNTIL, 0L)
        return UserSettings(
            sttModelId = stt,
            llmModelId = llm,
            languagePreference = lang,
            dictionaryWords = dict,
            dictationStyle = style,
            autoHideBubble = autoHide,
            liveStreamingEnabled = streaming,
            shakeToDictateEnabled = shake,
            hapticsEnabled = haptics,
            snoozedUntilEpochMs = snoozed,
        )
    }

    private companion object {
        const val KEY_STT_MODEL = "stt_model"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LANGUAGE = "language"
        const val KEY_DICTIONARY = "dictionary"
        const val KEY_DICTATION_STYLE = "dictation_style"
        const val KEY_AUTO_HIDE_BUBBLE = "auto_hide_bubble"
        const val KEY_LIVE_STREAMING = "live_streaming"
        const val KEY_SHAKE_TO_DICTATE = "shake_to_dictate"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SNOOZED_UNTIL = "snoozed_until"
        const val NONE_SENTINEL = "__none__"
    }
}
