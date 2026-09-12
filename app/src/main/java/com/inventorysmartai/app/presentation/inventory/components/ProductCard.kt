package com.inventorysmartai.app.presentation.inventory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.StatusChip
import com.inventorysmartai.app.domain.model.ProductStockSummary

/** رقم الصنف / الباركود / اسم الصنف / التصنيف / الوحدة / الكمية / الحد الأدنى / نقطة إعادة
 *  الطلب / الحالة — every field the spec asked for on the inventory card. */
@Composable
fun ProductCard(summary: ProductStockSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(summary.product.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(summary.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue("رقم الصنف", summary.product.itemNumber)
                summary.product.barcode?.let { LabeledValue("الباركود", it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                summary.product.categoryName?.let { LabeledValue("التصنيف", it) }
                summary.product.unitName?.let { LabeledValue("الوحدة", it) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledValue("الكمية", Formatters.formatNumber(summary.totalQuantity))
                LabeledValue("الحد الأدنى", Formatters.formatNumber(summary.product.minStock))
                LabeledValue("نقطة إعادة الطلب", Formatters.formatNumber(summary.product.reorderPoint))
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
