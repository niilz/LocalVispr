package dev.localvispr.text

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class TranscriptPostProcessorTest {

    @Test
    fun fallsBackToRuleFilterWhenLlmIsNull() {
        val processor = TranscriptPostProcessor(llm = null)
        val out = processor.process("Äh, ich bin gleich da.", Language.GERMAN)
        assertEquals("Ich bin gleich da.", out)
    }

    @Test
    fun passesPreCleanedTextAndContextIntoThePrompt() {
        var capturedSystem = ""
        var capturedUser = ""
        val fakeLlm = LlmClient { system, user ->
            capturedSystem = system
            capturedUser = user
            "Ich nutze LocalVispr."
        }
        val processor = TranscriptPostProcessor(fakeLlm)

        val result =
            processor.process(
                rawTranscript = "Ähm, ich nutze lokal visper.",
                language = Language.GERMAN,
                surroundingText = "Hallo! ",
                dictionaryWords = listOf("LocalVispr", "Sherpa-ONNX"),
            )

        assertEquals("Ich nutze LocalVispr.", result)
        assertTrue(capturedSystem.contains("LocalVispr"))
        assertTrue(capturedUser.contains("Ich nutze lokal visper."))
        assertTrue(capturedUser.contains("Hallo!"))
    }

    @Test
    fun stripsThinkBlocksAndSurroundingQuotesFromModelOutput() {
        val fakeLlm = LlmClient { _, _ ->
            "<think>\nLet me clean this.\n</think>\n\" Das ist der bereinigte Satz. \""
        }
        val out = TranscriptPostProcessor(fakeLlm).process("Das ist äh der Satz.", Language.GERMAN)
        assertEquals("Das ist der bereinigte Satz.", out)
    }

    @Test
    fun rejectsOutputThatLooksLikeAnAssistantReplyOrRunawayGeneration() {
        // Small models sometimes answer a dictated question instead of cleaning it.
        val chattyLlm = LlmClient { _, _ ->
            "Natürlich! Gerne helfe ich dir dabei, diesen Text zu bereinigen. Hier ist das Ergebnis: ..."
        }
        val out = TranscriptPostProcessor(chattyLlm).process("Wie spät ist es?", Language.GERMAN)
        assertEquals("Wie spät ist es?", out)
    }

    @Test
    fun rejectsEmptyModelOutputAndReturnsRuleCleanedText() {
        val silentLlm = LlmClient { _, _ -> "<think>nothing</think>   " }
        val out = TranscriptPostProcessor(silentLlm).process("Um, hello world.", Language.ENGLISH)
        assertEquals("Hello world.", out)
    }

    @Test
    fun prependsLeadingSpaceWhenInsertedRightAfterExistingWord() {
        assertEquals(
            " Welt",
            TranscriptPostProcessor.formatForInsertion("Welt", textBeforeCursor = "Hallo"),
        )
        assertEquals(
            "Welt",
            TranscriptPostProcessor.formatForInsertion("Welt", textBeforeCursor = "Hallo "),
        )
        assertEquals(
            "Welt",
            TranscriptPostProcessor.formatForInsertion("Welt", textBeforeCursor = ""),
        )
        assertEquals(
            ", Welt",
            TranscriptPostProcessor.formatForInsertion(", Welt", textBeforeCursor = "Hallo"),
        )
    }
}
