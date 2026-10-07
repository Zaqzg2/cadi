package com.inventorysmartai.backend.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Two jobs, both pure:
 *  1. [sanitizeIncoming] / [sanitizeTools] — what the app sends to POST /v1/ai/chat is untrusted, so the server keeps only
 *     known fields and enforces hard size limits BEFORE a single provider token is spent.
 *  2. [forProvider] / [trimHistory] — make one neutral OpenAI-format history valid for whichever provider answers.
 */
object MessageSanitizer {
    const val MAX_MESSAGES = 80
    const val MAX_TOTAL_CHARS = 160_000
    const val MAX_TOOLS = 40
    const val MAX_TOOLS_CHARS = 80_000
    const val MAX_TOKENS_CAP = 4_096

    private val ROLES = setOf("system", "user", "assistant", "tool")

    private fun reject(message: String, status: Int = 400, code: String = "BAD_REQUEST"): Nothing =
        throw RequestRejectedException(status, code, message)

    fun sanitizeIncoming(raw: JsonElement?): List<JsonObject> {
        val array = raw as? JsonArray ?: reject("الحقل messages مطلوب ويجب أن يكون قائمة")
        if (array.isEmpty()) reject("قائمة الرسائل فارغة")
        if (array.size > MAX_MESSAGES) reject("عدد الرسائل أكبر من الحد المسموح ($MAX_MESSAGES)", 413, "PAYLOAD_TOO_LARGE")

        var chars = 0
        val cleaned = array.map { element ->
            val obj = element as? JsonObject ?: reject("كل رسالة يجب أن تكون كائنًا")
            val role = (obj["role"] as? JsonPrimitive)?.contentOrNull
            if (role == null || role !in ROLES) reject("دور الرسالة غير صالح")

            val clean = LinkedHashMap<String, JsonElement>()
            clean["role"] = JsonPrimitive(role)

            when (val content = obj["content"]) {
                null, JsonNull -> clean["content"] = JsonNull
                is JsonPrimitive -> {
                    if (!content.isString) reject("محتوى الرسالة يجب أن يكون نصًا")
                    chars += content.content.length
                    clean["content"] = content
                }
                else -> reject("محتوى الرسالة يجب أن يكون نصًا")
            }

            val calls = obj["tool_calls"]
            if (calls is JsonArray) {
                chars += calls.toString().length
                clean["tool_calls"] = calls
            }
            (obj["tool_call_id"] as? JsonPrimitive)?.contentOrNull?.let { clean["tool_call_id"] = JsonPrimitive(it) }
            (obj["name"] as? JsonPrimitive)?.contentOrNull?.let { clean["name"] = JsonPrimitive(it) }
            JsonObject(clean)
        }
        if (chars > MAX_TOTAL_CHARS) reject("حجم المحادثة أكبر من الحد المسموح", 413, "PAYLOAD_TOO_LARGE")
        return cleaned
    }

    fun sanitizeTools(raw: JsonElement?): List<JsonObject> {
        if (raw == null || raw is JsonNull) return emptyList()
        val array = raw as? JsonArray ?: reject("الحقل tools يجب أن يكون قائمة")
        if (array.size > MAX_TOOLS) reject("عدد الأدوات أكبر من الحد المسموح ($MAX_TOOLS)", 413, "PAYLOAD_TOO_LARGE")
        if (array.toString().length > MAX_TOOLS_CHARS) reject("تعريف الأدوات كبير جدًا", 413, "PAYLOAD_TOO_LARGE")
        return array.map { element ->
            val tool = element as? JsonObject ?: reject("كل أداة يجب أن تكون كائنًا")
            val function = tool["function"] as? JsonObject ?: reject("تعريف الأداة بلا function")
            if ((function["name"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) reject("أداة بلا اسم")
            tool
        }
    }

    /**
     * Mistral only accepts tool-call ids of exactly 9 letters/digits; Groq and OpenRouter ids look like "call_q0wg".
     * For Mistral every id is replaced by a stable 9-character one (same input -> same output, so an assistant
     * `tool_calls` entry and its `tool` reply still match). Other providers get the history untouched.
     */
    fun forProvider(providerId: String, messages: List<JsonObject>): List<JsonObject> {
        if (providerId != "mistral") return messages
        val remap = LinkedHashMap<String, String>()
        fun mapId(id: String): String = remap.getOrPut(id) { "c" + (remap.size + 1).toString().padStart(8, '0') }

        return messages.map { message ->
            when ((message["role"] as? JsonPrimitive)?.contentOrNull) {
                "assistant" -> {
                    val calls = message["tool_calls"] as? JsonArray
                    if (calls == null) message else JsonObject(message + ("tool_calls" to JsonArray(calls.map { remapCall(it, ::mapId) })))
                }
                "tool" -> {
                    val id = (message["tool_call_id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    JsonObject(message + ("tool_call_id" to JsonPrimitive(mapId(id))))
                }
                else -> message
            }
        }
    }

    private fun remapCall(raw: JsonElement, mapId: (String) -> String): JsonElement {
        val call = raw as? JsonObject ?: return raw
        val id = (call["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return JsonObject(call + ("id" to JsonPrimitive(mapId(id))))
    }

    /**
     * Free tiers have small tokens-per-minute budgets, so a long chat is cut to the system prompt plus the most recent
     * [maxMessages]. The cut never starts on a `tool` reply or on an assistant message whose tool results were dropped
     * (providers reject such orphans): it moves forward to the next `user` message.
     */
    fun trimHistory(messages: List<JsonObject>, maxMessages: Int): List<JsonObject> {
        if (messages.size <= maxMessages + 1) return messages
        val system = messages.takeWhile { (it["role"] as? JsonPrimitive)?.contentOrNull == "system" }
        val rest = messages.drop(system.size)
        var tail = rest.takeLast(maxMessages)
        val firstUser = tail.indexOfFirst { (it["role"] as? JsonPrimitive)?.contentOrNull == "user" }
        tail = if (firstUser >= 0) tail.drop(firstUser) else tail
        return system + tail
    }
}
