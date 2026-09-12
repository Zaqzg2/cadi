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
 * A deliberate emerald + warm gold palette rather than Material's default purple —
 * emerald reads as "stock / growth / trustworthy" for a business inventory tool, and the
 * warm gold secondary keeps it from feeling like generic SaaS-card-kit green.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF0C6B54),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFAEF2D6),
    onPrimaryContainer = Color(0xFF002013),
    secondary = Color(0xFF6B5D2C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E1A7),
    onSecondaryContainer = Color(0xFF221B00),
    tertiary = Color(0xFF7A5238),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBC7),
    onTertiaryContainer = Color(0xFF2E1505),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
    surfaceContainer = Color(0xFFEFF2ED),
    surfaceContainerHigh = Color(0xFFE9ECE6),
    surfaceContainerHighest = Color(0xFFE3E6E0),
    surfaceContainerLow = Color(0xFFF5F7F1),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90D6B7),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFFAEF2D6),
    secondary = Color(0xFFD6C58D),
    onSecondary = Color(0xFF3A2F04),
    secondaryContainer = Color(0xFF534619),
    onSecondaryContainer = Color(0xFFF3E1A7),
    tertiary = Color(0xFFEBB69A),
    onTertiary = Color(0xFF48260F),
    tertiaryContainer = Color(0xFF613B23),
    onTertiaryContainer = Color(0xFFFFDBC7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE1E3DD),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE1E3DD),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF899289),
    outlineVariant = Color(0xFF404943),
    surfaceContainer = Color(0xFF1C201B),
    surfaceContainerHigh = Color(0xFF262B25),
    surfaceContainerHighest = Color(0xFF313530),
    surfaceContainerLow = Color(0xFF181C17),
    surfaceContainerLowest = Color(0xFF0B0F0A)
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
    available = Color(0xFF2E7D32),
    low = Color(0xFFB8860B),
    zero = Color(0xFF757575),
    nearExpiry = Color(0xFFE65100),
    expired = Color(0xFFC62828)
)

private val DarkStatusColors = InventoryStatusColors(
    available = Color(0xFF81C995),
    low = Color(0xFFE0C468),
    zero = Color(0xFFAFAFAF),
    nearExpiry = Color(0xFFFFAB70),
    expired = Color(0xFFFF8A80)
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
