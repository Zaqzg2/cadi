package com.inventorysmartai.app.data.remote

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException

class SafeApiCallTest {
    private val moshi = Moshi.Builder().build()

    private fun httpError(code: Int, body: String, contentType: String = "application/json"): HttpException =
        HttpException(Response.error<Any>(code, body.toResponseBody(contentType.toMediaType())))

    private fun <T> attempt(block: suspend () -> T): Result<T> = runBlocking { safeApiCall(moshi, block) }

    @Test
    fun `success passes the value through`() {
        assertEquals(42, attempt { 42 }.getOrNull())
    }

    @Test
    fun `a specific failure thrown inside the block is not re-wrapped`() {
        val failure = attempt<Unit> { throw BackendFailure.Structured("GOOGLE_LINK_TOKEN_MISSING", "رسالة محددة") }.exceptionOrNull()
        assertTrue(failure is BackendFailure.Structured)
        failure as BackendFailure.Structured
        assertEquals("GOOGLE_LINK_TOKEN_MISSING", failure.code)
        assertEquals("رسالة محددة", failure.messageAr)
    }

    @Test
    fun `no connection is the fixed Arabic internet message`() {
        val failure = attempt<Unit> { throw IOException("unreachable") }.exceptionOrNull()
        assertTrue(failure is BackendFailure.NetworkUnavailable)
        assertEquals("يتطلب اتصالًا بالإنترنت", (failure as BackendFailure).messageAr)
    }

    @Test
    fun `a timeout is reported as a sleeping server, not as no internet`() {
        val failure = attempt<Unit> { throw SocketTimeoutException("read timed out") }.exceptionOrNull()
        assertTrue(failure is BackendFailure.Structured)
        assertEquals("BACKEND_TIMEOUT", (failure as BackendFailure.Structured).code)
    }

    @Test
    fun `the server's error body becomes the failure`() {
        val failure = attempt<Unit> {
            throw httpError(422, """{"error":{"code":"PDF_NEEDS_IMAGES","message":"أرسل الصفحات كصور"}}""")
        }.exceptionOrNull() as BackendFailure.Structured
        assertEquals("PDF_NEEDS_IMAGES", failure.code)
        assertEquals("أرسل الصفحات كصور", failure.messageAr)
    }

    @Test
    fun `a platform error page becomes an explanation`() {
        val failure = attempt<Unit> { throw httpError(503, "<html>Service Unavailable</html>", "text/html") }.exceptionOrNull() as BackendFailure.Structured
        assertEquals("BACKEND_HTTP_503", failure.code)
    }

    @Test
    fun `anything else is unknown`() {
        val failure = attempt<Unit> { throw IllegalStateException("boom") }.exceptionOrNull()
        assertTrue(failure is BackendFailure.Unknown)
    }

    @Test
    fun `cancellation is never swallowed`() {
        try {
            attempt<Unit> { throw CancellationException("cancelled") }
            fail("expected the cancellation to propagate")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }
}
