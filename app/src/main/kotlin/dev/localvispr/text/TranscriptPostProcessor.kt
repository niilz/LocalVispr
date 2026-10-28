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
        val isEmail = StyleCategory.categoryForPackage(targetAppPackage) == StyleCategory.EMAIL
        val client =
            llm
                ?: return if (isEmail) {
                    EmailLayoutFormatter.format(ruleCleaned, language)
                } else {
                    ruleCleaned
                }

        val rawModelOutput =
            try {
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
            } catch (t: Throwable) {
                // Keep pure-JVM tests working without android.util.Log
                println("LocalVispr LLM completion FAILED: ${t.message}")
                t.printStackTrace()
                return if (isEmail) {
                    EmailLayoutFormatter.format(ruleCleaned, language)
                } else {
                    ruleCleaned
                }
            }

        val sanitised = sanitiseModelOutput(rawModelOutput)
        val candidate = DictionaryReplacements.apply(sanitised, parsedDict.replacements)
        val accepted =
            if (isPlausibleRewrite(input = ruleCleaned, candidate = candidate)) {
                candidate
            } else {
                ruleCleaned
            }
        return if (isEmail) EmailLayoutFormatter.format(accepted, language) else accepted
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
                if (StyleCategory.categoryForPackage(targetAppPackage) == StyleCategory.EMAIL) {
                    when (language) {
                        Language.GERMAN ->
                            "\n\nWICHTIG für E-Mails: Wenn der diktierte Text eine Begrüßung enthält, setze eine Leerzeile dahinter. " +
                                "Gliedere Sinneinheiten mit echten Absätzen (Leerzeilen). " +
                                "Wenn eine Grußformel diktiert wurde, setze eine Leerzeile davor und den Namen auf eine neue Zeile. " +
                                "Erfinde niemals Grußformeln, Begrüßungen oder Namen hinzu, die nicht diktiert wurden.\n" +
                                "Beispiel:\n" +
                                "Eingabe: hallo anna vielen dank für die rückmeldung ich prüfe das bis morgen viele grüße thomas\n" +
                                "Ausgabe:\n" +
                                "Hallo Anna,\n\n" +
                                "vielen Dank für die Rückmeldung. Ich prüfe das bis morgen.\n\n" +
                                "Viele Grüße\n" +
                                "Thomas"
                        Language.ENGLISH ->
                            "\n\nIMPORTANT for emails: When the dictation contains a greeting, place a blank line after it. " +
                                "Structure distinct points with clean blank lines between paragraphs. " +
                                "When a sign-off was dictated, place a blank line before it and the sender name on a new line. " +
                                "Never invent greetings, sign-offs, or names that were not spoken.\n" +
                                "Example:\n" +
                                "Input: hi anna thanks for the update i will review this by tomorrow best regards thomas\n" +
                                "Output:\n" +
                                "Hi Anna,\n\n" +
                                "thanks for the update. I will review this by tomorrow.\n\n" +
                                "Best regards,\n" +
                                "Thomas"
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

        private val CHATTY_ASSISTANT_PREFIX =
            Regex(
                """^(?:Natürlich|Gerne|Hier ist|Sicher|Sure|Certainly|Here is|As requested|Here's)[!,.:\s]""",
                RegexOption.IGNORE_CASE,
            )

        // Guard against small-model runaway loops or catastrophic hallucinations,
        // while allowing formatted emails (with blank lines, greetings, sign-offs).
        internal fun isPlausibleRewrite(input: String, candidate: String): Boolean {
            if (candidate.isBlank()) return false
            if (
                CHATTY_ASSISTANT_PREFIX.containsMatchIn(candidate) &&
                    !CHATTY_ASSISTANT_PREFIX.containsMatchIn(input)
            ) {
                return false
            }
            val maxAllowedLength = (input.length * 4).coerceAtLeast(input.length + 300)
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
