package com.inventorysmartai.backend.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** What a "link token" protects: the Google refresh token and what the user granted. */
@Serializable
data class SealedGoogleLink(val refreshToken: String, val scopes: List<String>, val linkedAt: Long)

/**
 * Why this exists: free hosting platforms wipe the disk whenever a service sleeps or redeploys, so a server-side token
 * file would silently log every user out of Google. Instead the server stays stateless: after linking, the Google
 * refresh token is ENCRYPTED (AES-256-GCM, key derived from TOKEN_ENCRYPTION_KEY — a secret only the server knows) and
 * the sealed result is handed to the phone to keep. The phone cannot read it, change it, or use it elsewhere:
 *  - any modification fails authentication, so a tampered token is simply "not linked";
 *  - the session id is bound in as associated data, so the token only opens for the session it was issued to;
 *  - rotating TOKEN_ENCRYPTION_KEY invalidates every token at once (everybody re-links).
 */
class TokenSealer(secret: String) {
    private val key = SecretKeySpec(deriveKey(secret), "AES")
    private val random = SecureRandom()

    fun seal(sessionId: String, link: SealedGoogleLink): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(sessionId.toByteArray(Charsets.UTF_8))
        val plain = Json.encodeToString(SealedGoogleLink.serializer(), link).toByteArray(Charsets.UTF_8)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(iv + cipher.doFinal(plain))
    }

    /** The link, or null when the token is malformed, tampered with, made with another secret, or issued to another session. */
    fun unseal(sessionId: String, token: String): SealedGoogleLink? {
        if (!token.startsWith(PREFIX)) return null
        return try {
            val raw = Base64.getUrlDecoder().decode(token.removePrefix(PREFIX))
            if (raw.size <= IV_BYTES + TAG_BITS / 8) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            cipher.updateAAD(sessionId.toByteArray(Charsets.UTF_8))
            val plain = cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES)
            Json.decodeFromString(SealedGoogleLink.serializer(), String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val PREFIX = "v1."
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        /** HKDF-SHA256 (extract + one expand block = 32 bytes = an AES-256 key). */
        fun deriveKey(secret: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec("inventory-smart-ai/token-seal/v1".toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val prk = mac.doFinal(secret.toByteArray(Charsets.UTF_8))
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            return mac.doFinal("aes-256-gcm".toByteArray(Charsets.UTF_8) + byteArrayOf(1))
        }
    }
}
