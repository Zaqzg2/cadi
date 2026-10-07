package com.inventorysmartai.backend.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenSealerTest {
    private val sealer = TokenSealer("a-long-random-secret-for-the-tests-0123456789")
    private val link = SealedGoogleLink("1//refresh-token-value", listOf("scope.a", "scope.b"), 1_700_000_000_000L)

    @Test
    fun `a sealed token opens for the same session`() {
        val token = sealer.seal("session-1", link)
        assertEquals(link, sealer.unseal("session-1", token))
    }

    @Test
    fun `the token never contains the refresh token in clear`() {
        val token = sealer.seal("session-1", link)
        assertTrue(token.startsWith("v1."))
        assertTrue(!token.contains("refresh-token-value"))
    }

    @Test
    fun `it does not open for another session`() {
        assertNull(sealer.unseal("session-2", sealer.seal("session-1", link)))
    }

    @Test
    fun `it does not open with another secret`() {
        val other = TokenSealer("a-completely-different-secret-0123456789abcdef")
        assertNull(other.unseal("session-1", sealer.seal("session-1", link)))
    }

    @Test
    fun `a tampered token is rejected`() {
        val token = sealer.seal("session-1", link)
        val index = token.length / 2
        val flipped = if (token[index] == 'A') 'B' else 'A'
        val tampered = token.substring(0, index) + flipped + token.substring(index + 1)
        assertNull(sealer.unseal("session-1", tampered))
    }

    @Test
    fun `garbage is not a token`() {
        listOf("", "v1.", "v1.AAAA", "not-a-token", "v1.!!!!").forEach {
            assertNull("input: $it", sealer.unseal("session-1", it))
        }
    }

    @Test
    fun `two seals of the same data differ because the IV is random`() {
        assertNotEquals(sealer.seal("s", link), sealer.seal("s", link))
        assertNotNull(sealer.unseal("s", sealer.seal("s", link)))
    }
}
