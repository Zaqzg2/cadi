package com.inventorysmartai.app.data.ai.provider

/**
 * The assistant keeps ONE neutral OpenAI-format history and may continue it on a different provider after
 * a rate-limit. These pure functions make that safe.
 */
object OpenAiMessageSanitizer {

    /**
     * What Google documents for a function call that Gemini did not produce itself (history carried over from another
     * provider): the signature check is skipped. Gemini 3 answers 400 "missing a thought_signature" without it.
     */
    const val GEMINI_SKIP_SIGNATURE = "skip_thought_signature_validator"

    fun forProvider(provider: AiProviderId, messages: List<Map<String, Any?>>): List<Map<String, Any?>> = when (provider) {
        AiProviderId.MISTRAL -> forMistral(stripExtraContent(messages))
        AiProviderId.GEMINI -> forGemini(messages)
        else -> stripExtraContent(messages)
    }

    /**
     * Mistral only accepts tool-call ids made of exactly 9 letters/digits; Groq and OpenRouter ids look like
     * "call_q0wg". For Mistral every id is replaced by a stable 9-character one (same input id -> same output id,
     * so an assistant `tool_calls` entry and its `tool` reply still match). Other providers get the history untouched.
     */
    private fun forMistral(messages: List<Map<String, Any?>>): List<Map<String, Any?>> {
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
     * Gemini 3 hands back a "thought signature" with its function calls (`extra_content.google.thought_signature`) and wants
     * it echoed on the next request. A call that came from another provider has none, so the documented skip value is used;
     * and an assistant turn that only calls tools is sent with `content: null` instead of an empty string (an empty text part is
     * rejected by Gemini).
     */
    private fun forGemini(messages: List<Map<String, Any?>>): List<Map<String, Any?>> {
        var changed = false
        val out = messages.map { message ->
            if (message["role"] != "assistant") return@map message
            val calls = message["tool_calls"] as? List<*> ?: return@map message
            if (calls.isEmpty()) return@map message

            var result = message
            if ((message["content"] as? String).isNullOrBlank() && message["content"] != null) {
                result = result + ("content" to null)
                changed = true
            }
            @Suppress("UNCHECKED_CAST")
            val typed = calls as List<Map<String, Any?>>
            if (typed.none { signatureOf(it) != null }) {
                val skip = mapOf("google" to mapOf("thought_signature" to GEMINI_SKIP_SIGNATURE))
                result = result + ("tool_calls" to typed.mapIndexed { index, call -> if (index == 0) call + ("extra_content" to skip) else call })
                changed = true
            }
            result
        }
        return if (changed) out else messages
    }

    /** Providers other than Gemini reject unknown fields, so a signature stored from an earlier Gemini turn is dropped for them. */
    private fun stripExtraContent(messages: List<Map<String, Any?>>): List<Map<String, Any?>> {
        if (messages.none { carriesExtraContent(it) }) return messages
        return messages.map { message ->
            val calls = message["tool_calls"] as? List<*> ?: return@map message
            message + ("tool_calls" to calls.map { raw ->
                @Suppress("UNCHECKED_CAST")
                val call = raw as Map<String, Any?>
                call - "extra_content"
            })
        }
    }

    private fun carriesExtraContent(message: Map<String, Any?>): Boolean =
        (message["tool_calls"] as? List<*>)?.any { (it as? Map<*, *>)?.containsKey("extra_content") == true } == true

    private fun signatureOf(call: Map<String, Any?>): String? {
        val google = (call["extra_content"] as? Map<*, *>)?.get("google") as? Map<*, *>
        return (google?.get("thought_signature") as? String)?.takeIf { it.isNotBlank() }
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
