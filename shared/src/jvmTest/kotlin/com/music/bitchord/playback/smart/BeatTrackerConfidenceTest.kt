package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The beat grid's confidence is the number the whole transition policy gates on, so what it lets
 * through is pinned here: a steady grid passes, one with half its gaps broken does not, and a
 * grid that is not in four does not reach the planner as a grid.
 */
class BeatTrackerConfidenceTest {

    private fun steady(count: Int, interval: Double = 0.5) = List(count) { it * interval }

    private fun logits(count: Int, value: Double) = List(count) { value }

    @Test
    fun aSteadyDecisiveGridClearsTheBeatMatchGate() {
        val beats = steady(64)
        val confidence = BeatTracker.gridConfidence(beats, logits(beats.size, 2.0))
        assertTrue(confidence >= MIN_BEATMATCH_CONFIDENCE, "was $confidence")
        assertTrue(confidence <= 0.95, "was $confidence")
    }

    @Test
    fun aGridWithHalfItsGapsBrokenDoesNotClearTheGate() {
        var time = 0.0
        val beats = listOf(0.0) + List(63) { index ->
            time += if (index % 2 == 0) 0.5 else 0.9
            time
        }
        val confidence = BeatTracker.gridConfidence(beats, logits(beats.size, 2.0))
        assertTrue(confidence < MIN_BEATMATCH_CONFIDENCE, "was $confidence")
    }

    @Test
    fun tooFewBeatsIsNoConfidenceAtAll() {
        assertEquals(0.0, BeatTracker.gridConfidence(steady(7), logits(7, 2.0)))
    }

    @Test
    fun barsAreCountedFromTheDetectedDownbeats() {
        val beats = steady(40)
        assertEquals(4, BeatTracker.beatsPerBar(beats, beats.filterIndexed { index, _ -> index % 4 == 0 }))
        assertEquals(3, BeatTracker.beatsPerBar(beats, beats.filterIndexed { index, _ -> index % 3 == 0 }))
        assertEquals(0, BeatTracker.beatsPerBar(beats, listOf(0.0, 2.0, 4.0)))
    }

    @Test
    fun aGridNotInFourIsCappedBelowTheDjGate() {
        assertEquals(0.9, BeatTracker.capForMeter(0.9, 4))
        assertEquals(0.9, BeatTracker.capForMeter(0.9, 0))
        val capped = BeatTracker.capForMeter(0.9, 3)
        assertTrue(capped < MIN_DJ_CONFIDENCE, "was $capped")
        assertEquals(0.1, BeatTracker.capForMeter(0.1, 6))
    }
}
