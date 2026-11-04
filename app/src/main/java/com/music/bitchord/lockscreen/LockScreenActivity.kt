package com.music.bitchord.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.R
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.playback.rememberPlayerState
import com.music.bitchord.ui.player.AndroidCanvasArtworkPlayer
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.theme.BitChordTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A full-screen "now playing" that sits on top of the lock screen, in the manner
 * of iOS 26's: the cover (or its animated Canvas) fills the screen, the clock
 * stays on top, and a card with the transport sits at the bottom.
 *
 * It is a real Activity shown over the keyguard, not the system's own lock-screen
 * card, which Android does not let an app replace. Swiping up asks the system to
 * unlock (PIN, pattern or biometric as usual); turning the screen off closes it.
 */
class LockScreenActivity : AppCompatActivity() {

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        // Screen off, or the phone unlocked: either way this has done its job.
        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            BitChordTheme(darkTheme = true) {
                LockScreenPlayer(onUnlock = { unlock() }, onClose = { finish() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Left behind by an unlock that went somewhere else: nothing to show.
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (!locked && !isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closeReceiver) }
        super.onDestroy()
    }

    /** Asks the system to unlock; it shows the PIN or biometric prompt if one is set. */
    private fun unlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            finish()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    finish()
                }
            },
        )
    }
}

/** How far up a swipe has to go before it unlocks. */
private val UnlockThreshold = 120.dp

