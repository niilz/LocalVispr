package dev.localvispr.llm

import dev.localvispr.text.Language
import dev.localvispr.text.TranscriptPostProcessor
import dev.localvispr.text.WritingStyle
import kotlin.test.assertTrue
import org.junit.Test

class LlmTokenBudgetTest {

    // LiteRT-LM's `maxNumTokens` is a shared budget for the prompt *and* the
    // generated answer. Subword tokenizers average roughly 3 characters per
    // token for German, so dividing by 3 is a deliberately pessimistic estimate.
    private fun estimateTokens(text: String): Int = (text.length + 2) / 3

    @Test
    fun worstCaseGermanEmailDictationFitsIntoTheTokenBudget() {
        val systemPrompt =
            TranscriptPostProcessor.buildSystemPrompt(
                language = Language.GERMAN,
                dictionaryWords = listOf("LocalVispr", "Sherpa-ONNX", "Steffi -> Stephie"),
                style = WritingStyle.FORMAL,
                targetAppPackage = "com.google.android.gm",
            )
        // About 90 seconds of speech (~200 words) is a long but realistic email.
        val dictation = "Hallo Herr Müller, vielen Dank für Ihre Nachricht. ".repeat(26)
        val userMessage =
            TranscriptPostProcessor.buildUserMessage(
                preCleanedTranscript = dictation,
                surroundingText = "x".repeat(500),
            )
        // Chat-template tokens (turn markers, role names) are not in our strings.
        val templateOverhead = 50
        val promptTokens = estimateTokens(systemPrompt + userMessage) + templateOverhead
        // The formatted answer repeats the dictation plus added line breaks.
        val answerTokens = estimateTokens(dictation) + 50

        val needed = promptTokens + answerTokens
        assertTrue(
            needed <= LlmTokenBudget.MAX_NUM_TOKENS,
            "Needs ~$needed tokens but budget is ${LlmTokenBudget.MAX_NUM_TOKENS}",
        )
    }

    @Test
    fun fallbackBudgetIsSmallerThanThePreferredBudget() {
        assertTrue(LlmTokenBudget.FALLBACK_MAX_NUM_TOKENS < LlmTokenBudget.MAX_NUM_TOKENS)
    }
}
