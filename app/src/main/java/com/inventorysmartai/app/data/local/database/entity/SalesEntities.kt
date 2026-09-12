package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales_invoices",
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("customerId"), Index("branchId"), Index(value = ["invoiceNumber"], unique = true)]
)
data class SalesInvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val invoiceNumber: String,
    val invoiceDate: Long,
    val customerId: Long?,
    val branchId: Long,
    val notes: String? = null,
    val status: String = "CONFIRMED", // InvoiceStatus.name
    val createdAt: Long,
    val updatedAt: Long
)

/** Discount and price live at the line level so each item can differ from the invoice total. */
@Entity(
    tableName = "sales_invoice_items",
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["salesInvoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("salesInvoiceId"), Index("productId")]
)
data class SalesInvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val salesInvoiceId: Long,
    val productId: Long,
    val quantity: Double,
    val unitPrice: Double,
    val discountPercent: Double = 0.0,
    val createdAt: Long,
    val updatedAt: Long
)
