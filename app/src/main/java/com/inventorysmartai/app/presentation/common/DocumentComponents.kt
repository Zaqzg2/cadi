package com.inventorysmartai.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** "Add items" (opens the picker) + "scan" — shared by sales, purchases and counting. */
@Composable
fun AddProductsBar(onAdd: () -> Unit, onBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" إضافة أصناف")
        }
        OutlinedButton(onClick = { startBarcodeScan(context, onResult = onBarcode) }) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = "مسح باركود لإضافة صنف")
            Text(" مسح")
        }
    }
}

/**
 * Shows [message] once, then calls [onShown]. When [undoLabel] is set the snackbar carries an undo
 * action; [onUndo] runs if it is pressed.
 */
@Composable
fun SnackbarEffect(
    host: SnackbarHostState,
    message: String?,
    onShown: () -> Unit,
    undoLabel: String? = null,
    onUndo: () -> Unit = {}
) {
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val result = host.showSnackbar(message = message, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) onUndo()
        onShown()
    }
}
