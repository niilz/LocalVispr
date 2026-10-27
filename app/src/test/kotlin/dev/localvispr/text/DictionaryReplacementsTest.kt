package dev.localvispr.text

import kotlin.test.assertEquals
import org.junit.Test

class DictionaryReplacementsTest {

    @Test
    fun parsesSimpleWordsAndArrowReplacements() {
        val raw = listOf("LocalVispr", "Steffi -> Stephie", "k8s = Kubernetes", "  ")
        val parsed = DictionaryReplacements.parse(raw)

        assertEquals(listOf("LocalVispr"), parsed.vocabulary)
        assertEquals(mapOf("steffi" to "Stephie", "k8s" to "Kubernetes"), parsed.replacements)
    }

    @Test
    fun appliesReplacementsCaseInsensitivelyWhilePreservingTargetCasing() {
        val rules = mapOf("steffi" to "Stephie", "mfg" to "Mit freundlichen Grüßen")

        assertEquals(
            "Hallo Stephie, wie geht es dir?",
            DictionaryReplacements.apply("Hallo Steffi, wie geht es dir?", rules),
        )
        assertEquals(
            "Ich habe mit Stephie gesprochen.",
            DictionaryReplacements.apply("Ich habe mit steffi gesprochen.", rules),
        )
        assertEquals("Mit freundlichen Grüßen", DictionaryReplacements.apply("mfg", rules))
    }

    @Test
    fun doesNotReplaceSubstringsInsideOtherWords() {
        val rules = mapOf("art" to "Kunst")
        assertEquals(
            "Das ist ein Startartikel ohne Fehler.",
            DictionaryReplacements.apply("Das ist ein Startartikel ohne Fehler.", rules),
        )
    }
}
