package com.inventorysmartai.app.data.ai.provider

import com.squareup.moshi.Moshi

/**
 * Minimal JSON helpers over Moshi's built-in `Any` adapter, so the provider layer needs no org.json
 * (which is a stub on the plain JVM and would make this code untestable). Objects are `Map<String, Any?>`,
 * arrays are `List<Any?>`, numbers come back as `Double`.
 */
object AiJson {
    private val moshi: Moshi = Moshi.Builder().build()
    private val anyAdapter = moshi.adapter(Any::class.java)

    fun toJson(value: Any?): String = anyAdapter.toJson(value)

    fun parse(json: String): Any? = anyAdapter.fromJson(json)

    @Suppress("UNCHECKED_CAST")
    fun parseObject(json: String): Map<String, Any?>? = parse(json) as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun Any?.asObject(): Map<String, Any?>? = this as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun Any?.asList(): List<Any?>? = this as? List<Any?>

    /**
     * Models sometimes wrap the JSON in a Markdown fence, prepend a sentence, or (reasoning models) emit a
     * `<think>` block first. Returns the outermost `{...}` slice, or null if there is none.
     */
    fun extractObjectText(text: String): String? {
        val withoutThink = text.replace(Regex("<think>[\\s\\S]*?</think>"), "")
        val start = withoutThink.indexOf('{')
        val end = withoutThink.lastIndexOf('}')
        return if (start in 0 until end) withoutThink.substring(start, end + 1) else null
    }
}
