package app.mohakash.apod.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The Mohakash theme — dark only.
 */
private val MohakashColorScheme = darkColorScheme(
    primary = MohakashColors.Accent,
    onPrimary = MohakashColors.OnAccent,
    primaryContainer = MohakashColors.Accent,
    onPrimaryContainer = MohakashColors.OnAccent,
    secondary = MohakashColors.Accent,
    onSecondary = MohakashColors.OnAccent,
    background = MohakashColors.Background,
    onBackground = MohakashColors.Text,
    surface = MohakashColors.Surface,
    onSurface = MohakashColors.Text,
    surfaceVariant = MohakashColors.SurfaceStrong,
    onSurfaceVariant = MohakashColors.TextBody,
    surfaceContainer = MohakashColors.Surface,
    surfaceContainerHigh = MohakashColors.SurfaceStrong,
    surfaceContainerHighest = MohakashColors.SurfaceStrong,
    outline = MohakashColors.Border,
    outlineVariant = MohakashColors.BorderStrong,
    error = MohakashColors.Accent,
    onError = MohakashColors.OnAccent,
    scrim = MohakashColors.Scrim,
)

@Composable
fun MohakashTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // The app is dark only: the system icons stay light.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = MohakashColorScheme,
        typography = MohakashTypography,
        content = content,
    )
}
