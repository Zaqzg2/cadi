package com.inventorysmartai.app.data.ai.provider

/** An HTTP answer other than 2xx from a provider. [body] is already trimmed and never contains the key. */
class AiProviderHttpException(
    val provider: AiProviderId,
    val code: Int,
    val body: String
) : Exception("${provider.name} HTTP $code: $body") {

    /** Worth trying the next provider: rate limit, overload, or a provider-side failure. */
    val isRetryableElsewhere: Boolean
        get() = code == 408 || code == 413 || code == 429 || code >= 500

    val isAuthFailure: Boolean get() = code == 401 || code == 403
}

/** Provider answered 2xx but the payload had no usable content. */
class AiProviderEmptyException(val provider: AiProviderId) : Exception("${provider.name} returned no content")

/** No provider has a key (or all are switched off). */
class AiNoProviderConfiguredException : Exception("No direct AI provider is configured")
