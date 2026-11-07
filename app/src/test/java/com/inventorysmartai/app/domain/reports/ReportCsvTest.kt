package com.inventorysmartai.app.domain.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCsvTest {

    @Test
    fun `the file starts with a BOM so Excel reads Arabic correctly`() {
        assertTrue(ReportCsv.build(listOf("الصنف"), emptyList()).startsWith("\uFEFF"))
    }

    @Test
    fun `headers and rows are comma separated with CRLF line ends`() {
        val csv = ReportCsv.build(listOf("الصنف", "الكمية"), listOf(listOf("حليب", 5.0), listOf("رز", 2.5)))
        assertEquals("\uFEFFالصنف,الكمية\r\nحليب,5\r\nرز,2.5\r\n", csv)
    }

    @Test
    fun `numbers have no trailing zero and no scientific notation`() {
        assertEquals("100", ReportCsv.cell(100.0))
        assertEquals("0.00001", ReportCsv.cell(0.00001))
        assertEquals("1234567.5", ReportCsv.cell(1234567.5))
        assertEquals("-3", ReportCsv.cell(-3.0))
    }

    @Test
    fun `integers and nulls are written as they are`() {
        assertEquals("7", ReportCsv.cell(7))
        assertEquals("", ReportCsv.cell(null))
    }

    @Test
    fun `non finite numbers become an empty cell`() {
        assertEquals("", ReportCsv.cell(Double.NaN))
        assertEquals("", ReportCsv.cell(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `a field with a comma, a quote or a line break is quoted and quotes are doubled`() {
        assertEquals("\"أ,ب\"", ReportCsv.cell("أ,ب"))
        assertEquals("\"قال \"\"مرحبا\"\"\"", ReportCsv.cell("قال \"مرحبا\""))
        assertEquals("\"سطر1\nسطر2\"", ReportCsv.cell("سطر1\nسطر2"))
    }

    @Test
    fun `text that would run as a spreadsheet formula is neutralised`() {
        assertEquals("'=SUM(A1:A9)", ReportCsv.cell("=SUM(A1:A9)"))
        assertEquals("'+1", ReportCsv.cell("+1"))
        assertEquals("'-1", ReportCsv.cell("-1"))
        assertEquals("'@cmd", ReportCsv.cell("@cmd"))
    }

    @Test
    fun `a real negative number is not treated as a formula`() {
        assertEquals("-5", ReportCsv.cell(-5.0))
        assertEquals("-5", ReportCsv.cell(-5))
    }

    @Test
    fun `ordinary text is untouched`() {
        assertEquals("حليب كامل الدسم", ReportCsv.cell("حليب كامل الدسم"))
    }
}
