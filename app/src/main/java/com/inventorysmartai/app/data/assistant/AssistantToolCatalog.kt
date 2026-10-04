package com.inventorysmartai.app.data.assistant

/**
 * The LOCAL tools of the assistant (everything that reads or writes Room), as OpenAI-format function
 * definitions. Names and parameters are copied from the backend's gemini/ToolCatalog.kt so
 * [DefaultLocalToolExecutor] answers them unchanged. The four Google Workspace tools (Drive, Docs,
 * Calendar, Gmail) are deliberately absent: they need the backend's OAuth tokens, and this path has no backend.
 */
internal object AssistantToolCatalog {

    private fun int(desc: String) = mapOf("type" to "integer", "description" to desc)
    private fun num(desc: String) = mapOf("type" to "number", "description" to desc)
    private fun str(desc: String) = mapOf("type" to "string", "description" to desc)
    private fun strEnum(desc: String, values: List<String>) = mapOf("type" to "string", "description" to desc, "enum" to values)
    private fun intArray(desc: String) = mapOf("type" to "array", "description" to desc, "items" to mapOf("type" to "integer"))

    private fun obj(properties: Map<String, Any?> = emptyMap(), required: List<String> = emptyList()): Map<String, Any?> =
        buildMap {
            put("type", "object")
            put("properties", properties)
            if (required.isNotEmpty()) put("required", required)
        }

    private fun tool(name: String, description: String, parameters: Map<String, Any?>): Map<String, Any?> = mapOf(
        "type" to "function",
        "function" to mapOf("name" to name, "description" to description, "parameters" to parameters)
    )

