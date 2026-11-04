package com.music.bitchord.playback.smart

import kotlin.math.roundToInt

/*
 * Display helpers for a track's measured tempo and key.
 *
 * The analyzer names a key like "A minor" or "C♯ major" (see
 * native/analyzer/audio_analysis.cpp). That is the form the planner reasons
 * in, so it is left alone; this file only renders it for a person, adding the
 * Camelot wheel code DJs mix by ("A minor" is 8A) so two tracks can be judged
 * compatible at a glance: same number, or one step either way, or the same
 * number with the letter swapped.
 */

/** Below this the planner treats the key as unknown, so the player does not advertise it either. */
const val MIN_DISPLAY_KEY_CONFIDENCE = 0.25

private val NOTE_PITCH_CLASS = mapOf(
    "C" to 0, "C♯" to 1, "D♭" to 1, "D" to 2, "D♯" to 3, "E♭" to 3,
    "E" to 4, "F" to 5, "F♯" to 6, "G♭" to 6, "G" to 7, "G♯" to 8,
    "A♭" to 8, "A" to 9, "A♯" to 10, "B♭" to 10, "B" to 11,
)

/** Camelot number of the *major* key on each pitch class, C = 0. A minor key borrows its relative major's. */
private val CAMELOT_MAJOR_NUMBER = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)

/**
 * The Camelot code for an analyzer key such as "A minor" (8A) or "C major"
 * (8B), or null when [key] is not in that form. ASCII `#` and `b` are accepted
 * for the sharp and flat signs.
 */
fun camelotOf(key: String): String? {
    val parts = key.trim().split(' ').filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    val sharpened = parts[0].replace('#', '♯')
    val note = if (sharpened.length == 2 && sharpened[1] == 'b') "${sharpened[0]}♭" else sharpened
    val pitch = NOTE_PITCH_CLASS[note] ?: return null
    return when (parts[1].lowercase()) {
        "major" -> "${CAMELOT_MAJOR_NUMBER[pitch]}B"
        // A minor key sits three semitones below its relative major.
        "minor" -> "${CAMELOT_MAJOR_NUMBER[(pitch + 3) % 12]}A"
        else -> null
    }
}

/** Whole-number BPM of a usable analysis, or 0 while the track has not been measured. */
fun TrackAnalysis.displayBpm(): Int = if (isUsable) bpm.roundToInt() else 0

/**
 * The key as shown to a listener — "A minor · 8A" — or blank when the analysis
 * is not usable or the analyzer was not confident enough for the planner to
 * trust it either.
 */
fun TrackAnalysis.displayKey(): String {
    if (!isUsable || key.isBlank() || keyConfidence < MIN_DISPLAY_KEY_CONFIDENCE) return ""
    val name = key.trim()
    val camelot = camelotOf(name) ?: return name
    return "$name · $camelot"
}
