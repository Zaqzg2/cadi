package com.inventorysmartai.app.core.common

/**
 * Lenient number parsing for text fields. Accepts Arabic-Indic (٠-٩) and Persian (۰-۹) digits and the
 * Arabic decimal/thousands separators and comma as the decimal point, so what a user types on an Arabic
 * keyboard is read as typed. Returns null for blank/invalid/non-finite input (callers decide whether
 * that is an error — the old `toDoubleOrNull() ?: 0.0` pattern silently turned "٣" into 0).
 */
fun String.toDecimalOrNull(): Double? {
    val t = trim()
    if (t.isEmpty()) return null
    val normalized = buildString(t.length) {
        for (ch in t) append(
            when (ch) {
                in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                '\u066B', '\u066C', ',', '\u060C' -> '.'
                else -> ch
            }
        )
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}
