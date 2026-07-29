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
        /**
         * The full catalogue, cheapest first. [ALL].first() is the free default.
         *
         * Trimmed to five premium colourways, each carrying a distinct [SkinEffect] -
         * one skin per non-NONE effect value, so no two purchasable skins compete for
         * the same flourish. Every effect renders both on the body ([drawSkinEffect])
         * and along the trail itself ([drawTrailEffect]), so the identity shows up in
         * the territory being claimed, not only on the caterpillar.
         */
        val ALL: List<CaterpillarSkin> = listOf(
            CaterpillarSkin(
                id = "classic", displayName = "JUNGLE", cost = 0,
                swatch = Color(0xFF6DBB4A), trailColor = Color(0xFF7CFF4A),
                blurb = "The original."
            ),
            CaterpillarSkin(
                id = "miami", displayName = "MIAMI", cost = 1000,
                hueShift = 30f, saturation = 1.5f,
                swatch = Color(0xFF25D6C0), trailColor = Color(0xFFFF5FA2),
                effect = SkinEffect.SUNGLASSES,
                blurb = "Beach shades. Permanent holiday."
            ),
            CaterpillarSkin(
                id = "frost", displayName = "GLACIER", cost = 1500,
                hueShift = 130f, saturation = 0.45f,
                swatch = Color(0xFFB3E5FC), trailColor = Color(0xFFE1F5FE),
                effect = SkinEffect.SPARKLE,
                blurb = "Iced over, catching the light."
            ),
            CaterpillarSkin(
                id = "neon", displayName = "NEON", cost = 2200,
                hueShift = 150f, saturation = 2.0f,
                swatch = Color(0xFF00E5FF), trailColor = Color(0xFF00F0FF),
                effect = SkinEffect.GLOW,
                blurb = "Radiates its own light."
            ),
            CaterpillarSkin(
                id = "gold", displayName = "GOLDEN", cost = 3200,
                hueShift = -48f, saturation = 1.6f,
                swatch = Color(0xFFFFC400), trailColor = Color(0xFFFFD700),
                effect = SkinEffect.GLOSS,
                blurb = "Solid gold. Obviously."
            ),
            CaterpillarSkin(
                id = "ember", displayName = "MAGMA", cost = 4600,
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
         * Prices of skins that used to be purchasable and have since been dropped
         * from [ALL] (the catalogue trim from 13 colourways down to 5 premium ones).
         * A player who bought one of these still has its id in their owned set, and
         * its cost must keep counting against their star balance - otherwise the
         * stars they spent on it silently reappear as spendable currency the moment
         * the catalogue changes underneath them.
         */
        private val LEGACY_COSTS: Map<String, Int> = mapOf(
            "amber" to 50,
            "teal" to 140,
            "azure" to 320,
            "violet" to 600,
            "crimson" to 1000,
            "coated" to 1300,
            "shadow" to 1600
        )

        /** The price an owned skin id cost, whether or not it is still purchasable. */
        fun costFor(id: String): Int = ALL.firstOrNull { it.id == id }?.cost ?: LEGACY_COSTS[id] ?: 0

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
