package com.inventorysmartai.app.data.ai.provider

/**
 * The three OpenAI-compatible providers the app can call directly (no backend, no Firebase).
 *
 * Model ids move fast on every provider (Groq in particular rotates preview models), so these are only
 * DEFAULTS: each one can be overridden from Settings → إعدادات الذكاء الاصطناعي without an app release.
 * All three share one wire format (`POST {baseUrl}chat/completions`), so one client serves them all.
 */
enum class AiProviderId(
    val labelAr: String,
    val baseUrl: String,
    val defaultTextModel: String,
    val defaultVisionModel: String,
    val keyUrl: String,
    val noteAr: String
) {
    MISTRAL(
        labelAr = "Mistral",
        baseUrl = "https://api.mistral.ai/v1/",
        defaultTextModel = "mistral-small-latest",
        defaultVisionModel = "mistral-small-latest",
        keyUrl = "https://console.mistral.ai/api-keys",
        noteAr = "الأفضل لقراءة المستندات العربية (Mistral OCR) ثم الاستخراج المنظّم."
    ),
    GROQ(
        labelAr = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/",
        defaultTextModel = "openai/gpt-oss-120b",
        defaultVisionModel = "qwen/qwen3.8-27b",
        keyUrl = "https://console.groq.com/keys",
        noteAr = "سريع جدًا، مناسب للمساعد الذكي. الرؤية تقبل حتى 3 صور في الطلب."
    ),
    OPENROUTER(
        labelAr = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/",
        defaultTextModel = "openrouter/free",
        defaultVisionModel = "openrouter/free",
        keyUrl = "https://openrouter.ai/keys",
        noteAr = "موجّه مجاني يختار نموذجًا مجانيًا يدعم ما يحتاجه الطلب. الحدّ اليومي صغير."
    );

    val ocrModel: String? get() = if (this == MISTRAL) "mistral-ocr-latest" else null

    companion object {
        val DEFAULT_ORDER: List<AiProviderId> = listOf(MISTRAL, GROQ, OPENROUTER)

        fun fromNameOrNull(name: String): AiProviderId? = entries.firstOrNull { it.name == name }
    }
}
