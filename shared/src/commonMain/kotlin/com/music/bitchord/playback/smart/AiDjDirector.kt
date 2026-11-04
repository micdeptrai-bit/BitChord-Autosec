package com.music.bitchord.playback.smart

import kotlin.math.abs
import kotlin.math.min

/**
 * What the AI DJ decided for one pair of songs.
 *
 * [pairOverride] is handed to [planTransitionWithOverride] in place of the stored one; null means
 * "the planner's own plan". [tuning] and [gesture] replace the listener's global Mix taste for this
 * pair only. [notes] is a short audit trail for the log, so a verdict can be explained afterwards.
 */
data class AiDjDecision(
    val pairOverride: TransitionOverride?,
    val tuning: MixTuning,
    val gesture: FilterTransitionVariant?,
    val notes: List<String>,
)

/**
 * AI DJ Mix: picks how each pair is mixed the way a DJ would, from what the analysis measured,
 * instead of falling back to a plain crossfade.
 *
 * It only chooses inputs the planner already understands (a bar length, a [MixTuning]), so every
 * invariant the planner keeps (edges on the beat, cue points, playback rates) still holds. It is a
 * pure function of the two analyses and the listener's settings: the same pair always gets the
 * same answer, which matters because the plan is recomputed on every playback tick.
 *
 * What a DJ would do, and what this does:
 * - Same tempo and a compatible key: a long, 16-bar blend, with the low-end handoff where both
 *   ends have a kick.
 * - Same tempo, borderline or clashing key: a shorter 8-bar blend, tighter when the keys clash so
 *   the clash is over quickly.
 * - Tempi a little apart: the planner's adaptive plan, nudged a little punchier.
 * - Tempi too far apart to beatmatch: a short, crisp handoff (filter sweep or bass swap), never a
 *   long smear of two tempi.
 * - A song that has not been analysed: a clean one-second cut, never a plain multi-second fade.
 *
 * The listener's own choices win: a pair set to "no mix", classic Automix or a quick cut is left
 * alone, a Mix profile other than Balanced or an intensity other than 50 is kept as it is, and a
 * forced gesture stays forced. Only the plain-crossfade styles are replaced, because replacing
 * them is the point of this mode.
 */
object AiDjDirector {
    const val NOTE_KEPT_PAIR_CHOICE = "ai-kept-pair-choice"
    const val NOTE_REPLACED_FADE = "ai-replaced-plain-fade"
    const val NOTE_CUT_NO_ANALYSIS = "ai-cut-no-analysis"
    const val NOTE_LONG_BLEND = "ai-long-blend"
    const val NOTE_SHORT_BLEND = "ai-short-blend"
    const val NOTE_CLASH_SHORT = "ai-clash-short"
    const val NOTE_ADAPTIVE = "ai-adaptive"
    const val NOTE_CRISP_HANDOFF = "ai-crisp-handoff"
    const val NOTE_LISTENER_TASTE = "ai-listener-taste-kept"

    /** Relative tempo gap that still beatmatches without audibly bending either song. */
    private const val MATCH_GAP = 0.06

    /** Relative tempo gap inside which the planner can still do something sensible on its own. */
    private const val NEAR_GAP = 0.12

    /** Camelot steps apart (same letter) up to which two keys are called compatible. */
    private const val COMPATIBLE_STEPS = 1

    /** Beyond this many steps, or a letter swap on a different number, the keys clash. */
    private const val CLASH_STEPS = 3

