package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.auth.GoogleAuthService
import com.inventorysmartai.backend.google.BackendToolExecutor
import com.inventorysmartai.backend.google.DriveClient
import com.inventorysmartai.backend.google.SheetsClient
import com.inventorysmartai.backend.routes.dto.CalendarEventRequestDto
import com.inventorysmartai.backend.routes.dto.DocCreateRequestDto
import com.inventorysmartai.backend.routes.dto.DriveUploadRequestDto
import com.inventorysmartai.backend.routes.dto.SendEmailRequestDto
import com.inventorysmartai.backend.routes.dto.SheetsExportRequestDto
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * These endpoints exist alongside the assistant's tool-calling flow (routes/AssistantRoutes.kt)
 * for the app's own direct UI actions — e.g. a "Save to Drive" button on the Reports screen that
 * doesn't go through a chat turn at all. Every write here is still something the app only calls
 * after the user tapped an explicit confirm button (Phase 4 spec, "ACTION CONFIRMATION") — this
 * backend does not re-implement that confirmation UI, it trusts the app already showed it, exactly
 * as it does for the assistant's executeBackendTool route.
 */
fun Route.googleWorkspaceRoutes(
    googleAuthService: GoogleAuthService,
    driveClient: DriveClient,
    sheetsClient: SheetsClient,
    backendToolExecutor: BackendToolExecutor
) {
    post("/v1/drive/ensureFolders") {
        val sessionId = call.receive<Map<String, String>>()["sessionId"].orEmpty()
        val token = googleAuthService.getValidAccessToken(sessionId)
        val structure = driveClient.ensureFolderStructure(token)
        call.respond(buildJsonObject {
            put("rootFolderId", structure.rootFolderId)
            put("subfolders", buildJsonObject { structure.subfolderIds.forEach { (k, v) -> put(k, v) } })
        })
    }

    get("/v1/drive/list") {
        val sessionId = call.request.queryParameters["sessionId"].orEmpty()
        val folderId = call.request.queryParameters["folderId"].orEmpty()
        val token = googleAuthService.getValidAccessToken(sessionId)
        call.respond(driveClient.listFiles(token, folderId))
    }

    post("/v1/drive/upload") {
        val request = call.receive<DriveUploadRequestDto>()
        val token = googleAuthService.getValidAccessToken(request.sessionId)
        val structure = driveClient.ensureFolderStructure(token)
        val folderId = request.folder?.let { structure.subfolderIds[it] } ?: structure.rootFolderId
        val bytes = Base64.getDecoder().decode(request.base64Content)
        val result = driveClient.uploadFile(token, folderId, request.fileName, request.mimeType, bytes)
        call.respond(buildJsonObject { put("fileId", result.fileId); put("webViewLink", result.webViewLink) })
    }

    post("/v1/sheets/ensureMaster") {
        val sessionId = call.receive<Map<String, String>>()["sessionId"].orEmpty()
        val token = googleAuthService.getValidAccessToken(sessionId)
        val structure = driveClient.ensureFolderStructure(token)
        val spreadsheetId = findOrCreateMasterSpreadsheet(token, driveClient, sheetsClient, structure.rootFolderId)
        call.respond(buildJsonObject { put("spreadsheetId", spreadsheetId) })
    }

    post("/v1/sheets/export") {
        val request = call.receive<SheetsExportRequestDto>()
        val token = googleAuthService.getValidAccessToken(request.sessionId)
        val structure = driveClient.ensureFolderStructure(token)
        val spreadsheetId = findOrCreateMasterSpreadsheet(token, driveClient, sheetsClient, structure.rootFolderId)
        sheetsClient.exportSheet(token, spreadsheetId, request.sheetName, request.headerRow, request.rows)
        call.respond(buildJsonObject { put("spreadsheetId", spreadsheetId); put("status", "exported") })
    }

    get("/v1/sheets/read") {
        val sessionId = call.request.queryParameters["sessionId"].orEmpty()
        val spreadsheetId = call.request.queryParameters["spreadsheetId"].orEmpty()
        val sheetName = call.request.queryParameters["sheetName"].orEmpty()
        val token = googleAuthService.getValidAccessToken(sessionId)
        call.respond(sheetsClient.readSheet(token, spreadsheetId, sheetName))
    }

    post("/v1/drive/saveReport") {
        val request = call.receive<DocCreateRequestDto>()
        call.respond(backendToolExecutor.saveReportToDrive(request.sessionId, request.title, request.bodyText, folder = null))
    }

    post("/v1/docs/create") {
        val request = call.receive<DocCreateRequestDto>()
        call.respond(backendToolExecutor.createGoogleDoc(request.sessionId, request.title, request.bodyText))
    }

    post("/v1/gmail/send") {
        val request = call.receive<SendEmailRequestDto>()
        call.respond(backendToolExecutor.sendEmail(request.sessionId, request.to, request.subject, request.body, request.attachmentDriveFileId))
    }

    post("/v1/calendar/events") {
        val request = call.receive<CalendarEventRequestDto>()
        call.respond(backendToolExecutor.createCalendarEvent(request.sessionId, request.title, request.startIso, request.endIso, request.description))
    }
}

/** Looks for the master workbook by name directly inside the app's Drive root folder (spec:
 *  "detect existing application spreadsheet") and only creates a new one on a genuine miss — never
 *  searches a subfolder, since [SheetsClient.createMasterSpreadsheet] always files it at the root.
 *  A real deployment should cache this id (e.g. next to the token row) instead of listing Drive on
 *  every call — left as a documented follow-up since this file-based TokenStore intentionally
 *  carries nothing beyond OAuth tokens; see backend/README.md's "known limitations". */
private suspend fun findOrCreateMasterSpreadsheet(
    accessToken: String,
    driveClient: DriveClient,
    sheetsClient: SheetsClient,
    rootFolderId: String
): String {
    val existing = driveClient.listFiles(accessToken, rootFolderId)
    return existing["files"]?.jsonArray
        ?.map { it.jsonObject }
        ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == SheetsClient.MASTER_SPREADSHEET_NAME }
        ?.get("id")?.jsonPrimitive?.contentOrNull
        ?: sheetsClient.createMasterSpreadsheet(accessToken, rootFolderId)
}
