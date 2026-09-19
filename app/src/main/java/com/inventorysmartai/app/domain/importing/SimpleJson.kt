package com.inventorysmartai.app.domain.importing

/**
 * A deliberately tiny, dependency-free JSON reader/writer for the flat string-keyed maps and
 * string lists the import pipeline stores (ImportRowEntity.rawData/normalizedData/warningsJson,
 * ImportMappingTemplate.mappingJson). NOT a general-purpose JSON library: no nested objects, no
 * arrays of objects, no numbers/booleans — every value is a JSON string (or JSON null).
 *
 * That scope limit is deliberate, not an oversight, for two reasons:
 *  1. This project has no JSON dependency at all yet, and adding one (Gson/Moshi/
 *     kotlinx.serialization) is exactly the kind of extra version-pinning surface
 *     android-kotlin-build-compatibility.md warns against taking on without being able to
 *     verify it against a real build.
 *  2. org.json.JSONObject/JSONArray are Android SDK stub classes that throw
 *     "RuntimeException: not mocked" when called from plain JVM unit tests (this project's
 *     app/build.gradle.kts has no `testOptions.unitTests.isReturnDefaultValues` and no
 *     Robolectric) — a hand-rolled encoder needs neither, so the whole pipeline (header
 *     detection, mapping, normalization, validation) stays unit-testable on the JVM.
 *
 * Arabic and other non-ASCII text is written through unescaped (valid JSON; only `"`, `\` and
 * control characters must be escaped), so stored rows stay human-readable if inspected directly.
 */
object SimpleJson {

    fun encodeMap(map: Map<String, String?>): String {
        if (map.isEmpty()) return "{}"
        return map.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
            "${encodeString(k)}:${if (v == null) "null" else encodeString(v)}"
        }
    }

    fun encodeList(items: List<String>): String {
        if (items.isEmpty()) return "[]"
        return items.joinToString(",", prefix = "[", postfix = "]") { encodeString(it) }
    }

    /** Malformed input returns an empty map rather than throwing — a corrupted blob should never
     *  crash the review screen, just show as "no data" for that row. */
    fun decodeMap(json: String?): Map<String, String> = runCatching {
        if (json.isNullOrBlank()) return emptyMap()
        val trimmed = json.trim()
        if (trimmed.length < 2 || trimmed.first() != '{' || trimmed.last() != '}') return emptyMap()
        val body = trimmed.substring(1, trimmed.length - 1)
        if (body.isBlank()) return emptyMap()

        val result = LinkedHashMap<String, String>()
        var i = 0
        while (i < body.length) {
            i = skipWhitespaceAndComma(body, i)
            if (i >= body.length) break
            val (key, afterKey) = readJsonString(body, i)
            i = skipWhitespace(body, afterKey)
            check(i < body.length && body[i] == ':') { "malformed JSON object (missing ':')" }
            i = skipWhitespace(body, i + 1)
            if (body.startsWith("null", i)) {
                i += 4
            } else {
                val (value, afterValue) = readJsonString(body, i)
                result[key] = value
                i = afterValue
            }
        }
        result
    }.getOrElse { emptyMap() }

    fun decodeList(json: String?): List<String> = runCatching {
        if (json.isNullOrBlank()) return emptyList()
        val trimmed = json.trim()
        if (trimmed.length < 2 || trimmed.first() != '[' || trimmed.last() != ']') return emptyList()
        val body = trimmed.substring(1, trimmed.length - 1)
        if (body.isBlank()) return emptyList()

        val result = mutableListOf<String>()
        var i = 0
        while (i < body.length) {
            i = skipWhitespaceAndComma(body, i)
            if (i >= body.length) break
            val (value, afterValue) = readJsonString(body, i)
            result += value
            i = afterValue
        }
        result
    }.getOrElse { emptyList() }

    private fun skipWhitespace(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun skipWhitespaceAndComma(s: String, from: Int): Int {
        var i = skipWhitespace(s, from)
        if (i < s.length && s[i] == ',') {
            i = skipWhitespace(s, i + 1)
        }
        return i
    }

    private fun readJsonString(s: String, from: Int): Pair<String, Int> {
        var i = skipWhitespace(s, from)
        check(i < s.length && s[i] == '"') { "expected a JSON string at index $i" }
        i++
        val sb = StringBuilder()
        while (i < s.length && s[i] != '"') {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        val hex = s.substring(i + 2, i + 6)
                        sb.append(hex.toInt(16).toChar())
                        i += 6
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c); i++
            }
        }
        check(i < s.length) { "unterminated JSON string" }
        i++ // closing quote
        return sb.toString() to i
    }

    private fun encodeString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