@Composable
private fun LockScreenPlayer(onUnlock: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val controller = rememberMediaController()
    val player = rememberPlayerState(controller)
    val song = player.song
    val currentOnUnlock by rememberUpdatedState(onUnlock)
    val currentOnClose by rememberUpdatedState(onClose)

    // The wake-up gate only knew that *some* app was playing. If it is not this
    // one there is nothing here to show, so leave before it is noticed.
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (!c.playWhenReady) currentOnClose()
    }

    // The same switches the player's own Canvas follows.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val overCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    val spotifyOn by AppSettings.spotifyCanvasEnabled.collectAsStateWithLifecycle()
    val canvasAllowed = canvasEnabled && (metered != true || overCellular)
    var canvas by remember(song?.title, song?.artist) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(song?.videoId, song?.albumName, canvasAllowed, spotifyOn) {
        val s = song
        canvas = if (s == null || !canvasAllowed) {
            null
        } else {
            CanvasRepository.cached(s) ?: CanvasRepository.canvasFor(s)
        }
    }

    // Position is polled here rather than taken from the player state, which
    // only ticks while the app is in the foreground.
    var positionMs by remember { mutableStateOf(0L) }
    LaunchedEffect(controller, player.isPlaying, song?.videoId) {
        val c = controller ?: return@LaunchedEffect
        val playing = player.isPlaying
        while (true) {
            positionMs = c.currentPosition.coerceAtLeast(0L)
            if (!playing) break
            delay(500)
        }
    }

    // --- clock face ---------------------------------------------------------
    val styleName by AppSettings.lockScreenClockStyle.collectAsStateWithLifecycle()
    val clockStyle = LockClockStyle.from(styleName)
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val time = rememberClockTime(withSeconds = clockStyle.usesSeconds)
    var pickerOpen by remember { mutableStateOf(false) }
    val pickerOpenNow by rememberUpdatedState(pickerOpen)
    // The face's name, flashed under the clock for a moment after a tap.
    var hintTick by remember { mutableIntStateOf(0) }
    var hintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(hintTick) {
        if (hintTick == 0) return@LaunchedEffect
        hintVisible = true
        delay(1_500)
        hintVisible = false
    }
    BackHandler(enabled = pickerOpen) { pickerOpen = false }
    val hintAlpha by animateFloatAsState(
        targetValue = if (hintVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (hintVisible) 160 else 260),
        label = "lockHint",
    )

    // --- what is on screen ----------------------------------------------------
    val showClockSetting by AppSettings.lockScreenShowClock.collectAsStateWithLifecycle()
    val showPlayerSetting by AppSettings.lockScreenShowPlayer.collectAsStateWithLifecycle()
    val fxName by AppSettings.lockScreenTrackFx.collectAsStateWithLifecycle()
    val fullCoverSetting by AppSettings.lockScreenFullCover.collectAsStateWithLifecycle()
    val trackFx = TrackFx.from(fxName)
    // A tap on the empty middle hides everything but the art; another brings it back.
    var bare by remember { mutableStateOf(false) }
    val clockShown = showClockSetting && !bare
    val playerShown = showPlayerSetting && !bare

    // The cover and the Canvas turn in when the track changes: 0 = just changed,
    // 1 = settled. Picking an effect in the sheet replays it as a preview.
    val fxProgress = remember { Animatable(1f) }
    var lastTrack by remember { mutableStateOf<Any?>(null) }
    var fxPreview by remember { mutableIntStateOf(0) }
    LaunchedEffect(song?.videoId) {
        val id: Any? = song?.videoId
        val before = lastTrack
        lastTrack = id
        if (before != null && id != null && before != id && trackFx != TrackFx.NONE) {
            fxProgress.snapTo(0f)
            fxProgress.animateTo(1f, tween(durationMillis = 560, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(fxPreview) {
        if (fxPreview > 0) {
            fxProgress.snapTo(0f)
            fxProgress.animateTo(1f, tween(durationMillis = 560, easing = FastOutSlowInEasing))
        }
    }

    // --- backdrop -----------------------------------------------------------
    // A cover that fills the whole screen is magnified, so it asks for more pixels.
    val artworkUrl = song?.artworkAt(if (fullCoverSetting) 1600 else 1080)
    var coverAspect by remember(artworkUrl) { mutableStateOf<Float?>(null) }
    val canvasClip = canvas
    // A tall cover (or a Canvas clip) is made for a phone screen and can fill it.
    // A square sleeve stretched to fit one is magnified two or three times over,
    // which is what made the old backdrop look coarse: that one is shown as a
    // sharp cover over a soft, blurred copy of itself instead.
    val aspect = coverAspect
    val tallCover = aspect != null && aspect < 0.8f
    // With "full-screen cover" on, a track that has no Canvas clip gets its cover
    // across the whole screen, square or not (a square one is cropped to fit).
    val fullBleed = tallCover || (fullCoverSetting && canvasClip == null)
    val showCoverCard = canvasClip == null && artworkUrl != null && aspect != null && !fullBleed

    // --- entrance and unlock drag -------------------------------------------
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "lockEnter",
    )
    var dragPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val dragSpec: FiniteAnimationSpec<Float> = if (dragging) {
        snap()
    } else {
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    val dragShown by animateFloatAsState(
        targetValue = if (dragging) dragPx else 0f,
        animationSpec = dragSpec,
        label = "unlockDrag",
    )

    // A cover across the whole screen needs less darkening at the bottom: the
    // glass card carries its own tint, so the picture can show through.
    val scrim = remember(fullBleed) { scrimBrush(bottomAlpha = if (fullBleed) 0.52f else 0.84f) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val glass = GlassSource(url = artworkUrl, width = rootSize.width, height = rootSize.height)
    val grain = rememberGrainBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .background(Color.Black)
            .pointerInput(Unit) {
                val threshold = UnlockThreshold.toPx()
                detectVerticalDragGestures(
                    onDragStart = {
                        dragPx = 0f
                        dragging = true
                    },
                    onDragEnd = {
                        val pulled = dragPx
                        dragging = false
                        if (pulled < -threshold && !pickerOpenNow) currentOnUnlock()
                    },
                    onDragCancel = { dragging = false },
                ) { _, dragAmount ->
                    dragPx = (dragPx + dragAmount).coerceIn(-threshold * 2.5f, 0f)
                }
            },
    ) {
        // The backdrop and the Canvas share one layer, so a track change turns them together.
        Box(Modifier.fillMaxSize().graphicsLayer { applyTrackFx(trackFx, fxProgress.value) }) {
            LockBackdrop(
                url = artworkUrl,
                fullBleed = fullBleed,
                onAspect = { coverAspect = it },
            )
            if (canvasClip != null) {
                AndroidCanvasArtworkPlayer(
                    canvas = canvasClip,
                    isPlaying = player.isPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Keeps the clock and the card legible over any cover. Eased in many
        // small steps rather than two, which is what leaves visible bands.
        Box(Modifier.fillMaxSize().background(scrim))
        // A whisper of grain over the lot: it hides the banding in the scrim
        // and the softness of a low-resolution clip.
        Box(Modifier.fillMaxSize().drawBehind { drawRect(grain, alpha = 0.55f) })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer {
                    translationY = dragShown * 0.55f
                    alpha = (1f + dragShown / (UnlockThreshold.toPx() * 3f)).coerceIn(0.35f, 1f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Tap: the next face. Hold: all of them.
            AnimatedVisibility(
                visible = clockShown,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .pointerInput(clockStyle) {
                                detectTapGestures(
                                    onTap = {
                                        AppSettings.setLockScreenClockStyle(clockStyle.next().name)
                                        hintTick++
                                    },
                                    onLongPress = { pickerOpen = true },
                                )
                            },
                    ) {
                        LockClock(style = clockStyle, time = time, is24h = is24h)
                    }
                    Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                        if (hintAlpha > 0.01f) {
                            Box(Modifier.graphicsLayer { alpha = hintAlpha }) {
                                HintPill(text = stringResource(clockStyle.labelRes))
                            }
                        }
                    }
                }
            }

            // The middle of the screen: a sharp cover where the backdrop is soft. A tap
            // here hides or brings back the clock and the player; a long press opens the
            // sheet, which is the way back when either one has been switched off.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { bare = !bare },
                            onLongPress = { pickerOpen = true },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val side = maxOf(0.dp, minOf(maxWidth - 56.dp, maxHeight - 12.dp))
                // Eases to its new size as the clock above and the card below come and go.
                val shownSide by animateDpAsState(
                    targetValue = side,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    label = "coverSide",
                )
                this@Column.AnimatedVisibility(
                    visible = showCoverCard && side >= 150.dp,
                    enter = fadeIn(tween(420)),
                    exit = fadeOut(tween(260)),
                ) {
                    Box(Modifier.graphicsLayer { applyTrackFx(trackFx, fxProgress.value) }) {
                        CoverCard(url = artworkUrl, side = shownSide, isPlaying = player.isPlaying)
                    }
                }
            }

            AnimatedVisibility(
                visible = playerShown,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
            ) {
                Column(
                    modifier = Modifier.graphicsLayer {
                        alpha = enter
                        translationY = (1f - enter) * 40.dp.toPx()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (song != null && controller != null) {
                        TransportCard(
                            glass = glass,
                            title = song.title,
                            artist = song.artist,
                            isPlaying = player.isPlaying,
                            positionMs = positionMs,
                            durationMs = player.durationMs,
                            onPrevious = { controller.seekToPrevious() },
                            onPlayPause = { if (controller.isPlaying) controller.pause() else controller.play() },
                            onNext = { controller.seekToNextMediaItem() },
                            onSeekFraction = { fraction ->
                                // Read now, not from the snapshot: a track change updates
                                // the item a beat before it updates the duration.
                                val duration = controller.duration
                                if (duration > 0L) {
                                    val target = (fraction * duration).toLong()
                                    // Shown at once, so the bar does not snap back to the
                                    // old spot until the next poll catches up.
                                    positionMs = target
                                    controller.seekTo(target)
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    GlassSurface(
                        glass = glass,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
                    ) {
                        VolumeBar()
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = pickerOpen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
        ) {
            ClockStylePicker(
                selected = clockStyle,
                is24h = is24h,
                onSelect = { AppSettings.setLockScreenClockStyle(it.name) },
                showClock = showClockSetting,
                onShowClock = { AppSettings.setLockScreenShowClock(it) },
                showPlayer = showPlayerSetting,
                onShowPlayer = { AppSettings.setLockScreenShowPlayer(it) },
                fullCover = fullCoverSetting,
                onFullCover = { AppSettings.setLockScreenFullCover(it) },
                fx = trackFx,
                onFx = {
                    AppSettings.setLockScreenTrackFx(it.name)
                    fxPreview++
                },
                onDismiss = { pickerOpen = false },
            )
        }
    }
}

// --- track change ------------------------------------------------------------

/** What the cover and the Canvas do when the track changes; stored by [name]. */
private enum class TrackFx(@StringRes val labelRes: Int) {
    NONE(R.string.lock_fx_none),
    FLIP(R.string.lock_fx_flip),
    FLIP_V(R.string.lock_fx_flip_v),
    SLIDE(R.string.lock_fx_slide),
    ZOOM(R.string.lock_fx_zoom),
    ;

    companion object {
        fun from(name: String?): TrackFx = TrackFx.entries.firstOrNull { it.name == name } ?: TrackFx.FLIP
    }
}

/**
 * Poses a layer for [fx] at [p]: 0 is the instant the track changed, 1 is at
 * rest. Read inside a graphicsLayer block, so a frame of it costs a redraw and
 * no recomposition.
 */
private fun GraphicsLayerScope.applyTrackFx(fx: TrackFx, p: Float) {
    if (fx == TrackFx.NONE || p >= 1f) return
    val rest = 1f - p
    when (fx) {
        TrackFx.FLIP -> {
            cameraDistance = 14f * density
            rotationY = -90f * rest
            alpha = smoothstep(p * 2.2f)
        }
        TrackFx.FLIP_V -> {
            cameraDistance = 14f * density
            rotationX = 90f * rest
            alpha = smoothstep(p * 2.2f)
        }
        TrackFx.SLIDE -> {
            translationX = size.width * 0.28f * rest
            alpha = smoothstep(p * 1.6f)
        }
        TrackFx.ZOOM -> {
            val grown = 0.84f + 0.16f * p
            scaleX = grown
            scaleY = grown
            alpha = smoothstep(p * 1.8f)
        }
        TrackFx.NONE -> Unit
    }
}

// --- backdrop ----------------------------------------------------------------

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** The legibility gradient, eased: dark for the clock, clear in the middle, dark for the card. */
private fun scrimBrush(bottomAlpha: Float = 0.84f): Brush {
    val steps = 8
    val stops = ArrayList<Pair<Float, Color>>()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        stops += (t * 0.36f) to Color.Black.copy(alpha = 0.50f * (1f - smoothstep(t)))
    }
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        stops += (0.52f + t * 0.48f) to Color.Black.copy(alpha = bottomAlpha * smoothstep(t))
    }
    return Brush.verticalGradient(*stops.toTypedArray())
}

/** A small tile of faint black and white speckle, repeated across the screen. */
@Composable
private fun rememberGrainBrush(): Brush = remember {
    val side = 96
    val rnd = java.util.Random(0x5EED)
    val pixels = IntArray(side * side) {
        val v = if (rnd.nextBoolean()) 255 else 0
        val a = rnd.nextInt(34)
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * The cover behind everything: always a soft, blurred, slightly saturated copy
 * that fills the screen; and, for artwork that is already phone-shaped, the
 * sharp full-bleed picture over it.
 *
 * The soft copy is also what reports the artwork's proportions. It is decoded
 * small — it is about to be blurred anyway — and Coil keeps the aspect.
 */
@Composable
private fun LockBackdrop(url: String?, fullBleed: Boolean, onAspect: (Float) -> Unit) {
    val context = LocalContext.current
    val saturate = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.25f) }) }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = remember(url) {
                ImageRequest.Builder(context).data(url).size(360, 360).crossfade(600).build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = saturate,
            onSuccess = { state ->
                val image = state.result.image
                if (image.height > 0) onAspect(image.width.toFloat() / image.height.toFloat())
            },
            modifier = Modifier.fillMaxSize().blur(36.dp),
        )
        AnimatedVisibility(
            visible = fullBleed,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(300)),
        ) {
            AsyncImage(
                model = remember(url) {
                    ImageRequest.Builder(context).data(url).crossfade(400).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The cover at its own resolution, easing smaller while the music is paused. */
@Composable
private fun CoverCard(url: String?, side: Dp, isPlaying: Boolean) {
    val context = LocalContext.current
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
        label = "coverScale",
    )
    val shape = RoundedCornerShape(26.dp)
    AsyncImage(
        model = remember(url) {
            ImageRequest.Builder(context).data(url).size(1080, 1080).crossfade(400).build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(side)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = 26.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    )
}

// --- clock chrome -------------------------------------------------------------

@Composable
private fun HintPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(text = text, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Every face in a row, each drawn live in miniature; the clock above stays in view. */
@Composable
private fun ClockStylePicker(
    selected: LockClockStyle,
    is24h: Boolean,
    onSelect: (LockClockStyle) -> Unit,
    showClock: Boolean,
    onShowClock: (Boolean) -> Unit,
    showPlayer: Boolean,
    onShowPlayer: (Boolean) -> Unit,
    fullCover: Boolean,
    onFullCover: (Boolean) -> Unit,
    fx: TrackFx,
    onFx: (TrackFx) -> Unit,
    onDismiss: () -> Unit,
) {
    val faces = remember { LockClockStyle.entries.toList() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selected.ordinal - 1).coerceAtLeast(0),
    )
    val previewTime = remember { LocalDateTime.of(2000, 1, 1, 10, 9, 36) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.25f),
                    1f to Color.Black.copy(alpha = 0.86f),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.lock_clock_picker_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = stringResource(R.string.lock_clock_picker_hint),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 14.dp),
            )
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(faces) { face ->
                    StyleTile(
                        style = face,
                        selected = face == selected,
                        time = previewTime,
                        is24h = is24h,
                        onClick = { onSelect(face) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SheetSwitch(stringResource(R.string.lock_show_clock), showClock, onShowClock)
            SheetSwitch(stringResource(R.string.lock_show_player), showPlayer, onShowPlayer)
            SheetSwitch(stringResource(R.string.lock_full_cover), fullCover, onFullCover)
            Text(
                text = stringResource(R.string.lock_fx_title),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TrackFx.entries.toList()) { option ->
                    FxChip(fx = option, selected = option == fx, onClick = { onFx(option) })
                }
            }
        }
    }
}

@Composable
private fun StyleTile(
    style: LockClockStyle,
    selected: Boolean,
    time: LocalDateTime,
    is24h: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 92.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = if (selected) 0.18f else 0.10f))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            LockClock(
                style = style,
                time = time,
                is24h = is24h,
                scale = style.previewScale,
                showDate = false,
                live = false,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(style.labelRes),
            color = Color.White.copy(alpha = if (selected) 1f else 0.7f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SheetSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onChange(!checked) },
            )
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FxChip(fx: TrackFx, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.10f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = stringResource(fx.labelRes),
            color = Color.White.copy(alpha = if (selected) 1f else 0.72f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// --- transport -----------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
// --- liquid glass ---------------------------------------------------------------

/** What the glass looks through: the cover, and the size of the screen behind it. */
@Immutable
private data class GlassSource(val url: String?, val width: Int, val height: Int)

/** Light falling across the pane from the top-left, easing off, and a faint return at the far corner. */
private val GlassSheen = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.26f),
    0.40f to Color.White.copy(alpha = 0.05f),
    0.75f to Color.White.copy(alpha = 0.02f),
    1f to Color.White.copy(alpha = 0.14f),
)

/** The edge: bright where the light hits it, nearly gone along the sides, bright again opposite. */
private val GlassRim = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.80f),
    0.30f to Color.White.copy(alpha = 0.16f),
    0.70f to Color.White.copy(alpha = 0.08f),
    1f to Color.White.copy(alpha = 0.50f),
)

private val GlassTopGlint = Brush.horizontalGradient(
    listOf(Color.Transparent, Color.White.copy(alpha = 0.65f), Color.Transparent),
)

/**
 * A pane of frosted glass. Behind it sits the same cover as the screen, blurred,
 * pushed to richer colour and lined up pixel for pixel with the sharp picture
 * around it, so the pane reads as a lens over the cover rather than a grey
 * rectangle laid on top. A tint keeps the white text legible over a pale
 * cover; a sheen and a lit rim give it thickness.
 */
@Composable
private fun GlassSurface(
    glass: GlassSource,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(shape),
    ) {
        if (glass.width > 0 && glass.height > 0) {
            GlassBackdropCopy(glass = glass, origin = origin, modifier = Modifier.matchParentSize())
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .background(GlassSheen),
        )
        Column(modifier = Modifier.padding(contentPadding), content = content)
        Box(Modifier.matchParentSize().border(width = 1.2.dp, brush = GlassRim, shape = shape))
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
                .fillMaxWidth(0.6f)
                .height(1.dp)
                .background(GlassTopGlint),
        )
    }
}

/**
 * The cover, as large as the screen, moved so that the part under the pane
 * lands exactly where the same part of the real backdrop is. Decoded small and
 * blurred: it only ever shows as colour and light.
 */
@Composable
private fun GlassBackdropCopy(glass: GlassSource, origin: Offset, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vivid = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) }) }
    val shiftX = origin.x.roundToInt()
    val shiftY = origin.y.roundToInt()
    Box(
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints.fixed(glass.width, glass.height))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(-shiftX, -shiftY)
            }
        },
    ) {
        AsyncImage(
            model = remember(glass.url) {
                ImageRequest.Builder(context).data(glass.url).size(128, 128).crossfade(500).build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = vivid,
            modifier = Modifier.fillMaxSize().blur(20.dp),
        )
    }
}

