package com.music.bitchord.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

/** How many bands [PlayerSettingsSource.readSpectrum] fills. */
internal const val SPECTRUM_BANDS = 32

/**
 * Counts the bars that want the spectrum, so the audio side computes it while at least one is
 * on screen and not a moment longer. Only touched from composition effects, on the main thread.
 */
internal object SpectrumWatch {
    private var watchers = 0

    fun acquire() {
        if (watchers++ == 0) PlayerSettings.setSpectrumWatched(true)
    }

    fun release() {
        if (watchers > 0 && --watchers == 0) PlayerSettings.setSpectrumWatched(false)
    }
}

/**
 * What a bar needs to move with the music: the smoothed [levels] of each band, and a [tick] that
 * is bumped whenever they change. Both are read in draw only, so a new frame redraws the bar and
 * recomposes nothing.
 */
@Stable
internal class BarEffect(val style: PlayerVisualEffect) {
    val levels = FloatArray(SPECTRUM_BANDS)
    var tick by mutableLongStateOf(0L)
    var seconds by mutableFloatStateOf(0f)

    val bass: Float
        get() {
            var sum = 0f
            for (i in 0 until BASS_BANDS) sum += levels[i]
            return sum / BASS_BANDS
        }

    private companion object {
        const val BASS_BANDS = 5
    }
}

/**
 * The listener's chosen effect, ready to hand to a [ThinSlider]; null while it is off, while
 * "reduce animation" is on, so that nothing is drawn, computed or watched.
 *
 * Silence and a paused player both settle the levels to nothing, after which the clock polls
 * slowly rather than asking for a frame at the display's rate.
 */
@Composable
internal fun rememberBarEffect(): BarEffect? {
    val style by PlayerSettings.visualEffect.collectAsStateWithLifecycle()
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val active = !reduceAnimation && style != PlayerVisualEffect.OFF
    val effect = remember(style) { BarEffect(style) }
    if (active) {
        DisposableEffect(Unit) {
            SpectrumWatch.acquire()
            onDispose { SpectrumWatch.release() }
        }
        LaunchedEffect(effect) {
            val raw = FloatArray(SPECTRUM_BANDS)
            var last = 0L
            while (true) {
                val settled = withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    last = now
                    val live = PlayerSettings.readSpectrum(raw)
                    if (!live) raw.fill(0f)
                    var loudest = 0f
                    for (i in effect.levels.indices) {
                        val target = raw[i]
                        // Quick to rise, slow to fall, so a kick reads as a punch rather than a flicker.
                        effect.levels[i] += (target - effect.levels[i]) * (if (target > effect.levels[i]) 0.55f else 0.10f)
                        loudest = max(loudest, effect.levels[i])
                    }
                    val quiet = !live && loudest < 0.003f
                    if (!quiet) {
                        effect.seconds += dt
                        effect.tick++
                    }
                    quiet
                }
                if (settled) delay(150)
            }
        }
    }
    return if (active) effect else null
}

/**
 * Draws [effect] on the strip a [ThinSlider] sits in: above the track for the bars and the wave,
 * around it for the glow. It lies under the track, so the track itself is never altered.
 */
@Composable
internal fun BarEffectCanvas(
    effect: BarEffect,
    progress: Float,
    trackHeight: Dp,
    stripHeight: Dp,
    playedColor: Color,
    unplayedColor: Color,
) {
    Canvas(Modifier.fillMaxWidth().height(stripHeight)) {
        @Suppress("UNUSED_VARIABLE")
        val redraw = effect.tick
        drawBarEffect(effect, progress, trackHeight.toPx(), playedColor, unplayedColor)
    }
}

private fun sample(levels: FloatArray, t: Float): Float {
    val position = t.coerceIn(0f, 1f) * (levels.size - 1)
    val index = floor(position).toInt().coerceIn(0, levels.size - 2)
    val fraction = position - index
    return levels[index] * (1f - fraction) + levels[index + 1] * fraction
}

private fun DrawScope.drawBarEffect(
    effect: BarEffect,
    progress: Float,
    trackHeightPx: Float,
    activeColor: Color,
    inactiveColor: Color,
) {
    val trackTop = (size.height - trackHeightPx) / 2f
    val gap = 1.5.dp.toPx()
    val baseline = trackTop - gap
    val maxHeight = min(baseline, 11.dp.toPx())
    val split = size.width * progress.coerceIn(0f, 1f)
    val played = activeColor.copy(alpha = activeColor.alpha * 0.85f)
    val unplayed = inactiveColor.copy(alpha = min(1f, inactiveColor.alpha * 1.5f))
    val levels = effect.levels

    when (effect.style) {
        PlayerVisualEffect.BARS -> {
            if (maxHeight <= 0f) return
            val barWidth = 2.5.dp.toPx()
            val count = max(2, (size.width / 5.dp.toPx()).toInt())
            for (i in 0 until count) {
                val x = (i + 0.5f) * size.width / count
                val height = sample(levels, i / (count - 1f)) * maxHeight
                if (height < 1.dp.toPx()) continue
                drawRoundRect(
                    color = if (x <= split) played else unplayed,
                    topLeft = Offset(x - barWidth / 2f, baseline - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }

        PlayerVisualEffect.WAVE -> {
            if (maxHeight <= 0f) return
            val step = 4.dp.toPx()
            val points = max(2, (size.width / step).toInt())
            val path = Path()
            path.moveTo(0f, baseline)
            for (i in 0..points) {
                val x = size.width * i / points
                path.lineTo(x, baseline - sample(levels, i / points.toFloat()) * maxHeight)
            }
            path.lineTo(size.width, baseline)
            path.close()
            clipRect(left = 0f, top = 0f, right = split, bottom = size.height) {
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(played, played.copy(alpha = 0f)),
                        startY = baseline - maxHeight,
                        endY = baseline,
                    ),
                )
            }
            clipRect(left = split, top = 0f, right = size.width, bottom = size.height) {
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(unplayed, unplayed.copy(alpha = 0f)),
                        startY = baseline - maxHeight,
                        endY = baseline,
                    ),
                )
            }
        }

        PlayerVisualEffect.GLOW -> {
            val bass = effect.bass
            val filled = max(split, trackHeightPx)
            for (layer in 1..3) {
                val grow = layer * 2.5.dp.toPx()
                drawRoundRect(
                    color = activeColor.copy(alpha = ((0.06f + 0.32f * bass) / layer).coerceIn(0f, 0.5f)),
                    topLeft = Offset(-grow, trackTop - grow),
                    size = Size(filled + grow, trackHeightPx + 2f * grow),
                    cornerRadius = CornerRadius(trackHeightPx / 2f + grow),
                )
            }
            drawCircle(
                color = activeColor.copy(alpha = (0.2f + 0.45f * bass).coerceIn(0f, 0.7f)),
                radius = trackHeightPx * (0.9f + 1.1f * bass),
                center = Offset(split, trackTop + trackHeightPx / 2f),
            )
        }

        PlayerVisualEffect.OFF -> Unit
    }
}
