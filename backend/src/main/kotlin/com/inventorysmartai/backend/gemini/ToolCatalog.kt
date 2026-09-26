package com.inventorysmartai.backend.gemini

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Where a tool's real implementation lives. This split exists because Room — the single source
 * of truth for every inventory/sales/purchase/goal fact — lives ONLY on the device (Phase 4
 * spec's own "IMPORTANT ARCHITECTURE" section: the backend does Gemini orchestration + Google
 * Workspace operations, the app owns local data). So:
 *  - [LOCAL] tools (every plain "get..." data question, plus [ToolCatalog.CREATE_PURCHASE_REQUEST])
 *    are executed by the Android app itself against Room, and only ever *relayed* through this
 *    backend as part of the Gemini tool-calling loop (see routes/AssistantRoutes.kt).
 *  - [BACKEND] tools touch Google Workspace on the user's behalf using the OAuth tokens this
 *    backend holds (see auth/TokenStore.kt) — the app never sees those tokens.
 */
enum class ToolExecutionSite { LOCAL, BACKEND }

/** Phase 4 spec, "ACTION CONFIRMATION": read-only tools run immediately; anything that writes or
 *  sends something requires the app to show a confirmation dialog (in Arabic) before executing. */
enum class ToolRisk { READ_ONLY, WRITE }

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val site: ToolExecutionSite,
    val risk: ToolRisk
) {
    fun toGeminiTool() = GeminiTool(name = name, description = description, parameters = parameters)
}

/**
 * Every tool name here is taken verbatim from the Phase 4 brief's "FUNCTION CALLING" list — this
 * backend does not invent additional tools, and does not rename any of these, so the app's
 * [ToolExecutionSite.LOCAL] executor (Android `domain/assistant/LocalToolExecutor`) can match on
 * the exact same names without a translation table on either side.
 */
object ToolCatalog {

