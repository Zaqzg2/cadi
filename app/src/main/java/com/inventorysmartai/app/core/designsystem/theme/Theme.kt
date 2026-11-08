package com.inventorysmartai.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

/** لون تمييز جاهز في الإعدادات. [hue] = null يعني الأزرق الأصلي للتطبيق. */
data class AccentPreset(val label: String, val hue: Float?)

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("الأزرق", null),
    AccentPreset("الفيروزي", 178f),
    AccentPreset("الأخضر", 148f),
    AccentPreset("البنفسجي", 268f),
    AccentPreset("الوردي", 322f),
    AccentPreset("الأحمر", 352f),
    AccentPreset("البرتقالي", 24f)
)

/** لون المعاينة (دائرة الاختيار في الإعدادات) للون التمييز [hue]؛ null = الأزرق الأصلي. */
fun accentPreviewColor(hue: Float?): Color = if (hue == null) LightColors.primary else readableOnWhite(hue)

private fun hsv(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))

/** أفتح درجة من اللون [hue] يبقى النص الأبيض فوقها مقروءًا (تباين WCAG ≥ 4.5 تقريبًا). */
private fun readableOnWhite(hue: Float, saturation: Float = 0.88f): Color {
    var value = 0.92f
    var color = hsv(hue, saturation, value)
    while (color.luminance() > 0.18f && value > 0.25f) {
        value -= 0.03f
        color = hsv(hue, saturation, value)
    }
    return color
}

/**
 * يبني مخطط ألوان كاملًا من درجة لون واحدة: الأساسي والثانوي وحاويات السطوح والخلفية كلها بنفس الأسلوب المستعمل في الأزرق
 * الافتراضي (أسطح مائلة قليلًا للون). ألوان المعنى (الخطأ، التحذير، حالات المخزون) لا تتغيّر أبدًا.
 */
private fun accentScheme(base: ColorScheme, hue: Float, dark: Boolean): ColorScheme {
    val second = (hue + 315f) % 360f
    return if (!dark) base.copy(
        primary = readableOnWhite(hue), onPrimary = Color.White,
        primaryContainer = hsv(hue, 0.14f, 1f), onPrimaryContainer = hsv(hue, 0.95f, 0.22f),
        secondary = readableOnWhite(second, 0.8f), onSecondary = Color.White,
        secondaryContainer = hsv(second, 0.15f, 0.97f), onSecondaryContainer = hsv(second, 0.9f, 0.2f),
        background = hsv(hue, 0.036f, 0.984f), onBackground = hsv(hue, 0.67f, 0.2f),
        surface = hsv(hue, 0.036f, 0.984f), onSurface = hsv(hue, 0.67f, 0.2f),
        surfaceVariant = hsv(hue, 0.073f, 0.96f), onSurfaceVariant = hsv(hue, 0.37f, 0.46f),
        outline = hsv(hue, 0.22f, 0.68f), outlineVariant = hsv(hue, 0.1f, 0.92f),
        surfaceContainer = hsv(hue, 0.06f, 0.97f), surfaceContainerHigh = hsv(hue, 0.075f, 0.957f),
        surfaceContainerHighest = Color.White, surfaceContainerLow = hsv(hue, 0.02f, 0.99f),
        surfaceContainerLowest = Color.White
    ) else base.copy(
        primary = hsv(hue, 0.38f, 1f), onPrimary = hsv(hue, 1f, 0.3f),
        primaryContainer = hsv(hue, 0.9f, 0.62f), onPrimaryContainer = hsv(hue, 0.14f, 1f),
        secondary = hsv(second, 0.45f, 0.88f), onSecondary = hsv(second, 1f, 0.22f),
        secondaryContainer = hsv(second, 1f, 0.31f), onSecondaryContainer = hsv(second, 0.14f, 0.96f),
        background = hsv(hue, 0.59f, 0.114f), onBackground = hsv(hue, 0.07f, 0.957f),
        surface = hsv(hue, 0.59f, 0.114f), onSurface = hsv(hue, 0.07f, 0.957f),
        surfaceVariant = hsv(hue, 0.41f, 0.28f), onSurfaceVariant = hsv(hue, 0.15f, 0.85f),
        outline = hsv(hue, 0.21f, 0.66f), outlineVariant = hsv(hue, 0.41f, 0.28f),
        surfaceContainer = hsv(hue, 0.49f, 0.17f), surfaceContainerHigh = hsv(hue, 0.45f, 0.22f),
        surfaceContainerHighest = hsv(hue, 0.43f, 0.275f), surfaceContainerLow = hsv(hue, 0.53f, 0.15f),
        surfaceContainerLowest = hsv(hue, 0.61f, 0.09f)
    )
}

/** «أسود حقيقي» للوضع الداكن: خلفية سوداء بالكامل (توفّر بطارية شاشات AMOLED) وبطاقات رمادية داكنة جدًا فوقها. */
private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF101010), surfaceContainerHigh = Color(0xFF181818), surfaceContainerHighest = Color(0xFF212121)
)

/**
 * App theme root. The app is Arabic-only, so layout direction is forced to RTL here rather
 * than left to follow the device locale — this is deliberate, not a fallback.
 *
 * [accentHue] درجة لون التمييز (0..360)، أو null للأزرق الأصلي. [pureBlack] يُطبَّق في الوضع الداكن فقط.
 */
@Composable
fun InventorySmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentHue: Float? = null,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(darkTheme, accentHue, pureBlack) {
        val base = if (darkTheme) DarkColors else LightColors
        val accented = if (accentHue == null) base else accentScheme(base, accentHue, darkTheme)
        if (darkTheme && pureBlack) accented.pureBlack() else accented
    }
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
