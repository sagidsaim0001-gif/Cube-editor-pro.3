package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val CubeDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = TextPrimary,
    primaryContainer = ElectricViolet.copy(alpha = 0.22f),
    onPrimaryContainer = ElectricVioletLight,
    secondary = CyberCyan,
    onSecondary = VoidBlack,
    secondaryContainer = CyberCyan.copy(alpha = 0.16f),
    onSecondaryContainer = CyberCyan,
    tertiary = NeonMagenta,
    onTertiary = TextPrimary,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    outlineVariant = SurfaceBorder.copy(alpha = 0.5f),
    error = CrimsonDanger,
    onError = TextPrimary
)

private val CubeAmoledColorScheme = CubeDarkColorScheme.copy(
    background = AmoledBlack,
    surface = VoidBlack,
    surfaceVariant = ObsidianSurface
)

val CubeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun CubeEditorTheme(
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (amoledBlack) CubeAmoledColorScheme else CubeDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CubeEditorTypography,
        shapes = CubeShapes,
        content = content
    )
}
