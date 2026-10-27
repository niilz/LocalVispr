package dev.localvispr.service

/**
 * Pure-JVM decision policy for floating bubble visibility.
 *
 * Dictation and speech processing take priority: while active, the bubble stays visible so the user
 * can observe transcription progress and stop recording. When idle, an active snooze timer hides
 * the bubble until [snoozedUntilEpochMs] has passed; otherwise the bubble is shown only when an
 * editable input field / soft keyboard is active ([hasEditableFocus]).
 */
object BubbleVisibilityPolicy {
    fun shouldShowBubble(
        hasEditableFocus: Boolean,
        isDictatingOrProcessing: Boolean,
        snoozedUntilEpochMs: Long,
        nowEpochMs: Long,
    ): Boolean {
        if (isDictatingOrProcessing) return true
        if (nowEpochMs < snoozedUntilEpochMs) return false
        return hasEditableFocus
    }
}
