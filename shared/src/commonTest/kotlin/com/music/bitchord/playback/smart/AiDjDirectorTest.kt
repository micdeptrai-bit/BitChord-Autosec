package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiDjDirectorTest {
    private fun measured(bpm: Double, key: String) = TrackAnalysis(
        status = TrackAnalysis.STATUS_READY,
        bpm = bpm,
        key = key,
        keyConfidence = 0.8,
    )

    private fun direct(
        from: TrackAnalysis,
        to: TrackAnalysis,
        stored: TransitionOverride? = null,
        listener: MixTuning = MixTuning.DEFAULT,
        gesture: FilterTransitionVariant? = null,
    ) = AiDjDirector.direct(from, to, stored, listener, gesture)

    @Test
    fun anUnmeasuredSongGetsACutNeverAPlainFade() {
        val decision = direct(TrackAnalysis(), measured(120.0, "A minor"))
        assertEquals(OverrideStyle.QUICK_CUT, decision.pairOverride?.style)
        assertTrue(AiDjDirector.NOTE_CUT_NO_ANALYSIS in decision.notes)
    }

    @Test
    fun sameTempoAndCompatibleKeysGetTheLongBlend() {
        // A minor is 8A and E minor is 9A: one step apart on the wheel.
        val decision = direct(measured(124.0, "A minor"), measured(126.0, "E minor"))
        assertEquals(OverrideLength.BARS_16, decision.pairOverride?.length)
        assertEquals(MixProfile.CLUB, decision.tuning.profile)
    }

    @Test
    fun sameTempoButClashingKeysGetAShortTightBlend() {
        // A minor is 8A and F sharp minor is 11A: three steps apart.
        val decision = direct(measured(124.0, "A minor"), measured(125.0, "F♯ minor"))
        assertEquals(OverrideLength.BARS_8, decision.pairOverride?.length)
        assertEquals(MixProfile.RADIO, decision.tuning.profile)
    }

    @Test
    fun tempiTooFarApartGetACrispHandoffAndNoForcedLength() {
        val decision = direct(measured(90.0, "A minor"), measured(130.0, "A minor"))
        assertNull(decision.pairOverride)
        assertEquals(MixProfile.RADIO, decision.tuning.profile)
        assertTrue(AiDjDirector.NOTE_CRISP_HANDOFF in decision.notes)
    }

    @Test
    fun aPlainFadeStoredForThePairIsReplacedButItsAbsenceOfMixIsNot() {
        val from = measured(124.0, "A minor")
        val to = measured(124.0, "A minor")
        val replaced = direct(from, to, stored = TransitionOverride(style = OverrideStyle.CROSSFADE))
        assertEquals(OverrideLength.BARS_16, replaced.pairOverride?.length)
        assertEquals(OverrideStyle.AUTO, replaced.pairOverride?.style)
        assertTrue(AiDjDirector.NOTE_REPLACED_FADE in replaced.notes)

        val off = TransitionOverride(enabled = false)
        assertEquals(off, direct(from, to, stored = off).pairOverride)
        val cut = TransitionOverride(style = OverrideStyle.QUICK_CUT)
        assertEquals(cut, direct(from, to, stored = cut).pairOverride)
    }

    @Test
    fun theListenersOwnTasteAndGestureAreKept() {
        val taste = MixTuning(MixProfile.CHILL, 20)
        val decision = direct(
            measured(124.0, "A minor"),
            measured(124.0, "A minor"),
            listener = taste,
            gesture = FilterTransitionVariant.SWEEP,
        )
        assertEquals(taste, decision.tuning)
        assertEquals(FilterTransitionVariant.SWEEP, decision.gesture)
    }

    @Test
    fun theSamePairAlwaysGetsTheSameAnswer() {
        val from = measured(122.0, "C major")
        val to = measured(128.0, "G major")
        assertEquals(direct(from, to), direct(from, to))
    }

    @Test
    fun halfTimeIsNearButNeverMatched() {
        val gap = AiDjDirector.tempoGap(70.0, 140.0)
        assertTrue(gap != null && gap > 0.06 && gap <= 0.12)
    }
}
