package com.inventorysmartai.app.presentation.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A read-only field that shows the current choice and opens a [PickerDialog] (search + group filter)
 * when tapped. Replaces the horizontal chip rows that were unusable past ~10 options.
 *
 * [selectedId] null = nothing chosen; a clear (x) button appears when [clearable].
 */
@Composable
fun SelectField(
    label: String,
    items: List<PickerItem>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<PickerGroup> = emptyList(),
    clearable: Boolean = true,
    placeholder: String = "اختر",
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    val selectedTitle = items.firstOrNull { it.id == selectedId }?.title.orEmpty()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // A read-only OutlinedTextField swallows clicks, so open on the press itself.
    LaunchedEffect(pressed) { if (pressed && enabled) open = true }

    OutlinedTextField(
        value = selectedTitle,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        interactionSource = interaction,
        modifier = modifier,
        trailingIcon = {
            if (enabled && clearable && selectedId != null) {
                IconButton(onClick = { onSelected(null) }) { Icon(Icons.Filled.Clear, contentDescription = "مسح الاختيار") }
            } else {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
    )

    if (open) {
        PickerDialog(
            title = label,
            items = items,
            groups = groups,
            allowScan = false,
            initiallySelected = selectedId?.let { setOf(it) } ?: emptySet(),
            onConfirm = { ids -> onSelected(ids.firstOrNull()); open = false },
            onDismiss = { open = false }
        )
    }
}
