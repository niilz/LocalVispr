package dev.localvispr.text

// ==============================================================================
// Dictionary word list & automatic replacement engine
// ==============================================================================
//
// Supports both simple vocabulary words ("LocalVispr", "Sherpa-ONNX") and
// explicit replacement rules ("Steffi -> Stephie" or "mfg = Mit freundlichen Grüßen").
// Replacements are applied case-insensitively on whole word boundaries.

data class ParsedDictionary(
    val vocabulary: List<String>,
    // Lowercase trigger word -> replacement string
    val replacements: Map<String, String>,
)

object DictionaryReplacements {

    private val SPLIT_DELIMITERS = Regex("""\s*(?:->|=>|=)\s*""")

    fun parse(rawEntries: List<String>): ParsedDictionary {
        val vocab = mutableListOf<String>()
        val repl = mutableMapOf<String, String>()

        for (raw in rawEntries) {
            val entry = raw.trim()
            if (entry.isEmpty()) continue
            if (
                entry.contains("->") ||
                    entry.contains("=>") ||
                    (entry.contains("=") && !entry.startsWith("="))
            ) {
                val parts = entry.split(SPLIT_DELIMITERS, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    val trigger = parts[0].trim().lowercase()
                    val target = parts[1].trim()
                    repl[trigger] = target
                    continue
                }
            }
            vocab.add(entry)
        }
        return ParsedDictionary(vocab, repl)
    }

    fun apply(text: String, replacements: Map<String, String>): String {
        if (text.isEmpty() || replacements.isEmpty()) return text
        var result = text
        for ((trigger, target) in replacements) {
            // Whole word boundary match (letters/digits/underscores)
            val regex =
                Regex(
                    """(?<!\p{L})""" + Regex.escape(trigger) + """(?!\p{L})""",
                    RegexOption.IGNORE_CASE,
                )
            result = regex.replace(result, target)
        }
        return result
    }
}
