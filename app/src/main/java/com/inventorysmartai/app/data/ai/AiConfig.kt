package com.inventorysmartai.app.data.ai

/**
 * Constants for document extraction through Firebase AI Logic (Gemini Developer API backend).
 */
object AiConfig {
    /**
     * The model name is only a string sent to Gemini through Firebase AI Logic. `gemini-3.8-flash` is
     * what Google's Firebase AI Logic getting-started guide uses (page updated 24 Sept 2026). Older Flash
     * names get retired, and Firebase recommends making this remotely changeable (Remote Config) once
     * the feature is live, so a retired name never needs an app release.
     */
    const val MODEL_NAME = "gemini-3.8-flash"

    /**
     * Largest file sent inline. The whole request must stay under Gemini's 20 MB inline limit and
     * base64 makes the bytes ~4/3 larger, so 10 MB of raw file is the safe ceiling. Typical invoice
     * photos and PDFs are far smaller.
     */
    const val MAX_INLINE_BYTES = 10 * 1024 * 1024
}
