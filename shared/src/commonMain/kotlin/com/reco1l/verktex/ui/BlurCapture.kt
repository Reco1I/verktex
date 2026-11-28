package com.reco1l.verktex.ui

import kotlin.concurrent.Volatile
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Mat2
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.graphics.FramebufferOrigin
import com.reco1l.verktex.graphics.GPUResource
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The capture of what is behind a component with a [com.reco1l.verktex.data.Background.Blur], blurred,
 * which the background draws. It belongs to the blur, and so to a single component at a time.
 *
 * Everything behind the component was drawn when its background is, so it is captured then, from the
 * framebuffer, and blurred by drawing it over the window with the blur shader and capturing it again.
 * Over a component that isn't the whole window the passes are clipped to its shape, so what is around
 * it stays as it was.
 */
internal class BlurCapture {

    /**
     * Whether the capture is current, it isn't taken again until [invalidate] is called.
     */
    val isShowingCapture: Boolean
        get() {
            val texture = texture ?: return false

            // A context loss empties the texture, it is captured again then.
            return isCaptured && texture.state == GPUResource.State.Loaded
                && texture.width.toInt() == Verktex.graphics.window.windowWidth
                && texture.height.toInt() == Verktex.graphics.window.windowHeight
        }

    /**
     * Whether the capture was taken for the whole window, as an overlay covering it.
     */
    val coversWindow: Boolean
        get() = coversWindow(capturedSceneRect)


    @Volatile
    private var isCaptured = false

    private var texture: Texture? = null
    private var region: Texture.Region? = null

    // The bounds in the window the capture was taken for, as left, top, right and bottom.
    private val capturedSceneRect = FloatArray(4)
    private val sceneRect = FloatArray(4)


    fun invalidate() {
        isCaptured = false
    }

    /**
     * Captures what was drawn behind [component], placed in the window by [localToScene], unless the
     * capture is current, blurs it by [radius] pixels and draws the part of it behind the component,
     * rounded by [radii].
     *
     * As an overlay covering the whole window nothing is drawn: the blurred capture is left over the
     * window, and it stands for what is behind in the next frames, see [drawCapture].
     */
    fun draw(renderer: Renderer, component: Component, localToScene: Mat2, radius: Float, isStatic: Boolean, radii: CornerRadius) {
        val bounds = component.bounds

        localToScene.transform(0f, 0f, sceneRect, 0)
        localToScene.transform(bounds.width, bounds.height, sceneRect, 2)

        if (!isStatic || !sceneRect.contentEquals(capturedSceneRect)) {
            sceneRect.copyInto(capturedSceneRect)
            isCaptured = false
        }

        val isWindow = Verktex.isOverlayRoot(component) && coversWindow(sceneRect)

        val topLeft = radii.topLeft.toPixels()
        val topRight = radii.topRight.toPixels()
        val bottomRight = radii.bottomRight.toPixels()
        val bottomLeft = radii.bottomLeft.toPixels()

        if (!isShowingCapture) {
            val texture = ensureTexture()
            renderer.captureFramebuffer(texture)

            if (radius > 0f) {
                // What is outside the component stays as it is, the blur is kept within its shape.
                if (!isWindow) renderer.pushClip(0f, 0f, bounds.width, bounds.height, Vec4(topLeft, topRight, bottomRight, bottomLeft))
                blur(renderer, texture, radius, if (isWindow) BlendMode.Opaque else BlendMode.Alpha)
                if (!isWindow) renderer.popClip()
            }
            isCaptured = true
        }

        if (isWindow) {
            return
        }

        val texture = texture ?: return
        val region = region ?: return

        renderer.drawTexture(
            texture = texture,
            region = region,
            x = 0f,
            y = 0f,
            width = bounds.width,
            height = bounds.height,
            uv = Vec4(
                sceneRect[0] / texture.width,
                renderer.captureV(sceneRect[1] / texture.height),
                sceneRect[2] / texture.width,
                renderer.captureV(sceneRect[3] / texture.height)
            ),
            radiusTopLeft = topLeft,
            radiusTopRight = topRight,
            radiusBottomRight = bottomRight,
            radiusBottomLeft = bottomLeft,
            maskX = 0f,
            maskY = 0f,
            maskWidth = bounds.width,
            maskHeight = bounds.height
        )
    }

