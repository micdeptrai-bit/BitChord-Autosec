package com.music.bitchord.lockscreen

import android.graphics.Typeface
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * The clock faces the lock-screen player can wear.
 *
 * The choice is stored by [name] (see [com.music.bitchord.data.settings.AppSettings.lockScreenClockStyle]),
 * so new faces can be added anywhere in this list without moving anybody's
 * saved choice onto a different one.
 *
 * [previewScale] is how far a face is shrunk to fit its tile in the picker.
 */
enum class LockClockStyle(@StringRes val labelRes: Int, val previewScale: Float) {
    CLASSIC(R.string.lock_clock_classic, 0.30f),
    THIN(R.string.lock_clock_thin, 0.26f),
    STACKED(R.string.lock_clock_stacked, 0.24f),
    SERIF(R.string.lock_clock_serif, 0.30f),
    DIGITAL(R.string.lock_clock_digital, 0.25f),
    CONDENSED(R.string.lock_clock_condensed, 0.26f),
    OUTLINE(R.string.lock_clock_outline, 0.28f),
    GRADIENT(R.string.lock_clock_gradient, 0.30f),
    DUAL(R.string.lock_clock_dual, 0.28f),
    EDITORIAL(R.string.lock_clock_editorial, 0.30f),
    SCRIPT(R.string.lock_clock_script, 0.28f),
    RING(R.string.lock_clock_ring, 0.30f),
    ANALOG(R.string.lock_clock_analog, 0.28f),
    COMPACT(R.string.lock_clock_compact, 0.80f),
    GLASS(R.string.lock_clock_glass, 0.27f),
    GHOST(R.string.lock_clock_ghost, 0.25f),
    WIRE(R.string.lock_clock_wire, 0.26f),
    FROSTED(R.string.lock_clock_frosted, 0.40f),
    NEON(R.string.lock_clock_neon, 0.28f),
    CARDS(R.string.lock_clock_cards, 0.32f),
    MINIMAL(R.string.lock_clock_minimal, 0.62f),
    ;

    /** Whether the face shows seconds, so the host has to tick every second. */
    val usesSeconds: Boolean
        get() = this == DIGITAL || this == RING || this == ANALOG

    fun next(): LockClockStyle = LockClockStyle.entries[(ordinal + 1) % LockClockStyle.entries.size]

    companion object {
        fun from(name: String?): LockClockStyle = LockClockStyle.entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

/** The time as the clock should show it: truncated to the minute unless [withSeconds]. */
@Composable
internal fun rememberClockTime(withSeconds: Boolean): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now().truncated(withSeconds)) }
    LaunchedEffect(withSeconds) {
        while (true) {
            now = LocalDateTime.now().truncated(withSeconds)
            delay(if (withSeconds) 200L else 1_000L)
        }
    }
    return now
}

private fun LocalDateTime.truncated(withSeconds: Boolean): LocalDateTime =
    if (withSeconds) withNano(0) else withSecond(0).withNano(0)

// --- typefaces -------------------------------------------------------------
//
// The system's own families, picked by name. Nothing is bundled: a face the
// phone lacks quietly falls back to its regular sans.

private val ThinFamily = FontFamily(Typeface.create("sans-serif-thin", Typeface.NORMAL))
private val LightFamily = FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
private val BlackFamily = FontFamily(Typeface.create("sans-serif-black", Typeface.NORMAL))
private val BoldFamily = FontFamily(Typeface.create("sans-serif", Typeface.BOLD))
private val CondensedFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
private val MonoFamily = FontFamily(Typeface.create("monospace", Typeface.NORMAL))

/** A soft drop shadow so a light face still reads over a bright cover. */
private val ClockShadow = Shadow(
    color = Color.Black.copy(alpha = 0.34f),
    offset = Offset(0f, 3f),
    blurRadius = 22f,
)

private fun scaledSp(value: Float, scale: Float) = (value * scale).sp

private fun face(
    size: Float,
    scale: Float,
    family: FontFamily? = null,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
    color: Color = Color.White,
    italic: Boolean = false,
    shadow: Boolean = true,
): TextStyle = TextStyle(
    color = color,
    fontSize = scaledSp(size, scale),
    fontFamily = family,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    letterSpacing = scaledSp(spacing, scale),
    // Fixed-width digits, so the face does not shuffle sideways each minute.
    fontFeatureSettings = "tnum",
    shadow = if (shadow) ClockShadow else null,
    textAlign = TextAlign.Center,
)

