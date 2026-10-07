package com.inventorysmartai.app.data.backend

import com.inventorysmartai.app.data.remote.BackendFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BackendRoutingTest {

    @Test
    fun `an unreachable server is an outage`() {
        assertTrue(BackendFailure.NetworkUnavailable(IOException("no route")).isBackendOutage())
    }

    @Test
    fun `quota, key and availability problems on the server are outages`() {
        listOf(
            "BACKEND_NOT_CONFIGURED", "BACKEND_TIMEOUT", "UNAUTHORIZED", "RATE_LIMITED", "DAILY_CAP_REACHED",
            "AI_UNAVAILABLE", "AI_QUOTA_EXCEEDED", "AI_INVALID_KEY", "BACKEND_HTTP_502", "BACKEND_HTTP_503"
        ).forEach { code ->
            assertTrue("$code should allow falling back to the person's own keys", BackendFailure.Structured(code, "x").isBackendOutage())
        }
    }

    @Test
    fun `problems with the request itself are not outages`() {
        listOf("AI_INVALID_OUTPUT", "PDF_NEEDS_IMAGES", "GOOGLE_NOT_LINKED", "BAD_REQUEST", "PAYLOAD_TOO_LARGE", "BACKEND_HTTP_404").forEach { code ->
            assertFalse("$code must be shown to the person, not hidden by a fallback", BackendFailure.Structured(code, "x").isBackendOutage())
        }
    }

    @Test
    fun `unknown failures are not outages`() {
        assertFalse(BackendFailure.Unknown(IllegalStateException()).isBackendOutage())
        assertFalse(IllegalArgumentException("x").isBackendOutage())
    }

    @Test
    fun `the app key is attached only to the backend host`() {
        assertTrue(BackendHeadersInterceptor.appliesTo("api.example.com", "api.example.com"))
        assertTrue(BackendHeadersInterceptor.appliesTo("API.example.com", "api.EXAMPLE.com"))
        assertFalse(BackendHeadersInterceptor.appliesTo("api.example.com", "api.mistral.ai"))
        assertFalse(BackendHeadersInterceptor.appliesTo("api.example.com", "api.groq.com"))
        assertFalse(BackendHeadersInterceptor.appliesTo("api.example.com", "evil-api.example.com"))
        assertFalse("no configured host means no headers anywhere", BackendHeadersInterceptor.appliesTo(null, "api.example.com"))
    }
}
