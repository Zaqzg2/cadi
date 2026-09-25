package com.inventorysmartai.app.domain.importing.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Mirrors backend `gemini/ExtractionSchemas.kt` exactly (field names, nesting) — deserialized
 * directly from `POST /v1/documents/extract`'s response by Moshi (moshi-kotlin-codegen). These
 * classes deliberately double as both the wire DTO and the domain model used by
 * [AiExtractionMapper] — a normal data-layer-DTO/domain-model split was judged not to earn its
 * complexity for six small, purely-structural leaf classes with no behavior of their own; every
 * field is still nullable/defaulted exactly as a DTO needs to be for safe parsing. Revisit if
 * these models ever grow real domain logic.
 */
enum class AiExtractionDocumentType { PRODUCTS, INVENTORY, COUNTING, PURCHASE_REQUESTS, SALES_INVOICES, GOALS }

@JsonClass(generateAdapter = true)
data class AiFieldValue(
    val raw: String? = null,
    val confidence: Double = 0.0,
    val uncertain: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AiExtractionRow(
    val sourceRow: Int? = null,
    val sourceColumn: String? = null,
    val sourcePage: Int? = null,
    val fields: Map<String, AiFieldValue> = emptyMap(),
    val rowWarnings: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AiExtractionDocument(
    val documentType: String = "",
    val header: Map<String, AiFieldValue> = emptyMap(),
    val rows: List<AiExtractionRow> = emptyList(),
    @Json(name = "documentWarnings") val documentWarnings: List<String> = emptyList()
)
