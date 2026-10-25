package dev.localvispr.text

enum class Language(val code: String) {
    GERMAN("de"),
    ENGLISH("en");

    companion object {
        private val WORD_REGEX = Regex("\\p{L}+")

        private val GERMAN_FUNCTION_WORDS =
            setOf(
                "ich",
                "du",
                "wir",
                "sie",
                "nicht",
                "und",
                "oder",
                "aber",
                "dass",
                "weil",
                "wenn",
                "ein",
                "eine",
                "der",
                "die",
                "das",
                "mit",
                "auf",
                "für",
                "ist",
                "sind",
            )

        private val ENGLISH_FUNCTION_WORDS =
            setOf(
                "i",
                "you",
                "we",
                "they",
                "not",
                "and",
                "or",
                "but",
                "that",
                "because",
                "when",
                "the",
                "with",
                "for",
                "is",
                "are",
                "this",
                "should",
                "would",
            )

        // Whisper sometimes wraps language tags as "<|de|>".
        fun fromCode(raw: String?): Language? {
            val cleaned =
                raw?.trim()?.removePrefix("<|")?.removeSuffix("|>")?.lowercase() ?: return null
            return entries.firstOrNull { it.code == cleaned }
        }

        // Used when the user selected automatic language detection and the STT
        // backend did not report a language tag.
        fun guess(text: String, default: Language = GERMAN): Language {
            val words = WORD_REGEX.findAll(text.lowercase()).map { it.value }.toList()
            val deScore =
                words.count { it in GERMAN_FUNCTION_WORDS } + text.count { it in "äöüßÄÖÜ" }
            val enScore = words.count { it in ENGLISH_FUNCTION_WORDS }
            return when {
                deScore > enScore -> GERMAN
                enScore > deScore -> ENGLISH
                else -> default
            }
        }
    }
}
