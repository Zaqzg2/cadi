package com.inventorysmartai.app.data.ai.provider

/**
 * The assistant keeps ONE neutral OpenAI-format history and may continue it on a different provider after
 * a rate-limit. These two pure functions make that safe.
 */
object OpenAiMessageSanitizer {

    /**
     * Mistral only accepts tool-call ids made of exactly 9 letters/digits; Groq and OpenRouter ids look like
     * "call_q0wg". For Mistral every id is replaced by a stable 9-character one (same input id -> same output id,
     * so an assistant `tool_calls` entry and its `tool` reply still match). Other providers get the history untouched.
     */
    fun forProvider(provider: AiProviderId, messages: List<Map<String, Any?>>): List<Map<String, Any?>> {
        if (provider != AiProviderId.MISTRAL) return messages
        val remap = LinkedHashMap<String, String>()
        fun map(id: String): String = remap.getOrPut(id) { "c" + (remap.size + 1).toString().padStart(8, '0') }

        return messages.map { message ->
            when (message["role"]) {
                "assistant" -> {
                    val calls = message["tool_calls"] as? List<*> ?: return@map message
                    message + ("tool_calls" to calls.map { raw ->
                        @Suppress("UNCHECKED_CAST")
                        val call = raw as Map<String, Any?>
                        call + ("id" to map(call["id"] as? String ?: ""))
                    })
                }
                "tool" -> message + ("tool_call_id" to map(message["tool_call_id"] as? String ?: ""))
                else -> message
            }
        }
    }

    /**
     * Free tiers have small tokens-per-minute budgets, so a long chat is cut to the system prompt plus the
     * most recent [maxMessages]. The cut never starts on a `tool` reply or an assistant message whose tool
     * results were dropped (providers reject such orphans): it moves forward to the next `user` message.
     */
    fun trimHistory(messages: List<Map<String, Any?>>, maxMessages: Int): List<Map<String, Any?>> {
        if (messages.size <= maxMessages + 1) return messages
        val system = messages.takeWhile { it["role"] == "system" }
        val rest = messages.drop(system.size)
        var tail = rest.takeLast(maxMessages)
        val firstUser = tail.indexOfFirst { it["role"] == "user" }
        tail = if (firstUser >= 0) tail.drop(firstUser) else tail
        return system + tail
    }
}
