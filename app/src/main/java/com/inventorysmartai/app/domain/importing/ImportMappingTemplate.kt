package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer

/**
 * A saved column mapping ("جرد الفرع الرئيسي" in the spec's own example) so a recurring file
 * layout doesn't need to be remapped by hand every time.
 *
 * [headerFingerprint] is the sorted, normalized, pipe-joined set of the source headers the
 * mapping was saved against — deliberately just a set comparison, not an exact ordered match, so
 * a column reorder still counts as "the same structure" but the spec's "do not automatically
 * apply it if the structure does not match sufficiently" rule is still honored via
 * [structureMatchRatio] rather than an equality check.
 */
data class ImportMappingTemplate(
    val id: Long = 0L,
    val name: String,
    val importType: ImportType,
    val headerFingerprint: String,
    /** JSON object of original header text -> ImportField.name. */
    val mappingJson: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val AUTO_OFFER_THRESHOLD = 0.7

        fun fingerprintOf(headers: List<String>): String =
            headers.map { ArabicTextNormalizer.normalize(it) }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .joinToString("|")
    }

    /** Fraction of the CURRENT file's headers that this template already has a mapping opinion
     *  for. 1.0 = every current header appears in the saved fingerprint; 0.0 = no overlap at all.
     *  The caller (see ImportRepository/ImportEngine) only offers "استخدام القالب السابق" when
     *  this is at least [AUTO_OFFER_THRESHOLD]. */
    fun structureMatchRatio(currentHeaders: List<String>): Double {
        val current = currentHeaders.map { ArabicTextNormalizer.normalize(it) }
            .filter { it.isNotEmpty() }
            .toSet()
        if (current.isEmpty()) return 0.0
        val saved = headerFingerprint.split("|").filter { it.isNotEmpty() }.toSet()
        if (saved.isEmpty()) return 0.0
        val overlap = current.intersect(saved).size
        return overlap.toDouble() / current.size
    }
}
