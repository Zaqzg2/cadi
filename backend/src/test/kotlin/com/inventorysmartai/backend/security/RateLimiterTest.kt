package com.inventorysmartai.backend.security

import com.inventorysmartai.backend.config.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `allows up to the limit then asks to wait`() {
        var now = 0L
        val limiter = RateLimiter(clock = { now })
        repeat(3) { assertEquals(0L, limiter.acquire("k", 3)) }
        val wait = limiter.acquire("k", 3)
        assertTrue("should ask to wait, got $wait", wait in 1L..60L)
    }

    @Test
    fun `the window slides`() {
        var now = 0L
        val limiter = RateLimiter(clock = { now })
        repeat(3) { limiter.acquire("k", 3) }
        now += 60_000L
        assertEquals(0L, limiter.acquire("k", 3))
    }

    @Test
    fun `keys are independent`() {
        val limiter = RateLimiter(clock = { 0L })
        repeat(2) { limiter.acquire("a", 2) }
        assertTrue(limiter.acquire("a", 2) > 0L)
        assertEquals(0L, limiter.acquire("b", 2))
    }

    @Test
    fun `daily counter stops at the cap and resets the next day`() {
        var now = 0L
        val counter = DailyCounter(2, clock = { now })
        assertTrue(counter.tryTake())
        assertTrue(counter.tryTake())
        assertFalse(counter.tryTake())
        now += 86_400_000L
        assertTrue(counter.tryTake())
    }

    @Test
    fun `a cap of zero means unlimited`() {
        val counter = DailyCounter(0, clock = { 0L })
        repeat(1000) { assertTrue(counter.tryTake()) }
    }
}

class RequestGuardTest {
    private val appKey = "a-sufficiently-long-app-key"

    private fun guard(extra: Map<String, String> = emptyMap()): RequestGuard =
        RequestGuard(ServerConfig.fromEnv(mapOf("APP_API_KEY" to appKey, "GROQ_API_KEY" to "g") + extra))

    @Test
    fun `the right key is accepted and anything else is not`() {
        val guard = guard()
        assertTrue(guard.keyAccepted(appKey))
        assertFalse(guard.keyAccepted("wrong"))
        assertFalse(guard.keyAccepted(appKey + "x"))
        assertFalse(guard.keyAccepted(null))
    }

    @Test
    fun `with auth disabled no key is needed`() {
        val open = RequestGuard(ServerConfig.fromEnv(mapOf("APP_AUTH_DISABLED" to "true", "GROQ_API_KEY" to "g")))
        assertTrue(open.keyAccepted(null))
    }

    @Test
    fun `the last forwarded address is the client`() {
        val guard = guard()
        assertEquals("2.2.2.2", guard.clientKeyFrom("1.1.1.1, 2.2.2.2", null))
        assertEquals("9.9.9.9", guard.clientKeyFrom(null, "9.9.9.9"))
        assertEquals("direct", guard.clientKeyFrom(null, null))
        assertEquals("direct", guard.clientKeyFrom("  ", null))
    }

    @Test
    fun `proxy headers can be distrusted`() {
        val guard = guard(mapOf("TRUST_PROXY_HEADERS" to "false"))
        assertEquals("direct", guard.clientKeyFrom("1.1.1.1", "2.2.2.2"))
    }
}
