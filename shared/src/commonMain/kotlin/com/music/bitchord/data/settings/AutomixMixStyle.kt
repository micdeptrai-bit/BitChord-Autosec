package com.music.bitchord.data.settings

import com.music.bitchord.playback.smart.FilterTransitionVariant

/**
 * When Automix may start a transition. How *long* it runs is the Mix button's
 * business (Auto, 8 or 16 bars, and per pair); this only decides whether the
 * planner may reach anywhere in the song or must keep to the ends. It maps onto
 * a mode [com.music.bitchord.playback.smart.planTransition] already implements,
 * so choosing it never changes what the renderer is asked to do — only which
 * plan it is handed.
 */
enum class AutomixMixStyle(
    /** Confine the mix to the last and first 30 seconds of the pair. */
    val mixWindow: Boolean,
) {
    /** The planner picks the start from how well the pair agrees. The default, and what shipped before. */
    ADAPTIVE(mixWindow = false),

    /** The adaptive plan, but never earlier than 30 seconds before the end or deeper than 30 seconds in. */
    WINDOWED(mixWindow = true),
}

/**
 * The gesture Automix uses for a pair whose tempi are too far apart to beatmatch.
 * [AUTO] keeps the planner's own pick, which is stable per pair but varies from pair to pair;
 * any other entry forces that gesture for every such pair.
 */
enum class AutomixGesture(val variant: FilterTransitionVariant?) {
    AUTO(null),
    SWEEP(FilterTransitionVariant.SWEEP),
    BASS_HANDOFF(FilterTransitionVariant.BASS_HANDOFF),
    ECHO_RIDE(FilterTransitionVariant.ECHO_RIDE),
}
