package com.reco1l.verktex

import com.reco1l.verktex.data.px
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.fonts.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A font weight is the value of the OpenType `wght` axis, 1 to 1000, with the usual names as constants.
 */
class FontWeightTest {

    @Test
    fun `named weights have their wght values`() {
        val named = listOf(
            Font.Weight.Thin, Font.Weight.ExtraLight, Font.Weight.Light, Font.Weight.Normal, Font.Weight.Medium,
            Font.Weight.SemiBold, Font.Weight.Bold, Font.Weight.ExtraBold, Font.Weight.Black,
        )
        assertEquals((100..900 step 100).toList(), named.map { it.value })
    }

    @Test
    fun `weights are clamped to 1 to 1000`() {
        assertEquals(1, Font.Weight(0).value)
        assertEquals(1, Font.Weight(-50).value)
        assertEquals(1000, Font.Weight(2000).value)
        assertEquals(550, Font.Weight(550).value)
    }

    @Test
    fun `weights are ordered by value`() {
        assertTrue(Font.Weight(550) > Font.Weight.Medium)
        assertTrue(Font.Weight(550) < Font.Weight.SemiBold)
        assertTrue(Font.Weight.Bold >= Font.Weight(700))
        assertEquals(Font.Weight.Bold, Font.Weight(700))
    }

    @Test
    fun `descriptors of the same weight are the same font`() {
        val family = FontFamily("fake", object : FontFamily.Source {
            override fun onCreateFont(descriptor: Font.Descriptor): Font = throw UnsupportedOperationException()
        })
        assertEquals(
            Font.Descriptor(family, 10f.px, Font.Weight(550)),
            Font.Descriptor(family, 10f.px, Font.Weight(550)),
        )
        assertEquals(
            Font.Descriptor(family, 10f.px).hashCode(),
            Font.Descriptor(family, 10f.px, Font.Weight.Normal).hashCode(),
        )
    }
}
