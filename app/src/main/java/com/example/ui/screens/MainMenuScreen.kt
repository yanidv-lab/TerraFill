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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin

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
    onPlay: () -> Unit = {},
    onOptions: () -> Unit = {},
    onScores: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    JungleBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 450.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            TitleHeader()
            JungleHero()

            MenuActionButton(
                label = "PLAY",
                icon = Icons.Default.PlayArrow,
                filled = true,
                onClick = onPlay,
                modifier = Modifier.testTag("play_button")
            )
            MenuActionButton(
                label = "OPTIONS",
                icon = Icons.Default.Settings,
                onClick = onOptions,
                modifier = Modifier.testTag("options_button")
            )
            MenuActionButton(
                label = "SCORE",
                icon = Icons.Default.Stars,
                accent = JungleCoast,
                onClick = onScores,
                modifier = Modifier.testTag("scores_button")
            )
        }
    }
}

/**
 * Animated jungle diorama for the main menu: the caterpillar hero rests on a big
 * leaf spitting a silk strand, while spiders slowly descend and rise on threads
 * from the canopy above. Pure Canvas + the real character art - if any sprite
 * fails to decode it is simply omitted, never a crash.
 */
@Composable
private fun JungleHero(modifier: Modifier = Modifier) {
    // Prefer the hand-made hero artwork; the animated canvas scene below is the
    // fallback when the image asset is missing or corrupt.
    val heroArt = rememberSafeImage(R.drawable.menu_hero)
    if (heroArt != null) {
        Image(
            bitmap = heroArt,
            contentDescription = "The caterpillar facing the jungle spiders",
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, LeafGreen.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
        )
        return
    }
    val caterpillar = rememberSafeImage(R.drawable.sprite_caterpillar)
    val spiderRed = rememberSafeImage(R.drawable.sprite_spider_red)
    val spiderBlue = rememberSafeImage(R.drawable.sprite_spider_blue)
    val spiderGreen = rememberSafeImage(R.drawable.sprite_spider)

    // One slow master clock (0..1 over 8s) drives every motion in the scene
    val t by rememberInfiniteTransition(label = "hero")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
            label = "heroClock"
        )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        val w = size.width
        val h = size.height
        val tau = (2.0 * PI).toFloat()

        // ---- Spiders hanging from the canopy, bobbing on silk threads ----
        data class Hanging(
            val fx: Float,      // horizontal position (fraction of width)
            val depth: Float,   // resting depth (fraction of height)
            val bob: Float,     // bob amplitude (fraction of height)
            val speed: Float,   // bob cycles per master clock loop
            val size: Float     // sprite long side (fraction of width)
        )
        val spiders = listOf(
            Triple(Hanging(0.16f, 0.34f, 0.10f, 2f, 0.20f), spiderRed, false),
            Triple(Hanging(0.50f, 0.20f, 0.07f, 3f, 0.15f), spiderGreen, true),
            Triple(Hanging(0.84f, 0.42f, 0.12f, 1.5f, 0.22f), spiderBlue, false)
        )
        for ((hang, sprite, flip) in spiders) {
            val bodyY = (hang.depth + hang.bob * sin(t * tau * hang.speed + hang.fx * 9f)) * h
            val x = hang.fx * w + sin(t * tau * hang.speed * 0.5f + hang.fx * 4f) * w * 0.008f
            // Silk thread from the top edge down to the spider
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, bodyY),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
            val longSide = hang.size * w
            if (sprite != null) {
                drawSpriteCentered(
                    image = sprite,
                    center = Offset(x, bodyY + longSide * 0.22f),
                    targetLongSide = longSide,
                    rotationDeg = sin(t * tau * hang.speed + hang.fx * 9f) * 5f,
                    flipX = flip
                )
            }
        }

        // ---- The big leaf perch (drawn, so it always exists) ----
        val leafTip = Offset(w * 0.66f, h * 0.72f)
        val leafStem = Offset(w * 0.06f, h * 0.90f)
        val leaf = Path().apply {
            moveTo(leafStem.x, leafStem.y)
            quadraticTo(w * 0.30f, h * 0.58f, leafTip.x, leafTip.y)
            quadraticTo(w * 0.34f, h * 1.02f, leafStem.x, leafStem.y)
            close()
        }
        drawPath(
            leaf,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF6DBB4A), Color(0xFF2E6B22)),
                start = Offset(leafStem.x, h * 0.6f),
                end = leafTip
            )
        )
        // Midrib vein
        drawLine(
            color = Color(0xFF1E4A16),
            start = leafStem,
            end = Offset(leafTip.x - w * 0.02f, leafTip.y),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // ---- The caterpillar hero on the leaf, gently breathing ----
        val catBreath = sin(t * tau * 4f) * h * 0.012f
        val catCenter = Offset(w * 0.30f, h * 0.70f + catBreath)
        val catLong = w * 0.30f
        if (caterpillar != null) {
            // Art faces LEFT; flip so the hero faces the spiders on the right
            drawSpriteCentered(
                image = caterpillar,
                center = catCenter,
                targetLongSide = catLong,
                rotationDeg = sin(t * tau * 2f) * 2f,
                flipX = true
            )
        }

        // ---- Silk spit: a dotted strand shooting toward the lowest spider ----
        val mouth = Offset(catCenter.x + catLong * 0.42f, catCenter.y - catLong * 0.05f)
        val target = Offset(w * 0.84f, h * 0.46f)
        val mid = Offset((mouth.x + target.x) / 2f, minOf(mouth.y, target.y) - h * 0.10f)
        // Strand re-fires twice per clock loop
        val shot = (t * 2f) % 1f
        val reach = (shot * 1.25f).coerceAtMost(1f)
        val dots = 14
        for (i in 0..(dots * reach).toInt()) {
            val f = i / dots.toFloat()
            // Quadratic bezier point
            val a = lerp(mouth, mid, f)
            val b = lerp(mid, target, f)
            val p = lerp(a, b, f)
            drawCircle(
                color = Color.White.copy(alpha = 0.85f - f * 0.35f),
                radius = 2.6f - f * 1.2f,
                center = p
            )
        }
        // Tiny web puff where the strand lands
        if (reach >= 1f) {
            val puff = ((shot - 0.8f) / 0.2f).coerceIn(0f, 1f)
            for (k in 0 until 3) {
                val ang = k * (tau / 6f) + 0.4f
                drawLine(
                    color = Color.White.copy(alpha = 0.8f * puff),
                    start = target - Offset(kotlin.math.cos(ang), sin(ang)) * (7f * puff),
                    end = target + Offset(kotlin.math.cos(ang), sin(ang)) * (7f * puff),
                    strokeWidth = 1.6f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** Linear interpolation between two points. */
private fun lerp(a: Offset, b: Offset, f: Float): Offset =
    Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

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
            text = "CLAIM THE JUNGLE, OUTSMART THE SPIDERS",
            color = JungleCoast,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
    }
}

