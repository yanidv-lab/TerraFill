package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.skins.CaterpillarSkin
import com.example.ui.skins.SkinEffect
import com.example.ui.theme.*

/**
 * SKINS shop: spend stars earned from levels on caterpillar colourways. Each card
 * previews the actual hero art recoloured exactly as it will look in play.
 */
@Composable
fun ShopScreen(
    availableStars: Int,
    ownedSkins: Set<String>,
    selectedSkin: String,
    onBuy: (CaterpillarSkin) -> Unit,
    onEquip: (CaterpillarSkin) -> Unit,
    onBack: () -> Unit,
    extraLives: Int = 0,
    extraLifeCost: Int = 100,
    maxExtraLives: Int = 3,
    onBuyExtraLife: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val portrait = rememberSafeImage(R.drawable.sprite_caterpillar)

    JungleBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 460.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SubScreenHeader(title = "SKINS", onBack = onBack)

            // Star balance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.5.dp, NeonYellow.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(18.dp))
                Text(
                    text = "$availableStars STARS TO SPEND",
                    color = NeonYellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Earn stars by finishing levels well.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            // Consumable: spare lives, carried between levels until a run is lost
            ExtraLifeCard(
                held = extraLives,
                cost = extraLifeCost,
                cap = maxExtraLives,
                affordable = availableStars >= extraLifeCost,
                onBuy = onBuyExtraLife
            )

            // Two-column grid of skins
            val skins = CaterpillarSkin.ALL
            for (row in skins.chunked(2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (skin in row) {
                        val owned = skin.id == CaterpillarSkin.DEFAULT.id || skin.id in ownedSkins
                        SkinCard(
                            skin = skin,
                            owned = owned,
                            equipped = skin.id == selectedSkin,
                            affordable = availableStars >= skin.cost,
                            portrait = portrait,
                            onClick = { if (owned) onEquip(skin) else onBuy(skin) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Keep the last odd card at half width
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Shop card for the spare-life consumable: shows how many are banked, what one
 * costs, and how they behave (they survive level completions but are lost when a
 * level is lost).
 */
@Composable
private fun ExtraLifeCard(
    held: Int,
    cost: Int,
    cap: Int,
    affordable: Boolean,
    onBuy: () -> Unit
) {
    val full = held >= cap
    val enabled = !full && affordable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F0A14).copy(alpha = 0.9f))
            .border(2.dp, NeonMagenta.copy(alpha = if (enabled) 0.85f else 0.35f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onBuy)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = NeonMagenta,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "  EXTRA LIFE",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = if (full) "MAX $held/$cap" else "HELD $held/$cap",
                color = if (full) NeonYellow else Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = "Start every level with an extra life. Kept when you clear a level, lost when a level beats you.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (enabled) Icons.Default.Star else Icons.Default.Lock,
                contentDescription = null,
                tint = if (enabled) NeonYellow else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = when {
                    full -> "  BANK FULL"
                    affordable -> "  $cost  ·  TAP TO BUY"
                    else -> "  $cost  ·  NOT ENOUGH STARS"
                },
                color = if (enabled) NeonYellow else Color.White.copy(alpha = 0.35f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SkinCard(
    skin: CaterpillarSkin,
    owned: Boolean,
    equipped: Boolean,
    affordable: Boolean,
    portrait: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = when {
        equipped -> NeonYellow
        owned -> LeafGreen.copy(alpha = 0.7f)
        affordable -> skin.swatch.copy(alpha = 0.8f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A1F0E).copy(alpha = 0.88f))
            .border(2.dp, border, RoundedCornerShape(16.dp))
            .clickable(enabled = owned || affordable, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag("skin_${skin.id}")
    ) {
        // Live preview of the recoloured hero art
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(52.dp)) {
            if (portrait != null) {
                Image(
                    bitmap = portrait,
                    contentDescription = skin.displayName,
                    colorFilter = skin.colorFilter,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .alpha(if (owned) 1f else 0.45f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(skin.swatch)
                )
            }
        }

        Text(
            text = skin.displayName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        // Special skins advertise their flourish
        if (skin.effect != SkinEffect.NONE) {
            Text(
                text = "✦ ${skin.effect.name}",
                color = skin.swatch,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
        if (skin.blurb.isNotEmpty()) {
            Text(
                text = skin.blurb,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        when {
            equipped -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = NeonYellow, modifier = Modifier.size(13.dp))
                Text(
                    text = " EQUIPPED",
                    color = NeonYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            owned -> Text(
                text = "TAP TO WEAR",
                color = Color(0xFF8CD44F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (affordable) Icons.Default.Star else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (affordable) NeonYellow else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = " ${skin.cost}",
                    color = if (affordable) NeonYellow else Color.White.copy(alpha = 0.35f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
