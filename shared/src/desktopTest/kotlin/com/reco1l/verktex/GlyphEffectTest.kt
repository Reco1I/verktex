package com.reco1l.verktex

import com.reco1l.verktex.fonts.GlyphEffect
import com.reco1l.verktex.fonts.dilate
import com.reco1l.verktex.fonts.gaussianBoxes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlyphEffectTest {

    // A single covered pixel at the center of a square of [size].
    private fun dot(size: Int) = FloatArray(size * size).also { it[(size / 2) * size + size / 2] = 1f }

    @Test
    fun `dilation grows a pixel into a disk of the radius`() {
        val size = 11
        val result = dilate(dot(size), size, size, 3)
        val center = size / 2

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - center
                val dy = y - center
                val inside = dx * dx + dy * dy <= 9
                assertEquals("($x, $y)", if (inside) 1f else 0f, result[y * size + x])
            }
        }
    }

    @Test
    fun `a fractional dilation is between the whole ones`() {
        val size = 11
        val effect = GlyphEffect.dilate(1.5f)
        val result = effect.apply(dot(size), size, size)
        val center = size / 2

        assertEquals(1f, result[center * size + center + 1])
        assertEquals(0.5f, result[center * size + center + 2], 1e-6f)
        assertEquals(0f, result[center * size + center + 3])
    }

    @Test
    fun `the boxes of a blur add up to the variance of the Gaussian`() {
        for (sigma in listOf(1f, 2.5f, 5f, 11f)) {
            // A box of radius r has a variance of ((2r + 1)^2 - 1) / 12.
            val variance = gaussianBoxes(sigma).sumOf { r -> ((2 * r + 1) * (2 * r + 1) - 1) / 12.0 }
            assertTrue("$sigma: $variance", abs(variance - sigma * sigma) < sigma * sigma * 0.15 + 0.5)
        }
    }

    @Test
    fun `a blur keeps the coverage and stays within its extent`() {
        val effect = GlyphEffect.blur(8f)
        val size = 2 * effect.extent + 1
        val result = effect.apply(dot(size), size, size)

        assertEquals(1f, result.sum(), 1e-3f)
        // Nothing reaches the edge, where the padding ends.
        for (i in 0 until size) {
            assertEquals(0f, result[i])
            assertEquals(0f, result[i * size])
        }
    }

    @Test
    fun `close radii share an effect`() {
        assertEquals(GlyphEffect.blur(10.1f, 2.01f), GlyphEffect.blur(9.9f, 1.99f))
        assertEquals(GlyphEffect.dilate(2.01f), GlyphEffect.dilate(1.99f))
    }
}
