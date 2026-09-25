package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.auth.TokenStore
import com.inventorysmartai.backend.gemini.GeminiClient
import com.inventorysmartai.backend.routes.dto.ServiceStatusDto
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
 * GET /v1/status?sessionId=...&verifyGemini=true
 * Backs the Phase 4 spec's "Google Services status screen", using its exact four Arabic status
 * labels. Drive/Sheets/Docs all key off the same `drive.file` grant (Sheets/Docs use `drive.file`
 * too — see google/GoogleWorkspaceClients.kt's class docs for why that scope alone covers all
 * three) so they always move together; Gmail and Calendar have their own narrower scopes and can
 * legitimately differ (e.g. a user who re-grants after Google adds a new scope mid-rollout).
 * `verifyGemini=true` spends one real, minimal Gemini call to confirm the key actually works
 * rather than just being present — off by default so opening the status screen doesn't burn quota
 * every time.
 */
fun Route.statusRoutes(tokenStore: TokenStore, geminiClient: GeminiClient) {
    get("/v1/status") {
        val sessionId = call.request.queryParameters["sessionId"].orEmpty()
        val verifyGemini = call.request.queryParameters["verifyGemini"] == "true"
        val stored = tokenStore.get(sessionId)

        val geminiStatus = if (!verifyGemini) {
            STATUS_CONNECTED // the process would have refused to start without a configured key
        } else {
            runCatching { geminiClient.createInteraction(inputText = "ping") }
                .fold(onSuccess = { STATUS_CONNECTED }, onFailure = { STATUS_ERROR })
        }

        val driveSheetsDocsStatus = when {
            stored == null -> STATUS_DISCONNECTED
            SCOPE_DRIVE_FILE in stored.grantedScopes -> STATUS_CONNECTED
            else -> STATUS_NEEDS_PERMISSION
        }
        val gmailStatus = when {
            stored == null -> STATUS_DISCONNECTED
            SCOPE_GMAIL_SEND in stored.grantedScopes -> STATUS_CONNECTED
            else -> STATUS_NEEDS_PERMISSION
        }
        val calendarStatus = when {
            stored == null -> STATUS_DISCONNECTED
            SCOPE_CALENDAR_EVENTS in stored.grantedScopes -> STATUS_CONNECTED
            else -> STATUS_NEEDS_PERMISSION
        }

        call.respond(
            ServiceStatusDto(
                gemini = geminiStatus,
                drive = driveSheetsDocsStatus,
                sheets = driveSheetsDocsStatus,
                docs = driveSheetsDocsStatus,
                gmail = gmailStatus,
                calendar = calendarStatus
            )
        )
    }
}
