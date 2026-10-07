package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.google.WorkspaceService
import com.inventorysmartai.backend.routes.dto.CalendarEventRequestDto
import com.inventorysmartai.backend.routes.dto.DocCreateRequestDto
import com.inventorysmartai.backend.routes.dto.DriveUploadRequestDto
import com.inventorysmartai.backend.routes.dto.SendEmailRequestDto
import com.inventorysmartai.backend.routes.dto.SessionRequest
import com.inventorysmartai.backend.routes.dto.SheetsExportRequestDto
import com.inventorysmartai.backend.security.Bucket
import com.inventorysmartai.backend.security.RequestGuard
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The REST face of [WorkspaceService]: the Reports and Data-Center screens call these directly, and the assistant's four
 * Workspace tools end up here too (the app executes every tool itself), so "save this report to Drive" behaves the same
 * whether a button or the assistant asked for it. Every write here is something the app only calls AFTER the user tapped
 * an explicit confirmation — this server trusts that and does not ask again.
 *
 * [maxBodyBytes] bounds the Drive upload (a base64 body is ~4/3 the size of the file).
 */
fun Route.googleWorkspaceRoutes(workspace: WorkspaceService, guard: RequestGuard, maxBodyBytes: Long) {
    post("/v1/drive/ensureFolders") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<SessionRequest>()
        call.respond(workspace.ensureFolders(call.googleSession(request.sessionId)))
    }

    get("/v1/drive/list") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@get
        val sessionId = call.request.queryParameters["sessionId"].orEmpty()
        val folderId = call.request.queryParameters["folderId"].orEmpty()
        call.respond(workspace.listFiles(call.googleSession(sessionId), folderId))
    }

    post("/v1/drive/upload") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        if (!guard.bodyWithin(call, maxBodyBytes * 4 / 3 + 4096)) return@post
        val request = call.receive<DriveUploadRequestDto>()
        call.respond(workspace.uploadFile(call.googleSession(request.sessionId), request.folder, request.fileName, request.mimeType, request.base64Content))
    }

    post("/v1/sheets/ensureMaster") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<SessionRequest>()
        val spreadsheetId = workspace.ensureMasterSpreadsheet(call.googleSession(request.sessionId))
        call.respond(buildJsonObject { put("spreadsheetId", spreadsheetId) })
    }

    post("/v1/sheets/export") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<SheetsExportRequestDto>()
        call.respond(workspace.exportSheet(call.googleSession(request.sessionId), request.sheetName, request.headerRow, request.rows))
    }

    get("/v1/sheets/read") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@get
        val sessionId = call.request.queryParameters["sessionId"].orEmpty()
        val spreadsheetId = call.request.queryParameters["spreadsheetId"].orEmpty()
        val sheetName = call.request.queryParameters["sheetName"].orEmpty()
        call.respond(workspace.readSheet(call.googleSession(sessionId), spreadsheetId, sheetName))
    }

    post("/v1/drive/saveReport") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<DocCreateRequestDto>()
        call.respond(workspace.saveReportToDrive(call.googleSession(request.sessionId), request.title, request.bodyText, folder = null))
    }

    post("/v1/docs/create") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<DocCreateRequestDto>()
        call.respond(workspace.createGoogleDoc(call.googleSession(request.sessionId), request.title, request.bodyText))
    }

    post("/v1/gmail/send") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<SendEmailRequestDto>()
        call.respond(workspace.sendEmail(call.googleSession(request.sessionId), request.to, request.subject, request.body, request.attachmentDriveFileId))
    }

    post("/v1/calendar/events") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<CalendarEventRequestDto>()
        call.respond(workspace.createCalendarEvent(call.googleSession(request.sessionId), request.title, request.startIso, request.endIso, request.description))
    }
}
