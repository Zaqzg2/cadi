@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.inventorysmartai.app.core.common.Formatters
import java.util.TimeZone

/** نافذة اختيار تاريخ (تُرجع منتصف الليل بالتوقيت المحلي، أو null عند «مسح التاريخ»). */
@Composable
fun ExpiryPickerDialog(initial: Long?, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    val tz = TimeZone.getDefault()
    val seed = initial ?: System.currentTimeMillis()
    val state = rememberDatePickerState(initialSelectedDateMillis = seed + tz.getOffset(seed))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedDateMillis?.let { it - tz.getOffset(it) }) }) { Text("تأكيد") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onConfirm(null) }) { Text("مسح التاريخ") }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    ) { DatePicker(state = state) }
}

/** حقل تاريخ للقراءة فقط: الضغط على أي مكان فيه يفتح منتقي التاريخ. */
@Composable
fun DateField(label: String, value: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = value?.let { Formatters.formatDate(it) }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text("اضغط لاختيار التاريخ") },
        trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "اختر التاريخ") },
        interactionSource = source,
        modifier = modifier
    )
    if (open) ExpiryPickerDialog(initial = value, onConfirm = { onChange(it); open = false }, onDismiss = { open = false })
}
