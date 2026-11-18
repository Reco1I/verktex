package com.reco1l.verktex

import com.reco1l.verktex.android.gles.ESExternalTexture
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.Buffer
import com.reco1l.verktex.graphics.GPUContext
import com.reco1l.verktex.graphics.Device
import com.reco1l.verktex.graphics.GPUResource
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture
import com.reco1l.verktex.graphics.Window
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The CPU side of external (video) textures: the mat4 uniform and the frame latch. The GL and
 * SurfaceTexture calls are replaced through the texture's seams.
 */
class ExternalTextureTest {

    private class FakeExternalTexture : ESExternalTexture(320f, 180f) {
        var updates = 0
        var contextLosses = 0

        override fun onContextLost() {
            contextLosses++
            super.onContextLost()
        }

        override fun onCreateTexture() = Unit
        override fun bindExternalTexture() = Unit
        override fun onUnload() = Unit

        override fun updateTexImage() {
            updates++
        }
    }

    @Test
    fun mat4UniformIsAcceptedByTheUniformSet() {
        val matrix = FloatArray(16) { it.toFloat() }
        val uniforms = Shader.UniformSet().apply { set("uTransform", matrix) }

        assertSame(matrix, uniforms.entries["uTransform"])
        assertEquals(16, (uniforms.entries["uTransform"] as FloatArray).size)
    }

    @Test
    fun externalTextureStartsWithIdentityTransform() {
        val texture = FakeExternalTexture()

        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        assertArrayEquals(identity, texture.transform, 0f)
        assertTrue(texture.isExternal)

        // One region covering the whole texture, so a sprite can draw it.
        val region = texture.region
        assertSame(texture, region.texture)
        assertEquals(0f, region.u0, 0f)
        assertEquals(0f, region.v0, 0f)
        assertEquals(1f, region.u1, 0f)
        assertEquals(1f, region.v1, 0f)
    }

    @Test
    fun markFrameAvailableIsLatchedOnce() {
        val texture = FakeExternalTexture()

        texture.bind()
        assertEquals(0, texture.updates)

        texture.markFrameAvailable()
        texture.markFrameAvailable()
        texture.bind()
        assertEquals(1, texture.updates)

        // The flag was cleared by the first bind.
        texture.bind()
        assertEquals(1, texture.updates)

        texture.markFrameAvailable()
        texture.bind()
        assertEquals(2, texture.updates)
    }

    @Test
    fun externalTextureIsRegisteredWithTheRenderer() {
        val device = object : Device {
            override val capabilities = Device.Capabilities(maxTextureSize = 4096f)
            override fun createBuffer(): Buffer = throw UnsupportedOperationException()
            override fun createShader(source: Shader.Source): Shader = throw UnsupportedOperationException()
            override fun createTexture(width: Float, height: Float): Texture = throw UnsupportedOperationException()
            override fun createExternalTexture(width: Float, height: Float): Texture = FakeExternalTexture()
        }
        val renderer = object : Renderer() {
            override val device = device
            override val context: GPUContext get() = throw UnsupportedOperationException()
            override val window: Window get() = throw UnsupportedOperationException()
            override var version = "fake"
            override fun drawCommand(command: DrawCommand) = Unit
        }

        val texture = renderer.createExternalTexture(320f, 180f) as FakeExternalTexture
        texture.load()

        renderer.markContextLoss()

        assertEquals(1, texture.contextLosses)
        assertEquals(GPUResource.State.Unloaded, texture.state)
    }

    @Test
    fun externalTextureRejectsSources() {
        val texture = FakeExternalTexture()
        val source = object : Texture.Source {
            override val width = 1f
            override val height = 1f
            override fun writePixels(target: Texture.PixelTarget, x: Int, y: Int) = Unit
        }

        assertFalse(texture.fitsSource(source))
        assertNull(texture.addSource(source))
    }

    @Test
    fun verticalFlipFoldMatchesMatrixMultiplication() {
        val random = java.util.Random(7)
        val matrix = FloatArray(16) { random.nextFloat() * 4f - 2f }

        // flip maps v to 1 - v, column-major: column 1 is (0, -1, 0, 0) and column 3 is (0, 1, 0, 1).
        val flip = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f
        )
        val expected = FloatArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += matrix[k * 4 + row] * flip[column * 4 + k]
                }
                expected[column * 4 + row] = sum
            }
        }

        val folded = matrix.copyOf()
        ESExternalTexture.foldVerticalFlip(folded)

        assertArrayEquals(expected, folded, 1e-6f)
    }
}
