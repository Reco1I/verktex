package com.reco1l.verktex

import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Mat2
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.shape.Box
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.Buffer
import com.reco1l.verktex.graphics.GPUContext
import com.reco1l.verktex.graphics.Device
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture
import com.reco1l.verktex.graphics.Window
import com.reco1l.verktex.graphics.VertexLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A depth clear is recorded as its own command between draws, so the batch is never merged across it.
 */
class RendererCommandsTest {

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

    private class FakeTexture(override val width: Float, override val height: Float) : Texture() {
        override fun onCreateTexture() = Unit
        override fun onBindTexture() = Unit
        override fun onWritePixels(x: Int, y: Int, width: Int, height: Int, pixels: com.reco1l.verktex.graphics.PixelBuffer) = Unit
        override fun onUnload() = Unit
    }

    private class FakeExternalTexture : Texture() {
        override val width = 16f
        override val height = 16f
        override val isExternal get() = true
        override val transform = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
        override fun onCreateTexture() = Unit
        override fun onBindTexture() = Unit
        override fun onWritePixels(x: Int, y: Int, width: Int, height: Int, pixels: com.reco1l.verktex.graphics.PixelBuffer) = Unit
        override fun onUnload() = Unit
    }

    private object FakeDevice : Device {
        override val capabilities = Device.Capabilities(maxTextureSize = 4096f)
        override fun createBuffer(): Buffer = FakeBuffer()
        override fun createShader(source: Shader.Source): Shader = FakeShader()
        override fun createTexture(width: Float, height: Float): Texture = FakeTexture(width, height)
        override fun createExternalTexture(width: Float, height: Float): Texture = FakeTexture(width, height)
    }

    private class RecordingRenderer : Renderer() {
        override val device: Device = FakeDevice
        override val context: GPUContext get() = throw UnsupportedOperationException()
        override val window: Window get() = throw UnsupportedOperationException()
        override var version = "fake"

        val commands = mutableListOf<DrawCommand>()

        override fun drawCommand(command: DrawCommand) {
            commands += command
        }
    }

    private val solidQuad = FakeShader()
    private val video = FakeShader()
    private val ui = FakeShader()
    private var previousSolidQuad: Shader? = null
    private var previousVideo: Shader? = null
    private var previousUI: Shader? = null

    @Before
    fun setUp() {
        // The built-in shaders are lateinit, reading one that was never set throws.
        previousSolidQuad = try { BuiltIn.Shaders.SolidQuad } catch (_: UninitializedPropertyAccessException) { null }
        BuiltIn.Shaders.SolidQuad = solidQuad
        previousVideo = try { BuiltIn.Shaders.Video } catch (_: UninitializedPropertyAccessException) { null }
        BuiltIn.Shaders.Video = video
        previousUI = try { BuiltIn.Shaders.UI } catch (_: UninitializedPropertyAccessException) { null }
        BuiltIn.Shaders.UI = ui
    }

    @After
    fun tearDown() {
        previousSolidQuad?.let { BuiltIn.Shaders.SolidQuad = it }
        previousVideo?.let { BuiltIn.Shaders.Video = it }
        previousUI?.let { BuiltIn.Shaders.UI = it }
    }

    @Test
    fun clearDepthSplitsTheBatchAndKeepsOrder() {
        val renderer = RecordingRenderer()

        renderer.begin()
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.clearDepth()
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.end()

        val commands = renderer.commands
        assertEquals(3, commands.size)

        assertFalse(commands[0].clearDepth)
        assertTrue(commands[1].clearDepth)
        assertNull(commands[1].buffer)
        assertNull(commands[1].shader)
        assertFalse(commands[2].clearDepth)

        // The draw after the clear was not merged back into the first command.
        assertNotSame(commands[0].buffer, commands[2].buffer)
        // A rect is one quad of the UI layout.
        assertEquals(4 * Renderer.UI_VERTEX_STRIDE, commands[0].buffer!!.position)
        assertEquals(4 * Renderer.UI_VERTEX_STRIDE, commands[2].buffer!!.position)
    }

    @Test
    fun drawsWithTheSameStateMergeWithoutAClear() {
        val renderer = RecordingRenderer()

        renderer.begin()
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.end()

        assertEquals(1, renderer.commands.size)
    }

