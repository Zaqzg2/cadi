package com.inventorysmartai.app.domain.importing

/**
 * Step 4 (HeaderDetection): finds which row in a freshly parsed [RawTable] is the real header
 * row, since real files routinely have leading title/blank rows before it (spec section 21's
 * real acceptance-test files, and section 6's "merged cells"/"repeated header rows" cases).
 *
 * Deliberately a plain heuristic scorer, not a hard-coded "row 0 is always the header" or
 * "row 1 if row 0 is blank" special case — the spec explicitly forbids hard-coded, file-specific
 * parsing logic.
 */
object HeaderRowDetector {
    private const val MAX_ROWS_TO_SCAN = 15

    data class Result(
        val headerRowIndex: Int,
        val headers: List<String>,
        /** Every row after the header, with any row that is byte-for-byte identical to the
         *  chosen header row already dropped (a "repeated header row", e.g. several same-shaped
         *  ranges pasted one after another into one sheet). */
        val dataRows: List<RawRow>
    )

    fun detect(rows: List<RawRow>): Result? {
        if (rows.isEmpty()) return null
        val scanLimit = minOf(rows.size, MAX_ROWS_TO_SCAN)

        var bestIndex = -1
        var bestScore = 0.0 // a genuine header must score > 0; an all-blank/all-numeric sheet has none
        for (i in 0 until scanLimit) {
            val score = scoreAsHeader(rows[i])
            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        if (bestIndex < 0) return null

        val header = rows[bestIndex]
        val dataRows = rows.drop(bestIndex + 1).filterNot { rowsEqual(it, header) }
        return Result(headerRowIndex = bestIndex, headers = header, dataRows = dataRows)
    }

    /** Higher = more header-like. A header row is mostly non-blank, mostly recognizable against
     *  the [ImportField] dictionary, and mostly NOT numbers (numbers are data, not headers).
     *  The absolute [recognized] count (not just its ratio) is weighted directly too — a lone
     *  title cell that happens to contain one dictionary word (e.g. a sheet title like "تقرير
     *  الجرد - الفرع الرئيسي" containing "الفرع") has a ratio of 1.0 over its own width of 1,
     *  identical to a genuine multi-column header's ratio of 1.0 over its width of 3+; scoring
     *  the absolute count as well is what correctly favors the wider, genuine header. */
    private fun scoreAsHeader(row: RawRow): Double {
        if (row.isEmpty()) return 0.0
        val nonBlank = row.count { it.isNotBlank() }
        if (nonBlank == 0) return 0.0

        val recognized = row.count { ImportField.suggest(it) != null }
        val numericLooking = row.count { it.isNotBlank() && it.trim().toDoubleOrNull() != null }

        val fillRatio = nonBlank.toDouble() / row.size
        val recognizedRatio = recognized.toDouble() / row.size
        val numericPenalty = numericLooking.toDouble() / row.size

        return (fillRatio * 1.0) + (recognizedRatio * 1.5) + (recognized * 1.5) - (numericPenalty * 2.0)
    }

    private fun rowsEqual(a: RawRow, b: RawRow): Boolean =
        a.size == b.size && a.indices.all { a[it].trim() == b[it].trim() }
}
