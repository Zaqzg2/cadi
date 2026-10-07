package com.inventorysmartai.backend

import com.inventorysmartai.backend.ai.AiGateway
import com.inventorysmartai.backend.ai.AiUnavailableException
import com.inventorysmartai.backend.ai.DocumentExtractor
import com.inventorysmartai.backend.ai.KtorAiTransport
import com.inventorysmartai.backend.ai.PdfNeedsImagesException
import com.inventorysmartai.backend.ai.RequestRejectedException
import com.inventorysmartai.backend.audit.AuditLog
import com.inventorysmartai.backend.auth.GoogleAuthException
import com.inventorysmartai.backend.auth.GoogleAuthService
import com.inventorysmartai.backend.config.ConfigException
import com.inventorysmartai.backend.config.ServerConfig
import com.inventorysmartai.backend.google.CalendarClient
import com.inventorysmartai.backend.google.DocsClient
import com.inventorysmartai.backend.google.DriveClient
import com.inventorysmartai.backend.google.GmailClient
import com.inventorysmartai.backend.google.GoogleApiException
import com.inventorysmartai.backend.google.SheetsClient
import com.inventorysmartai.backend.google.WorkspaceService
import com.inventorysmartai.backend.routes.GOOGLE_LINK_HEADER
import com.inventorysmartai.backend.routes.aiRoutes
import com.inventorysmartai.backend.routes.documentRoutes
import com.inventorysmartai.backend.routes.googleAuthRoutes
import com.inventorysmartai.backend.routes.googleWorkspaceRoutes
import com.inventorysmartai.backend.routes.respondError
import com.inventorysmartai.backend.routes.statusRoutes
import com.inventorysmartai.backend.security.RequestGuard
import com.inventorysmartai.backend.security.TokenSealer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.system.exitProcess

// slf4j directly: one logging API for the whole module (no Ktor-version-specific accessor involved).
private val serverLog = LoggerFactory.getLogger("com.inventorysmartai.backend.Server")

fun main() {
    val config = try {
        ServerConfig.fromEnv()
    } catch (e: ConfigException) {
        System.err.println()
        System.err.println("The server cannot start. Fix these environment variables:")
        e.problems.forEach { System.err.println("  - $it") }
        System.err.println()
        System.err.println("See backend/README.md and backend/.env.example")
        exitProcess(1)
    }
    embeddedServer(Netty, port = config.port, module = { backendModule(config) }).start(wait = true)
}

