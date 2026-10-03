package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Swipe a card row: one direction deletes (red), the other edits (blue) when [onEdit] is given.
 * The row always snaps back — the action is carried out through the callbacks, so state lives in the
 * ViewModel (use [showUndo] to offer "تراجع" before a delete is really committed).
 *
 * Use for CARD lists only. Do not wrap rows of a horizontally scrolling table: the swipe fights the
 * table's own horizontal scroll (tables get a delete button column + long-press selection instead).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> onEdit?.invoke()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // never leave the row dismissed; the caller removes/updates the item
        }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = onEdit != null,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val isDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color = when (state.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
            }
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (state.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        if (isDelete) Icons.Filled.Delete else Icons.Filled.Edit,
                        contentDescription = if (isDelete) "حذف" else "تعديل"
                    )
                }
            }
        }
    ) {
        content()
    }
}

/**
 * Shows [message] with a "تراجع" action. Returns true when the user did NOT undo (the delete should
 * now be committed), false when they pressed undo (restore the item).
 *
 * Pattern: hide the item immediately (a `pendingDeleteIds` set in the ViewModel), call this, then
 * either really delete (true) or clear the pending id (false). Nothing is deleted from the database
 * until the snackbar has finished, so "undo" never has to re-insert anything.
 */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String = "تراجع"): Boolean {
    currentSnackbarData?.dismiss()
    val result = showSnackbar(message = message, actionLabel = undoLabel, withDismissAction = false, duration = SnackbarDuration.Long)
    return result != SnackbarResult.ActionPerformed
}
