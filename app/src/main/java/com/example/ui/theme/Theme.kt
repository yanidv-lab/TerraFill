package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonMagenta,
    secondary = NeonCyan,
    tertiary = NeonGreen,
    background = ArcadeBgDark,
    surface = ArcadeCardDark,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
  )

private val LightColorScheme = DarkColorScheme // Always use retro dark theme for the true arcade experience

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default to preserve the customized neon retro-arcade aesthetics
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      else -> DarkColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

/**
 * Renders high-quality pixel horizontal CRT scanlines over any container to achieve a perfect retro arcade terminal feel.
 */
fun Modifier.retroArcadeOverlay(scanlineOpacity: Float = 0.12f): Modifier = this.drawWithContent {
    drawContent()
    
    val lineSpacing = 3.dp.toPx()
    val strokeWidth = 1.dp.toPx()
    val height = size.height
    val width = size.width
    
    var y = 0f
    while (y < height) {
        drawLine(
            color = Color.Black.copy(alpha = scanlineOpacity),
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = strokeWidth
        )
        y += lineSpacing
    }
}
