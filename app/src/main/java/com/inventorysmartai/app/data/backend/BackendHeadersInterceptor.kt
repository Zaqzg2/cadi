package com.inventorysmartai.app.data.backend

import com.inventorysmartai.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the app key and the Google link token to requests going to OUR backend — and ONLY those. The same OkHttp client
 * also carries requests to Mistral/Groq/OpenRouter when the user opts into direct mode, so the host check below is what
 * guarantees these two values can never leak to a third party.
 */
class BackendHeadersInterceptor(
    private val backendHost: String?,
    private val credentials: BackendCredentials
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!appliesTo(backendHost, request.url.host)) return chain.proceed(request)

        val builder = request.newBuilder()
        if (BuildConfig.BACKEND_APP_KEY.isNotBlank()) builder.header(APP_KEY_HEADER, BuildConfig.BACKEND_APP_KEY)
        credentials.googleLinkTokenBlocking()?.let { builder.header(GOOGLE_LINK_HEADER, it) }
        return chain.proceed(builder.build())
    }

    companion object {
        const val APP_KEY_HEADER = "X-App-Key"
        const val GOOGLE_LINK_HEADER = "X-Google-Link"

        /** True only for the backend's own host — the rule that keeps the app key away from every third party. */
        fun appliesTo(backendHost: String?, requestHost: String): Boolean =
            backendHost != null && requestHost.equals(backendHost, ignoreCase = true)
    }
}
