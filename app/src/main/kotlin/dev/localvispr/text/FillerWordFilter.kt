package dev.localvispr.text

// ==============================================================================
// Deterministic filler-word and hesitation filter
// ==============================================================================
//
// Even when an on-device LLM is configured, we run this fast regex pass first
// (and as a fallback when no LLM is downloaded yet). Removing obvious vocal
// pauses ("ähm", "uh", "[BLANK_AUDIO]") before prompting a small 270M/600M
// model noticeably reduces hallucinations because the model has less noise to
// interpret.

object FillerWordFilter {

    // Whisper emits non-speech events in brackets, parentheses, or asterisks.
    private val NON_SPEECH_TAG = Regex("""\[[^\]]*]|\([^)]*\)|\*[^*]+\*""")

    // German subordinate clauses must keep their leading comma when a filler in
    // front of the conjunction is removed ("Ich glaube, äh, dass..." ->
    // "Ich glaube, dass...").
    private val GERMAN_SUBORDINATORS =
        setOf(
            "dass",
            "weil",
            "obwohl",
            "wenn",
            "falls",
            "sodass",
            "damit",
            "ob",
            "während",
            "bevor",
            "nachdem",
        )

    // Words that can legitimately appear twice in a row. Everything else repeated
    // back-to-back ("Ich ich gehe", "to to go") is treated as a stutter.
    private val ALLOWED_DOUBLES = setOf("die", "der", "das", "sie", "had", "that", "very")

    private val STUTTER = Regex("""\b(\p{L}+)(?:\s+\1\b)+""", setOf(RegexOption.IGNORE_CASE))

    private val SPACE_BEFORE_PUNCT = Regex("""\s+([,.;:!?])""")
    private val REPEATED_COMMAS = Regex(""",(\s*,)+""")
    private val COMMA_BEFORE_END_PUNCT = Regex(""",\s*([.;:!?])""")
    private val LEADING_PUNCT = Regex("""^[,.;:!]+\s*""")
    private val MULTI_SPACE = Regex("""\s{2,}""")
    private val SENTENCE_START = Regex("""(?:^|(?<=[.!?]\s))(\p{Ll})""")

    fun clean(raw: String, language: Language): String {
        val normalizedPunct = SpokenPunctuationNormalizer.normalize(raw, language)
        val fillers = fillersPattern(language)
        var text = NON_SPEECH_TAG.replace(normalizedPunct, " ")

        // Mid-sentence filler wrapped in commas: "Ich wollte, äh, sagen" -> "Ich wollte sagen",
        // except before German subordinators where the comma belongs to the clause.
        text =
            Regex(""",\s*(?:$fillers)\s*,\s*(\p{L}+)""", RegexOption.IGNORE_CASE).replace(text) {
                match ->
                val nextWord = match.groupValues[1]
                val keepComma =
                    language == Language.GERMAN && nextWord.lowercase() in GERMAN_SUBORDINATORS
                if (keepComma) ", $nextWord" else " $nextWord"
            }

        // Remaining standalone fillers together with an optional trailing comma or ellipsis.
        text =
            Regex(
                    """(?<![\p{L}])(?:$fillers)(?![\p{L}])(?:\s*(?:,|\.{2,}))?""",
                    RegexOption.IGNORE_CASE,
                )
                .replace(text, " ")

        // Collapse stuttered repetitions while preserving the casing of the first occurrence.
        text =
            STUTTER.replace(text) { match ->
                val first = match.groupValues[1]
                if (first.lowercase() in ALLOWED_DOUBLES) match.value else first
            }

        text = SPACE_BEFORE_PUNCT.replace(text, "$1")
        text = REPEATED_COMMAS.replace(text, ",")
        text = COMMA_BEFORE_END_PUNCT.replace(text, "$1")
        text = MULTI_SPACE.replace(text, " ").trim()
        text = LEADING_PUNCT.replace(text, "").trim()
        // If the utterance only contained fillers plus trailing dots, nothing words-like remains.
        if (text.none { it.isLetterOrDigit() }) return ""

        return SENTENCE_START.replace(text) { it.value.uppercase() }
    }

    private fun fillersPattern(language: Language): String =
        when (language) {
            // Note: "um" is intentionally absent in German because it is a common preposition ("um
            // acht").
            Language.GERMAN -> """ähm+|äh+|öhm+|öh+|hm+|mhm+|tja"""
            Language.ENGLISH -> """u+h+m+|u+m+|u+h+|er+m+|er+|a+h+|hm+|mhm+|uh-huh"""
        }
}
