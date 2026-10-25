package dev.localvispr.service

/**
 * Pure-JVM decision policy for floating bubble visibility.
 *
 * Dictation and speech processing take absolute priority: when active, the bubble must remain
 * visible so the user can observe transcription progress and stop recording. When idle, an active
 * snooze timer suppresses the bubble until [snoozedUntilEpochMs] has passed. Otherwise,
 * [autoHideOnNoFocus] determines whether editable input focus is required.
 */
object BubbleVisibilityPolicy {
    fun shouldShowBubble(
        autoHideOnNoFocus: Boolean,
        hasEditableFocus: Boolean,
        isDictatingOrProcessing: Boolean,
        snoozedUntilEpochMs: Long,
        nowEpochMs: Long,
    ): Boolean {
        if (isDictatingOrProcessing) {
            return true
        }
        if (nowEpochMs < snoozedUntilEpochMs) {
            return false
        }
        return if (autoHideOnNoFocus) {
            hasEditableFocus
        } else {
            true
        }
    }
}
