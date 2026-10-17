package com.inventorysmartai.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/*
 * Royal blue on a cool white — the look of the design mockups: a confident blue for actions and the selected tab, white cards on a
 * pale blue-grey page, a teal second colour for chart bars, and red / amber / green kept for MEANING (low stock, nearly expired,
 * in stock), never for decoration.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5EFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF00164F),
    secondary = Color(0xFF0FA89B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3F4F0),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFFE8780A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE8D0),
    onTertiaryContainer = Color(0xFF2E1500),
    error = Color(0xFFE53935),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE4E2),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF2F5FB),
    onBackground = Color(0xFF111B33),
    surface = Color(0xFFF2F5FB),
    onSurface = Color(0xFF111B33),
    surfaceVariant = Color(0xFFE3E9F5),
    onSurfaceVariant = Color(0xFF4A5875),
    outline = Color(0xFF8794AD),
    outlineVariant = Color(0xFFD3DBEA),
    surfaceContainer = Color(0xFFE9EEF8),
    surfaceContainerHigh = Color(0xFFE2E8F4),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FD),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB9FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF0F3DA6),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF6FD9CD),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFFD3F4F0),
    tertiary = Color(0xFFFFB877),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3B00),
    onTertiaryContainer = Color(0xFFFFE8D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C111D),
    onBackground = Color(0xFFE3E8F4),
    surface = Color(0xFF0C111D),
    onSurface = Color(0xFFE3E8F4),
    surfaceVariant = Color(0xFF2A3347),
    onSurfaceVariant = Color(0xFFB9C3D9),
    outline = Color(0xFF8590A8),
    outlineVariant = Color(0xFF2A3347),
    surfaceContainer = Color(0xFF161C2B),
    surfaceContainerHigh = Color(0xFF1F2638),
    surfaceContainerHighest = Color(0xFF283046),
    surfaceContainerLow = Color(0xFF121826),
    surfaceContainerLowest = Color(0xFF090D17)
)

/** Status colors sit outside the standard M3 roles — Material has no built-in "expired" role. */
data class InventoryStatusColors(
    val available: Color,
    val low: Color,
    val zero: Color,
    val nearExpiry: Color,
    val expired: Color
)

private val LightStatusColors = InventoryStatusColors(
    available = Color(0xFF1E9E5A),
    low = Color(0xFFE53935),
    zero = Color(0xFF6B7280),
    nearExpiry = Color(0xFFF59E0B),
    expired = Color(0xFFD84315)
)

private val DarkStatusColors = InventoryStatusColors(
    available = Color(0xFF6FD39B),
    low = Color(0xFFFF8A80),
    zero = Color(0xFFB0B7C3),
    nearExpiry = Color(0xFFFFC857),
    expired = Color(0xFFFF9E80)
)

val LocalInventoryStatusColors = staticCompositionLocalOf { LightStatusColors }

/**
 * App theme root. The app is Arabic-only, so layout direction is forced to RTL here rather
 * than left to follow the device locale — this is deliberate, not a fallback.
 */
@Composable
fun InventorySmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(
        LocalInventoryStatusColors provides statusColors,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = InventorySmartTypography,
            shapes = InventorySmartShapes,
            content = content
        )
    }
}
