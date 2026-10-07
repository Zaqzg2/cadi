package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.BuildConfig
import com.inventorysmartai.app.data.ai.AiFileTooLargeException
import com.inventorysmartai.app.data.ai.AiInvalidOutputException
import com.inventorysmartai.app.data.ai.AiTimeoutException
import com.inventorysmartai.app.data.remote.BackendFailure
import java.io.IOException
import java.io.InterruptedIOException

/**
 * The one place a failure of the direct-provider path becomes a user-facing Arabic [BackendFailure] —
 * the same type the backend path returns, so no screen above changes.
 */
internal fun Throwable.toProviderFailure(): BackendFailure {
    if (this is BackendFailure) return this
    return when (this) {
        is AiNoProviderConfiguredException -> BackendFailure.Structured(
            "AI_NOT_CONFIGURED",
            "لم تُضَف مفاتيح الذكاء الاصطناعي بعد. افتح الإعدادات ← إعدادات الذكاء الاصطناعي وأضف مفتاح Mistral أو Groq أو OpenRouter."
        )
        is AiFileTooLargeException -> BackendFailure.Structured(
            "AI_FILE_TOO_LARGE",
            "حجم الملف كبير جدًا للتحليل بالذكاء الاصطناعي (الحد الأقصى ${limitBytes / (1024 * 1024)} ميغابايت). جرّب صورة أصغر أو قسّم الملف."
        )
        is AiProviderHttpException -> httpFailure()
        is AiProviderEmptyException, is AiInvalidOutputException -> BackendFailure.Structured(
            "AI_INVALID_OUTPUT",
            "لم يُرجع الذكاء الاصطناعي نتيجة صالحة. جرّب مستندًا أوضح أو أعد المحاولة."
        )
        is AiTimeoutException -> timeout()
        else -> {
            val chain = generateSequence<Throwable>(this) { it.cause }.toList()
            when {
                chain.any { it is InterruptedIOException } -> timeout()
                chain.any { it is IOException } -> BackendFailure.NetworkUnavailable(this)
                else -> BackendFailure.Structured(
                    "AI_ERROR",
                    if (BuildConfig.DEBUG) {
                        "تعذّر تنفيذ الطلب بالذكاء الاصطناعي (${javaClass.simpleName}: ${message.orEmpty().take(200)})"
                    } else {
                        "تعذّر تنفيذ الطلب بالذكاء الاصطناعي، حاول مرة أخرى بعد قليل."
                    }
                )
            }
        }
    }
}

private fun timeout() = BackendFailure.Structured(
    "AI_TIMEOUT",
    "انتهت مهلة الاتصال بخدمة الذكاء الاصطناعي، تحقق من الإنترنت (وأوقف الـ VPN إن وُجد) ثم حاول مرة أخرى."
)

private fun AiProviderHttpException.httpFailure(): BackendFailure {
    val name = provider.labelAr
    return when {
        isAuthFailure -> BackendFailure.Structured(
            "AI_INVALID_KEY",
            "مفتاح $name غير صالح أو لا يملك الصلاحية. راجع المفتاح في إعدادات الذكاء الاصطناعي."
        )
        code == 429 -> BackendFailure.Structured(
            "AI_QUOTA_EXCEEDED",
            "تم تجاوز حدّ الاستخدام المجاني لدى $name مؤقتًا. حاول بعد قليل أو فعّل مزوّدًا آخر."
        )
        code == 413 -> BackendFailure.Structured(
            "AI_FILE_TOO_LARGE",
            "المستند أكبر من الحجم الذي يقبله $name. جرّب صورة أصغر."
        )
        code >= 500 -> BackendFailure.Structured("AI_PROVIDER_DOWN", "خدمة $name غير متاحة حاليًا، حاول لاحقًا.")
        else -> BackendFailure.Structured(
            "AI_PROVIDER_REJECTED",
            if (BuildConfig.DEBUG) {
                "رفض $name الطلب ($code): ${body.take(160)}"
            } else {
                "رفض $name الطلب. قد يكون اسم النموذج غير صحيح أو لا يدعم الصور؛ راجع النماذج في إعدادات الذكاء الاصطناعي."
            }
        )
    }
}
