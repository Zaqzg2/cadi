package com.inventorysmartai.backend.ai

import com.inventorysmartai.backend.config.AiProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Test double for the network: records every call and lets each test decide the provider's answer. */
class FakeTransport(
    private val ocrText: String = "نص مقروء",
    private val onChat: suspend (AiProviderConfig, JsonObject) -> JsonObject
) : AiTransport {
    val chatCalls = mutableListOf<String>()
    val chatBodies = mutableListOf<JsonObject>()
    val ocrCalls = mutableListOf<String>()

    override suspend fun chat(provider: AiProviderConfig, body: JsonObject): JsonObject {
        chatCalls += provider.id
        chatBodies += body
        return onChat(provider, body)
    }

    override suspend fun ocr(provider: AiProviderConfig, mimeType: String, bytes: ByteArray): String {
        ocrCalls += provider.id
        return ocrText
    }
}

fun testProvider(id: String, ocr: Boolean = false): AiProviderConfig =
    AiProviderConfig(id, id.replaceFirstChar { it.uppercase() }, "https://$id.test/", "key-$id", "text-$id", "vision-$id", if (ocr) "ocr-model" else null)

/** A provider answer carrying [content] as the assistant text. */
fun chatResponse(content: String): JsonObject =
    Json.parseToJsonElement("""{"choices":[{"message":{"role":"assistant","content":${JsonPrimitive(content)}},"finish_reason":"stop"}]}""").jsonObject

fun userMessage(text: String): JsonObject = JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive(text)))