    @Test
    fun clearDepthWithNoDrawsStillRecords() {
        val renderer = RecordingRenderer()

        renderer.begin()
        renderer.clearDepth()
        renderer.end()

        assertEquals(1, renderer.commands.size)
        assertTrue(renderer.commands[0].clearDepth)
        assertNull(renderer.commands[0].buffer)
    }

    @Test
    fun drawTrianglesEmitsTheGivenVertexCount() {
        val renderer = RecordingRenderer()
        val vertices = floatArrayOf(0f, 0f, 10f, 0f, 0f, 10f)

        renderer.begin()
        renderer.pushMatrix(Mat2(a = 2f, d = 2f, tx = 5f, ty = 7f))
        renderer.setState(shader = solidQuad)
        renderer.drawTriangles(vertices, 3, Color4.White)
        renderer.popMatrix()
        renderer.end()

        assertEquals(1, renderer.commands.size)

        val buffer = renderer.commands[0].buffer!!
        assertEquals(3 * Renderer.VERTEX_2D_STRIDE, buffer.position)

        // Every vertex writes x, y, u and v as floats and a packed color, the pushed matrix is applied to the positions.
        val written = buffer.words.copyOf(buffer.wordCount).toList().chunked(5).map { vertex ->
            vertex.take(4).map(java.lang.Float::intBitsToFloat)
        }
        val positions = written.map { (x, y) -> x to y }
        assertEquals(listOf(5f to 7f, 25f to 7f, 5f to 27f), positions)
        assertTrue(written.all { it[2] == 0.5f && it[3] == 0.5f })
    }

    @Test
    fun drawTrianglesUsesTheSolidStateAfterARect() {
        val renderer = RecordingRenderer()

        renderer.begin()
        renderer.drawRect(0f, 0f, 10f, 10f, radius = 4f)
        renderer.drawTriangles(floatArrayOf(0f, 0f, 10f, 0f, 0f, 10f), 3, Color4.White)
        renderer.end()

        // The mesh did not merge into the rect's command and does not inherit its uniforms.
        assertEquals(2, renderer.commands.size)

        val command = renderer.commands.last()
        assertSame(BuiltIn.Shaders.SolidQuad, command.shader)
        assertEquals(Shader.UniformSet(), command.uniformSet)
        assertNull(command.texture)
    }

    @Test
    fun externalTexturesDrawWithTheVideoShaderAndMerge() {
        val renderer = RecordingRenderer()
        val texture = FakeExternalTexture()
        val region = Texture.Region(texture, FakeSource, 0f, 0f, 16f, 16f)

        renderer.begin()
        renderer.drawTexture(texture, region, 0f, 0f, 16f, 16f)
        renderer.drawTexture(texture, region, 16f, 0f, 16f, 16f)
        renderer.end()

        assertEquals(1, renderer.commands.size)

        val command = renderer.commands[0]
        assertSame(video, command.shader)
        assertSame(texture.transform, command.uniformSet.entries["uTransform"])
        assertEquals(2 * VERTICES_PER_RECT * Renderer.VERTEX_2D_STRIDE, command.buffer!!.position)
    }

    private object FakeSource : Texture.Source {
        override val width = 16f
        override val height = 16f
        override fun writePixels(target: Texture.PixelTarget, x: Int, y: Int) = Unit
    }

    private companion object {
        const val VERTICES_PER_RECT = 6
    }

    @Test
    fun drawVerticesRecordsTheRangeAndIsNeverMerged() {
        val renderer = RecordingRenderer()
        val layout = VertexLayout(listOf(VertexLayout.Attribute(0, 3)))
        val buffer = FakeBuffer().apply { repeat(9 * 3) { putFloat(it.toFloat()) } }

        renderer.begin()
        renderer.drawVertices(solidQuad, buffer, layout, 3, 6, Shader.UniformSet())
        renderer.drawVertices(solidQuad, buffer, layout, 0, 3, Shader.UniformSet())
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.end()

        val commands = renderer.commands
        assertEquals(3, commands.size)
        assertEquals(3, commands[0].firstVertex)
        assertEquals(6, commands[0].drawnVertexCount())
        assertEquals(0, commands[1].firstVertex)
        assertEquals(3, commands[1].drawnVertexCount())
    }

    @Test
    fun clearingABufferChangesItsClearCount() {
        val buffer = FakeBuffer()
        val before = buffer.clearCount

        buffer.putFloat(1f)
        assertEquals(before, buffer.clearCount)

        buffer.clear()
        assertEquals(before + 1, buffer.clearCount)
    }

