package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.data.ai.AiArray
import com.inventorysmartai.app.data.ai.AiBoolean
import com.inventorysmartai.app.data.ai.AiInteger
import com.inventorysmartai.app.data.ai.AiNumber
import com.inventorysmartai.app.data.ai.AiObject
import com.inventorysmartai.app.data.ai.AiSchemaNode
import com.inventorysmartai.app.data.ai.AiString

/**
 * The [AiSchemaNode] tree is turned here into plain JSON Schema,
 * which is then pasted into the prompt. Groq / Mistral / OpenRouter free models all honour
 * `response_format: json_object` but not every model honours a strict schema, so the schema is given to
 * the model as text and the answer is validated afterwards by Moshi + the deterministic import pipeline.
 */
object JsonSchemaConverter {

    fun toJsonSchema(node: AiSchemaNode): Map<String, Any?> = when (node) {
        is AiObject -> buildMap {
            put("type", "object")
            node.description?.let { put("description", it) }
            put("properties", node.properties.mapValues { (_, v) -> toJsonSchema(v) })
            if (node.required.isNotEmpty()) put("required", node.properties.keys.filter { it in node.required })
        }
        is AiArray -> buildMap {
            put("type", "array")
            node.description?.let { put("description", it) }
            put("items", toJsonSchema(node.items))
        }
        is AiString -> scalar("string", node.nullable, node.description)
        is AiInteger -> scalar("integer", node.nullable, node.description)
        is AiNumber -> scalar("number", node.nullable, node.description)
        is AiBoolean -> scalar("boolean", node.nullable, node.description)
    }

    private fun scalar(type: String, nullable: Boolean, description: String?): Map<String, Any?> = buildMap {
        put("type", if (nullable) listOf(type, "null") else type)
        description?.let { put("description", it) }
    }

    fun toSchemaText(node: AiSchemaNode): String = AiJson.toJson(toJsonSchema(node))
}
