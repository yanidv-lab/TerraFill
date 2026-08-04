package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp as lerpOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.engine.*
import com.example.ui.GameUiState
import com.example.ui.skins.CaterpillarSkin
import com.example.ui.skins.SkinEffect
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Fallback spider drawn when a sprite asset fails to decode (body, legs, eyes). */
private fun DrawScope.drawFallbackSpider(center: Offset, radius: Float, color: Color) {
    for (side in intArrayOf(-1, 1)) {
        for (k in 0 until 4) {
            val a = -0.7f + k * 0.45f
            drawLine(
                color = color,
                start = center,
                end = center + Offset(side * cos(a) * radius * 1.9f, sin(a) * radius * 1.9f),
                strokeWidth = radius * 0.16f,
                cap = StrokeCap.Round
            )
        }
    }
    drawCircle(color, radius, center)
    for (side in intArrayOf(-1, 1)) {
        val eye = center + Offset(side * radius * 0.32f, -radius * 0.25f)
        drawCircle(Color.White, radius * 0.26f, eye)
        drawCircle(Color.Black, radius * 0.12f, eye)
    }
}

/** Fallback caterpillar drawn when the player sprite fails to decode. */
private fun DrawScope.drawFallbackCaterpillar(center: Offset, longSide: Float) {
    val r = longSide * 0.18f
    for (k in 3 downTo 1) {
        drawCircle(NeonGreen, r * (1f - k * 0.08f), center + Offset(k * r * 1.4f, 0f))
    }
    drawCircle(NeonGreen, r * 1.15f, center) // head
    val eye = center + Offset(-r * 0.35f, -r * 0.3f)
    drawCircle(Color.White, r * 0.4f, eye)
    drawCircle(Color.Black, r * 0.2f, eye)
}

/** How many grid cells long the player caterpillar sprite is drawn (visual only; hitbox stays small). */
private const val PLAYER_SPRITE_CELLS = 3.3f

/** How many grid cells wide the enemy spider sprite is drawn. */
private const val ENEMY_SPRITE_CELLS = 3.1f

/**
 * Draws an [image] centered at [center], scaled so its longer side spans [targetLongSide]
 * pixels (aspect preserved), optionally rotated and/or horizontally mirrored, with
 * high-quality filtering so the art stays crisp.
 */
private fun DrawScope.drawSprite(
    image: ImageBitmap,
    center: Offset,
    targetLongSide: Float,
    rotationDeg: Float,
    flipX: Boolean,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    alpha: Float = 1f
) {
    val aspect = image.width.toFloat() / image.height.toFloat()
    val dw: Float
    val dh: Float
    if (aspect >= 1f) {
        dw = targetLongSide; dh = targetLongSide / aspect
    } else {
        dw = targetLongSide * aspect; dh = targetLongSide
    }
    withTransform({
        rotate(rotationDeg, center)
        // Squash & stretch in the sprite's own axes (applied before rotation), so a
        // walk/crawl cycle deforms along the creature's body regardless of heading.
        scale(if (flipX) -scaleX else scaleX, scaleY, center)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(
                (center.x - dw / 2f).roundToInt(),
                (center.y - dh / 2f).roundToInt()
            ),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            filterQuality = FilterQuality.High,
            colorFilter = colorFilter,
            alpha = alpha
        )
    }
}

/**
 * Draws a premium skin's flourish over the hero sprite, inside the sprite's own
 * rotated/mirrored space so accessories ride along with the body. [longSide] is the
 * rendered sprite length; the artwork's head sits toward its leading edge.
 */
