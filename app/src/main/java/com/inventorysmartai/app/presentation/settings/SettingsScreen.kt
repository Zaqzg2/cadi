@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
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

private data class SettingsRow(val label: String, val route: String)

private val catalogRows = listOf(
    SettingsRow("الفروع", Destination.SettingsCatalog.createRoute("branch")),
    SettingsRow("التصنيفات", Destination.SettingsCatalog.createRoute("category")),
    SettingsRow("الوحدات", Destination.SettingsCatalog.createRoute("unit"))
)

private val placeholderRows = listOf(
    SettingsRow("النسخ الاحتياطي", Destination.SettingsPlaceholder.createRoute("backup")),
    SettingsRow("المزامنة", Destination.SettingsPlaceholder.createRoute("sync")),
    SettingsRow("الحساب وخدمات Google", Destination.GoogleServicesStatus.route),
    SettingsRow("الصلاحيات", Destination.SettingsPlaceholder.createRoute("permissions")),
    SettingsRow("إعدادات الذكاء الاصطناعي", Destination.SettingsPlaceholder.createRoute("ai_settings"))
)

@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("الإعدادات") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionLabel("عام") }
            item {
                NavRow("معلومات التطبيق") { navController.navigate(Destination.SettingsAppInfo.route) }
            }

            item { SectionLabel("المظهر") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("المظهر", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = uiState.themeMode == ThemeMode.LIGHT, onClick = { viewModel.onThemeModeSelected(ThemeMode.LIGHT) }, label = { Text("فاتح") })
                            FilterChip(selected = uiState.themeMode == ThemeMode.DARK, onClick = { viewModel.onThemeModeSelected(ThemeMode.DARK) }, label = { Text("داكن") })
                            FilterChip(selected = uiState.themeMode == ThemeMode.SYSTEM, onClick = { viewModel.onThemeModeSelected(ThemeMode.SYSTEM) }, label = { Text("تلقائي") })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("استخدام الأرقام العربية الهندية (٠١٢٣)")
                            Switch(checked = uiState.useArabicIndicDigits, onCheckedChange = viewModel::onUseArabicIndicDigitsChanged)
                        }
                    }
                }
            }

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
