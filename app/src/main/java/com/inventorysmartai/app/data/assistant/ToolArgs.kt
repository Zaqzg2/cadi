package com.inventorysmartai.app.data.assistant

/**
 * Typed reads of a tool call's arguments. The arguments arrive as the model wrote them, parsed by Moshi's generic adapter, so
 * every number is a Double and a model may send "10" for 10 or "true" for true — these helpers accept both and never throw:
 * a missing or unreadable value is simply null and the tool decides whether that is an error.
 */
internal object ToolArgs {

    fun string(args: Map<String, Any?>, key: String): String? = (args[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    fun double(args: Map<String, Any?>, key: String): Double? = when (val value = args[key]) {
        is Number -> value.toDouble()
        is String -> value.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }?.takeIf { !it.isNaN() && !it.isInfinite() }

    fun long(args: Map<String, Any?>, key: String): Long? = double(args, key)?.toLong()

    fun int(args: Map<String, Any?>, key: String): Int? = double(args, key)?.toInt()

    fun bool(args: Map<String, Any?>, key: String): Boolean? = when (val value = args[key]) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }
        else -> null
    }

    fun list(args: Map<String, Any?>, key: String): List<Any?>? = args[key] as? List<Any?>

    @Suppress("UNCHECKED_CAST")
    fun obj(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    /** The standard "you forgot an argument" answer the model can act on. */
    fun missing(fields: String): Map<String, Any?> =
        mapOf("error" to "missing_argument", "message" to "Required arguments missing: $fields")

    fun invalid(message: String): Map<String, Any?> = mapOf("error" to "invalid_argument", "message" to message)
}
