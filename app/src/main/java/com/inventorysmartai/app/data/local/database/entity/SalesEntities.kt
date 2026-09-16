package com.inventorysmartai.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [invoiceTotal]/[discountTotal]/[finalBalance] are stored (not recomputed on the fly) because
 * a completed invoice must stay correct even if line items or product prices change later —
 * this mirrors the same "snapshot, don't recompute from mutable state" reasoning as the item
 * snapshots below. [previousBalance]/[finalBalance] are nullable: they only make sense for
 * customers carried on account, not walk-in/cash sales with no [customerId].
 */
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
    val warehouse: String? = null,
    val currency: String = "",
    val previousBalance: Double? = null,
    val invoiceTotal: Double = 0.0,
    val discountTotal: Double = 0.0,
    val finalBalance: Double? = null,
    val notes: String? = null,
    val status: String = "CONFIRMED", // InvoiceStatus.name
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Discount and price live at the line level so each item can differ from the invoice total.
 *
 * [itemNumberSnapshot]/[itemNameSnapshot]/[unitSnapshot] are copied from the Product at the
 * moment the line is added and never re-read from it afterwards — required by the spec so a
 * historical invoice keeps showing exactly what was sold even after the product is renamed,
 * re-coded, or deactivated. [total] is the stored line total (quantity × price, less discount).
 */
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
    val itemNumberSnapshot: String?,
    val itemNameSnapshot: String,
    val unitSnapshot: String?,
    val quantity: Double,
    val unitPrice: Double,
    val discountPercent: Double = 0.0,
    val total: Double,
    val createdAt: Long,
    val updatedAt: Long
)
