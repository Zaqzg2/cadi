package com.inventorysmartai.app.domain.matching

/**
 * Normalizes Arabic (and mixed Arabic/Latin) text so that spelling variants which should be
 * treated as "the same value" — for EXACT product-name matching (Phase 2) and for import-time
 * header/value comparison (Phase 3) — collapse to one canonical string. E.g. "حليب السعودية" and
 * "حليب السعوديه" (the spec's own example of a ta-marbuta/ha variant that should compare equal),
 * or "٥" and "5" (an Arabic-Indic digit variant that should compare equal).
 *
 * This is intentionally simple and deterministic — no fuzzy scoring, no Levenshtein distance.
 * Phase 2 explicitly forbids fuzzy matching; this only feeds the "exact normalized name"
 * strategy, so anything not covered here still correctly falls through to NewProduct/Unresolved
 * rather than being force-matched.
 *
 * Phase 3 note: [normalize] never deletes digits or letters, only diacritics/tatweel/punctuation
 * and whitespace — deleting a punctuation mark outright can accidentally fuse two words together
 * ("دجاج-مشوي" -> "دجاجمشوي"), so punctuation is replaced with a space instead and the usual
 * whitespace-collapse pass cleans that up. Callers that need the raw value untouched (the import
 * pipeline's "store both rawValue and normalizedValue, never overwrite the original" rule) must
 * keep their own copy of the input — this function only ever returns the normalized form.
 */
object ArabicTextNormalizer {

    private const val TASHKEEL = "\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0670"
    private const val TATWEEL = "\u0640"

    // Arabic-Indic (٠-٩, U+0660-U+0669) and Extended Arabic-Indic / Persian (۰-۹, U+06F0-U+06F9)
    // digits, in order, mapping onto ASCII '0'-'9'.
    private const val ARABIC_INDIC_DIGITS = "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"
    private const val PERSIAN_DIGITS = "\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9"

    // Punctuation that shows up in real spreadsheet headers/values and should not create a
    // "different value" on its own — Arabic comma/semicolon/question mark plus common ASCII
    // punctuation. Deliberately excludes characters that are meaningful inside numbers (., ,) —
    // those are handled separately by numeric parsing, not by this general text normalizer.
    private const val PUNCTUATION = "\u060C\u061B\u061F!\"'`():*؛"

    /** Maps any Arabic-Indic or Persian digit in [input] to its ASCII equivalent; leaves every
     *  other character untouched. Exposed separately from [normalize] because numeric parsing
     *  (see the `Normalizer` import-pipeline component) needs digit-only normalization without
     *  the rest of [normalize]'s lowercase/alef/ta-marbuta text rules. */
    fun normalizeDigits(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (c in input) {
            val arabicIdx = ARABIC_INDIC_DIGITS.indexOf(c)
            val persianIdx = PERSIAN_DIGITS.indexOf(c)
            when {
                arabicIdx >= 0 -> sb.append('0' + arabicIdx)
                persianIdx >= 0 -> sb.append('0' + persianIdx)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

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

        // Normalize digit script (Arabic-Indic/Persian -> ASCII) so "٥" and "5" compare equal.
        s = normalizeDigits(s)

        // Punctuation and separators (-, _, /, \) become a space rather than being deleted, so
        // words they separate don't get glued together; whitespace is then collapsed below.
        s = s.map { c -> if (PUNCTUATION.indexOf(c) >= 0) ' ' else c }.joinToString("")
        s = s.replace(Regex("[-_/\\\\]"), " ")

        // Collapse internal whitespace and lowercase any Latin characters.
        s = s.replace(Regex("\\s+"), " ").trim().lowercase()

        return s
    }
}
