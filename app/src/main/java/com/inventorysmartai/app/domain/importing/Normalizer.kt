package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One cell's value after normalization for a given [ImportField]. Per the spec's rule 9
 * ("do not modify the original value; store both rawValue and normalizedValue"), [raw] is
 * exactly what was read from the file (already trimmed of leading/trailing whitespace by the
 * parser, nothing else) and [normalized] is what validation/matching/persistence actually use.
 * [numeric] is populated only for [ImportField.isNumeric] fields, and only when [normalized]
 * parses cleanly — a null [numeric] on a numeric field is the pipeline's signal to raise
 * INVALID_NUMBER at the validation step, not a silent zero.
 */
data class NormalizedValue(
    val raw: String?,
    val normalized: String?,
    val numeric: Double? = null
)

/** Step 9 of the pipeline: RawRows (after column mapping) -> Normalization. Kept as its own
 *  interface (rather than folded into ColumnMapper or ImportValidator) so normalization rules
 *  can be unit tested and reused independently, per "keep these components independent". */
interface Normalizer {
    fun normalize(field: ImportField, rawValue: String?): NormalizedValue
}

@Singleton
class DefaultNormalizer @Inject constructor() : Normalizer {

    override fun normalize(field: ImportField, rawValue: String?): NormalizedValue {
        val raw = rawValue?.trim()?.takeIf { it.isNotEmpty() }
        if (raw == null) return NormalizedValue(raw = rawValue, normalized = null, numeric = null)

        return when {
            field == ImportField.BARCODE || field == ImportField.ITEM_NUMBER -> normalizeIdentifier(raw)
            field.isNumeric -> normalizeNumeric(raw)
            else -> NormalizedValue(raw = raw, normalized = ArabicTextNormalizer.normalize(raw), numeric = null)
        }
    }

    /** Barcodes/item numbers are matched with an exact, case-sensitive string comparison against
     *  whatever is already stored on the product (see DeterministicProductMatcher) — so this
     *  intentionally does NOT lowercase or touch punctuation the way the general text normalizer
     *  does. It only removes whitespace a barcode should never contain and converts Arabic-Indic
     *  digits to ASCII, which is a strict correctness improvement (an Arabic-digit barcode is
     *  still "the same barcode"), not a lossy rewrite. */
    private fun normalizeIdentifier(raw: String): NormalizedValue {
        val normalized = ArabicTextNormalizer.normalizeDigits(raw).replace(Regex("\\s+"), "")
        return NormalizedValue(raw = raw, normalized = normalized.ifEmpty { null }, numeric = null)
    }

    private fun normalizeNumeric(raw: String): NormalizedValue {
        val digitsNormalized = ArabicTextNormalizer.normalizeDigits(raw).replace(" ", "")
        // Treat "1,234" / "1,234.50" as thousands-separated; anything else with a stray comma
        // (e.g. "1,5" meant as a decimal comma) is left as-is and will correctly fail to parse
        // below, surfacing as INVALID_NUMBER at validation rather than being silently guessed.
        val cleaned = if (Regex("^-?\\d{1,3}(,\\d{3})+(\\.\\d+)?$").matches(digitsNormalized)) {
            digitsNormalized.replace(",", "")
        } else {
            digitsNormalized
        }
        return NormalizedValue(raw = raw, normalized = cleaned, numeric = cleaned.toDoubleOrNull())
    }
}
