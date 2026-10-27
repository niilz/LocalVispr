package dev.localvispr.text

// ==============================================================================
// Two-stage transcript post-processor (deterministic rules -> on-device LLM)
// ==============================================================================
//
// The reference app performs its cleanup on a cloud server and sends surrounding
// field text plus a user dictionary along with the audio. Locally we only have a
// sub-billion-parameter model (e.g. Qwen3 0.6B or Gemma 3 270M), which is prone
// to two failure modes:
//   1. Emitting `<think>...</think>` blocks or wrapping the answer in quotes.
//   2. Answering a dictated question instead of editing it.
//
// We mitigate both by running `FillerWordFilter` first, keeping the prompt tight,
// stripping reasoning tags, and rejecting outputs whose length diverges wildly
// from the input.

fun interface LlmClient {
    fun complete(systemPrompt: String, userMessage: String): String
}

class TranscriptPostProcessor(private val llm: LlmClient?) {

    fun process(
        rawTranscript: String,
        language: Language,
        surroundingText: String? = null,
        dictionaryWords: List<String> = emptyList(),
        style: WritingStyle = WritingStyle.CASUAL,
        targetAppPackage: String? = null,
    ): String {
        val parsedDict = DictionaryReplacements.parse(dictionaryWords)
        val ruleCleaned = FillerWordFilter.clean(rawTranscript, language, parsedDict.replacements)
        if (ruleCleaned.isEmpty()) return ""
        val client = llm ?: return ruleCleaned

        val rawModelOutput =
            runCatching {
                    client.complete(
                        systemPrompt =
                            buildSystemPrompt(
                                language = language,
                                dictionaryWords = dictionaryWords,
                                style = style,
                                targetAppPackage = targetAppPackage,
                            ),
                        userMessage = buildUserMessage(ruleCleaned, surroundingText),
                    )
                }
                .getOrNull() ?: return ruleCleaned

        val sanitised = sanitiseModelOutput(rawModelOutput)
        val candidate = DictionaryReplacements.apply(sanitised, parsedDict.replacements)
        return if (isPlausibleRewrite(input = ruleCleaned, candidate = candidate)) {
            candidate
        } else {
            ruleCleaned
        }
    }

    companion object {
        private val THINK_BLOCK = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)
        private val CODE_FENCE = Regex("""^```(?:\w+)?\s*|\s*```$""")
        private val WRAPPING_QUOTES = Regex("""^["„“”'‚‘’]\s*([\s\S]*?)\s*["„“”'‚‘’]$""")

        fun buildSystemPrompt(
            language: Language,
            dictionaryWords: List<String> = emptyList(),
            style: WritingStyle = WritingStyle.CASUAL,
            targetAppPackage: String? = null,
        ): String {
            val parsed = DictionaryReplacements.parse(dictionaryWords)
            val vocabHint =
                if (parsed.vocabulary.isEmpty()) {
                    ""
                } else {
                    "\nPreferred spellings: ${parsed.vocabulary.joinToString(", ")}."
                }
            val replHint =
                if (parsed.replacements.isEmpty()) {
                    ""
                } else {
                    val rules =
                        parsed.replacements.entries.joinToString(", ") {
                            "'${it.key}' -> '${it.value}'"
                        }
                    "\nMandatory word replacements: $rules."
                }
            val styleHint = "\nStil-Vorgabe: ${style.promptInstruction(language)}"
            val emailGuidance =
                if (
                    style == WritingStyle.FORMAL ||
                        StyleCategory.categoryForPackage(targetAppPackage) == StyleCategory.EMAIL
                ) {
                    when (language) {
                        Language.GERMAN ->
                            "\n\nWICHTIG für E-Mails: Gliedere den Text immer mit echten Absätzen (Leerzeilen)!\n" +
                                "Beispiel:\n" +
                                "Hi Name,\n\n" +
                                "vielen Dank für das nette Meeting. Ich freue mich auf das nächste.\n\n" +
                                "Viele Grüße\n" +
                                "Nils"
                        Language.ENGLISH ->
                            "\n\nIMPORTANT for emails: Always structure the message with clean blank lines between paragraphs, after greetings, and before sign-offs!\n" +
                                "Example:\n" +
                                "Hi Name,\n\n" +
                                "thanks for the meeting. Looking forward to the next one.\n\n" +
                                "Best regards\n" +
                                "Nils"
                    }
                } else {
                    ""
                }
            val appHint =
                if (targetAppPackage.isNullOrBlank()) {
                    ""
                } else {
                    "\nTarget app: ${targetAppPackage.trim()}."
                }
            val base =
                when (language) {
                    Language.GERMAN ->
                        "Du bereinigst diktierten Text auf Deutsch. Entferne Fülllaute, Stottern und " +
                            "Versprecher, korrigiere Zeichensetzung und Groß-/Kleinschreibung, aber " +
                            "ändere niemals den Sinn und beantworte keine Fragen. Gib ausschließlich " +
                            "den bereinigten Text aus, ohne Erklärungen oder Anführungszeichen."
                    Language.ENGLISH ->
                        "You clean up dictated English text. Remove filler words, stutters, and false " +
                            "starts, fix punctuation and capitalization, but never change the meaning " +
                            "and never answer questions. Output only the cleaned text, with no " +
                            "explanations or quotation marks."
                }
            return base + vocabHint + replHint + styleHint + emailGuidance + appHint
        }

        fun formatHotwords(dictionaryWords: List<String>): String =
            dictionaryWords.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        fun buildUserMessage(preCleanedTranscript: String, surroundingText: String?): String {
            val context = surroundingText?.takeLast(160)?.trim().orEmpty()
            // `/no_think` disables chain-of-thought on Qwen3 models and is harmless on Gemma.
            return if (context.isEmpty()) {
                "/no_think\n<dictation>$preCleanedTranscript</dictation>"
            } else {
                "/no_think\n<context>$context</context>\n<dictation>$preCleanedTranscript</dictation>"
            }
        }

        fun sanitiseModelOutput(raw: String): String {
            var s = THINK_BLOCK.replace(raw, "").trim()
            s = CODE_FENCE.replace(s, "").trim()
            WRAPPING_QUOTES.matchEntire(s)?.let { s = it.groupValues[1].trim() }
            return s
        }

        // Guard against small-model failure modes: empty output, or a chatty explanation
        // that is much longer than the dictated sentence.
        internal fun isPlausibleRewrite(input: String, candidate: String): Boolean {
            if (candidate.isEmpty()) return false
            val maxAllowedLength = (input.length * 2).coerceAtLeast(input.length + 24)
            return candidate.length <= maxAllowedLength
        }

        // When the user's cursor sits right after a word ("Hallo|"), committing "Welt"
        // without a leading space would produce "HalloWelt".
        fun formatForInsertion(cleanedText: String, textBeforeCursor: String?): String {
            if (cleanedText.isEmpty() || textBeforeCursor.isNullOrEmpty()) return cleanedText
            val lastChar = textBeforeCursor.last()
            val firstChar = cleanedText.first()
            val needsSpace =
                !lastChar.isWhitespace() && lastChar !in "([{\"'„“" && firstChar !in ",.;:!?)]}"
            return if (needsSpace) " $cleanedText" else cleanedText
        }
    }
}
