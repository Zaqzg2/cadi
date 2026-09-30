package com.inventorysmartai.app.data.ai

import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType

/**
 * The response schema for each [AiExtractionDocumentType], sent to Gemini through Firebase AI Logic.
 *
 * Ported from the backend module's gemini/ExtractionSchemas.kt (whose tests ran green in CI): the same
 * six document types, the same field names, the same instruction text — expressed as [AiSchemaNode]s
 * instead of a JSON tree, because the Firebase SDK takes its own `Schema` type, not raw JSON Schema.
 *
 * Every schema shares one shape — a document-level `header` (facts that apply to the whole document:
 * invoice number, requester name, ...) plus a `rows` array (one entry per line/product/tier) — because
 * the Phase 4 spec's requirement is uniform across all six types: "preserve raw value, ... source page,
 * source row/column, confidence/uncertainty, validation warnings" and "never save AI-extracted data
 * directly without review".
 *
 * Deliberate design choice: Gemini is NOT asked for a "normalized value". It is asked only for the
 * [raw] text exactly as printed/written, per field, with a confidence score. That raw text is then run
 * through the *same* deterministic `Normalizer`/`ImportValidator`/`ProductMatcher` used for
 * spreadsheet imports (see domain/importing/ai/AiExtractionMapper.kt) — so normalization and validation
 * are ONE code path whether a value came from a spreadsheet cell or a photographed invoice. Field names
 * are the app's own `ImportField` enum names verbatim (e.g. "CURRENT_STOCK", "REQUESTED_QUANTITY").
 */
object ExtractionSchemas {

    private fun fieldValue(fieldLabel: String): AiObject = AiObject(
        description = "Extracted value for \"$fieldLabel\", exactly as it appears in the source — never invented, never auto-corrected.",
        properties = mapOf(
            "raw" to AiString(
                nullable = true,
                description = "The raw text/number exactly as printed or written. Null if this field is genuinely absent from the document."
            ),
            "confidence" to AiNumber(
                description = "0.0 to 1.0 — how confident the model is that \"raw\" was read correctly."
            ),
            "uncertain" to AiBoolean(
                description = "true if handwriting/print quality made this genuinely hard to read — flags it for human review even if a guess was still made."
            )
        ),
        required = setOf("raw", "confidence", "uncertain")
    )

    private fun stringArray(description: String): AiArray = AiArray(items = AiString(), description = description)

    private fun row(fieldNames: List<String>): AiObject = AiObject(
        properties = mapOf(
            "sourceRow" to AiInteger(
                nullable = true,
                description = "Row number in the source table, if the document is tabular (1-based). Null for a free-form document."
            ),
            "sourceColumn" to AiString(
                nullable = true,
                description = "Column header/letter in the source table, if applicable."
            ),
            "sourcePage" to AiInteger(
                nullable = true,
                description = "Page number in the source PDF/image this row came from (1-based)."
            ),
            "fields" to AiObject(properties = fieldNames.associateWith { fieldValue(it) }),
            "rowWarnings" to stringArray("Human-readable warnings about this specific row (e.g. \"quantity is barely legible\").")
        ),
        required = setOf("fields")
    )

    private fun document(rowFieldNames: List<String>, headerFieldNames: List<String> = emptyList()): AiObject {
        val properties = linkedMapOf<String, AiSchemaNode>()
        properties["documentType"] = AiString(description = "Echo back the requested document type.")
        if (headerFieldNames.isNotEmpty()) {
            properties["header"] = AiObject(
                description = "Facts that apply to the whole document rather than one row/line.",
                properties = headerFieldNames.associateWith { fieldValue(it) }
            )
        }
        properties["rows"] = AiArray(
            items = row(rowFieldNames),
            description = "One entry per line item / product / row. Do not merge distinct rows; do not invent rows that are not in the source."
        )
        properties["documentWarnings"] = stringArray("Warnings about the document as a whole (e.g. \"page 2 was partially cut off\").")
        return AiObject(properties = properties, required = setOf("documentType", "rows"))
    }

