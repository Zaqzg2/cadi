package com.inventorysmartai.app.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

/** الجزء القابل للاختبار من «Enter ينتقل للصف الأسفل»: كم نمرّر ليظهر الصف الهدف دون قفز غير لازم. */
class ScrollRevealTest {

    // viewport: 100..500, rows 52 tall

    @Test
    fun `a row already fully inside needs no scroll`() {
        assertEquals(0, ScrollReveal.deltaToReveal(offset = 200, size = 52, viewportStart = 100, viewportEnd = 500))
        assertEquals(0, ScrollReveal.deltaToReveal(offset = 100, size = 52, viewportStart = 100, viewportEnd = 500)) // touching the top edge
        assertEquals(0, ScrollReveal.deltaToReveal(offset = 448, size = 52, viewportStart = 100, viewportEnd = 500)) // touching the bottom edge
    }

    @Test
    fun `a row cut off at the bottom scrolls forward just enough`() {
        // row spans 470..522, viewport ends at 500 -> 22 px more
        assertEquals(22, ScrollReveal.deltaToReveal(offset = 470, size = 52, viewportStart = 100, viewportEnd = 500))
    }

    @Test
    fun `a row completely below scrolls forward until its bottom meets the edge`() {
        assertEquals(152, ScrollReveal.deltaToReveal(offset = 600, size = 52, viewportStart = 100, viewportEnd = 500))
    }

    @Test
    fun `a row cut off at the top scrolls back just enough`() {
        // row starts at 80, viewport starts at 100 -> 20 px back
        assertEquals(-20, ScrollReveal.deltaToReveal(offset = 80, size = 52, viewportStart = 100, viewportEnd = 500))
    }

    @Test
    fun `a row taller than the viewport aligns its top instead of clipping it`() {
        // 600 tall in a 400 viewport: scrolling to the bottom would hide its top, so only the top is aligned
        assertEquals(50, ScrollReveal.deltaToReveal(offset = 150, size = 600, viewportStart = 100, viewportEnd = 500))
    }
}
