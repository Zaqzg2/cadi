package com.inventorysmartai.app.domain.access

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * تجزئة الرموز (PIN) بـ PBKDF2-HMAC-SHA256 مع ملح عشوائي لكل مستخدم، والمقارنة بزمن ثابت.
 * الرمز من 4 إلى 8 أرقام فهو قابل للتخمين دون اتصال لمن يملك ملف الإعدادات (جهاز مروَّت)؛ التجزئة تبطئ ذلك ولا تمنعه.
 * الحماية هنا فصل صلاحيات بين المستخدمين على الجهاز، لا حماية من خصم يملك الجهاز ويستطيع الوصول إلى ملفات التطبيق.
 */
object PinHasher {
    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun newSalt(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun hash(secret: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(secret: String, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(secret, salt, iterations), expected)

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}

/** تباطؤ التخمين: بعد [maxAttempts] محاولات خاطئة متتالية يُقفل الدخول [lockMillis]. في الذاكرة فقط (يُصفَّر بإعادة تشغيل التطبيق). */
class PinThrottle(private val maxAttempts: Int = 5, private val lockMillis: Long = 30_000L) {
    private var failures = 0
    private var lockedUntil = 0L

    fun remainingLockMillis(now: Long): Long = (lockedUntil - now).coerceAtLeast(0L)
    fun attemptsLeft(): Int = maxAttempts - failures

    fun recordFailure(now: Long) {
        failures++
        if (failures >= maxAttempts) {
            lockedUntil = now + lockMillis
            failures = 0
        }
    }

    fun recordSuccess() {
        failures = 0
        lockedUntil = 0L
    }
}

/**
 * رمز استرداد يُعرض للمدير مرة واحدة عند تفعيل الحماية، ليعيد به ضبط رمزه إن نسيه (وإلا فلا مخرج سوى مسح بيانات التطبيق كلها).
 * 12 حرفًا من أبجدية بلا حروف ملتبسة (0/O و1/I)، أي نحو 60 بتًا من العشوائية، ويُخزَّن مجزَّأً مثل الرمز.
 */
object RecoveryCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 12

    fun generate(random: SecureRandom = SecureRandom()): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** XXXX-XXXX-XXXX للعرض. */
    fun display(code: String): String = code.chunked(4).joinToString("-")

    /** ما يكتبه المستخدم: أحرف كبيرة بلا شرطات ولا مسافات، فيُقبل بأي تنسيق. */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }
}
