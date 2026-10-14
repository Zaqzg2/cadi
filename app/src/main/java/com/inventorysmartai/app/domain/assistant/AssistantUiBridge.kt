package com.inventorysmartai.app.domain.assistant

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** One thing the assistant asks the app's UI to do. Kept tiny on purpose: every "control the app" tool boils down to opening a route. */
sealed interface UiCommand {
    /**
     * Open [route] on top of the current screen. The assistant chat stays on the back stack, so one Back press returns to the
     * conversation (and a turn that is still running keeps going — the chat's ViewModel is not destroyed by this).
     */
    data class Navigate(val route: String) : UiCommand
}

/**
 * The only channel between the assistant's tools (data layer) and the screens (presentation layer).
 *
 * Why a bridge and not a direct NavController call: a tool runs inside a coroutine on the repository's loop, long after the
 * composable that started the conversation may have left composition. Tools therefore only [post] a command; the app shell
 * (navigation/AppNavHost) is the single collector that turns commands into real navigation.
 *
 * Filters for the inventory list travel INSIDE the route (see `Destination.InventoryFiltered`), never as shared mutable state,
 * so there is no race between "the command was posted" and "the screen's ViewModel was created".
 */
@Singleton
class AssistantUiBridge @Inject constructor() {

    private val commandChannel = Channel<UiCommand>(capacity = Channel.BUFFERED)

    /** Commands in the order they were posted; each is delivered to exactly one collector (the app shell). */
    val commands: Flow<UiCommand> = commandChannel.receiveAsFlow()

    /** Returns false only when the buffer is full (the shell stopped collecting) — the tool then tells the model it could not open the screen. */
    fun post(command: UiCommand): Boolean = commandChannel.trySend(command).isSuccess

    /** A finished assistant answer shown over whatever screen the person is on, because the assistant opened that screen for them. */
    data class Notice(val id: Long, val text: String)

    private val noticeState = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = noticeState.asStateFlow()
    private val noticeIds = AtomicLong(0L)

    fun postNotice(text: String) {
        if (text.isBlank()) return
        noticeState.value = Notice(noticeIds.incrementAndGet(), text)
    }

    fun clearNotice() {
        noticeState.value = null
    }
}
