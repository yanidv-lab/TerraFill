package com.example.ui.skins

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * A cosmetic caterpillar colourway bought with stars earned from levels.
 *
 * Skins recolour the hero art with a hue rotation (plus an optional saturation
 * tweak) rather than a flat tint, so the original shading, highlights and outlines
 * survive - the caterpillar changes colour instead of turning into a silhouette.
 */
data class CaterpillarSkin(
    val id: String,
    val displayName: String,
    /** Stars required to unlock. The default skin is free. */
    val cost: Int,
    /** Degrees of hue rotation applied to the artwork (0 = untouched). */
    val hueShift: Float = 0f,
    /** 1 = original saturation, <1 washes out, >1 intensifies. */
    val saturation: Float = 1f,
    /** Swatch shown in the shop grid. */
    val swatch: Color
) {
    /** The filter to hand to the sprite renderer, or null for the untouched art. */
    val colorFilter: ColorFilter?
        get() = if (hueShift == 0f && saturation == 1f) null else buildFilter(hueShift, saturation)

    companion object {
        /** The full catalogue, cheapest first. [ALL].first() is always owned. */
        val ALL: List<CaterpillarSkin> = listOf(
            CaterpillarSkin("classic", "JUNGLE", 0, swatch = Color(0xFF6DBB4A)),
            CaterpillarSkin("amber", "AMBER", 4, hueShift = -60f, saturation = 1.15f, swatch = Color(0xFFE8C33A)),
            CaterpillarSkin("teal", "LAGOON", 8, hueShift = 55f, swatch = Color(0xFF3FBFA8)),
            CaterpillarSkin("azure", "AZURE", 14, hueShift = 115f, swatch = Color(0xFF4A8FE0)),
            CaterpillarSkin("violet", "ORCHID", 20, hueShift = 175f, swatch = Color(0xFFB14CFF)),
            CaterpillarSkin("crimson", "CRIMSON", 28, hueShift = 235f, saturation = 1.1f, swatch = Color(0xFFE04B4B)),
            CaterpillarSkin("shadow", "SHADOW", 36, saturation = 0.15f, hueShift = 1f, swatch = Color(0xFF7A7F85)),
            CaterpillarSkin("gold", "GOLDEN", 50, hueShift = -48f, saturation = 1.6f, swatch = Color(0xFFFFC400))
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
