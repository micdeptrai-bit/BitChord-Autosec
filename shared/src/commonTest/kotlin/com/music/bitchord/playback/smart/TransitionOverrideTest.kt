package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-pair overrides: nothing asked means nothing changed, and each knob lands
 * on an input the planner already understands.
 */
class TransitionOverrideTest {

    /** A synthetic, fully analysed track on a steady grid, as in [PhraseBarAutomixTest]. */
    private fun track(id: String, bpm: Double, duration: Double = 200.0): TrackAnalysis {
        val beat = 60.0 / bpm
        val downbeats = generateSequence(0.5) { it + 4 * beat }.takeWhile { it < duration - 1 }.toList()
        val energy = (0 until (duration * 2).toInt()).map { EnergySample(it / 2.0, 1.0) }
        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = id,
            duration = duration,
            bpm = bpm,
            beatInterval = beat,
            beatConfidence = 0.9,
            downbeats = downbeats,
            phraseBoundaries = downbeats.filterIndexed { index, _ -> index % 8 == 0 },
            firstBeat = 0.5,
            key = "C major",
            keyConfidence = 0.9,
            audibleStartTime = 0.5,
            contentEndTime = duration - 2,
            mixInTime = downbeats[4],
            energyCurve = energy,
            vocalProbability = 0.1,
        )
    }

    private fun info(analysis: TrackAnalysis) =
        TransitionTrackInfo(analysis.trackId, (analysis.duration * 1000).toLong())

    private fun planWith(
        pairOverride: TransitionOverride?,
        current: TrackAnalysis,
        next: TrackAnalysis,
        phraseBars: Int = 0,
    ) = planTransitionWithOverride(
        pairOverride = pairOverride,
        analysis = current,
        nextAnalysis = next,
        currentTrack = info(current),
        nextTrack = info(next),
        currentTime = 10.0,
        duration = current.duration,
        mode = CrossfadeMode.SMART,
        advanced = true,
        phraseBars = phraseBars,
    )

    @Test
    fun noOverrideIsExactlyThePlannersOwnPlan() {
        val current = track("a", 120.0)
        val next = track("b", 120.0)
        val expected = planTransition(
            analysis = current,
            nextAnalysis = next,
            currentTrack = info(current),
            nextTrack = info(next),
            currentTime = 10.0,
            duration = current.duration,
            mode = CrossfadeMode.SMART,
            advanced = true,
        )

        val none = planWith(null, current, next)
        assertEquals(expected, none.plan)
        assertFalse(none.overrideApplied)

        val untouched = planWith(TransitionOverride(), current, next)
        assertEquals(expected, untouched.plan, "an all-default override must change nothing")
        assertFalse(untouched.overrideApplied)
    }

    @Test
    fun turningThePairOffBlocksTheMix() {
        val current = track("a", 120.0)
        val next = track("b", 120.0)
        val result = planWith(TransitionOverride(enabled = false), current, next)

        assertTrue(result.plan.blocked)
        assertEquals(REASON_USER_OFF, result.plan.reason)
        assertFalse(result.plan.shouldStart)
        assertFalse(result.plan.markerVisible, "a blocked pair draws no marker")
        assertTrue(result.overrideApplied)
    }

    @Test
    fun crossfadeStyleGivesAPlainEqualPowerFade() {
        val current = track("a", 120.0)
        val next = track("b", 120.0)
        val result = planWith(
            TransitionOverride(style = OverrideStyle.CROSSFADE, crossfadeSeconds = 6.0),
            current,
            next,
        )
        val plan = result.plan

        assertEquals(TransitionStyle.EQUAL_POWER, plan.transitionStyle)
        assertEquals(6.0, plan.fadeSeconds, 1e-6)
        assertEquals(194.0, plan.transitionStart, 1e-6)
        assertEquals(200.0, plan.transitionEnd, 1e-6)
        assertEquals(1.0, plan.incomingPlaybackRate, "a plain fade never stretches")
        assertTrue(result.overrideApplied)
    }

    @Test
    fun quickCutAndLongFadeArePlainFadesOfTheirOwnLength() {
        val current = track("a", 120.0)
        val next = track("b", 120.0)

        val quick = planWith(TransitionOverride(style = OverrideStyle.QUICK_CUT), current, next).plan
        assertEquals(TransitionStyle.EQUAL_POWER, quick.transitionStyle)
        assertEquals(OVERRIDE_QUICK_CUT_SECONDS, quick.fadeSeconds, 1e-6)
        assertEquals(200.0, quick.transitionEnd, 1e-6)

        val long = planWith(TransitionOverride(style = OverrideStyle.LONG_FADE), current, next).plan
        assertEquals(TransitionStyle.EQUAL_POWER, long.transitionStyle)
        assertEquals(OVERRIDE_LONG_FADE_SECONDS, long.fadeSeconds, 1e-6)
        assertEquals(1.0, long.incomingPlaybackRate, "a plain fade never stretches")
    }

    @Test
    fun classicIsThePlannersOwnPlanWithAdvancedOff() {
        val current = track("a", 120.0)
        val next = track("b", 124.0)
        val result = planWith(TransitionOverride(style = OverrideStyle.CLASSIC), current, next)
        val expected = planTransition(
            analysis = current,
            nextAnalysis = next,
            currentTrack = info(current),
            nextTrack = info(next),
            currentTime = 10.0,
            duration = current.duration,
            mode = CrossfadeMode.SMART,
            advanced = false,
        )

        assertEquals(expected, result.plan)
        assertTrue(result.overrideApplied)
        assertTrue(result.notes.isEmpty(), "classic asks for no length, so none can be unavailable")
    }

    @Test
    fun crossfadeLengthIsClampedToWhatThePlannerAllows() {
        val current = track("a", 120.0)
        val next = track("b", 120.0)

        val tooLong = planWith(
            TransitionOverride(style = OverrideStyle.CROSSFADE, crossfadeSeconds = 40.0),
            current,
            next,
        )
        assertEquals(OVERRIDE_MAX_CROSSFADE_SECONDS, tooLong.plan.fadeSeconds, 1e-6)

        val tooShort = planWith(
            TransitionOverride(style = OverrideStyle.CROSSFADE, crossfadeSeconds = 0.0),
            current,
            next,
        )
        assertEquals(OVERRIDE_MIN_CROSSFADE_SECONDS, tooShort.plan.fadeSeconds, 1e-6)
    }

    @Test
    fun eightBarsIsExactlyEightBarsOnTheGrid() {
        val current = track("a", 120.0)
        val next = track("b", 120.0)
        val result = planWith(TransitionOverride(length = OverrideLength.BARS_8), current, next)
        val plan = result.plan

        assertTrue(result.overrideApplied, "notes: ${result.notes}")
        assertTrue(result.notes.isEmpty())
        assertEquals(TransitionStyle.DJ_BLEND, plan.transitionStyle)
        assertEquals(32, plan.transitionBeats, "8 bars of 4/4 is 32 beats")
        assertEquals(plan.fadeSeconds, plan.transitionEnd - plan.transitionStart, 1e-6)
    }

    @Test
    fun thePairsLengthBeatsAGlobalPhraseSetting() {
        val current = track("a", 120.0)
        val next = track("b", 120.0)
        val result = planWith(
            TransitionOverride(length = OverrideLength.BARS_8),
            current,
            next,
            phraseBars = PHRASE_BARS_LONG,
        )
        assertEquals(32, result.plan.transitionBeats, "the override asked for 8 bars, not the global 16")
    }

    @Test
    fun aLengthNeedsATrustedTempoAndSaysSoWhenItDoesNotGetOne() {
        val current = TrackAnalysis(trackId = "a", duration = 200.0)
        val next = TrackAnalysis(trackId = "b", duration = 200.0)
        val result = planWith(TransitionOverride(length = OverrideLength.BARS_16), current, next)

        assertFalse(result.overrideApplied)
        assertEquals(listOf(NOTE_LENGTH_UNAVAILABLE), result.notes)
    }
}