@Composable
private fun ClockText(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(
        text = text,
        style = style,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
}

@Composable
private fun DateLine(text: String, scale: Float, alpha: Float = 0.88f, size: Float = 20f) {
    ClockText(
        text = text,
        style = face(
            size = size,
            scale = scale,
            weight = FontWeight.Medium,
            color = Color.White.copy(alpha = alpha),
        ),
    )
}

/**
 * One clock face.
 *
 * [scale] shrinks the whole face (type, rings, paddings) in one go; the picker
 * uses it to draw a live miniature of each face. A preview passes
 * `live = false` and a fixed [time], so nothing in it ticks.
 */
@Composable
internal fun LockClock(
    style: LockClockStyle,
    time: LocalDateTime,
    is24h: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    showDate: Boolean = true,
    live: Boolean = true,
) {
    val locale = Locale.getDefault()
    val hourText = remember(time, is24h) {
        time.format(DateTimeFormatter.ofPattern(if (is24h) "HH" else "h", locale))
    }
    val minuteText = remember(time) { time.format(DateTimeFormatter.ofPattern("mm", locale)) }
    val secondText = remember(time) { time.format(DateTimeFormatter.ofPattern("ss", locale)) }
    val hm = "$hourText:$minuteText"
    val date = remember(time.toLocalDate(), locale) {
        time.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    }
    val shortDate = remember(time.toLocalDate(), locale) {
        time.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
    }

    when (style) {
        LockClockStyle.CLASSIC -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            ClockText(hm, face(92f, scale, weight = FontWeight.SemiBold))
        }

        LockClockStyle.THIN -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            ClockText(hm, face(116f, scale, family = ThinFamily, spacing = -2f))
        }

        LockClockStyle.STACKED -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            val text = buildAnnotatedString {
                append(hourText.padStart(2, '0'))
                append("\n")
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.72f))) { append(minuteText) }
            }
            BasicText(
                text = text,
                style = face(
                    size = 122f,
                    scale = scale,
                    family = BlackFamily,
                    spacing = -2f,
                ).copy(lineHeight = scaledSp(104f, scale)),
                maxLines = 2,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }

        LockClockStyle.SERIF -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) {
                ClockText(
                    date,
                    face(
                        size = 21f,
                        scale = scale,
                        family = FontFamily.Serif,
                        italic = true,
                        color = Color.White.copy(alpha = 0.9f),
                    ),
                )
            }
            ClockText(hm, face(98f, scale, family = FontFamily.Serif, weight = FontWeight.Normal))
        }

        LockClockStyle.DIGITAL -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            ClockText(
                "$hm:$secondText",
                face(66f, scale, family = MonoFamily, weight = FontWeight.Medium, spacing = 2f),
            )
        }

        LockClockStyle.CONDENSED -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            ClockText(hm, face(156f, scale, family = CondensedFamily, spacing = -1f))
        }

        LockClockStyle.OUTLINE -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            val strokePx = with(LocalDensity.current) { (2.6f * scale).dp.toPx() }
            ClockText(
                hm,
                face(104f, scale, family = BoldFamily, shadow = false)
                    .copy(drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)),
            )
        }

        LockClockStyle.GRADIENT -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            ClockText(
                hm,
                face(100f, scale, family = BlackFamily, shadow = false).copy(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFFFD3A8), Color(0xFFFF9EC0)),
                    ),
                ),
            )
        }

        LockClockStyle.DUAL -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            val text = buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = BoldFamily)) { append(hourText) }
                withStyle(SpanStyle(fontFamily = ThinFamily, color = Color.White.copy(alpha = 0.7f))) {
                    append(":")
                    append(minuteText)
                }
            }
            BasicText(
                text = text,
                style = face(104f, scale),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }

        LockClockStyle.EDITORIAL -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = (32f * scale).dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (showDate) {
                BasicText(
                    text = date.uppercase(locale),
                    style = face(
                        size = 14f,
                        scale = scale,
                        weight = FontWeight.SemiBold,
                        spacing = 3f,
                        color = Color.White.copy(alpha = 0.82f),
                    ).copy(textAlign = TextAlign.Start),
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height((8f * scale).dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width((4f * scale).dp)
                        .height((78f * scale).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                )
                Spacer(Modifier.width((14f * scale).dp))
                ClockText(hm, face(88f, scale, family = LightFamily))
            }
        }

        LockClockStyle.SCRIPT -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            ClockText(hm, face(104f, scale, family = FontFamily.Cursive))
        }

        LockClockStyle.RING -> RingFace(modifier, time, hm, shortDate, scale, showDate, live)

        LockClockStyle.ANALOG -> AnalogFace(modifier, time, date, scale, showDate, live)

        // The see-through faces: a pane of glass, a ghost of a clock, a wireframe.
        LockClockStyle.GLASS -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale, alpha = 0.72f)
            GlassText(hm, size = 112f, scale = scale, family = BoldFamily, fill = 0.20f, edge = 0.88f, edgeWidth = 1.6f)
        }

        LockClockStyle.GHOST -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale, alpha = 0.60f)
            ClockText(
                hm,
                face(120f, scale, family = BlackFamily, color = Color.White.copy(alpha = 0.30f), shadow = false),
            )
        }

        LockClockStyle.WIRE -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale, alpha = 0.72f)
            GlassText(hm, size = 118f, scale = scale, family = BoldFamily, fill = 0f, edge = 0.92f, edgeWidth = 1.1f)
        }

        LockClockStyle.FROSTED -> {
            val panel = RoundedCornerShape((30f * scale).dp)
            Column(
                modifier = modifier
                    .clip(panel)
                    .background(Color.White.copy(alpha = 0.12f))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f)),
                        ),
                        shape = panel,
                    )
                    .padding(horizontal = (34f * scale).dp, vertical = (16f * scale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showDate) DateLine(shortDate, scale, alpha = 0.80f, size = 16f)
                ClockText(hm, face(72f, scale, family = LightFamily, shadow = false))
            }
        }

        LockClockStyle.NEON -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            ClockText(
                hm,
                face(100f, scale, family = BoldFamily, color = Color(0xFFE8FDFF), shadow = false)
                    .copy(shadow = NeonShadow),
            )
        }

        LockClockStyle.CARDS -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate) DateLine(date, scale)
            Row(
                horizontalArrangement = Arrangement.spacedBy((12f * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlipCard(hourText.padStart(2, '0'), scale)
                FlipCard(minuteText, scale)
            }
        }

        LockClockStyle.MINIMAL -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            ClockText(
                hm,
                face(52f, scale, family = ThinFamily, spacing = 6f, color = Color.White.copy(alpha = 0.92f)),
            )
            if (showDate) {
                BasicText(
                    text = date.uppercase(locale),
                    style = face(
                        size = 13f,
                        scale = scale,
                        weight = FontWeight.Medium,
                        spacing = 4f,
                        color = Color.White.copy(alpha = 0.72f),
                        shadow = false,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        LockClockStyle.COMPACT -> {
            val pill = RoundedCornerShape(50)
            Row(
                modifier = modifier
                    .clip(pill)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), pill)
                    .padding(horizontal = (20f * scale).dp, vertical = (9f * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((10f * scale).dp),
            ) {
                if (showDate) {
                    ClockText(
                        shortDate,
                        face(17f, scale, weight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f), shadow = false),
                    )
                    Box(
                        Modifier
                            .size((4f * scale).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.6f)),
                    )
                }
                ClockText(hm, face(26f, scale, weight = FontWeight.Bold, shadow = false))
            }
        }
    }
}

