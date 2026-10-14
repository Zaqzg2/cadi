package com.inventorysmartai.app.navigation

import androidx.lifecycle.ViewModel
import com.inventorysmartai.app.domain.assistant.AssistantUiBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Gives [AppNavHost] access to the assistant's [AssistantUiBridge] (a composable cannot be constructor-injected). It holds no
 * state of its own: the bridge is a singleton shared with the assistant's tools.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    val bridge: AssistantUiBridge
) : ViewModel()
