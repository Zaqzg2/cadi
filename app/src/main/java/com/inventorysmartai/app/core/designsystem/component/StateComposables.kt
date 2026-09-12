package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inventorysmartai.app.core.common.UiState

/**
 * The one place every screen routes its [UiState] through, so loading / empty / error never
 * drift into three different looks across the app.
 */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    emptyContent: @Composable () -> Unit = { EmptyStateView() },
    content: @Composable (T) -> Unit
) {
    when (state) {
        is UiState.Loading -> LoadingStateView(modifier)
        is UiState.Success -> content(state.data)
        is UiState.Empty -> emptyContent()
        is UiState.Error -> ErrorStateView(message = state.message, modifier = modifier, onRetry = onRetry)
    }
}

@Composable
fun LoadingStateView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Empty states are written as an invitation to act, not just "no data" — see [description]
 * for the actionable hint each call site provides.
 */
@Composable
fun EmptyStateView(
    modifier: Modifier = Modifier,
    title: String = "لا توجد بيانات بعد",
    description: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorStateView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("تعذّر إتمام العملية", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("إعادة المحاولة") }
        }
    }
}
