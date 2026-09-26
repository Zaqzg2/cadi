package com.inventorysmartai.backend

import com.inventorysmartai.backend.assistant.AssistantOrchestrator
import com.inventorysmartai.backend.assistant.InMemoryConversationStore
import com.inventorysmartai.backend.audit.AuditLog
import com.inventorysmartai.backend.auth.FileTokenStore
import com.inventorysmartai.backend.auth.GoogleAuthException
import com.inventorysmartai.backend.auth.GoogleAuthService
import com.inventorysmartai.backend.config.AppConfig
import com.inventorysmartai.backend.gemini.GeminiClient
import com.inventorysmartai.backend.gemini.GeminiException
import com.inventorysmartai.backend.google.BackendToolExecutor
import com.inventorysmartai.backend.google.CalendarClient
import com.inventorysmartai.backend.google.DocsClient
import com.inventorysmartai.backend.google.DriveClient
import com.inventorysmartai.backend.google.GmailClient
import com.inventorysmartai.backend.google.GoogleApiException
import com.inventorysmartai.backend.google.SheetsClient
import com.inventorysmartai.backend.routes.assistantRoutes
import com.inventorysmartai.backend.routes.documentRoutes
import com.inventorysmartai.backend.routes.dto.ErrorDetailDto
import com.inventorysmartai.backend.routes.dto.ErrorResponseDto
import com.inventorysmartai.backend.routes.googleAuthRoutes
import com.inventorysmartai.backend.routes.googleWorkspaceRoutes
import com.inventorysmartai.backend.routes.statusRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    embeddedServer(Netty, port = AppConfig.port, module = Application::module).start(wait = true)
}

fun Application.module() {
    // ---- Shared HTTP client used for every outbound call (Gemini, Google token endpoint, every
    // Google Workspace REST call). One generous, uniform timeout: document extraction can
    // legitimately take longer than a chat turn, so this is sized for the slower case rather than
    // configured per call (see GeminiClient's class doc for why per-request overrides were
    // deliberately avoided). ----
    val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val tokenStore = FileTokenStore()
    val auditLog = AuditLog()
    val geminiClient = GeminiClient(httpClient)
    val googleAuthService = GoogleAuthService(httpClient, tokenStore)
    val driveClient = DriveClient(httpClient)
    val sheetsClient = SheetsClient(httpClient, driveClient)
    val docsClient = DocsClient(httpClient, driveClient)
    val gmailClient = GmailClient(httpClient)
    val calendarClient = CalendarClient(httpClient)
    val backendToolExecutor = BackendToolExecutor(googleAuthService, driveClient, sheetsClient, docsClient, gmailClient, calendarClient, auditLog)
    val assistantOrchestrator = AssistantOrchestrator(geminiClient, InMemoryConversationStore())

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }

    install(CallLogging) {
        level = Level.INFO
    }

    if (AppConfig.corsAllowedHosts.isNotEmpty()) {
        install(CORS) {
            AppConfig.corsAllowedHosts.forEach { allowHost(it, schemes = listOf("https")) }
            allowHeader(io.ktor.http.HttpHeaders.ContentType)
        }
    }

    install(StatusPages) {
        exception<GeminiException.Timeout> { call, _ -> call.respondError(HttpStatusCode.GatewayTimeout, "GEMINI_TIMEOUT", "انتهت مهلة الاتصال بالذكاء الاصطناعي") }
        exception<GeminiException.NetworkError> { call, _ -> call.respondError(HttpStatusCode.BadGateway, "GEMINI_NETWORK_ERROR", "تعذر الوصول إلى خدمة الذكاء الاصطناعي") }
        exception<GeminiException.RateLimited> { call, _ -> call.respondError(HttpStatusCode.TooManyRequests, "GEMINI_RATE_LIMITED", "تم تجاوز الحد المسموح من الطلبات، حاول لاحقًا") }
        exception<GeminiException.InvalidOutput> { call, cause -> call.respondError(HttpStatusCode.BadGateway, "GEMINI_INVALID_OUTPUT", cause.message ?: "استجابة غير صالحة من الذكاء الاصطناعي") }
        exception<GeminiException.SchemaMismatch> { call, cause -> call.respondError(HttpStatusCode.BadGateway, "GEMINI_SCHEMA_MISMATCH", cause.message ?: "استجابة لا تطابق البنية المتوقعة") }
        exception<GeminiException.ApiError> { call, cause -> call.respondError(HttpStatusCode.BadGateway, "GEMINI_API_ERROR", cause.message ?: "خطأ من واجهة الذكاء الاصطناعي") }

        exception<GoogleAuthException.NotLinked> { call, _ -> call.respondError(HttpStatusCode.Unauthorized, "GOOGLE_NOT_LINKED", "يجب ربط حساب Google أولًا") }
        exception<GoogleAuthException.ExchangeFailed> { call, cause -> call.respondError(HttpStatusCode.BadRequest, "GOOGLE_AUTH_EXCHANGE_FAILED", cause.message ?: "فشل ربط حساب Google") }
        exception<GoogleAuthException.RefreshFailed> { call, cause -> call.respondError(HttpStatusCode.Unauthorized, "GOOGLE_AUTH_REFRESH_FAILED", cause.message ?: "يجب إعادة ربط حساب Google") }

        exception<GoogleApiException> { call, cause ->
            val code = "GOOGLE_${cause.service.uppercase()}_FAILED"
            call.respondError(HttpStatusCode.BadGateway, code, "فشل الاتصال بخدمة ${cause.service}: ${cause.message}")
        }

        exception<IllegalArgumentException> { call, cause -> call.respondError(HttpStatusCode.BadRequest, "BAD_REQUEST", cause.message ?: "طلب غير صالح") }
        exception<IllegalStateException> { call, cause -> call.respondError(HttpStatusCode.Conflict, "INVALID_STATE", cause.message ?: "حالة غير صالحة") }
        exception<Throwable> { call, cause -> call.respondError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", cause.message ?: "خطأ داخلي غير متوقع") }
    }

    routing {
        assistantRoutes(assistantOrchestrator, backendToolExecutor)
        documentRoutes(geminiClient, auditLog)
        googleAuthRoutes(googleAuthService, tokenStore)
        googleWorkspaceRoutes(googleAuthService, driveClient, sheetsClient, backendToolExecutor)
        statusRoutes(tokenStore, geminiClient)

        get("/health") { call.respond(mapOf("status" to "ok")) }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) {
    respond(status, ErrorResponseDto(ErrorDetailDto(code, message)))
}