// --- smooth seconds ---------------------------------------------------------

/**
 * Wall-clock milliseconds, refreshed every frame while [live].
 *
 * Handed back as state and read only inside draw blocks, so a frame costs a
 * redraw of the dial and no recomposition at all.
 */
@Composable
private fun rememberSmoothMillis(live: Boolean): LongState {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        while (true) {
            withFrameMillis { state.longValue = System.currentTimeMillis() }
        }
    }
    return state
}

/** Seconds since local midnight at [epochMs], with the fraction. */
private fun secondsOfDay(epochMs: Long): Double {
    val local = epochMs + TimeZone.getDefault().getOffset(epochMs)
    return Math.floorMod(local, 86_400_000L) / 1000.0
}

// --- ring -------------------------------------------------------------------

@Composable
private fun RingFace(
    modifier: Modifier,
    time: LocalDateTime,
    hm: String,
    shortDate: String,
    scale: Float,
    showDate: Boolean,
    live: Boolean,
) {
    val smooth = rememberSmoothMillis(live)
    val fixedSeconds = time.second.toDouble()
    Box(modifier.size((232f * scale).dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((232f * scale).dp)) {
            val stroke = 7.dp.toPx() * scale
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val seconds = if (live) secondsOfDay(smooth.longValue) % 60.0 else fixedSeconds
            drawArc(
                color = Color.White.copy(alpha = 0.95f),
                startAngle = -90f,
                sweepAngle = (seconds / 60.0 * 360.0).toFloat().coerceAtLeast(1.5f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ClockText(hm, face(62f, scale, weight = FontWeight.Medium))
            if (showDate) {
                ClockText(
                    shortDate,
                    face(14f, scale, weight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f)),
                )
            }
        }
    }
}

// --- analog -----------------------------------------------------------------

@Composable
private fun AnalogFace(
    modifier: Modifier,
    time: LocalDateTime,
    date: String,
    scale: Float,
    showDate: Boolean,
    live: Boolean,
) {
    val smooth = rememberSmoothMillis(live)
    val fixedSeconds = time.toLocalTime().toSecondOfDay().toDouble()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size((250f * scale).dp)) {
            val u = 1.dp.toPx() * scale
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 2f * u
            val secs = if (live) secondsOfDay(smooth.longValue) else fixedSeconds

            // Face: a darkened disc under a hairline rim, so the dial reads on any cover.
            drawCircle(Color.Black.copy(alpha = 0.26f), radius = radius, center = center)
            drawCircle(
                Color.White.copy(alpha = 0.34f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f * u),
            )

            // Ticks: the hours long and bright, the minutes short and faint.
            for (i in 0 until 60) {
                val hour = i % 5 == 0
                val outer = radius - 6f * u
                val inner = outer - (if (hour) 13f else 5f) * u
                drawRadial(
                    center = center,
                    angleDeg = i * 6.0,
                    from = inner,
                    to = outer,
                    color = Color.White.copy(alpha = if (hour) 0.95f else 0.42f),
                    width = (if (hour) 2.6f else 1.1f) * u,
                )
            }

            val hourAngle = (secs / 3600.0 % 12.0) * 30.0
            val minuteAngle = (secs / 60.0 % 60.0) * 6.0
            val secondAngle = (secs % 60.0) * 6.0

            drawRadial(center, hourAngle, 0f, radius * 0.50f, Color.White, 6.5f * u)
            drawRadial(center, minuteAngle, 0f, radius * 0.76f, Color.White, 4.2f * u)
            val red = Color(0xFFFF453A)
            drawRadial(center, secondAngle, -radius * 0.16f, radius * 0.84f, red, 1.8f * u)
            drawCircle(red, radius = 4.6f * u, center = center)
            drawCircle(Color.Black.copy(alpha = 0.55f), radius = 1.6f * u, center = center)
        }
        if (showDate) {
            Spacer(Modifier.height((8f * scale).dp))
            DateLine(date, scale, size = 19f)
        }
    }
}

