package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonYellow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn vector characters for TerraFill.
 *
 * Everything here is drawn with Canvas primitives (no image assets), so the
 * characters scale crisply with any grid/cell size and can be animated per frame.
 * Sprites are intentionally drawn LARGER than their engine hitboxes: readable
 * characters matter more than pixel-accurate hitbox visuals, and an oversized
 * sprite with a smaller hitbox always errs in the player's favor.
 */

// Character shading palette (darker companions to the neon theme colors)
private val BouncerDark = Color(0xFF7A0040)
private val CrawlerDark = Color(0xFF7A6900)
private val CaterpillarDark = Color(0xFF157A08)
private val EyeWhite = Color(0xFFF4F7FF)
private val EyeDark = Color(0xFF10101E)

private fun polar(angleRad: Float, dist: Float) = Offset(cos(angleRad) * dist, sin(angleRad) * dist)

private fun Offset.normalizedOrZero(): Offset {
    val d = kotlin.math.hypot(x, y)
    return if (d > 1e-4f) Offset(x / d, y / d) else Offset.Zero
}

/**
 * The Bouncer: an angry spiky mine. The spike ring spins slowly; the face stays
 * upright and its pupils track the player.
 *
 * @param scale one grid cell in pixels; the body is drawn ~1.7 cells wide.
 * @param lookAt world position the eyes should track (usually the player head).
 */
fun DrawScope.drawSpikyBouncer(
    center: Offset,
    scale: Float,
    id: Int,
    timeMs: Long,
    lookAt: Offset?
) {
    val bodyR = scale * 0.85f * (1f + 0.05f * sin(timeMs / 220.0 + id).toFloat())

    // Soft glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonMagenta.copy(alpha = 0.4f), Color.Transparent),
            center = center,
            radius = bodyR * 2.1f
        ),
        radius = bodyR * 2.1f,
        center = center
    )

    // Spinning spike ring
    val spin = ((timeMs / 25f) + id * 37f) % 360f
    rotate(degrees = spin, pivot = center) {
        val spikes = Path()
        val count = 9
        for (i in 0 until count) {
            val a = (i * 2.0 * Math.PI / count).toFloat()
            val tip = center + polar(a, bodyR * 1.45f)
            val base1 = center + polar(a - 0.22f, bodyR * 0.9f)
            val base2 = center + polar(a + 0.22f, bodyR * 0.9f)
            spikes.moveTo(base1.x, base1.y)
            spikes.lineTo(tip.x, tip.y)
            spikes.lineTo(base2.x, base2.y)
        }
        drawPath(spikes, BouncerDark)
    }

    // Shaded body sphere
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonMagenta, BouncerDark),
            center = center + Offset(-bodyR * 0.3f, -bodyR * 0.35f),
            radius = bodyR * 1.8f
        ),
        radius = bodyR,
        center = center
    )
    drawCircle(BouncerDark, bodyR, center, style = Stroke(width = bodyR * 0.12f))

    // Face: two big eyes with tracking pupils and angry brows
    val eyeSpread = bodyR * 0.38f
    val eyeR = bodyR * 0.3f
    for (side in intArrayOf(-1, 1)) {
        val eyeC = center + Offset(side * eyeSpread, -bodyR * 0.12f)
        drawCircle(EyeWhite, eyeR, eyeC)
        val gaze = if (lookAt != null) {
            (lookAt - eyeC).normalizedOrZero() * (eyeR * 0.4f)
        } else {
            polar((timeMs / 600f + id), eyeR * 0.3f)
        }
        drawCircle(EyeDark, eyeR * 0.45f, eyeC + gaze)
        drawCircle(EyeWhite, eyeR * 0.14f, eyeC + gaze + Offset(-eyeR * 0.15f, -eyeR * 0.15f))
        // Brow slanting in toward the nose = angry
        drawLine(
            color = BouncerDark,
            start = eyeC + Offset(side * eyeR * 0.9f, -eyeR * 1.25f),
            end = eyeC + Offset(-side * eyeR * 0.5f, -eyeR * 0.55f),
            strokeWidth = bodyR * 0.13f,
            cap = StrokeCap.Round
        )
    }

    // Grumpy mouth
    drawArc(
        color = EyeDark,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = center + Offset(-bodyR * 0.32f, bodyR * 0.3f),
        size = Size(bodyR * 0.64f, bodyR * 0.42f),
        style = Stroke(width = bodyR * 0.1f, cap = StrokeCap.Round)
    )
}

/**
 * The Crawler: a beetle that patrols the walls. Body and legs rotate to face its
 * heading; the legs scuttle as it moves.
 *
 * @param scale one grid cell in pixels; the beetle is ~2 cells long.
 * @param headingRad direction of travel in radians (0 = +x, screen coordinates).
 */
