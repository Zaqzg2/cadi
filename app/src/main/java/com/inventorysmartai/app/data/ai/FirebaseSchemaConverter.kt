package com.inventorysmartai.app.data.ai

import com.google.firebase.ai.type.Schema

/**
 * The only place the extraction schemas touch the Firebase SDK: one line per node type, using the
 * `Schema` builders documented for Firebase AI Logic (Kotlin). Parameter names (`description`,
 * `nullable`, `optionalProperties`) are the SDK's own.
 */
internal fun AiSchemaNode.toFirebaseSchema(): Schema = when (this) {
    is AiObject -> Schema.obj(
        properties.mapValues { (_, node) -> node.toFirebaseSchema() },
        optionalProperties = optionalProperties,
        description = description
    )
    is AiArray -> Schema.array(items.toFirebaseSchema(), description = description)
    is AiString -> Schema.string(description = description, nullable = nullable)
    is AiInteger -> Schema.integer(description = description, nullable = nullable)
    is AiNumber -> Schema.double(description = description, nullable = nullable)
    is AiBoolean -> Schema.boolean(description = description, nullable = nullable)
}
