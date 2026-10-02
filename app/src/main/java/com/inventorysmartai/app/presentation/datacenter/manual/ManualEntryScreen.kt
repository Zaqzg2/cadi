package com.inventorysmartai.app.presentation.datacenter.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.navigation.Destination

@Composable
fun ManualEntryScreen(navController: NavController, viewModel: ManualEntryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedProductId) {
        state.savedProductId?.let { id ->
            viewModel.consumeSaved()
            // Replace this form with the new product's page so Back returns to the Data Center.
            navController.navigate(Destination.ProductDetail.createRoute(id)) {
                popUpTo(Destination.ManualEntry.route) { inclusive = true }
            }
        }
    }

    Scaffold(topBar = { AppTopBar(title = "إدخال صنف يدويًا", onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(state.name, viewModel::onName, label = { Text("اسم الصنف *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.itemNumber, viewModel::onItemNumber, label = { Text("رقم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.barcode, viewModel::onBarcode, label = { Text("الباركود") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            ChipPicker("التصنيف", state.categories.map { it.id to it.name }, state.categoryId, viewModel::onCategory)
            ChipPicker("الوحدة", state.units.map { it.id to it.name }, state.unitId, viewModel::onUnit)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("الحد الأدنى", state.minStock, viewModel::onMinStock, Modifier.weight(1f))
                NumberField("نقطة إعادة الطلب", state.reorderPoint, viewModel::onReorderPoint, Modifier.weight(1f))
            }
            NumberField("السعر الافتراضي", state.price, viewModel::onPrice, Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("له تاريخ صلاحية", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.hasExpiry, onCheckedChange = viewModel::onHasExpiry)
            }

            Text("رصيد افتتاحي (اختياري)", style = MaterialTheme.typography.titleSmall)
            ChipPicker("الفرع", state.branches.map { it.id to it.name }, state.branchId, viewModel::onBranch)
            NumberField("الكمية", state.openingQuantity, viewModel::onOpeningQuantity, Modifier.fillMaxWidth())

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                Text("حفظ الصنف")
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun ChipPicker(title: String, options: List<Pair<Long, String>>, selected: Long?, onSelect: (Long?) -> Unit) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { (id, name) ->
                FilterChip(selected = selected == id, onClick = { onSelect(if (selected == id) null else id) }, label = { Text(name) })
            }
        }
    }
}
