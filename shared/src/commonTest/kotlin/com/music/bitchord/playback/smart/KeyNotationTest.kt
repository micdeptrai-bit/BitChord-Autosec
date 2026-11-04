package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyNotationTest {

    @Test
    fun majorKeysMapToTheBRing() {
        assertEquals("8B", camelotOf("C major"))
        assertEquals("9B", camelotOf("G major"))
        assertEquals("11B", camelotOf("A major"))
        assertEquals("5B", camelotOf("E♭ major"))
        assertEquals("1B", camelotOf("B major"))
        assertEquals("2B", camelotOf("F♯ major"))
    }

    @Test
    fun minorKeysShareTheirRelativeMajorsNumber() {
        assertEquals("8A", camelotOf("A minor"))
        assertEquals("5A", camelotOf("C minor"))
        assertEquals("10A", camelotOf("B minor"))
        assertEquals("7A", camelotOf("D minor"))
        assertEquals("11A", camelotOf("F♯ minor"))
    }

    @Test
    fun asciiAccidentalsAreAccepted() {
        assertEquals(camelotOf("F♯ minor"), camelotOf("F# minor"))
        assertEquals(camelotOf("E♭ major"), camelotOf("Eb major"))
        assertEquals(camelotOf("B♭ minor"), camelotOf("Bb minor"))
    }

    @Test
    fun anythingElseHasNoCode() {
        assertNull(camelotOf(""))
        assertNull(camelotOf("C"))
        assertNull(camelotOf("H major"))
        assertNull(camelotOf("C dorian"))
    }

    @Test
    fun anUnmeasuredTrackShowsNothing() {
        val unmeasured = TrackAnalysis()
        assertEquals(0, unmeasured.displayBpm())
        assertEquals("", unmeasured.displayKey())
    }

    @Test
    fun aMeasuredTrackShowsRoundedTempoAndKeyWithCode() {
        val measured = TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            bpm = 127.6,
            key = "A minor",
            keyConfidence = 0.8,
        )
        assertEquals(128, measured.displayBpm())
        assertEquals("A minor · 8A", measured.displayKey())
    }

    @Test
    fun aKeyThePlannerWouldNotTrustIsNotShown() {
        val unsure = TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            bpm = 100.0,
            key = "A minor",
            keyConfidence = 0.1,
        )
        assertEquals(100, unsure.displayBpm())
        assertEquals("", unsure.displayKey())
    }
}
