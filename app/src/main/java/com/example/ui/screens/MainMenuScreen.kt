package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DailyMissionState
import com.example.engine.DailyMission
import com.example.engine.DailyMissionType
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
    onShop: () -> Unit = {},
    resumeLevel: Int? = null,
    onResume: () -> Unit = {},
    dailyMission: DailyMissionState? = null,
    onRefreshDailyMission: () -> Unit = {},
    onClaimDailyMission: () -> Unit = {},
    dailyMissionClaimReward: Int? = null,
    onConsumeDailyMissionClaim: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Rolls today's mission if one doesn't exist yet - cheap no-op every other time
    // the main menu is shown.
    LaunchedEffect(Unit) { onRefreshDailyMission() }
    var showMissionDialog by remember { mutableStateOf(false) }

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

            // An interrupted run takes top billing so it is never lost by accident.
            if (resumeLevel != null) {
                MenuActionButton(
                    label = "RESUME • LEVEL $resumeLevel",
                    icon = Icons.Default.Replay,
                    filled = true,
                    accent = NeonYellow,
                    onClick = onResume,
                    modifier = Modifier.testTag("resume_button")
                )
            }
            MenuActionButton(
                label = "PLAY",
                icon = Icons.Default.PlayArrow,
                filled = resumeLevel == null,
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
                label = "STORE",
                icon = Icons.Default.Palette,
                accent = Color(0xFFB14CFF),
                onClick = onShop,
                modifier = Modifier.testTag("shop_button")
            )
            MenuActionButton(
                label = "SCORE",
                icon = Icons.Default.Stars,
                accent = JungleCoast,
                onClick = onScores,
                modifier = Modifier.testTag("scores_button")
            )
        }

        DailyMissionBadge(
            mission = dailyMission,
            onClick = { showMissionDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 16.dp)
        )

        dailyMission?.let { mission ->
            if (showMissionDialog) {
                DailyMissionDialog(
                    mission = mission,
                    onClaim = {
                        onClaimDailyMission()
                        showMissionDialog = false
                    },
                    onDismiss = { showMissionDialog = false }
                )
            }
        }

        // Flying-stars celebration, played once per claim and cleaned up by the
        // caller (the ViewModel) once we tell it we've shown it.
        if (dailyMissionClaimReward != null) {
            LaunchedEffect(dailyMissionClaimReward) {
                kotlinx.coroutines.delay(1300)
                onConsumeDailyMissionClaim()
            }
            FlyingStarsBurst(modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * The main menu's entry point into today's mission: a star badge that draws the
 * eye while a mission is still open, then trembles and glows once it's done and
 * waiting to be claimed. Tapping it (in any state) opens [DailyMissionDialog].
 */
@Composable
private fun DailyMissionBadge(
    mission: DailyMissionState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (mission == null) return
    val readyToClaim = mission.completed && !mission.claimed
    val doneForToday = mission.claimed

    val infinite = rememberInfiniteTransition(label = "daily_mission_badge")
    // A slow breathing pulse while the mission is still open (draws the eye without
    // being annoying), a fast tremble once it's ready to claim (reads as urgent).
    val scale by infinite.animateFloat(
        initialValue = if (readyToClaim) 0.92f else 1f,
        targetValue = if (readyToClaim) 1.16f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (readyToClaim) 420 else 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "daily_mission_scale"
    )
    val jitterDeg by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(70, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "daily_mission_jitter"
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "daily_mission_glow"
    )

    Box(modifier = modifier.size(60.dp), contentAlignment = Alignment.Center) {
        // Outer glow: only while a reward is actually sitting there waiting.
        if (readyToClaim) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(NeonYellow.copy(alpha = glowAlpha), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    val s = if (doneForToday) 1f else scale
                    scaleX = s
                    scaleY = s
                    rotationZ = if (readyToClaim) jitterDeg else 0f
                }
                .clip(CircleShape)
                .background(Color(0xFF0A1F0E).copy(alpha = 0.92f))
                .border(2.dp, if (doneForToday) JungleBorder.copy(alpha = 0.4f) else NeonYellow, CircleShape)
                .clickable(onClick = onClick)
                .testTag("daily_mission_badge"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Daily mission",
                tint = if (doneForToday) Color.White.copy(alpha = 0.4f) else NeonYellow,
                modifier = Modifier.size(24.dp)
            )
        }
        // "!" flag while the mission still needs doing - gone the moment it's done,
        // whether or not the reward has been claimed yet.
        if (!mission.completed) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(JungleEmber),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** Plain-English description of what today's mission asks for. */
private fun missionDescription(mission: DailyMission): String = when (mission.type) {
    DailyMissionType.CAPTURE_BURST -> "Capture ${mission.target}% of the field in one single move"
    DailyMissionType.FLAWLESS_LEVEL -> "Finish a level without a single crash"
    DailyMissionType.COMBO_STREAK -> "Reach a ${mission.target}x combo in one level"
}

@Composable
private fun DailyMissionDialog(
    mission: DailyMissionState,
    onClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    val readyToClaim = mission.completed && !mission.claimed
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JunglePanel,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.85f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.border(2.dp, NeonYellow.copy(alpha = 0.7f), RoundedCornerShape(18.dp)),
        title = {
            Text(
                text = "DAILY MISSION",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = missionDescription(mission.mission),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(16.dp))
                    Text(
                        text = when {
                            mission.claimed -> "+${mission.reward} stars claimed today - come back tomorrow"
                            readyToClaim -> "+${mission.reward} stars ready to claim"
                            else -> "+${mission.reward} stars when you finish it"
                        },
                        color = NeonYellow,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Streak day ${mission.streakDay} of 7",
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            if (readyToClaim) {
                TextButton(onClick = onClaim, modifier = Modifier.testTag("claim_daily_mission_button")) {
                    Text("CLAIM", color = NeonYellow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("GOT IT", color = JungleCoast, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            if (readyToClaim) {
                TextButton(onClick = onDismiss) {
                    Text("LATER", color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                }
            }
        }
    )
}

/** One flying star's fixed random shape, chosen once per burst. */
private data class StarSeed(val angleRad: Float, val speed: Float, val radius: Float, val delay: Float)

/**
 * A one-shot burst of twinkling stars flying outward and up from screen centre
 * with a fade-out, played when the player claims a reward. Composed only while
 * the caller wants it on screen - it plays exactly once and does not loop.
 */
@Composable
private fun FlyingStarsBurst(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(1200, easing = LinearOutSlowInEasing))
    }
    val stars = remember {
        List(24) {
            StarSeed(
                angleRad = Random.nextDouble(0.0, 2.0 * PI).toFloat(),
                speed = 0.55f + Random.nextFloat() * 0.45f,
                radius = 9f + Random.nextFloat() * 9f,
                delay = Random.nextFloat() * 0.25f
            )
        }
    }
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val reach = kotlin.math.min(size.width, size.height) * 0.55f
        for (s in stars) {
            val t = ((progress.value - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val eased = 1f - (1f - t) * (1f - t) // ease-out
            val dist = eased * reach * s.speed
            val x = cx + cos(s.angleRad) * dist
            val y = cy + sin(s.angleRad) * dist - eased * reach * 0.35f // slight upward drift
            val alpha = (1f - t).coerceIn(0f, 1f)
            drawTwinkleStar(center = Offset(x, y), radius = s.radius, color = NeonYellow.copy(alpha = alpha))
        }
    }
}

/** A tiny four-pointed sparkle: a bright core plus two crossing radiating lines. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTwinkleStar(center: Offset, radius: Float, color: Color) {
    drawCircle(color = color, radius = radius * 0.4f, center = center)
    for (k in 0 until 4) {
        val ang = k * (PI.toFloat() / 2f)
        val arm = Offset(cos(ang) * radius, sin(ang) * radius)
        drawLine(
            color = color,
            start = center - arm,
            end = center + arm,
            strokeWidth = radius * 0.22f,
            cap = StrokeCap.Round
        )
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

