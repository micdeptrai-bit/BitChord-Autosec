package com.music.bitchord.playback.smart

/**
 * What the listener asked for on one specific pair of songs, on top of what
 * Automix would have decided by itself.
 *
 * This layer never edits a finished [TransitionPlan]. Every knob is resolved
 * onto an input the planner already understands (`mode`, `phraseBars`,
 * `fadeSeconds`) and the plan is then built by the planner itself, so the
 * invariants it keeps — edges on the beat, overlap = end - start, cue points,
 * playback rates — hold for an overridden pair exactly as they do for an
 * automatic one.
 *
 * Deliberately small for the first version. Forcing a DJ blend or a filter
 * ride onto a pair the planner declined, and shifting the start by bars, both
 * need changes inside the planner and are not offered here.
 */
enum class OverrideStyle {
    /** Let Automix decide. */
    AUTO,

    /** A plain equal-power crossfade: no beat-matching, no filter. */
    CROSSFADE,

    /**
     * Automix as it worked before the DJ planner: no phrase alignment, no key or
     * vocal checks, and tempo matched by changing the incoming song's speed rather
     * than by leaving it alone. The planner's own plan with `advanced` off, so it
     * is as sound as that plan always was; it just sounds different.
     */
    CLASSIC,

    /** A near-cut: a one-second fade, as a radio segue would do. */
    QUICK_CUT,

    /** The longest plain fade the planner allows, for songs that should melt into each other. */
    LONG_FADE,
}

/**
 * A fixed overlap measured in bars of the outgoing track's own grid. These are
 * the two lengths the planner's phrase mode supports (see [planTransition]'s
 * `phraseBars`).
 */
enum class OverrideLength(val bars: Int) {
    AUTO(0),
    BARS_8(PHRASE_BARS_SHORT),
    BARS_16(PHRASE_BARS_LONG),
}

const val OVERRIDE_DEFAULT_CROSSFADE_SECONDS = 6.0
const val OVERRIDE_MIN_CROSSFADE_SECONDS = 2.0

/** The planner's own ceiling for a plain fade; asking for more would be clamped anyway. */
const val OVERRIDE_MAX_CROSSFADE_SECONDS = 12.0

/** How long [OverrideStyle.QUICK_CUT] fades for. */
const val OVERRIDE_QUICK_CUT_SECONDS = 1.0

/** How long [OverrideStyle.LONG_FADE] fades for: the longest plain fade there is. */
const val OVERRIDE_LONG_FADE_SECONDS = OVERRIDE_MAX_CROSSFADE_SECONDS

/** Reason string on a plan blocked because the listener turned the mix off for this pair. */
const val REASON_USER_OFF = "user-off"

/** The pair cannot carry a bar-aligned mix of the requested length (no trusted tempo, or too short). */
const val NOTE_LENGTH_UNAVAILABLE = "length-unavailable"

data class TransitionOverride(
    /** False means: no mix for this pair, the outgoing track just plays on. */
    val enabled: Boolean = true,
    val style: OverrideStyle = OverrideStyle.AUTO,
    val length: OverrideLength = OverrideLength.AUTO,
    /** Only read when [style] is [OverrideStyle.CROSSFADE]. */
    val crossfadeSeconds: Double = OVERRIDE_DEFAULT_CROSSFADE_SECONDS,
) {
    /** True when this changes nothing, so a store need not keep it. */
    val isDefault: Boolean
        get() = enabled && style == OverrideStyle.AUTO && length == OverrideLength.AUTO
}

/**
 * A plan plus what the override did to it.
 *
 * [overrideApplied] is false when there was nothing to apply, or when the
 * request could not be honoured; in the second case [notes] says why, so a
 * settings sheet can tell the listener rather than silently ignore them.
 */
data class OverriddenPlan(
    val plan: TransitionPlan,
    val overrideApplied: Boolean,
    val notes: List<String> = emptyList(),
)

/**
 * [planTransition], with one pair's [pairOverride] folded in. With a null or
 * default override this returns exactly what [planTransition] returns.
 *
 * The remaining parameters mirror [planTransition] one for one.
 */
