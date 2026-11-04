package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.smart.OVERRIDE_DEFAULT_CROSSFADE_SECONDS
import com.music.bitchord.playback.smart.OverrideLength
import com.music.bitchord.playback.smart.OverrideStyle
import com.music.bitchord.playback.smart.TransitionOverride
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the listener chose for individual pairs of songs, keyed by the outgoing
 * and incoming media ids.
 *
 * Kept as one small JSON array in its own SharedPreferences file, in the same
 * spirit as the rest of the app's settings. A pair that goes back to defaults is
 * removed rather than stored, and the oldest entries are dropped past
 * [MAX_ENTRIES], so a long-lived install cannot grow it without bound.
 */
object TransitionOverrideStore {
    private const val PREFS_NAME = "bitchord_transition_overrides"
    private const val KEY_ENTRIES = "entries_v1"
    private const val MAX_ENTRIES = 500

    private data class PairKey(val outgoing: String, val incoming: String)

    private var prefs: SharedPreferences? = null

    /** Insertion-ordered, so the first key is always the oldest. */
    private val entries = LinkedHashMap<PairKey, TransitionOverride>()

    /** Loads what was saved. Call once at startup, next to the other stores. */
    @Synchronized
    fun init(context: Context) {
        val stored = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = stored
        entries.clear()
        val raw = stored.getString(KEY_ENTRIES, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val entry = array.getJSONObject(index)
                val outgoing = entry.optString("out")
                val incoming = entry.optString("in")
                if (outgoing.isEmpty() || incoming.isEmpty()) continue
                entries[PairKey(outgoing, incoming)] = TransitionOverride(
                    enabled = entry.optBoolean("enabled", true),
                    style = enumOrDefault(entry.optString("style"), OverrideStyle.AUTO),
                    length = enumOrDefault(entry.optString("length"), OverrideLength.AUTO),
                    crossfadeSeconds = entry.optDouble("crossfade", OVERRIDE_DEFAULT_CROSSFADE_SECONDS),
                )
            }
        }.onFailure { entries.clear() }
    }

    @Synchronized
    fun get(outgoingId: String, incomingId: String): TransitionOverride? =
        entries[PairKey(outgoingId, incomingId)]

    /**
     * What to plan this pair with: its own choice if it has one, otherwise the
     * listener's global Mix settings, otherwise nothing (Automix decides).
     */
    fun forPair(outgoingId: String, incomingId: String): TransitionOverride? =
        get(outgoingId, incomingId) ?: globalOverride()

    private fun globalOverride(): TransitionOverride? {
        val global = TransitionOverride(
            style = when (AppSettings.smartMixStyle.value) {
                1 -> OverrideStyle.CROSSFADE
                2 -> OverrideStyle.CLASSIC
                3 -> OverrideStyle.QUICK_CUT
                4 -> OverrideStyle.LONG_FADE
                else -> OverrideStyle.AUTO
            },
            length = when (AppSettings.smartMixBars.value) {
                8 -> OverrideLength.BARS_8
                16 -> OverrideLength.BARS_16
                else -> OverrideLength.AUTO
            },
        )
        return if (global.isDefault) null else global
    }

    /** Saves [value] for the pair, or forgets the pair when [value] changes nothing. */
    @Synchronized
    fun set(outgoingId: String, incomingId: String, value: TransitionOverride) {
        val key = PairKey(outgoingId, incomingId)
        // Removed first so a rewritten pair moves to the newest end.
        entries.remove(key)
        if (!value.isDefault) {
            entries[key] = value
            while (entries.size > MAX_ENTRIES) {
                val oldest = entries.keys.iterator().next()
                entries.remove(oldest)
            }
        }
        persist()
    }

    @Synchronized
    fun clear(outgoingId: String, incomingId: String) {
        if (entries.remove(PairKey(outgoingId, incomingId)) != null) persist()
    }

    private fun persist() {
        val array = JSONArray()
        for ((key, value) in entries) {
            array.put(
                JSONObject()
                    .put("out", key.outgoing)
                    .put("in", key.incoming)
                    .put("enabled", value.enabled)
                    .put("style", value.style.name)
                    .put("length", value.length.name)
                    .put("crossfade", value.crossfadeSeconds),
            )
        }
        prefs?.edit()?.putString(KEY_ENTRIES, array.toString())?.apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
