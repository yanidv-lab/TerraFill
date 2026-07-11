package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.*
import com.example.ui.GameUiState
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.min

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

    val bgGradient = Brush.verticalGradient(
        colors = listOf(ArcadeBgDark, Color(0xFF120B24), ArcadeBgDark)
    )

    // Layout Root
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient)
            .retroArcadeOverlay(scanlineOpacity = 0.12f)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    repeat(5) { i ->
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(state.gridWidth.toFloat() / state.gridHeight.toFloat(), matchHeightConstraintsFirst = true)
            .border(2.dp, NeonPurple, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(ArcadeBgDark)
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
        val cellSideWidth = maxWidth / state.gridWidth
        val cellSideHeight = maxHeight / state.gridHeight
        val cellSide = min(cellSideWidth.value, cellSideHeight.value).dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / state.gridWidth
            val cellH = size.height / state.gridHeight

            // 1. Draw subtle background lines
            val borderAlpha = 0.06f
            for (x in 1 until state.gridWidth) {
                drawLine(
                    color = Color.White,
                    start = Offset(x * cellW, 0f),
                    end = Offset(x * cellW, size.height),
                    strokeWidth = 0.5f,
                    alpha = borderAlpha
                )
            }
            for (y in 1 until state.gridHeight) {
                drawLine(
                    color = Color.White,
                    start = Offset(0f, y * cellH),
                    end = Offset(size.width, y * cellH),
                    strokeWidth = 0.5f,
                    alpha = borderAlpha
                )
            }

            // 2. Render all Grid cells
            if (state.grid.isNotEmpty()) {
                for (x in 0 until state.gridWidth) {
                    for (y in 0 until state.gridHeight) {
                        val cellState = state.grid[x][y]
                        if (cellState == GridCellState.CAPTURED) {
                            // Captured territory (Neon Cyan with 20% alpha)
                            drawRect(
                                color = NeonCyan,
                                topLeft = Offset(x * cellW, y * cellH),
                                size = Size(cellW + 0.5f, cellH + 0.5f), // small overlap to prevent pixel gaps
                                alpha = 0.2f
                            )
                            // Draw nice outline borders on captured squares
                            drawRect(
                                color = NeonCyan,
                                topLeft = Offset(x * cellW, y * cellH),
                                size = Size(cellW, cellH),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f),
                                alpha = 0.4f
                            )
                        } else if (cellState == GridCellState.TRAIL) {
                            // Unfinished trail (Neon Magenta)
                            drawRect(
                                color = NeonMagenta,
                                topLeft = Offset(x * cellW, y * cellH),
                                size = Size(cellW, cellH)
                            )
                        }
                    }
                }
            }

            // 3. Render Enemies (smooth floating point positions)
            for (enemy in state.enemies) {
                val ex = enemy.x.toFloat() * cellW
                val ey = enemy.y.toFloat() * cellH
                val r = enemy.radius.toFloat() * min(cellW, cellH)

                if (enemy.type == "Bouncer") {
                    // Bouncer (Neon Magenta with inner core)
                    drawCircle(
                        color = NeonMagenta,
                        radius = r,
                        center = Offset(ex + cellW / 2, ey + cellH / 2)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = r * 0.35f,
                        center = Offset(ex + cellW / 2, ey + cellH / 2)
                    )
                } else {
                    // Crawler (Neon Yellow perimeter hugger)
                    drawCircle(
                        color = NeonYellow,
                        radius = r,
                        center = Offset(ex + cellW / 2, ey + cellH / 2)
                    )
                    drawCircle(
                        color = NeonCyan, // Neon Cyan core
                        radius = r * 0.3f,
                        center = Offset(ex + cellW / 2, ey + cellH / 2)
                    )
                }
            }

            // 4. Render Player Cursor (Sleek White rounded square with subtle glow)
            val px = state.playerX * cellW
            val py = state.playerY * cellH
            val pSize = 0.8f * min(cellW, cellH)
            val paddingX = (cellW - pSize) / 2
            val paddingY = (cellH - pSize) / 2

            // Draw glowing background under cursor
            drawRect(
                color = Color.White,
                topLeft = Offset(px + paddingX - 2f, py + paddingY - 2f),
                size = Size(pSize + 4f, pSize + 4f),
                alpha = 0.25f
            )

            // Draw clean main player block
            drawRect(
                color = Color.White,
                topLeft = Offset(px + paddingX, py + paddingY),
                size = Size(pSize, pSize)
            )
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
    }
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
