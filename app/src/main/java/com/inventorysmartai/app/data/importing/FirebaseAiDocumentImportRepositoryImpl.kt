package com.inventorysmartai.app.data.importing

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.inventorysmartai.app.AppCheckInstaller
import com.inventorysmartai.app.BuildConfig
import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.AppCheckDebugInfo
import com.inventorysmartai.app.data.ai.AppCheckDiagnostics
import com.inventorysmartai.app.data.ai.AppCheckStatus
import com.inventorysmartai.app.data.ai.AiDocumentInput
import com.inventorysmartai.app.data.ai.AiFileTooLargeException
import com.inventorysmartai.app.data.ai.AiInvalidOutputException
import com.inventorysmartai.app.data.ai.AiNotConfiguredException
import com.inventorysmartai.app.data.ai.AiTimeoutException
import com.inventorysmartai.app.data.ai.ExtractionSchemas
import com.inventorysmartai.app.data.ai.toFirebaseSchema
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.inventorysmartai.app.domain.repository.AiDocumentImportRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val AI_IMPORT_LOG_TAG = "AiDocImport"

/**
 * [AiDocumentImportRepository] over Firebase AI Logic: the phone calls Gemini itself through Google's
 * client SDK, and Firebase App Check (Play Integrity in release builds, the debug provider in debug
 * builds) is what proves to Google that the request comes from THIS app — so, unlike a raw API key in the
 * APK, there is no secret here to extract. See README, "Firebase AI Logic".
 *
 * It is bound in `di/RepositoryModule` in place of [AiDocumentImportRepositoryImpl] (the backend-routed
 * one, left untouched so the backend can be switched back on with a one-line change to that binding).
 * Nothing above this class changes: [AiDocumentAnalysisEngine] still receives a
 * `Result<AiExtractionDocument>`, and every failure still arrives as a [BackendFailure] carrying an
 * Arabic message — see [toBackendFailure].
 *
 * `sessionId` is part of the interface for the backend's audit log; there is no backend here, so it is
 * intentionally unused.
 */
@Singleton
class FirebaseAiDocumentImportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) : AiDocumentImportRepository {

    override suspend fun extract(
        fileBytes: ByteArray,
        mimeType: String,
        documentType: AiExtractionDocumentType,
        sessionId: String
    ): Result<AiExtractionDocument> {
        return try {
            Result.success(extractOrThrow(fileBytes, mimeType, documentType))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure(e)
            // Only for an App Check rejection, and only in debug builds (release returns null/empty): find
            // the debug secret so the error screen can show it, plus what App Check left on disk, so a
            // missing secret can be diagnosed from the screen alone.
            val debugInfo = if (AppCheckDiagnostics.isAppCheckRejection(e)) {
                withContext(Dispatchers.IO) {
                    AppCheckDebugInfo(
                        secret = AppCheckInstaller.recentDebugSecret(context),
                        appCheckPreferenceFiles = AppCheckInstaller.appCheckPreferenceFiles(context)
                    )
                }
            } else {
                null
            }
            Result.failure(e.toBackendFailure(debugInfo))
        }
    }

