package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.auth.GoogleAuthService
import com.inventorysmartai.backend.google.GoogleSession
import com.inventorysmartai.backend.routes.dto.GoogleAuthStatusDto
import com.inventorysmartai.backend.routes.dto.LinkGoogleAccountRequest
import com.inventorysmartai.backend.routes.dto.UnlinkGoogleAccountRequest
import com.inventorysmartai.backend.security.Bucket
import com.inventorysmartai.backend.security.RequestGuard
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** The header carrying the sealed Google link token the phone stores (see security/TokenSealer.kt). */
const val GOOGLE_LINK_HEADER = "X-Google-Link"

/** The caller's Google session: the app's [sessionId] plus whatever link token the request carries. */
fun ApplicationCall.googleSession(sessionId: String): GoogleSession = GoogleSession(sessionId, request.headers[GOOGLE_LINK_HEADER])

fun Route.googleAuthRoutes(auth: GoogleAuthService, guard: RequestGuard) {
    post("/v1/auth/google/link") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<LinkGoogleAccountRequest>()
        val result = auth.linkAccount(request.sessionId, request.serverAuthCode)
        call.respond(GoogleAuthStatusDto(linked = true, grantedScopes = result.grantedScopes, linkToken = result.linkToken))
    }

    get("/v1/auth/google/status") {
        if (!guard.admit(call, Bucket.STATUS)) return@get
        val sessionId = call.request.queryParameters["sessionId"].orEmpty()
        val scopes = auth.scopesOf(sessionId, call.request.headers[GOOGLE_LINK_HEADER])
        call.respond(GoogleAuthStatusDto(linked = scopes != null, grantedScopes = scopes.orEmpty()))
    }

    post("/v1/auth/google/unlink") {
        if (!guard.admit(call, Bucket.WORKSPACE)) return@post
        val request = call.receive<UnlinkGoogleAccountRequest>()
        auth.unlink(request.sessionId, call.request.headers[GOOGLE_LINK_HEADER])
        call.respond(GoogleAuthStatusDto(linked = false))
    }
}