/** A line along the dial's radius, [from]..[to] away from the centre; 0° is twelve o'clock. */
private fun DrawScope.drawRadial(
    center: Offset,
    angleDeg: Double,
    from: Float,
    to: Float,
    color: Color,
    width: Float,
) {
    val rad = Math.toRadians(angleDeg)
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    drawLine(
        color = color,
        start = Offset(center.x + dx * from, center.y + dy * from),
        end = Offset(center.x + dx * to, center.y + dy * to),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

// --- see-through faces ------------------------------------------------------------

/** A glow rather than a drop shadow: the colour bleeds out from the digits. */
private val NeonShadow = Shadow(
    color = Color(0xFF59F0FF),
    offset = Offset.Zero,
    blurRadius = 38f,
)

/**
 * Digits as a pane of glass: a faint white fill ([fill], 0 for none) under a
 * hairline edge ([edge]). Nothing opaque, so the cover shows straight through.
 */
@Composable
private fun GlassText(
    text: String,
    size: Float,
    scale: Float,
    family: FontFamily,
    fill: Float,
    edge: Float,
    edgeWidth: Float,
) {
    val strokePx = with(LocalDensity.current) { (edgeWidth * scale).dp.toPx() }
    Box(contentAlignment = Alignment.Center) {
        if (fill > 0f) {
            ClockText(
                text,
                face(size, scale, family = family, color = Color.White.copy(alpha = fill), shadow = false),
            )
        }
        ClockText(
            text,
            face(size, scale, family = family, color = Color.White.copy(alpha = edge), shadow = false)
                .copy(drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)),
        )
    }
}

/** One half-dark card of a flip clock, with the hinge line across its middle. */
@Composable
private fun FlipCard(text: String, scale: Float) {
    val shape = RoundedCornerShape((20f * scale).dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = (18f * scale).dp, vertical = (8f * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        ClockText(text, face(92f, scale, family = BoldFamily, shadow = false))
        Box(
            Modifier
                .fillMaxWidth()
                .height((2f * scale).dp)
                .background(Color.Black.copy(alpha = 0.55f)),
        )
    }
}
