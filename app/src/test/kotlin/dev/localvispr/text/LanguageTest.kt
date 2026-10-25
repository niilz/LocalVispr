package dev.localvispr.text

import kotlin.test.assertEquals
import org.junit.Test

class LanguageTest {

    @Test
    fun mapsWhisperLanguageCodes() {
        assertEquals(Language.GERMAN, Language.fromCode("de"))
        assertEquals(Language.ENGLISH, Language.fromCode("<|en|>"))
        assertEquals(null, Language.fromCode("fr"))
    }

    @Test
    fun guessesGermanFromFunctionWords() {
        assertEquals(Language.GERMAN, Language.guess("Ich glaube, dass wir das morgen machen."))
    }

    @Test
    fun guessesEnglishFromFunctionWords() {
        assertEquals(Language.ENGLISH, Language.guess("I think that we should do it tomorrow."))
    }

    @Test
    fun fallsBackToTheGivenDefaultWhenUndecidable() {
        assertEquals(Language.ENGLISH, Language.guess("OK", default = Language.ENGLISH))
    }
}
