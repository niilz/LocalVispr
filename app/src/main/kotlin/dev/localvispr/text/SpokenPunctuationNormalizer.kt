package dev.localvispr.text

// ==============================================================================
// Spoken punctuation normalizer (German & English)
// ==============================================================================
//
// Converts explicitly spoken punctuation commands into real characters:
// - "Name Bindestrich Nachname" -> "Name-Nachname"
// - "Hallo Komma" -> "Hallo,"
// - "neue Zeile" -> "\n", "Absatz" -> "\n\n"
// - "Fragezeichen" -> "?", "Ausrufezeichen" -> "!"

object SpokenPunctuationNormalizer {

    private val DE_HYPHEN = Regex("""(?<=\S)\s*(?:[Bb]indestrich|[Gg]edankenstrich)\s*(?=\S)""")
    private val EN_HYPHEN = Regex("""(?<=\S)\s*(?:hyphen|dash)\s*(?=\S)""", RegexOption.IGNORE_CASE)

    private val DE_COMMA = Regex("""\s+[Kk]omma\b""")
    private val EN_COMMA = Regex("""\s+comma\b""", RegexOption.IGNORE_CASE)

    private val DE_PERIOD = Regex("""\s+[Pp]unkt\b""")
    private val EN_PERIOD = Regex("""\s+(?:period|full stop)\b""", RegexOption.IGNORE_CASE)

    private val DE_QUESTION = Regex("""\s+[Ff]ragezeichen\b""")
    private val EN_QUESTION = Regex("""\s+question mark\b""", RegexOption.IGNORE_CASE)

    private val DE_EXCLAMATION = Regex("""\s+[Aa]usrufezeichen\b""")
    private val EN_EXCLAMATION =
        Regex("""\s+(?:exclamation mark|exclamation point)\b""", RegexOption.IGNORE_CASE)

    private val DE_COLON = Regex("""\s+[Dd]oppelpunkt\b""")
    private val EN_COLON = Regex("""\s+colon\b""", RegexOption.IGNORE_CASE)

    private val DE_NEWLINE = Regex("""\s+[Nn]eue [Zz]eile\s*""")
    private val EN_NEWLINE = Regex("""\s+new line\s*""", RegexOption.IGNORE_CASE)

    private val DE_PARAGRAPH = Regex("""\s+(?:[Nn]euer [Aa]bsatz|[Aa]bsatz)\s+""")
    private val EN_PARAGRAPH = Regex("""\s+new paragraph\s+""", RegexOption.IGNORE_CASE)

    fun normalize(raw: String, language: Language): String {
        var text = raw
        when (language) {
            Language.GERMAN -> {
                text = DE_HYPHEN.replace(text, "-")
                text = DE_COMMA.replace(text, ",")
                text = DE_PERIOD.replace(text, ".")
                text = DE_QUESTION.replace(text, "?")
                text = DE_EXCLAMATION.replace(text, "!")
                text = DE_COLON.replace(text, ":")
                text = DE_PARAGRAPH.replace(text, "\n\n")
                text = DE_NEWLINE.replace(text, "\n")
            }
            Language.ENGLISH -> {
                text = EN_HYPHEN.replace(text, "-")
                text = EN_COMMA.replace(text, ",")
                text = EN_PERIOD.replace(text, ".")
                text = EN_QUESTION.replace(text, "?")
                text = EN_EXCLAMATION.replace(text, "!")
                text = EN_COLON.replace(text, ":")
                text = EN_PARAGRAPH.replace(text, "\n\n")
                text = EN_NEWLINE.replace(text, "\n")
            }
        }
        return text
    }
}
