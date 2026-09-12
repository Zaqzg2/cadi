package com.inventorysmartai.app.presentation.settings.inventorysettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar

@Composable
fun InventorySettingsScreen(navController: NavController, viewModel: InventorySettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var windowText by remember { mutableStateOf(uiState.nearExpiryWindowDays.toString()) }
    var thresholdText by remember { mutableStateOf(uiState.defaultLowStockThreshold.toString()) }

    LaunchedEffect(uiState.nearExpiryWindowDays, uiState.defaultLowStockThreshold) {
        windowText = uiState.nearExpiryWindowDays.toString()
        thresholdText = uiState.defaultLowStockThreshold.toString()
    }

    Scaffold(topBar = { AppTopBar(title = "إعدادات المخزون", onBack = { navController.popBackStack() }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نافذة تنبيه قرب الانتهاء (بالأيام)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "أي صنف تنتهي صلاحيته خلال هذه المدة يُصنَّف \"قريب الانتهاء\" في المخزون والتقارير.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = windowText,
                        onValueChange = { windowText = it; it.toIntOrNull()?.let(viewModel::onNearExpiryWindowChanged) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الحد الأدنى الافتراضي للمخزون", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "يُستخدم كقيمة مقترحة عند إضافة صنف جديد لا يحدد له حداً أدنى خاصاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it; it.toDoubleOrNull()?.let(viewModel::onDefaultLowStockThresholdChanged) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }
    }
}
