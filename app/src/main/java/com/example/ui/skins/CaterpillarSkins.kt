package com.example.ui.skins

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * A decorative flourish drawn on top of the hero art for premium skins. Effects are
 * rendered procedurally over the sprite (in its own rotated/mirrored space), so they
 * ride along with the caterpillar without needing extra artwork.
 */
enum class SkinEffect {
    /** Nothing extra - a pure recolour. */
    NONE,

    /** Beach shades perched on the head. */
    SUNGLASSES,

    /** Wet-look lacquer: a bright specular streak sweeping along the body. */
    GLOSS,

    /** Pulsing neon halo around the whole body. */
    GLOW,

    /** Slow twinkles drifting around the body, like frost catching the light. */
    SPARKLE,

    /** Embers rising off the body. */
    EMBER
}

/**
 * A cosmetic caterpillar colourway bought with stars earned from levels.
 *
 * Skins recolour the hero art with a hue rotation (plus an optional saturation
 * tweak) rather than a flat tint, so the original shading, highlights and outlines
 * survive - the caterpillar changes colour instead of turning into a silhouette.
 * Premium skins add a [SkinEffect] on top, and every skin colours the trail it
 * leaves behind so the whole look is coherent.
 */
data class CaterpillarSkin(
    val id: String,
    val displayName: String,
    /**
     * Stars required to unlock. The first colourway is cheap, then prices climb
     * steeply - the finest skins are meant to be a long-term goal earned by
     * replaying (and mastering) the later, higher-paying levels.
     */
    val cost: Int,
    /** Degrees of hue rotation applied to the artwork (0 = untouched). */
    val hueShift: Float = 0f,
    /** 1 = original saturation, <1 washes out, >1 intensifies. */
    val saturation: Float = 1f,
    /** Swatch shown in the shop grid. */
    val swatch: Color,
    /** Colour of the trail this skin draws while claiming territory. */
    val trailColor: Color,
    /** Optional flourish drawn over the sprite. */
    val effect: SkinEffect = SkinEffect.NONE,
    /** One-line description shown in the shop. */
    val blurb: String = ""
) {
    /** The filter to hand to the sprite renderer, or null for the untouched art. */
    val colorFilter: ColorFilter?
        get() = if (hueShift == 0f && saturation == 1f) null else buildFilter(hueShift, saturation)

    companion object {
        /** The full catalogue, cheapest first. [ALL].first() is always owned. */
        val ALL: List<CaterpillarSkin> = listOf(
            CaterpillarSkin(
                id = "classic", displayName = "JUNGLE", cost = 0,
                swatch = Color(0xFF6DBB4A), trailColor = Color(0xFF7CFF4A),
                blurb = "The original."
            ),
            CaterpillarSkin(
                id = "amber", displayName = "AMBER", cost = 50,
                hueShift = -60f, saturation = 1.15f,
                swatch = Color(0xFFE8C33A), trailColor = Color(0xFFFFD54A),
                blurb = "Warm honey glow."
            ),
            CaterpillarSkin(
                id = "teal", displayName = "LAGOON", cost = 140,
                hueShift = 55f,
                swatch = Color(0xFF3FBFA8), trailColor = Color(0xFF3FE8C8),
                blurb = "Cool shallow water."
            ),
            CaterpillarSkin(
                id = "azure", displayName = "AZURE", cost = 320,
                hueShift = 115f,
                swatch = Color(0xFF4A8FE0), trailColor = Color(0xFF4FA8FF),
                blurb = "Deep sky blue."
            ),
            CaterpillarSkin(
                id = "violet", displayName = "ORCHID", cost = 600,
                hueShift = 175f,
                swatch = Color(0xFFB14CFF), trailColor = Color(0xFFC96BFF),
                blurb = "Rare jungle bloom."
            ),
            CaterpillarSkin(
                id = "miami", displayName = "MIAMI", cost = 800,
                hueShift = 30f, saturation = 1.5f,
                swatch = Color(0xFF25D6C0), trailColor = Color(0xFFFF5FA2),
                effect = SkinEffect.SUNGLASSES,
                blurb = "Beach shades. Permanent holiday."
            ),
            CaterpillarSkin(
                id = "crimson", displayName = "CRIMSON", cost = 1000,
                hueShift = 235f, saturation = 1.1f,
                swatch = Color(0xFFE04B4B), trailColor = Color(0xFFFF5A5A),
                blurb = "Danger red."
            ),
            CaterpillarSkin(
                id = "coated", displayName = "LACQUER", cost = 1300,
                hueShift = 200f, saturation = 0.75f,
                swatch = Color(0xFF7E57C2), trailColor = Color(0xFFB39DDB),
                effect = SkinEffect.GLOSS,
                blurb = "Glossy coated shell."
            ),
            CaterpillarSkin(
                id = "shadow", displayName = "SHADOW", cost = 1600,
                hueShift = 1f, saturation = 0.15f,
                swatch = Color(0xFF7A7F85), trailColor = Color(0xFFBFC7CF),
                blurb = "Colour drained away."
            ),
            CaterpillarSkin(
                id = "neon", displayName = "NEON", cost = 1900,
                hueShift = 150f, saturation = 2.0f,
                swatch = Color(0xFF00E5FF), trailColor = Color(0xFF00F0FF),
                effect = SkinEffect.GLOW,
                blurb = "Radiates its own light."
            ),
            CaterpillarSkin(
                id = "frost", displayName = "GLACIER", cost = 2200,
                hueShift = 130f, saturation = 0.45f,
                swatch = Color(0xFFB3E5FC), trailColor = Color(0xFFE1F5FE),
                effect = SkinEffect.SPARKLE,
                blurb = "Iced over, catching the light."
            ),
            CaterpillarSkin(
                id = "gold", displayName = "GOLDEN", cost = 2600,
                hueShift = -48f, saturation = 1.6f,
                swatch = Color(0xFFFFC400), trailColor = Color(0xFFFFD700),
                effect = SkinEffect.GLOSS,
                blurb = "Solid gold. Obviously."
            ),
            CaterpillarSkin(
                id = "ember", displayName = "MAGMA", cost = 3200,
                hueShift = -35f, saturation = 1.9f,
                swatch = Color(0xFFFF6D00), trailColor = Color(0xFFFF7A18),
                effect = SkinEffect.EMBER,
                blurb = "Burning. Leaves embers behind."
            )
        )

        /** The default, always-owned skin. */
        val DEFAULT: CaterpillarSkin = ALL.first()

        fun byId(id: String?): CaterpillarSkin = ALL.firstOrNull { it.id == id } ?: DEFAULT

        /**
         * Standard hue-rotation colour matrix (the same maths CSS `hue-rotate` uses),
         * optionally pre-multiplied by a saturation matrix.
         */
        private fun buildFilter(degrees: Float, saturation: Float): ColorFilter {
            val rad = degrees * (Math.PI.toFloat() / 180f)
            val c = cos(rad)
            val s = sin(rad)
            // Luminance weights keep brightness stable while the hue turns.
            val lr = 0.213f
            val lg = 0.715f
            val lb = 0.072f
            val hue = ColorMatrix(
                floatArrayOf(
                    lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
                    lr + c * (-lr) + s * (0.143f), lg + c * (1 - lg) + s * (0.140f), lb + c * (-lb) + s * (-0.283f), 0f, 0f,
                    lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * (lg), lb + c * (1 - lb) + s * (lb), 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            if (saturation != 1f) {
                val sat = ColorMatrix().apply { setToSaturation(saturation) }
                sat.timesAssign(hue)
                return ColorFilter.colorMatrix(sat)
            }
            return ColorFilter.colorMatrix(hue)
        }
    }
}
