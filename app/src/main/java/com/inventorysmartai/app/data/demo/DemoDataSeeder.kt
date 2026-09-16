package com.inventorysmartai.app.data.demo

import com.inventorysmartai.app.data.local.database.dao.BranchDao
import com.inventorysmartai.app.data.local.database.dao.CategoryDao
import com.inventorysmartai.app.data.local.database.dao.CountingDao
import com.inventorysmartai.app.data.local.database.dao.CustomerDao
import com.inventorysmartai.app.data.local.database.dao.GoalDao
import com.inventorysmartai.app.data.local.database.dao.InventoryDao
import com.inventorysmartai.app.data.local.database.dao.ProductDao
import com.inventorysmartai.app.data.local.database.dao.PurchaseDao
import com.inventorysmartai.app.data.local.database.dao.SalesDao
import com.inventorysmartai.app.data.local.database.dao.SupplierDao
import com.inventorysmartai.app.data.local.database.dao.UnitDao
import com.inventorysmartai.app.data.local.database.entity.BranchEntity
import com.inventorysmartai.app.data.local.database.entity.CategoryEntity
import com.inventorysmartai.app.data.local.database.entity.CommissionEntity
import com.inventorysmartai.app.data.local.database.entity.CustomerEntity
import com.inventorysmartai.app.data.local.database.entity.GoalEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountItemEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryEntity
import com.inventorysmartai.app.data.local.database.entity.ProductEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestItemEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceItemEntity
import com.inventorysmartai.app.data.local.database.entity.SupplierEntity
import com.inventorysmartai.app.data.local.database.entity.UnitEntity
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * "Use local demo data only for UI previews... do not create permanent fake production
 * records" is honored like this: the app ships with a genuinely empty database, and this
 * class only ever runs when SettingsRepository.loadDemoData() is called — which only happens
 * from one explicit button in Settings. Nothing in app startup calls this.
 */