@Composable
private fun TransportCard(
    glass: GlassSource,
    title: String,
    artist: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeekFraction: (Float) -> Unit,
) {
    val shape = RoundedCornerShape(34.dp)
    // The fraction under the finger while scrubbing; null the rest of the time.
    var scrub by remember { mutableStateOf<Float?>(null) }
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val scrubbing = scrub
    val shownPosition = if (scrubbing != null && durationMs > 0L) (scrubbing * durationMs).toLong() else positionMs
    val digits = TextStyle(fontFeatureSettings = "tnum")

    GlassSurface(
        glass = glass,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 14.dp),
    ) {
        // A long title drifts sideways instead of ending in an ellipsis.
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(),
        )
        Text(
            text = artist,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        ThinSlider(
            value = scrubbing ?: progress,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                val target = scrub
                scrub = null
                if (target != null) onSeekFraction(target)
            },
            idleHeight = 6.dp,
            activeHeight = 11.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(shownPosition),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 12.sp,
                style = digits,
            )
            Text(
                text = "-" + formatTime((durationMs - shownPosition).coerceAtLeast(0L)),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 12.sp,
                style = digits,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(onClick = onPrevious, touch = 68.dp) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            ControlButton(onClick = onPlayPause, touch = 80.dp) {
                Crossfade(targetState = isPlaying, animationSpec = tween(180), label = "playPause") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(58.dp),
                    )
                }
            }
            ControlButton(onClick = onNext, touch = 68.dp) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }
    }
}

/** A transport button with no ripple: it dips under the finger and springs back. */
@Composable
private fun ControlButton(onClick: () -> Unit, touch: Dp, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "controlPress",
    )
    Box(
        modifier = Modifier
            .size(touch)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * The phone's media volume, between a quiet and a loud speaker, on the same bar
 * the player's own volume uses. It follows the hardware keys and changes
 * of output, and never fights the finger that is moving it.
 */
@Composable
private fun VolumeBar(modifier: Modifier = Modifier) {
    val volume = remember { PlayerPlatform.host.volume }
    val systemLevel by volume.level.collectAsStateWithLifecycle()
    var level by remember { mutableFloatStateOf(systemLevel) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { volume.refresh() }
    LaunchedEffect(systemLevel) {
        if (!dragging) level = systemLevel
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        ThinSlider(
            value = level,
            onValueChange = {
                dragging = true
                level = it
                volume.set(it)
            },
            onValueChangeFinished = {
                dragging = false
                volume.refresh()
            },
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
