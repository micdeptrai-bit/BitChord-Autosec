package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MixTuningTest {
    private val kicksNoClash = VariantSignals(kicksOnBothSides = true, keyClash = false, vocalConflict = false)

    @Test
    fun theDefaultIsNeutralInEveryNumber() {
        val neutral = MixTuning.DEFAULT
        assertTrue(neutral.isDefault)
        assertEquals(1.0, neutral.lengthScale)
        assertEquals(1.0, neutral.sweepScale)
        assertNull(neutral.pickVariant(kicksNoClash))
    }

    @Test
    fun gentlerIsLongerAndPunchierIsShorter() {
        val gentle = MixTuning(MixProfile.BALANCED, 0)
        val punchy = MixTuning(MixProfile.BALANCED, 100)
        assertTrue(gentle.lengthScale > 1.0)
        assertTrue(punchy.lengthScale < 1.0)
        assertTrue(gentle.sweepScale < 1.0)
        assertEquals(1.0, punchy.sweepScale)
    }

    @Test
    fun theProfilesOrderTheLengthFromRadioToChill() {
        val radio = MixTuning(MixProfile.RADIO).lengthScale
        val balanced = MixTuning(MixProfile.BALANCED).lengthScale
        val club = MixTuning(MixProfile.CLUB).lengthScale
        val chill = MixTuning(MixProfile.CHILL).lengthScale
        assertTrue(radio < balanced && balanced < club && club < chill)
    }

    @Test
    fun anOutOfRangeIntensityIsClamped() {
        assertEquals(MixTuning(MixProfile.CLUB, 100).lengthScale, MixTuning(MixProfile.CLUB, 400).lengthScale)
        assertEquals(MixTuning(MixProfile.CLUB, 0).lengthScale, MixTuning(MixProfile.CLUB, -30).lengthScale)
    }

    @Test
    fun clubHandsTheLowEndOverAndChillSweeps() {
        assertEquals(
            FilterTransitionVariant.BASS_HANDOFF,
            MixTuning(MixProfile.CLUB).pickVariant(kicksNoClash),
        )
        assertEquals(
            FilterTransitionVariant.SWEEP,
            MixTuning(MixProfile.CHILL).pickVariant(kicksNoClash),
        )
    }

    @Test
    fun aKeyClashOrTwoVoicesFavourTheSweep() {
        val clash = kicksNoClash.copy(keyClash = true, vocalConflict = true)
        assertEquals(FilterTransitionVariant.SWEEP, MixTuning(MixProfile.BALANCED, 80).pickVariant(clash))
        assertEquals(FilterTransitionVariant.SWEEP, MixTuning(MixProfile.CLUB).pickVariant(clash))
    }

    @Test
    fun noLowEndMeasurementMeansTheSweep() {
        val unknown = VariantSignals(kicksOnBothSides = null, keyClash = false, vocalConflict = false)
        assertEquals(FilterTransitionVariant.SWEEP, MixTuning(MixProfile.CLUB).pickVariant(unknown))
    }
}
