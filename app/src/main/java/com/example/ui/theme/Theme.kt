package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val SafeVoiceLightColorScheme = lightColorScheme(
    primary = SafeBluePrimary,
    onPrimary = Color.White,
    primaryContainer = SafeBlueContainer,
    onPrimaryContainer = SafeBlueOnContainer,
    secondary = TextSecondary,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantSlate,
    onSecondaryContainer = TextPrimary,
    background = BackgroundSlate,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantSlate,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSlate,
    outlineVariant = OutlineSlate,
    error = StatusErrorText,
    onError = Color.White,
    errorContainer = StatusErrorBg,
    onErrorContainer = StatusErrorText
)

val SafeVoiceShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun SafeVoiceTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = SafeVoiceLightColorScheme,
        typography = Typography,
        shapes = SafeVoiceShapes,
        content = content
    )
}
