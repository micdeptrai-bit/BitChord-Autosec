package com.music.bitchord.playback.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A read-only look at what the player is sending out, for the visualizer on the Now Playing screen.
 *
 * Each [DspChain] owns a [SpectrumAnalyzer] and offers it the block it has just finished; the
 * analyzer does nothing at all until something is watching, so with the visualizer off this costs
 * a volatile read per block. It never touches the samples, so it cannot change what is heard.
 */
object SpectrumTap {
    const val BANDS = 32

    /** Set by the screen that draws the spectrum while it is on screen. */
    @Volatile
    var watched: Boolean = false

    private class Slot(val owner: Any) {
        val bands = FloatArray(BANDS)
        var atNanos = 0L
    }

    private val lock = Any()
    private val slots = ArrayList<Slot>(4)

    internal fun publish(owner: Any, bands: FloatArray) {
        val now = System.nanoTime()
        synchronized(lock) {
            var slot = slots.firstOrNull { it.owner === owner }
            if (slot == null) {
                // A chain that went away leaves a stale slot behind; reuse the oldest one.
                if (slots.size >= MAX_SLOTS) slots.removeAt(slots.indices.minByOrNull { slots[it].atNanos } ?: 0)
                slot = Slot(owner)
                slots.add(slot)
            }
            bands.copyInto(slot.bands)
            slot.atNanos = now
        }
    }

    /**
     * Copies the latest bands (0..1, low to high) into [into], taking the louder of the two
     * players while a crossfade has both audible. False when nothing has been heard lately.
     */
    fun read(into: FloatArray): Boolean {
        val now = System.nanoTime()
        var any = false
        into.fill(0f)
        synchronized(lock) {
            for (slot in slots) {
                if (now - slot.atNanos > FRESH_NANOS) continue
                any = true
                for (i in 0 until min(BANDS, into.size)) into[i] = max(into[i], slot.bands[i])
            }
        }
        return any
    }

    private const val MAX_SLOTS = 4
    private const val FRESH_NANOS = 250_000_000L
}

/** One FFT over the tail of each block it is offered, folded into [SpectrumTap.BANDS] log-spaced bands. */
class SpectrumAnalyzer {
    private val window = FloatArray(FFT_SIZE) { (0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1))).toFloat() }
    private val cosTable = FloatArray(FFT_SIZE / 2) { cos(2.0 * PI * it / FFT_SIZE).toFloat() }
    private val sinTable = FloatArray(FFT_SIZE / 2) { (-sin(2.0 * PI * it / FFT_SIZE)).toFloat() }
    private val reversed = IntArray(FFT_SIZE).also { table ->
        val bits = Integer.numberOfTrailingZeros(FFT_SIZE)
        for (i in table.indices) table[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)
    private val bands = FloatArray(SpectrumTap.BANDS)
    private var firstBin = IntArray(SpectrumTap.BANDS)
    private var lastBin = IntArray(SpectrumTap.BANDS)
    private var lastNanos = 0L

    fun configure(sampleRate: Int) {
        val top = min(16_000.0, sampleRate / 2.0 - 100.0).coerceAtLeast(2_000.0)
        val ratio = (top / LOW_HZ).pow(1.0 / SpectrumTap.BANDS)
        val binHz = sampleRate.toDouble() / FFT_SIZE
        for (b in 0 until SpectrumTap.BANDS) {
            val from = LOW_HZ * ratio.pow(b)
            val to = LOW_HZ * ratio.pow(b + 1)
            val first = (from / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            firstBin[b] = first
            lastBin[b] = max(first, (to / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1))
        }
    }

    fun process(block: AudioBlock) {
        if (!SpectrumTap.watched) return
        val now = System.nanoTime()
        if (now - lastNanos < MIN_INTERVAL_NANOS) return
        val frames = block.frameCount
        if (frames < MIN_FRAMES) return
        lastNanos = now

        val channels = block.channelCount
        val samples = block.samples
        val count = min(frames, FFT_SIZE)
        val start = frames - count
        for (i in 0 until FFT_SIZE) {
            if (i < count) {
                var sum = 0f
                val base = (start + i) * channels
                for (c in 0 until channels) sum += samples[base + c]
                re[i] = sum / channels * window[i]
            } else {
                re[i] = 0f
            }
            im[i] = 0f
        }
        transform()

        for (b in 0 until SpectrumTap.BANDS) {
            var peak = 0f
            for (k in firstBin[b]..lastBin[b]) {
                val magnitude = sqrt(re[k] * re[k] + im[k] * im[k])
                if (magnitude > peak) peak = magnitude
            }
            // A full-scale sine reads N/4 under a Hann window.
            val amplitude = 4f * peak / FFT_SIZE
            val db = 20f * log10(amplitude + 1e-7f)
            // The top of a mix sits well below its bass, so the bands are tilted up to be seen.
            val tilt = 8f * b / SpectrumTap.BANDS
            bands[b] = ((db + tilt + FLOOR_DB) / RANGE_DB).coerceIn(0f, 1f)
        }
        SpectrumTap.publish(this, bands)
    }

    /** In-place radix-2 FFT of [re] and [im]. */
    private fun transform() {
        for (i in 0 until FFT_SIZE) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= FFT_SIZE) {
            val half = size / 2
            val step = FFT_SIZE / size
            var i = 0
            while (i < FFT_SIZE) {
                var k = 0
                for (j in i until i + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val tr = wr * re[j + half] - wi * im[j + half]
                    val ti = wr * im[j + half] + wi * re[j + half]
                    re[j + half] = re[j] - tr
                    im[j + half] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }

    private companion object {
        const val FFT_SIZE = 1024
        const val MIN_FRAMES = 256
        const val LOW_HZ = 40.0
        const val MIN_INTERVAL_NANOS = 25_000_000L
        const val FLOOR_DB = 62f
        const val RANGE_DB = 46f
    }
}