class DemoDataSeeder @Inject constructor(
    private val branchDao: BranchDao,
    private val categoryDao: CategoryDao,
    private val unitDao: UnitDao,
    private val customerDao: CustomerDao,
    private val supplierDao: SupplierDao,
    private val productDao: ProductDao,
    private val inventoryDao: InventoryDao,
    private val countingDao: CountingDao,
    private val purchaseDao: PurchaseDao,
    private val salesDao: SalesDao,
    private val goalDao: GoalDao
) {
    suspend fun seed() {
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)

        val branchIds = listOf("الفرع الرئيسي", "فرع جدة").map {
            branchDao.upsert(BranchEntity(name = it, createdAt = now, updatedAt = now))
        }
        val categoryIds = listOf("مواد غذائية", "منظفات", "أدوات منزلية").map {
            categoryDao.upsert(CategoryEntity(name = it, createdAt = now, updatedAt = now))
        }
        val unitIds = listOf("قطعة" to "قطعة", "كرتون" to "كرتون", "كيلوجرام" to "كجم").map { (name, symbol) ->
            unitDao.upsert(UnitEntity(name = name, symbol = symbol, createdAt = now, updatedAt = now))
        }
        val customerId = customerDao.upsert(CustomerEntity(name = "عميل نقدي", createdAt = now, updatedAt = now))
        val supplierId = supplierDao.upsert(SupplierEntity(name = "المورد العام للتوريدات", createdAt = now, updatedAt = now))

        data class Seed(val itemNo: String, val barcode: String, val name: String, val min: Double, val reorder: Double, val hasExpiry: Boolean)
        val seeds = listOf(
            Seed("P-0001", "6281000001", "أرز بسمتي 5 كجم", 10.0, 15.0, false),
            Seed("P-0002", "6281000002", "زيت دوار الشمس 1.5 لتر", 20.0, 30.0, false),
            Seed("P-0003", "6281000003", "سكر أبيض 1 كجم", 15.0, 20.0, false),
            Seed("P-0004", "6281000004", "منظف أرضيات 3 لتر", 8.0, 12.0, true),
            Seed("P-0005", "6281000005", "معجون طماطم 400 جم", 25.0, 35.0, true),
            Seed("P-0006", "6281000006", "شاي أحمر 100 كيس", 12.0, 18.0, false),
            Seed("P-0007", "6281000007", "صابون غسيل يدوي", 10.0, 15.0, false),
            Seed("P-0008", "6281000008", "مياه معدنية 1.5 لتر (كرتون)", 30.0, 40.0, true)
        )
        val productIds = seeds.mapIndexed { index, s ->
            productDao.upsert(
                ProductEntity(
                    itemNumber = s.itemNo,
                    barcode = s.barcode,
                    name = s.name,
                    categoryId = categoryIds[index % categoryIds.size],
                    unitId = unitIds[index % unitIds.size],
                    minStock = s.min,
                    reorderPoint = s.reorder,
                    hasExpiry = s.hasExpiry,
                    defaultPrice = 10.0 + index * 3.5,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // Deliberately varied stock so every Inventory tab (available/low/zero/near-expiry/expired) has content.
        val stockPlan = listOf(
            50.0 to null,                 // available
            5.0 to null,                  // low (below reorder, above zero)
            0.0 to null,                  // zero
            18.0 to (now + 20 * day),     // near-expiry (within default 30-day window)
            40.0 to (now - 5 * day),      // expired
            22.0 to null,                 // available
            0.0 to null,                  // zero
            60.0 to (now + 400 * day)     // available, expiry far out
        )
        productIds.forEachIndexed { index, productId ->
            val (qty, expiry) = stockPlan[index % stockPlan.size]
            inventoryDao.upsert(
                InventoryEntity(productId = productId, branchId = branchIds.first(), quantity = qty, expiryDate = expiry, createdAt = now, updatedAt = now)
            )
        }

        // One completed count, one purchase request, one sales invoice, one goal — enough for
        // every "recent" list on the dashboard and every detail screen to have something to open.
        val countId = countingDao.upsertCount(
            InventoryCountEntity(branchId = branchIds.first(), countDate = now - 2 * day, status = "COMPLETED", notes = "جرد دوري", createdAt = now, updatedAt = now)
        )
        countingDao.insertItems(
            listOf(
                InventoryCountItemEntity(countId = countId, productId = productIds[0], systemQuantity = 50.0, actualQuantity = 48.0, createdAt = now, updatedAt = now),
                InventoryCountItemEntity(countId = countId, productId = productIds[1], systemQuantity = 5.0, actualQuantity = 5.0, createdAt = now, updatedAt = now)
            )
        )

        val requestId = purchaseDao.upsertRequest(
            PurchaseRequestEntity(requestNumber = "PR-0001", supplierId = supplierId, branchId = branchIds.first(), requestDate = now - day, status = "SUBMITTED", createdAt = now, updatedAt = now)
        )
        purchaseDao.insertItems(
            listOf(
                PurchaseRequestItemEntity(purchaseRequestId = requestId, productId = productIds[2], currentStockSnapshot = 0.0, requestedQuantity = 100.0, createdAt = now, updatedAt = now)
            )
        )

        val invoiceId = salesDao.upsertInvoice(
            SalesInvoiceEntity(invoiceNumber = "INV-0001", invoiceDate = now, customerId = customerId, branchId = branchIds.first(), status = "CONFIRMED", createdAt = now, updatedAt = now)
        )
        salesDao.insertItems(
            listOf(
                SalesInvoiceItemEntity(
                    salesInvoiceId = invoiceId, productId = productIds[0],
                    itemNumberSnapshot = seeds[0].itemNo, itemNameSnapshot = seeds[0].name, unitSnapshot = "قطعة",
                    quantity = 3.0, unitPrice = 24.5, total = 3.0 * 24.5, createdAt = now, updatedAt = now
                ),
                SalesInvoiceItemEntity(
                    salesInvoiceId = invoiceId, productId = productIds[5],
                    itemNumberSnapshot = seeds[5].itemNo, itemNameSnapshot = seeds[5].name, unitSnapshot = "كجم",
                    quantity = 2.0, unitPrice = 14.0, discountPercent = 5.0, total = 2.0 * 14.0 * 0.95, createdAt = now, updatedAt = now
                )
            )
        )

        val goalId = goalDao.upsertGoal(
            GoalEntity(productId = productIds[0], periodStart = now - 15 * day, periodEnd = now + 15 * day, notes = "هدف الشهر الحالي", createdAt = now, updatedAt = now)
        )
        goalDao.insertCommissions(
            listOf(
                CommissionEntity(goalId = goalId, groupOrder = 1, targetQuantity = 50.0, commissionType = "FIXED", commissionValue = 20.0, createdAt = now, updatedAt = now),
                CommissionEntity(goalId = goalId, groupOrder = 2, targetQuantity = 100.0, commissionType = "FIXED", commissionValue = 50.0, createdAt = now, updatedAt = now),
                CommissionEntity(goalId = goalId, groupOrder = 3, targetQuantity = 150.0, commissionType = "PERCENTAGE", commissionValue = 2.0, createdAt = now, updatedAt = now)
            )
        )
    }
}
