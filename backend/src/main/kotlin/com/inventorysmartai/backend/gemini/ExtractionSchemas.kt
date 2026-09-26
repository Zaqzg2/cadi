package com.inventorysmartai.backend.gemini

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Document type this backend knows how to extract — one entry per schema the Phase 4 brief names
 * (ProductImportResult, InventoryImportResult, CountingImportResult, PurchaseRequestImportResult,
 * SalesInvoiceImportResult, GoalImportResult).
 */
enum class ExtractionDocumentType { PRODUCTS, INVENTORY, COUNTING, PURCHASE_REQUESTS, SALES_INVOICES, GOALS }

/**
 * Builds the `response_format` JSON Schema for each [ExtractionDocumentType]. Every schema shares
 * one shape — a document-level `header` (for facts that apply to the whole document: invoice
 * number, requester name, ...) plus a `rows` array (one entry per line/product/tier) — because
 * the Phase 4 spec's own requirement is uniform across all six types: "preserve raw value,
 * normalized value where needed, field name, source page, source row/column, confidence/
 * uncertainty, validation warnings" and "never save AI-extracted data directly without review".
 *
 * Deliberate design choice, spelled out here because it is not obvious from the schema alone:
 * this backend does NOT ask Gemini for a "normalized value" — it asks only for [raw] text exactly
 * as printed/written, per field, with a confidence score. The Android app runs that raw text
 * through the *same* deterministic `Normalizer`/`ImportValidator`/`ProductMatcher` already used
 * for spreadsheet imports (see domain/importing/ai/AiExtractionMapper.kt on the app side) — so
 * normalization and validation are ONE deterministic code path regardless of whether a value came
 * from a spreadsheet cell or a photographed invoice, rather than two implementations that could
 * quietly disagree. Field names below are the app's own `ImportField` enum names verbatim (e.g.
 * "CURRENT_STOCK", "REQUESTED_QUANTITY") so the app-side mapper needs no translation table.
 */
object ExtractionSchemas {

    private fun fieldValueSchema(fieldLabel: String): JsonObject = buildJsonObject {
        put("type", "object")
        put("description", "Extracted value for \"$fieldLabel\", exactly as it appears in the source — never invented, never auto-corrected.")
        put(
            "properties",
            buildJsonObject {
                put(
                    "raw",
                    buildJsonObject {
                        putJsonArray("type") { add("string"); add("null") }
                        put("description", "The raw text/number exactly as printed or written. Null if this field is genuinely absent from the document.")
                    }
                )
                put(
                    "confidence",
                    buildJsonObject {
                        put("type", "number")
                        put("description", "0.0 to 1.0 — how confident the model is that \"raw\" was read correctly.")
                    }
                )
                put(
                    "uncertain",
                    buildJsonObject {
                        put("type", "boolean")
                        put("description", "true if handwriting/print quality made this genuinely hard to read — flags it for human review even if a guess was still made.")
                    }
                )
            }
        )
        putJsonArray("required") { add("raw"); add("confidence"); add("uncertain") }
    }

    private fun nullableInt(description: String): JsonObject = buildJsonObject {
        putJsonArray("type") { add("integer"); add("null") }
        put("description", description)
    }

    private fun nullableString(description: String): JsonObject = buildJsonObject {
        putJsonArray("type") { add("string"); add("null") }
        put("description", description)
    }

    private fun stringArray(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", buildJsonObject { put("type", "string") })
    }

