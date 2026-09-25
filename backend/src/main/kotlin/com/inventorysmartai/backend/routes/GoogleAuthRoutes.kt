package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.auth.GoogleAuthService
import com.inventorysmartai.backend.auth.TokenStore
import com.inventorysmartai.backend.routes.dto.GoogleAuthStatusDto
import com.inventorysmartai.backend.routes.dto.LinkGoogleAccountRequest
import com.inventorysmartai.backend.routes.dto.UnlinkGoogleAccountRequest
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.googleAuthRoutes(googleAuthService: GoogleAuthService, tokenStore: TokenStore) {
    post("/v1/auth/google/link") {
        val request = call.receive<LinkGoogleAccountRequest>()
        val stored = googleAuthService.linkAccount(request.sessionId, request.serverAuthCode)
        call.respond(GoogleAuthStatusDto(linked = true, grantedScopes = stored.grantedScopes))
    }

    get("/v1/auth/google/status") {
        val sessionId = call.request.queryParameters["sessionId"] ?: run {
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to mapOf("code" to "MISSING_SESSION_ID", "message" to "sessionId query parameter is required")))
            return@get
        }
        val stored = tokenStore.get(sessionId)
        call.respond(GoogleAuthStatusDto(linked = stored != null, grantedScopes = stored?.grantedScopes.orEmpty()))
    }

    post("/v1/auth/google/unlink") {
        val sessionId = call.receive<UnlinkGoogleAccountRequest>().sessionId
        googleAuthService.unlink(sessionId)
        call.respond(GoogleAuthStatusDto(linked = false))
    }
}
