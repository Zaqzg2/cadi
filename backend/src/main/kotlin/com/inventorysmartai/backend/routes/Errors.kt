package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.routes.dto.ErrorDetailDto
import com.inventorysmartai.backend.routes.dto.ErrorResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** The one error shape every endpoint uses: `{"error": {"code": "...", "message": "..."}}` (message is Arabic, user-facing). */
suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) {
    respond(status, ErrorResponseDto(ErrorDetailDto(code, message)))
}
