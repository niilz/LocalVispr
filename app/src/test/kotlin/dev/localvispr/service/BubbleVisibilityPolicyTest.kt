package dev.localvispr.service

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class BubbleVisibilityPolicyTest {

    @Test
    fun dictatingOrProcessingAlwaysShowsBubbleEvenWhenSnoozedOrNoFocus() {
        assertTrue(
            BubbleVisibilityPolicy.shouldShowBubble(
                hasEditableFocus = false,
                isDictatingOrProcessing = true,
                snoozedUntilEpochMs = 1_000_000L,
                nowEpochMs = 500_000L,
            )
        )
    }

    @Test
    fun activeSnoozeHidesBubbleWhenNotDictating() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShowBubble(
                hasEditableFocus = true,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 2_000L,
                nowEpochMs = 1_000L,
            )
        )
    }

    @Test
    fun expiredSnoozeShowsBubbleWhenFieldIsFocused() {
        assertTrue(
            BubbleVisibilityPolicy.shouldShowBubble(
                hasEditableFocus = true,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 1_000L,
                nowEpochMs = 1_000L,
            )
        )
    }

    @Test
    fun hidesBubbleWheneverNoEditableFieldIsFocused() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShowBubble(
                hasEditableFocus = false,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 0L,
                nowEpochMs = 1_000L,
            )
        )
    }
}
