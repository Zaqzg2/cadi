package com.inventorysmartai.app.navigation

/**
 * The screens the assistant may open by name (its `openScreen` tool). The model only ever picks one of these names; the route
 * strings stay in [Destination], so renaming a route can never leave the assistant pointing at a screen that no longer exists.
 *
 * [labelAr] is what the model is told the screen is called and what it says back to the person.
 */
enum class AssistantScreen(val labelAr: String, val route: String) {
    HOME("الرئيسية", Destination.Home.route),
    STOCK("صفحة المخزون (بطاقات المخزون والجرد والمشتريات)", Destination.Stock.route),
    INVENTORY("الأصناف (قائمة المخزون)", Destination.Inventory.route),
    COUNTING("الجرد", Destination.CountingList.route),
    PURCHASES("المشتريات — طلبات الشراء / فواتير الشراء", Destination.PurchaseList.route),
    NEW_PURCHASE_REQUEST("طلب شراء جديد", Destination.PurchaseDetail.createRoute()),
    SALES("المبيعات — فواتير البيع", Destination.SalesList.route),
    NEW_SALES_INVOICE("فاتورة بيع جديدة", Destination.SalesDetail.createRoute()),
    GOALS("الأهداف والعمولات", Destination.Goals.route),
    REPORTS("التقارير", Destination.Reports.route),
    DATA_CENTER("مركز البيانات (الاستيراد والإدخال)", Destination.DataCenter.route),
    SETTINGS("الإعدادات", Destination.Settings.route),
    MORE("المزيد", Destination.More.route);

    companion object {
        /** The exact names offered to the model, in declaration order. */
        val keys: List<String> get() = AssistantScreen.entries.map { it.name }

        /** Tolerates case and stray spaces ("inventory " -> INVENTORY); anything else is null so the tool can report an unknown screen. */
        fun fromKey(key: String?): AssistantScreen? {
            val wanted = key?.trim().orEmpty()
            if (wanted.isEmpty()) return null
            return AssistantScreen.entries.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
        }
    }
}
