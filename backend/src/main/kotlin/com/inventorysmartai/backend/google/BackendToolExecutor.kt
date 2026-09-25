package com.inventorysmartai.backend.google

import com.inventorysmartai.backend.audit.AuditLog
import com.inventorysmartai.backend.auth.GoogleAuthService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The one place [ToolCatalog][com.inventorysmartai.backend.gemini.ToolCatalog]'s four
 * [com.inventorysmartai.backend.gemini.ToolExecutionSite.BACKEND] tools actually run — called
 * both from the assistant's confirmed-write flow (routes/AssistantRoutes.kt) and from the plain
 * per-feature REST endpoints the Reports/Data-Center screens call directly (routes/
 * GoogleWorkspaceRoutes.kt), so "save this report to Drive" behaves identically whether the user
 * tapped a button or asked the assistant to do it. Every method here is a WRITE/send action —
 * callers are responsible for having already obtained the user's confirmation (Phase 4 spec,
 * "ACTION CONFIRMATION"); this class does not ask again.
 */
class BackendToolExecutor(
    private val googleAuthService: GoogleAuthService,
    private val driveClient: DriveClient,
    private val sheetsClient: SheetsClient,
    private val docsClient: DocsClient,
    private val gmailClient: GmailClient,
    private val calendarClient: CalendarClient,
    private val auditLog: AuditLog
) {
    suspend fun saveReportToDrive(sessionId: String, title: String, reportMarkdown: String, folder: String?): JsonObject {
        val token = googleAuthService.getValidAccessToken(sessionId)
        val structure = driveClient.ensureFolderStructure(token)
        val folderId = folder?.let { structure.subfolderIds[it] } ?: structure.subfolderIds["Reports"] ?: structure.rootFolderId
        val fileName = "$title.md"
        val result = driveClient.uploadFile(token, folderId, fileName, "text/markdown", reportMarkdown.toByteArray(Charsets.UTF_8))
        auditLog.record("GOOGLE_DRIVE_UPLOAD", sessionId, mapOf("fileId" to result.fileId, "title" to title))
        return buildJsonObject {
            put("fileId", result.fileId)
            put("webViewLink", result.webViewLink)
            put("status", "uploaded")
        }
    }

    suspend fun createGoogleDoc(sessionId: String, title: String, reportMarkdown: String): JsonObject {
        val token = googleAuthService.getValidAccessToken(sessionId)
        val structure = driveClient.ensureFolderStructure(token)
        val folderId = structure.subfolderIds["Reports"] ?: structure.rootFolderId
        val result = docsClient.createReportDocument(token, folderId, title, reportMarkdown)
        auditLog.record("GOOGLE_DOC_CREATE", sessionId, mapOf("fileId" to result.fileId, "title" to title))
        return buildJsonObject {
            put("fileId", result.fileId)
            put("webViewLink", result.webViewLink)
            put("status", "created")
        }
    }

    suspend fun createCalendarEvent(sessionId: String, title: String, startIso: String, endIso: String, description: String?): JsonObject {
        val token = googleAuthService.getValidAccessToken(sessionId)
        val created = calendarClient.createEvent(token, title, startIso, endIso, description)
        val eventId = created["id"]?.jsonPrimitive?.contentOrNull
        auditLog.record("CALENDAR_CREATE", sessionId, mapOf("eventId" to (eventId ?: "?"), "title" to title))
        return buildJsonObject {
            put("eventId", eventId)
            put("htmlLink", created["htmlLink"]?.jsonPrimitive?.contentOrNull)
            put("status", "created")
        }
    }

    suspend fun sendEmail(sessionId: String, to: String, subject: String, body: String, attachmentDriveFileId: String?): JsonObject {
        val token = googleAuthService.getValidAccessToken(sessionId)
        val attachmentLink = attachmentDriveFileId?.let { "https://drive.google.com/file/d/$it/view" }
        gmailClient.sendEmail(token, to, subject, body, attachmentLink)
        auditLog.record("GMAIL_SEND", sessionId, mapOf("to" to to, "subject" to subject))
        return buildJsonObject { put("status", "sent") }
    }

    /** Generic dispatcher keyed by the exact tool name from
     *  [com.inventorysmartai.backend.gemini.ToolCatalog] — what the assistant's confirmed-write
     *  flow calls after the user approves a `function_call` step for one of these four tools.
     *  [arguments] is the tool's raw JSON arguments object, exactly as Gemini produced it. */
    suspend fun execute(name: String, sessionId: String, arguments: JsonObject): JsonObject = when (name) {
        "saveReportToDrive" -> saveReportToDrive(
            sessionId,
            title = arguments.str("title") ?: "تقرير",
            reportMarkdown = arguments.str("reportMarkdown").orEmpty(),
            folder = arguments.str("folder")
        )
        "createGoogleDoc" -> createGoogleDoc(
            sessionId,
            title = arguments.str("title") ?: "تقرير",
            reportMarkdown = arguments.str("reportMarkdown").orEmpty()
        )
        "createCalendarEvent" -> createCalendarEvent(
            sessionId,
            title = arguments.str("title") ?: "حدث",
            startIso = arguments.str("startDateTime").orEmpty(),
            endIso = arguments.str("endDateTime").orEmpty(),
            description = arguments.str("description")
        )
        "sendEmail" -> sendEmail(
            sessionId,
            to = arguments.str("to").orEmpty(),
            subject = arguments.str("subject").orEmpty(),
            body = arguments.str("body").orEmpty(),
            attachmentDriveFileId = arguments.str("attachmentDriveFileId")
        )
        else -> error("Unknown backend tool: $name")
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