    // ---- Helpers: small, boring JSON-Schema builders (kept private — only this file needs them) ----
    private fun obj(properties: JsonObject, required: List<String> = emptyList()): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", properties)
        if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
    }
    private fun props(vararg pairs: Pair<String, JsonObject>): JsonObject = buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }
    private fun str(desc: String): JsonObject = buildJsonObject { put("type", "string"); put("description", desc) }
    private fun strEnum(desc: String, values: List<String>): JsonObject = buildJsonObject {
        put("type", "string"); put("description", desc)
        putJsonArray("enum") { values.forEach { add(it) } }
    }
    private fun num(desc: String): JsonObject = buildJsonObject { put("type", "number"); put("description", desc) }
    private fun int(desc: String): JsonObject = buildJsonObject { put("type", "integer"); put("description", desc) }
    private fun bool(desc: String): JsonObject = buildJsonObject { put("type", "boolean"); put("description", desc) }
    private fun arr(itemType: String, desc: String): JsonObject = buildJsonObject {
        put("type", "array"); put("description", desc)
        put("items", buildJsonObject { put("type", itemType) })
    }
    private fun empty(): JsonObject = obj(props())

    private fun local(name: String, description: String, parameters: JsonObject, risk: ToolRisk = ToolRisk.READ_ONLY) =
        ToolDefinition(name, description, parameters, ToolExecutionSite.LOCAL, risk)
    private fun backend(name: String, description: String, parameters: JsonObject, risk: ToolRisk = ToolRisk.WRITE) =
        ToolDefinition(name, description, parameters, ToolExecutionSite.BACKEND, risk)

    val all: List<ToolDefinition> = listOf(
        local(
            "getProducts",
            "List products in the catalog, optionally filtered by category or branch. Use for broad questions about what products exist.",
            obj(props("categoryId" to int("Optional category id filter"), "limit" to int("Max rows to return, default 50")))
        ),
        local(
            "getProduct",
            "Get one product's full detail (identifiers, stock per branch, category/unit) by its id.",
            obj(props("productId" to int("The product's id")), required = listOf("productId"))
        ),
        local(
            "searchProducts",
            "Search products by name, item number, or barcode (partial match).",
            obj(props("query" to str("Search text — name, item number, or barcode"), "limit" to int("Max rows, default 20")), required = listOf("query"))
        ),
        local(
            "getInventory",
            "Get current stock levels across all branches, optionally filtered by product or status (available/low/zero/near_expiry/expired).",
            obj(
                props(
                    "productId" to int("Optional: limit to one product"),
                    "status" to strEnum("Optional status filter", listOf("AVAILABLE", "LOW", "ZERO", "NEAR_EXPIRY", "EXPIRED"))
                )
            )
        ),
        local(
            "getBranchInventory",
            "Get current stock levels for one specific branch.",
            obj(props("branchId" to int("The branch id")), required = listOf("branchId"))
        ),
        local(
            "getLowStock",
            "List products at or below their reorder point / minimum stock, across all branches or one branch.",
            obj(props("branchId" to int("Optional: limit to one branch")))
        ),
        local(
            "getZeroStock",
            "List products with zero stock, across all branches or one branch.",
            obj(props("branchId" to int("Optional: limit to one branch")))
        ),
        local(
            "getExpiredItems",
            "List batches/products already past their expiry date.",
            obj(props("branchId" to int("Optional: limit to one branch")))
        ),
        local(
            "getNearExpiryItems",
            "List batches/products expiring within the app's configured near-expiry window.",
            obj(props("branchId" to int("Optional: limit to one branch")))
        ),
        local(
            "getInventoryMovements",
            "Get the stock movement history (purchases, sales, adjustments, transfers) for one product.",
            obj(
                props(
                    "productId" to int("The product's id"),
                    "limit" to int("Max rows, default 30")
                ),
                required = listOf("productId")
            )
        ),
        local(
            "getCounting",
            "Get recent physical inventory counts, or one count's detail by id.",
            obj(props("countId" to int("Optional: one specific count's id"), "limit" to int("Max rows when listing, default 10")))
        ),
        local(
            "getPurchaseRequests",
            "List recent purchase requests, optionally filtered by branch or status.",
            obj(
                props(
                    "branchId" to int("Optional branch filter"),
                    "status" to strEnum(
                        "Optional status filter",
                        listOf("DRAFT", "SUBMITTED", "APPROVED", "PARTIALLY_APPROVED", "REJECTED", "ORDERED", "RECEIVED", "CANCELLED")
                    ),
                    "limit" to int("Max rows, default 20")
                )
            )
        ),
        local(
            "getPurchaseRequest",
            "Get one purchase request's full detail (items, quantities, status) by id.",
            obj(props("requestId" to int("The purchase request's id")), required = listOf("requestId"))
        ),
        local(
            "getSalesInvoices",
            "List recent sales invoices, optionally filtered by branch or date range (epoch millis).",
            obj(
                props(
                    "branchId" to int("Optional branch filter"),
                    "fromDate" to num("Optional start date, epoch millis"),
                    "toDate" to num("Optional end date, epoch millis"),
                    "limit" to int("Max rows, default 20")
                )
            )
        ),
        local(
            "getSalesInvoice",
            "Get one sales invoice's full detail (items, totals, customer) by id.",
            obj(props("invoiceId" to int("The invoice's id")), required = listOf("invoiceId"))
        ),
        local(
            "getGoals",
            "List current sales/commission goals, optionally for one product.",
            obj(props("productId" to int("Optional: limit to one product's goal")))
        ),
        local(
            "getGoalProgress",
            "Get one goal's achievement so far (target vs. actual sold quantity, percent complete) by goal id.",
            obj(props("goalId" to int("The goal's id")), required = listOf("goalId"))
        ),
        local(
            "compareBranches",
            "Compare stock levels (or sales totals) for a product, or overall, across two or more branches.",
            obj(
                props(
                    "branchIds" to arr("integer", "Branch ids to compare (2 or more)"),
                    "productId" to int("Optional: compare one product only; omit to compare overall stock value/counts"),
                    "metric" to strEnum("What to compare", listOf("STOCK", "SALES"))
                ),
                required = listOf("branchIds")
            )
        ),
        local(
            "createPurchaseRequest",
            "Create a new purchase request for one or more products at a branch. WRITE action — the app must show an Arabic confirmation dialog before executing this, per the assistant's action-confirmation rules.",
            obj(
                props(
                    "branchId" to int("The branch the request is for"),
                    "supplierId" to int("Optional supplier id"),
                    "items" to buildJsonObject {
                        put("type", "array")
                        put("description", "Line items to request")
                        put(
                            "items",
                            obj(
                                props("productId" to int("Product id"), "quantity" to num("Requested quantity")),
                                required = listOf("productId", "quantity")
                            )
                        )
                    },
                    "notes" to str("Optional free-text notes")
                ),
                required = listOf("branchId", "items")
            ),
            risk = ToolRisk.WRITE
        ),
        local(
            "createReport",
            "Assemble a report (inventory / counting / purchases / sales / goals / expired items / AI analysis) from local data, ready to preview, save to Drive, or turn into a Google Doc. This step itself only builds the report data — it does not save or send anything, so it does not need confirmation.",
            obj(
                props(
                    "reportType" to strEnum(
                        "Which report to build",
                        listOf("INVENTORY", "COUNTING", "PURCHASES", "SALES", "GOALS", "EXPIRED_ITEMS", "AI_ANALYSIS")
                    ),
                    "branchId" to int("Optional: scope the report to one branch"),
                    "fromDate" to num("Optional start date, epoch millis"),
                    "toDate" to num("Optional end date, epoch millis")
                ),
                required = listOf("reportType")
            )
        ),
        backend(
            "saveReportToDrive",
            "Upload a previously built report (see createReport) to the app's Google Drive folder as a file. WRITE action — requires confirmation.",
            obj(
                props(
                    "title" to str("File title/name"),
                    "reportMarkdown" to str("The report content, as Markdown or plain text"),
                    "folder" to strEnum(
                        "Which app subfolder to save into",
                        listOf("Imports", "Invoices", "PurchaseRequests", "Counting", "Goals", "Reports", "Images", "Backups")
                    )
                ),
                required = listOf("title", "reportMarkdown")
            )
        ),
        backend(
            "createGoogleDoc",
            "Create a structured Google Doc from a previously built report (see createReport) and save it in the app's Drive folder. WRITE action — requires confirmation.",
            obj(
                props("title" to str("Document title"), "reportMarkdown" to str("The report content, as Markdown or plain text")),
                required = listOf("title", "reportMarkdown")
            )
        ),
        backend(
            "createCalendarEvent",
            "Create a Google Calendar event (counting schedule, inventory review, reorder reminder, goal review, expiry reminder). WRITE action — requires confirmation.",
            obj(
                props(
                    "title" to str("Event title"),
                    "startDateTime" to str("ISO-8601 start date-time, e.g. 2026-10-01T09:00:00+03:00"),
                    "endDateTime" to str("ISO-8601 end date-time"),
                    "description" to str("Optional event description")
                ),
                required = listOf("title", "startDateTime", "endDateTime")
            )
        ),
        backend(
            "sendEmail",
            "Send an email (purchase request, inventory/counting/AI report) via the user's connected Gmail account, optionally with a Drive-hosted attachment link. WRITE action — requires confirmation.",
            obj(
                props(
                    "to" to str("Recipient email address"),
                    "subject" to str("Email subject"),
                    "body" to str("Email body (plain text)"),
                    "attachmentDriveFileId" to str("Optional: a Drive file id (from saveReportToDrive) to link/attach")
                ),
                required = listOf("to", "subject", "body")
            )
        )
    )

    private val byName: Map<String, ToolDefinition> = all.associateBy { it.name }

    fun find(name: String): ToolDefinition? = byName[name]

    fun asGeminiTools(): List<GeminiTool> = all.map { it.toGeminiTool() }

    /** Only the tools the app should be allowed to call at all in a given turn — kept as a single
     *  list (rather than per-conversation subsets) since every tool is safe to always offer:
     *  read-only tools are harmless, and write tools are already gated by [ToolRisk.WRITE] +
     *  confirmation on the client. */
    val localToolNames: Set<String> = all.filter { it.site == ToolExecutionSite.LOCAL }.map { it.name }.toSet()
    val backendToolNames: Set<String> = all.filter { it.site == ToolExecutionSite.BACKEND }.map { it.name }.toSet()
    val writeToolNames: Set<String> = all.filter { it.risk == ToolRisk.WRITE }.map { it.name }.toSet()
}
