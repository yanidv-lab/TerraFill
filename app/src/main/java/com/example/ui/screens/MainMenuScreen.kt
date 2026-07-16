package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Modern, clean, and polished Start Menu for TerraFill.
 * Focuses exclusively on playing stages and showing high scores in a sleek Material 3 layout.
 */
@Composable
fun MainMenuScreen(
    highestUnlockedLevel: Int,
    highScores: Map<Int, Int>,
    levelStars: Map<Int, Int> = emptyMap(),
    lastPlayedLevel: Int = 1,
    onStartGame: (Int) -> Unit,
    onResetProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }

    // Modern clean gradient background
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1E), // Ultra dark indigo
            Color(0xFF14142B), // Dark premium purple
            Color(0xFF0D0D1E)
        )
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth > 650.dp

        if (isWide) {
            // Adaptive wide layout: columns side-by-side
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1000.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Title & Play Action
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TitleHeader()
                    PlayCard(
                        highestUnlockedLevel = highestUnlockedLevel,
                        levelStars = levelStars,
                        lastPlayedLevel = lastPlayedLevel,
                        onStartGame = onStartGame
                    )
                }

                // Right Column: High Scores
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    HighScoresCard(
                        highScores = highScores,
                        onResetClick = { showResetDialog = true }
                    )
                }
            }
        } else {
            // Mobile vertical layout: stacked vertically
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 450.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TitleHeader()

                PlayCard(
                    highestUnlockedLevel = highestUnlockedLevel,
                    levelStars = levelStars,
                    lastPlayedLevel = lastPlayedLevel,
                    onStartGame = onStartGame
                )

                HighScoresCard(
                    highScores = highScores,
                    onResetClick = { showResetDialog = true }
                )
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "RESET TERMINAL DATA?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonMagenta
                )
            },
            text = {
                Text(
                    text = "This will lock all levels above Level 1 and delete your high scores permanently. Continue?",
                    color = Color.White.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                    onClick = {
                        onResetProgress()
                        showResetDialog = false
                    }
                ) {
                    Text("YES, RESET", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", color = NeonCyan, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = ArcadeCardDark,
            textContentColor = Color.White,
            titleContentColor = NeonMagenta
        )
    }
}

@Composable
private fun TitleHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = "TERRAFILL",
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "GRID TERRITORY EXPANSION",
            color = NeonCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlayCard(
    highestUnlockedLevel: Int,
    levelStars: Map<Int, Int> = emptyMap(),
    lastPlayedLevel: Int = 1,
    onStartGame: (Int) -> Unit
) {
    // One-tap resume: return the player exactly where they left off. Falls back
    // to the highest unlocked level if the memory is stale or out of range.
    val continueLevel = lastPlayedLevel.coerceIn(1, highestUnlockedLevel)
    val isFreshStart = highestUnlockedLevel == 1 && continueLevel == 1
    Card(
        colors = CardDefaults.cardColors(containerColor = ArcadeCardDark),
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SYSTEM CONTROLS",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Start)
            )

            // Primary Play Button: resumes the last played level
            Button(
                onClick = { onStartGame(continueLevel) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("play_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFreshStart) "START GAME" else "CONTINUE \u2022 LEVEL $continueLevel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Divider separating launch from manual level selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
                Text(
                    text = "SELECT LEVEL",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
            }

            // Level selection grid, laid out in rows of 5, for the whole campaign
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val perRow = 5
                val totalLevels = com.example.engine.LevelConfig.TOTAL_LEVELS
                val rows = (totalLevels + perRow - 1) / perRow
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0 until perRow) {
                            val level = row * perRow + col + 1
                            if (level <= totalLevels) {
                                LevelBadge(
                                    level = level,
                                    isUnlocked = level <= highestUnlockedLevel,
                                    isCurrent = level == highestUnlockedLevel,
                                    stars = levelStars[level] ?: 0,
                                    onClick = { onStartGame(level) }
                                )
                            } else {
                                // Empty slot keeps the row spacing aligned
                                Spacer(modifier = Modifier.size(1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelBadge(
    level: Int,
    isUnlocked: Boolean,
    isCurrent: Boolean,
    stars: Int = 0,
    onClick: () -> Unit
) {
    val borderColor = when {
        isCurrent -> NeonCyan
        isUnlocked -> NeonPurple.copy(alpha = 0.6f)
        else -> Color.White.copy(alpha = 0.05f)
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isUnlocked) Color(0xFF1E1E3F) else Color(0xFF0F0F1A)
            )
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = isUnlocked, onClick = onClick)
            .testTag("level_card_$level"),
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$level",
                    color = if (isCurrent) NeonCyan else NeonGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                // Earned stars (up to 3) shown beneath the number
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    repeat(3) { i ->
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (i < stars) NeonYellow else Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun HighScoresCard(
    highScores: Map<Int, Int>,
    onResetClick: () -> Unit
) {
    val totalScore = highScores.values.sum()

    Card(
        colors = CardDefaults.cardColors(containerColor = ArcadeCardDark),
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = "Scores",
                        tint = NeonYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HIGH SCORES",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "TOTAL: ${String.format("%,d", totalScore)}",
                    color = NeonYellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Scrollable list of high scores
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (level in 1..10) {
                    val score = highScores[level] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STAGE $level",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (score > 0) String.format("%,d PTS", score) else "---",
                            color = if (score > 0) NeonGreen else Color.White.copy(alpha = 0.2f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Subtle, sleek Reset Button inside the scores card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = onResetClick,
                    modifier = Modifier.testTag("reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Progress",
                        tint = NeonMagenta.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RESET PROGRESS",
                        color = NeonMagenta.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
