package com.inventorysmartai.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.inventorysmartai.app.core.designsystem.theme.ColorMath
import com.inventorysmartai.app.domain.model.ColorOverrides

private class ColorPreset(val label: String, val argb: Int)

private fun preset(label: String, hex: Long) = ColorPreset(label, hex.toInt())

private val backgroundPresets = listOf(
    preset("أبيض", 0xFFFFFFFF),
    preset("رمادي فاتح", 0xFFF1F1F1),
    preset("كريمي", 0xFFFBF5E6),
    preset("أزرق هادئ", 0xFFE6EFFC),
    preset("نعناعي", 0xFFE7F5EE),
    preset("لافندر", 0xFFF0EBFA),
    preset("كحلي", 0xFF0B1730),
    preset("فحمي", 0xFF181A1F),
    preset("أخضر داكن", 0xFF0E201A),
    preset("بنفسجي داكن", 0xFF1A1426),
    preset("أسود", 0xFF000000)
)

private val textPresets = listOf(
    preset("أسود", 0xFF000000),
    preset("كحلي", 0xFF111B33),
    preset("رمادي داكن", 0xFF37474F),
    preset("بني", 0xFF3E2723),
    preset("أبيض", 0xFFFFFFFF),
    preset("أبيض ناعم", 0xFFE8EAF0),
    preset("كريمي", 0xFFFFF3D6),
    preset("أخضر فاتح", 0xFFCDEFD8)
)

private val cardPresets = listOf(
    preset("أبيض", 0xFFFFFFFF),
    preset("رمادي", 0xFFF3F3F3),
    preset("أصفر فاتح", 0xFFFFF6DA),
    preset("أزرق فاتح", 0xFFE0EEFD),
    preset("أخضر فاتح", 0xFFE4F4E7),
    preset("وردي فاتح", 0xFFFBE3EB),
    preset("كحلي", 0xFF1B2A41),
    preset("رمادي داكن", 0xFF262626),
    preset("أخضر داكن", 0xFF1B3128),
    preset("بنفسجي", 0xFF2C2240)
)

/**
 * ألوان الخلفية والخط والبطاقات: لكل منها «تلقائي» (يتبع الثيم) وألوان جاهزة ومنتقي لون مخصص.
 * التغيير يُحفظ ويُطبَّق على كل الصفحات فورًا. تظهر تحذيرًا إن صار الخط غير مقروء فوق الخلفية أو البطاقة.
 */
@Composable
internal fun ColorCustomizationSection(
    colors: ColorOverrides,
    onBackground: (Int?) -> Unit,
    onText: (Int?) -> Unit,
    onCard: (Int?) -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ألوان الصفحات والبطاقات", style = MaterialTheme.typography.titleSmall)
        ColorChoiceRow("لون الخلفية", colors.background, backgroundPresets, onBackground)
        ColorChoiceRow("لون الخط", colors.text, textPresets, onText)
        ColorChoiceRow("لون البطاقات", colors.card, cardPresets, onCard)

        // الألوان الفعلية بعد التطبيق (ما اشتُقّ تلقائيًا يُحسب أيضًا).
        val scheme = MaterialTheme.colorScheme
        val text = scheme.onSurface.toArgb()
        val low = minOf(
            ColorMath.contrast(text, scheme.background.toArgb()),
            ColorMath.contrast(text, scheme.surfaceContainerHighest.toArgb())
        )
        if (!colors.isDefault && low < 3.0) {
            Text(
                "لون الخط قريب من لون الخلفية أو البطاقات وقد يصعب قراءته. جرّب لونًا أوضح.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!colors.isDefault) {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("إعادة الألوان الافتراضية") }
        }
    }
}

@Composable
private fun ColorChoiceRow(title: String, current: Int?, presets: List<ColorPreset>, onChoose: (Int?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val isCustom = current != null && presets.none { it.argb == current }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                current?.let { ColorMath.toHex(it) } ?: "تلقائي",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Swatch(label = "تلقائي", color = null, selected = current == null) { onChoose(null) }
            presets.forEach { p -> Swatch(label = p.label, color = p.argb, selected = current == p.argb) { onChoose(p.argb) } }
            Swatch(label = "مخصص", color = if (isCustom) current else null, selected = isCustom, custom = true) { picking = true }
        }
    }

    if (picking) {
        ColorPickerDialog(
            title = title,
            initial = current ?: presets.first().argb,
            onDismiss = { picking = false },
            onPick = { picking = false; onChoose(it) }
        )
    }
}

@Composable
private fun Swatch(label: String, color: Int?, selected: Boolean, custom: Boolean = false, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val ring = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 4.dp)
    ) {
        val fill: Modifier = when {
            color != null -> Modifier.background(Color(color))
            custom -> Modifier.background(
                Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
            )
            else -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .then(fill)
                .border(if (selected) 3.dp else 1.dp, if (selected) ring else outline.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val mark = color?.let { Color(ColorMath.bestTextFor(it)) } ?: MaterialTheme.colorScheme.onSurface
            when {
                selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = mark, modifier = Modifier.size(20.dp))
                custom -> Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                color == null -> Icon(Icons.Filled.FormatColorReset, contentDescription = null, tint = mark, modifier = Modifier.size(18.dp))
                else -> Unit
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** منتقي لون: شرائح الدرجة والتشبع والسطوع، أو كتابة الرمز السداسي مباشرة (#RRGGBB)، مع معاينة حيّة. */
@Composable
private fun ColorPickerDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(ColorMath.toHex(initial)) }

    fun hsvToArgb(h: Float, s: Float, v: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    fun setHsv(h: Float, s: Float, v: Float) {
        hue = h; sat = s; value = v
        hex = ColorMath.toHex(hsvToArgb(h, s, v))
    }

    val chosen = ColorMath.parseHex(hex) ?: hsvToArgb(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(chosen))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("نص تجريبي", color = Color(ColorMath.bestTextFor(chosen)), textAlign = TextAlign.Center)
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        ColorMath.parseHex(text)?.let { c ->
                            val tmp = FloatArray(3)
                            android.graphics.Color.colorToHSV(c, tmp)
                            hue = tmp[0]; sat = tmp[1]; value = tmp[2]
                        }
                    },
                    label = { Text("الرمز (#RRGGBB)") },
                    singleLine = true,
                    isError = ColorMath.parseHex(hex) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
                // التدرّجات اللونية لا تنعكس مع RTL، فالمنزلقات تبقى LTR كي يطابق مكان اللون قيمته.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        GradientSlider(
                            hue, 0f..360f,
                            (0..360 step 60).map { Color(hsvToArgb(it.toFloat().coerceAtMost(359.9f), 1f, 1f)) }
                        ) { setHsv(it, sat, value) }
                        GradientSlider(
                            sat, 0f..1f,
                            listOf(Color(hsvToArgb(hue, 0f, value)), Color(hsvToArgb(hue, 1f, value)))
                        ) { setHsv(hue, it, value) }
                        GradientSlider(
                            value, 0f..1f,
                            listOf(Color.Black, Color(hsvToArgb(hue, sat, 1f)))
                        ) { setHsv(hue, sat, it) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen) }, enabled = ColorMath.parseHex(hex) != null) { Text("تطبيق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun GradientSlider(value: Float, range: ClosedFloatingPointRange<Float>, colors: List<Color>, onChange: (Float) -> Unit) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brush.horizontalGradient(colors)))
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
