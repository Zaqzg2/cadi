package com.inventorysmartai.app.presentation.datacenter.importflow

import com.inventorysmartai.app.domain.importing.ColumnMappingResult
import com.inventorysmartai.app.domain.importing.ImportMappingTemplate
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.model.Branch
import com.inventorysmartai.app.domain.model.ImportRow
import com.inventorysmartai.app.domain.model.ImportSourceType
import com.inventorysmartai.app.domain.model.Supplier
import com.inventorysmartai.app.domain.repository.ImportApprovalResult

/** One shared state for every screen of the import flow (setup -> sheet -> analyzing -> mapping
 *  -> review), so a step back-navigated-to never loses what an earlier step already decided. */
data class ImportFlowState(
    // Step: setup (import type + branch/supplier context)
    val importType: ImportType? = null,
    val branches: List<Branch> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val selectedBranchId: Long? = null,
    val selectedSupplierId: Long? = null,

    // File
    val pickedFileName: String? = null,
    val pickedFileSize: Long? = null,
    val pickedFileMime: String? = null,
    val detectedSourceType: ImportSourceType? = null,
    val fileError: String? = null,

    // Step: sheet select
    val sheets: List<String?> = emptyList(),
    val selectedSheet: String? = null,

    // Step: analyzing
    val stageLabel: String = "",
    val analyzedProcessed: Int = 0,
    val analyzedTotal: Int = 0,
    val analysisError: String? = null,

    // Step: column mapping
    val headers: List<String> = emptyList(),
    val columnMapping: ColumnMappingResult? = null,
    val suggestedTemplate: ImportMappingTemplate? = null,

    // Step: review
    val jobId: Long? = null,
    val reviewRows: List<ImportRow> = emptyList(),

    // Step: result
    val approvalResult: ImportApprovalResult? = null,

    val isBusy: Boolean = false,
    val snackbarMessage: String? = null
) {
    val canStartAnalysis: Boolean
        get() = importType != null && detectedSourceType != null && fileError == null &&
            (sheets.size <= 1 || selectedSheet != null || sheets.isEmpty())
}
