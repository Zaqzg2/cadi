package com.inventorysmartai.backend.audit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Backend-side half of the Phase 4 spec's AUDIT requirement — records AI and external-service
 * actions with a timestamp and the relevant entity ids: AI_IMPORT, AI_ANALYSIS,
 * GOOGLE_DRIVE_UPLOAD, GOOGLE_SHEET_EXPORT, GOOGLE_DOC_CREATE, GMAIL_SEND, CALENDAR_CREATE.
 *
 * This is deliberately a structured *log line* (one JSON object per line, to stdout via Logback —
 * see resources/logback.xml), not a database table: this backend has no database of its own (see
 * TokenStore's doc comment on why), and every one of these actions ALSO gets its own
 * `AuditLogEntity` row in the app's local Room database on the Android side (the app already has
 * `AuditRepository` from Phase 2/3 — see the app's `data/assistant/AssistantRepositoryImpl.kt`
 * and `data/google/GoogleWorkspaceRepositoryImpl.kt`, which write the on-device audit row after a
 * successful call returns). This backend-side log exists for operational visibility (rate of AI
 * calls, which sessions hit which Google API, error-rate monitoring) — pipe it to whatever log
 * aggregation the real deployment uses.
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
        logger.info(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), entry))
    }
}
