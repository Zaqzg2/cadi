package com.inventorysmartai.backend.ai

import java.io.IOException

enum class FailureKind { AUTH, RATE_LIMITED, UNAVAILABLE, TIMEOUT, NETWORK, REJECTED, EMPTY, INVALID_OUTPUT }

/** What went wrong with one provider. [detail] is already trimmed and never contains an API key. */
data class ProviderFailure(
    val providerId: String,
    val kind: FailureKind,
    val detail: String,
    val retryAfterSeconds: Long? = null
)

/** A non-2xx answer from a provider. [body] is already trimmed to a short summary. */
class ProviderHttpException(
    val providerId: String,
    val code: Int,
    val body: String,
    val retryAfterSeconds: Long? = null
) : Exception("$providerId HTTP $code: $body")

/** The provider answered 2xx but with nothing usable. */
class ProviderEmptyException(val providerId: String) : Exception("$providerId returned no usable content")

/** The provider answered, but not with the JSON document that was asked for. */
class ProviderInvalidOutputException(message: String) : Exception(message)

/** Every provider failed or is cooling down. */
class AiUnavailableException(val failures: List<ProviderFailure>) :
    Exception("No AI provider could answer: " + failures.joinToString { "${it.providerId}=${it.kind}" }) {

    val retryAfterSeconds: Long? get() = failures.mapNotNull { it.retryAfterSeconds }.minOrNull()
    val allRateLimited: Boolean get() = failures.isNotEmpty() && failures.all { it.kind == FailureKind.RATE_LIMITED }
    val allAuthFailures: Boolean get() = failures.isNotEmpty() && failures.all { it.kind == FailureKind.AUTH }
}

/**
 * The server could not read a PDF itself (no OCR provider, or OCR failed). The Android app reacts to this by turning
 * the pages into images and asking again — the server never rasterises PDFs.
 */
class PdfNeedsImagesException : Exception("The PDF could not be read on the server; send its pages as images")

/** A request the server refuses before spending any AI quota. */
class RequestRejectedException(val status: Int, val code: String, override val message: String) : Exception(message)

internal fun Throwable.toFailure(providerId: String): ProviderFailure = when (this) {
    is ProviderHttpException -> ProviderFailure(
        providerId = providerId,
        kind = when {
            code == 401 || code == 403 -> FailureKind.AUTH
            code == 429 -> FailureKind.RATE_LIMITED
            code == 408 || code >= 500 -> FailureKind.UNAVAILABLE
            else -> FailureKind.REJECTED
        },
        detail = "HTTP $code: ${body.take(200)}",
        retryAfterSeconds = retryAfterSeconds
    )
    is ProviderEmptyException -> ProviderFailure(providerId, FailureKind.EMPTY, message.orEmpty())
    is ProviderInvalidOutputException -> ProviderFailure(providerId, FailureKind.INVALID_OUTPUT, message.orEmpty())
    is IOException -> ProviderFailure(providerId, FailureKind.NETWORK, javaClass.simpleName + ": " + message.orEmpty().take(120))
    else -> ProviderFailure(providerId, FailureKind.UNAVAILABLE, javaClass.simpleName + ": " + message.orEmpty().take(120))
}
