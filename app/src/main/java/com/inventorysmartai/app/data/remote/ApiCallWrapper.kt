package com.inventorysmartai.app.data.remote

import com.inventorysmartai.app.data.remote.dto.ErrorResponseDto
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Every Phase 4 repository that calls [BackendApi] goes through this — one place that turns the
 * three failure shapes the spec's "ERROR HANDLING" section cares about (network unavailable, a
 * structured backend error, anything else) into a single [Result] the caller can show a specific
 * Arabic message for, via [BackendFailure.messageAr].
 */
sealed class BackendFailure(val messageAr: String, cause: Throwable? = null) : Exception(messageAr, cause) {
    class NetworkUnavailable(cause: Throwable) : BackendFailure("يتطلب اتصالًا بالإنترنت", cause)
    class Structured(val code: String, message: String) : BackendFailure(message)
    class Unknown(cause: Throwable) : BackendFailure("حدث خطأ غير متوقع، يرجى المحاولة مرة أخرى", cause)
}

suspend fun <T> safeApiCall(moshi: Moshi, block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e // a cancelled coroutine must stay cancelled — never turn it into a "failure" the UI would show
} catch (e: BackendFailure) {
    // A block that already decided on a specific, user-facing failure (e.g. "the server returned no link token") must
    // reach the screen as that failure, not be re-wrapped as "unexpected error".
    Result.failure(e)
} catch (e: InterruptedIOException) {
    // A timeout (SocketTimeoutException is an InterruptedIOException): the connection exists but nothing came back in time.
    // On free hosting this is usually "the server is waking up", which deserves its own message, not "no internet".
    Result.failure(BackendFailure.Structured("BACKEND_TIMEOUT", "انتهت مهلة الانتظار. قد يكون الخادم يستيقظ من وضع السكون (يستغرق نحو دقيقة) — أعد المحاولة بعد قليل."))
} catch (e: IOException) {
    // No connection, DNS failure, ... — the one case the spec explicitly wants a fixed,
    // literal Arabic string for everywhere it appears ("يتطلب اتصالًا بالإنترنت").
    Result.failure(BackendFailure.NetworkUnavailable(e))
} catch (e: HttpException) {
    val parsed = runCatching {
        e.response()?.errorBody()?.string()?.let { body ->
            moshi.adapter(ErrorResponseDto::class.java).fromJson(body)
        }
    }.getOrNull()
    Result.failure(backendFailureFor(e.code(), parsed))
} catch (e: Exception) {
    Result.failure(BackendFailure.Unknown(e))
}
