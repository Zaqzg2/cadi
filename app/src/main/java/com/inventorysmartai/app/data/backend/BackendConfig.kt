package com.inventorysmartai.app.data.backend

import com.inventorysmartai.app.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What this build knows about the backend, and the exact Arabic explanation when something is missing. */
object BackendConfig {
    val baseUrl: String get() = BuildConfig.BACKEND_BASE_URL

    /** The backend's host name — the app key and Google link token are attached ONLY to requests to this host. */
    val host: String? get() = baseUrl.toHttpUrlOrNull()?.host

    /** Base URL without a trailing slash, ready to append "/v1/...". */
    val baseUrlNoSlash: String get() = baseUrl.trimEnd('/')

    /**
     * What Retrofit is built with: always a valid URL ending in "/", even when the configured value is garbage — Retrofit
     * throws on a bad base URL, and that must not crash the app at startup ([configurationProblem] reports it, in Arabic,
     * the first time a backend feature is actually used).
     */
    val retrofitBaseUrl: String
        get() {
            val parsed = baseUrl.toHttpUrlOrNull() ?: return "http://localhost/"
            val text = parsed.toString()
            return if (text.endsWith("/")) text else "$text/"
        }

    /** The Android emulator's alias for the developer machine: a local backend there may run with APP_AUTH_DISABLED. */
    private val isLocalEmulatorBackend: Boolean get() = BuildConfig.DEBUG && baseUrl.contains("10.0.2.2")

    /** null = ready to call; otherwise the one thing to fix, in Arabic. */
    fun configurationProblem(): String? = when {
        baseUrl.contains("CHANGE-ME") ->
            "لم يُضبط عنوان الخادم في هذا الإصدار. أضف BACKEND_BASE_URL (رابط الخادم المنشور) في إعدادات البناء ثم أعد بناء التطبيق، " +
                "أو أضف مفاتيحك في «إعدادات الذكاء الاصطناعي» وفعّل «استخدام مفاتيحي مباشرة»."
        baseUrl.toHttpUrlOrNull() == null ->
            "عنوان الخادم غير صالح ($baseUrl). يجب أن يبدأ بـ https:// وينتهي بشرطة مائلة."
        BuildConfig.BACKEND_APP_KEY.isBlank() && !isLocalEmulatorBackend ->
            "لم يُضبط BACKEND_APP_KEY في هذا الإصدار. انسخ قيمة APP_API_KEY من إعدادات الخادم إلى إعدادات البناء ثم أعد بناء التطبيق."
        else -> null
    }

    /** Shown when the server cannot be reached at all. */
    fun unreachableMessage(): String =
        "تعذّر الوصول إلى الخادم ($baseUrl). إن كانت الاستضافة مجانية فقد يكون الخادم في وضع السكون — انتظر نحو دقيقة ثم أعد المحاولة. " +
            "وتأكد من أن العنوان صحيح (العنوان 10.0.2.2 يعمل على المحاكي فقط)."
}
