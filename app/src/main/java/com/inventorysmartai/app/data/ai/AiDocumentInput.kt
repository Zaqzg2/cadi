package com.inventorysmartai.app.data.ai

/**
 * File-handling helpers for the AI extraction path. Pure Kotlin (no Android, no Firebase), so they are
 * unit-tested on the plain JVM.
 */
object AiDocumentInput {

    /** What the bytes actually ARE, from magic numbers; null when unrecognised. */
    fun sniffMimeType(bytes: ByteArray): String? {
        fun startsWith(vararg signature: Int): Boolean =
            bytes.size >= signature.size && signature.indices.all { (bytes[it].toInt() and 0xFF) == signature[it] }

        return when {
            startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
            startsWith(0x25, 0x50, 0x44, 0x46) -> "application/pdf" // "%PDF"
            // "RIFF" + 4 size bytes + "WEBP"
            bytes.size >= 12 &&
                startsWith(0x52, 0x49, 0x46, 0x46) &&
                (bytes[8].toInt() and 0xFF) == 0x57 &&
                (bytes[9].toInt() and 0xFF) == 0x45 &&
                (bytes[10].toInt() and 0xFF) == 0x42 &&
                (bytes[11].toInt() and 0xFF) == 0x50 -> "image/webp"
            else -> null
        }
    }

    /**
     * The MIME type to send. What the bytes are beats what the picker claimed (a document picker often
     * reports `application/octet-stream`, or nothing, for a perfectly good JPEG); when the bytes are not
     * recognised, fall back to the declared type, and finally to PDF — the same default the backend route used.
     */
    fun resolveMimeType(bytes: ByteArray, declared: String?): String {
        val sniffed = sniffMimeType(bytes)
        if (sniffed != null) return sniffed
        val d = declared?.trim()?.lowercase().orEmpty()
        return if (d.isEmpty() || d == "application/octet-stream") "application/pdf" else d
    }

    /** Models occasionally wrap JSON in a Markdown fence even when asked not to; peel it off. */
    fun stripCodeFence(text: String): String {
        val t = text.trim()
        if (!t.startsWith("```")) return t
        return t.removePrefix("```").removePrefix("json").removePrefix("JSON").removeSuffix("```").trim()
    }
}
