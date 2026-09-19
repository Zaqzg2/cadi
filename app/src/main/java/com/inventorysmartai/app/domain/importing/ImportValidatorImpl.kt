package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.model.ImportErrorCode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Step 7 (Validation). Enforces exactly the spec's per-[ImportType] requirements (section 8):
 * every type needs "product identification" (at least one of item number/barcode/name — an
 * OR, not a flat AND list, so it's checked here rather than folded into [ImportType.requiredFields]
 * which lists genuinely type-specific AND-required fields like CURRENT_STOCK for INVENTORY).
 *
 * Deliberately does not force fields a type doesn't need (spec: "Do not force unnecessary
 * fields") — e.g. a PRODUCTS import is never rejected for lacking a quantity column.
 */
@Singleton
class DefaultImportValidator @Inject constructor() : ImportValidator {

    override fun validate(row: ParsedImportRow): ValidationResult {
        val errors = mutableListOf<RowIssue>()
        val warnings = mutableListOf<RowIssue>()

        if (!row.hasIdentification()) {
            errors += RowIssue(
                ImportErrorCode.MISSING_REQUIRED_FIELD,
                "لا يوجد رقم صنف أو باركود أو اسم صنف — لا يمكن تحديد الصنف من هذا الصف"
            )
        }

        row.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
            if (!barcode.all { it.isDigit() } || barcode.length !in 6..14) {
                // A warning, not an error: plenty of real internal item codes are legitimately
                // not EAN/UPC-shaped, and rejecting the whole row for it would be too strict.
                warnings += RowIssue(ImportErrorCode.INVALID_BARCODE, "صيغة الباركود غير معتادة: \"$barcode\"", ImportField.BARCODE)
            }
        }

        row.importType.requiredFields.forEach { field ->
            if (!row.hasRawValue(field)) {
                errors += RowIssue(
                    ImportErrorCode.MISSING_REQUIRED_FIELD,
                    "الحقل \"${field.labelAr}\" مطلوب لنوع الاستيراد \"${row.importType.labelAr}\" ولم يتم تعيينه أو تركه فارغًا",
                    field
                )
            }
        }

        // Any numeric field with a raw value present that failed to parse is INVALID_NUMBER,
        // whether or not this ImportType happens to require that particular field.
        ImportField.entries.filter { it.isNumeric }.forEach { field ->
            val raw = row.rawValue(field)
            val numeric = row.numericValue(field)
            when {
                raw != null && numeric == null ->
                    errors += RowIssue(ImportErrorCode.INVALID_NUMBER, "قيمة غير رقمية في \"${field.labelAr}\": \"$raw\"", field)
                numeric != null && numeric < 0 ->
                    errors += RowIssue(ImportErrorCode.INVALID_NUMBER, "قيمة سالبة غير مسموح بها في \"${field.labelAr}\"", field)
            }
        }

        return ValidationResult(isValid = errors.isEmpty(), errors = errors, warnings = warnings)
    }
}
