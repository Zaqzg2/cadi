package com.inventorysmartai.app.presentation.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inventorysmartai.app.domain.model.CommissionType

@Composable
fun GoalGroupCard(
    row: GroupRow,
    onTargetChange: (String) -> Unit,
    onCommissionValueChange: (String) -> Unit,
    onCommissionTypeChange: (CommissionType) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("المجموعة ${row.groupOrder}", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف المجموعة")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.targetQuantity,
                    onValueChange = onTargetChange,
                    label = { Text("الهدف") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = row.commissionValue,
                    onValueChange = onCommissionValueChange,
                    label = { Text("العمولة") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = row.commissionType == CommissionType.FIXED,
                    onClick = { onCommissionTypeChange(CommissionType.FIXED) },
                    label = { Text("مبلغ ثابت") }
                )
                FilterChip(
                    selected = row.commissionType == CommissionType.PERCENTAGE,
                    onClick = { onCommissionTypeChange(CommissionType.PERCENTAGE) },
                    label = { Text("نسبة مئوية") }
                )
            }
        }
    }
}
