package com.inventorysmartai.backend.routes.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- Documents / extraction ----------

@Serializable
data class DocumentExtractionResponseDto(
    val documentType: String,
    /** The extraction document (header?, rows, documentWarnings) — the shape the app's AiExtractionDocument reads. */
    val result: JsonObject,
    /** Which provider/model produced it, and whether OCR read the text first — informational only. */
    val provider: String? = null,
    val model: String? = null,
    val usedOcr: Boolean = false
)

// ---------- Google auth ----------

@Serializable
data class SessionRequest(val sessionId: String)

@Serializable
data class LinkGoogleAccountRequest(val sessionId: String, val serverAuthCode: String)

/** [linkToken] is present ONLY in the answer to /link: the sealed token the phone must keep (see security/TokenSealer.kt). */
@Serializable
data class GoogleAuthStatusDto(val linked: Boolean, val grantedScopes: List<String> = emptyList(), val linkToken: String? = null)

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
    val ai: String, // "متصل" | "خطأ"
    /** Labels of the AI providers that are ready right now (e.g. ["Mistral", "Groq"]). */
    val aiProviders: List<String> = emptyList(),
    /** false when the server has no Google OAuth client — the Google rows below then read "غير متصل" for everyone. */
    val googleConfigured: Boolean = true,
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
