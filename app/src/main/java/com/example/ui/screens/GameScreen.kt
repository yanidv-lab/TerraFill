package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
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
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Loads an image resource, tolerating corrupt/undecodable data: returns null instead
 * of throwing. Art assets have repeatedly been corrupted by external tooling, and a
 * broken picture must degrade the visuals - never crash the game.
 */
@Composable
private fun rememberSafeImage(resId: Int, sampleSize: Int = 1): ImageBitmap? {
    val context = LocalContext.current
    return remember(resId) {
        runCatching {
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            android.graphics.BitmapFactory.decodeResource(context.resources, resId, options)?.asImageBitmap()
        }.getOrNull()
    }
}

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
private const val PLAYER_SPRITE_CELLS = 2.9f

/** How many grid cells wide the enemy spider sprite is drawn. */
private const val ENEMY_SPRITE_CELLS = 2.7f

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
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null
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
        if (flipX) scale(-1f, 1f, center)
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
            colorFilter = colorFilter
        )
    }
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
    modifier: Modifier = Modifier
) {
    // 1. Frame-synced Game Loop driving the GameEngine simulation
    LaunchedEffect(state.status) {
        if (state.status == GameStateStatus.RUNNING) {
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
        val jungleBg = rememberSafeImage(R.drawable.bg_jungle, sampleSize = 2)
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================== 1. TOP HUD HEADER ==================
            HUDHeader(state = state, onPauseToggle = onPauseToggle)

            // ================== 2. THE PLAYFIELD CANVAS ==================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Playfield(
                    state = state,
                    onDirectionChanged = onDirectionChanged
                )

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

            // ================== 3. TACTICAL PRECISION CONTROLS ==================
            ControlPanel(
                currentDirection = state.playerDirection,
                onDirectionChanged = onDirectionChanged
            )
        }
    }
}

/**
 * Top HUD Bar displaying stats.
 */
