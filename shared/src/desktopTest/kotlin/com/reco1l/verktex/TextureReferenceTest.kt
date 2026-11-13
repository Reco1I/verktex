package com.reco1l.verktex

import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.graphics.PixelBuffer
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.texture.reference
import com.reco1l.verktex.texture.storedTexture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A stored texture reference follows the region [Textures] holds under its key.
 */
class TextureReferenceTest {

    private class Atlas : GraphicsTexture() {
        override val width = 64f
        override val height = 64f

        override fun onCreateTexture() = Unit
        override fun onBindTexture() = Unit
        override fun onUnload() = Unit
        override fun onWritePixels(x: Int, y: Int, width: Int, height: Int, pixels: PixelBuffer) = Unit
    }

    private class SolidSource(size: Int) : GraphicsTexture.Source {
        override val width = size.toFloat()
        override val height = size.toFloat()

        override fun writePixels(target: GraphicsTexture.PixelTarget, x: Int, y: Int) = Unit
    }

    private val key = "texture-reference-test"

    @After
    fun unload() {
        if (key in Textures) {
            Textures.unload(key)
        }
    }

    @Test
    fun storedFollowsTheKey() {
        val atlas = Atlas()
        val reference = storedTexture(key)

        assertNull(reference.resolve())

        val first = Textures.load(key, SolidSource(8), destinationAtlas = atlas)
        assertSame(first, reference.resolve())

        val second = Textures.load(key, SolidSource(4), destinationAtlas = atlas)
        assertSame(second, reference.resolve())

        Textures.unload(key)
        assertNull(reference.resolve())
    }

    @Test
    fun referencesCompareByWhatTheyReferTo() {
        val region = Atlas().addSource(SolidSource(8))!!

        assertEquals(storedTexture(key), storedTexture(key))
        assertEquals(region.reference, region.reference)
        assertSame(region, region.reference.resolve())
    }
}
