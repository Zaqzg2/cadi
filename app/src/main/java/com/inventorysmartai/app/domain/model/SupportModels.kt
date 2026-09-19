package com.inventorysmartai.app.domain.model

import com.inventorysmartai.app.domain.importing.ImportType

data class Attachment(
    val id: Long = 0L,
    val ownerType: AttachmentOwnerType,
    val ownerId: Long,
    val fileName: String,
    val filePath: String,
    val mimeType: String? = null,
    val createdAt: Long
)

data class ImportJob(
    val id: Long = 0L,
    val sourceType: ImportSourceType,
    val fileName: String? = null,
    val status: ImportJobStatus,
    val totalRows: Int? = null,
    val processedRows: Int? = null,
    val acceptedRows: Int? = null,
    val matchedRows: Int? = null,
    val newProductRows: Int? = null,
    val duplicateRows: Int? = null,
    val errorRows: Int? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
    // --- Phase 3 additions, all additive/nullable so Phase 1/2 call sites keep compiling ---
    /** Which of the five (six, counting SALES_INVOICES) pipelines this job runs. Null only for
     *  legacy/never-analyzed rows; every job created by Phase 3's import flow sets it immediately. */
    val importType: ImportType? = null,
    /** The workbook sheet the user picked, when [sourceType] == EXCEL and the workbook had more
     *  than one sheet. Null for CSV (single "sheet" by definition) or a single-sheet workbook. */
    val sheetName: String? = null,
    /** Branch context for row types (INVENTORY/COUNTING/PURCHASE_REQUESTS) that need one and
     *  didn't get it from a mapped BRANCH column — chosen once on the "اختر نوع البيانات" step. */
    val defaultBranchId: Long? = null,
    /** Supplier context for PURCHASE_REQUESTS imports; optional even then. */
    val defaultSupplierId: Long? = null,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    /** Rows the human explicitly rejected/ignored during review — kept distinct from errorRows
     *  (a rejected row need not have failed validation) for an honest "تقرير النتيجة". */
    val rejectedRows: Int? = null
)

data class ImportRow(
    val id: Long = 0L,
    val importJobId: Long,
    val rowIndex: Int,
    /** JSON object of the ORIGINAL column header -> raw cell value, exactly as read from the
     *  file. Never mutated after parsing — the spec's "do not modify the original value" rule. */
    val rawData: String,
    /** JSON object of CANONICAL FIELD NAME (an [ImportType]-relevant ImportField.name) -> the
     *  normalized value for that field. Doubles as both "normalized values" and "mapped fields"
     *  from the spec's row-review list: the key already tells you which field a column was
     *  mapped to, and the value is the post-normalization value used for validation/matching. */
    val normalizedData: String? = null,
    val matchedProductId: Long? = null,
    val suggestedProductId: Long? = null,
    val confidence: Double? = null,
    val status: ImportRowStatus,
    val errorMessage: String? = null,
    val createdAt: Long,
    // --- Phase 3 additions ---
    /** Machine-readable counterpart to [errorMessage] (see ImportErrorCode) — null when the row
     *  has no blocking error, even if it has warnings. */
    val errorCode: ImportErrorCode? = null,
    /** JSON array of non-blocking warning strings (e.g. "matched by name only, no barcode on
     *  file" or a fuzzy-suggestion confidence note). A row can have warnings and still be
     *  ACCEPTED; it cannot have a non-null [errorCode] and be ACCEPTED. */
    val warningsJson: String? = null,
    /** True once a human has edited any field on this row during review — surfaced in the UI so
     *  edited rows are visually distinguishable from the pipeline's original output. */
    val edited: Boolean = false
)

/**
 * Pure aggregation used to build [ImportJob]'s row-count columns from a batch of rows once the
 * import pipeline actually classifies them — kept separate from any DAO/Room so it's plain-JUnit
 * testable (see ImportRowAggregationTest). Deliberately counts "accepted" independently from
 * "matched an existing product" per the spec's explicit warning that the two are not the same
 * number (a row can be accepted as a brand-new product, matching nothing).
 */
fun aggregateImportRowCounts(rows: List<ImportRow>): ImportJobRowCounts = ImportJobRowCounts(
    totalRows = rows.size,
    processedRows = rows.count { it.status != ImportRowStatus.PENDING },
    acceptedRows = rows.count { it.status == ImportRowStatus.ACCEPTED },
    matchedRows = rows.count { it.status == ImportRowStatus.MATCHED },
    newProductRows = rows.count { it.status == ImportRowStatus.NEW_PRODUCT },
    duplicateRows = rows.count { it.status == ImportRowStatus.DUPLICATE },
    errorRows = rows.count { it.status == ImportRowStatus.ERROR },
    ambiguousRows = rows.count { it.status == ImportRowStatus.AMBIGUOUS },
    rejectedRows = rows.count { it.status == ImportRowStatus.REJECTED }
)

data class ImportJobRowCounts(
    val totalRows: Int,
    val processedRows: Int,
    val acceptedRows: Int,
    val matchedRows: Int,
    val newProductRows: Int,
    val duplicateRows: Int,
    val errorRows: Int,
    val ambiguousRows: Int = 0,
    val rejectedRows: Int = 0
)

data class AuditLog(
    val id: Long = 0L,
    val entityType: String,
    val entityId: String,
    val action: AuditAction,
    val performedBy: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val createdAt: Long
)