@Composable
fun HUDHeader(
    state: GameUiState,
    onPauseToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ArcadeCardDark),
        border = BorderStroke(1.5.dp, NeonPurple),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // First Row: Stats and Pause button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SCORE",
                        color = NeonPurple,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%06d", state.score),
                        color = NeonCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Level indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "STAGE",
                        color = NeonPurple,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "LEVEL ${String.format("%02d", state.levelNumber)}",
                        color = NeonYellow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Timer countdown
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val timeInt = state.timeRemainingSeconds.toInt()
                    Text(
                        text = "TIME LIMIT",
                        color = NeonPurple,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%03d", timeInt),
                        color = if (timeInt < 30) NeonMagenta else NeonCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Lives indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) { i ->
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Life Icon",
                            tint = if (i < state.lives) NeonMagenta else Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Pause button
                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier.size(36.dp).testTag("pause_button")
                ) {
                    Icon(
                        imageVector = if (state.status == GameStateStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause",
                        tint = NeonCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second Row: Progress toward target percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CAPTURE PROGRESS",
                    color = NeonPurple,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                // Neon progress bar
                val progress = (state.capturedPercentage / 100.0).toFloat().coerceIn(0f, 1f)
                val targetProgress = (state.targetPercentage / 100.0).toFloat().coerceIn(0f, 1f)
                val hasSucceeded = state.capturedPercentage >= state.targetPercentage

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ArcadeBgDark)
                        .border(1.5.dp, NeonPurple, RoundedCornerShape(6.dp))
                ) {
                    // Current progress fill (Gradient matching design: Cyan to Lavender)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Brush.horizontalGradient(colors = listOf(NeonCyan, NeonMagenta)))
                    )
                    
                    // Target indicator tick
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .align(Alignment.CenterStart)
                            .offset(x = (160.dp * targetProgress).coerceAtLeast(0.dp)) // approximate visual indicator
                            .background(NeonYellow)
                    )
                }

                Text(
                    text = String.format("%.1f%%", state.capturedPercentage),
                    color = if (hasSucceeded) NeonGreen else NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "/ ${state.targetPercentage.toInt()}%",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
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
    onDirectionChanged: (Direction) -> Unit
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

    // Capture flash + green particle burst on each completed capture
    var captureFlashStart by remember { mutableStateOf(-1L) }
    var captureFlashCells by remember { mutableStateOf(emptyList<Pair<Int, Int>>()) }
    LaunchedEffect(state.captureCount) {
        if (state.captureCount > 0) {
            captureFlashCells = state.lastCapturedCells
            captureFlashStart = frameTimeMillis
            val cells = state.lastCapturedCells
            val sample = if (cells.size > 24) cells.shuffled().take(24) else cells
            for (c in sample) {
                repeat(2) {
                    val ang = Math.random() * 2 * Math.PI
                    val spd = 2f + Math.random().toFloat() * 4f
                    particles.add(
                        GameParticle(
                            x = c.first + 0.5f, y = c.second + 0.5f,
                            vx = (cos(ang) * spd).toFloat(), vy = (sin(ang) * spd).toFloat(),
                            life = 0.5f + Math.random().toFloat() * 0.4f, maxLife = 0.9f,
                            color = NeonGreen, size = 0.12f + Math.random().toFloat() * 0.14f, gravity = 5f
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
    val spiderRedSprite = rememberSafeImage(R.drawable.sprite_spider_red)
    val spiderBlueSprite = rememberSafeImage(R.drawable.sprite_spider_blue)
    val spiderGreenSprite = rememberSafeImage(R.drawable.sprite_spider)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(state.gridWidth.toFloat() / state.gridHeight.toFloat(), matchHeightConstraintsFirst = true)
            .border(2.dp, JungleBorder, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            // Dark, mostly-opaque panel so the jungle bleeds in only slightly and gameplay stays readable
            .background(JunglePanel)
            .pointerInput(Unit) {
                // Swipe gesture detection
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val (dx, dy) = dragAmount
                        if (abs(dx) > abs(dy)) {
                            if (dx > 8f) onDirectionChanged(Direction.RIGHT)
                            else if (dx < -8f) onDirectionChanged(Direction.LEFT)
                        } else {
                            if (dy > 8f) onDirectionChanged(Direction.DOWN)
                            else if (dy < -8f) onDirectionChanged(Direction.UP)
                        }
                    }
                )
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
                // ---------- 3. Capture flash: newly claimed land lights up and fades ----------
                val flashAge = now - captureFlashStart
                if (captureFlashStart >= 0 && flashAge < 700) {
                    val fade = 1f - flashAge / 700f
                    for (cell in captureFlashCells) {
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cell.first * cellW, cell.second * cellH),
                            size = Size(cellW + 0.5f, cellH + 0.5f),
                            alpha = 0.5f * fade
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
                    drawPath(trailPath, NeonMagenta, alpha = 0.25f, style = stroke(cellMin * 0.9f))
                    drawPath(trailPath, NeonMagenta, alpha = 0.9f, style = stroke(cellMin * 0.45f))
                    drawPath(trailPath, Color.White, alpha = 0.85f, style = stroke(cellMin * 0.15f))
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
                    // Sprite per type: red = bouncer, blue = crawler, green = jumper,
                    // crimson-tinted silhouette = hunter, gold-tinted = speeder
                    val sprite = when (enemy.type) {
                        "Bouncer" -> spiderRedSprite
                        "Crawler" -> spiderBlueSprite
                        "Hunter" -> spiderBlueSprite
                        "Speeder" -> spiderRedSprite
                        else -> spiderGreenSprite   // Jumper
                    }
                    val tint = when (enemy.type) {
                        "Hunter" -> androidx.compose.ui.graphics.ColorFilter.tint(
                            Color(0xFFE01E2B), androidx.compose.ui.graphics.BlendMode.SrcAtop
                        )
                        "Speeder" -> androidx.compose.ui.graphics.ColorFilter.tint(
                            Color(0xFFFFD500), androidx.compose.ui.graphics.BlendMode.SrcAtop
                        )
                        else -> null
                    }
                    val speed = kotlin.math.hypot(enemy.vx, enemy.vy).toFloat()
                    val leapScale = if (enemy.type == "Jumper") (1f + (speed / 20f)).coerceAtMost(1.6f) else 1f
                    val sizeScale = if (enemy.type == "Hunter") 1.15f else 1f
                    val bob = (sin(now / 180.0 + enemy.id) * cellMin * 0.08).toFloat()
                    val flip = enemy.vx < 0
                    if (sprite != null) {
                        drawSprite(
                            image = sprite,
                            center = center + Offset(0f, bob),
                            targetLongSide = cellMin * ENEMY_SPRITE_CELLS * leapScale * sizeScale,
                            rotationDeg = 0f,
                            flipX = flip,
                            colorFilter = tint
                        )
                    } else {
                        // Asset failed to decode: draw a simple spider so gameplay continues
                        drawFallbackSpider(
                            center = center + Offset(0f, bob),
                            radius = cellMin * 0.85f * leapScale * sizeScale,
                            color = glow
                        )
                    }
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

                    drawCircle(Color.White, cellMin * 1.1f, headCenter, alpha = 0.12f) // soft glow
                    if (caterpillarSprite != null) {
                        drawSprite(
                            image = caterpillarSprite,
                            center = headCenter,
                            targetLongSide = cellMin * PLAYER_SPRITE_CELLS,
                            rotationDeg = rotationDeg,
                            flipX = flipX
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

        // Floating HUD Overlay showing real-time statistics
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ArcadeBgDark.copy(alpha = 0.85f))
                .border(1.5.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("real_time_hud_overlay"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Level Icon",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "L${state.levelNumber}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(NeonPurple.copy(alpha = 0.5f))
            )

            // Percentage Captured
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Capture Icon",
                    tint = NeonGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = String.format("%.1f%%", state.capturedPercentage),
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(NeonPurple.copy(alpha = 0.5f))
            )

            // Remaining Lives
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Lives Icon",
                    tint = NeonMagenta,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "x${state.lives}",
                    color = NeonMagenta,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Combo multiplier + active power-up effects (top-left of the playfield)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state.scoreMultiplier > 1) {
                Text(
                    text = "COMBO x${state.scoreMultiplier}",
                    color = NeonYellow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (state.shieldActive) EffectChip("SHIELD", NeonCyan)
            if (state.freezeRemaining > 0) EffectChip("FREEZE ${state.freezeRemaining.toInt() + 1}s", Color(0xFF9AD8FF))
            if (state.slowRemaining > 0) EffectChip("SLOW ${state.slowRemaining.toInt() + 1}s", NeonYellow)
        }
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

/**
 * Tactical precision D-pad control board.
 */
@Composable
fun ControlPanel(
    currentDirection: Direction,
    onDirectionChanged: (Direction) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    ) {
        // D-Pad Grid matching tactile Sleek Interface layout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: UP
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (currentDirection == Direction.UP) NeonPurple.copy(alpha = 0.25f) else Color(0xFF14142B))
                    .border(
                        width = 1.5.dp,
                        color = if (currentDirection == Direction.UP) NeonCyan else NeonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onDirectionChanged(Direction.UP) }
                    .testTag("dpad_up")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    tint = if (currentDirection == Direction.UP) NeonCyan else NeonPurple,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Row 2: LEFT, STOP, RIGHT
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (currentDirection == Direction.LEFT) NeonPurple.copy(alpha = 0.25f) else Color(0xFF14142B))
                    .border(
                        width = 1.5.dp,
                        color = if (currentDirection == Direction.LEFT) NeonCyan else NeonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                        .clickable { onDirectionChanged(Direction.LEFT) }
                        .testTag("dpad_left")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Move Left",
                        tint = if (currentDirection == Direction.LEFT) NeonCyan else NeonPurple,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // STOP (NONE)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (currentDirection == Direction.NONE) NeonPurple.copy(alpha = 0.25f) else Color(0xFF14142B))
                    .border(
                        width = 1.5.dp,
                        color = if (currentDirection == Direction.NONE) NeonCyan else NeonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                        .clickable { onDirectionChanged(Direction.NONE) }
                        .testTag("dpad_stop")
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(if (currentDirection == Direction.NONE) NeonCyan else NeonPurple.copy(alpha = 0.6f), CircleShape)
                    )
                }

                // RIGHT
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (currentDirection == Direction.RIGHT) NeonPurple.copy(alpha = 0.25f) else Color(0xFF14142B))
                    .border(
                        width = 1.5.dp,
                        color = if (currentDirection == Direction.RIGHT) NeonCyan else NeonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                        .clickable { onDirectionChanged(Direction.RIGHT) }
                        .testTag("dpad_right")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Move Right",
                        tint = if (currentDirection == Direction.RIGHT) NeonCyan else NeonPurple,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Row 3: DOWN
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (currentDirection == Direction.DOWN) NeonPurple.copy(alpha = 0.25f) else Color(0xFF14142B))
                    .border(
                        width = 1.5.dp,
                        color = if (currentDirection == Direction.DOWN) NeonCyan else NeonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onDirectionChanged(Direction.DOWN) }
                    .testTag("dpad_down")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    tint = if (currentDirection == Direction.DOWN) NeonCyan else NeonPurple,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