    val tools: List<Map<String, Any?>> = listOf(
        tool("getProducts", "List products in the catalog, optionally filtered by category. Use for broad questions about what products exist.",
            obj(mapOf("categoryId" to int("Optional category id filter"), "limit" to int("Max rows to return, default 50")))),
        tool("getProduct", "Get one product's full detail (identifiers, stock per branch, category/unit) by its id.",
            obj(mapOf("productId" to int("The product's id")), listOf("productId"))),
        tool("searchProducts", "Search products by name, item number, or barcode (partial match).",
            obj(mapOf("query" to str("Search text — name, item number, or barcode"), "limit" to int("Max rows, default 20")), listOf("query"))),
        tool("getInventory", "Get current stock levels across all branches, optionally filtered by product or status.",
            obj(mapOf(
                "productId" to int("Optional: limit to one product"),
                "status" to strEnum("Optional status filter", listOf("AVAILABLE", "LOW", "ZERO", "NEAR_EXPIRY", "EXPIRED"))
            ))),
        tool("getBranchInventory", "Get current stock levels for one specific branch.",
            obj(mapOf("branchId" to int("The branch id")), listOf("branchId"))),
        tool("getLowStock", "List products at or below their reorder point / minimum stock, across all branches or one branch.",
            obj(mapOf("branchId" to int("Optional: limit to one branch")))),
        tool("getZeroStock", "List products with zero stock, across all branches or one branch.",
            obj(mapOf("branchId" to int("Optional: limit to one branch")))),
        tool("getExpiredItems", "List batches/products already past their expiry date.",
            obj(mapOf("branchId" to int("Optional: limit to one branch")))),
        tool("getNearExpiryItems", "List batches/products expiring within the app's configured near-expiry window.",
            obj(mapOf("branchId" to int("Optional: limit to one branch")))),
        tool("getInventoryMovements", "Get the stock movement history (purchases, sales, adjustments, transfers) for one product.",
            obj(mapOf("productId" to int("The product's id"), "limit" to int("Max rows, default 30")), listOf("productId"))),
        tool("getCounting", "Get recent physical inventory counts, or one count's detail by id.",
            obj(mapOf("countId" to int("Optional: one specific count's id"), "limit" to int("Max rows when listing, default 10")))),
        tool("getPurchaseRequests", "List recent purchase requests, optionally filtered by branch or status.",
            obj(mapOf(
                "branchId" to int("Optional branch filter"),
                "status" to strEnum("Optional status filter", listOf("DRAFT", "SUBMITTED", "APPROVED", "PARTIALLY_APPROVED", "REJECTED", "ORDERED", "RECEIVED", "CANCELLED")),
                "limit" to int("Max rows, default 20")
            ))),
        tool("getPurchaseRequest", "Get one purchase request's full detail (items, quantities, status) by id.",
            obj(mapOf("requestId" to int("The purchase request's id")), listOf("requestId"))),
        tool("getSalesInvoices", "List recent sales invoices, optionally filtered by branch or date range (epoch millis).",
            obj(mapOf(
                "branchId" to int("Optional branch filter"),
                "fromDate" to num("Optional start date, epoch millis"),
                "toDate" to num("Optional end date, epoch millis"),
                "limit" to int("Max rows, default 20")
            ))),
        tool("getSalesInvoice", "Get one sales invoice's full detail (items, totals, customer) by id.",
            obj(mapOf("invoiceId" to int("The invoice's id")), listOf("invoiceId"))),
        tool("getGoals", "List current sales/commission goals, optionally for one product.",
            obj(mapOf("productId" to int("Optional: limit to one product's goal")))),
        tool("getGoalProgress", "Get one goal's achievement so far (target vs. actual sold quantity, percent complete) by goal id.",
            obj(mapOf("goalId" to int("The goal's id")), listOf("goalId"))),
        tool("compareBranches", "Compare stock levels (or sales totals) for a product, or overall, across two or more branches.",
            obj(mapOf(
                "branchIds" to intArray("Branch ids to compare (2 or more)"),
                "productId" to int("Optional: compare one product only; omit to compare overall"),
                "metric" to strEnum("What to compare", listOf("STOCK", "SALES"))
            ), listOf("branchIds"))),
        tool("createPurchaseRequest", "Create a new purchase request for one or more products at a branch. WRITE action — the app shows an Arabic confirmation dialog before executing it.",
            obj(mapOf(
                "branchId" to int("The branch the request is for"),
                "supplierId" to int("Optional supplier id"),
                "items" to mapOf(
                    "type" to "array",
                    "description" to "Line items to request",
                    "items" to obj(mapOf("productId" to int("Product id"), "quantity" to num("Requested quantity")), listOf("productId", "quantity"))
                ),
                "notes" to str("Optional free-text notes")
            ), listOf("branchId", "items"))),
        tool("createReport", "Assemble a report (inventory / counting / purchases / sales / goals / expired items / AI analysis) from local data, ready to preview. Only builds the report data — saves and sends nothing.",
            obj(mapOf(
                "reportType" to strEnum("Which report to build", listOf("INVENTORY", "COUNTING", "PURCHASES", "SALES", "GOALS", "EXPIRED_ITEMS", "AI_ANALYSIS")),
                "branchId" to int("Optional: scope the report to one branch"),
                "fromDate" to num("Optional start date, epoch millis"),
                "toDate" to num("Optional end date, epoch millis")
            ), listOf("reportType")))
    )

    val systemPrompt: String = """
        أنت "المساعد الذكي" داخل تطبيق Inventory Smart AI لإدارة المخزون. أجب دائمًا بالعربية الفصحى المبسّطة وبإيجاز.
        استخدم الأدوات المتاحة (tools) للحصول على أي بيانات فعلية عن المنتجات أو المخزون أو المبيعات أو طلبات الشراء أو الأهداف أو الفروع — لا تخترع أرقامًا أو أسماء أصناف أو نتائج من عندك أبدًا. إن لم تتوفر أداة مناسبة لسؤال المستخدم، وضّح ذلك بصراحة بدلاً من الافتراض.
        إذا طلب المستخدم إنشاء طلب شراء، استدعِ الأداة المناسبة مباشرة — سيتولى التطبيق عرض تأكيد صريح على المستخدم قبل أي تنفيذ فعلي، فلا داعي لأن تطلب أنت التأكيد نصيًا.
        حفظ التقارير في Drive وإرسال البريد وإضافة مواعيد التقويم غير متاحة في هذا الوضع؛ إن طُلبت فاعتذر بلطف واعرض بدلاً منها إعداد التقرير أو ملخصًا نصيًا.
        عند تقديم تحليل أو توصية (مخزون منخفض، توصية شراء، تقدم هدف)، اذكر بوضوح أنها توصية أو تحليل من الذكاء الاصطناعي وليست حقيقة نهائية مؤكدة.
    """.trimIndent()
}
