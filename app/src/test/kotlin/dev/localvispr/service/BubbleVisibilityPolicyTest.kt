package dev.localvispr.service

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class BubbleVisibilityPolicyTest {

    @Test
    fun dictatingOrProcessingAlwaysShowsBubbleEvenWhenSnoozedOrNoFocus() {
        // Dictation overrides snooze and focus settings
        assertTrue(
            BubbleVisibilityPolicy.shouldShowBubble(
                autoHideOnNoFocus = true,
                hasEditableFocus = false,
                isDictatingOrProcessing = true,
                snoozedUntilEpochMs = 1_000_000L,
                nowEpochMs = 500_000L,
            )
        )
    }

    @Test
    fun dictatingOrProcessingShowsBubbleWithFocusAndNotSnoozed() {
        assertTrue(
            BubbleVisibilityPolicy.shouldShowBubble(
                autoHideOnNoFocus = false,
                hasEditableFocus = true,
                isDictatingOrProcessing = true,
                snoozedUntilEpochMs = 0L,
                nowEpochMs = 100_000L,
            )
        )
    }

    @Test
    fun activeSnoozeHidesBubbleWhenNotDictating() {
        // nowEpochMs < snoozedUntilEpochMs -> false even if focus is present
        assertFalse(
            BubbleVisibilityPolicy.shouldShowBubble(
                autoHideOnNoFocus = false,
                hasEditableFocus = true,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 2_000L,
                nowEpochMs = 1_000L,
            )
        )
    }

    @Test
    fun expiredSnoozeAllowsBubbleToShow() {
        // nowEpochMs >= snoozedUntilEpochMs -> not snoozed anymore
        assertTrue(
            BubbleVisibilityPolicy.shouldShowBubble(
                autoHideOnNoFocus = false,
                hasEditableFocus = false,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 1_000L,
                nowEpochMs = 1_000L,
            )
        )
        assertTrue(
            BubbleVisibilityPolicy.shouldShowBubble(
                autoHideOnNoFocus = false,
                hasEditableFocus = false,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 1_000L,
                nowEpochMs = 2_000L,
            )
        )
    }

    @Test
    fun autoHideOnNoFocusRequiresEditableFocus() {
        // autoHideOnNoFocus is true and not snoozed: depends on hasEditableFocus
        assertFalse(
            BubbleVisibilityPolicy.shouldShowBubble(
                autoHideOnNoFocus = true,
                hasEditableFocus = false,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 0L,
                nowEpochMs = 1_000L,
            )
        )
        assertTrue(
            BubbleVisibilityPolicy.shouldShowBubble(
                autoHideOnNoFocus = true,
                hasEditableFocus = true,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 0L,
                nowEpochMs = 1_000L,
            )
        )
    }

    @Test
    fun autoHideOffShowsBubbleWithoutFocus() {
        // autoHideOnNoFocus is false and not snoozed: always true
        assertTrue(
            BubbleVisibilityPolicy.shouldShowBubble(
                autoHideOnNoFocus = false,
                hasEditableFocus = false,
                isDictatingOrProcessing = false,
                snoozedUntilEpochMs = 0L,
                nowEpochMs = 1_000L,
            )
        )
    }
}
