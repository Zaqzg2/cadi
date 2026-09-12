package com.inventorysmartai.app.data.local.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.inventorysmartai.app.data.local.database.entity.CommissionEntity
import com.inventorysmartai.app.data.local.database.entity.GoalEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountEntity
import com.inventorysmartai.app.data.local.database.entity.InventoryCountItemEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestEntity
import com.inventorysmartai.app.data.local.database.entity.PurchaseRequestItemEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceEntity
import com.inventorysmartai.app.data.local.database.entity.SalesInvoiceItemEntity

data class PurchaseRequestWithItems(
    @Embedded val request: PurchaseRequestEntity,
    @Relation(parentColumn = "id", entityColumn = "purchaseRequestId")
    val items: List<PurchaseRequestItemEntity>
)

data class SalesInvoiceWithItems(
    @Embedded val invoice: SalesInvoiceEntity,
    @Relation(parentColumn = "id", entityColumn = "salesInvoiceId")
    val items: List<SalesInvoiceItemEntity>
)

data class GoalWithCommissions(
    @Embedded val goal: GoalEntity,
    @Relation(parentColumn = "id", entityColumn = "goalId")
    val commissions: List<CommissionEntity>
)

data class InventoryCountWithItems(
    @Embedded val count: InventoryCountEntity,
    @Relation(parentColumn = "id", entityColumn = "countId")
    val items: List<InventoryCountItemEntity>
)
