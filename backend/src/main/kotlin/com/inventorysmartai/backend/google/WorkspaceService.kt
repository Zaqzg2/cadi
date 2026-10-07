package com.inventorysmartai.backend.google

import com.inventorysmartai.backend.audit.AuditLog
import com.inventorysmartai.backend.auth.GoogleAuthService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/** Who is calling: the app's session id and the sealed Google link token it stores (header X-Google-Link). */
class GoogleSession(val sessionId: String, val linkToken: String?)

/**
 * Every Google Workspace action the app can ask for, in one place. Each method is a WRITE or a send: callers (the REST
 * routes) trust that the app already showed the user a confirmation — this class does not ask again. Each successful
 * action is also written to the audit log (one JSON line on stdout).
 */
class WorkspaceService(
    private val auth: GoogleAuthService,
    private val drive: DriveClient,
    private val sheets: SheetsClient,
    private val docs: DocsClient,
    private val gmail: GmailClient,
    private val calendar: CalendarClient,
    private val audit: AuditLog
) {
    private suspend fun token(session: GoogleSession): String = auth.accessToken(session.sessionId, session.linkToken)

    suspend fun ensureFolders(session: GoogleSession): JsonObject {
        val structure = drive.ensureFolderStructure(token(session))
        return buildJsonObject {
            put("rootFolderId", structure.rootFolderId)
            put("subfolders", buildJsonObject { structure.subfolderIds.forEach { (name, id) -> put(name, id) } })
        }
    }

    suspend fun listFiles(session: GoogleSession, folderId: String): JsonObject = drive.listFiles(token(session), folderId)

    suspend fun uploadFile(session: GoogleSession, folder: String?, fileName: String, mimeType: String, base64Content: String): JsonObject {
        val accessToken = token(session)
        val structure = drive.ensureFolderStructure(accessToken)
        val folderId = folder?.let { structure.subfolderIds[it] } ?: structure.rootFolderId
        val bytes = try {
            Base64.getDecoder().decode(base64Content)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("base64Content ليس Base64 صالحًا")
        }
        val result = drive.uploadFile(accessToken, folderId, fileName, mimeType, bytes)
        audit.record("GOOGLE_DRIVE_UPLOAD", session.sessionId, mapOf("fileId" to result.fileId, "fileName" to fileName))
        return buildJsonObject {
            put("fileId", result.fileId)
            put("webViewLink", result.webViewLink)
        }
    }

    suspend fun ensureMasterSpreadsheet(session: GoogleSession): String {
        val accessToken = token(session)
        val structure = drive.ensureFolderStructure(accessToken)
        return findOrCreateMaster(accessToken, structure.rootFolderId)
    }

    suspend fun exportSheet(session: GoogleSession, sheetName: String, headerRow: List<String>, rows: List<List<String>>): JsonObject {
        val accessToken = token(session)
        val structure = drive.ensureFolderStructure(accessToken)
        val spreadsheetId = findOrCreateMaster(accessToken, structure.rootFolderId)
        sheets.exportSheet(accessToken, spreadsheetId, sheetName, headerRow, rows)
        audit.record("GOOGLE_SHEET_EXPORT", session.sessionId, mapOf("sheet" to sheetName, "rows" to rows.size.toString()))
        return buildJsonObject {
            put("spreadsheetId", spreadsheetId)
            put("status", "exported")
        }
    }

    suspend fun readSheet(session: GoogleSession, spreadsheetId: String, sheetName: String): JsonObject =
        sheets.readSheet(token(session), spreadsheetId, sheetName)

    suspend fun saveReportToDrive(session: GoogleSession, title: String, reportMarkdown: String, folder: String?): JsonObject {
        val accessToken = token(session)
        val structure = drive.ensureFolderStructure(accessToken)
        val folderId = folder?.let { structure.subfolderIds[it] } ?: structure.subfolderIds["Reports"] ?: structure.rootFolderId
        val result = drive.uploadFile(accessToken, folderId, "$title.md", "text/markdown", reportMarkdown.toByteArray(Charsets.UTF_8))
        audit.record("GOOGLE_DRIVE_UPLOAD", session.sessionId, mapOf("fileId" to result.fileId, "title" to title))
        return buildJsonObject {
            put("fileId", result.fileId)
            put("webViewLink", result.webViewLink)
            put("status", "uploaded")
        }
    }

    suspend fun createGoogleDoc(session: GoogleSession, title: String, reportMarkdown: String): JsonObject {
        val accessToken = token(session)
        val structure = drive.ensureFolderStructure(accessToken)
        val folderId = structure.subfolderIds["Reports"] ?: structure.rootFolderId
        val result = docs.createReportDocument(accessToken, folderId, title, reportMarkdown)
        audit.record("GOOGLE_DOC_CREATE", session.sessionId, mapOf("fileId" to result.fileId, "title" to title))
        return buildJsonObject {
            put("fileId", result.fileId)
            put("webViewLink", result.webViewLink)
            put("status", "created")
        }
    }

    suspend fun createCalendarEvent(session: GoogleSession, title: String, startIso: String, endIso: String, description: String?): JsonObject {
        val created = calendar.createEvent(token(session), title, startIso, endIso, description)
        val eventId = created["id"]?.jsonPrimitive?.contentOrNull
        audit.record("CALENDAR_CREATE", session.sessionId, mapOf("eventId" to (eventId ?: "?"), "title" to title))
        return buildJsonObject {
            put("eventId", eventId)
            put("htmlLink", created["htmlLink"]?.jsonPrimitive?.contentOrNull)
            put("status", "created")
        }
    }

    suspend fun sendEmail(session: GoogleSession, to: String, subject: String, body: String, attachmentDriveFileId: String?): JsonObject {
        // A line break in To/Subject would let a caller inject extra mail headers (e.g. a hidden Bcc).
        require(to.none { it == '\r' || it == '\n' } && subject.none { it == '\r' || it == '\n' }) { "عنوان المستلم والموضوع يجب أن يكونا في سطر واحد" }
        require(to.contains('@')) { "عنوان المستلم غير صالح" }
        val attachmentLink = attachmentDriveFileId?.let { "https://drive.google.com/file/d/$it/view" }
        gmail.sendEmail(token(session), to, subject, body, attachmentLink)
        audit.record("GMAIL_SEND", session.sessionId, mapOf("to" to to, "subject" to subject))
        return buildJsonObject { put("status", "sent") }
    }

    /**
     * Looks for the master workbook by name directly inside the app's Drive root folder and creates a new one only on a
     * genuine miss. (Caching the id is not possible here — the server keeps no state — so Drive is listed per call; the
     * folder holds a handful of files, so this is cheap.)
     */
    private suspend fun findOrCreateMaster(accessToken: String, rootFolderId: String): String {
        val existing = drive.listFiles(accessToken, rootFolderId)
        return existing["files"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == SheetsClient.MASTER_SPREADSHEET_NAME }
            ?.get("id")?.jsonPrimitive?.contentOrNull
            ?: sheets.createMasterSpreadsheet(accessToken, rootFolderId)
    }
}
