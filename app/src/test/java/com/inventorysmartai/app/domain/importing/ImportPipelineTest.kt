package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.data.importing.DeterministicProductMatcher
import com.inventorysmartai.app.data.local.database.entity.ProductEntity
import com.inventorysmartai.app.domain.model.ImportRowStatus
import com.inventorysmartai.app.fakes.FakeProductDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end tests of the pure pipeline (HeaderDetection -> ColumnMapping -> Normalization ->
 * Validation -> ProductMatching -> DuplicateDetection) for each of the five row-oriented import
 * types (spec section 26: "Goal import", "Purchase request import", "Inventory import" —
 * covered here at the pipeline/validation level, since the actual database write
 * (ImportRepositoryImpl.approveJob) needs a real Room database this plain-JUnit test
 * environment does not have — see the project's own README on that constraint).
 */
class ImportPipelineTest {

    private fun pipelineWith(seed: List<ProductEntity> = emptyList()): ImportPipeline = DefaultImportPipeline(
        columnMapper = DefaultColumnMapper(),
        normalizer = DefaultNormalizer(),
        validator = DefaultImportValidator(),
        productMatcher = DeterministicProductMatcher(FakeProductDao(seed)),
        duplicateDetector = DefaultDuplicateDetector()
    )

    private fun table(vararg rows: List<String>) = RawTable(sheetName = null, rows = rows.toList())

    @Test
    fun `PRODUCTS row with no existing match is a new product`() = runBlocking {
        val result = pipelineWith().analyze(
            table(listOf("اسم الصنف", "الباركود"), listOf("أرز بسمتي", "123456")),
            ImportType.PRODUCTS,
            importJobId = 1L
        )
        assertEquals(1, result.analyses.size)
        assertEquals(ImportRowStatus.NEW_PRODUCT, result.analyses.first().resolvedStatus())
    }

    @Test
    fun `PRODUCTS row matching an existing barcode is MATCHED`() = runBlocking {
        val seed = listOf(ProductEntity(id = 1, itemNumber = null, barcode = "123456", name = "أرز بسمتي", categoryId = null, unitId = null, createdAt = 0, updatedAt = 0))
        val result = pipelineWith(seed).analyze(
            table(listOf("اسم الصنف", "الباركود"), listOf("أرز بسمتي فاخر", "123456")),
            ImportType.PRODUCTS,
            importJobId = 1L
        )
        assertEquals(ImportRowStatus.MATCHED, result.analyses.first().resolvedStatus())
        assertEquals(1L, result.analyses.first().matchedProductId)
    }

    @Test
    fun `INVENTORY row without CURRENT_STOCK is an ERROR and never reaches ProductMatcher`() = runBlocking {
        val result = pipelineWith().analyze(
            table(listOf("اسم الصنف", "الباركود"), listOf("أرز بسمتي", "123456")),
            ImportType.INVENTORY,
            importJobId = 1L
        )
        val analysis = result.analyses.first()
        assertEquals(ImportRowStatus.ERROR, analysis.resolvedStatus())
        assertEquals(MatchResult.Unresolved, analysis.matchResult)
    }

    @Test
    fun `INVENTORY row with a valid CURRENT_STOCK is accepted for matching`() = runBlocking {
        val result = pipelineWith().analyze(
            table(listOf("اسم الصنف", "الرصيد الحالي"), listOf("أرز بسمتي", "25")),
            ImportType.INVENTORY,
            importJobId = 1L
        )
        val analysis = result.analyses.first()
        assertTrue(analysis.validation.isValid)
        assertEquals(25.0, analysis.row.numericValue(ImportField.CURRENT_STOCK))
    }

    @Test
    fun `COUNTING requires COUNTED_QUANTITY specifically`() = runBlocking {
        val result = pipelineWith().analyze(
            table(listOf("اسم الصنف", "الكمية المجرودة"), listOf("أرز بسمتي", "18")),
            ImportType.COUNTING,
            importJobId = 1L
        )
        assertTrue(result.analyses.first().validation.isValid)
        assertEquals(18.0, result.analyses.first().row.numericValue(ImportField.COUNTED_QUANTITY))
    }

    @Test
    fun `PURCHASE_REQUESTS recognizes the spec's own worked example header 'المطلوب'`() = runBlocking {
        val result = pipelineWith().analyze(
            table(listOf("اسم الصنف", "المطلوب"), listOf("أرز بسمتي", "30")),
            ImportType.PURCHASE_REQUESTS,
            importJobId = 1L
        )
        assertEquals(ImportField.REQUESTED_QUANTITY, result.columnMapping.fieldFor(1))
        assertTrue(result.analyses.first().validation.isValid)
        assertEquals(30.0, result.analyses.first().row.numericValue(ImportField.REQUESTED_QUANTITY))
    }

    @Test
    fun `GOALS requires TARGET and rejects a row missing it`() = runBlocking {
        val result = pipelineWith().analyze(
            table(listOf("اسم الصنف"), listOf("أرز بسمتي")),
            ImportType.GOALS,
            importJobId = 1L
        )
        assertEquals(ImportRowStatus.ERROR, result.analyses.first().resolvedStatus())
    }

    @Test
    fun `two rows with the same barcode in one file are matched and duplicate respectively`() = runBlocking {
        val result = pipelineWith().analyze(
            table(
                listOf("اسم الصنف", "الباركود"),
                listOf("أرز بسمتي", "123456"),
                listOf("أرز بسمتي مكرر", "123456")
            ),
            ImportType.PRODUCTS,
            importJobId = 1L
        )
        assertEquals(ImportRowStatus.NEW_PRODUCT, result.analyses[0].resolvedStatus())
        assertEquals(ImportRowStatus.DUPLICATE, result.analyses[1].resolvedStatus())
    }

    @Test
    fun `a leading title row before the real header does not break column mapping`() = runBlocking {
        val result = pipelineWith().analyze(
            table(
                listOf("تقرير الأصناف"),
                listOf("اسم الصنف", "الباركود"),
                listOf("أرز بسمتي", "123456")
            ),
            ImportType.PRODUCTS,
            importJobId = 1L
        )
        assertEquals(1, result.headerRowIndex)
        assertEquals(ImportField.PRODUCT_NAME, result.columnMapping.fieldFor(0))
        assertEquals(1, result.analyses.size)
    }

    @Test
    fun `entirely blank rows are skipped and counted, not treated as data`() = runBlocking {
        val result = pipelineWith().analyze(
            table(
                listOf("اسم الصنف", "الباركود"),
                listOf("أرز بسمتي", "123456"),
                listOf("", ""),
                listOf("حليب", "654321")
            ),
            ImportType.PRODUCTS,
            importJobId = 1L
        )
        assertEquals(2, result.analyses.size)
        assertEquals(1, result.skippedBlankRows)
    }

    @Test
    fun `progress callback reports every row processed`() = runBlocking {
        val seen = mutableListOf<Pair<Int, Int>>()
        pipelineWith().analyze(
            table(listOf("اسم الصنف"), listOf("أرز"), listOf("حليب")),
            ImportType.PRODUCTS,
            importJobId = 1L,
            onProgress = { processed, total -> seen += processed to total }
        )
        assertEquals(listOf(1 to 2, 2 to 2), seen)
    }
}
