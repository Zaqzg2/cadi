package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The one confirmation dialog every write/destructive AI Assistant or Google Workspace action
 * routes through (Phase 4 spec: "Show confirmation UI in Arabic" — createPurchaseRequest,
 * saveReportToDrive, createGoogleDoc, sendEmail, createCalendarEvent all use this exact
 * component), so the person learns one consistent shape for "this is about to do something real"
 * rather than a different-looking dialog per feature.
 */
@Composable
fun ActionConfirmationDialog(
    actionDescriptionAr: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "تأكيد العملية"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(actionDescriptionAr) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
