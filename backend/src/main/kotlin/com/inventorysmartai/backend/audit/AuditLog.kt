package com.inventorysmartai.backend.audit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Backend-side half of the AUDIT requirement — records AI and external-service actions with a timestamp and the relevant
 * ids: AI_IMPORT, GOOGLE_DRIVE_UPLOAD, GOOGLE_SHEET_EXPORT, GOOGLE_DOC_CREATE, GMAIL_SEND, CALENDAR_CREATE.
 *
 * It is a structured log LINE (one JSON object per line, to stdout via Logback — see resources/logback.xml), not a
 * database table: this backend is deliberately stateless. Every one of these actions ALSO gets its own AuditLogEntity row
 * in the app's local Room database; this log exists for operational visibility (how often AI is called, which provider
 * answered, error rates) and shows up in the hosting platform's log viewer. Never put a secret, a token or document
 * content in [details].
 */
class AuditLog {
    private val logger: Logger = LoggerFactory.getLogger("AUDIT")

    fun record(action: String, sessionId: String, details: Map<String, String> = emptyMap()) {
        val entry = buildJsonObject {
            put("timestamp", Instant.now().toString())
            put("action", action)
            put("sessionId", sessionId)
            details.forEach { (k, v) -> put(k, v) }
        }
        logger.info(Json.encodeToString(JsonObject.serializer(), entry))
    }
}
