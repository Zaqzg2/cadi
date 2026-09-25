package com.inventorysmartai.app.navigation

/**
 * Every screen's route lives here as a sealed class rather than raw strings scattered through
 * the app. Optional numeric args (edit-or-create screens) use the classic "-1 means new" query
 * parameter pattern instead of the newer kotlinx.serialization type-safe nav — chosen
 * deliberately for Phase 1 since it is the best-understood, lowest-risk option to hand-verify
 * without a real Gradle build available.
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")

    data object Inventory : Destination("inventory")
    data object ProductDetail : Destination("inventory/product/{productId}") {
        const val ARG_PRODUCT_ID = "productId"
        fun createRoute(productId: Long) = "inventory/product/$productId"
    }

    data object Goals : Destination("goals")

    data object CountingList : Destination("counting")
    data object CountingDetail : Destination("counting/detail?countId={countId}") {
        const val ARG_COUNT_ID = "countId"
        fun createRoute(countId: Long? = null) = "counting/detail?countId=${countId ?: -1L}"
    }

    data object PurchaseList : Destination("purchases")
    data object PurchaseDetail : Destination("purchases/detail?requestId={requestId}") {
        const val ARG_REQUEST_ID = "requestId"
        fun createRoute(requestId: Long? = null) = "purchases/detail?requestId=${requestId ?: -1L}"
    }

    data object SalesList : Destination("sales")
    data object SalesDetail : Destination("sales/detail?invoiceId={invoiceId}") {
        const val ARG_INVOICE_ID = "invoiceId"
        fun createRoute(invoiceId: Long? = null) = "sales/detail?invoiceId=${invoiceId ?: -1L}"
    }

    data object Reports : Destination("reports")
    data object ReportDetail : Destination("reports/detail/{reportType}") {
        const val ARG_REPORT_TYPE = "reportType"
        fun createRoute(reportType: String) = "reports/detail/$reportType"
    }

    data object DataCenter : Destination("data_center")
    data object AiAssistant : Destination("assistant")
    data object More : Destination("more")

    // --- Phase 3: bulk import flow. Nested under one graph (route "import_flow") so its four
    // steps share a single ImportFlowViewModel via Hilt's back-stack-entry scoping — see
    // AppNavHost. ImportFlow itself is never shown as a screen; it only exists as the shared
    // ViewModel's scope. ---
    data object ImportFlow : Destination("import_flow")
    data object ImportSetup : Destination("import_flow/setup")
    data object ImportSheetSelect : Destination("import_flow/sheet")
    data object ImportAnalyzing : Destination("import_flow/analyzing")
    data object ImportColumnMapping : Destination("import_flow/mapping")
    data object ImportReview : Destination("import_flow/review")

    data object ImportHistory : Destination("data_center/import_history?filter={filter}") {
        const val ARG_FILTER = "filter"
        fun createRoute(filter: String = "all") = "data_center/import_history?filter=$filter"
    }
    data object ImportJobDetail : Destination("data_center/import_history/detail/{jobId}") {
        const val ARG_JOB_ID = "jobId"
        fun createRoute(jobId: Long) = "data_center/import_history/detail/$jobId"
    }

    data object PartyList : Destination("data_center/parties/{kind}") {
        const val ARG_KIND = "kind"
        fun createRoute(kind: String) = "data_center/parties/$kind"
    }

    data object Settings : Destination("settings")
    data object SettingsAppInfo : Destination("settings/app_info")
    data object SettingsCatalog : Destination("settings/catalog/{type}") {
        const val ARG_TYPE = "type"
        fun createRoute(type: String) = "settings/catalog/$type"
    }
    data object SettingsInventory : Destination("settings/inventory_settings")
    // --- Phase 4 ---
    data object GoogleServicesStatus : Destination("settings/google_services")
    data object SettingsPlaceholder : Destination("settings/placeholder/{key}") {
        const val ARG_KEY = "key"
        fun createRoute(key: String) = "settings/placeholder/$key"
    }

    companion object {
        /** Routes where the bottom bar is shown — every "MAIN NAVIGATION" section plus the
         *  More hub. Everything else is a secondary page navigated to normally, per spec. */
        val topLevelRoutes = setOf(
            Home.route, Inventory.route, Goals.route, CountingList.route, PurchaseList.route,
            SalesList.route, Reports.route, DataCenter.route, AiAssistant.route, Settings.route, More.route
        )
    }
}
