package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.local.database.entity.ProductEntity
import com.inventorysmartai.app.domain.importing.MatchResult
import com.inventorysmartai.app.domain.importing.ParsedImportRow
import com.inventorysmartai.app.fakes.FakeProductDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DeterministicProductMatcherTest {

    private fun product(id: Long, itemNumber: String?, barcode: String?, name: String) = ProductEntity(
        id = id, itemNumber = itemNumber, barcode = barcode, name = name,
        categoryId = null, unitId = null, createdAt = 0L, updatedAt = 0L
    )

    private fun row(itemNumber: String? = null, barcode: String? = null, name: String? = null) = ParsedImportRow(
        importJobId = 1L, rowIndex = 0, itemNumber = itemNumber, barcode = barcode, name = name, quantity = 1.0, rawJson = "{}"
    )

    @Test
    fun `exact barcode wins even when other fields would also match a different product`() = runBlocking {
        val dao = FakeProductDao(listOf(
            product(1L, itemNumber = "P-001", barcode = "1111", name = "منتج أ"),
            product(2L, itemNumber = "P-002", barcode = "2222", name = "منتج ب")
        ))
        val matcher = DeterministicProductMatcher(dao)

        val result = matcher.match(row(barcode = "2222", itemNumber = "P-001"))

        assertEquals(MatchResult.ExactBarcode(2L), result)
    }

    @Test
    fun `falls back to item number when barcode does not match anything`() = runBlocking {
        val dao = FakeProductDao(listOf(product(1L, itemNumber = "P-001", barcode = "1111", name = "منتج أ")))
        val matcher = DeterministicProductMatcher(dao)

        val result = matcher.match(row(barcode = "9999", itemNumber = "P-001"))

        assertEquals(MatchResult.ExactItemNumber(1L), result)
    }

    @Test
    fun `falls back to exact normalized name, matching spelling variants`() = runBlocking {
        val dao = FakeProductDao(listOf(product(1L, itemNumber = null, barcode = null, name = "حليب السعودية")))
        val matcher = DeterministicProductMatcher(dao)

        // Row's raw text uses the ha-ending variant; the spec's own example of "the same product".
        val result = matcher.match(row(name = "حليب السعوديه"))

        assertEquals(MatchResult.ExactName(1L), result)
    }

    @Test
    fun `a name with no deterministic hit is a NewProduct candidate, not Unresolved`() = runBlocking {
        val dao = FakeProductDao(listOf(product(1L, itemNumber = "P-001", barcode = "1111", name = "منتج أ")))
        val matcher = DeterministicProductMatcher(dao)

        val result = matcher.match(row(name = "منتج غير موجود إطلاقًا"))

        assertEquals(MatchResult.NewProduct, result)
    }

    @Test
    fun `a row with nothing usable is Unresolved`() = runBlocking {
        val matcher = DeterministicProductMatcher(FakeProductDao())

        val result = matcher.match(row())

        assertEquals(MatchResult.Unresolved, result)
    }

    @Test
    fun `never returns a fuzzy suggestion`() = runBlocking {
        val dao = FakeProductDao(listOf(product(1L, itemNumber = null, barcode = null, name = "حليب سعودي كامل الدسم")))
        val matcher = DeterministicProductMatcher(dao)

        // Close but not an exact normalized match — must not be force-matched.
        val result = matcher.match(row(name = "حليب سعودي كامل الد"))

        assert(result !is MatchResult.FuzzySuggestion)
        assertEquals(MatchResult.NewProduct, result)
    }
}
