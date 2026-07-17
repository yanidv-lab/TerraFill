package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * OPTIONS screen: sound toggle, a short how-to-play primer, and the (guarded)
 * progress reset.
 */
@Composable
fun OptionsScreen(
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onResetProgress: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }

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
            SubScreenHeader(title = "OPTIONS", onBack = onBack)

            MenuActionButton(
                label = if (soundEnabled) "SOUND: ON" else "SOUND: OFF",
                icon = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                accent = if (soundEnabled) Color(0xFF8CD44F) else Color.White.copy(alpha = 0.5f),
                onClick = onToggleSound,
                modifier = Modifier.testTag("sound_toggle")
            )

            // How to play - the full rules in five short lines
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F0E).copy(alpha = 0.88f)),
                border = BorderStroke(1.dp, LeafGreen.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "HOW TO PLAY",
                        color = JungleCoast,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    for (line in listOf(
                        "SWIPE anywhere to steer the caterpillar",
                        "TAP to stop moving",
                        "Venture into the wild and return to claimed land to capture territory",
                        "Spiders must not touch you or your trail",
                        "Claim the target percentage before time runs out"
                    )) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", color = Color(0xFF8CD44F), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                text = line,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            MenuActionButton(
                label = "RESET PROGRESS",
                icon = Icons.Default.Delete,
                accent = NeonMagenta,
                onClick = { showResetDialog = true },
                modifier = Modifier.testTag("reset_button")
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "RESET ALL PROGRESS?",
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
                    Text("CANCEL", color = Color(0xFF8CD44F), fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = Color(0xFF0F2413),
            textContentColor = Color.White,
            titleContentColor = NeonMagenta
        )
    }
}
