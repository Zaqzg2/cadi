package com.inventorysmartai.app.domain.repository

import com.inventorysmartai.app.domain.assistant.AssistantContext
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import kotlinx.coroutines.flow.Flow

/** Drives one Gemini Interactions API conversation via the backend (see backend/assistant/
 *  AssistantOrchestrator.kt) — everything about the tool-calling loop (which calls are LOCAL vs
 *  BACKEND, which need confirmation) is resolved internally; a ViewModel only ever sees
 *  [AssistantStepResult.Final] or [AssistantStepResult.ConfirmationRequired]. */
interface AssistantRepository {
    suspend fun sendMessage(conversationId: String, message: String, context: AssistantContext?): AssistantStepResult

    /** Resumes the same conversation after the user answered the confirmation dialog for the
     *  most recent [AssistantStepResult.ConfirmationRequired]. */
    suspend fun confirmPendingAction(conversationId: String, approved: Boolean): AssistantStepResult
}

data class GoogleAuthStatus(val linked: Boolean, val grantedScopes: List<String> = emptyList())

/** Status-only — actually obtaining the user's consent (Credential Manager + AuthorizationClient)
 *  inherently needs an Activity, so that part lives in the Android-framework-aware
 *  `data/google/GoogleAuthManager` instead of behind this domain interface; this repository only
 *  ever handles "tell the backend about a code we already got" and "what's the current status". */
interface GoogleAuthRepository {
    suspend fun getStatus(): Result<GoogleAuthStatus>
    suspend fun completeLinking(serverAuthCode: String): Result<GoogleAuthStatus>
    suspend fun unlink(): Result<Unit>
}

data class DriveUploadResult(val fileId: String, val webViewLink: String?)
data class CalendarEventResult(val eventId: String?, val htmlLink: String?)

/** Direct Google Workspace actions the app calls outside the assistant chat (e.g. a "حفظ في
 *  Drive" button on the Reports screen) — same underlying backend actions the assistant's
 *  confirmed tool calls use (backend google/BackendToolExecutor.kt), so behavior is identical
 *  either way. Every method here is a write/send action; the caller is responsible for having
 *  already shown the Arabic confirmation dialog (spec: "ACTION CONFIRMATION") — this repository
 *  does not ask again. */
interface GoogleWorkspaceRepository {
    suspend fun saveReportToDrive(title: String, reportMarkdown: String, folder: String? = null): Result<DriveUploadResult>
    suspend fun createGoogleDoc(title: String, reportMarkdown: String): Result<DriveUploadResult>
    suspend fun sendEmail(to: String, subject: String, body: String, attachmentDriveFileId: String? = null): Result<Unit>
    suspend fun createCalendarEvent(title: String, startIso: String, endIso: String, description: String? = null): Result<CalendarEventResult>
    suspend fun exportSheet(sheetName: String, headerRow: List<String>, rows: List<List<String>>): Result<String>
}

/** One [status] value per service, using the spec's exact four Arabic labels — see backend
 *  routes/StatusRoutes.kt, which is the sole source of truth for what these strings are. */
data class GoogleServiceStatus(
    val gemini: String,
    val drive: String,
    val sheets: String,
    val docs: String,
    val gmail: String,
    val calendar: String
)

interface GoogleServiceStatusRepository {
    suspend fun getStatus(verifyGemini: Boolean = false): Result<GoogleServiceStatus>
}

/** Phase 4's AI document-understanding pipeline entry point. [extract] only calls the backend and
 *  returns Gemini's structured result as-is — turning it into [com.inventorysmartai.app.domain.
 *  importing.ParsedImportRow]s, running deterministic validation/matching, and persisting nothing
 *  without human review all happen afterwards via the existing [ImportRepository] +
 *  [com.inventorysmartai.app.domain.importing.ImportPipeline] (see domain/importing/ai/
 *  AiExtractionMapper.kt) — this repository's only job is "ask Gemini, hand back what it said". */
interface AiDocumentImportRepository {
    suspend fun extract(
        fileBytes: ByteArray,
        mimeType: String,
        documentType: AiExtractionDocumentType,
        sessionId: String
    ): Result<AiExtractionDocument>
}

/** Thin domain-facing wrapper over `data/local/datastore/DeviceSessionLocalDataSource` — the
 *  opaque, non-identifying id sent as `sessionId` on every backend call (see that class's doc
 *  comment for exactly what it is and isn't). */
interface DeviceSessionRepository {
    suspend fun getSessionId(): String
}
