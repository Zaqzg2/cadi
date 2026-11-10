package com.inventorysmartai.app.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun `contrast of black on white is the maximum 21`() {
        assertEquals(21.0, ColorMath.contrast(black, white), 0.01)
    }

    @Test
    fun `contrast of a color with itself is 1`() {
        assertEquals(1.0, ColorMath.contrast(0xFF336699.toInt(), 0xFF336699.toInt()), 0.0001)
    }

    @Test
    fun `best text on a dark background is light and on a light background is dark`() {
        assertEquals(ColorMath.NEAR_WHITE, ColorMath.bestTextFor(0xFF0B1730.toInt()))
        assertEquals(ColorMath.NEAR_BLACK, ColorMath.bestTextFor(0xFFFBF5E6.toInt()))
    }

    @Test
    fun `best text considers every surface and keeps both readable`() {
        val darkBackground = 0xFF181A1F.toInt()
        val lightCard = 0xFFFFF6DA.toInt()
        val text = ColorMath.bestTextFor(darkBackground, lightCard)
        assertTrue(ColorMath.contrast(text, darkBackground) >= 3.0 || ColorMath.contrast(text, lightCard) >= 3.0)
    }

    @Test
    fun `blend endpoints return the endpoint colors fully opaque`() {
        assertEquals(black, ColorMath.blend(black, white, 0f))
        assertEquals(white, ColorMath.blend(black, white, 1f))
        assertEquals(0xFF808080.toInt(), ColorMath.blend(black, white, 0.5f))
    }

    @Test
    fun `isDark separates dark and light backgrounds`() {
        assertTrue(ColorMath.isDark(black))
        assertTrue(ColorMath.isDark(0xFF0B1730.toInt()))
        assertTrue(!ColorMath.isDark(white))
        assertTrue(!ColorMath.isDark(0xFFE6EFFC.toInt()))
    }

    @Test
    fun `card derived from a dark background is lighter and from a light one is lighter still`() {
        val dark = 0xFF181A1F.toInt()
        val light = 0xFFE6EFFC.toInt()
        assertTrue(ColorMath.luminance(ColorMath.cardFor(dark)) > ColorMath.luminance(dark))
        assertTrue(ColorMath.luminance(ColorMath.cardFor(light)) > ColorMath.luminance(light))
    }

    @Test
    fun `hex round trips`() {
        assertEquals("#1F5EFF", ColorMath.toHex(0xFF1F5EFF.toInt()))
        assertEquals(0xFF1F5EFF.toInt(), ColorMath.parseHex("#1f5eff"))
        assertEquals(0xFF1F5EFF.toInt(), ColorMath.parseHex("1F5EFF"))
        assertEquals(0xFFAABBCC.toInt(), ColorMath.parseHex("#abc"))
    }

    @Test
    fun `invalid hex is rejected`() {
        assertNull(ColorMath.parseHex(""))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GG0000"))
        assertNull(ColorMath.parseHex("#1234567"))
    }
}