    fun direct(
        analysis: TrackAnalysis,
        nextAnalysis: TrackAnalysis,
        stored: TransitionOverride?,
        listener: MixTuning,
        listenerGesture: FilterTransitionVariant?,
    ): AiDjDecision {
        val notes = ArrayList<String>()

        // The listener's explicit "no mix", classic Automix or quick cut for this pair stands.
        if (stored != null &&
            (!stored.enabled || stored.style == OverrideStyle.CLASSIC || stored.style == OverrideStyle.QUICK_CUT)
        ) {
            notes.add(NOTE_KEPT_PAIR_CHOICE)
            return AiDjDecision(stored, listener, listenerGesture, notes)
        }

        val replacedFade = stored != null &&
            (stored.style == OverrideStyle.CROSSFADE || stored.style == OverrideStyle.LONG_FADE)
        if (replacedFade) notes.add(NOTE_REPLACED_FADE)
        val askedLength = if (stored != null && !replacedFade) stored.length else OverrideLength.AUTO

        // Nothing measured yet: a DJ with no idea of the beat cuts, they do not smear.
        if (!analysis.isUsable || !nextAnalysis.isUsable) {
            notes.add(NOTE_CUT_NO_ANALYSIS)
            return AiDjDecision(
                pairOverride = TransitionOverride(style = OverrideStyle.QUICK_CUT),
                tuning = listener,
                gesture = listenerGesture,
                notes = notes,
            )
        }

        val gap = tempoGap(analysis.bpm, nextAnalysis.bpm)
        val steps = harmonicSteps(analysis, nextAnalysis)
        val matched = gap != null && gap <= MATCH_GAP
        val near = gap != null && gap <= NEAR_GAP
        val compatible = steps == null || steps <= COMPATIBLE_STEPS
        val clash = steps != null && steps >= CLASH_STEPS

        val chosenLength: OverrideLength
        val chosenTuning: MixTuning
        if (matched && compatible) {
            chosenLength = OverrideLength.BARS_16
            chosenTuning = MixTuning(MixProfile.CLUB, 55)
            notes.add(NOTE_LONG_BLEND)
        } else if (matched && clash) {
            chosenLength = OverrideLength.BARS_8
            chosenTuning = MixTuning(MixProfile.RADIO, 65)
            notes.add(NOTE_CLASH_SHORT)
        } else if (matched) {
            chosenLength = OverrideLength.BARS_8
            chosenTuning = MixTuning(MixProfile.BALANCED, 60)
            notes.add(NOTE_SHORT_BLEND)
        } else if (near) {
            chosenLength = OverrideLength.AUTO
            chosenTuning = MixTuning(MixProfile.BALANCED, 55)
            notes.add(NOTE_ADAPTIVE)
        } else {
            chosenLength = OverrideLength.AUTO
            chosenTuning = MixTuning(MixProfile.RADIO, 60)
            notes.add(NOTE_CRISP_HANDOFF)
        }

        // A taste the listener set themselves is not for the AI to overwrite.
        val tuning = if (listener.isDefault) {
            chosenTuning
        } else {
            notes.add(NOTE_LISTENER_TASTE)
            listener
        }
        val length = if (askedLength != OverrideLength.AUTO) askedLength else chosenLength
        val pairOverride = if (length == OverrideLength.AUTO) null else TransitionOverride(length = length)
        return AiDjDecision(pairOverride, tuning, listenerGesture, notes)
    }

    /**
     * How far apart the tempi are, as a fraction of the first. Null while either is unmeasured.
     * A song at half or double the other's tempo counts as near, never as matched: the planner
     * decides what it can do with that, this only declines to ask for a long blend it may not carry.
     */
    fun tempoGap(from: Double, to: Double): Double? {
        if (!from.isFinite() || !to.isFinite() || from <= 0.0 || to <= 0.0) return null
        val straight = abs(to / from - 1.0)
        val doubled = abs(to / (2.0 * from) - 1.0)
        val halved = abs(2.0 * to / from - 1.0)
        val related = min(doubled, halved)
        return if (related <= MATCH_GAP && straight > NEAR_GAP) NEAR_GAP else straight
    }

    /**
     * Distance on the Camelot wheel: 0 for the same code, 1 for a neighbour or the relative
     * major/minor, larger for worse. Null when either key is unknown or not trusted, which the
     * planner treats the same way.
     */
    fun harmonicSteps(a: TrackAnalysis, b: TrackAnalysis): Int? {
        val x = camelotPosition(a) ?: return null
        val y = camelotPosition(b) ?: return null
        val raw = abs(x.first - y.first)
        val around = min(raw, 12 - raw)
        return when {
            x.second == y.second -> around
            around == 0 -> 1
            else -> around + 2
        }
    }

    private fun camelotPosition(analysis: TrackAnalysis): Pair<Int, Char>? {
        if (analysis.key.isBlank() || analysis.keyConfidence < MIN_DISPLAY_KEY_CONFIDENCE) return null
        val code = camelotOf(analysis.key) ?: return null
        val number = code.dropLast(1).toIntOrNull() ?: return null
        return Pair(number, code.last())
    }
}