fun planTransitionWithOverride(
    pairOverride: TransitionOverride?,
    analysis: TrackAnalysis = TrackAnalysis(),
    nextAnalysis: TrackAnalysis = TrackAnalysis(),
    currentTrack: TransitionTrackInfo? = null,
    nextTrack: TransitionTrackInfo? = null,
    currentTime: Double = 0.0,
    duration: Double = 0.0,
    fadeSeconds: Double = 6.0,
    minFadeSeconds: Double = 1.0,
    mode: CrossfadeMode = CrossfadeMode.STANDARD,
    albumSequential: Boolean = false,
    advanced: Boolean = false,
    phraseBars: Int = 0,
    mixWindow: Boolean = false,
    preferredFilterVariant: FilterTransitionVariant? = null,
    tuning: MixTuning = MixTuning.DEFAULT,
): OverriddenPlan {
    if (pairOverride == null || pairOverride.isDefault) {
        val plan = planTransition(
            analysis = analysis,
            nextAnalysis = nextAnalysis,
            currentTrack = currentTrack,
            nextTrack = nextTrack,
            currentTime = currentTime,
            duration = duration,
            fadeSeconds = fadeSeconds,
            minFadeSeconds = minFadeSeconds,
            mode = mode,
            albumSequential = albumSequential,
            advanced = advanced,
            phraseBars = phraseBars,
            mixWindow = mixWindow,
            preferredFilterVariant = preferredFilterVariant,
            tuning = tuning,
        )
        return OverriddenPlan(plan, overrideApplied = false)
    }

    if (!pairOverride.enabled) {
        // Blocked plans draw no marker and arm nothing: the track plays to its end.
        return OverriddenPlan(
            plan = TransitionPlan(blocked = true, reason = REASON_USER_OFF),
            overrideApplied = true,
        )
    }

    // The three plain fades are one algorithm that differs only in how long it lasts.
    val plainFadeSeconds: Double? = when (pairOverride.style) {
        OverrideStyle.CROSSFADE -> clamp(
            pairOverride.crossfadeSeconds,
            OVERRIDE_MIN_CROSSFADE_SECONDS,
            OVERRIDE_MAX_CROSSFADE_SECONDS,
        )
        OverrideStyle.QUICK_CUT -> OVERRIDE_QUICK_CUT_SECONDS
        OverrideStyle.LONG_FADE -> OVERRIDE_LONG_FADE_SECONDS
        OverrideStyle.AUTO, OverrideStyle.CLASSIC -> null
    }
    if (plainFadeSeconds != null) {
        // The planner's non-smart path builds the plain equal-power fade, so
        // the result is the same plan every unanalysed pair already gets.
        val plan = planTransition(
            analysis = analysis,
            nextAnalysis = nextAnalysis,
            currentTrack = currentTrack,
            nextTrack = nextTrack,
            currentTime = currentTime,
            duration = duration,
            fadeSeconds = plainFadeSeconds,
            // A quick cut is shorter than the usual floor; the floor is for the global default.
            minFadeSeconds = minOf(minFadeSeconds, plainFadeSeconds),
            mode = CrossfadeMode.STANDARD,
            albumSequential = false,
            advanced = false,
            phraseBars = 0,
            mixWindow = mixWindow,
        )
        return OverriddenPlan(plan, overrideApplied = true)
    }

    // Style AUTO with a fixed length: ask the planner's phrase mode for it. The
    // override wins over a global phrase setting passed by the caller.
    val bars = pairOverride.length.bars
    val plan = planTransition(
        analysis = analysis,
        nextAnalysis = nextAnalysis,
        currentTrack = currentTrack,
        nextTrack = nextTrack,
        currentTime = currentTime,
        duration = duration,
        fadeSeconds = fadeSeconds,
        minFadeSeconds = minFadeSeconds,
        mode = mode,
        albumSequential = albumSequential,
        advanced = advanced && pairOverride.style != OverrideStyle.CLASSIC,
        phraseBars = bars,
        mixWindow = mixWindow,
        preferredFilterVariant = preferredFilterVariant,
        tuning = tuning,
    )
    // The planner falls back to its adaptive plan when the evidence cannot carry
    // a bar-aligned mix of that length; its reason string says which one ran.
    // With no length asked for there is nothing to honour or to fail at: that is
    // [OverrideStyle.CLASSIC] on its own.
    val honoured = bars == 0 || plan.reason.contains("phrase-$bars-bar")
    return OverriddenPlan(
        plan = plan,
        overrideApplied = honoured,
        notes = if (honoured) emptyList() else listOf(NOTE_LENGTH_UNAVAILABLE),
    )
}
