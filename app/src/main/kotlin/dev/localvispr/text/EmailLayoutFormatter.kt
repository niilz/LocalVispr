package dev.localvispr.text

// ==============================================================================
// Deterministic email layout safety net
// ==============================================================================
//
// Small on-device models (e.g. Qwen3-0.6B, SmolLM2-135M) struggle to reliably
// emit paragraph breaks and blank lines, even when explicitly instructed in the
// system prompt. Because email structure adheres to predictable conventions
// (greeting -> blank line -> body -> blank line -> sign-off -> name), this
// formatter acts as a reliable post-processing safety net.
//
// It only runs when LocalVispr detects an email client (e.g. Gmail, Outlook,
// Spark, Thunderbird). It detects greetings at the start and sign-offs at the
// end, inserting proper `\n\n` separators without hallucinating names or phrases
// that the user never dictated.

object EmailLayoutFormatter {

    // --------------------------------------------------------------------------
    // Greeting patterns (matched strictly at start of text)
    // --------------------------------------------------------------------------

    private val GERMAN_GREETING =
        Regex(
            """^(Sehr geehrte Damen und Herren|Sehr geehrte[rn]?\s+(?:Frau|Herrn?)[^,!\n]+|Liebe[rs]?\s+[^,!\n]+|Guten\s+(?:Morgen|Tag|Abend)(?:\s+[^,!\n]+)?|Hallo(?:\s+zusammen|\s+allerseits|\s+[^,!\n]+)?|Hi(?:\s+[^,!\n]+)?|Hey(?:\s+[^,!\n]+)?)[,!:]?\s*""",
            RegexOption.IGNORE_CASE,
        )

    private val ENGLISH_GREETING =
        Regex(
            """^(Dear\s+[^,!\n]+|Good\s+(?:morning|afternoon|evening)(?:\s+[^,!\n]+)?|Hi(?:\s+all|\s+team|\s+everyone|\s+[^,!\n]+)?|Hello(?:\s+all|\s+team|\s+everyone|\s+[^,!\n]+)?|Hey(?:\s+all|\s+team|\s+everyone|\s+[^,!\n]+)?)[,!:]?\s*""",
            RegexOption.IGNORE_CASE,
        )

    // --------------------------------------------------------------------------
    // Sign-off patterns (matched towards the end of text)
    // --------------------------------------------------------------------------

    private val GERMAN_SIGN_OFF =
        Regex(
            """(?:^|\n\s*|\s+)(Mit\s+freundlichen\s+Grüßen|Mit\s+herzlichen\s+Grüßen|Mit\s+besten\s+Grüßen|Viele\s+Grüße|Liebe\s+Grüße|Beste\s+Grüße|Herzliche\s+Grüße|Schöne\s+Grüße|Freundliche\s+Grüße|Danke\s+und\s+viele\s+Grüße|Bis\s+bald|Bis\s+dann|Bis\s+morgen)[,!:]?(?:\s+(.+))?$""",
            RegexOption.IGNORE_CASE,
        )

    private val ENGLISH_SIGN_OFF =
        Regex(
            """(?:^|\n\s*|\s+)(Best\s+regards|Kind\s+regards|Warm\s+regards|With\s+best\s+regards|All\s+the\s+best|Best|Regards|Sincerely|Yours\s+sincerely|Cheers|Thanks\s+and\s+best\s+regards|Thank\s+you|Thanks)[,!:]?(?:\s+(.+))?$""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Enforces blank lines around greetings and sign-offs for email dictations. Leaves non-email
     * text or emails without conventional markers intact.
     */
    fun format(text: String, language: Language): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val greetingRegex = if (language == Language.GERMAN) GERMAN_GREETING else ENGLISH_GREETING
        val signOffRegex = if (language == Language.GERMAN) GERMAN_SIGN_OFF else ENGLISH_SIGN_OFF

        // 1. Separate greeting from the rest of the text
        val greetingMatch = greetingRegex.find(trimmed)
        var greeting: String? = null
        var remainder = trimmed

        if (greetingMatch != null && greetingMatch.range.first == 0) {
            val matchedGreeting = greetingMatch.groupValues[1].trim()
            greeting = ensurePunctuation(matchedGreeting, ",")
            remainder = trimmed.substring(greetingMatch.range.last + 1).trim()
        }

        // 2. Separate sign-off (and trailing sender name) from remainder
        var body = remainder
        var signOffPhrase: String? = null
        var senderName: String? = null

        val signOffMatch = signOffRegex.find(remainder)
        if (signOffMatch != null) {
            body = remainder.substring(0, signOffMatch.range.first).trim()
            val matchedSignOff = signOffMatch.groupValues[1].trim()
            signOffPhrase =
                if (language == Language.ENGLISH) {
                    ensurePunctuation(matchedSignOff, ",")
                } else {
                    matchedSignOff
                }
            val trailing = signOffMatch.groupValues.getOrNull(2)?.trim().orEmpty()
            if (trailing.isNotEmpty()) {
                senderName = trailing
            }
        }

        // 3. Assemble the parts with canonical blank-line spacing
        val parts = mutableListOf<String>()

        if (greeting != null) {
            parts.add(greeting)
        }

        if (body.isNotEmpty()) {
            // Clean up any internal multi-line blank gaps to canonical \n\n
            val normalizedBody = body.replace(Regex("""\n\s*\n+"""), "\n\n").trim()
            parts.add(normalizedBody)
        }

        val signOffBlock =
            when {
                signOffPhrase != null && senderName != null -> "$signOffPhrase\n$senderName"
                signOffPhrase != null -> signOffPhrase
                senderName != null -> senderName
                else -> null
            }

        if (signOffBlock != null) {
            parts.add(signOffBlock)
        }

        return parts.joinToString("\n\n")
    }

    private fun ensurePunctuation(phrase: String, punct: String): String {
        val last = phrase.lastOrNull()
        return if (last != null && last in ",.!:") phrase else "$phrase$punct"
    }
}
