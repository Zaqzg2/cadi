package com.inventorysmartai.app.data.remote

import com.inventorysmartai.app.data.remote.dto.ErrorResponseDto
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import java.io.IOException

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
} catch (e: IOException) {
    // No connection, DNS failure, timeout, ... — the one case the spec explicitly wants a fixed,
    // literal Arabic string for everywhere it appears ("يتطلب اتصالًا بالإنترنت").
    Result.failure(BackendFailure.NetworkUnavailable(e))
} catch (e: HttpException) {
    val parsed = runCatching {
        e.response()?.errorBody()?.string()?.let { body ->
            moshi.adapter(ErrorResponseDto::class.java).fromJson(body)
        }
    }.getOrNull()
    if (parsed != null) {
        Result.failure(BackendFailure.Structured(parsed.error.code, parsed.error.message))
    } else {
        Result.failure(BackendFailure.Unknown(e))
    }
} catch (e: Exception) {
    Result.failure(BackendFailure.Unknown(e))
}
