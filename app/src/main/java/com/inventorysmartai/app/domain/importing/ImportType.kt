package com.inventorysmartai.app.domain.importing

/**
 * The kinds of bulk import the same pipeline (File → ... → Database, see ImportPipeline)
 * supports. PRODUCTS/INVENTORY/COUNTING/PURCHASE_REQUESTS/GOALS have been fully implemented since
 * Phase 3. SALES_INVOICES gained real approval logic in Phase 4 (see
 * [com.inventorysmartai.app.data.repository.ImportRepositoryImpl.approveSalesInvoices]) — Phase 3
 * left it with the enum value and pipeline plumbing only ("infrastructure but not advanced
 * invoice-specific parsing yet"); Gemini's document understanding (see the backend module's
 * gemini/ExtractionSchemas.kt) is exactly that "advanced invoice-specific parsing".
 */
enum class ImportType(val labelAr: String) {
    PRODUCTS("الأصناف"),
    INVENTORY("الجرد / المخزون"),
    COUNTING("الجرد الفعلي"),
    PURCHASE_REQUESTS("طلبات الشراء"),
    GOALS("الأهداف"),
    SALES_INVOICES("فواتير المبيعات");

    /** Fields a row MUST resolve to (after mapping+normalization) to be acceptable at all —
     *  matches the spec's per-type "Required" lists exactly. Product identification means
     *  "at least one of item number / barcode / name", checked separately (see
     *  [ImportValidator]) since it is an OR-of-fields requirement, not a flat AND list. */
    val requiredFields: Set<ImportField>
        get() = when (this) {
            PRODUCTS -> emptySet() // product identification is itself the row being created
            INVENTORY -> setOf(ImportField.CURRENT_STOCK)
            COUNTING -> setOf(ImportField.COUNTED_QUANTITY)
            PURCHASE_REQUESTS -> setOf(ImportField.REQUESTED_QUANTITY)
            GOALS -> setOf(ImportField.TARGET)
            SALES_INVOICES -> setOf(ImportField.QUANTITY)
        }

    /** Fields this type will recognize/use if present in the file, beyond the identification
     *  columns (item number/barcode/name) that every type accepts. Purely informational for the
     *  column-mapping screen (which fields to offer in the "map to" dropdown) — the validator
     *  only enforces [requiredFields]. */
    val optionalFields: Set<ImportField>
        get() = when (this) {
            PRODUCTS -> setOf(
                ImportField.CATEGORY, ImportField.UNIT, ImportField.MIN_STOCK,
                ImportField.REORDER_POINT, ImportField.NOTES
            )
            INVENTORY -> setOf(ImportField.BRANCH, ImportField.NOTES)
            COUNTING -> setOf(ImportField.BRANCH, ImportField.CURRENT_STOCK, ImportField.NOTES)
            PURCHASE_REQUESTS -> setOf(ImportField.BRANCH, ImportField.CURRENT_STOCK, ImportField.NOTES)
            GOALS -> setOf(ImportField.NOTES, ImportField.TARGET_GROUP, ImportField.COMMISSION_VALUE)
            SALES_INVOICES -> setOf(ImportField.BRANCH, ImportField.UNIT, ImportField.UNIT_PRICE, ImportField.DISCOUNT_PERCENT, ImportField.NOTES)
        }

    /** Every field this type's column-mapping screen should offer, in a sensible order. */
    fun assignableFields(): List<ImportField> =
        (listOf(ImportField.ITEM_NUMBER, ImportField.BARCODE, ImportField.PRODUCT_NAME) +
            requiredFields.toList() + optionalFields.toList())
            .distinct()
}
