package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
 * Main Menu Screen for TerraFill.
 * Features a neon retro arcade layout with high contrast colors and level unlocking.
 */
@Composable
fun MainMenuScreen(
    highestUnlockedLevel: Int,
    onStartGame: (Int) -> Unit,
    onResetProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }

    // Retro Arcade Synthwave Gradient Background
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            ArcadeBgDark,
            Color(0xFF160E2B), // Deep synthwave purple
            ArcadeBgDark
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient)
            .retroArcadeOverlay(scanlineOpacity = 0.15f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp)
        ) {
            // Arcade Header Panel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "✦ RETRO ARCADE CLASSIC ✦",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Title Banner with Neon Glow Border
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, NeonMagenta, RoundedCornerShape(16.dp))
                        .background(ArcadeCardDark)
                        .padding(vertical = 16.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TERRAFILL",
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 4.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "GRID TERRITORY EXPANSION",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Quick Play Button (Starts highest unlocked level) - Primary button style from Sleek Interface
            Button(
                onClick = { onStartGame(highestUnlockedLevel) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonMagenta,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color.White),
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
                        contentDescription = "Play Icon",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INSERT COIN & START (LVL $highestUnlockedLevel)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Level Selector Panel
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ArcadeCardDark
                ),
                border = BorderStroke(1.5.dp, NeonPurple),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "Level Icon",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SELECT MISSION",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "$highestUnlockedLevel/5 UNLOCKED",
                            color = NeonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // 5 Levels Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(180.dp)
                    ) {
                        items(5) { index ->
                            val levelNum = index + 1
                            val isUnlocked = levelNum <= highestUnlockedLevel

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isUnlocked) Color(0xFF1E1E36) else Color(0xFF0F0F1A)
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = when {
                                            levelNum == highestUnlockedLevel -> NeonCyan // Glowing current
                                            isUnlocked -> NeonPurple.copy(alpha = 0.5f)
                                            else -> Color.White.copy(alpha = 0.05f)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = isUnlocked) {
                                        onStartGame(levelNum)
                                    }
                                    .testTag("level_card_$levelNum"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isUnlocked) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "STAGE",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "$levelNum",
                                            color = if (levelNum == highestUnlockedLevel) NeonCyan else NeonGreen,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Footer / Reset Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.testTag("reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Progress Icon",
                        tint = NeonMagenta.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RESET TERMINAL DATA",
                        color = NeonMagenta.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "⚠ RESET TERMINAL PROGRESS?",
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
