package com.inventorysmartai.app.data.ai

/**
 * A tiny, SDK-independent description of the JSON shape the AI model is asked to return (used by the direct-provider
 * mode, which embeds it in the prompt as a JSON Schema — see provider/JsonSchemaConverter.kt). Everything here is plain
 * Kotlin, so the shape rules (what is required, what may be null) are unit-tested on the plain JVM.
 */
sealed interface AiSchemaNode {
    val description: String?
}

/**
 * [required] names the properties that must be present. Note the direction of the default: JSON
 * Schema (and the backend schemas this was ported from) treats a property as OPTIONAL unless listed
 * as required; this tree stores the OPTIONAL names ([optionalProperties]) instead, and the conversion to JSON Schema
 * derives `required` from them.
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
