@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.inventorysmartai.app.core.designsystem.theme.AccentPreset
import com.inventorysmartai.app.core.designsystem.theme.AccentPresets
import com.inventorysmartai.app.core.designsystem.theme.accentPreviewColor
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.domain.model.ThemeMode
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.core.designsystem.component.AppTopBar

private data class SettingsRow(val label: String, val route: String)

private val catalogRows = listOf(
    SettingsRow("الفروع", Destination.SettingsCatalog.createRoute("branch")),
    SettingsRow("التصنيفات", Destination.SettingsCatalog.createRoute("category")),
    SettingsRow("الوحدات", Destination.SettingsCatalog.createRoute("unit"))
)

private val placeholderRows = listOf(
    SettingsRow("النسخ الاحتياطي والمزامنة", Destination.Backup.route),
    SettingsRow("تنبيهات المخزون", Destination.StockAlerts.route),
    SettingsRow("الحساب وخدمات Google", Destination.GoogleServicesStatus.route),
    SettingsRow("الصلاحيات", Destination.Permissions.route),
    SettingsRow("إعدادات الذكاء الاصطناعي", Destination.AiProviders.route)
)

@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar(title = "الإعدادات", onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionLabel("عام") }
            item {
                NavRow("معلومات التطبيق") { navController.navigate(Destination.SettingsAppInfo.route) }
            }

            item { SectionLabel("المظهر والألوان") }
            item { AppearanceCard(uiState, viewModel) }

            item { SectionLabel("بيانات أساسية") }
            items(catalogRows) { row -> NavRow(row.label) { navController.navigate(row.route) } }

            item { SectionLabel("المخزون") }
            item { NavRow("إعدادات المخزون") { navController.navigate(Destination.SettingsInventory.route) } }

            item { SectionLabel("الحساب والنسخ الاحتياطي") }
            items(placeholderRows) { row -> NavRow(row.label) { navController.navigate(row.route) } }

            item { SectionLabel("بيانات تجريبية") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "التطبيق يبدأ بقاعدة بيانات فارغة فعلياً. لمعاينة الواجهات ببيانات واقعية، يمكنك تحميل بيانات تجريبية — هذا الإجراء الوحيد الذي يضيف بيانات تجريبية، ولن يحدث تلقائياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = viewModel::onLoadDemoData, enabled = !uiState.isDemoDataLoaded, modifier = Modifier.fillMaxWidth()) {
                            Text(if (uiState.isDemoDataLoaded) "تم تحميل البيانات التجريبية" else "تحميل بيانات تجريبية للمعاينة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Text(label, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/** المظهر: الوضع (فاتح/داكن/تلقائي)، لون التطبيق (جاهز أو مخصص)، أسود حقيقي، وشكل الأرقام. التغيير ينعكس فورًا على كل الصفحات. */
@Composable
private fun AppearanceCard(ui: SettingsUiData, viewModel: SettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("الوضع", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.themeMode == ThemeMode.LIGHT, onClick = { viewModel.onThemeModeSelected(ThemeMode.LIGHT) }, label = { Text("فاتح") })
                FilterChip(selected = ui.themeMode == ThemeMode.DARK, onClick = { viewModel.onThemeModeSelected(ThemeMode.DARK) }, label = { Text("داكن") })
                FilterChip(selected = ui.themeMode == ThemeMode.SYSTEM, onClick = { viewModel.onThemeModeSelected(ThemeMode.SYSTEM) }, label = { Text("تلقائي") })
            }

            Text("لون التطبيق", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccentPresets.forEach { preset ->
                    AccentSwatch(preset, selected = ui.accentHue == preset.hue) { viewModel.onAccentHueSelected(preset.hue) }
                }
            }

            Text("أو اختر لونًا مخصصًا", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CustomHueSlider(currentHue = ui.accentHue, onHueChosen = viewModel::onAccentHueSelected)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("أسود حقيقي في الوضع الداكن")
                    Text(
                        "خلفية سوداء كاملة، تناسب شاشات AMOLED وتوفّر البطارية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = ui.pureBlackDark, onCheckedChange = viewModel::onPureBlackDarkChanged)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("استخدام الأرقام العربية الهندية (٠١٢٣)", modifier = Modifier.weight(1f))
                Switch(checked = ui.useArabicIndicDigits, onCheckedChange = viewModel::onUseArabicIndicDigitsChanged)
            }
        }
    }
}

@Composable
private fun AccentSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentPreviewColor(preset.hue))
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(preset.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** شريط درجات اللون: يُحفظ اللون عند رفع الإصبع (لا عند كل حركة) كي لا يُعاد بناء كل التطبيق أثناء السحب. */
@Composable
private fun CustomHueSlider(currentHue: Float?, onHueChosen: (Float?) -> Unit) {
    var hue by remember(currentHue) { mutableFloatStateOf(currentHue ?: 220f) }
    val spectrum = remember { (0..360 step 30).map { accentPreviewColor(it.toFloat()) } }
    // الاتجاه ثابت من اليسار لليمين هنا: التدرّج اللوني لا ينعكس مع RTL، فيجب ألّا ينعكس المنزلق معه كي يتطابق مكان اللون مع قيمته.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(spectrum))
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    onValueChangeFinished = { onHueChosen(hue) },
                    valueRange = 0f..360f
                )
            }
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(accentPreviewColor(hue)))
        }
    }
}
