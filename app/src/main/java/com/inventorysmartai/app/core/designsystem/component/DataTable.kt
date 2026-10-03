package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One column of a [DataTable].
 * - [text] renders the cell (also what an editable cell starts from).
 * - [comparator] makes the header tappable for sorting (tap again to reverse).
 * - [onEdit] makes the cell editable in place (tap, type, Done / leave the cell to commit).
 *   Return value of the callback is ignored; validate in the ViewModel and update the row list.
 */
class TableColumn<T>(
    val title: String,
    val width: Dp = 110.dp,
    val numeric: Boolean = false,
    val text: (T) -> String,
    val comparator: Comparator<T>? = null,
    val onEdit: ((row: T, newText: String) -> Unit)? = null
)

/**
 * Spreadsheet-style list: sticky header, horizontal scroll for many columns, row numbers, in-place
 * editing, sorting, delete button per row, and multi-select (long-press a row to start selecting).
 *
 * State that matters to the app is hoisted: [selectedKeys]/[onSelectionChange] and the actions. The
 * table itself only owns sort order and which cell is being edited.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> DataTable(
    rows: List<T>,
    rowKey: (T) -> Any,
    columns: List<TableColumn<T>>,
    modifier: Modifier = Modifier,
    selectedKeys: Set<Any> = emptySet(),
    onSelectionChange: ((Set<Any>) -> Unit)? = null,
    onRowClick: ((T) -> Unit)? = null,
    onDelete: ((T) -> Unit)? = null,
    showRowNumbers: Boolean = true,
    emptyText: String = "لا توجد بيانات"
) {
    var sortColumn by remember { mutableStateOf<Int?>(null) }
    var ascending by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<Pair<Any, Int>?>(null) }

    val sortedRows = remember(rows, sortColumn, ascending, columns) {
        val cmp = sortColumn?.let { columns.getOrNull(it)?.comparator }
        if (cmp == null) rows else rows.sortedWith(if (ascending) cmp else cmp.reversed())
    }

    val selectionEnabled = onSelectionChange != null
    val selecting = selectionEnabled && selectedKeys.isNotEmpty()
    val numberWidth = 40.dp
    val checkWidth = 48.dp
    val actionWidth = 48.dp
    val totalWidth = columns.fold(0.dp) { acc, c -> acc + c.width } +
        (if (showRowNumbers) numberWidth else 0.dp) +
        (if (selecting) checkWidth else 0.dp) +
        (if (onDelete != null) actionWidth else 0.dp)

    val hScroll = rememberScrollState()

    Box(modifier = modifier.horizontalScroll(hScroll)) {
        Column(modifier = Modifier.width(maxOf(totalWidth, 0.dp)).fillMaxHeight()) {
            // ---- header ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selecting) {
                    Box(Modifier.width(checkWidth), contentAlignment = Alignment.Center) {
                        Checkbox(
                            checked = sortedRows.isNotEmpty() && selectedKeys.size == sortedRows.size,
                            onCheckedChange = { all ->
                                onSelectionChange?.invoke(if (all) sortedRows.map(rowKey).toSet() else emptySet())
                            }
                        )
                    }
                }
                if (showRowNumbers) HeaderCell("#", numberWidth, null, false) {}
                columns.forEachIndexed { index, col ->
                    HeaderCell(
                        title = col.title,
                        width = col.width,
                        sortArrow = if (sortColumn == index) ascending else null,
                        clickable = col.comparator != null
                    ) {
                        if (sortColumn == index) ascending = !ascending else { sortColumn = index; ascending = true }
                    }
                }
                if (onDelete != null) Box(Modifier.width(actionWidth))
            }
            HorizontalDivider()

            if (sortedRows.isEmpty()) {
                Text(
                    emptyText,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- body ----
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(sortedRows, key = { _, r -> rowKey(r) }) { position, row ->
                    val key = rowKey(row)
                    val isSelected = key in selectedKeys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selecting) {
                                        onSelectionChange?.invoke(if (isSelected) selectedKeys - key else selectedKeys + key)
                                    } else {
                                        onRowClick?.invoke(row)
                                    }
                                },
                                onLongClick = if (selectionEnabled) {
                                    { onSelectionChange?.invoke(selectedKeys + key) }
                                } else null
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selecting) {
                            Box(Modifier.width(checkWidth), contentAlignment = Alignment.Center) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        onSelectionChange?.invoke(if (isSelected) selectedKeys - key else selectedKeys + key)
                                    }
                                )
                            }
                        }
                        if (showRowNumbers) {
                            Text(
                                (position + 1).toString(),
                                modifier = Modifier.width(numberWidth),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        columns.forEachIndexed { index, col ->
                            val isEditing = editing == (key to index)
                            if (col.onEdit != null && isEditing) {
                                EditableCell(
                                    initial = col.text(row),
                                    width = col.width,
                                    numeric = col.numeric,
                                    onCommit = { newText ->
                                        editing = null
                                        if (newText != col.text(row)) col.onEdit.invoke(row, newText)
                                    }
                                )
                            } else {
                                Text(
                                    col.text(row),
                                    modifier = Modifier
                                        .width(col.width)
                                        .padding(horizontal = 8.dp)
                                        .then(
                                            if (col.onEdit != null) Modifier.combinedClickable(
                                                onClick = { if (selecting) onSelectionChange?.invoke(if (isSelected) selectedKeys - key else selectedKeys + key) else editing = key to index },
                                                onLongClick = if (selectionEnabled) {
                                                    { onSelectionChange?.invoke(selectedKeys + key) }
                                                } else null
                                            ) else Modifier
                                        ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = if (col.numeric) TextAlign.Center else TextAlign.Start,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (col.onEdit != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (onDelete != null) {
                            IconButton(onClick = { onDelete(row) }, modifier = Modifier.width(actionWidth)) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderCell(title: String, width: Dp, sortArrow: Boolean?, clickable: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .then(if (clickable) Modifier.combinedClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (sortArrow != null) {
            Icon(
                if (sortArrow) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.height(14.dp)
            )
        }
    }
}

@Composable
private fun EditableCell(initial: String, width: Dp, numeric: Boolean, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onCommit(text.trim()) }),
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) hadFocus = true
                else if (hadFocus) onCommit(text.trim()) // leaving the cell commits (never silently drops typing)
            }
    )
}

/**
 * Bar shown while rows are selected: count, select-all, bulk delete, cancel.
 * Place it above the table; it renders nothing when [selectedCount] is 0.
 */
@Composable
fun SelectionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDeleteSelected: (() -> Unit)? = null,
    extraActions: @Composable () -> Unit = {}
) {
    if (selectedCount <= 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "إلغاء التحديد") }
        Text("$selectedCount محدد", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        extraActions()
        if (onDeleteSelected != null) {
            TextButton(onClick = onDeleteSelected) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(" حذف", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