    @Test
    fun drawsUnderAClipCarryItAndMerge() {
        val renderer = RecordingRenderer()
        val radii = Vec4(8f, 8f, 8f, 8f)

        renderer.begin()
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.pushClip(0f, 0f, 100f, 50f, radii)
        val clip = renderer.currentClip!!
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.drawRect(20f, 0f, 10f, 10f)
        renderer.popClip()
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.end()

        val commands = renderer.commands
        assertEquals(3, commands.size)

        assertNull(commands[0].clip)
        assertNull(commands[0].scissor)

        // Both rects under the clip share its command, scissored to its bounds.
        assertSame(clip, commands[1].clip)
        assertEquals(8 * Renderer.UI_VERTEX_STRIDE, commands[1].buffer!!.position)
        assertEquals(Vec4(0f, 0f, 100f, 50f), commands[1].scissor)
        assertEquals(radii, clip.radii)
        assertEquals(Vec4(0f, 0f, 100f, 50f), clip.rect)

        // Popping it removes its scissor too.
        assertNull(commands[2].clip)
        assertNull(commands[2].scissor)
        assertNull(renderer.currentClip)
    }

    @Test
    fun clipsWithEqualValuesDontMerge() {
        val renderer = RecordingRenderer()
        val radii = Vec4(4f, 4f, 4f, 4f)

        renderer.begin()
        renderer.pushClip(0f, 0f, 100f, 50f, radii)
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.popClip()
        renderer.pushClip(0f, 0f, 100f, 50f, radii)
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.popClip()
        renderer.end()

        assertEquals(2, renderer.commands.size)
        assertNotSame(renderer.commands[0].clip, renderer.commands[1].clip)
    }

    @Test
    fun clipMapsTheSceneBackToTheClippedSpace() {
        val renderer = RecordingRenderer()
        val matrix = Mat2().apply {
            skew(-15f, 0f)
            rotate(30f)
            translate(100f, 50f)
        }

        renderer.begin()
        renderer.pushMatrix(matrix)
        renderer.pushClip(0f, 0f, 100f, 50f, Vec4(0f, 0f, 0f, 0f))
        val clip = renderer.currentClip!!
        renderer.popClip()
        renderer.popMatrix()
        renderer.end()

        // A point of the clipped space, drawn in the scene, comes back to where it was.
        val scene = matrix.transform(10f, 20f)
        val t = clip.transform
        val o = clip.translation
        assertEquals(10f, t.x * scene.x + t.z * scene.y + o.x, 1e-3f)
        assertEquals(20f, t.y * scene.x + t.w * scene.y + o.y, 1e-3f)

        // Enabled, and no flip on a framebuffer whose origin is at the top left.
        assertEquals(1f, o.w, 0f)
        assertEquals(0f, o.z, 0f)
    }

    @Test
    fun componentsClipWithAScissorUnlessRoundedOrTransformed() {
        fun clipOf(builder: Container.() -> Unit): DrawCommand {
            val container = Container().apply {
                width = 100f.px
                height = 50f.px
                clipping = ClipMode.Bounds
                builder()
                +Box().apply { width = 10f.px; height = 10f.px }
            }
            container.measure(LayoutConstraints(0f, 800f, 0f, 600f))
            container.layout(Vec4(0f, 0f, 800f, 600f))

            val renderer = RecordingRenderer()
            renderer.begin()
            container.draw(renderer)
            renderer.end()

            // The box, the only draw.
            return renderer.commands.single()
        }

        val plain = clipOf { }
        assertNull(plain.clip)
        assertEquals(Vec4(0f, 0f, 100f, 50f), plain.scissor)

        val rounded = clipOf { radius = CornerRadius(8f.px) }
        assertEquals(Vec4(8f, 8f, 8f, 8f), rounded.clip!!.radii)

        val rotated = clipOf { rotationZ = 10f }
        assertEquals(Vec4(0f, 0f, 0f, 0f), rotated.clip!!.radii)

        val padded = clipOf {
            clipping = ClipMode.Padding
            padding = Padding(4f.px, 4f.px, 4f.px, 4f.px)
            radius = CornerRadius(10f.px)
        }
        // The inner corners, reduced by the padding.
        assertEquals(Vec4(6f, 6f, 6f, 6f), padded.clip!!.radii)
        assertEquals(Vec4(4f, 4f, 92f, 42f), padded.clip!!.rect)
    }

}
