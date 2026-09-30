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
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.AiDocumentInput
import com.inventorysmartai.app.data.ai.AiFileTooLargeException
import com.inventorysmartai.app.data.ai.AiInvalidOutputException
import com.inventorysmartai.app.data.ai.AiNotConfiguredException
import com.inventorysmartai.app.data.ai.ExtractionSchemas
import com.inventorysmartai.app.data.ai.toFirebaseSchema
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.inventorysmartai.app.domain.repository.AiDocumentImportRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
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
            Result.failure(e.toBackendFailure())
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

        val text = model.generateContent(request).text
            ?: throw AiInvalidOutputException("Gemini returned no text")
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

/** The single place any failure of the AI path becomes a user-facing Arabic [BackendFailure]. */
internal fun Throwable.toBackendFailure(): BackendFailure {
    if (this is BackendFailure) return this
    return when (this) {
        is AiNotConfiguredException -> BackendFailure.Structured(
            "AI_NOT_CONFIGURED",
            "لم يُربط التطبيق بمشروع Firebase بعد. أضف ملف google-services.json إلى مجلد app ثم أعد بناء التطبيق."
        )
        is AiFileTooLargeException -> BackendFailure.Structured(
            "AI_FILE_TOO_LARGE",
            "حجم الملف كبير جدًا للتحليل بالذكاء الاصطناعي (الحد الأقصى ${limitBytes / (1024 * 1024)} ميغابايت). جرّب صورة أصغر أو قسّم الملف."
        )
        is AiInvalidOutputException, is SerializationException -> BackendFailure.Structured(
            "AI_INVALID_OUTPUT",
            "لم يُرجع الذكاء الاصطناعي نتيجة صالحة. جرّب مستندًا أوضح أو أعد المحاولة."
        )
        is QuotaExceededException -> BackendFailure.Structured(
            "AI_QUOTA_EXCEEDED",
            "تم تجاوز حدّ استخدام الذكاء الاصطناعي مؤقتًا، حاول بعد قليل."
        )
        is RequestTimeoutException -> BackendFailure.Structured(
            "AI_TIMEOUT",
            "انتهت مهلة الاتصال بخدمة الذكاء الاصطناعي، حاول مرة أخرى."
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
        is ServerException -> serverFailure()
        else -> {
            val chain = generateSequence<Throwable>(this) { it.cause }.toList()
            when {
                chain.any { it is InterruptedIOException } -> BackendFailure.Structured(
                    "AI_TIMEOUT",
                    "انتهت مهلة الاتصال بخدمة الذكاء الاصطناعي، حاول مرة أخرى."
                )
                chain.any { it is IOException } -> BackendFailure.NetworkUnavailable(this)
                else -> BackendFailure.Unknown(this)
            }
        }
    }
}

/**
 * A rejected request is, on a fresh setup, almost always App Check: enforcement is on by default (since
 * July 2026) and the debug build's token must be registered once. That case gets its own actionable
 * message; anything else from the server gets the generic one.
 */
private fun ServerException.serverFailure(): BackendFailure {
    val text = message.orEmpty()
    return if (text.contains("App Check", ignoreCase = true) || text.contains("appcheck", ignoreCase = true)) {
        BackendFailure.Structured(
            "AI_APP_CHECK_REJECTED",
            "رفضت Firebase الطلب بسبب App Check. في نسخة التطوير: شغّل التطبيق وانسخ «debug secret» من Logcat " +
                "(الوسم DebugAppCheckProvider) وسجّله في Firebase Console ← App Check ← Manage debug tokens."
        )
    } else {
        BackendFailure.Structured(
            "AI_SERVER_ERROR",
            "خدمة الذكاء الاصطناعي غير متاحة حاليًا أو رفضت الطلب، حاول بعد قليل."
        )
    }
}