    /** ProductImportResult */
    val PRODUCTS: AiObject = document(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "CATEGORY", "UNIT", "MIN_STOCK", "REORDER_POINT", "NOTES")
    )

    /** InventoryImportResult */
    val INVENTORY: AiObject = document(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "BRANCH", "CURRENT_STOCK", "NOTES")
    )

    /** CountingImportResult */
    val COUNTING: AiObject = document(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "BRANCH", "COUNTED_QUANTITY", "CURRENT_STOCK", "NOTES")
    )

    /** PurchaseRequestImportResult — covers the spec's Excel purchase request, photographed purchase
     *  request, and handwritten purchase request cases identically: a handwritten request's "signature
     *  present?" note has nowhere structured to live in the deterministic ImportField model, so it is
     *  deliberately surfaced as a rowWarning instead (still visible on the review screen, still never
     *  silently dropped). */
    val PURCHASE_REQUESTS: AiObject = document(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "BRANCH", "CURRENT_STOCK", "REQUESTED_QUANTITY", "NOTES"),
        headerFieldNames = listOf("REQUESTER_NAME", "REQUEST_DATE")
    )

    /** SalesInvoiceImportResult */
    val SALES_INVOICES: AiObject = document(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "UNIT", "QUANTITY", "UNIT_PRICE", "DISCOUNT_PERCENT"),
        headerFieldNames = listOf(
            "INVOICE_NUMBER", "INVOICE_DATE", "CUSTOMER_NAME", "BRANCH", "WAREHOUSE",
            "CURRENCY", "PREVIOUS_BALANCE", "INVOICE_TOTAL", "FINAL_BALANCE"
        )
    )

    /** GoalImportResult — "do not assume a fixed number of groups" (spec) is handled by letting the
     *  model emit one ROW per (product, target group) tier rather than fixed Group1/Group2/Group3
     *  columns; the app groups rows back into one Goal-with-N-CommissionGroups per product at approval
     *  time (see ImportRepositoryImpl.approveGoals). */
    val GOALS: AiObject = document(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "TARGET_GROUP", "TARGET", "COMMISSION_VALUE", "NOTES")
    )

    fun forDocumentType(type: AiExtractionDocumentType): AiObject = when (type) {
        AiExtractionDocumentType.PRODUCTS -> PRODUCTS
        AiExtractionDocumentType.INVENTORY -> INVENTORY
        AiExtractionDocumentType.COUNTING -> COUNTING
        AiExtractionDocumentType.PURCHASE_REQUESTS -> PURCHASE_REQUESTS
        AiExtractionDocumentType.SALES_INVOICES -> SALES_INVOICES
        AiExtractionDocumentType.GOALS -> GOALS
    }

    /** The instruction text sent alongside the document/image part — deliberately blunt about
     *  "do not invent values" (spec: "Never invent values. Missing values must remain
     *  null/unknown.") since this is the one place that rule can actually be enforced (a schema
     *  can force *shape*, never *honesty*). */
    fun instructionFor(type: AiExtractionDocumentType): String {
        val kind = when (type) {
            AiExtractionDocumentType.PRODUCTS -> "a product list (Excel export, printed list, or catalog page)"
            AiExtractionDocumentType.INVENTORY -> "an inventory/stock sheet"
            AiExtractionDocumentType.COUNTING -> "a physical inventory count sheet"
            AiExtractionDocumentType.PURCHASE_REQUESTS -> "a purchase request — this may be a printed sheet, a photographed handwritten note, or a typed document"
            AiExtractionDocumentType.SALES_INVOICES -> "a sales invoice"
            AiExtractionDocumentType.GOALS -> "a sales target / commission sheet, which may have any number of target-tier columns per product"
        }
        return """
            You are extracting structured data from $kind for an Arabic-language inventory
            management system. Read every row/line you can find. For each field:
            - Put the value EXACTLY as printed/written into "raw" (same digits, same script). Do not
              translate, round, reformat dates, or "fix" what looks like a typo.
            - If a field is not present at all, set "raw" to null. Never invent or guess a value
              that is not visibly present in the document.
            - Set "confidence" (0.0-1.0) honestly. If handwriting or print quality made the value
              genuinely hard to read, set "uncertain": true even if you still produced a best-guess
              "raw" value — do not silently guess without flagging it.
            - Record sourceRow/sourceColumn/sourcePage whenever the document's structure makes them
              meaningful (a spreadsheet-like table); leave them null for a free-form document.
            - Add a short warning string to "rowWarnings" or "documentWarnings" for anything a human
              reviewer should double-check (illegible handwriting, a smudged total, a torn page,
              an apparent signature you cannot verify, numbers that don't add up).
            Do not perform any arithmetic validation yourself and do not decide whether a row should
            be accepted — that happens separately, deterministically, after you respond.
        """.trimIndent()
    }
}
