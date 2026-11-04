package com.music.bitchord.playback.smart

import kotlin.math.min

/**
 * The taste the listener gives Automix, on top of what each pair of tracks asks for.
 *
 * It never overrides the analysis: a pair that cannot be beat-matched is still not
 * beat-matched, and a pair that cannot be mixed is still faded. What a profile moves is
 * how long the overlap may run and which handoff gesture an unmatched pair gets.
 */
enum class MixProfile {
    /** Whatever the planner would have done without any taste. */
    BALANCED,

    /** Longer, softer overlaps, and a filter sweep rather than a low-end handoff. */
    CHILL,

    /** A little longer than balanced, and the low-end handoff wherever both ends have a kick. */
    CLUB,

    /** Short, quick overlaps. */
    RADIO,
}

/**
 * [profile] and [intensity] (0..100, 50 being what Automix always did) together.
 *
 * At the defaults every number here is exactly the neutral one, so a [DEFAULT] tuning plans
 * byte-for-byte as before; [pickVariant] answers null then, and the planner's own choice
 * stands.
 */
data class MixTuning(
    val profile: MixProfile = MixProfile.BALANCED,
    val intensity: Int = DEFAULT_INTENSITY,
) {
    private val level: Int get() = intensity.coerceIn(0, 100)

    val isDefault: Boolean get() = profile == MixProfile.BALANCED && level == DEFAULT_INTENSITY

    /**
     * Multiplies the longest overlap the planner allows. A gentle setting stretches it, a
     * punchy one shortens it; the planner's own caps (the song's length, its energy) still
     * apply to the result.
     */
    val lengthScale: Double
        get() {
            val byProfile = when (profile) {
                MixProfile.BALANCED -> 1.0
                MixProfile.CHILL -> 1.3
                MixProfile.CLUB -> 1.1
                MixProfile.RADIO -> 0.7
            }
            return byProfile * (1.25 - 0.5 * level / 100.0)
        }

    /**
     * Multiplies the depth of the filter sweep an unmatched pair gets. Only ever softens: the
     * renderer treats 1 as the full sweep, and a punchy setting has nothing deeper to ask for.
     */
    val sweepScale: Double get() = min(1.0, 0.5 + level / 100.0)

    /**
     * Scores the two handoff gestures an unmatched pair can get, or null at the defaults so
     * the planner keeps the choice it always made.
     *
     * Echo out is left out on purpose: the planner switched it off because its repeats flam
     * against the incoming grid and were heard as the song cutting out.
     */
    fun pickVariant(signals: VariantSignals): FilterTransitionVariant? {
        if (isDefault) return null
        var sweep = 1.0
        var handoff = if (signals.kicksOnBothSides == true) 1.4 else 0.2
        // A filter sweep hides a key clash and separates two voices; a low-end handoff does not.
        if (signals.keyClash) sweep += 0.5
        if (signals.vocalConflict) {
            sweep += 0.5
            handoff -= 0.3
        }
        when (profile) {
            MixProfile.CHILL -> {
                sweep += 0.4
                handoff -= 0.2
            }
            MixProfile.CLUB -> {
                sweep -= 0.2
                handoff += 0.5
            }
            MixProfile.RADIO -> sweep += 0.2
            MixProfile.BALANCED -> Unit
        }
        return if (handoff > sweep) FilterTransitionVariant.BASS_HANDOFF else FilterTransitionVariant.SWEEP
    }

    companion object {
        const val DEFAULT_INTENSITY = 50
        val DEFAULT = MixTuning()
    }
}

/** What the planner knows about a pair when it picks a handoff gesture. */
data class VariantSignals(
    /** Both ends have a kick under the join, or null when there is no low-end measurement. */
    val kicksOnBothSides: Boolean?,
    val keyClash: Boolean,
    val vocalConflict: Boolean,
)