    /**
     * Draws the capture over the whole window, in place of what is behind.
     */
    fun drawCapture(renderer: Renderer) {
        val texture = texture ?: return
        val region = region ?: return

        // Opaque, it stands for everything drawn behind including the background.
        renderer.pushBlendMode(BlendMode.Opaque)
        renderer.drawTexture(
            texture = texture,
            region = region,
            x = 0f,
            y = 0f,
            width = texture.width,
            height = texture.height,
            uv = Vec4(0f, renderer.captureV(0f), 1f, renderer.captureV(1f))
        )
        renderer.popBlendMode()
    }

    /**
     * Frees the capture, it isn't kept while its component is out of the tree: a capture of the
     * window takes a few megabytes.
     */
    fun release() {
        isCaptured = false

        val texture = texture ?: return
        this.texture = null
        region = null

        Verktex.scheduleOnDraw {
            texture.dispose()
            Verktex.graphics.unregisterResource(texture)
        }
    }

    private fun coversWindow(rect: FloatArray): Boolean {
        val window = Verktex.graphics.window
        return rect[0] <= 0f && rect[1] <= 0f && rect[2] >= window.windowWidth && rect[3] >= window.windowHeight
    }

    /**
     * Blurs the captured [texture] by drawing it over the window with the blur shader and capturing
     * it again, one axis at a time. Each pass doubles the spacing of the taps of the last one, so a
     * large radius takes a few passes instead of many taps.
     */
    private fun blur(renderer: Renderer, texture: Texture, radius: Float, blendMode: BlendMode) {

        // A pass has a deviation of two steps, the variances of all passes add up to the radius.
        // Steps longer than 1.5 pixels skip over details in the first pass, more passes are used then.
        fun firstStep(passes: Int) = radius / (2f * sqrt((4f.pow(passes) - 1f) / 3f))

        var passes = 1
        while (passes < MAX_BLUR_PASSES && firstStep(passes) > 1.5f) passes++

        var step = firstStep(passes)

        renderer.pushBlendMode(blendMode)
        repeat(passes) {
            drawBlurPass(renderer, texture, Vec2(step / texture.width, 0f))
            renderer.captureFramebuffer(texture)
            drawBlurPass(renderer, texture, Vec2(0f, step / texture.height))
            renderer.captureFramebuffer(texture)
            step *= 2f
        }
        renderer.popBlendMode()
    }

    private fun drawBlurPass(renderer: Renderer, texture: Texture, direction: Vec2) = with(renderer) {
        setState(
            shader = BuiltIn.Shaders.Blur,
            texture = texture,
            uniformSet = Shader.UniformSet().set("uDirection", direction)
        )

        val width = texture.width
        val height = texture.height

        val top = captureV(0f)
        val bottom = captureV(1f)

        // The window, whatever the transformation of the component.
        drawGeometry { _, _ ->
            addVertex(0f, 0f, 0f, top, Color4.White)
            addVertex(width, 0f, 1f, top, Color4.White)
            addVertex(width, height, 1f, bottom, Color4.White)

            addVertex(0f, 0f, 0f, top, Color4.White)
            addVertex(width, height, 1f, bottom, Color4.White)
            addVertex(0f, height, 0f, bottom, Color4.White)
        }
    }

    // The v of the capture for a v going down the window, the capture is upside down when the
    // framebuffer starts at the bottom.
    private fun Renderer.captureV(v: Float) = if (framebufferOrigin == FramebufferOrigin.BottomLeft) 1f - v else v

    private fun ensureTexture(): Texture {
        val width = Verktex.graphics.window.windowWidth.toFloat()
        val height = Verktex.graphics.window.windowHeight.toFloat()

        val current = texture
        if (current != null && current.width == width && current.height == height) {
            return current
        }

        // A new size, e.g. after a rotation or another resolution scale. This runs while drawing,
        // on the draw worker, where the old texture can be deleted.
        if (current != null) {
            current.dispose()
            Verktex.graphics.unregisterResource(current)
        }

        val texture = Verktex.graphics.createTexture(width, height)
        this.texture = texture
        region = Texture.Region(texture, CaptureSource(width, height), 0f, 0f, width, height)
        return texture
    }


    companion object {
        private const val MAX_BLUR_PASSES = 6
    }


    // The texture is filled by copying the framebuffer, nothing is uploaded.
    private class CaptureSource(override val width: Float, override val height: Float) : Texture.Source {
        override fun writePixels(target: Texture.PixelTarget, x: Int, y: Int) = Unit
    }
}
