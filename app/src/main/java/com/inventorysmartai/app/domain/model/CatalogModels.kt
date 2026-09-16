package com.inventorysmartai.app.domain.model

data class Branch(
    val id: Long = 0L,
    val name: String,
    val code: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val isActive: Boolean = true
)

data class Category(
    val id: Long = 0L,
    val name: String,
    val code: String? = null,
    val isActive: Boolean = true
)

/** Named "UnitOfMeasure", not "Unit" — that name is reserved by kotlin.Unit. */
data class UnitOfMeasure(
    val id: Long = 0L,
    val name: String,
    val symbol: String? = null,
    val isActive: Boolean = true
)

data class Customer(
    val id: Long = 0L,
    val customerNumber: String? = null,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val openingBalance: Double = 0.0,
    val isActive: Boolean = true
)

data class Supplier(
    val id: Long = 0L,
    val supplierNumber: String? = null,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true
)
