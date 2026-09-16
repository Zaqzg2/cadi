package com.inventorysmartai.app.domain.matching

/**
 * Normalizes a product name so that spelling variants which should be treated as "the same name"
 * for EXACT matching collapse to one canonical string — e.g. "حليب السعودية" and "حليب السعوديه"
 * (the spec's own example of a ta-marbuta/ha variant that should compare equal).
 *
 * This is intentionally simple and deterministic — no fuzzy scoring, no Levenshtein distance.
 * Phase 2 explicitly forbids fuzzy matching; this only feeds the "exact normalized name"
 * strategy, so anything not covered here still correctly falls through to NewProduct/Unresolved
 * rather than being force-matched.
 */
object ArabicTextNormalizer {

    private const val TASHKEEL = "\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0670"
    private const val TATWEEL = "\u0640"

    fun normalize(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return s

        // Strip diacritics (tashkeel) and the tatweel elongation character.
        s = s.filter { c -> TASHKEEL.indexOf(c) < 0 }
        s = s.replace(TATWEEL.toRegex(), "")

        // Normalize alef variants (أ/إ/آ/ٱ) to bare alef.
        s = s.replace(Regex("[\u0623\u0625\u0622\u0671]"), "\u0627")
        // Normalize ta-marbuta to ha (matches the spec's own "السعودية" vs "السعوديه" example).
        s = s.replace('\u0629', '\u0647')
        // Normalize alif-maqsura to ya.
        s = s.replace('\u0649', '\u064A')

        // Collapse internal whitespace and lowercase any Latin characters.
        s = s.replace(Regex("\\s+"), " ").trim().lowercase()

        return s
    }
}
