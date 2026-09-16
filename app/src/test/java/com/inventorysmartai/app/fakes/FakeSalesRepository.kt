package com.inventorysmartai.app.fakes

import com.inventorysmartai.app.domain.model.SalesInvoice
import com.inventorysmartai.app.domain.repository.SalesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSalesRepository(private val soldQuantity: Double = 0.0) : SalesRepository {
    override fun observeInvoices(): Flow<List<SalesInvoice>> = MutableStateFlow(emptyList())
    override fun observeRecentInvoices(limit: Int): Flow<List<SalesInvoice>> = MutableStateFlow(emptyList())
    override fun observeInvoice(invoiceId: Long): Flow<SalesInvoice?> = MutableStateFlow(null)
    override suspend fun saveInvoice(invoice: SalesInvoice): Long = 0L
    override suspend fun getSoldQuantity(productId: Long, fromDate: Long, toDate: Long): Double = soldQuantity
}
