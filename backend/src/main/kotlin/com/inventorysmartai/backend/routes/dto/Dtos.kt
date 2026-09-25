package com.inventorysmartai.backend.routes.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- Assistant ----------

@Serializable
data class SendMessageRequest(
    val sessionId: String,
    val conversationId: String,
    val message: String,
    /** Free-text summary of whatever's selected in the app right now (a product, an invoice, a
     *  purchase request, a report) — Phase 4 spec's "AI CHAT CONTEXT". The app builds this text;
     *  the backend treats it as opaque context, never as instructions to follow on their own. */
    val context: String? = null
)

@Serializable
data class ToolResultDto(val callId: String, val name: String, val resultText: String)

@Serializable
data class ContinueRequest(val conversationId: String, val results: List<ToolResultDto>)

@Serializable
data class ExecuteBackendToolRequest(
    val conversationId: String,
    val sessionId: String,
    val callId: String,
    val name: String,
    val arguments: JsonObject,
    val approved: Boolean
)

@Serializable
data class PendingToolCallDto(val callId: String, val name: String, val arguments: JsonObject, val site: String, val risk: String)

@Serializable
data class AssistantTurnResponseDto(
    val type: String, // "final" | "toolCalls"
    val conversationId: String,
    val text: String? = null,
    val calls: List<PendingToolCallDto>? = null
)

// ---------- Documents / extraction ----------

@Serializable
data class DocumentExtractionResponseDto(
    val documentType: String,
    val result: JsonObject
)

// ---------- Google auth ----------

@Serializable
data class LinkGoogleAccountRequest(val sessionId: String, val serverAuthCode: String)

@Serializable
data class GoogleAuthStatusDto(val linked: Boolean, val grantedScopes: List<String> = emptyList())

@Serializable
data class UnlinkGoogleAccountRequest(val sessionId: String)

// ---------- Google Workspace direct actions ----------

@Serializable
data class DriveUploadRequestDto(val sessionId: String, val folder: String? = null, val fileName: String, val mimeType: String, val base64Content: String)

@Serializable
data class SheetsExportRequestDto(val sessionId: String, val sheetName: String, val headerRow: List<String>, val rows: List<List<String>>)

@Serializable
data class DocCreateRequestDto(val sessionId: String, val title: String, val bodyText: String)

@Serializable
data class SendEmailRequestDto(val sessionId: String, val to: String, val subject: String, val body: String, val attachmentDriveFileId: String? = null)

@Serializable
data class CalendarEventRequestDto(val sessionId: String, val title: String, val startIso: String, val endIso: String, val description: String? = null)

// ---------- Service status ----------

@Serializable
data class ServiceStatusDto(
    val gemini: String, // "متصل" | "غير متصل" | "خطأ"
    val drive: String,
    val sheets: String,
    val docs: String,
    val gmail: String,
    val calendar: String
)

// ---------- Errors ----------

@Serializable
data class ErrorDetailDto(val code: String, val message: String)

@Serializable
data class ErrorResponseDto(val error: ErrorDetailDto)
