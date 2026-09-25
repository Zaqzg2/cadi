package com.inventorysmartai.app.presentation.datacenter.importflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.data.importing.AiDocumentAnalysisEngine
import com.inventorysmartai.app.data.importing.FileDetectionResult
import com.inventorysmartai.app.data.importing.ImportEngine
import com.inventorysmartai.app.data.importing.ImportProgress
import com.inventorysmartai.app.domain.importing.ColumnMapper
import com.inventorysmartai.app.domain.importing.ColumnMappingResult
import com.inventorysmartai.app.domain.importing.ImportField
import com.inventorysmartai.app.domain.importing.ImportReviewManager
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.OpenedFile
import com.inventorysmartai.app.domain.importing.RowDecision
import com.inventorysmartai.app.domain.model.Attachment
import com.inventorysmartai.app.domain.model.AttachmentOwnerType
import com.inventorysmartai.app.domain.model.ImportRowStatus
import com.inventorysmartai.app.domain.model.ImportSourceType
import com.inventorysmartai.app.domain.repository.AttachmentRepository
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.DeviceSessionRepository
import com.inventorysmartai.app.domain.repository.ImportRepository
import com.inventorysmartai.app.domain.repository.PartyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Source types the AI extraction path handles — everything else still goes through
 *  [ImportEngine]'s tabular CSV/Excel pipeline. See [ImportFlowViewModel.runAnalysis]. */
private val AI_SOURCE_TYPES = setOf(ImportSourceType.PDF, ImportSourceType.IMAGE, ImportSourceType.CAMERA)

/**
 * Drives every step of the spec's UX flow (section 24) from "اختر نوع البيانات" through
 * "اعتماد"/"تقرير النتيجة" — steps 1 (اختر الملف) and 3 (اختر الورقة) included. One instance is
 * shared across all the flow's screens via Hilt's nested-navigation-graph scoping (see
 * AppNavHost), so navigating between steps never loses state.
 *
 * Phase 4: also drives the AI-extraction path (PDF/photographed/scanned documents) via
 * [AiDocumentAnalysisEngine] instead of [ImportEngine] — see [runAnalysis]. Every step AFTER
 * analysis (mapping-screen skip aside — see [loadSheets]) is identical code for both paths, since
 * both converge on the same [ImportProgress] states.
 */
