package com.reco1l.verktex.graphics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelsSourceTest {

    private fun written(source: PixelsSource): ByteArray {
        var bytes = ByteArray(0)
        source.writePixels({ _, _, _, _, pixels -> bytes = ByteArray(pixels.remaining()).also { pixels.duplicate().get(it) } }, 0, 0)
        return bytes
    }

    @Test
    fun `Test the pixels are written as given`() {
        val pixels = ByteArray(2 * 1 * 4) { it.toByte() }
        val source = PixelsSource(2, 1, pixels)

        assertEquals(2f, source.width, 0f)
        assertEquals(1f, source.height, 0f)
        assertTrue(pixels.contentEquals(written(source)))
    }

    @Test
    fun `Test a soft dot is opaque in the centre and clear in the corners`() {
        val size = 9
        val bytes = written(PixelsSource.softDot(size))
        fun alpha(x: Int, y: Int) = bytes[(y * size + x) * 4 + 3].toInt() and 0xFF

        assertEquals(255, alpha(4, 4))
        assertEquals(0, alpha(0, 0))
        assertTrue(alpha(4, 2) in 1 until 255)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Test the size must match the pixels`() {
        PixelsSource(2, 2, ByteArray(4))
    }
}
