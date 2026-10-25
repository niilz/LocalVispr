package dev.localvispr.text

import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun buildSystemPromptIncludesStyleInstructionsWhenNotCasual() {
        val casualPrompt =
            TranscriptPostProcessor.buildSystemPrompt(
                language = Language.GERMAN,
                style = DictationStyle.CASUAL,
            )
        assertFalse(casualPrompt.contains(DictationStyle.FORMAL.promptInstruction(Language.GERMAN)))

        for (style in
            listOf(
                DictationStyle.FORMAL,
                DictationStyle.CODE_PROMPT,
                DictationStyle.BULLET_POINTS,
            )) {
            val dePrompt =
                TranscriptPostProcessor.buildSystemPrompt(language = Language.GERMAN, style = style)
            assertTrue(dePrompt.contains(style.promptInstruction(Language.GERMAN)))

            val enPrompt =
                TranscriptPostProcessor.buildSystemPrompt(
                    language = Language.ENGLISH,
                    style = style,
                )
            assertTrue(enPrompt.contains(style.promptInstruction(Language.ENGLISH)))
        }
    }

    @Test
    fun buildSystemPromptIncludesTargetAppPackageWhenPresent() {
        val promptWithApp =
            TranscriptPostProcessor.buildSystemPrompt(
                language = Language.GERMAN,
                targetAppPackage = "com.slack",
            )
        assertTrue(promptWithApp.contains("Target app: com.slack."))

        val promptWithoutApp =
            TranscriptPostProcessor.buildSystemPrompt(
                language = Language.GERMAN,
                targetAppPackage = null,
            )
        assertFalse(promptWithoutApp.contains("Target app:"))

        val promptWithBlankApp =
            TranscriptPostProcessor.buildSystemPrompt(
                language = Language.GERMAN,
                targetAppPackage = "   ",
            )
        assertFalse(promptWithBlankApp.contains("Target app:"))
    }

    @Test
    fun processPassesStyleAndTargetAppToLlm() {
        var capturedSystem = ""
        val fakeLlm = LlmClient { system, _ ->
            capturedSystem = system
            "Formaler Text."
        }
        val processor = TranscriptPostProcessor(fakeLlm)

        val out =
            processor.process(
                rawTranscript = "Ich brauche das Dokument.",
                language = Language.GERMAN,
                style = DictationStyle.FORMAL,
                targetAppPackage = "com.microsoft.office.outlook",
            )

        assertEquals("Formaler Text.", out)
        assertTrue(
            capturedSystem.contains(DictationStyle.FORMAL.promptInstruction(Language.GERMAN))
        )
        assertTrue(capturedSystem.contains("Target app: com.microsoft.office.outlook."))
    }

    @Test
    fun formatHotwordsTrimsAndIgnoresEmptyEntries() {
        assertEquals("", TranscriptPostProcessor.formatHotwords(emptyList()))
        assertEquals("", TranscriptPostProcessor.formatHotwords(listOf("", "   ", "\t")))
        assertEquals(
            "LocalVispr\nSherpa-ONNX\nWhisper",
            TranscriptPostProcessor.formatHotwords(
                listOf("  LocalVispr  ", "", "Sherpa-ONNX", "   ", "Whisper")
            ),
        )
    }

    @Test
    fun dictationStyleLabelsAndInstructions() {
        assertEquals("Locker / Chat", DictationStyle.CASUAL.label)
        assertEquals("Formell / E-Mail", DictationStyle.FORMAL.label)
        assertEquals("Code & Prompt", DictationStyle.CODE_PROMPT.label)
        assertEquals("Stichpunkte", DictationStyle.BULLET_POINTS.label)

        for (style in DictationStyle.entries) {
            assertTrue(style.promptInstruction(Language.GERMAN).isNotBlank())
            assertTrue(style.promptInstruction(Language.ENGLISH).isNotBlank())
        }
    }

    @Test
    fun editorContextHoldsCursorAndPackageInfo() {
        val ctx =
            EditorContext(
                packageName = "org.telegram.messenger",
                textBeforeCursor = "Hallo ",
                textAfterCursor = "!",
            )
        assertEquals("org.telegram.messenger", ctx.packageName)
        assertEquals("Hallo ", ctx.textBeforeCursor)
        assertEquals("!", ctx.textAfterCursor)
    }
}
