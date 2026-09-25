package com.inventorysmartai.app.data.remote.dto

import com.squareup.moshi.JsonClass

// ---------- Assistant ----------

@JsonClass(generateAdapter = true)
data class SendMessageRequest(val sessionId: String, val conversationId: String, val message: String, val context: String? = null)

@JsonClass(generateAdapter = true)
data class ToolResultDto(val callId: String, val name: String, val resultText: String)

@JsonClass(generateAdapter = true)
data class ContinueRequest(val conversationId: String, val results: List<ToolResultDto>)

@JsonClass(generateAdapter = true)
data class ExecuteBackendToolRequest(
    val conversationId: String,
    val sessionId: String,
    val callId: String,
    val name: String,
    val arguments: Map<String, Any?>,
    val approved: Boolean
)

@JsonClass(generateAdapter = true)
data class PendingToolCallDto(val callId: String, val name: String, val arguments: Map<String, Any?> = emptyMap(), val site: String, val risk: String)

@JsonClass(generateAdapter = true)
data class AssistantTurnResponseDto(
    val type: String,
    val conversationId: String,
    val text: String? = null,
    val calls: List<PendingToolCallDto>? = null
)

// ---------- Documents ----------

@JsonClass(generateAdapter = true)
data class DocumentExtractionResponseDto(
    val documentType: String,
    val result: com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
)

// ---------- Google auth ----------

@JsonClass(generateAdapter = true)
data class LinkGoogleAccountRequest(val sessionId: String, val serverAuthCode: String)

@JsonClass(generateAdapter = true)
data class GoogleAuthStatusDto(val linked: Boolean, val grantedScopes: List<String> = emptyList())

@JsonClass(generateAdapter = true)
data class UnlinkGoogleAccountRequest(val sessionId: String)

@JsonClass(generateAdapter = true)
data class SessionIdRequest(val sessionId: String)

// ---------- Google Workspace direct actions ----------

@JsonClass(generateAdapter = true)
data class SheetsExportRequestDto(val sessionId: String, val sheetName: String, val headerRow: List<String>, val rows: List<List<String>>)

@JsonClass(generateAdapter = true)
data class DocCreateRequestDto(val sessionId: String, val title: String, val bodyText: String)

@JsonClass(generateAdapter = true)
data class SendEmailRequestDto(val sessionId: String, val to: String, val subject: String, val body: String, val attachmentDriveFileId: String? = null)

@JsonClass(generateAdapter = true)
data class CalendarEventRequestDto(val sessionId: String, val title: String, val startIso: String, val endIso: String, val description: String? = null)

@JsonClass(generateAdapter = true)
data class DriveUploadResultDto(val fileId: String, val webViewLink: String? = null)

@JsonClass(generateAdapter = true)
data class CalendarEventResultDto(val eventId: String? = null, val htmlLink: String? = null)

@JsonClass(generateAdapter = true)
data class SpreadsheetIdDto(val spreadsheetId: String, val status: String? = null)

// ---------- Service status ----------

@JsonClass(generateAdapter = true)
data class ServiceStatusDto(
    val gemini: String,
    val drive: String,
    val sheets: String,
    val docs: String,
    val gmail: String,
    val calendar: String
)

// ---------- Errors ----------

@JsonClass(generateAdapter = true)
data class ErrorDetailDto(val code: String, val message: String)

@JsonClass(generateAdapter = true)
data class ErrorResponseDto(val error: ErrorDetailDto)
