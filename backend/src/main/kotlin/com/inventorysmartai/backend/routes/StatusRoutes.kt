package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.ai.AiGateway
import com.inventorysmartai.backend.auth.GoogleAuthService
import com.inventorysmartai.backend.routes.dto.ServiceStatusDto
import com.inventorysmartai.backend.security.Bucket
import com.inventorysmartai.backend.security.RequestGuard
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val STATUS_CONNECTED = "متصل"
private const val STATUS_DISCONNECTED = "غير متصل"
private const val STATUS_ERROR = "خطأ"
private const val STATUS_NEEDS_PERMISSION = "يتطلب صلاحية"

private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
private const val SCOPE_GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send"
private const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"

/**
 * GET /v1/status?sessionId=...&verifyAi=true — backs the "Google/AI services status" screen with its four Arabic labels.
 * Drive/Sheets/Docs all follow the same `drive.file` grant so they always move together; Gmail and Calendar have their own
 * narrower scopes and can legitimately differ. `verifyAi=true` spends one real, tiny AI request to prove a key and a model
 * work (off by default so opening the screen never burns quota).
 */
fun Route.statusRoutes(gateway: AiGateway, auth: GoogleAuthService, guard: RequestGuard) {
    get("/v1/status") {
        if (!guard.admit(call, Bucket.STATUS)) return@get
        val sessionId = call.request.queryParameters["sessionId"].orEmpty()
        val verifyAi = call.request.queryParameters["verifyAi"] == "true"
        val scopes = auth.scopesOf(sessionId, call.request.headers[GOOGLE_LINK_HEADER])

        val providers = gateway.status()
        val aiStatus = when {
            verifyAi -> if (runCatching { gateway.ping() }.isSuccess) STATUS_CONNECTED else STATUS_ERROR
            providers.any { it.ready } -> STATUS_CONNECTED
            else -> STATUS_ERROR
        }

        fun googleStatus(scope: String): String = when {
            scopes == null -> STATUS_DISCONNECTED
            scope in scopes -> STATUS_CONNECTED
            else -> STATUS_NEEDS_PERMISSION
        }
        val driveSheetsDocs = googleStatus(SCOPE_DRIVE_FILE)

        call.respond(
            ServiceStatusDto(
                ai = aiStatus,
                aiProviders = providers.filter { it.ready }.map { it.label },
                googleConfigured = auth.isConfigured,
                drive = driveSheetsDocs,
                sheets = driveSheetsDocs,
                docs = driveSheetsDocs,
                gmail = googleStatus(SCOPE_GMAIL_SEND),
                calendar = googleStatus(SCOPE_CALENDAR_EVENTS)
            )
        )
    }
}
