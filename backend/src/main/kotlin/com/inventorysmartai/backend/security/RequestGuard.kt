package com.inventorysmartai.backend.security

import com.inventorysmartai.backend.config.ServerConfig
import com.inventorysmartai.backend.routes.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import java.security.MessageDigest

enum class Bucket(val id: String) { CHAT("chat"), EXTRACT("extract"), WORKSPACE("workspace"), STATUS("status") }

/**
 * Everything checked before a route does any work, in one place:
 *  1. the app key (header X-App-Key, constant-time compare) — this server holds paid/limited provider keys, so an
 *     anonymous caller must not reach them;
 *  2. a per-client, per-bucket rate limit;
 *  3. for the two AI buckets, a global per-day ceiling.
 * Call [admit] first in every route; when it returns false the response has ALREADY been sent — just return.
 *
 * Honest limit: an app key shipped inside an APK can be extracted by someone determined. It stops casual abuse and
 * scanners; the rate limits and the daily cap are what bound the damage if it leaks (rotate APP_API_KEY then).
 */
class RequestGuard(
    private val config: ServerConfig,
    private val limiter: RateLimiter = RateLimiter(),
    private val daily: DailyCounter = DailyCounter(config.dailyAiRequestCap)
) {
    suspend fun admit(call: ApplicationCall, bucket: Bucket): Boolean {
        if (!keyAccepted(call.request.headers[APP_KEY_HEADER])) {
            call.respondError(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "مفتاح التطبيق مفقود أو غير صحيح")
            return false
        }
        val limit = when (bucket) {
            Bucket.CHAT -> config.chatPerMinute
            Bucket.EXTRACT -> config.extractPerMinute
            Bucket.WORKSPACE -> config.workspacePerMinute
            Bucket.STATUS -> STATUS_PER_MINUTE
        }
        val client = clientKeyFrom(call.request.headers["X-Forwarded-For"], call.request.headers["X-Real-IP"])
        val wait = limiter.acquire("${bucket.id}|$client", limit)
        if (wait > 0) {
            call.response.header(HttpHeaders.RetryAfter, wait.toString())
            call.respondError(HttpStatusCode.TooManyRequests, "RATE_LIMITED", "طلبات كثيرة خلال وقت قصير. انتظر $wait ثانية ثم حاول مرة أخرى.")
            return false
        }
        if ((bucket == Bucket.CHAT || bucket == Bucket.EXTRACT) && !daily.tryTake()) {
            call.respondError(
                HttpStatusCode.TooManyRequests,
                "DAILY_CAP_REACHED",
                "بلغ الخادم حدّه اليومي لطلبات الذكاء الاصطناعي. يتجدّد عند منتصف الليل بتوقيت UTC."
            )
            return false
        }
        return true
    }

    /** Refuses an oversized body by its declared Content-Length before reading any of it. */
    suspend fun bodyWithin(call: ApplicationCall, maxBytes: Long): Boolean {
        val length = call.request.headers[HttpHeaders.ContentLength]?.trim()?.toLongOrNull()
        if (length != null && length > maxBytes) {
            call.respondError(HttpStatusCode.PayloadTooLarge, "PAYLOAD_TOO_LARGE", "حجم الطلب أكبر من الحد المسموح (${maxBytes / (1024 * 1024)} ميغابايت)")
            return false
        }
        return true
    }

    internal fun keyAccepted(provided: String?): Boolean {
        val expected = config.appApiKey ?: return true // APP_AUTH_DISABLED=true (development only)
        if (provided == null) return false
        return MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    }

    /**
     * Behind a hosting proxy every connection comes from the proxy, so the real client is read from the forwarding
     * header. The LAST entry is the one the platform's own proxy appended; earlier entries are client-supplied and
     * trivially forged, so they are ignored.
     */
    internal fun clientKeyFrom(forwardedFor: String?, realIp: String?): String {
        if (config.trustProxyHeaders) {
            val last = forwardedFor?.split(',')?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
            if (last != null) return last
            realIp?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return "direct"
    }

    companion object {
        const val APP_KEY_HEADER = "X-App-Key"
        const val STATUS_PER_MINUTE = 60
    }
}
