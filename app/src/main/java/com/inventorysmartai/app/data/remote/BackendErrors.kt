package com.inventorysmartai.app.data.remote

import com.inventorysmartai.app.data.remote.dto.ErrorResponseDto

/**
 * An HTTP error from the backend as a [BackendFailure]. The server answers every error with
 * `{"error": {"code", "message"}}` (message already in Arabic) — that is used when present. When it is absent the answer came
 * from somewhere else (typically a hosting platform's own page while the free-tier server is still waking up), so the message
 * explains the likely cause instead of a generic "unexpected error".
 */
internal fun backendFailureFor(httpCode: Int, parsed: ErrorResponseDto?): BackendFailure {
    if (parsed != null) return BackendFailure.Structured(parsed.error.code, parsed.error.message)
    val explanation = when (httpCode) {
        502, 503, 504 -> "الخادم غير جاهز بعد (HTTP $httpCode). إن كانت الاستضافة مجانية فهو يستيقظ من السكون — انتظر نحو دقيقة ثم أعد المحاولة."
        401, 403 -> "رفض الخادم الطلب (HTTP $httpCode). تحقق من تطابق BACKEND_APP_KEY في التطبيق مع APP_API_KEY على الخادم."
        404 -> "عنوان الخادم لا يستجيب لهذا المسار (HTTP 404). تحقق من BACKEND_BASE_URL."
        else -> "ردّ الخادم برمز غير متوقع (HTTP $httpCode)."
    }
    return BackendFailure.Structured("BACKEND_HTTP_$httpCode", explanation)
}