fun DrawScope.drawCrawlerBeetle(
    center: Offset,
    scale: Float,
    headingRad: Float,
    id: Int,
    timeMs: Long
) {
    val halfL = scale * 0.95f
    val halfW = scale * 0.58f

    // Soft glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonYellow.copy(alpha = 0.35f), Color.Transparent),
            center = center,
            radius = halfL * 1.9f
        ),
        radius = halfL * 1.9f,
        center = center
    )

    val degrees = Math.toDegrees(headingRad.toDouble()).toFloat()
    rotate(degrees = degrees, pivot = center) {
        // Scuttling legs: three per side, alternating phase
        val legW = scale * 0.13f
        for (i in -1..1) {
            val legX = center.x + i * halfL * 0.45f
            val wiggle = (sin(timeMs / 70.0 + id + i * 2.1) * halfL * 0.18).toFloat()
            drawLine(
                color = CrawlerDark,
                start = Offset(legX, center.y - halfW * 0.5f),
                end = Offset(legX + wiggle, center.y - halfW * 1.3f),
                strokeWidth = legW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = CrawlerDark,
                start = Offset(legX, center.y + halfW * 0.5f),
                end = Offset(legX - wiggle, center.y + halfW * 1.3f),
                strokeWidth = legW,
                cap = StrokeCap.Round
            )
        }

        // Shell (dark outline oval under a bright shell oval)
        drawOval(
            color = CrawlerDark,
            topLeft = Offset(center.x - halfL, center.y - halfW),
            size = Size(halfL * 2f, halfW * 2f)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(NeonYellow, CrawlerDark),
                center = center + Offset(-halfL * 0.2f, -halfW * 0.4f),
                radius = halfL * 1.9f
            ),
            topLeft = Offset(center.x - halfL * 0.9f, center.y - halfW * 0.85f),
            size = Size(halfL * 1.8f, halfW * 1.7f)
        )

        // Wing split line + spots
        drawLine(
            color = CrawlerDark,
            start = Offset(center.x - halfL * 0.85f, center.y),
            end = Offset(center.x + halfL * 0.45f, center.y),
            strokeWidth = scale * 0.08f
        )
        drawCircle(CrawlerDark, scale * 0.1f, center + Offset(-halfL * 0.35f, -halfW * 0.42f))
        drawCircle(CrawlerDark, scale * 0.1f, center + Offset(-halfL * 0.35f, halfW * 0.42f))
        drawCircle(CrawlerDark, scale * 0.08f, center + Offset(halfL * 0.05f, -halfW * 0.5f))
        drawCircle(CrawlerDark, scale * 0.08f, center + Offset(halfL * 0.05f, halfW * 0.5f))

        // Head at the front with forward-looking eyes
        val headC = Offset(center.x + halfL * 0.95f, center.y)
        drawCircle(CrawlerDark, halfW * 0.6f, headC)
        for (side in intArrayOf(-1, 1)) {
            val eyeC = headC + Offset(halfW * 0.18f, side * halfW * 0.32f)
            drawCircle(EyeWhite, halfW * 0.22f, eyeC)
            drawCircle(EyeDark, halfW * 0.11f, eyeC + Offset(halfW * 0.08f, 0f))
        }
    }
}

/**
 * One body segment of the player caterpillar: a shaded green sphere with a pair
 * of stubby walking feet.
 *
 * @param radius segment radius in pixels.
 * @param dirRad travel direction of this segment along the path, in radians.
 * @param phase per-segment phase so the feet alternate like a real gait.
 */
fun DrawScope.drawCaterpillarSegment(
    center: Offset,
    radius: Float,
    dirRad: Float,
    phase: Float,
    timeMs: Long
) {
    val perp = dirRad + (Math.PI / 2).toFloat()
    val step = (sin(timeMs / 80.0 + phase) * radius * 0.4).toFloat()

    // Feet (under the body, alternating forward/back)
    val footR = radius * 0.26f
    drawCircle(CaterpillarDark, footR, center + polar(perp, radius * 0.95f) + polar(dirRad, step))
    drawCircle(CaterpillarDark, footR, center - polar(perp, radius * 0.95f) - polar(dirRad, step))

    // Shaded body sphere
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF8CFF70), NeonGreen, CaterpillarDark),
            center = center + Offset(-radius * 0.3f, -radius * 0.35f),
            radius = radius * 1.8f
        ),
        radius = radius,
        center = center
    )
    drawCircle(CaterpillarDark, radius, center, style = Stroke(width = radius * 0.14f))
}

/**
 * The player caterpillar's head: bright green sphere, waving antennae, and big
 * friendly eyes that look where it's going.
 *
 * @param facingRad direction the head is looking, in radians.
 */
fun DrawScope.drawCaterpillarHead(
    center: Offset,
    radius: Float,
    facingRad: Float,
    timeMs: Long
) {
    // Antennae, waving slightly out of phase
    for (side in intArrayOf(-1, 1)) {
        val wave = (sin(timeMs / 180.0 + side) * 0.15).toFloat()
        val angle = facingRad + side * 0.5f + wave
        val base = center + polar(angle, radius * 0.75f)
        val tip = center + polar(angle, radius * 1.75f)
        drawLine(
            color = CaterpillarDark,
            start = base,
            end = tip,
            strokeWidth = radius * 0.13f,
            cap = StrokeCap.Round
        )
        drawCircle(NeonGreen, radius * 0.18f, tip)
    }

    // Head sphere (brighter than the body)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFB6FFA3), NeonGreen, CaterpillarDark),
            center = center + Offset(-radius * 0.3f, -radius * 0.35f),
            radius = radius * 1.9f
        ),
        radius = radius,
        center = center
    )
    drawCircle(CaterpillarDark, radius, center, style = Stroke(width = radius * 0.12f))

    // Big friendly eyes looking in the travel direction
    val perp = facingRad + (Math.PI / 2).toFloat()
    val eyeR = radius * 0.34f
    for (side in intArrayOf(-1, 1)) {
        val eyeC = center + polar(facingRad, radius * 0.42f) + polar(perp, side * radius * 0.42f)
        drawCircle(EyeWhite, eyeR, eyeC)
        drawCircle(EyeDark, eyeR * 0.5f, eyeC + polar(facingRad, eyeR * 0.35f))
        drawCircle(EyeWhite, eyeR * 0.16f, eyeC + polar(facingRad, eyeR * 0.35f) + Offset(-eyeR * 0.18f, -eyeR * 0.18f))
    }
}
