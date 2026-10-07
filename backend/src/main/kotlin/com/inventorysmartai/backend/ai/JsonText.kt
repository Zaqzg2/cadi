package com.inventorysmartai.backend.ai

/** Small, pure helpers for the text models sometimes wrap around the JSON they were asked for. */
object JsonText {

    /** Removes a ```json ... ``` fence. Text without a fence is only trimmed. */
    fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val afterFirstLine = trimmed.substringAfter('\n', "")
        return afterFirstLine.removeSuffix("```").trim()
    }

    /**
     * The first balanced `{ ... }` in [raw] (braces inside JSON strings are ignored), or null when there is none
     * or it never closes. Lets a reply such as "Here you go: {...} hope it helps" still be parsed.
     */
    fun extractObjectText(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else if (c == '"') {
                inString = true
            } else if (c == '{') {
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0) return raw.substring(start, i + 1)
            }
        }
        return null
    }
}
