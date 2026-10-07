package com.inventorysmartai.app.data.ai

/** Limits shared by every AI path (the backend, and the optional direct-provider mode). */
object AiConfig {
    /**
     * Largest file sent in one request. The backend caps uploads at 10 MB by default (MAX_UPLOAD_BYTES) and base64 makes
     * the bytes ~4/3 larger on the wire for providers that need it, so 10 MB of raw file is the ceiling. Typical invoice
     * photos and PDFs are far smaller.
     */
    const val MAX_INLINE_BYTES = 10 * 1024 * 1024

    /**
     * Longest ONE direct-provider request may take. A call that is stuck (blocked network, a VPN) would otherwise leave
     * the spinner up forever.
     */
    const val REQUEST_TIMEOUT_MS = 90_000L

    /**
     * Longest the app waits for the whole of one backend AI request. It is deliberately long: a free-tier server that has
     * gone to sleep needs up to about a minute to wake before it even starts working, and then the server itself may try
     * several providers in turn (it stops by itself after ~100 s).
     */
    const val BACKEND_REQUEST_TIMEOUT_MS = 200_000L
}