private fun DrawScope.drawSkinEffect(
    effect: SkinEffect,
    accent: Color,
    center: Offset,
    longSide: Float,
    rotationDeg: Float,
    flipX: Boolean,
    now: Long
) {
    if (effect == SkinEffect.NONE) return
    val bodyH = longSide / 2f          // artwork is roughly 2:1

    when (effect) {
        SkinEffect.GLOW -> {
            // Halo sits in world space (no rotation needed) and breathes.
            val pulse = 0.75f + 0.25f * sin(now / 260.0).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.45f * pulse), Color.Transparent),
                    center = center,
                    radius = longSide * 0.75f
                ),
                radius = longSide * 0.75f,
                center = center
            )
        }
        SkinEffect.SPARKLE -> {
            // Four-point twinkles orbiting the body at different phases.
            for (k in 0 until 3) {
                val phase = now / 900.0 + k * 2.1
                val life = ((phase % 1.0)).toFloat()
                val ang = (k * 2.4 + phase * 0.7)
                val dist = longSide * (0.22f + 0.20f * k)
                val p = center + Offset(cos(ang).toFloat() * dist, sin(ang).toFloat() * dist * 0.5f)
                val a = (1f - life) * 0.9f
                val r = longSide * 0.055f * (0.5f + life)
                drawLine(Color.White.copy(alpha = a), p - Offset(r, 0f), p + Offset(r, 0f), longSide * 0.012f, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = a), p - Offset(0f, r), p + Offset(0f, r), longSide * 0.012f, cap = StrokeCap.Round)
            }
        }
        SkinEffect.EMBER -> {
            // Embers drifting upward off the body.
            for (k in 0 until 4) {
                val phase = ((now / 700.0) + k * 0.27) % 1.0
                val rise = phase.toFloat()
                val sway = sin(now / 300.0 + k * 1.9).toFloat() * longSide * 0.06f
                val p = center + Offset(
                    (k - 1.5f) * longSide * 0.14f + sway,
                    -rise * longSide * 0.55f
                )
                drawCircle(
                    color = lerpColor(accent, Color(0xFFFFF176), rise),
                    radius = longSide * 0.035f * (1f - rise * 0.6f),
                    center = p,
                    alpha = (1f - rise) * 0.9f
                )
            }
        }
        else -> {
            // Accessories that must follow the body's orientation.
            withTransform({
                rotate(rotationDeg, center)
                scale(if (flipX) -1f else 1f, 1f, center)
            }) {
                when (effect) {
                    SkinEffect.SUNGLASSES -> {
                        // Art faces LEFT: the head is toward the negative-x end.
                        val headX = center.x - longSide * 0.30f
                        val eyeY = center.y - bodyH * 0.18f
                        val lensR = longSide * 0.058f
                        val gap = lensR * 1.85f
                        val left = Offset(headX - gap * 0.5f, eyeY)
                        val right = Offset(headX + gap * 0.5f, eyeY)
                        // Bridge + arm
                        drawLine(Color(0xFF15151A), left, right, lensR * 0.42f, cap = StrokeCap.Round)
                        drawLine(
                            Color(0xFF15151A), right,
                            Offset(right.x + lensR * 1.5f, eyeY - lensR * 0.25f),
                            lensR * 0.3f, cap = StrokeCap.Round
                        )
                        for (lens in listOf(left, right)) {
                            drawCircle(Color(0xFF101014), lensR, lens)
                            drawCircle(Color(0xFF3A3A45), lensR, lens, style = Stroke(width = lensR * 0.22f))
                            // Glint
                            drawCircle(
                                Color.White.copy(alpha = 0.75f),
                                lensR * 0.26f,
                                lens + Offset(-lensR * 0.32f, -lensR * 0.34f)
                            )
                        }
                    }
                    SkinEffect.GLOSS -> {
                        // A specular streak sweeping along the body.
                        val sweep = ((now / 1400.0) % 1.0).toFloat()
                        val x = center.x - longSide * 0.42f + sweep * longSide * 0.84f
                        val h = bodyH * 0.34f
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                startY = center.y - h,
                                endY = center.y + h
                            ),
                            start = Offset(x, center.y - h),
                            end = Offset(x, center.y + h),
                            strokeWidth = longSide * 0.05f,
                            cap = StrokeCap.Round
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

/**
 * The same skin flourish as [drawSkinEffect], woven along the trail's own arc length
 * (via [PathMeasure]) rather than relative to the body - so a premium skin's identity
 * shows up in the territory being claimed, not only on the caterpillar itself.
 */
private fun DrawScope.drawTrailEffect(
    effect: SkinEffect,
    accent: Color,
    path: Path,
    cellMin: Float,
    now: Long
) {
    if (effect == SkinEffect.NONE) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    if (length < cellMin * 0.5f) return // too short a trail for a path-length effect to read

    when (effect) {
        SkinEffect.GLOW -> {
            // A wider, breathing halo riding the whole trail beneath the core line.
            val pulse = 0.7f + 0.3f * sin(now / 260.0).toFloat()
            drawPath(
                path, accent,
                alpha = 0.35f * pulse,
                style = Stroke(width = cellMin * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        SkinEffect.SPARKLE -> {
            // Twinkles fixed at points along the trail, each on its own clock so they
            // read as catching the light rather than blinking in unison.
            val spacing = cellMin * 1.6f
            var d = 0f
            var i = 0
            while (d < length) {
                val p = measure.getPosition(d)
                val life = ((sin(now / 900.0 + i * 1.7) + 1.0) / 2.0).toFloat()
                val r = cellMin * 0.09f * (0.4f + life)
                drawLine(Color.White.copy(alpha = life * 0.85f), p - Offset(r, 0f), p + Offset(r, 0f), cellMin * 0.045f, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = life * 0.85f), p - Offset(0f, r), p + Offset(0f, r), cellMin * 0.045f, cap = StrokeCap.Round)
                d += spacing
                i++
            }
        }
        SkinEffect.EMBER -> {
            // Embers lift off several points along the trail, not just the body, so a
            // long claimed line still feels like it is smouldering.
            val spacing = cellMin * 2.4f
            var d = 0f
            var i = 0
            while (d < length) {
                val base = measure.getPosition(d)
                val rise = ((now / 750.0 + i * 0.31) % 1.0).toFloat()
                val sway = sin(now / 320.0 + i * 1.3).toFloat() * cellMin * 0.12f
                val p = base + Offset(sway, -rise * cellMin * 1.1f)
                drawCircle(
                    color = lerpColor(accent, Color(0xFFFFF176), rise),
                    radius = cellMin * 0.08f * (1f - rise * 0.6f),
                    center = p,
                    alpha = (1f - rise) * 0.85f
                )
                d += spacing
                i++
            }
        }
        SkinEffect.SUNGLASSES -> {
            // Sun-glint beads skimming along the trail together - a beach-holiday
            // shimmer rather than a single sweeping shine.
            val beadCount = 4
            val travel = ((now / 1100.0) % 1.0).toFloat()
            for (k in 0 until beadCount) {
                val d = ((travel + k / beadCount.toFloat()) % 1f) * length
                val p = measure.getPosition(d)
                drawCircle(accent.copy(alpha = 0.5f), cellMin * 0.2f, p)
                drawCircle(Color.White.copy(alpha = 0.8f), cellMin * 0.11f, p)
            }
        }
        SkinEffect.GLOSS -> {
            // A bright highlight gliding the length of the trail, mirroring the sweep
            // drawn over the body.
            val sweep = ((now / 1600.0) % 1.0).toFloat()
            val windowLen = length * 0.18f
            val windowStart = sweep * (length + windowLen) - windowLen
            val samples = 10
            for (s in 0 until samples) {
                val d = windowStart + windowLen * s / (samples - 1f)
                if (d < 0f || d > length) continue
                val p = measure.getPosition(d)
                val t = s / (samples - 1f)
                val edge = 1f - kotlin.math.abs(t - 0.5f) * 2f
                drawCircle(Color.White.copy(alpha = edge * 0.75f), cellMin * 0.22f * edge, p)
            }
        }
        else -> Unit
    }
}

/**
 * Soft elliptical ground shadow that anchors a creature to the field. [lift] (0..1+)
 * raises the creature off the ground: the shadow shrinks and fades with height,
 * which is what sells the jumper's leap.
 */
private fun DrawScope.drawGroundShadow(center: Offset, width: Float, lift: Float = 0f) {
    val squeeze = (1f - lift * 0.45f).coerceAtLeast(0.35f)
    val w = width * squeeze
    val h = w * 0.3f
    drawOval(
        color = Color.Black,
        topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
        size = Size(w, h),
        alpha = 0.30f * squeeze
    )
}

/** A short-lived visual particle. Position/velocity are in grid-cell units. */
private class GameParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val size: Float,
    val gravity: Float
)

/** Draws a collectible power-up badge (shield / freeze / slow) at [center]. */
private fun DrawScope.drawPowerUp(center: Offset, cellMin: Float, type: String, now: Long) {
    val pulse = 0.85f + 0.15f * sin(now / 180.0 + center.x.toDouble()).toFloat()
    val r = cellMin * 0.7f * pulse
    val color = when (type) {
        "SHIELD" -> NeonCyan
        "FREEZE" -> Color(0xFF9AD8FF)
        else -> NeonYellow // SLOW
    }
    // Glow + badge
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
            center = center, radius = r * 2.2f
        ),
        radius = r * 2.2f, center = center
    )
    drawCircle(color.copy(alpha = 0.25f), r, center)
    drawCircle(color, r, center, style = Stroke(width = cellMin * 0.14f))

    // Simple white glyph per type
    val g = r * 0.55f
    when (type) {
        "SHIELD" -> {
            // shield outline
            drawCircle(Color.White, g * 0.75f, center, style = Stroke(width = cellMin * 0.1f))
        }
        "FREEZE" -> {
            // snowflake: three crossed lines
            for (k in 0 until 3) {
                val a = (k * Math.PI / 3).toFloat()
                drawLine(
                    Color.White,
                    center + Offset(cos(a) * g, sin(a) * g),
                    center - Offset(cos(a) * g, sin(a) * g),
                    strokeWidth = cellMin * 0.09f, cap = StrokeCap.Round
                )
            }
        }
        else -> {
            // clock: circle + two hands
            drawCircle(Color.White, g, center, style = Stroke(width = cellMin * 0.08f))
            drawLine(Color.White, center, center + Offset(0f, -g * 0.8f), strokeWidth = cellMin * 0.08f, cap = StrokeCap.Round)
            drawLine(Color.White, center, center + Offset(g * 0.6f, 0f), strokeWidth = cellMin * 0.08f, cap = StrokeCap.Round)
        }
    }
}


/**
 * Interactive Playfield Screen for TerraFill.
 * Renders the custom grid Canvas, tactical precision D-pad, swipe controls, and detailed M3 HUD overlays.
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onDirectionChanged: (Direction) -> Unit,
    onTick: (Double) -> Unit,
    onPauseToggle: () -> Unit,
    onQuitGame: () -> Unit,
    onToggleSound: () -> Unit,
    onFieldSized: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Level-intro banner: LEVEL N + target, with a 3-2-1-GO countdown. Counts
    // 3..1 (sim held), then 0 shows "GO!" while play begins, then -1 hides it.
    // Levels that introduce a new spider hold each count longer so the player
    // has time to read the reveal card.
    val newEnemy = remember(state.levelNumber) { LevelConfig.newEnemyAt(state.levelNumber) }
    var introCount by remember { mutableStateOf(3) }
    LaunchedEffect(state.levelNumber) {
        introCount = 3
        val stepMs = if (newEnemy != null) 1300L else 700L
        while (introCount > 0) {
            kotlinx.coroutines.delay(stepMs)
            introCount--
        }
        kotlinx.coroutines.delay(600)
        introCount = -1
    }
    val introHoldsSim = introCount > 0

    // 1. Frame-synced Game Loop driving the GameEngine simulation.
    // Held during the intro countdown so the clock and spiders wait for the player.
    LaunchedEffect(state.status, introHoldsSim) {
        if (state.status == GameStateStatus.RUNNING && !introHoldsSim) {
            var lastTimeNanos = System.nanoTime()
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    val elapsedSeconds = (frameTimeNanos - lastTimeNanos) / 1_000_000_000.0
                    lastTimeNanos = frameTimeNanos
                    // Clamp delta time to avoid large physics anomalies during stuttering
                    onTick(elapsedSeconds.coerceAtMost(0.05))
                }
            }
        }
    }

    // Keep the screen awake while playing
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Haptic feedback: a strong buzz on crash, lighter taps on capture / power-up pickup
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.crashCount) {
        if (state.crashCount > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.captureCount) {
        if (state.captureCount > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(state.powerUpCollectedCount) {
        if (state.powerUpCollectedCount > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Time-pressure warnings. Each threshold fires exactly once per level: a
    // banner + buzz at 30s left, and a sharper one at 10s. Booleans (not the raw
    // seconds) key the effects, so they trigger on the crossing rather than every
    // frame, and reset naturally when a new level starts.
    val secondsLeft = state.timeRemainingSeconds
    val warn30 = secondsLeft <= 30.0 && secondsLeft > 10.0
    val warn10 = secondsLeft <= 10.0 && secondsLeft > 0.0
    var timeBanner by remember(state.levelNumber) { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.levelNumber, warn30) {
        if (warn30) {
            timeBanner = 30
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(1900)
            if (timeBanner == 30) timeBanner = null
        }
    }
    LaunchedEffect(state.levelNumber, warn10) {
        if (warn10) {
            timeBanner = 10
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(1900)
            if (timeBanner == 10) timeBanner = null
        }
    }

    // Auto-pause the game (and its music) when the app goes to the background
    val currentStatus by rememberUpdatedState(state.status)
    val pauseNow by rememberUpdatedState(onPauseToggle)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && currentStatus == GameStateStatus.RUNNING) {
                pauseNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Layout Root
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Jungle backdrop: the real jungle artwork, filling the whole screen.
        // If the image asset is corrupt/undecodable, fall back to a jungle-toned
        // gradient instead of crashing.
        // Downsample by 2: full-screen backdrop doesn't need full resolution, and this
        // halves peak memory on low-RAM phones.
        val jungleBg = rememberSafeImage(R.drawable.bg_game, sampleSize = 2)
        if (jungleBg != null) {
            Image(
                bitmap = jungleBg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF07210B), Color(0xFF031407), Color(0xFF010603))
                        )
                    )
            )
        }
        // Dark scrim so the HUD text stays readable and characters pop over the busy jungle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================== 1. TOP HUD HEADER (compact, never over the field) ==================
            HUDHeader(state = state, onPauseToggle = onPauseToggle)

            // ================== 2. THE PLAYFIELD CANVAS (fills all remaining screen) ==================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Playfield(
                    state = state,
                    onDirectionChanged = onDirectionChanged,
                    onFieldSized = onFieldSized
                )

                // Level-intro banner overlay: level number, goal, and the countdown
                if (introCount >= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = if (introCount > 0) 0.5f else 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "LEVEL ${state.levelNumber}",
                                color = NeonYellow,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp
                            )
                            Text(
                                text = "CLAIM ${state.targetPercentage.toInt()}% OF THE JUNGLE",
                                color = JungleCoast,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )

                            // Your hero, wearing the equipped skin - so the colourway
                            // you bought is on show every time a level begins.
                            HeroPreview(skinId = state.skinId)

                            // New-spider reveal: shows the newcomer's portrait, name and
                            // what makes it dangerous, so nothing on the field is a mystery.
                            if (newEnemy != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                NewEnemyCard(newEnemy)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            // The count itself pops in on every change
                            key(introCount) {
                                val pop = remember {
                                    androidx.compose.animation.core.Animatable(1.9f)
                                }
                                LaunchedEffect(Unit) {
                                    pop.animateTo(
                                        1f,
                                        androidx.compose.animation.core.spring(dampingRatio = 0.45f)
                                    )
                                }
                                Text(
                                    text = if (introCount > 0) "$introCount" else "GO!",
                                    color = if (introCount > 0) Color.White else NeonGreen,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = pop.value
                                        scaleY = pop.value
                                    }
                                )
                            }
                        }
                    }
                }

                // One-line touch tutorial on the first level, fading away on its own.
                // With no visible buttons, this is the only teaching the game needs.
                if (state.levelNumber == 1) {
                    var hintVisible by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(8000)
                        hintVisible = false
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hintVisible,
                        exit = androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(900)
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 22.dp)
                    ) {
                        Text(
                            text = "SWIPE TO STEER  •  TAP TO STOP",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                // Time-pressure warning banner (30s, then 10s), plus a red urgency
                // vignette on the final ten seconds so the pressure is unmissable.
                if (warn10 && state.status == GameStateStatus.RUNNING) {
                    val urgency = rememberInfiniteTransition(label = "urgency").animateFloat(
                        initialValue = 0.12f,
                        targetValue = 0.34f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(500),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "urgencyPulse"
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color(0xFFFF2A2A).copy(alpha = urgency.value)),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width.coerceAtLeast(size.height) * 0.75f
                            ),
                            size = size
                        )
                    }
                }
                timeBanner?.let { warnAt ->
                    val urgent = warnAt == 10
                    val pulse = rememberInfiniteTransition(label = "timeWarn").animateFloat(
                        initialValue = 0.94f,
                        targetValue = 1.10f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(if (urgent) 260 else 420),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "timeWarnPulse"
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 26.dp)
                            .graphicsLayer {
                                scaleX = pulse.value
                                scaleY = pulse.value
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .border(
                                2.dp,
                                if (urgent) Color(0xFFFF2A2A) else NeonYellow,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (urgent) "10 SECONDS!" else "30 SECONDS LEFT",
                            color = if (urgent) Color(0xFFFF6B6B) else NeonYellow,
                            fontSize = if (urgent) 26.sp else 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = if (urgent) "FINISH IT!" else "HURRY UP",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Crash flashing reset animation overlay
                if (state.status == GameStateStatus.CRASH_RESET) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Red.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E070B)),
                            border = BorderStroke(2.dp, Color.Red),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "SYSTEM CRASHED",
                                    color = Color.Red,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "RE-INITIALIZING SECTOR...",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Paused Screen Overlay
                if (state.status == GameStateStatus.PAUSED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "✦ SIMULATION PAUSED ✦",
                                color = NeonCyan,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )

                            // Sound on/off toggle
                            OutlinedButton(
                                onClick = onToggleSound,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
                                border = BorderStroke(1.5.dp, NeonYellow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    if (state.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "Toggle sound",
                                    tint = NeonYellow
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (state.soundEnabled) "SOUND: ON" else "SOUND: OFF",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = onPauseToggle,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, "Resume", tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("RESUME", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onQuitGame,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonMagenta),
                                    border = BorderStroke(1.5.dp, NeonMagenta),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.ExitToApp, "Quit", tint = NeonMagenta)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("QUIT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

/**
 * Compact top HUD bar: one slim stats row plus a thin capture-progress strip.
 * Deliberately small so the playfield below gets nearly the whole screen, and it
 * sits ABOVE the field - it never covers gameplay.
 */
@Composable
fun HUDHeader(
    state: GameUiState,
    onPauseToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ArcadeBgDark.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("real_time_hud_overlay")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level
            Text(
                text = "L${state.levelNumber}",
                color = NeonYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )

            // Score (+ combo multiplier when active)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = String.format("%06d", state.score),
                    color = NeonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                if (state.scoreMultiplier > 1) {
                    // Higher combos pulse faster and glow hotter (yellow -> orange) -
                    // a quiet build toward the max multiplier rather than one flat badge
                    // that looks identical whether this is a x2 or a x8 chain.
                    val comboBoost = (state.scoreMultiplier - 1).coerceIn(0, 7)
                    val pulseScale by rememberInfiniteTransition(label = "combo_pulse")
                        .animateFloat(
                            initialValue = 1f,
                            targetValue = 1f + comboBoost * 0.03f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(420 - comboBoost * 30, easing = LinearEasing),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ),
                            label = "combo_pulse_scale"
                        )
                    val comboColor = lerpColor(NeonYellow, Color(0xFFFF8A3D), comboBoost / 7f)
                    Text(
                        text = "COMBO x${state.scoreMultiplier}",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                            .clip(RoundedCornerShape(5.dp))
                            .background(comboColor)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            // Time remaining
            run {
                val timeInt = state.timeRemainingSeconds.toInt()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Time",
                        tint = NeonPurple,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = String.format("%03d", timeInt),
                        color = if (timeInt < 30) NeonMagenta else NeonCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Lives
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Lives Icon",
                    tint = NeonMagenta,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "x${state.lives}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Pause button
            IconButton(
                onClick = onPauseToggle,
                modifier = Modifier.size(30.dp).testTag("pause_button")
            ) {
                Icon(
                    imageVector = if (state.status == GameStateStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = "Pause",
                    tint = NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Thin capture-progress strip with the current % and target
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val progress = (state.capturedPercentage / 100.0).toFloat().coerceIn(0f, 1f)
            val targetProgress = (state.targetPercentage / 100.0).toFloat().coerceIn(0f, 1f)
            val hasSucceeded = state.capturedPercentage >= state.targetPercentage

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Brush.horizontalGradient(colors = listOf(NeonCyan, NeonMagenta)))
                )
                // Target tick, placed proportionally along the bar
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(targetProgress)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(NeonYellow)
                    )
                }
            }

            // Active power-up effect chips live here, off the field
            if (state.shieldActive) EffectChip("SHIELD", NeonCyan)
            if (state.freezeRemaining > 0) EffectChip("FRZ ${state.freezeRemaining.toInt() + 1}", Color(0xFF9AD8FF))
            if (state.slowRemaining > 0) EffectChip("SLW ${state.slowRemaining.toInt() + 1}", NeonYellow)

            Text(
                text = String.format("%.1f%%", state.capturedPercentage),
                color = if (hasSucceeded) NeonGreen else NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "/${state.targetPercentage.toInt()}%",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Custom Canvas Playfield that renders cells and enemies smoothly.
 * Supports Swipe-based directional input.
 */
@Composable
fun Playfield(
    state: GameUiState,
    onDirectionChanged: (Direction) -> Unit,
    onFieldSized: (Float) -> Unit = {}
) {
    // Independent frame clock. Reading this inside the Canvas makes it redraw every
    // frame, so effect animations (capture flash, crash shake, enemy pulsing) keep
    // running even while the simulation itself is not ticking (e.g. crash reset).
    // Live particles (cell-space); advanced every frame by the clock below.
    val particles = remember { mutableListOf<GameParticle>() }
    var frameTimeMillis by remember { mutableStateOf(0L) }


    val isTesting = remember {
        runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
    }
    LaunchedEffect(Unit) {
        if (isTesting) return@LaunchedEffect
        var lastMs = 0L
        while (true) {
            withFrameNanos { nanos ->
                val ms = nanos / 1_000_000
                val dt = if (lastMs == 0L) 0f else ((ms - lastMs) / 1000f).coerceAtMost(0.05f)
                lastMs = ms
                frameTimeMillis = ms
                val iter = particles.iterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += p.gravity * dt
                    p.vx *= 0.92f
                    p.life -= dt
                    if (p.life <= 0f) iter.remove()
                }
            }
        }
    }

    // Capture flash: a golden shockwave sweeps out from the claimed region's centre,
    // lighting each cell as the wavefront reaches it, followed by a sparkle burst.
    var captureFlashStart by remember { mutableStateOf(-1L) }
    var captureFlashCells by remember { mutableStateOf(emptyList<Pair<Int, Int>>()) }
    var captureCentroidX by remember { mutableStateOf(0f) }
    var captureCentroidY by remember { mutableStateOf(0f) }
    var captureRadius by remember { mutableStateOf(1f) }
    LaunchedEffect(state.captureCount) {
        if (state.captureCount > 0) {
            val cells = state.lastCapturedCells
            captureFlashCells = cells
            captureFlashStart = frameTimeMillis
            if (cells.isNotEmpty()) {
                // Centroid + furthest-cell radius define the shockwave geometry.
                var sx = 0f; var sy = 0f
                for (c in cells) { sx += c.first + 0.5f; sy += c.second + 0.5f }
                val cx = sx / cells.size; val cy = sy / cells.size
                captureCentroidX = cx; captureCentroidY = cy
                var maxD = 1f
                for (c in cells) {
                    val d = kotlin.math.hypot((c.first + 0.5f) - cx, (c.second + 0.5f) - cy)
                    if (d > maxD) maxD = d
                }
                captureRadius = maxD
            }
            // Sparkle burst: gold + green motes lifting off the reclaimed land. Chaining
            // captures within the combo window earns a bigger, hotter burst each step -
            // a small escalating reward for keeping the combo alive, not just a flat
            // effect that looks the same whether this is a x1 or a x8 capture.
            val comboBoost = (state.scoreMultiplier - 1).coerceIn(0, 7)
            val motesPerCell = 2 + comboBoost / 3
            val sample = if (cells.size > 28) cells.shuffled().take(28) else cells
            for (c in sample) {
                repeat(motesPerCell) {
                    val ang = Math.random() * 2 * Math.PI
                    val spd = 2f + Math.random().toFloat() * 4.5f + comboBoost * 0.35f
                    // Higher combos skew the palette hotter (more white-gold, less green).
                    val hot = Math.random() < 0.5 + comboBoost * 0.05
                    particles.add(
                        GameParticle(
                            x = c.first + 0.5f, y = c.second + 0.5f,
                            vx = (cos(ang) * spd).toFloat(), vy = (sin(ang) * spd - 1.5f).toFloat(),
                            life = 0.5f + Math.random().toFloat() * 0.5f, maxLife = 1.0f,
                            color = if (hot) Color(0xFFFFE082) else NeonGreen,
                            size = (0.10f + Math.random().toFloat() * 0.16f) * (1f + comboBoost * 0.04f),
                            gravity = 4.5f
                        )
                    )
                }
            }
        }
    }

    // Crash shake + red vignette + red splash particles
    var crashFlashStart by remember { mutableStateOf(-1L) }
    LaunchedEffect(state.crashCount) {
        if (state.crashCount > 0) {
            crashFlashStart = frameTimeMillis
            repeat(28) {
                val ang = Math.random() * 2 * Math.PI
                val spd = 3f + Math.random().toFloat() * 6f
                particles.add(
                    GameParticle(
                        x = state.playerX + 0.5f, y = state.playerY + 0.5f,
                        vx = (cos(ang) * spd).toFloat(), vy = (sin(ang) * spd).toFloat(),
                        life = 0.4f + Math.random().toFloat() * 0.4f, maxLife = 0.8f,
                        color = Color(0xFFFF4466), size = 0.14f + Math.random().toFloat() * 0.16f, gravity = 7f
                    )
                )
            }
        }
    }

    // Character sprites (transparent PNGs in res/drawable-nodpi). Loaded once and
    // reused. Each may be null if the asset is corrupt - draw sites fall back to a
    // simple vector creature so the game keeps running no matter what.
    val caterpillarSprite = rememberSafeImage(R.drawable.sprite_caterpillar)
    // Equipped cosmetic skin: recolours the hero art (without flattening its
    // shading), tints the trail it leaves, and may add a flourish over the body.
    val skin = remember(state.skinId) { CaterpillarSkin.byId(state.skinId) }
    val skinFilter = remember(skin) { skin.colorFilter }
    // Every enemy type now has its own painted sprite. Earlier builds re-coloured
    // three spiders with a flat SrcAtop tint, which replaced every pixel with one
    // colour and reduced nine of the eleven types to solid silhouettes.
    val spiderRedSprite = rememberSafeImage(R.drawable.sprite_spider_red)
    val spiderBlueSprite = rememberSafeImage(R.drawable.sprite_spider_blue)
    val spiderGreenSprite = rememberSafeImage(R.drawable.sprite_spider)
    val hunterSprite = rememberSafeImage(R.drawable.sprite_spider_hunter)
    val speederSprite = rememberSafeImage(R.drawable.sprite_spider_speeder)
    val eaterSprite = rememberSafeImage(R.drawable.sprite_spider_eater)
    val spitterSprite = rememberSafeImage(R.drawable.sprite_spider_spitter)
    val weaverSprite = rememberSafeImage(R.drawable.sprite_weaver)
    val hornetSprite = rememberSafeImage(R.drawable.sprite_hornet)
    val phantomSprite = rememberSafeImage(R.drawable.sprite_phantom)
    val broodmotherSprite = rememberSafeImage(R.drawable.sprite_broodmother)
    val spiderlingSprite = rememberSafeImage(R.drawable.sprite_spiderling)

    Box(
        modifier = Modifier
            // Fill ALL remaining screen space. The grid itself is generated to match
            // this box's aspect ratio (reported below), so cells stay square while
            // the field runs edge-to-edge.
            .fillMaxSize()
            .onSizeChanged { sz ->
                if (sz.width > 0 && sz.height > 0) {
                    onFieldSized(sz.width.toFloat() / sz.height.toFloat())
                }
            }
            .border(1.5.dp, JungleBorder, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            // Dark, mostly-opaque panel so the jungle bleeds in only slightly and gameplay stays readable
            .background(JunglePanel)
            .pointerInput(Unit) {
                // Continuous swipe steering: movement accumulates while the finger is
                // down, and every `stepPx` of travel fires the dominant direction and
                // resets. The player can steer through corners with one long stroke -
                // no lifting, no per-event jitter (the old code re-decided on every
                // tiny drag event, which felt twitchy on diagonals).
                val stepPx = 26.dp.toPx()
                var accX = 0f
                var accY = 0f
                detectDragGestures(
                    onDragStart = { accX = 0f; accY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accX += dragAmount.x
                        accY += dragAmount.y
                        if (abs(accX) >= stepPx || abs(accY) >= stepPx) {
                            if (abs(accX) > abs(accY)) {
                                onDirectionChanged(if (accX > 0f) Direction.RIGHT else Direction.LEFT)
                            } else {
                                onDirectionChanged(if (accY > 0f) Direction.DOWN else Direction.UP)
                            }
                            accX = 0f
                            accY = 0f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // A simple tap (no drag) halts the caterpillar - the safety brake the
                // old STOP button provided, now on the whole field.
                detectTapGestures(onTap = { onDirectionChanged(Direction.NONE) })
            }
            .testTag("playfield_canvas")
    ) {
        val now = frameTimeMillis

        // 1. STATIC BACKGROUND CANVAS: GPU-cached using graphicsLayer.
        // This is only redraws when the level configuration or capture/grid cells change.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer() // GPU Cache! Highly performant, avoids CPU allocation & draw loops
        ) {
            val cellW = size.width / state.gridWidth
            val cellH = size.height / state.gridHeight
            val cellMin = min(cellW, cellH)

            // ---------- 1. Subtle background grid ----------
            for (x in 1 until state.gridWidth) {
                drawLine(
                    color = Color.White,
                    start = Offset(x * cellW, 0f),
                    end = Offset(x * cellW, size.height),
                    strokeWidth = 0.5f,
                    alpha = 0.05f
                )
            }
            for (y in 1 until state.gridHeight) {
                drawLine(
                    color = Color.White,
                    start = Offset(0f, y * cellH),
                    end = Offset(size.width, y * cellH),
                    strokeWidth = 0.5f,
                    alpha = 0.05f
                )
            }

            fun isCellOpen(x: Int, y: Int): Boolean =
                x in 0 until state.gridWidth &&
                        y in 0 until state.gridHeight &&
                        state.grid[x][y] != GridCellState.CAPTURED

            if (state.grid.isNotEmpty()) {
                // ---------- 2. Reclaimed (captured) land with a sunlit coastline ----------
                for (x in 0 until state.gridWidth) {
                    for (y in 0 until state.gridHeight) {
                        if (state.grid[x][y] != GridCellState.CAPTURED) continue
                        drawRect(
                            color = JungleCaptured,
                            topLeft = Offset(x * cellW, y * cellH),
                            size = Size(cellW + 0.5f, cellH + 0.5f), // overlap avoids pixel seams
                            alpha = 0.42f
                        )
                    }
                }

                // Bright edge only where reclaimed land meets wild territory: the coastline
                val edgeWidth = (cellMin * 0.1f).coerceAtLeast(1.5f)
                for (x in 0 until state.gridWidth) {
                    for (y in 0 until state.gridHeight) {
                        if (state.grid[x][y] != GridCellState.CAPTURED) continue
                        val left = x * cellW
                        val top = y * cellH
                        val right = (x + 1) * cellW
                        val bottom = (y + 1) * cellH
                        if (isCellOpen(x - 1, y)) {
                            drawLine(JungleCoast, Offset(left, top), Offset(left, bottom), edgeWidth, alpha = 0.9f)
                        }
                        if (isCellOpen(x + 1, y)) {
                            drawLine(JungleCoast, Offset(right, top), Offset(right, bottom), edgeWidth, alpha = 0.9f)
                        }
                        if (isCellOpen(x, y - 1)) {
                            drawLine(JungleCoast, Offset(left, top), Offset(right, top), edgeWidth, alpha = 0.9f)
                        }
                        if (isCellOpen(x, y + 1)) {
                            drawLine(JungleCoast, Offset(left, bottom), Offset(right, bottom), edgeWidth, alpha = 0.9f)
                        }
                    }
                }
            }
        }

        // 2. DYNAMIC FOREGROUND CANVAS: Redraws every frame/tick for player, enemies, trail and particle fx
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / state.gridWidth
            val cellH = size.height / state.gridHeight
            val cellMin = min(cellW, cellH)

            fun cellCenter(cell: Pair<Int, Int>) =
                Offset((cell.first + 0.5f) * cellW, (cell.second + 0.5f) * cellH)

            // Interpolated head position: glides smoothly between grid cells
            val hist = state.pathHistory
            val headCenter: Offset? = when {
                hist.size >= 2 -> lerpOffset(cellCenter(hist[1]), cellCenter(hist[0]), state.moveProgress)
                hist.size == 1 -> cellCenter(hist[0])
                else -> null
            }

            // Crash shake: a short decaying jolt of the whole playfield
            val crashAge = now - crashFlashStart
            val crashActive = crashFlashStart >= 0 && crashAge < 500
            var shakeX = 0f
            var shakeY = 0f
            if (crashActive) {
                val falloff = 1f - crashAge / 500f
                val amp = cellMin * 0.4f * falloff
                shakeX = (sin(now / 11.0) * amp).toFloat()
                shakeY = (cos(now / 13.0) * amp).toFloat()
            }

            translate(left = shakeX, top = shakeY) {
                // ---------- 3. Capture flash: a golden shockwave sweeps the claimed land ----------
                val flashDurMs = 850f
                val flashAge = now - captureFlashStart
                if (captureFlashStart >= 0 && flashAge < flashDurMs) {
                    val prog = flashAge / flashDurMs                 // 0..1 over the flash
                    // Wavefront radius (in cells) expands a bit past the region edge.
                    val waveR = prog * (captureRadius + 3f)
                    val centroidPx = Offset(captureCentroidX * cellW, captureCentroidY * cellH)
                    for (cell in captureFlashCells) {
                        val d = kotlin.math.hypot(
                            (cell.first + 0.5f) - captureCentroidX,
                            (cell.second + 0.5f) - captureCentroidY
                        )
                        // How long ago (in cells) the wavefront passed this cell.
                        val since = waveR - d
                        if (since < 0f) continue                     // wave hasn't arrived yet
                        val bright = (1f - since / 3.5f).coerceIn(0f, 1f)  // bright at front, fades behind
                        if (bright <= 0f) continue
                        // Gold at the wavefront, cooling to leaf-green behind it.
                        val col = lerpColor(JungleCaptured, Color(0xFFFFF2C0), bright)
                        drawRect(
                            color = col,
                            topLeft = Offset(cell.first * cellW, cell.second * cellH),
                            size = Size(cellW + 0.5f, cellH + 0.5f),
                            alpha = 0.85f * bright * (1f - prog * 0.3f)
                        )
                    }
                    // Expanding golden ring outline riding the wavefront.
                    val ringR = waveR * cellMin
                    if (ringR > cellMin) {
                        drawCircle(
                            color = Color(0xFFFFE082),
                            radius = ringR,
                            center = centroidPx,
                            alpha = (1f - prog) * 0.55f,
                            style = Stroke(width = cellMin * 0.35f)
                        )
                    }
                }

                // ---------- 4. The trail as a glowing neon line ----------
                if (state.trail.isNotEmpty()) {
                    val trailPath = Path()
                    val start = cellCenter(state.trail.first())
                    trailPath.moveTo(start.x, start.y)
                    for (i in 1 until state.trail.size) {
                        val p = cellCenter(state.trail[i])
                        trailPath.lineTo(p.x, p.y)
                    }
                    if (state.isDrawing && headCenter != null) {
                        trailPath.lineTo(headCenter.x, headCenter.y)
                    }
                    val stroke = { width: Float ->
                        Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    }
                    drawPath(trailPath, skin.trailColor, alpha = 0.25f, style = stroke(cellMin * 0.9f))
                    drawPath(trailPath, skin.trailColor, alpha = 0.9f, style = stroke(cellMin * 0.45f))
                    drawPath(trailPath, Color.White, alpha = 0.85f, style = stroke(cellMin * 0.15f))
                    drawTrailEffect(
                        effect = skin.effect,
                        accent = skin.trailColor,
                        path = trailPath,
                        cellMin = cellMin,
                        now = now
                    )
                }

                // ---------- 4b. Power-ups on the field ----------
                for (pu in state.powerUps) {
                    drawPowerUp(
                        center = Offset((pu.x + 0.5f) * cellW, (pu.y + 0.5f) * cellH),
                        cellMin = cellMin,
                        type = pu.type.name,
                        now = now
                    )
                }

                // ---------- 4c. Sticky web traps spun by Weavers ----------
                for ((tx, ty) in state.webTraps) {
                    val c = Offset((tx + 0.5f) * cellW, (ty + 0.5f) * cellH)
                    val r = cellMin * 0.46f
                    // Silk pad: concentric rings crossed by radial threads
                    drawCircle(Color.White.copy(alpha = 0.16f), r, c)
                    for (ring in 1..2) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f),
                            radius = r * (ring / 2.4f),
                            center = c,
                            style = Stroke(width = cellMin * 0.045f)
                        )
                    }
                    for (k in 0 until 6) {
                        val a = k * (Math.PI / 3)
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = c,
                            end = c + Offset(cos(a).toFloat() * r, sin(a).toFloat() * r),
                            strokeWidth = cellMin * 0.035f
                        )
                    }
                }

                // ---------- 5. Enemies: distinct spider per type ----------
                for (enemy in state.enemies) {
                    val center = Offset(
                        ((enemy.x + 0.5) * cellW).toFloat(),
                        ((enemy.y + 0.5) * cellH).toFloat()
                    )
                    val glow = when (enemy.type) {
                        "Bouncer" -> NeonMagenta
                        "Crawler" -> NeonCyan
                        "Hunter" -> Color(0xFFFF2A2A)
                        "Speeder" -> Color(0xFFFFD500)
                        "Eater" -> Color(0xFFB14CFF)     // violet muncher
                        "Spitter" -> Color(0xFFAEEA00)   // venom yellow-green
                        "Weaver" -> Color(0xFFE0E0E0)    // pale silk
                        "Hornet" -> Color(0xFFFFC107)    // wasp amber
                        "Phantom" -> Color(0xFF9FE8FF)   // spectral blue
                        "Broodmother" -> Color(0xFF7B1FA2) // deep royal purple
                        "Spiderling" -> Color(0xFFCE93D8) // pale brood
                        else -> NeonGreen   // Jumper
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.4f), Color.Transparent),
                            center = center,
                            radius = cellMin * 1.9f
                        ),
                        radius = cellMin * 1.9f,
                        center = center
                    )
                    if (enemy.type == "Hunter") {
                        val pulse = (0.7f + 0.3f * sin(now / 140.0 + enemy.id).toFloat())
                        drawCircle(
                            color = Color(0xFFFF2A2A),
                            radius = cellMin * (1.4f + 0.2f * pulse),
                            center = center,
                            alpha = 0.5f * pulse,
                            style = Stroke(width = cellMin * 0.12f)
                        )
                    }
                    // Weaver telegraph: a silky ring swells as the next trap is spun.
                    if (enemy.type == "Weaver") {
                        val charge = ((enemy as? Weaver)?.spinCharge ?: 0.0).toFloat()
                        if (charge > 0.05f) {
                            drawCircle(
                                color = Color.White,
                                radius = cellMin * (0.9f + charge * 0.9f),
                                center = center,
                                alpha = 0.35f * charge,
                                style = Stroke(width = cellMin * 0.09f)
                            )
                        }
                    }
                    // Broodmother telegraph: the egg sac glows before it hatches.
                    if (enemy.type == "Broodmother") {
                        val charge = ((enemy as? Broodmother)?.broodCharge ?: 0.0).toFloat()
                        if (charge > 0.03f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFE1BEE7).copy(alpha = 0.55f * charge),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = cellMin * 2.4f
                                ),
                                radius = cellMin * 2.4f,
                                center = center
                            )
                        }
                    }
                    // Phantom: a spectral shimmer, drawn semi-transparent below.
                    // Spitter telegraph: a warning ring tightens as it charges a web shot.
                    if (enemy.type == "Spitter") {
                        val charge = ((enemy as? Spitter)?.spitCharge ?: 0.0).toFloat()
                        if (charge > 0.05f) {
                            drawCircle(
                                color = Color(0xFFAEEA00),
                                radius = cellMin * (2.0f - charge * 1.1f),
                                center = center,
                                alpha = 0.25f + 0.5f * charge,
                                style = Stroke(width = cellMin * (0.05f + 0.12f * charge))
                            )
                        }
                    }
                    // Speeders leave motion streaks trailing opposite their velocity
                    if (enemy.type == "Speeder") {
                        val vmag = kotlin.math.hypot(enemy.vx, enemy.vy).toFloat()
                        if (vmag > 0.01f) {
                            val ux = (-enemy.vx / vmag).toFloat()
                            val uy = (-enemy.vy / vmag).toFloat()
                            for (k in 1..3) {
                                val off = cellMin * 0.5f * k
                                drawCircle(
                                    color = Color(0xFFFFD500),
                                    radius = cellMin * (0.5f - 0.12f * k),
                                    center = Offset(center.x + ux * off, center.y + uy * off),
                                    alpha = 0.35f / k
                                )
                            }
                        }
                    }
                    // One painted sprite per type - no recolouring, so every spider
                    // keeps its own shading, markings and expression.
                    val sprite = when (enemy.type) {
                        "Bouncer" -> spiderRedSprite
                        "Crawler" -> spiderBlueSprite
                        "Hunter" -> hunterSprite
                        "Speeder" -> speederSprite
                        "Eater" -> eaterSprite
                        "Spitter" -> spitterSprite
                        "Weaver" -> weaverSprite
                        "Hornet" -> hornetSprite
                        "Phantom" -> phantomSprite
                        "Broodmother" -> broodmotherSprite
                        "Spiderling" -> spiderlingSprite
                        else -> spiderGreenSprite   // Jumper
                    }
                    val speed = kotlin.math.hypot(enemy.vx, enemy.vy).toFloat()
                    val leapScale = if (enemy.type == "Jumper") (1f + (speed / 20f)).coerceAtMost(1.6f) else 1f
                    // Sizes are tuned against each sprite's own framing: the Hornet and
                    // Phantom art carry a painted aura, so their creature fills less of
                    // the image and needs no extra shrink.
                    val sizeScale = when (enemy.type) {
                        "Hunter" -> 1.15f
                        "Broodmother" -> 1.7f     // an imposing queen
                        "Spiderling" -> 0.55f     // scurrying babies
                        else -> 1f
                    }
                    // New spider art faces LEFT natively; mirror when moving right.
                    // Uses the engine's smoothed facing so a spider bouncing inside a
                    // tight pocket doesn't mirror every frame (which read as flicker).
                    val flip = enemy.facing > 0

                    // Crawl gait: a calm, low-frequency leg cadence (not a buzz). The
                    // body sways gently and bobs as if pushing off alternating legs;
                    // amplitudes stay small so it reads as a real spider crawling
                    // rather than vibrating. Each enemy's id de-syncs its phase.
                    val speedNorm = (speed / 10f).coerceIn(0f, 1f)
                    val gaitHz = 1.1 + 2.1 * speedNorm                       // ~1-3 Hz, was 2-7
                    val phase = (now / 1000.0 * gaitHz * 2.0 * Math.PI + enemy.id * 1.7)
                    val wobbleDeg = (sin(phase) * (0.8 + 1.6 * speedNorm)).toFloat()   // small sway
                    val stretch = 1f + (0.012f + 0.028f * speedNorm) * sin(phase).toFloat()
                    // Vertical bob at twice the stride rate reads as leg steps, kept subtle.
                    val bob = (sin(phase * 2.0) * cellMin * (0.02 + 0.045 * speedNorm)).toFloat()

                    // Jumpers physically leave the ground mid-leap: body rises, shadow shrinks
                    val lift = (leapScale - 1f) / 0.6f
                    val spriteLong = cellMin * ENEMY_SPRITE_CELLS * leapScale * sizeScale
                    val bodyCenter = center + Offset(0f, bob - lift * cellMin * 1.1f)

                    drawGroundShadow(
                        center = center + Offset(0f, cellMin * 0.75f),
                        width = spriteLong * 0.55f,
                        lift = lift
                    )
                    if (sprite != null) {
                        // Phantoms are see-through: you can watch them slide under
                        // your claimed land, which is what makes them unsettling.
                        // The art is already painted as vapour, so this only needs a
                        // light touch - any less and the ghost stops reading at all.
                        val ghost = enemy.type == "Phantom"
                        drawSprite(
                            image = sprite,
                            center = bodyCenter,
                            targetLongSide = spriteLong,
                            rotationDeg = wobbleDeg,
                            flipX = flip,
                            scaleX = stretch,
                            scaleY = 1f / stretch,
                            alpha = if (ghost) 0.72f else 1f
                        )
                    } else {
                        // Asset failed to decode: draw a simple spider so gameplay continues
                        drawFallbackSpider(
                            center = bodyCenter,
                            radius = cellMin * 0.85f * leapScale * sizeScale,
                            color = glow
                        )
                    }
                }

                // ---------- 5b. Web projectiles fired by spitters ----------
                for (web in state.webShots) {
                    val wc = Offset(((web.x + 0.5) * cellW).toFloat(), ((web.y + 0.5) * cellH).toFloat())
                    val vmag = kotlin.math.hypot(web.vx, web.vy).toFloat()
                    val webCol = Color(0xFFD7F26B)
                    // Motion streak trailing opposite the travel direction
                    if (vmag > 1e-4f) {
                        val ux = (-web.vx / vmag).toFloat()
                        val uy = (-web.vy / vmag).toFloat()
                        drawLine(
                            color = webCol.copy(alpha = 0.35f),
                            start = wc,
                            end = Offset(wc.x + ux * cellMin * 1.0f, wc.y + uy * cellMin * 1.0f),
                            strokeWidth = cellMin * 0.14f,
                            cap = StrokeCap.Round
                        )
                    }
                    // Sticky glob with a couple of radiating strands
                    for (k in 0 until 4) {
                        val a = k * (Math.PI / 2) + 0.4
                        drawLine(
                            color = webCol.copy(alpha = 0.55f),
                            start = wc,
                            end = Offset(
                                wc.x + cos(a).toFloat() * cellMin * 0.34f,
                                wc.y + sin(a).toFloat() * cellMin * 0.34f
                            ),
                            strokeWidth = cellMin * 0.05f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(Color.White.copy(alpha = 0.9f), cellMin * 0.24f, wc)
                    drawCircle(webCol, cellMin * 0.24f, wc, style = Stroke(width = cellMin * 0.07f))
                }

                // ---------- 6. The player: the caterpillar sprite, facing its direction ----------
                if (headCenter != null) {
                    val dir = if (state.playerDirection != Direction.NONE) {
                        state.playerDirection
                    } else if (hist.size >= 2) {
                        when {
                            hist[0].first < hist[1].first -> Direction.LEFT
                            hist[0].first > hist[1].first -> Direction.RIGHT
                            hist[0].second < hist[1].second -> Direction.UP
                            else -> Direction.DOWN
                        }
                    } else {
                        Direction.LEFT
                    }

                    val (rotationDeg, flipX) = when (dir) {
                        Direction.LEFT -> 0f to false
                        Direction.RIGHT -> 0f to true
                        Direction.UP -> 90f to false
                        Direction.DOWN -> 270f to false
                        Direction.NONE -> 0f to false
                    }

                    // Inchworm crawl cycle while moving: the body rhythmically stretches
                    // and contracts along its own length with a slight waddle; at rest it
                    // settles into a gentle idle "breathing" so it never looks frozen.
                    val moving = state.playerDirection != Direction.NONE &&
                        state.status == GameStateStatus.RUNNING
                    val crawlPhase = now / 1000.0 * 2.0 * Math.PI * (if (moving) 4.5 else 1.2)
                    val crawlAmp = if (moving) 0.085f else 0.02f
                    val stretch = 1f + crawlAmp * sin(crawlPhase).toFloat()
                    val waddleDeg = if (moving) (sin(crawlPhase * 0.5) * 2.5).toFloat() else 0f

                    drawGroundShadow(
                        center = headCenter + Offset(0f, cellMin * 0.62f),
                        width = cellMin * PLAYER_SPRITE_CELLS * 0.6f
                    )
                    // Sunlit halo behind the caterpillar: the default skin's body colour
                    // (leaf green) sits too close to the reclaimed-ground fill colour to
                    // read clearly on its own (tester feedback), so a warm gold wash plus
                    // a tight white rim gives it a consistent silhouette against both the
                    // dark wild jungle and the lighter captured land.
                    drawCircle(JungleCoast, cellMin * 1.3f, headCenter, alpha = 0.32f)
                    drawCircle(Color.White, cellMin * 0.95f, headCenter, alpha = 0.22f)
                    if (caterpillarSprite != null) {
                        drawSprite(
                            image = caterpillarSprite,
                            center = headCenter,
                            targetLongSide = cellMin * PLAYER_SPRITE_CELLS,
                            rotationDeg = rotationDeg + waddleDeg,
                            flipX = flipX,
                            colorFilter = skinFilter,
                            scaleX = stretch,
                            scaleY = 1f - (stretch - 1f) * 0.7f
                        )
                        drawSkinEffect(
                            effect = skin.effect,
                            accent = skin.trailColor,
                            center = headCenter,
                            longSide = cellMin * PLAYER_SPRITE_CELLS,
                            rotationDeg = rotationDeg + waddleDeg,
                            flipX = flipX,
                            now = now
                        )
                    } else {
                        // Asset failed to decode: draw a simple caterpillar so gameplay continues
                        drawFallbackCaterpillar(headCenter, cellMin * PLAYER_SPRITE_CELLS)
                    }
                }

                // ---------- 6b. Particles ----------
                for (p in particles) {
                    val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                    drawCircle(
                        color = p.color,
                        radius = p.size * cellMin,
                        center = Offset(p.x * cellW, p.y * cellH),
                        alpha = alpha
                    )
                }
            }

            // ---------- 7. Crash vignette ----------
            if (crashActive) {
                val fade = 1f - crashAge / 500f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Red.copy(alpha = 0.4f * fade)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width.coerceAtLeast(size.height) * 0.75f
                    ),
                    size = size
                )
            }
        }

    }
}

