package com.inventorysmartai.app.domain.access

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class AccessCryptoTest {

    // Few iterations: the algorithm is the same, only the work factor differs (production uses 120k).
    private val fast = 1_000

    @Test
    fun `the same pin and salt always give the same hash`() {
        val salt = PinHasher.newSalt()
        assertArrayEquals(PinHasher.hash("1234", salt, fast), PinHasher.hash("1234", salt, fast))
    }

    @Test
    fun `a different salt gives a different hash for the same pin`() {
        val a = PinHasher.hash("1234", PinHasher.newSalt(), fast)
        val b = PinHasher.hash("1234", PinHasher.newSalt(), fast)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `verify accepts the right pin and rejects a wrong one`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4321", salt, fast)
        assertTrue(PinHasher.verify("4321", salt, hash, fast))
        assertFalse(PinHasher.verify("4322", salt, hash, fast))
    }

    @Test
    fun `salts are random and the hash is 256 bits`() {
        assertFalse(PinHasher.newSalt().contentEquals(PinHasher.newSalt()))
        assertEquals(32, PinHasher.hash("0000", PinHasher.newSalt(), fast).size)
    }

    @Test
    fun `base64 helpers round trip`() {
        val bytes = byteArrayOf(0, 1, 2, 120, 127, -1, -128)
        assertArrayEquals(bytes, PinHasher.decode(PinHasher.encode(bytes)))
    }

    // ------------------------------------------------------------------ throttle

    @Test
    fun `five wrong attempts lock sign in for thirty seconds`() {
        val throttle = PinThrottle(maxAttempts = 5, lockMillis = 30_000)
        repeat(4) { throttle.recordFailure(now = 1_000) }
        assertEquals(0L, throttle.remainingLockMillis(now = 1_000))
        assertEquals(1, throttle.attemptsLeft())

        throttle.recordFailure(now = 1_000)
        assertEquals(30_000L, throttle.remainingLockMillis(now = 1_000))
        assertEquals(10_000L, throttle.remainingLockMillis(now = 21_000))
        assertEquals(0L, throttle.remainingLockMillis(now = 31_000))
    }

    @Test
    fun `after the lock the attempts start over`() {
        val throttle = PinThrottle(maxAttempts = 3, lockMillis = 1_000)
        repeat(3) { throttle.recordFailure(now = 0) }
        assertEquals(3, throttle.attemptsLeft())
    }

    @Test
    fun `a success clears the failures and any lock`() {
        val throttle = PinThrottle(maxAttempts = 3, lockMillis = 5_000)
        repeat(2) { throttle.recordFailure(now = 0) }
        throttle.recordSuccess()
        assertEquals(3, throttle.attemptsLeft())
        assertEquals(0L, throttle.remainingLockMillis(now = 0))
    }

    // ------------------------------------------------------------------ recovery code

    @Test
    fun `a recovery code is twelve unambiguous characters`() {
        val code = RecoveryCode.generate(SecureRandom())
        assertEquals(12, code.length)
        assertTrue(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
    }

    @Test
    fun `two codes differ`() {
        assertNotEquals(RecoveryCode.generate(), RecoveryCode.generate())
    }

    @Test
    fun `display groups by four and normalize accepts any format`() {
        assertEquals("ABCD-EFGH-JKLM", RecoveryCode.display("ABCDEFGHJKLM"))
        assertEquals("ABCDEFGHJKLM", RecoveryCode.normalize("abcd-efgh jklm"))
        assertEquals("ABCDEFGHJKLM", RecoveryCode.normalize(" ABCD-EFGH-JKLM "))
    }

    // ------------------------------------------------------------------ profile codec

    private fun profile(id: String, name: String, role: AppRole) = UserProfile(id, name, role, PinHasher.encode(byteArrayOf(1, 2)), PinHasher.encode(byteArrayOf(3, 4)))

    @Test
    fun `profiles survive encode then decode - including Arabic names and punctuation`() {
        val profiles = listOf(
            profile("a", "أحمد: المدير", AppRole.OWNER),
            profile("b", "Sara, \"Sales\"\nline", AppRole.SALES)
        )
        assertEquals(profiles, ProfileCodec.decode(ProfileCodec.encode(profiles)))
    }

    @Test
    fun `nothing stored decodes to an empty list`() {
        assertEquals(emptyList<UserProfile>(), ProfileCodec.decode(null))
        assertEquals(emptyList<UserProfile>(), ProfileCodec.decode(""))
    }

    @Test
    fun `a damaged line is skipped without losing the others`() {
        val good = ProfileCodec.encode(listOf(profile("a", "Ahmed", AppRole.OWNER)))
        val decoded = ProfileCodec.decode("garbage-line\n$good\n::::")
        assertEquals(listOf("a"), decoded.map { it.id })
    }

    @Test
    fun `an unknown role drops that line`() {
        val line = listOf("id", "Name", "SUPERUSER", "s", "h").joinToString(":") {
            java.util.Base64.getEncoder().encodeToString(it.toByteArray())
        }
        assertTrue(ProfileCodec.decode(line).isEmpty())
        assertNull(ProfileCodec.decode(line).firstOrNull())
    }
}
