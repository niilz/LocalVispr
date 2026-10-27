package dev.localvispr.settings

import android.content.Context
import dev.localvispr.models.ModelCatalog
import dev.localvispr.models.ModelSpec
import dev.localvispr.text.Language
import dev.localvispr.text.StyleCategory
import dev.localvispr.text.WritingStyle
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
    val personalStyle: WritingStyle = WritingStyle.CASUAL,
    val workStyle: WritingStyle = WritingStyle.FORMAL,
    val emailStyle: WritingStyle = WritingStyle.FORMAL,
    val otherStyle: WritingStyle = WritingStyle.FORMAL,
    val liveStreamingEnabled: Boolean = true,
    val shakeToDictateEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val snoozedUntilEpochMs: Long = 0L,
    val onboardingCompleted: Boolean = false,
) {
    val sttModel: ModelSpec
        get() = ModelCatalog.byId(sttModelId) ?: ModelCatalog.defaultStt

    fun styleForPackage(packageName: String?): WritingStyle =
        when (StyleCategory.categoryForPackage(packageName)) {
            StyleCategory.PERSONAL -> personalStyle
            StyleCategory.WORK -> workStyle
            StyleCategory.EMAIL -> emailStyle
            StyleCategory.OTHER -> otherStyle
        }

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

    fun setCategoryStyle(category: StyleCategory, style: WritingStyle) = update {
        val key =
            when (category) {
                StyleCategory.PERSONAL -> KEY_PERSONAL_STYLE
                StyleCategory.WORK -> KEY_WORK_STYLE
                StyleCategory.EMAIL -> KEY_EMAIL_STYLE
                StyleCategory.OTHER -> KEY_OTHER_STYLE
            }
        prefs.edit().putString(key, style.name).apply()
        when (category) {
            StyleCategory.PERSONAL -> it.copy(personalStyle = style)
            StyleCategory.WORK -> it.copy(workStyle = style)
            StyleCategory.EMAIL -> it.copy(emailStyle = style)
            StyleCategory.OTHER -> it.copy(otherStyle = style)
        }
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

    fun setOnboardingCompleted(completed: Boolean) = update {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        it.copy(onboardingCompleted = completed)
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
        val pStyle =
            prefs.getString(KEY_PERSONAL_STYLE, null)?.let {
                runCatching { WritingStyle.valueOf(it) }.getOrNull()
            } ?: WritingStyle.CASUAL
        val wStyle =
            prefs.getString(KEY_WORK_STYLE, null)?.let {
                runCatching { WritingStyle.valueOf(it) }.getOrNull()
            } ?: WritingStyle.FORMAL
        val eStyle =
            prefs.getString(KEY_EMAIL_STYLE, null)?.let {
                runCatching { WritingStyle.valueOf(it) }.getOrNull()
            } ?: WritingStyle.FORMAL
        val oStyle =
            prefs.getString(KEY_OTHER_STYLE, null)?.let {
                runCatching { WritingStyle.valueOf(it) }.getOrNull()
            } ?: WritingStyle.FORMAL
        val streaming = prefs.getBoolean(KEY_LIVE_STREAMING, true)
        val shake = prefs.getBoolean(KEY_SHAKE_TO_DICTATE, false)
        val haptics = prefs.getBoolean(KEY_HAPTICS, true)
        val snoozed = prefs.getLong(KEY_SNOOZED_UNTIL, 0L)
        val onboarding = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        return UserSettings(
            sttModelId = stt,
            llmModelId = llm,
            languagePreference = lang,
            dictionaryWords = dict,
            personalStyle = pStyle,
            workStyle = wStyle,
            emailStyle = eStyle,
            otherStyle = oStyle,
            liveStreamingEnabled = streaming,
            shakeToDictateEnabled = shake,
            hapticsEnabled = haptics,
            snoozedUntilEpochMs = snoozed,
            onboardingCompleted = onboarding,
        )
    }

    private companion object {
        const val KEY_STT_MODEL = "stt_model"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LANGUAGE = "language"
        const val KEY_DICTIONARY = "dictionary"
        const val KEY_PERSONAL_STYLE = "style_personal"
        const val KEY_WORK_STYLE = "style_work"
        const val KEY_EMAIL_STYLE = "style_email"
        const val KEY_OTHER_STYLE = "style_other"
        const val KEY_LIVE_STREAMING = "live_streaming"
        const val KEY_SHAKE_TO_DICTATE = "shake_to_dictate"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SNOOZED_UNTIL = "snoozed_until"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val NONE_SENTINEL = "__none__"
    }
}
