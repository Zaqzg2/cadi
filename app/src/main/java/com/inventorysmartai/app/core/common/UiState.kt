package com.inventorysmartai.app.core.common

/**
 * A single, immutable state wrapper used by every screen in the app so loading / success /
 * empty / error are always handled the same way (see StateContent in the designsystem package).
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Empty(val message: String? = null) : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}

fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data