    private fun rowSchema(fieldNames: List<String>): JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put("sourceRow", nullableInt("Row number in the source table, if the document is tabular (1-based). Null for a free-form document."))
                put("sourceColumn", nullableString("Column header/letter in the source table, if applicable."))
                put("sourcePage", nullableInt("Page number in the source PDF/image this row came from (1-based)."))
                put(
                    "fields",
                    buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject { fieldNames.forEach { put(it, fieldValueSchema(it)) } })
                    }
                )
                put("rowWarnings", stringArray("Human-readable warnings about this specific row (e.g. \"quantity is barely legible\")."))
            }
        )
        putJsonArray("required") { add("fields") }
    }

    private fun documentSchema(rowFieldNames: List<String>, headerFieldNames: List<String> = emptyList()): JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put("documentType", buildJsonObject { put("type", "string"); put("description", "Echo back the requested document type.") })
                if (headerFieldNames.isNotEmpty()) {
                    put(
                        "header",
                        buildJsonObject {
                            put("type", "object")
                            put("description", "Facts that apply to the whole document rather than one row/line.")
                            put("properties", buildJsonObject { headerFieldNames.forEach { put(it, fieldValueSchema(it)) } })
                        }
                    )
                }
                put(
                    "rows",
                    buildJsonObject {
                        put("type", "array")
                        put("description", "One entry per line item / product / row. Do not merge distinct rows; do not invent rows that are not in the source.")
                        put("items", rowSchema(rowFieldNames))
                    }
                )
                put("documentWarnings", stringArray("Warnings about the document as a whole (e.g. \"page 2 was partially cut off\")."))
            }
        )
        putJsonArray("required") { add("documentType"); add("rows") }
    }

    /** ProductImportResult */
    val PRODUCTS: JsonObject = documentSchema(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "CATEGORY", "UNIT", "MIN_STOCK", "REORDER_POINT", "NOTES")
    )

    /** InventoryImportResult */
    val INVENTORY: JsonObject = documentSchema(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "BRANCH", "CURRENT_STOCK", "NOTES")
    )

    /** CountingImportResult */
    val COUNTING: JsonObject = documentSchema(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "BRANCH", "COUNTED_QUANTITY", "CURRENT_STOCK", "NOTES")
    )

    /** PurchaseRequestImportResult — covers the spec's Excel purchase request, photographed
     *  purchase request, and handwritten purchase request cases identically: a handwritten
     *  request's "signature present?" note has nowhere structured to live in the deterministic
     *  ImportField model, so it is deliberately surfaced as a rowWarning instead (still visible
     *  on the review screen, still never silently dropped). */
    val PURCHASE_REQUESTS: JsonObject = documentSchema(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "BRANCH", "CURRENT_STOCK", "REQUESTED_QUANTITY", "NOTES"),
        headerFieldNames = listOf("REQUESTER_NAME", "REQUEST_DATE")
    )

    /** SalesInvoiceImportResult */
    val SALES_INVOICES: JsonObject = documentSchema(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "UNIT", "QUANTITY", "UNIT_PRICE", "DISCOUNT_PERCENT"),
        headerFieldNames = listOf(
            "INVOICE_NUMBER", "INVOICE_DATE", "CUSTOMER_NAME", "BRANCH", "WAREHOUSE",
            "CURRENCY", "PREVIOUS_BALANCE", "INVOICE_TOTAL", "FINAL_BALANCE"
        )
    )

    /** GoalImportResult — "do not assume a fixed number of groups" (spec) is handled by letting
     *  the model emit one ROW per (product, target group) tier rather than fixed Group1/Group2/
     *  Group3 columns; the app groups rows back into one Goal-with-N-CommissionGroups per
     *  product at approval time (see ImportRepositoryImpl.approveGoals). */
    val GOALS: JsonObject = documentSchema(
        rowFieldNames = listOf("ITEM_NUMBER", "BARCODE", "PRODUCT_NAME", "TARGET_GROUP", "TARGET", "COMMISSION_VALUE", "NOTES")
    )

    fun forDocumentType(type: ExtractionDocumentType): JsonObject = when (type) {
        ExtractionDocumentType.PRODUCTS -> PRODUCTS
        ExtractionDocumentType.INVENTORY -> INVENTORY
        ExtractionDocumentType.COUNTING -> COUNTING
        ExtractionDocumentType.PURCHASE_REQUESTS -> PURCHASE_REQUESTS
        ExtractionDocumentType.SALES_INVOICES -> SALES_INVOICES
        ExtractionDocumentType.GOALS -> GOALS
    }

    /** The instruction text sent alongside the document/image part — deliberately blunt about
     *  "do not invent values" (spec: "Never invent values. Missing values must remain
     *  null/unknown.") since this is the one place that rule can actually be enforced (a schema
     *  can force *shape*, never *honesty*). */
    fun instructionFor(type: ExtractionDocumentType): String {
        val kind = when (type) {
            ExtractionDocumentType.PRODUCTS -> "a product list (Excel export, printed list, or catalog page)"
            ExtractionDocumentType.INVENTORY -> "an inventory/stock sheet"
            ExtractionDocumentType.COUNTING -> "a physical inventory count sheet"
            ExtractionDocumentType.PURCHASE_REQUESTS -> "a purchase request — this may be a printed sheet, a photographed handwritten note, or a typed document"
            ExtractionDocumentType.SALES_INVOICES -> "a sales invoice"
            ExtractionDocumentType.GOALS -> "a sales target / commission sheet, which may have any number of target-tier columns per product"
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
