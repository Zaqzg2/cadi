package com.inventorysmartai.app.presentation.datacenter.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.presentation.common.PickerItem
import com.inventorysmartai.app.presentation.common.ScanField
import com.inventorysmartai.app.presentation.common.SelectField

/**
 * Create a product, or (when opened with a productId) edit one.
 * Create mode also offers "حفظ وإضافة آخر": the form clears the name/codes/quantity but keeps the
 * category, unit, branch and limits, and puts the cursor back on the name — for entering many
 * similar products quickly.
 */
@Composable
fun ManualEntryScreen(navController: NavController, viewModel: ManualEntryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(state.savedProductId) {
        state.savedProductId?.let { id ->
            viewModel.consumeSaved()
            // Replace this form with the new product's page so Back returns to the Data Center.
            navController.navigate(Destination.ProductDetail.createRoute(id)) {
                popUpTo(Destination.ManualEntry.route) { inclusive = true }
            }
        }
    }
    LaunchedEffect(state.editSaved) {
        if (state.editSaved) {
            viewModel.consumeSaved()
            navController.popBackStack()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    // After "save & add another" the cursor goes back to the name field.
    LaunchedEffect(state.resetTick) {
        if (state.resetTick > 0) runCatching { nameFocus.requestFocus() }
    }

    val title = when {
        state.isEdit -> "تعديل الصنف"
        state.savedCount > 0 -> "إدخال صنف (${state.savedCount} محفوظ)"
        else -> "إدخال صنف يدويًا"
    }

    Scaffold(
        topBar = { AppTopBar(title = title, onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp)) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                state.name, viewModel::onName,
                label = { Text("اسم الصنف *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
            )
            OutlinedTextField(state.itemNumber, viewModel::onItemNumber, label = { Text("رقم الصنف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ScanField(state.barcode, viewModel::onBarcode, label = "الباركود", modifier = Modifier.fillMaxWidth())

            SelectField(
                label = "التصنيف",
                items = state.categories.map { PickerItem(it.id, it.name) },
                selectedId = state.categoryId,
                onSelected = viewModel::onCategory,
                modifier = Modifier.fillMaxWidth()
            )
            SelectField(
                label = "الوحدة",
                items = state.units.map { PickerItem(it.id, it.name, subtitle = it.symbol) },
                selectedId = state.unitId,
                onSelected = viewModel::onUnit,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("الحد الأدنى", state.minStock, viewModel::onMinStock, Modifier.weight(1f))
                NumberField("نقطة إعادة الطلب", state.reorderPoint, viewModel::onReorderPoint, Modifier.weight(1f))
            }
            NumberField("السعر الافتراضي", state.price, viewModel::onPrice, Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("له تاريخ صلاحية", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.hasExpiry, onCheckedChange = viewModel::onHasExpiry)
            }

            if (!state.isEdit) {
                Text("رصيد افتتاحي (اختياري)", style = MaterialTheme.typography.titleSmall)
                SelectField(
                    label = "الفرع",
                    items = state.branches.map { PickerItem(it.id, it.name) },
                    selectedId = state.branchId,
                    onSelected = viewModel::onBranch,
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField("الكمية", state.openingQuantity, viewModel::onOpeningQuantity, Modifier.fillMaxWidth())
            } else {
                Text(
                    "تعديل الكميات يتم من الجرد أو من حركات المخزون، حتى لا تتغير الأرصدة بالخطأ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                Text(if (state.isEdit) "حفظ التعديلات" else "حفظ الصنف")
            }
            if (!state.isEdit) {
                OutlinedButton(onClick = viewModel::saveAndAddAnother, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                    Text("حفظ وإضافة آخر")
                }
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
