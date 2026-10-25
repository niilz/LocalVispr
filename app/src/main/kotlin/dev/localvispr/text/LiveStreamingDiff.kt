package dev.localvispr.text

// ==============================================================================
// Diff calculation for streaming speech-to-text live insertion
// ==============================================================================
//
// Computes minimal edits (deletions before the cursor + text to append) between
// the previously committed partial transcript and the newly incoming candidate.

data class StreamingTextDelta(val deleteBeforeCursor: Int, val textToCommit: String)

object LiveStreamingDiff {

    fun computeDelta(previouslyCommitted: String, nextCandidate: String): StreamingTextDelta {
        if (previouslyCommitted == nextCandidate) {
            return StreamingTextDelta(0, "")
        }

        val minLen = minOf(previouslyCommitted.length, nextCandidate.length)
        var commonPrefixLength = 0
        while (
            commonPrefixLength < minLen &&
                previouslyCommitted[commonPrefixLength] == nextCandidate[commonPrefixLength]
        ) {
            commonPrefixLength++
        }

        val deleteBeforeCursor = previouslyCommitted.length - commonPrefixLength
        val textToCommit = nextCandidate.substring(commonPrefixLength)
        return StreamingTextDelta(deleteBeforeCursor, textToCommit)
    }
}
