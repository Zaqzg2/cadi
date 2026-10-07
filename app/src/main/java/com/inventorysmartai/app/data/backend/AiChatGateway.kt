package com.inventorysmartai.app.data.backend

import com.inventorysmartai.app.data.ai.provider.AiChatResult

/**
 * One assistant turn: "here is the conversation and the tools, what does the model say next?". Everything above this
 * line (the tool loop, confirmations, local data) is identical whichever implementation answers:
 *  - [BackendChatGateway]  — the app's own server (default; the provider keys live there)
 *  - [DirectChatGateway]   — the keys saved on this phone, called directly
 *  - [RoutingChatGateway]  — picks between the two
 */
interface AiChatGateway {
    suspend fun chat(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>,
        temperature: Double,
        maxTokens: Int
    ): AiChatResult
}
