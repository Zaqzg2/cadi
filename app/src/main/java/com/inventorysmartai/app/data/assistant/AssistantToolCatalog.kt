package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.navigation.AssistantScreen

/**
 * Every tool the assistant can call, as OpenAI-format function definitions: the LOCAL tools (everything that reads or writes
 * Room — answered by [DefaultLocalToolExecutor]) plus the four Google Workspace tools (Drive, Docs, Calendar, Gmail), which
 * [AssistantToolExecutor] forwards to the backend after the person confirmed them. The server holds the Google credentials;
 * the model only ever sees the tool's name and a short JSON result.
 */
internal object AssistantToolCatalog {

    /** The tools that act on the person's Google account. All of them are WRITE/send actions and need a confirmation. */
    val WORKSPACE_TOOL_NAMES: Set<String> = setOf("saveReportToDrive", "createGoogleDoc", "createCalendarEvent", "sendEmail")

    /** The tools that read or edit a file the person attached. Only offered to the model once the conversation has an attachment. */
    val ATTACHMENT_TOOL_NAMES: Set<String> = setOf("listAttachments", "readAttachment", "queryTable", "fillForm")

    /**
     * Every file/number tool, answered by [com.inventorysmartai.app.data.assistant.files.FileToolExecutor]. None of them needs a
     * confirmation: they only read what the person attached or create a NEW file in the app's own cache (nothing is sent anywhere).
     */
    val FILE_TOOL_NAMES: Set<String> =
        ATTACHMENT_TOOL_NAMES + setOf("calculate", "calculateTieredCommission", "createSpreadsheet", "buildDashboard")

    /** "Control the app": they only navigate (open a screen, a filtered list, a record) and change no data, so no confirmation. */
    val APP_CONTROL_TOOL_NAMES: Set<String> = setOf("openScreen", "showInventory", "openRecord")

    /** The smart report analyst: read-only questions answered with exact numbers computed in the app. */
    val INSIGHT_TOOL_NAMES: Set<String> = setOf("analyzeGoals", "compareFlows", "rankBranchesByGoals")

    /** Purchase planning: suggestPurchaseOrder reads; draftPurchaseOrder WRITES a draft that the person reviews before it is sent. */
    val PLANNING_TOOL_NAMES: Set<String> = setOf("suggestPurchaseOrder", "draftPurchaseOrder")

    /** Name matching: matchProductNames reads and only suggests; addProductAlias writes one confirmed spelling. */
    val MATCHING_TOOL_NAMES: Set<String> = setOf("matchProductNames", "addProductAlias")

    /** Every tool of the four groups above (answered by their own executors, not by DefaultLocalToolExecutor). */
    val SMART_TOOL_NAMES: Set<String> = APP_CONTROL_TOOL_NAMES + INSIGHT_TOOL_NAMES + PLANNING_TOOL_NAMES + MATCHING_TOOL_NAMES

    /** The ones that change data: the app shows the person an explicit confirmation before running them. */
    val SMART_WRITE_TOOL_NAMES: Set<String> = setOf("draftPurchaseOrder", "addProductAlias")

    private fun int(desc: String) = mapOf("type" to "integer", "description" to desc)
    private fun num(desc: String) = mapOf("type" to "number", "description" to desc)
    private fun str(desc: String) = mapOf("type" to "string", "description" to desc)
    private fun strEnum(desc: String, values: List<String>) = mapOf("type" to "string", "description" to desc, "enum" to values)
    private fun intArray(desc: String) = mapOf("type" to "array", "description" to desc, "items" to mapOf("type" to "integer"))
    private fun bool(desc: String) = mapOf("type" to "boolean", "description" to desc)
    private fun strArray(desc: String) = mapOf("type" to "array", "description" to desc, "items" to mapOf("type" to "string"))
    private fun arr(desc: String, items: Map<String, Any?>) = mapOf("type" to "array", "description" to desc, "items" to items)

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

