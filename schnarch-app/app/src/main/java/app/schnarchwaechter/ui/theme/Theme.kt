package app.schnarchwaechter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Calm, dark colours for the bedroom. */
object AppColors {
    val Background = Color(0xFF0B0F1A)
    val Surface = Color(0xFF141A2A)
    val SurfaceHigh = Color(0xFF1C2338)
    val Primary = Color(0xFF8EA2FF)
    val Secondary = Color(0xFF6FD3C1)
    val Warning = Color(0xFFFFB86B)
    val Bad = Color(0xFFFF7A85)
    val Good = Color(0xFF7BD88F)
    val Text = Color(0xFFE6E9F2)
    val Muted = Color(0xFF8A93A8)
    /** Night mode: barely visible on black. */
    val NightText = Color(0xFF3A4157)
}

private val scheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.Background,
    primaryContainer = AppColors.SurfaceHigh,
    onPrimaryContainer = AppColors.Text,
    secondary = AppColors.Secondary,
    onSecondary = AppColors.Background,
    secondaryContainer = AppColors.SurfaceHigh,
    onSecondaryContainer = AppColors.Text,
    background = AppColors.Background,
    onBackground = AppColors.Text,
    surface = AppColors.Background,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Surface,
    onSurfaceVariant = AppColors.Muted,
    surfaceContainerLowest = AppColors.Background,
    surfaceContainerLow = AppColors.Surface,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.SurfaceHigh,
    surfaceContainerHighest = AppColors.SurfaceHigh,
    outline = Color(0xFF39425A),
    outlineVariant = Color(0xFF262E44),
    error = AppColors.Bad,
)

@Composable
fun SchnarchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
