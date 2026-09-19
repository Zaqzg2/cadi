package com.inventorysmartai.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeaderRowDetectorTest {

    @Test
    fun `finds the header row immediately when it is row zero`() {
        val rows = listOf(
            listOf("اسم الصنف", "الباركود", "الكمية"),
            listOf("أرز بسمتي", "123456", "10")
        )
        val result = HeaderRowDetector.detect(rows)
        assertEquals(0, result?.headerRowIndex)
        assertEquals(listOf("اسم الصنف", "الباركود", "الكمية"), result?.headers)
        assertEquals(1, result?.dataRows?.size)
    }

    @Test
    fun `skips leading title and blank rows to find the real header`() {
        val rows = listOf(
            listOf("تقرير الجرد - الفرع الرئيسي"),
            listOf("", "", ""),
            listOf("اسم الصنف", "الباركود", "الكمية"),
            listOf("أرز بسمتي", "123456", "10"),
            listOf("حليب", "654321", "20")
        )
        val result = HeaderRowDetector.detect(rows)
        assertEquals(2, result?.headerRowIndex)
        assertEquals(2, result?.dataRows?.size)
    }

    @Test
    fun `drops a repeated header row appearing again later in the sheet`() {
        val rows = listOf(
            listOf("اسم الصنف", "الباركود", "الكمية"),
            listOf("أرز بسمتي", "123456", "10"),
            listOf("اسم الصنف", "الباركود", "الكمية"), // pasted a second range in below
            listOf("حليب", "654321", "20")
        )
        val result = HeaderRowDetector.detect(rows)
        assertEquals(0, result?.headerRowIndex)
        // The repeated header row must be excluded from dataRows, leaving only the two real rows.
        assertEquals(2, result?.dataRows?.size)
        assertEquals("أرز بسمتي", result?.dataRows?.get(0)?.get(0))
        assertEquals("حليب", result?.dataRows?.get(1)?.get(0))
    }

    @Test
    fun `a purely numeric first row is not mistaken for a header`() {
        val rows = listOf(
            listOf("100", "200", "300"),
            listOf("اسم الصنف", "الباركود", "الكمية"),
            listOf("أرز بسمتي", "123456", "10")
        )
        val result = HeaderRowDetector.detect(rows)
        assertEquals(1, result?.headerRowIndex)
    }

    @Test
    fun `an entirely empty table has no header`() {
        assertNull(HeaderRowDetector.detect(emptyList()))
    }

    @Test
    fun `a table where every row is blank has no header either`() {
        val rows = listOf(listOf("", "", ""), listOf("", "", ""))
        assertNull(HeaderRowDetector.detect(rows))
    }
}
