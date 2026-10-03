package com.inventorysmartai.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inventorysmartai.app.core.designsystem.component.SearchField

/**
 * Full-screen pop-up for choosing from a long list (products, customers, branches, categories...):
 *  1. optional group filter chips (e.g. product CATEGORY — choose the category first, then the item),
 *  2. a search box that also takes a scanned barcode,
 *  3. the filtered list, single- or multi-select.
 *
 * Single select: tapping a row confirms immediately.
 * Multi select: rows toggle; the footer button confirms the whole selection; "تحديد المعروض" ticks every
 * row that passes the current filter (so "category X" + "select shown" adds the whole category at once).
 * [disabledIds] are shown dimmed and cannot be picked (e.g. products already on the invoice).
 */
@Composable
fun PickerDialog(
    title: String,
    items: List<PickerItem>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    multiSelect: Boolean = false,
    groups: List<PickerGroup> = emptyList(),
    initiallySelected: Set<Long> = emptySet(),
    disabledIds: Set<Long> = emptySet(),
    disabledNote: String = "مضاف",
    allowScan: Boolean = true,
    confirmLabel: String = "إضافة"
) {
    var query by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf(initiallySelected) }
    val context = LocalContext.current

    val visible = remember(items, query, groupId) { filterPickerItems(items, query, groupId) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = "بحث بالاسم أو الرقم أو الباركود"
                    )
                    if (allowScan) {
                        IconButton(onClick = {
                            startBarcodeScan(context, onResult = { code -> query = code })
                        }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "مسح الباركود") }
                    }
                }

                if (groups.isNotEmpty()) {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(selected = groupId == null, onClick = { groupId = null }, label = { Text("الكل") })
                        }
                        items(groups, key = { it.id }) { g ->
                            FilterChip(selected = groupId == g.id, onClick = { groupId = g.id }, label = { Text(g.name) })
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${visible.size} نتيجة",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (multiSelect && visible.isNotEmpty()) {
                        val selectable = visible.map { it.id }.filter { it !in disabledIds }
                        val allShownSelected = selectable.isNotEmpty() && selectable.all { it in selected }
                        TextButton(onClick = {
                            selected = if (allShownSelected) selected - selectable.toSet() else selected + selectable
                        }) { Text(if (allShownSelected) "إلغاء تحديد المعروض" else "تحديد المعروض") }
                    }
                }
                HorizontalDivider()

                if (visible.isEmpty()) {
                    Text(
                        "لا توجد نتائج مطابقة",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visible, key = { it.id }) { item ->
                        val disabled = item.id in disabledIds
                        val isSelected = item.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !disabled) {
                                    if (multiSelect) {
                                        selected = if (isSelected) selected - item.id else selected + item.id
                                    } else {
                                        onConfirm(setOf(item.id))
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (multiSelect) {
                                Checkbox(checked = isSelected || disabled, onCheckedChange = null, enabled = !disabled)
                            } else if (item.id in initiallySelected) {
                                RadioButton(selected = true, onClick = null)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                item.subtitle?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val trailing = if (disabled) disabledNote else item.trailing
                            trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        HorizontalDivider()
                    }
                }

                if (multiSelect) {
                    Button(
                        onClick = { onConfirm(selected) },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) { Text(if (selected.isEmpty()) confirmLabel else "$confirmLabel (${selected.size})") }
                }
            }
        }
    }
}
