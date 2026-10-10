package com.inventorysmartai.app.data.remote.dto

import com.squareup.moshi.JsonClass

// ---------- Documents ----------

@JsonClass(generateAdapter = true)
data class DocumentExtractionResponseDto(
    val documentType: String,
    val result: com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument,
    /** Which provider/model produced it (informational). */
    val provider: String? = null,
    val model: String? = null,
    val usedOcr: Boolean = false
)

/** What /v1/documents/read returns: the text of a photo or PDF, tables as Markdown tables. */
@JsonClass(generateAdapter = true)
data class DocumentReadResponseDto(
    val text: String,
    val pages: Int = 0,
    val provider: String? = null,
    val usedOcr: Boolean = false
)

// ---------- Google auth ----------

@JsonClass(generateAdapter = true)
data class LinkGoogleAccountRequest(val sessionId: String, val serverAuthCode: String)

/** [linkToken] is present only in the answer to /link: the sealed token the app keeps (see data/backend/BackendCredentials.kt). */
@JsonClass(generateAdapter = true)
data class GoogleAuthStatusDto(val linked: Boolean, val grantedScopes: List<String> = emptyList(), val linkToken: String? = null)

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
    val ai: String,
    /** Labels of the AI providers ready on the server right now (e.g. ["Mistral", "Groq"]). */
    val aiProviders: List<String> = emptyList(),
    /** false when the server has no Google OAuth client at all. */
    val googleConfigured: Boolean = true,
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