@HiltViewModel
class ImportFlowViewModel @Inject constructor(
    private val importEngine: ImportEngine,
    private val aiDocumentAnalysisEngine: AiDocumentAnalysisEngine,
    private val importRepository: ImportRepository,
    private val importReviewManager: ImportReviewManager,
    private val columnMapper: ColumnMapper,
    private val catalogRepository: CatalogRepository,
    private val partyRepository: PartyRepository,
    private val attachmentRepository: AttachmentRepository,
    private val deviceSessionRepository: DeviceSessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ImportFlowState())
    val state: StateFlow<ImportFlowState> = _state.asStateFlow()

    private var openedFile: OpenedFile? = null
    private var pickedFileUriString: String? = null
    private var reviewObservationJob: Job? = null

    init {
        viewModelScope.launch {
            catalogRepository.observeBranches().collect { branches -> update { it.copy(branches = branches) } }
        }
        viewModelScope.launch {
            partyRepository.observeSuppliers().collect { suppliers -> update { it.copy(suppliers = suppliers) } }
        }
    }

    private inline fun update(block: (ImportFlowState) -> ImportFlowState) {
        _state.value = block(_state.value)
    }

    // ---- Step: setup ----

    fun onImportTypeSelected(type: ImportType) = update { it.copy(importType = type) }
    fun onBranchSelected(branchId: Long?) = update { it.copy(selectedBranchId = branchId) }
    fun onSupplierSelected(supplierId: Long?) = update { it.copy(selectedSupplierId = supplierId) }

    /** [uriString] is the content:// URI the SAF document picker returned, as a string. */
    fun onFilePicked(uriString: String) {
        pickedFileUriString = uriString
        viewModelScope.launch {
            update { it.copy(isBusy = true, fileError = null, sheets = emptyList(), selectedSheet = null) }

            val file = runCatching { importEngine.openFile(uriString) }.getOrElse { e ->
                update { it.copy(isBusy = false, fileError = "تعذّر فتح الملف المحدد: ${e.message ?: e.javaClass.simpleName}") }
                return@launch
            }
            openedFile = file

            when (val detection = importEngine.detect(file)) {
                is FileDetectionResult.Recognized -> {
                    update {
                        it.copy(
                            pickedFileName = file.displayName,
                            pickedFileSize = file.sizeBytes,
                            pickedFileMime = file.mimeType,
                            detectedSourceType = detection.sourceType,
                            fileError = null
                        )
                    }
                    if (detection.sourceType in AI_SOURCE_TYPES) {
                        // No sheet concept for a photographed/scanned document — go straight to
                        // "ready to analyze", same as a single-sheet CSV would.
                        update { it.copy(isBusy = false, sheets = listOf(null), selectedSheet = null) }
                    } else {
                        loadSheets(file, detection.sourceType)
                    }
                }
                is FileDetectionResult.Empty ->
                    update { it.copy(isBusy = false, fileError = "الملف المحدد فارغ ولا يحتوي على بيانات") }
                is FileDetectionResult.Unsupported ->
                    update { it.copy(isBusy = false, fileError = detection.reasonAr) }
                is FileDetectionResult.Unreadable ->
                    update { it.copy(isBusy = false, fileError = detection.reasonAr) }
            }
        }
    }

    private fun loadSheets(file: OpenedFile, sourceType: ImportSourceType) {
        viewModelScope.launch {
            val sheets = runCatching { importEngine.listSheets(file, sourceType) }.getOrDefault(listOf(null))
            update { it.copy(isBusy = false, sheets = sheets, selectedSheet = sheets.firstOrNull()) }
        }
    }

    // ---- Step: sheet select ----

    fun onSheetSelected(sheet: String?) = update { it.copy(selectedSheet = sheet) }

    // ---- Step: analyzing ----

    fun startAnalysis() {
        val file = openedFile ?: return
        val current = _state.value
        val sourceType = current.detectedSourceType ?: return
        val importType = current.importType ?: return

        // Reset synchronously (not inside the coroutine below) so a screen that navigates
        // immediately after calling this — every caller does — never reads stale headers/isBusy
        // left over from a previous attempt in the same ViewModel instance.
        update {
            it.copy(
                isBusy = true, analysisError = null, stageLabel = "",
                analyzedProcessed = 0, analyzedTotal = 0,
                headers = emptyList(), columnMapping = null, suggestedTemplate = null
            )
        }

        viewModelScope.launch {
            val jobId = importRepository.startImportJob(
                sourceType = sourceType,
                fileName = current.pickedFileName,
                fileSizeBytes = current.pickedFileSize,
                mimeType = current.pickedFileMime,
                importType = importType,
                sheetName = current.selectedSheet,
                defaultBranchId = current.selectedBranchId,
                defaultSupplierId = current.selectedSupplierId
            )
            update { it.copy(jobId = jobId) }

            if (sourceType in AI_SOURCE_TYPES) {
                // Phase 4 file traceability: the review screen can look this up later via
                // attachmentRepository.observeAttachments(IMPORT_JOB, jobId) to let the user open
                // the original photographed/scanned document (spec's FILE TRACEABILITY section).
                pickedFileUriString?.let { uri ->
                    runCatching {
                        attachmentRepository.addAttachment(
                            Attachment(
                                ownerType = AttachmentOwnerType.IMPORT_JOB,
                                ownerId = jobId,
                                fileName = current.pickedFileName ?: "مستند",
                                filePath = uri,
                                mimeType = current.pickedFileMime,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            runAnalysis(file, sourceType, importType, jobId, mappingOverride = null)
        }
    }

    private suspend fun runAnalysis(
        file: OpenedFile,
        sourceType: ImportSourceType,
        importType: ImportType,
        jobId: Long,
        mappingOverride: ColumnMappingResult?
    ) {
        val progressFlow = if (sourceType in AI_SOURCE_TYPES) {
            val sessionId = deviceSessionRepository.getSessionId()
            val bytes = runCatching { file.inputStream().use { it.readBytes() } }.getOrElse { e ->
                update { it.copy(isBusy = false, analysisError = "تعذّر قراءة الملف: ${e.message ?: e.javaClass.simpleName}") }
                return
            }
            aiDocumentAnalysisEngine.analyzeDocument(bytes, file.mimeType ?: "application/octet-stream", importType, jobId, sessionId)
        } else {
            importEngine.analyze(file, sourceType, _state.value.selectedSheet, importType, jobId, mappingOverride)
        }

        progressFlow.collect { progress ->
            when (progress) {
                is ImportProgress.Stage -> update { it.copy(stageLabel = progress.labelAr) }
                is ImportProgress.Analyzing -> update { it.copy(analyzedProcessed = progress.processed, analyzedTotal = progress.total) }
                is ImportProgress.Failed -> update { it.copy(isBusy = false, analysisError = progress.messageAr) }
                is ImportProgress.Done -> {
                    if (mappingOverride == null) {
                        // First pass only: seed the mapping screen with the auto-suggestion and
                        // check for a matching saved template. A re-analysis after the user
                        // edited the mapping (confirmColumnMappingAndReanalyze) keeps their edits.
                        // For an AI-sourced job this "mapping" is the synthetic identity mapping
                        // ImportPipeline.analyzeRows built (one entry per field Gemini actually
                        // returned) — harmless to show on the mapping screen as-is, though a
                        // dedicated "this was AI-extracted, here's what was found" presentation
                        // would read better; left as a follow-up (see README's Known limitations).
                        val template = runCatching {
                            importRepository.findBestMatchingTemplate(importType, progress.result.headers)
                        }.getOrNull()
                        update { it.copy(headers = progress.result.headers, columnMapping = progress.result.columnMapping, suggestedTemplate = template) }
                    }
                    importRepository.persistAnalysis(jobId, progress.result)
                    update { it.copy(isBusy = false) }
                    beginReviewObservation(jobId)
                }
            }
        }
    }

    // ---- Step: column mapping ----

    fun onColumnMappingChanged(columnIndex: Int, field: ImportField?) {
        val current = _state.value.columnMapping ?: return
        update { it.copy(columnMapping = columnMapper.applyOverride(current, columnIndex, field)) }
    }

    fun useSuggestedTemplate() {
        val template = _state.value.suggestedTemplate ?: return
        val importType = _state.value.importType ?: return
        update { it.copy(columnMapping = columnMapper.applyTemplate(it.headers, importType, template)) }
    }

    /** Re-runs steps 4-9 with the (possibly human-edited) mapping applied, so validation/
     *  matching/duplicate-detection reflect the final mapping rather than the auto-suggestion. */
    fun confirmColumnMapping() {
        val file = openedFile ?: return
        val current = _state.value
        val sourceType = current.detectedSourceType ?: return
        val importType = current.importType ?: return
        val jobId = current.jobId ?: return
        val mapping = current.columnMapping ?: return

        viewModelScope.launch {
            update { it.copy(isBusy = true) }
            runAnalysis(file, sourceType, importType, jobId, mappingOverride = mapping)
        }
    }

    fun saveCurrentMappingAsTemplate(name: String) {
        val importType = _state.value.importType ?: return
        val mapping = _state.value.columnMapping ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val fieldMap = mapping.mapped.mapNotNull { m -> m.field?.let { field -> m.header to field } }.toMap()
            runCatching { importRepository.saveMappingTemplate(name.trim(), importType, mapping.mappings.map { it.header }, fieldMap) }
                .onSuccess { update { it.copy(snackbarMessage = "تم حفظ القالب \"${name.trim()}\"") } }
                .onFailure { e -> update { it.copy(snackbarMessage = e.message) } }
        }
    }

    // ---- Step: review ----

    private fun beginReviewObservation(jobId: Long) {
        reviewObservationJob?.cancel()
        reviewObservationJob = viewModelScope.launch {
            importReviewManager.observeRows(jobId).collect { rows -> update { it.copy(reviewRows = rows) } }
        }
    }

    fun onRowDecision(rowId: Long, decision: RowDecision) {
        viewModelScope.launch {
            runCatching { importReviewManager.applyDecision(rowId, decision) }
                .onFailure { e -> update { it.copy(snackbarMessage = e.message ?: "تعذّر تنفيذ الإجراء") } }
        }
    }

    fun onEditRow(rowId: Long, edits: Map<ImportField, String?>) {
        viewModelScope.launch { importReviewManager.updateRowFields(rowId, edits) }
    }

    /** "Accept confident matches" per spec section 15 — MATCHED (exact match) and NEW_PRODUCT
     *  rows only. AMBIGUOUS/PENDING/DUPLICATE/ERROR rows are deliberately excluded: "do not allow
     *  ambiguous rows to be silently accepted... unless unambiguous". */
    fun onAcceptAllValid() {
        val jobId = _state.value.jobId ?: return
        val ids = _state.value.reviewRows
            .filter { it.status == ImportRowStatus.MATCHED || it.status == ImportRowStatus.NEW_PRODUCT }
            .map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch { importReviewManager.applyBulkDecision(jobId, ids, RowDecision.Accept) }
    }

    fun onRejectAll() {
        val jobId = _state.value.jobId ?: return
        val ids = _state.value.reviewRows.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch { importReviewManager.applyBulkDecision(jobId, ids, RowDecision.Reject) }
    }

    // ---- Step: approval / result ----

    fun approveImport() {
        val jobId = _state.value.jobId ?: return
        viewModelScope.launch {
            update { it.copy(isBusy = true) }
            val result = importRepository.approveJob(jobId)
            update { it.copy(isBusy = false, approvalResult = result, snackbarMessage = result.errorMessage) }
        }
    }

    fun cancelImport() {
        val jobId = _state.value.jobId ?: return
        viewModelScope.launch { importRepository.cancelJob(jobId) }
    }

    fun consumeSnackbar() = update { it.copy(snackbarMessage = null) }
}
