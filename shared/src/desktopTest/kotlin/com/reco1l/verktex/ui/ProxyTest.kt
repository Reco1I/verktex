package com.reco1l.verktex.ui

import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.Buffer
import com.reco1l.verktex.graphics.Device
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.GPUContext
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture
import com.reco1l.verktex.graphics.Window
import com.reco1l.verktex.ui.container.Container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProxyTest {

    private object FakeDevice : Device {
        override val capabilities = Device.Capabilities(maxTextureSize = 4096f)
        override fun createBuffer(): Buffer = throw UnsupportedOperationException()
        override fun createShader(source: Shader.Source): Shader = throw UnsupportedOperationException()
        override fun createTexture(width: Float, height: Float): Texture = throw UnsupportedOperationException()
        override fun createExternalTexture(width: Float, height: Float): Texture = throw UnsupportedOperationException()
    }

    private object FakeWindow : Window {
        override var windowWidth = 1280
        override var windowHeight = 720
        override var insets = Window.Insets(Vec4(0f, 0f, 1280f, 720f), Vec2(0f, 0f))
        override fun shouldClose() = false
        override fun pollEvents() = Unit
    }

    private class FakeRenderer : Renderer() {
        override val device: Device = FakeDevice
        override val context: GPUContext get() = throw UnsupportedOperationException()
        override val window: Window = FakeWindow
        override var version = "fake"
        override fun drawCommand(command: DrawCommand) = Unit
    }

    /** Records the order it's drawn in, with the color and the translation in effect. */
    private class Probe(private val log: MutableList<String>, private val name: String) : Component() {
        var seen: Color4? = null
        var sceneX = Float.NaN

        override fun onDraw(renderer: Renderer) {
            log += name
            seen = renderer.transformColor(Color4.White)
            sceneX = renderer.currentTransform.tx
        }
    }

    private val log = mutableListOf<String>()

    private fun draw(root: Component) {
        val renderer = FakeRenderer()
        renderer.begin()
        root.draw(renderer)
        renderer.end()
    }

    // root: [owner: [piece, sibling], layer: [proxy]]
    private val piece = Probe(log, "piece")
    private val owner = Container().apply {
        translationX = 100f.px
        this += piece
        this += Probe(log, "sibling")
    }
    private val pieceProxy = Proxy(piece)
    private val layer = Container().apply { this += pieceProxy }
    private val root = Container().apply {
        this += owner
        this += layer
    }

    @Test
    fun `Proxied component is skipped by its parent and drawn through the proxy`() {
        draw(root)

        assertEquals(listOf("sibling", "piece"), log)
    }

    @Test
    fun `Proxied component keeps its position in its parent`() {
        draw(root)

        assertEquals(100f, piece.sceneX, 1e-4f)
    }

    @Test
    fun `Proxied component takes the alpha and tint of its ancestors`() {
        owner.color = Color4(0.5f, 1f, 1f, 0.5f)
        layer.alpha = 0.5f

        draw(root)

        val color = piece.seen!!
        assertEquals(0.25f, color.alpha, 1e-4f)
        assertEquals(0.5f, color.red, 1e-4f)
        assertEquals(1f, color.green, 1e-4f)
    }

    @Test
    fun `Proxied component isn't drawn while an ancestor is invisible`() {
        owner.isVisible = false

        draw(root)

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `Removing the proxy gives the component back to its parent`() {
        layer -= pieceProxy
        assertNull(piece.proxy)

        draw(root)

        assertEquals(listOf("piece", "sibling"), log)
    }

    @Test
    fun `Proxy only claims its target while it's attached`() {
        val detached = Proxy(piece)
        assertSame(pieceProxy, piece.proxy)

        layer += detached
        assertSame(detached, piece.proxy)

        draw(root)

        // The first proxy no longer owns it, so the piece is drawn once.
        assertEquals(listOf("sibling", "piece"), log)
    }

    @Test
    fun `Changing the target releases the previous one`() {
        val other = Probe(log, "other")
        owner += other

        pieceProxy.target = other
        assertNull(piece.proxy)
        assertSame(pieceProxy, other.proxy)

        draw(root)

        assertEquals(listOf("piece", "sibling", "other"), log)
    }
}
