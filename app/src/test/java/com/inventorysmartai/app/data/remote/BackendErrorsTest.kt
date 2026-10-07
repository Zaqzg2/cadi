package com.inventorysmartai.app.data.remote

import com.inventorysmartai.app.data.remote.dto.ErrorDetailDto
import com.inventorysmartai.app.data.remote.dto.ErrorResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendErrorsTest {

    @Test
    fun `the server's own error is used verbatim`() {
        val failure = backendFailureFor(429, ErrorResponseDto(ErrorDetailDto("AI_QUOTA_EXCEEDED", "حاول بعد قليل")))
        assertTrue(failure is BackendFailure.Structured)
        failure as BackendFailure.Structured
        assertEquals("AI_QUOTA_EXCEEDED", failure.code)
        assertEquals("حاول بعد قليل", failure.messageAr)
    }

    @Test
    fun `a gateway error from the hosting platform explains the sleeping server`() {
        listOf(502, 503, 504).forEach { http ->
            val failure = backendFailureFor(http, null) as BackendFailure.Structured
            assertEquals("BACKEND_HTTP_$http", failure.code)
            assertTrue(failure.messageAr.contains("السكون"))
        }
    }

    @Test
    fun `a refused key points at the app key`() {
        val failure = backendFailureFor(401, null) as BackendFailure.Structured
        assertTrue(failure.messageAr.contains("BACKEND_APP_KEY"))
    }

    @Test
    fun `a missing route points at the base url`() {
        val failure = backendFailureFor(404, null) as BackendFailure.Structured
        assertTrue(failure.messageAr.contains("BACKEND_BASE_URL"))
    }

    @Test
    fun `anything else still names the status code`() {
        val failure = backendFailureFor(418, null) as BackendFailure.Structured
        assertEquals("BACKEND_HTTP_418", failure.code)
        assertTrue(failure.messageAr.contains("418"))
    }
}
