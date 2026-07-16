package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Loads an image resource, tolerating corrupt/undecodable data: returns null instead
 * of throwing. Art assets have repeatedly been corrupted by external tooling, and a
 * broken picture must degrade the visuals - never crash the game.
 */
@Composable
internal fun rememberSafeImage(resId: Int, sampleSize: Int = 1): ImageBitmap? {
    val context = LocalContext.current
    return remember(resId) {
        runCatching {
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            android.graphics.BitmapFactory.decodeResource(context.resources, resId, options)?.asImageBitmap()
        }.getOrNull()
    }
}

/**
 * Draws [image] centered at [center], scaled so its longer side spans [targetLongSide]
 * pixels (aspect preserved), optionally rotated and/or mirrored. A lightweight sibling
 * of GameScreen's in-game sprite renderer for menus and decorative scenes.
 */
internal fun DrawScope.drawSpriteCentered(
    image: ImageBitmap,
    center: Offset,
    targetLongSide: Float,
    rotationDeg: Float = 0f,
    flipX: Boolean = false
) {
    val aspect = image.width.toFloat() / image.height.toFloat()
    val dw: Float
    val dh: Float
    if (aspect >= 1f) {
        dw = targetLongSide; dh = targetLongSide / aspect
    } else {
        dw = targetLongSide * aspect; dh = targetLongSide
    }
    withTransform({
        rotate(rotationDeg, center)
        if (flipX) scale(-1f, 1f, center)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(
                (center.x - dw / 2f).roundToInt(),
                (center.y - dh / 2f).roundToInt()
            ),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            filterQuality = FilterQuality.High
        )
    }
}