    private val dataTools: List<Map<String, Any?>> = listOf(
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
            ), listOf("reportType"))),
        tool("saveReportToDrive", "Save a report as a Markdown file in the user's Google Drive (folder \"Reports\"). WRITE action — the app shows an Arabic confirmation dialog before executing it. Write the report text yourself (using data from createReport / the other tools) and pass it as reportMarkdown.",
            obj(mapOf("title" to str("File title, without an extension"), "reportMarkdown" to str("The complete report text, in Markdown")), listOf("title", "reportMarkdown"))),
        tool("createGoogleDoc", "Create a Google Docs document containing a report. WRITE action — confirmation dialog first. Write the report text yourself and pass it as reportMarkdown.",
            obj(mapOf("title" to str("Document title"), "reportMarkdown" to str("The complete report text, in Markdown")), listOf("title", "reportMarkdown"))),
        tool("createCalendarEvent", "Add an event to the user's Google Calendar. WRITE action — confirmation dialog first. Times are ISO-8601 with a UTC offset, e.g. 2026-10-12T09:00:00+03:00.",
            obj(mapOf(
                "title" to str("Event title"),
                "startIso" to str("Start time, ISO-8601 with UTC offset"),
                "endIso" to str("End time, ISO-8601 with UTC offset"),
                "description" to str("Optional description")
            ), listOf("title", "startIso", "endIso"))),
        tool("sendEmail", "Send an email from the user's Gmail account. WRITE action — confirmation dialog first. Only send to an address the user gave you.",
            obj(mapOf(
                "to" to str("Recipient email address"),
                "subject" to str("Subject line (single line)"),
                "body" to str("Plain-text message body"),
                "attachmentDriveFileId" to str("Optional: the Drive file id returned by saveReportToDrive, to link in the email")
            ), listOf("to", "subject", "body")))
    )

    // ---- file tools (see data/assistant/files) ----

    private val attachmentIdParam = str("Attachment id from the [ملفات مرفقة] note or listAttachments (may be omitted when only one file is attached)")

    /** The arguments of queryTable — also the `source` of a dashboard KPI, chart or table. */
    private val queryProperties: Map<String, Any?> = mapOf(
        "attachmentId" to attachmentIdParam,
        "sheet" to str("Sheet or table name (default: the first)"),
        "headerRow" to int("Row number holding the column titles (detected automatically when omitted)"),
        "filters" to arr(
            "Rows to keep (all conditions must hold)",
            obj(
                mapOf(
                    "column" to str("Column title or letter"),
                    "op" to strEnum("Comparison", listOf("eq", "ne", "contains", "notContains", "gt", "gte", "lt", "lte", "empty", "notEmpty")),
                    "value" to str("Value to compare with (numbers as digits; not needed for empty / notEmpty)")
                ),
                listOf("column", "op")
            )
        ),
        "groupBy" to strArray("Columns to group by"),
        "aggregates" to arr(
            "What to compute per group (or over all rows when there is no groupBy)",
            obj(
                mapOf(
                    "column" to str("Column to aggregate (omit for a plain row count)"),
                    "op" to strEnum("Aggregation", listOf("sum", "avg", "min", "max", "count", "countDistinct")),
                    "as" to str("Optional name for the result column")
                ),
                listOf("op")
            )
        ),
        "sortBy" to obj(mapOf("column" to str("Result column to sort by"), "direction" to strEnum("Order", listOf("asc", "desc")))),
        "select" to strArray("Columns to list when not grouping"),
        "limit" to int("Max result rows (default 50, max 200)")
    )

    private val fileTools: List<Map<String, Any?>> = listOf(
        tool("listAttachments", "List the files the user attached in this conversation: id, name, kind and sheets. Use it when unsure which file is meant.",
            obj()),
        tool("readAttachment",
            "Read an attached file. Spreadsheets (and tables found in photos/PDF/Word) come back as lines with the REAL cell addresses (e.g. C5=9 حبه) — use those addresses with fillForm. " +
                "Word/text files and photos/PDF (read by OCR, so check important numbers) come back as text, paged. To locate a label (a supermarket, a product) pass find instead of scanning rows.",
            obj(mapOf(
                "attachmentId" to attachmentIdParam,
                "sheet" to str("Sheet/table name (spreadsheets, or a table found in OCR text)"),
                "fromRow" to int("First row to show (spreadsheets); continue from nextRow"),
                "maxRows" to int("Max rows per call, default 40"),
                "find" to str("Search all cells for this text and return their addresses"),
                "fromChar" to int("Text files: start position; continue from nextChar"),
                "maxChars" to int("Text files: characters per call, default 6000")
            ))),
        tool("queryTable",
            "Exact filtering, grouping and totals on an attached table that has a header row. ALWAYS use this instead of adding numbers yourself. " +
                "A column is named by its title or its letter. Cells such as \"10 كرتون\" count as 10 (mixed units are reported in notes).",
            obj(queryProperties)),
        tool("calculate",
            "Evaluate arithmetic exactly: + - * / ^ ( ) and % (percent), plus sum, avg, min, max, round, floor, ceil, abs, sqrt, pow, mod, percent(part, whole). " +
                "Use it for every calculation instead of computing in your head. Pass expression, or expressions for several.",
            obj(mapOf("expression" to str("One expression, e.g. 200*15%"), "expressions" to strArray("Several expressions at once")))),
        tool("calculateTieredCommission",
            "Commission for tiered targets, like the monthly promoter sheet where each product group has levels 1/2/3, each with a quantity target and a commission amount. " +
                "Give every group's tiers and the quantity achieved; returns the level reached, the commission, and what is left for the next level. " +
                "mode HIGHEST_TIER (default) pays the highest level reached; CUMULATIVE adds up every level reached.",
            obj(mapOf(
                "mode" to strEnum("How levels combine", listOf("HIGHEST_TIER", "CUMULATIVE")),
                "groups" to arr(
                    "Product groups",
                    obj(
                        mapOf(
                            "name" to str("Group name"),
                            "achieved" to num("Quantity achieved for the whole group"),
                            "tiers" to arr(
                                "Levels, lowest first",
                                obj(mapOf("target" to num("Quantity needed for this level"), "commission" to num("Commission paid for this level")), listOf("target", "commission"))
                            )
                        ),
                        listOf("name", "achieved", "tiers")
                    )
                )
            ), listOf("groups"))),
        tool("fillForm",
            "Fill cells of an attached Excel (.xlsx) form and save the result as a NEW file shown to the user — formatting, borders, merged cells and other sheets are kept exactly. " +
                "First read the form with readAttachment to find the exact cell addresses. Formula cells are never overwritten; a cell inside a merged range is written to its top-left cell and reported in redirected. " +
                "Values are written as text exactly as given (e.g. \"10 كرتون\", \"12\"); pass null to clear a cell. Report any skipped cells to the user honestly.",
            obj(mapOf(
                "attachmentId" to attachmentIdParam,
                "sheet" to str("Default sheet for the cells (default: the first)"),
                "cells" to arr(
                    "Cells to fill (max 400 per call)",
                    obj(mapOf("cell" to str("Address such as C7"), "value" to str("Text to write"), "sheet" to str("Optional sheet for this cell")), listOf("cell"))
                ),
                "outputName" to str("Optional name for the filled file, without extension")
            ), listOf("cells"))),
        tool("createSpreadsheet",
            "Create a NEW Excel file (.xlsx) from tables you provide — styled header row, filterable, right-to-left by default — and show it to the user with open/share buttons. " +
                "Give sheets, or headers + rows for a single sheet. Cells are text; plain numbers such as \"120\" or \"15.5\" become real numbers.",
            obj(mapOf(
                "title" to str("File name, without extension"),
                "sheets" to arr(
                    "One entry per sheet",
                    obj(mapOf("name" to str("Sheet name"), "headers" to strArray("Column titles"), "rows" to arr("Rows", strArray("Cells of one row"))), listOf("headers", "rows"))
                ),
                "headers" to strArray("Column titles (single-sheet shorthand)"),
                "rows" to arr("Rows (single-sheet shorthand)", strArray("Cells of one row")),
                "rtl" to bool("Right-to-left sheets, default true")
            ), listOf("title"))),
        tool("buildDashboard",
            "Create a visual dashboard — KPI cards, charts, tables — that the user views inside the app and can share. Chart types: bar (horizontal bars, best for categories), column (up to 8 categories), line (one or more series), pie, donut. " +
                "To avoid copying numbers, a kpi/chart/table may carry source = queryTable arguments plus attachmentId: a kpi source uses aggregates (first value shown), a chart source uses groupBy + aggregates (first column = labels, the rest = series), a table source is a plain query. " +
                "Otherwise give the numbers yourself (labels + values, e.g. from other tools' results).",
            obj(mapOf(
                "title" to str("Dashboard title"),
                "subtitle" to str("Optional subtitle (period, branch...)"),
                "kpis" to arr(
                    "Headline numbers",
                    obj(mapOf(
                        "label" to str("What the number is"),
                        "value" to str("The number or text to show (omit when using source)"),
                        "unit" to str("Optional unit"),
                        "note" to str("Optional small note"),
                        "tone" to strEnum("Colour cue", listOf("good", "warn", "bad", "neutral")),
                        "source" to obj(queryProperties)
                    ), listOf("label"))
                ),
                "charts" to arr(
                    "Charts",
                    obj(mapOf(
                        "type" to strEnum("Chart type", listOf("bar", "column", "line", "pie", "donut")),
                        "title" to str("Chart title"),
                        "unit" to str("Optional unit"),
                        "labels" to strArray("Category labels"),
                        "values" to arr("One series of numbers, same length as labels", mapOf("type" to "number")),
                        "series" to arr(
                            "Several series (line/column)",
                            obj(mapOf("name" to str("Series name"), "values" to arr("Numbers", mapOf("type" to "number"))), listOf("values"))
                        ),
                        "source" to obj(queryProperties)
                    ), listOf("type"))
                ),
                "tables" to arr(
                    "Tables",
                    obj(mapOf(
                        "title" to str("Table title"),
                        "headers" to strArray("Column titles"),
                        "rows" to arr("Rows", strArray("Cells of one row")),
                        "source" to obj(queryProperties)
                    ))
                ),
                "notes" to strArray("Short notes shown at the bottom")
            ), listOf("title")))
    )

    private val periodProperties: Map<String, Any?> = mapOf(
        "month" to str("Calendar month as YYYY-MM, e.g. 2026-10. Default: the current month"),
        "fromDate" to num("Alternative to month: period start, epoch millis"),
        "toDate" to num("Alternative to month: period end, epoch millis")
    )

    /** The app-control, report-analyst, purchase-planning and name-matching tools. */
    private val smartTools: List<Map<String, Any?>> = listOf(
        // ---- control the app
        tool("openScreen",
            "Open one of the app's screens for the person (navigation only, changes no data). Use for «افتح صفحة الأصناف» / «انتقل إلى المشتريات» / «افتح التقارير». " +
                "To open the item list WITH filters use showInventory instead. Screens: " +
                AssistantScreen.entries.joinToString("; ") { "${it.name} = ${it.labelAr}" } + ".",
            obj(mapOf("screen" to strEnum("Which screen to open", AssistantScreen.keys)), listOf("screen"))),
        tool("showInventory",
            "Open the item list (الأصناف) with filters already applied and report how many items match (changes no data). " +
                "Use for «اعرض الأصناف منتهية الصلاحية» / «فلتر الأصناف التي رصيدها أقل من 10» / «اعرض الناقص في الفرع الأول». " +
                "quantityBelow / quantityAbove bound the stock quantity strictly («أقل من 10» excludes 10) unless inclusive=true; with a branch the quantity is that branch's stock, otherwise the total. All filters are optional and combine.",
            obj(mapOf(
                "status" to strEnum("Stock status filter", listOf("ALL", "AVAILABLE", "LOW", "ZERO", "NEAR_EXPIRY", "EXPIRED")),
                "quantityBelow" to num("Only items whose stock is LESS than this number"),
                "quantityAbove" to num("Only items whose stock is MORE than this number"),
                "inclusive" to bool("true = the quantity limits include the number itself (<= / >=)"),
                "search" to str("Text to look for in the item name"),
                "branch" to str("Branch NAME as the person said it (e.g. «الفرع الأول»); alternative to branchId"),
                "branchId" to int("Branch id"),
                "category" to str("Category NAME as the person said it; alternative to categoryId"),
                "categoryId" to int("Category id"),
                "sortBy" to strEnum("Order of the list", listOf("NAME_ASC", "NAME_DESC", "QUANTITY_ASC", "QUANTITY_DESC")),
                "open" to bool("Default true: open the item list on screen. false = only report what matches (the chat shows it as a table)")
            ))),
        tool("openRecord",
            "Open one existing record's detail screen (changes no data): a product, a purchase request, a sales invoice or a stock count. " +
                "Use it to show the person a draft you just created, or for «افتح الصنف X».",
            obj(mapOf(
                "type" to strEnum("Record type", listOf("PRODUCT", "PURCHASE_REQUEST", "SALES_INVOICE", "COUNT")),
                "id" to int("The record id")
            ), listOf("type", "id"))),
        // ---- smart report analyst (read-only, exact numbers)
        tool("analyzeGoals",
            "Goal-achievement analysis with exact numbers: every goal whose period overlaps the month (default: the current month) with its target, what was sold, the remainder and the percent, worst first. " +
                "Use for «ما الأصناف التي لم تحقق هدف الشهر؟». Never compute these yourself.",
            obj(periodProperties + mapOf(
                "onlyUnmet" to bool("true = list only the goals NOT yet met"),
                "limit" to int("Max goals to return, default 20")
            ))),
        tool("compareFlows",
            "Compare stock RECEIVED (purchases) against SALES and the goal targets for a period (default: the current month), per product or per branch. " +
                "Use for «قارن الوارد بالمبيعات والأهداف». Exact sums — never add the numbers yourself.",
            obj(periodProperties + mapOf(
                "groupBy" to strEnum("PRODUCT (default) or BRANCH", listOf("PRODUCT", "BRANCH")),
                "branchId" to int("Optional: only this branch (PRODUCT grouping)"),
                "limit" to int("Max rows to return, default 20")
            ))),
        tool("rankBranchesByGoals",
            "Rank the branches by their contribution to the goals of a period (default: the current month). Goals belong to the whole company, not to a branch, " +
                "so a branch's percent = what it sold of the goal products ÷ the total target. Use for «رتب الفروع حسب نسبة تحقيق الهدف». Say this basis when you answer.",
            obj(periodProperties)),
        // ---- purchase order from shortages (the person reviews before it is sent)
        tool("suggestPurchaseOrder",
            "Find the products that are short (stock at or below their minimum / reorder point, or zero) and compute how much to order for each. Exact arithmetic — never work out quantities yourself. " +
                "The result includes the rule used (state it to the person) and the branches and suppliers. Read-only; the next step is draftPurchaseOrder. Use for «جهّز أمر شراء للنواقص».",
            obj(mapOf(
                "targetFactor" to num("Order up to this multiple of the minimum level (default 2; range 1 to 10)"),
                "categoryId" to int("Optional category filter"),
                "limit" to int("Max lines to return, default 40")
            ))),
        tool("draftPurchaseOrder",
            "Save a purchase request as a DRAFT and open it for the person to review and edit. You never send or approve it: the person presses «إرسال الطلب» on that screen. " +
                "Use after suggestPurchaseOrder, with the final quantities the person agreed to. The app asks the person to confirm before saving. " +
                "Prefer this over createPurchaseRequest for orders built from shortages.",
            obj(mapOf(
                "branchId" to int("Receiving branch id (optional when the company has a single branch)"),
                "supplierId" to int("Optional supplier id"),
                "notes" to str("Optional note"),
                "items" to arr(
                    "The lines of the order",
                    obj(mapOf("productId" to int("Product id"), "quantity" to num("Quantity to order, greater than 0")), listOf("productId", "quantity"))
                )
            ), listOf("items"))),
        // ---- name matching (suggestions only)
        tool("matchProductNames",
            "Match names written in different ways (spelling, word order, ml/مل, missing spaces, the lines of a photographed purchase invoice) to the catalog. " +
                "Returns per name a confidence (EXACT, HIGH, MEDIUM, LOW, AMBIGUOUS, NONE), the best product and alternatives. These are SUGGESTIONS: " +
                "show everything that is not EXACT to the person and let them choose — never assume. Read-only.",
            obj(mapOf(
                "items" to arr(
                    "The names to match (up to 60)",
                    obj(mapOf(
                        "name" to str("The name exactly as written"),
                        "quantity" to num("Optional quantity, echoed back in the result"),
                        "unitPrice" to num("Optional unit price, echoed back in the result")
                    ), listOf("name"))
                )
            ), listOf("items"))),
        tool("addProductAlias",
            "Save a spelling the person has just CONFIRMED as an alternate name of a product, so it is recognised automatically next time. " +
                "Only after the person confirmed the match. The app asks for confirmation before saving.",
            obj(mapOf(
                "productId" to int("The product id"),
                "alias" to str("The alternate name exactly as it was written")
            ), listOf("productId", "alias")))
    )

    /** Everything the assistant can call. Tests and the tool-name checks use this complete list. */
    val tools: List<Map<String, Any?>> = dataTools + smartTools + fileTools

    /** The tools to offer for one request: the file-reading ones only make sense once something is attached, so they are left out
     *  until then to keep the request small (free tiers have small tokens-per-minute budgets). */
    fun toolsFor(hasAttachments: Boolean): List<Map<String, Any?>> =
        if (hasAttachments) tools else tools.filterNot { toolName(it) in ATTACHMENT_TOOL_NAMES }

    @Suppress("UNCHECKED_CAST")
    private fun toolName(tool: Map<String, Any?>): String = ((tool["function"] as Map<String, Any?>)["name"] as? String).orEmpty()

    val systemPrompt: String = """
        أنت "المساعد الذكي" داخل تطبيق Inventory Smart AI لإدارة المخزون. أجب دائمًا بالعربية الفصحى المبسّطة وبإيجاز.
        استخدم الأدوات المتاحة (tools) للحصول على أي بيانات فعلية عن المنتجات أو المخزون أو المبيعات أو طلبات الشراء أو الأهداف أو الفروع — لا تخترع أرقامًا أو أسماء أصناف أو نتائج من عندك أبدًا. إن لم تتوفر أداة مناسبة لسؤال المستخدم، وضّح ذلك بصراحة بدلاً من الافتراض.
        إذا طلب المستخدم إنشاء طلب شراء، استدعِ الأداة المناسبة مباشرة — سيتولى التطبيق عرض تأكيد صريح على المستخدم قبل أي تنفيذ فعلي، فلا داعي لأن تطلب أنت التأكيد نصيًا.
        لحفظ تقرير في Drive أو إنشاء مستند Google Docs أو إضافة موعد إلى التقويم أو إرسال بريد عبر Gmail: أعدّ نص التقرير بنفسك من بيانات الأدوات (createReport وغيرها) ثم استدعِ الأداة المناسبة (saveReportToDrive / createGoogleDoc / createCalendarEvent / sendEmail) مباشرة — سيعرض التطبيق تأكيدًا صريحًا على المستخدم قبل التنفيذ. لا ترسل بريدًا إلا إلى عنوان ذكره المستخدم. إن أعادت الأداة خطأ google_not_linked فاطلب منه ربط حساب Google من الإعدادات ← خدمات Google ثم أعد المحاولة، وإن أعادت أي خطأ آخر فاشرح له السبب باختصار ولا تدّعِ نجاح العملية.
        عند تقديم تحليل أو توصية (مخزون منخفض، توصية شراء، تقدم هدف)، اذكر بوضوح أنها توصية أو تحليل من الذكاء الاصطناعي وليست حقيقة نهائية مؤكدة.
        الملفات المرفقة: حين يرفق المستخدم ملفات يظهر في رسالته قسم [ملفات مرفقة] فيه معرّف كل ملف. لا تخمّن محتوى أي ملف أبدًا: اقرأه بالأداة readAttachment (للجداول والنماذج تعيد عناوين الخلايا الحقيقية مثل C5، وللبحث عن نص معيّن استخدم find). نص الصور وPDF يأتي من القراءة الضوئية وقد يخطئ في الأرقام والحروف، فنبّه المستخدم لمراجعة الأرقام المهمة.
        الحسابات: لا تجمع ولا تحسب بنفسك أبدًا. للتجميع والتصفية على جدول مرفق استخدم queryTable، ولأي عملية حسابية استخدم calculate، ولأهداف وعمولات بمستويات (الفئة الأولى/الثانية/الثالثة) استخدم calculateTieredCommission — وإن كانت الفئات في صورة فاقرأها أولًا ثم مرّر المستويات للأداة، وافترض أن العمولة هي عمولة أعلى مستوى تحقق ما لم يقل المستخدم غير ذلك، واذكر هذا الافتراض.
        تعبئة النماذج: اقرأ نموذج Excel المرفق أولًا (readAttachment) لتحديد مكان كل خلية، ثم استدعِ fillForm بقائمة {cell, value}. الخلية المدمجة تُكتب في أول خلية فيها، ولا تُكتب فوق الصيغ. إن أعادت الأداة skipped أو redirected فاذكرها للمستخدم بصدق ولا تدّعِ أن كل شيء عُبّئ.
        الملفات الناتجة: لإنشاء ملف Excel جديد استخدم createSpreadsheet، ولتحويل بيانات إلى لوحة بيانات (داشبورد) استخدم buildDashboard — ويمكنك تمرير source بدل نسخ الأرقام. يظهر الملف الناتج للمستخدم تلقائيًا تحت ردّك مع زرّي فتح ومشاركة، فاذكر ما أنشأته باختصار ولا تلصق محتواه ولا تذكر مسارات.
        التحكم بالتطبيق: openScreen لفتح شاشة، وshowInventory لعرض الأصناف بشرط (الفرع والتصنيف بالاسم كما قاله المستخدم)؛ ولا تحتاجان تأكيدًا. بعد التنفيذ قل بجملة ماذا فتحت وكم صنفًا ظهر. «أقل من 10» تعني أقل من 10 فعلًا.
        التقارير: لأسئلة الأهداف والوارد والمبيعات وترتيب الفروع استخدم analyzeGoals وcompareFlows وrankBranchesByGoals؛ الأرقام جاهزة فلا تحسب بنفسك، واذكر أساس الحساب (basis) — فالأهداف للشركة كلها لا لكل فرع.
        أمر الشراء من النواقص: suggestPurchaseOrder، ثم اعرض الأصناف والقاعدة المستخدمة وعدّل الكميات بطلب المستخدم، ثم draftPurchaseOrder (مسودة تُفتح للمراجعة). لا تقل أبدًا إنك أرسلت الطلب أو اعتمدته؛ الإرسال زرّ المستخدم. لا تستخدم createPurchaseRequest لذلك إلا بطلب صريح لإرسال فوري.
        فاتورة شراء مصورة: اقرأها بـ readAttachment ورتّب المورد والتاريخ والرقم والبنود (الاسم والكمية والسعر) كما وردت دون تخمين، ثم matchProductNames لأسماء البنود؛ ما ليس EXACT يختاره المستخدم ولا تعتمده بنفسك، وبعد تأكيده addProductAlias. نبّه إلى أخطاء القراءة الضوئية في الأرقام.
        البطاقات: نتائج أدوات التحكم والتقارير والمطابقة تظهر للمستخدم تلقائيًا كجداول وأشرطة تحت ردّك، فلا تكرر الأرقام ولا ترسم جدولًا؛ لخّص الخلاصة في جملتين.
    """.trimIndent()
}