/**
 * The player's caterpillar shown on the level-intro banner in whichever skin is
 * equipped, gently breathing - a character-select style look at your hero before
 * the countdown starts.
 */
@Composable
private fun HeroPreview(skinId: String, modifier: Modifier = Modifier) {
    val art = rememberSafeImage(R.drawable.sprite_caterpillar) ?: return
    val skin = remember(skinId) { CaterpillarSkin.byId(skinId) }
    val breathe = rememberInfiniteTransition(label = "hero").animateFloat(
        initialValue = 0.97f,
        targetValue = 1.05f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "heroBreathe"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(top = 6.dp)
    ) {
        Image(
            bitmap = art,
            contentDescription = "Your caterpillar",
            colorFilter = skin.colorFilter,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .height(64.dp)
                .graphicsLayer {
                    scaleX = breathe.value
                    scaleY = breathe.value
                }
        )
        Text(
            text = skin.displayName,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}

/**
 * Drawable for an enemy type, used by the reveal card so the portrait is the very
 * same artwork the player is about to meet on the board.
 */
private fun enemyPortrait(type: String): Int = when (type) {
    "Crawler" -> R.drawable.sprite_spider_blue
    "Jumper" -> R.drawable.sprite_spider
    "Hunter" -> R.drawable.sprite_spider_hunter
    "Speeder" -> R.drawable.sprite_spider_speeder
    "Eater" -> R.drawable.sprite_spider_eater
    "Spitter" -> R.drawable.sprite_spider_spitter
    "Weaver" -> R.drawable.sprite_weaver
    "Hornet" -> R.drawable.sprite_hornet
    "Phantom" -> R.drawable.sprite_phantom
    "Broodmother" -> R.drawable.sprite_broodmother
    "Spiderling" -> R.drawable.sprite_spiderling
    else -> R.drawable.sprite_spider_red   // Bouncer
}

/**
 * "NEW ENEMY" reveal card shown in the level-intro banner on levels that add a
 * spider type: its portrait, its name, and one line on what makes it dangerous.
 */
@Composable
private fun NewEnemyCard(intro: EnemyIntro) {
    val accent = when (intro.type) {
        "Bouncer" -> NeonMagenta
        "Crawler" -> NeonCyan
        "Hunter" -> Color(0xFFFF2A2A)
        "Speeder" -> Color(0xFFFFD500)
        "Eater" -> Color(0xFFB14CFF)
        "Spitter" -> Color(0xFFAEEA00)
        "Weaver" -> Color(0xFFE0E0E0)
        "Hornet" -> Color(0xFFFFC107)
        "Phantom" -> Color(0xFF9FE8FF)
        "Broodmother" -> Color(0xFFCE93D8)
        else -> NeonGreen   // Jumper
    }
    val portrait = rememberSafeImage(enemyPortrait(intro.type))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(2.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "⚠ NEW ENEMY",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 3.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (portrait != null) {
            // Gentle idle pulse so the newcomer feels alive on the card
            val breathe = rememberInfiniteTransition(label = "reveal").animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(900),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "revealPulse"
            )
            Image(
                bitmap = portrait,
                contentDescription = intro.name,
                modifier = Modifier
                    .size(74.dp)
                    .graphicsLayer {
                        scaleX = breathe.value
                        scaleY = breathe.value
                    }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = intro.name,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = intro.description,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** A small labelled status chip for an active power-up effect. */
@Composable
private fun EffectChip(label: String, color: Color) {
    Text(
        text = label,
        color = Color.Black,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
