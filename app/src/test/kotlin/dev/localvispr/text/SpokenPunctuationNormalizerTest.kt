package dev.localvispr.text

import kotlin.test.assertEquals
import org.junit.Test

class SpokenPunctuationNormalizerTest {

    @Test
    fun convertsSpokenHyphenBetweenWordsToDashWithoutSpaces() {
        assertEquals(
            "Hans-Peter",
            SpokenPunctuationNormalizer.normalize("Hans Bindestrich Peter", Language.GERMAN),
        )
        assertEquals(
            "Hans-Peter",
            SpokenPunctuationNormalizer.normalize("Hans bindestrich Peter", Language.GERMAN),
        )
        assertEquals(
            "Hans-Peter",
            SpokenPunctuationNormalizer.normalize("Hans Gedankenstrich Peter", Language.GERMAN),
        )
    }

    @Test
    fun convertsEnglishSpokenHyphenAndDash() {
        assertEquals(
            "state-of-the-art",
            SpokenPunctuationNormalizer.normalize(
                "state hyphen of hyphen the hyphen art",
                Language.ENGLISH,
            ),
        )
        assertEquals(
            "word-word",
            SpokenPunctuationNormalizer.normalize("word dash word", Language.ENGLISH),
        )
    }

    @Test
    fun convertsSpokenCommaAndPeriod() {
        assertEquals(
            "Hallo, wie geht es dir?",
            SpokenPunctuationNormalizer.normalize(
                "Hallo Komma wie geht es dir Fragezeichen",
                Language.GERMAN,
            ),
        )
        assertEquals(
            "Das ist gut. Fertig.",
            SpokenPunctuationNormalizer.normalize("Das ist gut Punkt Fertig Punkt", Language.GERMAN),
        )
    }

    @Test
    fun convertsSpokenColonAndExclamationMark() {
        assertEquals(
            "Achtung: wichtig!",
            SpokenPunctuationNormalizer.normalize(
                "Achtung Doppelpunkt wichtig Ausrufezeichen",
                Language.GERMAN,
            ),
        )
        assertEquals(
            "Warning: danger!",
            SpokenPunctuationNormalizer.normalize(
                "Warning colon danger exclamation mark",
                Language.ENGLISH,
            ),
        )
    }

    @Test
    fun convertsSpokenNewlineAndParagraph() {
        assertEquals(
            "Erste Zeile\nZweite Zeile",
            SpokenPunctuationNormalizer.normalize(
                "Erste Zeile neue Zeile Zweite Zeile",
                Language.GERMAN,
            ),
        )
        assertEquals(
            "Hallo\n\nWelt",
            SpokenPunctuationNormalizer.normalize("Hallo neuer Absatz Welt", Language.GERMAN),
        )
        assertEquals(
            "First line\nSecond line",
            SpokenPunctuationNormalizer.normalize(
                "First line new line Second line",
                Language.ENGLISH,
            ),
        )
        assertEquals(
            "First para\n\nSecond para",
            SpokenPunctuationNormalizer.normalize(
                "First para new paragraph Second para",
                Language.ENGLISH,
            ),
        )
    }
}
