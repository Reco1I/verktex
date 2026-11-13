package com.reco1l.verktex

import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.graphics.PixelBuffer
import com.reco1l.verktex.texture.FramesReference
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.UIAnimatedSprite
import com.reco1l.verktex.texture.reference
import com.reco1l.verktex.texture.storedTexture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A stored texture reference follows the region [Textures] holds under its key, and an animated
 * sprite follows the frames its reference looks up.
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

    private class FixedClock : FrameClock() {
        override val time = 0f
        override val isRunning = true
        override val elapsedFrameTime = 0.25f
        override val lastFrameTime = 0f
    }

    // Frames stored as "<key>-0", "<key>-1"... looked up until one is missing.
    private inner class StoredFrames : FramesReference.Lookup() {
        var lookups = 0

        override fun lookup() = generateSequence(0) { it + 1 }
            .map { Textures["$key-$it"] }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
            .also { lookups++ }
    }

    private val key = "texture-reference-test"

    @After
    fun unload() {
        Textures.unloadAll { key, _ -> key.startsWith(this.key) }
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

    @Test
    fun lookupIsCachedUntilTheStoreChanges() {
        val atlas = Atlas()
        val reference = StoredFrames()

        Textures.load("$key-0", SolidSource(8), destinationAtlas = atlas)
        val first = reference.resolve()
        assertEquals(1, first.size)
        assertSame(first, reference.resolve())
        assertEquals(1, reference.lookups)

        Textures.load("$key-1", SolidSource(8), destinationAtlas = atlas)
        assertEquals(2, reference.resolve().size)
        assertEquals(2, reference.lookups)
    }

    @Test
    fun animatedSpriteFollowsItsFrames() {
        val atlas = Atlas()
        val clock = FixedClock()
        val sprite = UIAnimatedSprite(StoredFrames(), 0f)

        assertNull(sprite.textureRegion)

        val first = Textures.load("$key-0", SolidSource(8), destinationAtlas = atlas)
        sprite.update(clock)
        assertSame(first, sprite.textureRegion)
        assertEquals(1f, sprite.duration)

        val second = Textures.load("$key-1", SolidSource(8), destinationAtlas = atlas)
        // A second frame halves the frame time, so the animation still takes one second.
        sprite.update(clock)
        sprite.update(clock)
        assertEquals(0.5f, sprite.frameTime)
        assertSame(second, sprite.textureRegion)

        // Its current frame going away, the sprite falls back to the last one left.
        Textures.unload("$key-1")
        assertSame(first, sprite.textureRegion)
    }
}
