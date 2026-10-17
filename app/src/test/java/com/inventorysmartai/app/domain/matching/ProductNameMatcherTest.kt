package com.inventorysmartai.app.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductNameMatcherTest {

    private val catalog = listOf(
        MatchCandidate(1, "صابون سائل 500 مل"),
        MatchCandidate(2, "صابون سائل 1 لتر"),
        MatchCandidate(3, "منظف أرضيات", aliases = listOf("أرضيات فلاش")),
        MatchCandidate(4, "مبيض ملابس"),
        MatchCandidate(5, "منظف زجاج"),
        MatchCandidate(6, "عطر جو"),
        MatchCandidate(7, "مستلزمات حمام"),
        MatchCandidate(8, "حليب السعودية", itemNumber = "A100", barcode = "6281001")
    )
    private val matcher = ProductNameMatcher(catalog)

    @Test
    fun `the same name with Arabic-Indic digits is an exact match`() {
        val match = matcher.match("صابون سائل ٥٠٠ مل")
        assertEquals(MatchConfidence.EXACT, match.confidence)
        assertEquals(1L, match.best!!.productId)
        assertFalse(match.needsReview)
    }

    @Test
    fun `alef and ta marbuta variants are still exact`() {
        assertEquals(MatchConfidence.EXACT, matcher.match("منظف ارضيات").confidence)
        val milk = matcher.match("حليب السعوديه")
        assertEquals(MatchConfidence.EXACT, milk.confidence)
        assertEquals(8L, milk.best!!.productId)
    }

    @Test
    fun `a barcode or an item number typed as the name is exact`() {
        assertEquals(8L, matcher.match("6281001").best!!.productId)
        assertEquals(MatchConfidence.EXACT, matcher.match("6281001").confidence)
        // item numbers compare without regard to case
        assertEquals(MatchConfidence.EXACT, matcher.match("a100").confidence)
    }

    @Test
    fun `the same words in another order with a unit spelled differently is high but still needs review`() {
        val match = matcher.match("سائل صابون 500ml")
        assertEquals(MatchConfidence.HIGH, match.confidence)
        assertEquals(1L, match.best!!.productId)
        assertTrue(match.needsReview)
    }

    @Test
    fun `a small spelling slip is still found`() {
        val match = matcher.match("صابون سايل 500 مل")
        assertEquals(MatchConfidence.HIGH, match.confidence)
        assertEquals(1L, match.best!!.productId)
    }

    @Test
    fun `plural and article differences do not matter`() {
        val floor = matcher.match("منظفات أرضيات")
        assertEquals(MatchConfidence.HIGH, floor.confidence)
        assertEquals(3L, floor.best!!.productId)
        assertEquals(6L, matcher.match("عطر الجو").best!!.productId)
        assertEquals(7L, matcher.match("مستلزمات الحمام").best!!.productId)
    }

    @Test
    fun `an adjective form of a word is close enough to suggest`() {
        val match = matcher.match("حليب سعودي")
        assertEquals(MatchConfidence.HIGH, match.confidence)
        assertEquals(8L, match.best!!.productId)
    }

    @Test
    fun `a name that fits two products equally is ambiguous and never picked silently`() {
        val match = matcher.match("صابون سائل")
        assertEquals(MatchConfidence.AMBIGUOUS, match.confidence)
        assertTrue(match.needsReview)
        val offered = (listOf(match.best!!) + match.alternatives).map { it.productId }.toSet()
        assertTrue(offered.containsAll(setOf(1L, 2L)))
    }

    @Test
    fun `a different size is not the same product`() {
        val match = matcher.match("صابون سائل 2 لتر")
        // it resembles the 1-litre soap, but the number differs, so it can never be better than a weak suggestion
        assertEquals(MatchConfidence.LOW, match.confidence)
        assertTrue(match.reasons.any { it.contains("الأرقام") })
    }

    @Test
    fun `a partial name is only a low suggestion`() {
        val match = matcher.match("مبيض")
        assertEquals(MatchConfidence.LOW, match.confidence)
        assertEquals(4L, match.best!!.productId)
    }

    @Test
    fun `an unrelated text matches nothing`() {
        val match = matcher.match("xyz")
        assertEquals(MatchConfidence.NONE, match.confidence)
        assertNull(match.best)
    }

    @Test
    fun `blank input matches nothing`() {
        assertEquals(MatchConfidence.NONE, matcher.match("").confidence)
        assertEquals(MatchConfidence.NONE, matcher.match("   ").confidence)
    }

    @Test
    fun `an alternate name counts, and an exact alternate name is exact`() {
        val exact = matcher.match("ارضيات فلاش")
        assertEquals(MatchConfidence.EXACT, exact.confidence)
        assertEquals(3L, exact.best!!.productId)
        assertNotNull(exact.best.matchedOn)

        val reordered = matcher.match("فلاش ارضيات")
        assertEquals(MatchConfidence.HIGH, reordered.confidence)
        assertEquals(3L, reordered.best!!.productId)
        assertEquals("أرضيات فلاش", reordered.best.matchedOn)
    }

    @Test
    fun `only an exact match skips review`() {
        listOf("صابون سائل ٥٠٠ مل", "سائل صابون 500ml", "صابون سائل", "مبيض", "xyz").forEach { text ->
            val match = matcher.match(text)
            assertEquals(text, match.confidence != MatchConfidence.EXACT, match.needsReview)
        }
    }

    @Test
    fun `two products with the same normalised name are ambiguous, not silently merged`() {
        val twins = ProductNameMatcher(listOf(MatchCandidate(1, "صابون"), MatchCandidate(2, "صابون")))
        assertEquals(MatchConfidence.AMBIGUOUS, twins.match("صابون").confidence)
    }

    @Test
    fun `the closest other names are offered as alternatives`() {
        val match = matcher.match("منظف ارضيات")
        assertTrue(match.alternatives.none { it.productId == 3L })
        assertTrue(match.alternatives.size <= 3)
    }
}
