package com.inventorysmartai.app.data.ai

/**
 * A tiny, SDK-independent description of the JSON shape Gemini is asked to return.
 *
 * Why not build Firebase's `Schema` objects directly? Everything here is plain Kotlin, so the shape
 * rules (what is required, what may be null) are unit-tested on the plain JVM. Only
 * [toFirebaseSchema] (FirebaseSchemaConverter.kt) touches the Firebase SDK, and it is a one-line
 * mapping per node type.
 */
sealed interface AiSchemaNode {
    val description: String?
}

/**
 * [required] names the properties that must be present. Note the direction of the default: JSON
 * Schema (and the backend schemas this was ported from) treats a property as OPTIONAL unless listed
 * as required, while Firebase AI Logic's `Schema.obj` treats every property as REQUIRED unless listed
 * in `optionalProperties` — so the conversion goes through [optionalProperties], never `required`.
 */
data class AiObject(
    val properties: Map<String, AiSchemaNode>,
    val required: Set<String> = emptySet(),
    override val description: String? = null
) : AiSchemaNode {
    init {
        val unknown = required.filter { it !in properties }
        require(unknown.isEmpty()) { "required names properties that do not exist: $unknown" }
    }

    /** Every property that is NOT required, in declaration order. */
    val optionalProperties: List<String> get() = properties.keys.filter { it !in required }
}

data class AiArray(
    val items: AiSchemaNode,
    override val description: String? = null
) : AiSchemaNode

data class AiString(
    val nullable: Boolean = false,
    override val description: String? = null
) : AiSchemaNode

data class AiInteger(
    val nullable: Boolean = false,
    override val description: String? = null
) : AiSchemaNode

data class AiNumber(
    val nullable: Boolean = false,
    override val description: String? = null
) : AiSchemaNode

data class AiBoolean(
    val nullable: Boolean = false,
    override val description: String? = null
) : AiSchemaNode
