package dev.localvispr.text

import kotlin.test.assertEquals
import org.junit.Test

class LiveStreamingDiffTest {

    @Test
    fun computesDeltaForInitialInsert() {
        val delta = LiveStreamingDiff.computeDelta("", "Hallo")
        assertEquals(StreamingTextDelta(deleteBeforeCursor = 0, textToCommit = "Hallo"), delta)
    }

    @Test
    fun computesDeltaForAppendingText() {
        val delta = LiveStreamingDiff.computeDelta("Hallo", "Hallo Welt")
        assertEquals(StreamingTextDelta(deleteBeforeCursor = 0, textToCommit = " Welt"), delta)
    }

    @Test
    fun computesDeltaForRetroactiveContextCorrection() {
        val delta = LiveStreamingDiff.computeDelta("Ich gehe nach haus", "Ich gehe nach Hause.")
        assertEquals(StreamingTextDelta(deleteBeforeCursor = 4, textToCommit = "Hause."), delta)
    }

    @Test
    fun returnsNoOpDeltaForIdenticalText() {
        val delta = LiveStreamingDiff.computeDelta("Hallo", "Hallo")
        assertEquals(StreamingTextDelta(deleteBeforeCursor = 0, textToCommit = ""), delta)

        val emptyDelta = LiveStreamingDiff.computeDelta("", "")
        assertEquals(StreamingTextDelta(deleteBeforeCursor = 0, textToCommit = ""), emptyDelta)
    }
}
