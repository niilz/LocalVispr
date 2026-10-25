package dev.localvispr.settings

import android.content.Context
import dev.localvispr.models.ModelCatalog
import dev.localvispr.models.ModelSpec
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
        return UserSettings(
            sttModelId = stt,
            llmModelId = llm,
            languagePreference = lang,
            dictionaryWords = dict,
        )
    }

    private companion object {
        const val KEY_STT_MODEL = "stt_model"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LANGUAGE = "language"
        const val KEY_DICTIONARY = "dictionary"
        const val NONE_SENTINEL = "__none__"
    }
}
