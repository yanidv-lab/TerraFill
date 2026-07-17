package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.LevelConfig
import com.example.ui.theme.*

/**
 * PLAY screen: one-tap continue into the last played level, plus the full
 * campaign grid with per-level star ratings.
 */
@Composable
fun LevelSelectScreen(
    highestUnlockedLevel: Int,
    levelStars: Map<Int, Int>,
    lastPlayedLevel: Int,
    onStartGame: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val continueLevel = lastPlayedLevel.coerceIn(1, highestUnlockedLevel)
    val isFreshStart = highestUnlockedLevel == 1 && continueLevel == 1

    JungleBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 450.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SubScreenHeader(title = "PLAY", onBack = onBack)

            MenuActionButton(
                label = if (isFreshStart) "START GAME" else "CONTINUE • LEVEL $continueLevel",
                icon = Icons.Default.PlayArrow,
                filled = true,
                onClick = { onStartGame(continueLevel) },
                modifier = Modifier.testTag("play_button")
            )

            Text(
                text = "SELECT LEVEL",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            // Level grid, rows of 5, for the whole campaign
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val perRow = 5
                val totalLevels = LevelConfig.TOTAL_LEVELS
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
        isCurrent -> NeonYellow
        isUnlocked -> LeafGreen.copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.05f)
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isUnlocked) Color(0xFF14350F) else Color(0xFF0A1607)
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
                    color = if (isCurrent) NeonYellow else Color(0xFF8CD44F),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
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