fun Application.backendModule(config: ServerConfig) {
    config.describe().forEach { serverLog.info(it) }

    // One shared outbound client (AI providers, Google token endpoint, every Workspace REST call). Real per-attempt
    // time limits are enforced by AiGateway with withTimeout; the values here are only a safety net above them.
    val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = config.totalAiTimeoutMs + 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = config.totalAiTimeoutMs + 30_000
        }
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val auditLog = AuditLog()
    val guard = RequestGuard(config)

    val transport = KtorAiTransport(httpClient)
    val gateway = AiGateway(config.providers, transport, config.providerTimeoutMs, config.totalAiTimeoutMs)
    val extractor = DocumentExtractor(gateway, transport)

    val sealer = config.tokenEncryptionSecret?.let { TokenSealer(it) }
    val googleAuth = GoogleAuthService(httpClient, config.google, sealer)
    val driveClient = DriveClient(httpClient)
    val workspace = WorkspaceService(
        auth = googleAuth,
        drive = driveClient,
        sheets = SheetsClient(httpClient, driveClient),
        docs = DocsClient(httpClient, driveClient),
        gmail = GmailClient(httpClient),
        calendar = CalendarClient(httpClient),
        audit = auditLog
    )

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }

    install(CallLogging) {
        level = Level.INFO
    }

    if (config.corsAllowedHosts.isNotEmpty()) {
        install(CORS) {
            config.corsAllowedHosts.forEach { allowHost(it, schemes = listOf("https")) }
            allowHeader(HttpHeaders.ContentType)
            allowHeader(RequestGuard.APP_KEY_HEADER)
            allowHeader(GOOGLE_LINK_HEADER)
        }
    }

    install(StatusPages) {
        // ---- AI ----
        exception<AiUnavailableException> { call, cause ->
            cause.retryAfterSeconds?.let { call.response.header(HttpHeaders.RetryAfter, it.toString()) }
            when {
                cause.allRateLimited -> call.respondError(
                    HttpStatusCode.TooManyRequests,
                    "AI_QUOTA_EXCEEDED",
                    "تم تجاوز حدّ الاستخدام المجاني لدى مزوّدي الذكاء الاصطناعي مؤقتًا. حاول بعد قليل."
                )
                cause.allAuthFailures -> call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "AI_INVALID_KEY",
                    "مفاتيح الذكاء الاصطناعي على الخادم غير صالحة. راجع متغيرات البيئة على الخادم."
                )
                else -> call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "AI_UNAVAILABLE",
                    "تعذّر الحصول على ردّ من مزوّدي الذكاء الاصطناعي حاليًا. حاول بعد قليل."
                )
            }
        }
        exception<PdfNeedsImagesException> { call, _ ->
            call.respondError(
                HttpStatusCode.UnprocessableEntity,
                "PDF_NEEDS_IMAGES",
                "تعذّرت قراءة ملف PDF على الخادم. أرسل صفحاته كصور."
            )
        }
        exception<RequestRejectedException> { call, cause ->
            call.respondError(HttpStatusCode.fromValue(cause.status), cause.code, cause.message)
        }

        // ---- Google ----
        exception<GoogleAuthException.NotConfigured> { call, _ ->
            call.respondError(HttpStatusCode.ServiceUnavailable, "GOOGLE_NOT_CONFIGURED", "خدمات Google غير مفعّلة على الخادم.")
        }
        exception<GoogleAuthException.NotLinked> { call, _ ->
            call.respondError(HttpStatusCode.Unauthorized, "GOOGLE_NOT_LINKED", "يجب ربط حساب Google أولًا")
        }
        exception<GoogleAuthException.ExchangeFailed> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, "GOOGLE_AUTH_EXCHANGE_FAILED", cause.message ?: "فشل ربط حساب Google")
        }
        exception<GoogleAuthException.RefreshFailed> { call, cause ->
            call.respondError(HttpStatusCode.Unauthorized, "GOOGLE_AUTH_REFRESH_FAILED", cause.message ?: "يجب إعادة ربط حساب Google")
        }
        exception<GoogleApiException> { call, cause ->
            call.respondError(HttpStatusCode.BadGateway, "GOOGLE_${cause.service.uppercase()}_FAILED", "فشل الاتصال بخدمة ${cause.service}: ${cause.message}")
        }

        // ---- generic ----
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, "BAD_REQUEST", "الطلب غير مفهوم أو بصيغة غير صحيحة")
        }
        exception<SerializationException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, "BAD_REQUEST", "الطلب غير مفهوم أو بصيغة غير صحيحة")
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, "BAD_REQUEST", cause.message ?: "طلب غير صالح")
        }
        exception<Throwable> { call, cause ->
            // Never echo internals to a client; the log has the stack trace.
            serverLog.error("Unhandled error", cause)
            call.respondError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", "خطأ داخلي غير متوقع في الخادم")
        }
    }

    routing {
        aiRoutes(gateway, guard, auditLog)
        documentRoutes(extractor, guard, auditLog, config.maxUploadBytes)
        googleAuthRoutes(googleAuth, guard)
        googleWorkspaceRoutes(workspace, guard, config.maxUploadBytes.toLong())
        statusRoutes(gateway, googleAuth, guard)

        // Public on purpose (no key): platform health checks and a browser sanity check. Reveals nothing.
        get("/health") { call.respond(mapOf("status" to "ok")) }
        get("/") { call.respond(buildJsonObject { put("name", "inventory-smart-ai-backend"); put("status", "ok") }) }
    }
}
