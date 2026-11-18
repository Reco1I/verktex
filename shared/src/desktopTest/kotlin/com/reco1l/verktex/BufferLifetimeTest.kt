package com.reco1l.verktex

import com.reco1l.verktex.Logger
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.Buffer
import com.reco1l.verktex.graphics.GPUContext
import com.reco1l.verktex.graphics.Device
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture
import com.reco1l.verktex.graphics.Window
import com.reco1l.verktex.graphics.VertexLayout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A buffer used by a recorded draw must keep its data until the frame is executed, after which it
 * can be rewritten. See the lifetime in [Buffer].
 */
class BufferLifetimeTest {

    private class FakeBuffer : Buffer() {
        override fun uploadDataToGPU() = Unit
        override fun onBind() = Unit
        override fun onLoad() = Unit
        override fun onUnload() = Unit
    }

    private class FakeShader : Shader() {
        override val source = Source("", "")
        override fun applyUniforms(uniformSet: UniformSet) = Unit
        override fun onBind() = Unit
        override fun onLoad() = Unit
        override fun onUnload() = Unit
    }

    private object FakeDevice : Device {
        override val capabilities = Device.Capabilities(maxTextureSize = 4096f)
        override fun createBuffer(): Buffer = FakeBuffer()
        override fun createShader(source: Shader.Source): Shader = FakeShader()
        override fun createTexture(width: Float, height: Float): Texture = throw UnsupportedOperationException()
        override fun createExternalTexture(width: Float, height: Float): Texture = throw UnsupportedOperationException()
    }

    private class RecordingRenderer : Renderer() {
        override val device = FakeDevice
        override val context: GPUContext get() = throw UnsupportedOperationException()
        override val window: Window get() = throw UnsupportedOperationException()
        override var version = "fake"

        // What each executed command's buffer held when it was executed.
        val executed = mutableListOf<IntArray>()

        override fun drawCommand(command: DrawCommand) {
            val buffer = command.instanceBuffer ?: command.buffer ?: return
            executed += buffer.words.copyOf(buffer.wordCount)
        }
    }

    private val warnings = mutableListOf<String>()

    @Before
    fun setUp() {
        Logger.implementation = object : Logger.Implementation {
            override fun i(tag: String, message: String) = Unit
            override fun w(tag: String, message: String) {
                warnings += message
            }
            override fun e(tag: String, message: String, throwable: Throwable?) = Unit
        }
    }

    private val shader = FakeShader()

    private fun RecordingRenderer.drawParticles(vertices: Buffer, instances: Buffer, value: Int) {
        instances.clear()
        instances.putPackedBytes(value)

        drawInstanced(
            shader = shader,
            texture = null,
            vertexBuffer = vertices,
            vertexLayout = VertexLayout.Default,
            vertexCount = 1,
            instanceBuffer = instances,
            instanceLayout = VertexLayout.Default,
            instanceCount = 1,
            uniformSet = Shader.UniformSet()
        )
    }

    private fun quad() = FakeBuffer().apply { putVertex(0f, 0f, 0f, 0f, 0) }

    @Test
    fun rewritingABufferInTheNextFrameIsAllowed() {
        val renderer = RecordingRenderer()
        val vertices = quad()
        val instances = FakeBuffer()

        renderer.begin()
        renderer.drawParticles(vertices, instances, 1)
        renderer.end()

        renderer.begin()
        renderer.drawParticles(vertices, instances, 2)
        renderer.end()

        assertEquals(emptyList<String>(), warnings)
        assertArrayEquals(intArrayOf(1), renderer.executed[0])
        assertArrayEquals(intArrayOf(2), renderer.executed[1])
    }

    @Test
    fun rewritingABufferUsedByThisFrameWarns() {
        val renderer = RecordingRenderer()
        val vertices = quad()
        val instances = FakeBuffer()

        renderer.begin()
        renderer.drawParticles(vertices, instances, 1)
        renderer.drawParticles(vertices, instances, 2)
        renderer.end()

        assertEquals(1, warnings.size)
    }

    @Test
    fun buffersOfADroppedFrameCanBeRewritten() {
        val renderer = RecordingRenderer()
        val vertices = quad()
        val instances = FakeBuffer()

        // Recorded but never executed, the next frame starts over.
        renderer.begin()
        renderer.drawParticles(vertices, instances, 1)

        renderer.begin()
        renderer.drawParticles(vertices, instances, 2)
        renderer.end()

        assertEquals(emptyList<String>(), warnings)
        assertArrayEquals(intArrayOf(2), renderer.executed.single())
    }

    @Test
    fun pooledBuffersAreNotSharedWithinAFrame() {
        val renderer = RecordingRenderer()
        val first = FakeShader()
        val second = FakeShader()

        renderer.begin()
        renderer.setState(shader = first)
        renderer.drawGeometry { _, _ -> putPackedBytes(1) }
        renderer.setState(shader = second)
        renderer.drawGeometry { _, _ -> putPackedBytes(2) }
        renderer.end()

        assertEquals(emptyList<String>(), warnings)
        assertArrayEquals(intArrayOf(1), renderer.executed[0])
        assertArrayEquals(intArrayOf(2), renderer.executed[1])
    }
}
