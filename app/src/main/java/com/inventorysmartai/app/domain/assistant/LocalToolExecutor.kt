package com.inventorysmartai.app.domain.assistant

/**
 * Executes one [ToolExecutionSite.LOCAL] tool call — every plain data question (getProducts,
 * getLowStock, ...) plus the one local write, createPurchaseRequest — against Room-backed
 * repositories. This exists because Room is the local source of truth and lives only on-device
 * (Phase 4 spec's "IMPORTANT ARCHITECTURE"): the backend cannot answer these itself, so it hands
 * the tool call back to the app (see data/assistant/AssistantRepositoryImpl), which runs it here
 * and reports the JSON result back through the same conversation.
 */
interface LocalToolExecutor {
    /**
     * [argumentsJson] is the tool's raw JSON arguments object exactly as Gemini produced it (see
     * backend gemini/ToolCatalog.kt for each tool's parameter schema). Returns a JSON-encoded
     * result string — never throws for "no rows found" (an empty JSON array/object is a normal,
     * valid answer); only for a genuinely unknown tool name, which the caller turns into an
     * explicit error result rather than crashing the conversation.
     */
    suspend fun execute(name: String, argumentsJson: String): String

    /** True for [com.inventorysmartai.app.domain.assistant.ToolExecutionSite.LOCAL] tools whose
     *  [com.inventorysmartai.app.domain.assistant] write requires the user's explicit
     *  confirmation before [execute] is even called — currently just createPurchaseRequest. */
    fun requiresConfirmation(name: String): Boolean

    /** Human-readable Arabic summary of what a write tool is about to do, built from its
     *  arguments — shown verbatim in the confirmation dialog. Only meaningful when
     *  [requiresConfirmation] is true for [name]. */
    suspend fun describeForConfirmation(name: String, argumentsJson: String): String
}