    private suspend fun extractOrThrow(
        fileBytes: ByteArray,
        declaredMimeType: String,
        documentType: AiExtractionDocumentType
    ): AiExtractionDocument {
        // No google-services.json at build time => Firebase never initialised => nothing to call.
        if (FirebaseApp.getApps(context).isEmpty()) throw AiNotConfiguredException()
        if (fileBytes.size > AiConfig.MAX_INLINE_BYTES) {
            throw AiFileTooLargeException(fileBytes.size, AiConfig.MAX_INLINE_BYTES)
        }

        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = AiConfig.MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ExtractionSchemas.forDocumentType(documentType).toFirebaseSchema()
            }
        )

        // File first, then the instruction: Google's guidance for a single image/PDF plus a prompt.
        val request = content {
            inlineData(fileBytes, AiDocumentInput.resolveMimeType(fileBytes, declaredMimeType))
            text(ExtractionSchemas.instructionFor(documentType))
        }

        // Bounded: a stuck request (blocked network, VPN, App Check retrying) must end in a message, not a
        // spinner. withTimeoutOrNull (not withTimeout) so the timeout is a value, never a CancellationException
        // that the caller's handler would rethrow silently.
        val response = withTimeoutOrNull(AiConfig.REQUEST_TIMEOUT_MS) { model.generateContent(request) }
            ?: throw AiTimeoutException()
        val text = response.text ?: throw AiInvalidOutputException("Gemini returned no text")
        return parseDocument(AiDocumentInput.stripCodeFence(text))
    }

    private fun parseDocument(json: String): AiExtractionDocument {
        val parsed = try {
            moshi.adapter(AiExtractionDocument::class.java).fromJson(json)
        } catch (e: Exception) {
            // Moshi throws JsonDataException (wrong shape) or an IOException subclass (not JSON) —
            // both mean "the model's answer was unusable", never "no network".
            throw AiInvalidOutputException("Gemini's JSON did not match the extraction schema: ${e.message}")
        }
        return parsed ?: throw AiInvalidOutputException("Gemini returned an empty JSON document")
    }

    private fun logFailure(error: Exception) {
        // android.util.Log is a stub that throws in plain-JVM unit tests, and a diagnostic must never
        // turn a handled failure into a crash. The request (the whole document) is never logged.
        runCatching {
            Log.w(AI_IMPORT_LOG_TAG, "AI extraction failed: ${error.javaClass.simpleName}: ${error.message}", error)
        }
    }
}

/**
 * The single place any failure of the AI path becomes a user-facing Arabic [BackendFailure].
 * [debugInfo] is what a debug build found out about the App Check debug secret (null for other failures).
 */
internal fun Throwable.toBackendFailure(debugInfo: AppCheckDebugInfo? = null): BackendFailure {
    if (this is BackendFailure) return this
    // First, by message: every App Check refusal from Firebase AI Logic says so in its text, whichever
    // exception class the SDK wraps it in.
    if (AppCheckDiagnostics.isAppCheckRejection(this)) return appCheckFailure(debugInfo)
    return when (this) {
        is AiNotConfiguredException -> BackendFailure.Structured(
            "AI_NOT_CONFIGURED",
            "لم يُربط التطبيق بمشروع Firebase بعد. أضف ملف google-services.json إلى مجلد app ثم أعد بناء التطبيق."
        )
        is AiFileTooLargeException -> BackendFailure.Structured(
            "AI_FILE_TOO_LARGE",
            "حجم الملف كبير جدًا للتحليل بالذكاء الاصطناعي (الحد الأقصى ${limitBytes / (1024 * 1024)} ميغابايت). جرّب صورة أصغر أو قسّم الملف."
        )
        is AiTimeoutException, is RequestTimeoutException -> timeoutFailure()
        is AiInvalidOutputException, is SerializationException -> BackendFailure.Structured(
            "AI_INVALID_OUTPUT",
            "لم يُرجع الذكاء الاصطناعي نتيجة صالحة. جرّب مستندًا أوضح أو أعد المحاولة."
        )
        is QuotaExceededException -> BackendFailure.Structured(
            "AI_QUOTA_EXCEEDED",
            "تم تجاوز حدّ استخدام الذكاء الاصطناعي مؤقتًا، حاول بعد قليل."
        )
        is ServiceDisabledException -> BackendFailure.Structured(
            "AI_SERVICE_DISABLED",
            "خدمة Firebase AI Logic غير مفعّلة لهذا المشروع. فعّلها من Firebase Console ← AI Logic ← Get started."
        )
        is InvalidAPIKeyException -> BackendFailure.Structured(
            "AI_INVALID_KEY",
            "مفتاح Firebase في google-services.json غير صالح أو لا يسمح بخدمة Firebase AI Logic. راجع قيود المفتاح في Google Cloud Console."
        )
        is UnsupportedUserLocationException -> BackendFailure.Structured(
            "AI_REGION_NOT_SUPPORTED",
            "خدمة Gemini غير متاحة في منطقتك الجغرافية حاليًا."
        )
        is PromptBlockedException, is ContentBlockedException, is ResponseStoppedException -> BackendFailure.Structured(
            "AI_CONTENT_BLOCKED",
            "رفض نموذج الذكاء الاصطناعي معالجة هذا المستند. جرّب صورة أو ملفًا آخر."
        )
        else -> {
            val chain = generateSequence<Throwable>(this) { it.cause }.toList()
            when {
                chain.any { it is InterruptedIOException } -> timeoutFailure()
                chain.any { it is IOException } -> BackendFailure.NetworkUnavailable(this)
                else -> BackendFailure.Structured(
                    "AI_ERROR",
                    if (BuildConfig.DEBUG) {
                        "تعذّر استخراج البيانات بالذكاء الاصطناعي (${this.javaClass.simpleName}: ${this.message.orEmpty().take(200)})"
                    } else {
                        "تعذّر استخراج البيانات بالذكاء الاصطناعي، حاول مرة أخرى بعد قليل."
                    }
                )
            }
        }
    }
}

