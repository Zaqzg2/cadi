package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/** One source column's mapping state, before or after a human override. [header] is the raw
 *  header text exactly as read from the file (Excel/CSV column header, or a synthetic
 *  "العمود 3" label when [HeaderRowDetector] could not find a real header row at all). */
data class ColumnMapping(
    val columnIndex: Int,
    val header: String,
    val field: ImportField?,
    val requiresConfirmation: Boolean = false,
    val isUserOverride: Boolean = false
)

/** The column-mapping screen's whole model: every source column plus its current field (or
 *  lack of one). "Unknown columns" per the spec = [unmapped]; "Unmapped columns" in the spec's
 *  wording covers both columns nothing matched and columns the user explicitly chose to ignore —
 *  kept separate here ([unmapped] vs [ignored]) since they need different UI treatment (one is
 *  "needs a decision", the other is "decision already made: skip it"). */
data class ColumnMappingResult(val mappings: List<ColumnMapping>) {
    val mapped: List<ColumnMapping> get() = mappings.filter { it.field != null && it.field != ImportField.IGNORE }
    val ignored: List<ColumnMapping> get() = mappings.filter { it.field == ImportField.IGNORE }
    val unmapped: List<ColumnMapping> get() = mappings.filter { it.field == null }
    val needsConfirmation: List<ColumnMapping> get() = mappings.filter { it.requiresConfirmation && !it.isUserOverride }

    fun fieldFor(columnIndex: Int): ImportField? = mappings.firstOrNull { it.columnIndex == columnIndex }?.field
    fun columnFor(field: ImportField): ColumnMapping? = mappings.firstOrNull { it.field == field }
}

/** Step 7 (Column Mapping) of the pipeline. Deliberately independent of [Normalizer]/
 *  [ImportValidator]: this only decides WHICH field a column feeds, not what happens to the
 *  value once mapped. */
interface ColumnMapper {
    /** Dictionary-driven auto-suggestion — never throws, a header nothing matches just comes
     *  back unmapped for the human to assign. */
    fun suggestMapping(headers: List<String>, importType: ImportType): ColumnMappingResult

    /** Re-suggests using a previously saved [ImportMappingTemplate] first (exact normalized-
     *  header match against the template's saved mapping), falling back to the plain dictionary
     *  for any header the template doesn't cover — e.g. an extra column the file gained since
     *  the template was saved. Caller is responsible for only offering this when
     *  [ImportMappingTemplate.structureMatchRatio] clears [ImportMappingTemplate.AUTO_OFFER_THRESHOLD]. */
    fun applyTemplate(headers: List<String>, importType: ImportType, template: ImportMappingTemplate): ColumnMappingResult

    /** A human explicitly setting/clearing/remapping one column. Passing `field = null` clears
     *  the mapping back to "unmapped" (distinct from [ImportField.IGNORE], which is an explicit
     *  "skip this column" decision). */
    fun applyOverride(result: ColumnMappingResult, columnIndex: Int, field: ImportField?): ColumnMappingResult
}

@Singleton
class DefaultColumnMapper @Inject constructor() : ColumnMapper {

    override fun suggestMapping(headers: List<String>, importType: ImportType): ColumnMappingResult {
        val assignable = importType.assignableFields().toSet() + ImportField.IGNORE
        return ColumnMappingResult(buildMappings(headers, assignable) { header -> ImportField.suggest(header) })
    }

    override fun applyTemplate(
        headers: List<String>,
        importType: ImportType,
        template: ImportMappingTemplate
    ): ColumnMappingResult {
        val savedMapping = SimpleJson.decodeMap(template.mappingJson) // normalized header -> ImportField.name
        val assignable = importType.assignableFields().toSet() + ImportField.IGNORE

        return ColumnMappingResult(buildMappings(headers, assignable) { header ->
            val normalized = ArabicTextNormalizer.normalize(header)
            val fromTemplate = savedMapping[normalized]?.let { name -> runCatching { ImportField.valueOf(name) }.getOrNull() }
            if (fromTemplate != null) {
                ImportField.Suggestion(fromTemplate, requiresConfirmation = false, exact = true)
            } else {
                ImportField.suggest(header)
            }
        })
    }

    override fun applyOverride(result: ColumnMappingResult, columnIndex: Int, field: ImportField?): ColumnMappingResult {
        val updated = result.mappings.map {
            if (it.columnIndex == columnIndex) {
                it.copy(field = field, requiresConfirmation = false, isUserOverride = true)
            } else {
                it
            }
        }
        return ColumnMappingResult(updated)
    }

    /** Shared column-building logic. Two things need to be true for a suggested field to be
     *  auto-applied: it must be assignable for this [ImportType], and it must not collide with a
     *  non-repeatable field another column in this same file already claimed (NOTES/IGNORE may
     *  repeat; nothing else should — two columns both guessed as PRODUCT_NAME is a real
     *  ambiguity, and the second one is left unmapped for a human to resolve rather than picked
     *  for them).
     *
     *  A suggestion that fails either check is NOT silently dropped — the column still comes
     *  back flagged [ColumnMapping.requiresConfirmation] = true even with `field = null`, so the
     *  mapping screen visibly shows "this column looked like something, but it needs a manual
     *  decision" rather than looking identical to a column nothing matched at all. This is what
     *  the spec's "use explicit mapping when ambiguity exists" actually requires: an unassignable
     *  or colliding guess is exactly as ambiguous as a bare "الكمية"/"العدد", not less. */
    private fun buildMappings(
        headers: List<String>,
        assignable: Set<ImportField>,
        suggest: (String) -> ImportField.Suggestion?
    ): List<ColumnMapping> {
        val used = mutableSetOf<ImportField>()
        return headers.mapIndexed { index, header ->
            val suggestion = suggest(header)
            val rawField = suggestion?.field
            val isAssignable = rawField != null && rawField in assignable
            val repeatable = rawField == ImportField.NOTES || rawField == ImportField.IGNORE
            val collides = isAssignable && !repeatable && rawField in used

            val finalField = if (isAssignable && !collides) rawField else null
            if (finalField != null && !repeatable) used += finalField

            val needsAttention = when {
                collides -> true
                rawField != null && !isAssignable -> true
                suggestion?.requiresConfirmation == true -> true
                else -> false
            }

            ColumnMapping(columnIndex = index, header = header, field = finalField, requiresConfirmation = needsAttention)
        }
    }
}
