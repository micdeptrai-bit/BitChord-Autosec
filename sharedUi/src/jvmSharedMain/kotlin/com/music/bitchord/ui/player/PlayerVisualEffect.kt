package com.music.bitchord.ui.player

/** The effect drawn on the Now Playing seek and volume bars, moving with the music. */
enum class PlayerVisualEffect {
    OFF,

    /** A soft halo round the played part of the bar that swells with the bass, and a bright head. */
    GLOW,

    /** Equaliser bars rising from the bar, one per few dp, taller where the music is louder. */
    BARS,

    /** The same spectrum as one smooth filled wave rising from the bar. */
    WAVE,
}