private fun timeoutFailure(): BackendFailure = BackendFailure.Structured(
    "AI_TIMEOUT",
    "انتهت مهلة الاتصال بخدمة الذكاء الاصطناعي، تحقق من الإنترنت (وأوقف الـ VPN إن وُجد) ثم حاول مرة أخرى."
)

/**
 * App Check is enforced for Firebase AI Logic (and from 2 Nov 2026 enforcement cannot be turned off), so
 * on a fresh setup this is the expected first failure. In a debug build the message carries what is
 * needed to fix it, including the debug secret when it could be found; in release it points at Play
 * Integrity instead.
 */
private fun appCheckFailure(debugInfo: AppCheckDebugInfo?): BackendFailure {
    val message = if (BuildConfig.DEBUG) {
        buildString {
            append("رفضت Firebase الطلب لأن App Check لم يتحقق من هذا التطبيق.\n")
            append("1) في Firebase Console، من القائمة اليسرى: App Check ← تبويب APIs ← Firebase AI Logic ← Set up ← Enforced.\n")
            append("2) تبويب Apps ← التطبيق الذي ينتهي بـ .debug ← ⋮ ← Manage debug tokens ← Add debug token ← الصق الرمز أدناه واحفظ.\n")
            val secret = debugInfo?.secret
            if (secret != null) {
                append("\nالرمز:\n").append(secret).append("\n")
                append("(استخدم زر «نسخ الرمز» أدناه. لا تشارك لقطة هذه الشاشة مع أحد.)")
            } else {
                append("\nلم أجد رمز debug في ملفات التطبيق.")
                val installError = AppCheckStatus.installError
                if (installError != null) {
                    append("\nتعذّر تثبيت مزوّد App Check عند بدء التطبيق: ").append(installError)
                } else {
                    val files = debugInfo?.appCheckPreferenceFiles.orEmpty()
                    if (files.isEmpty()) {
                        append("\nلا يوجد أي ملف لـ App Check بعد، أي أن المزوّد لم يُنشئ رمزًا. أعد المحاولة مرة واحدة.")
                    } else {
                        append("\nملفات App Check الموجودة: ").append(files.joinToString())
                    }
                    append("\nإن تكرر هذا فأرسل لي لقطة هذه الشاشة (ليس فيها رمز).")
                }
            }
        }
    } else {
        "فشل التحقق من التطبيق (App Check). تأكد أن مزوّد Play Integrity مفعّل في Firebase وأن التطبيق مثبّت من Google Play."
    }
    return BackendFailure.Structured("AI_APP_CHECK_REJECTED", message)
}
