package dev.localvispr.text

import kotlin.test.assertEquals
import org.junit.Test

class FillerWordFilterTest {

    @Test
    fun removesGermanHesitationsTogetherWithTheirCommas() {
        assertEquals(
            "Ich wollte sagen, dass es morgen regnet.",
            FillerWordFilter.clean(
                "Ich wollte, äh, sagen, dass es ähm morgen regnet.",
                Language.GERMAN,
            ),
        )
    }

    @Test
    fun removesEnglishHesitations() {
        assertEquals(
            "So I think we should ship it.",
            FillerWordFilter.clean("So, um, I think uh we should, uhm, ship it.", Language.ENGLISH),
        )
    }

    @Test
    fun capitalisesTheSentenceAfterALeadingFiller() {
        assertEquals(
            "Wir treffen uns um acht.",
            FillerWordFilter.clean("Äh, wir treffen uns um acht.", Language.GERMAN),
        )
    }

    @Test
    fun collapsesStutteredWordRepetitions() {
        assertEquals(
            "Ich gehe nach Hause.",
            FillerWordFilter.clean("Ich ich ich gehe nach Hause.", Language.GERMAN),
        )
        assertEquals("I want to go.", FillerWordFilter.clean("I want to to go.", Language.ENGLISH))
    }

    @Test
    fun keepsGrammaticalDoubleWords() {
        // German relative clauses ("die die") and English past perfect ("had had") are valid.
        assertEquals(
            "Die Frau, die die Katze füttert.",
            FillerWordFilter.clean("Die Frau, die die Katze füttert.", Language.GERMAN),
        )
        assertEquals(
            "She had had enough.",
            FillerWordFilter.clean("She had had enough.", Language.ENGLISH),
        )
    }

    @Test
    fun keepsTheGermanPrepositionUmWhichIsAnEnglishFiller() {
        assertEquals(
            "Um acht, um zu essen.",
            FillerWordFilter.clean("Um acht, um zu essen.", Language.GERMAN),
        )
    }

    @Test
    fun keepsTheCommaBeforeASubordinateClause() {
        assertEquals(
            "Ich glaube, dass es klappt.",
            FillerWordFilter.clean("Ich glaube, äh, dass es klappt.", Language.GERMAN),
        )
    }

    @Test
    fun doesNotTouchWordsThatMerelyContainFillerSyllables() {
        assertEquals(
            "Ähnlich wie Umweltschutz.",
            FillerWordFilter.clean("Ähnlich wie Umweltschutz.", Language.GERMAN),
        )
    }

    @Test
    fun stripsWhisperNonSpeechAnnotations() {
        assertEquals(
            "Hallo zusammen.",
            FillerWordFilter.clean("[Musik] Hallo zusammen. (Applaus)", Language.GERMAN),
        )
        assertEquals(
            "Hello there.",
            FillerWordFilter.clean("*music* Hello there. [BLANK_AUDIO]", Language.ENGLISH),
        )
    }

    @Test
    fun normalisesWhitespaceAndDanglingPunctuation() {
        assertEquals(
            "Eins, zwei.",
            FillerWordFilter.clean("  eins ,  zwei ,  ähm .  ", Language.GERMAN),
        )
    }

    @Test
    fun returnsEmptyStringWhenOnlyFillersWereSpoken() {
        assertEquals("", FillerWordFilter.clean("Ähm... äh.", Language.GERMAN))
    }
}
