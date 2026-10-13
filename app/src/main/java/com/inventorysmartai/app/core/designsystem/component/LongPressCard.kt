@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

class CardAction(val label: String, val run: () -> Unit)

/**
 * بطاقة قابلة للضغط، والضغط المطوّل عليها يفتح قائمة إجراءات (فتح / تعديل / حذف) مع اهتزاز خفيف.
 * تُستخدم داخل SwipeActionRow فيجتمع السحب (تعديل/حذف) والضغط المطوّل (قائمة) على نفس البطاقة.
 */
@Composable
fun LongPressCard(
    onClick: () -> Unit,
    actions: List<CardAction>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onClick,
                onLongClick = if (actions.isEmpty()) null else ({
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    open = true
                })
            ),
            content = content
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { open = false; a.run() }) }
        }
    }
}
